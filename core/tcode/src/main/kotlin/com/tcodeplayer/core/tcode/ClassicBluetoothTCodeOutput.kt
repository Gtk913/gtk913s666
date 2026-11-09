package com.tcodeplayer.core.tcode

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * 经典蓝牙 SPP（非 BLE），UUID 00001101-0000-1000-8000-00805f9b34fb。
 *
 * 第十六轮：**移除 TimedBlockingWriter / 100ms 超时丢帧 / busy 丢帧**。
 * MFP 的 SerialOutputTarget 也是专用线程顺序写，没有「超时就丢帧」这个概念：
 * 写慢了就晚一点，但每一帧都必须送到。丢帧正是「设备动几下停一会」的直接来源。
 * 真正的断连只由 IOException 判定。
 */
class ClassicBluetoothTCodeOutput(
    private val io: CoroutineContext = Dispatchers.IO,
) : TCodeOutput {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        private const val QUERY_TIMEOUT_MS = 500L
        private const val QUERY_RETRIES = 2
        private val VERSION = Regex("""0\.[234]""")
        private val AXIS = Regex("""\b[LR][0-2]\b""", RegexOption.IGNORE_CASE)
    }

    @Volatile private var lastOutcome: WriteOutcome = WriteOutcome.OK
    private val writeFailures = AtomicLong(0L)

    @Volatile private var target: BluetoothDevice? = null
    fun setTarget(device: BluetoothDevice) { target = device }

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var connected = false
    @Volatile private var protocolVersion: String? = null

    fun isConnected(): Boolean = connected
    override fun setProtocolVersion(version: String?) { protocolVersion = version }
    fun currentProtocolVersion(): String? = protocolVersion

    override suspend fun connect(): Boolean = withContext(io) {
        val device = target ?: return@withContext false
        val s = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            sendStop()
            return@withContext false
        }
        socket = s
        try {
            s.connect()
            writeFailures.set(0)
            connected = true
            true
        } catch (e: Exception) {
            connected = false
            sendStop()
            closeQuietly()
            false
        }
    }

    override suspend fun disconnect() = withContext(io) {
        sendStop()
        connected = false
        closeQuietly()
    }

    /**
     * 专用驱动线程直写：不切协程、不排队、不丢帧。
     * 写阻塞会让本帧晚到，但 I<elapsed> 会同步变大，固件自行平滑。
     */
    override fun sendNow(batch: String) {
        val s = socket ?: throw IOException("SPP socket not connected")
        try {
            s.outputStream.write(batch.toByteArray(Charsets.US_ASCII))
            s.outputStream.flush()
            lastOutcome = WriteOutcome.OK
        } catch (e: IOException) {
            lastOutcome = WriteOutcome.FAILED
            writeFailures.incrementAndGet()
            connected = false
            throw e
        }
    }

    /** 协程路径（手动调试 / 握手 / DSTOP）仍走 IO 调度器，内部复用同一份直写逻辑。 */
    override suspend fun send(batch: String): Unit = withContext(io) { sendNow(batch) }

    /** 面板读取：丢帧计数恒为 0（机制已移除），写失败单独可见。 */
    fun lastWriteOutcome(): WriteOutcome = lastOutcome
    fun droppedBusyCount(): Long = 0L
    fun droppedTimeoutCount(): Long = 0L
    fun writeFailureCount(): Long = writeFailures.get()

    /** D1：等待 500ms 读取响应；超时重试 2 次；全部失败返回 null。 */
    override suspend fun queryProtocolVersion(): String? =
        query("D1\n") { VERSION.find(it)?.value }

    /** D2：同上；失败返回 null。返回 null 时调用方不过滤轴。 */
    override suspend fun queryAxes(): List<String>? =
        query("D2\n") { response ->
            val list = AXIS.findAll(response)
                .map { it.value.uppercase() }
                .distinct()
                .toList()
            if (list.isEmpty()) null else list
        }

    private suspend fun <T> query(command: String, parse: (String) -> T?): T? {
        repeat(QUERY_RETRIES + 1) {
            try {
                val response = withTimeout(QUERY_TIMEOUT_MS) {
                    withContext(io) {
                        writeRaw(command)
                        readResponse(QUERY_TIMEOUT_MS)
                    }
                }
                parse(response)?.let { return it }
            } catch (e: Exception) {
                // 超时或读异常：进入下一次重试
            }
        }
        sendStop()
        return null
    }

    private fun writeRaw(text: String) {
        val s = socket ?: throw IOException("SPP socket not connected")
        s.outputStream.write(text.toByteArray(Charsets.US_ASCII))
        s.outputStream.flush()
    }

    /** 非阻塞轮询读取，保证 withTimeout 的取消能生效。 */
    private suspend fun readResponse(timeoutMs: Long): String {
        val s = socket ?: throw IOException("SPP socket not connected")
        val input = s.inputStream
        val sb = StringBuilder()
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            currentCoroutineContext().ensureActive()
            val available = input.available()
            if (available > 0) {
                val buf = ByteArray(available)
                val read = input.read(buf)
                if (read > 0) {
                    sb.append(String(buf, 0, read, Charsets.US_ASCII))
                    if (sb.contains('\n')) break
                }
            } else {
                delay(10L)
            }
        }
        return sb.toString()
    }

    private suspend fun sendStop() {
        try {
            send(TCodeCommandFormatter.STOP)
        } catch (e: Exception) {
            // 连接已断，DSTOP 无法送达；不再重试
        }
    }

    private fun closeQuietly() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // 静默
        }
        socket = null
    }
}
