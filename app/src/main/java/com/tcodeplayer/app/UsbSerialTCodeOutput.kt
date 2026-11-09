package com.tcodeplayer.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.tcodeplayer.core.tcode.TCodeOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 有线串口输出：手机 OTG → CH340（也兼容 CP210x / FTDI / PL2303）。
 * 默认探测器的 getDefaultProber() 已包含 Ch34xDriver，无需手写 CH340 控制传输。
 *
 * 这里是**真波特率**：port.setParameters(baud, 8, STOPBITS_1, PARITY_NONE)
 * 直接配置芯片，跟蓝牙 SPP 的「模块 UART 档案」完全是两回事。
 */
class UsbSerialTCodeOutput(
    private val context: Context?,
    private var baudRate: Int,
) : TCodeOutput {
    companion object {
        const val ACTION_USB_PERMISSION = "com.tcodeplayer.app.USB_PERMISSION"
        /** CH340 的 vendor/product 由 getDefaultProber() 自动识别，无需硬编码 VID/PID。 */
        private const val WRITE_TIMEOUT_MS = 100
    }

    @Volatile private var port: UsbSerialPort? = null
    @Volatile private var connected = false
    @Volatile private var version: String? = null

    fun reconfigure(baud: Int) { baudRate = baud }
    fun isConnected(): Boolean = connected

    /** 首次连接会弹系统授权框；允许后再点一次连接即可（无需常驻 receiver）。 */
    private fun ensurePermission(manager: UsbManager, device: android.hardware.usb.UsbDevice): Boolean {
        if (manager.hasPermission(device)) return true
        val ctx = context ?: return false
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ACTION_USB_PERMISSION).setPackage(ctx.packageName), flags
        )
        manager.requestPermission(device, pi)
        return false
    }

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext false
        val manager = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return@withContext false
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        val driver = drivers.firstOrNull() ?: return@withContext false
        if (!ensurePermission(manager, driver.device)) return@withContext false
        val connection = manager.openDevice(driver.device) ?: return@withContext false
        val p = driver.ports.firstOrNull() ?: return@withContext false
        runCatching {
            p.open(connection)
            p.setParameters(
                baudRate,
                UsbSerialSpec.DATA_BITS,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE,
            )
            port = p
            connected = true
            true
        }.getOrDefault(false)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { port?.close() }
        port = null; connected = false
    }

    override suspend fun send(batch: String) = withContext(Dispatchers.IO) {
        val p = port ?: return@withContext
        p.write(batch.toByteArray(Charsets.UTF_8), WRITE_TIMEOUT_MS)
    }

    /** 第十六轮：专用驱动线程直写，不切协程调度器。 */
    override fun sendNow(batch: String) {
        val p = port ?: return
        p.write(batch.toByteArray(Charsets.UTF_8), WRITE_TIMEOUT_MS)
    }

    override suspend fun queryProtocolVersion(): String? = null
    override suspend fun queryAxes(): List<String>? = null
    override fun setProtocolVersion(version: String?) { this.version = version }
}
