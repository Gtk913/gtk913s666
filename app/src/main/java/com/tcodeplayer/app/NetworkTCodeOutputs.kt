package com.tcodeplayer.app

import com.tcodeplayer.core.tcode.TCodeOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * MultiFunPlayer 风格的 WiFi 输出：TCP 长连接 / UDP 无连接。
 * 只做「把一批 TCode 指令发到 host:port」，不改插值、不改指令格式。
 * 网络设备一般不回 D1/D2，故 query 返回 null → 回落 0.3 / 不过滤。
 */
class TcpTCodeOutput(
    private var host: String,
    private var port: Int,
) : TCodeOutput {
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var connected = false
    @Volatile private var version: String? = null

    fun reconfigure(host: String, port: Int) { this.host = host; this.port = port }
    fun isConnected(): Boolean = connected

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            disconnectInternal()
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 2000)
            s.tcpNoDelay = true
            socket = s
            writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            connected = true
            true
        }.getOrDefault(false)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) { disconnectInternal() }

    private fun disconnectInternal() {
        runCatching { writer?.flush() }
        runCatching { socket?.close() }
        writer = null; socket = null; connected = false
    }

    /** 与蓝牙一致：整批一次写出 + flush，末尾 \n 由 formatter 保证。 */
    override suspend fun send(batch: String) = withContext(Dispatchers.IO) {
        val w = writer ?: return@withContext
        synchronized(this) { w.write(batch); w.flush() }
    }

    /** 第十六轮：专用驱动线程直写，不切协程调度器。 */
    override fun sendNow(batch: String) {
        val w = writer ?: return
        synchronized(this) { w.write(batch); w.flush() }
    }

    override suspend fun queryProtocolVersion(): String? = null
    override suspend fun queryAxes(): List<String>? = null
    override fun setProtocolVersion(version: String?) { this.version = version }
}

class UdpTCodeOutput(
    private var host: String,
    private var port: Int,
) : TCodeOutput {
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var connected = false
    @Volatile private var version: String? = null

    fun reconfigure(host: String, port: Int) { this.host = host; this.port = port }
    fun isConnected(): Boolean = connected

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            socket?.close()
            socket = DatagramSocket()
            connected = true
            true
        }.getOrDefault(false)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { socket?.close() }
        socket = null; connected = false
    }

    override suspend fun send(batch: String) = withContext(Dispatchers.IO) {
        val s = socket ?: return@withContext
        val bytes = batch.toByteArray(Charsets.UTF_8)
        s.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(host), port))
    }

    /** 第十六轮：UDP 专用线程直写；UDP 不确认收到，故驱动层对它强制关闭 dirty-only。 */
    override fun sendNow(batch: String) {
        val s = socket ?: return
        val bytes = batch.toByteArray(Charsets.UTF_8)
        s.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(host), port))
    }

    override suspend fun queryProtocolVersion(): String? = null
    override suspend fun queryAxes(): List<String>? = null
    override fun setProtocolVersion(version: String?) { this.version = version }
}
