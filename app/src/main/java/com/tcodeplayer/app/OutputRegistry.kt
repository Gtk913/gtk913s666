package com.tcodeplayer.app

import android.annotation.SuppressLint
import android.content.Context
import com.tcodeplayer.core.tcode.ClassicBluetoothTCodeOutput
import com.tcodeplayer.core.tcode.TCodeOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 输出层注册表（可插拔）。参考 MultiFunPlayer 的输出分类（Serial / TCP / UDP），
 * 但只借分类，不搬它的插值算法；新增一种输出只需在这里加一个分支 + 一个实现类。
 *
 * 关键约束：蓝牙 SPP 链路**没有波特率**概念（Android BluetoothSocket 不暴露），
 * 只有 USB 有线串口才需要真实配置波特率。
 *
 * 第十五轮：WiFi 默认目标改为 `tcode.local:8000`（TCodeESP32 常见 mDNS 名），
 * 且 host / port / wifiMode / baud / key 全部持久化，退出重进仍保留。
 */
object OutputRegistry {
    const val BLUETOOTH = "bluetooth"
    const val TCP = "tcp"
    const val UDP = "udp"
    const val USB = "usb"
    /** UI 一级分组：TCP / UDP 收在 WiFi 下面，不是实际输出 key。 */
    const val WIFI = "wifi"

    val keys: List<String> = listOf(BLUETOOTH, TCP, UDP, USB)
    /** 输出选择器的一级分组：经典蓝牙 / WiFi / USB 串口。 */
    val uiGroups: List<String> = listOf(BLUETOOTH, WIFI, USB)
    val groupLabels: Map<String, String> = mapOf(
        BLUETOOTH to "经典蓝牙",
        WIFI to "WiFi",
        USB to "USB 串口",
    )
    fun groupOf(key: String): String = if (key == TCP || key == UDP) WIFI else key
    const val defaultKey: String = BLUETOOTH

    /** 第十五轮：用户指定的 WiFi 默认地址。 */
    const val DEFAULT_HOST = "tcode.local"
    const val DEFAULT_PORT = 8000
    private const val PREFS = "tcode_output_registry"
    private const val SEED_FLAG = "default_seeded_v2"

    /** 供 C20 自检断言：蓝牙无波特率、USB 有真实波特率。 */
    const val BLUETOOTH_HAS_BAUD_RATE = false
    const val USB_HAS_BAUD_RATE = true

    val labels: Map<String, String> = mapOf(
        BLUETOOTH to "经典蓝牙 SPP",
        TCP to "WiFi TCP",
        UDP to "WiFi UDP",
        USB to "USB 串口（CH340）",
    )

    private val _key = MutableStateFlow(defaultKey)
    val key: StateFlow<String> = _key

    private val _host = MutableStateFlow(DEFAULT_HOST)
    val host: StateFlow<String> = _host
    private val _port = MutableStateFlow(DEFAULT_PORT)
    val port: StateFlow<Int> = _port
    private val _baud = MutableStateFlow(UsbSerialSpec.DEFAULT_BAUD)
    val baud: StateFlow<Int> = _baud
    /** WiFi 分组下的二级选择：TCP 或 UDP。 */
    private val _wifiMode = MutableStateFlow(TCP)
    val wifiMode: StateFlow<String> = _wifiMode

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        val p = prefs() ?: return
        if (!p.getBoolean(SEED_FLAG, false)) {
            // 覆盖安装也强制切到新默认一次；之后完全尊重用户手动改的值
            p.edit().putString("host", DEFAULT_HOST)
                .putInt("port", DEFAULT_PORT)
                .putString("wifiMode", TCP)
                .putBoolean(SEED_FLAG, true)
                .commit()
        }
        _host.value = p.getString("host", DEFAULT_HOST) ?: DEFAULT_HOST
        _port.value = p.getInt("port", DEFAULT_PORT)
        _wifiMode.value = p.getString("wifiMode", TCP) ?: TCP
        _baud.value = p.getInt("baud", UsbSerialSpec.DEFAULT_BAUD)
        val k = p.getString("key", defaultKey) ?: defaultKey
        _key.value = if (k in keys) k else defaultKey
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 同步落盘：退出重进一定能读到（不用 apply 的异步窗口）。 */
    private fun save(vararg pairs: Pair<String, Any?>) {
        val p = prefs() ?: return
        val e = p.edit()
        for ((k, v) in pairs) {
            when (v) {
                is String -> e.putString(k, v)
                is Int -> e.putInt(k, v)
                is Boolean -> e.putBoolean(k, v)
                null -> e.remove(k)
            }
        }
        e.commit()
    }

    fun setKey(value: String) {
        if (value in keys) {
            _key.value = value
            save("key" to value)
        }
    }
    fun setHost(value: String) {
        val v = value.trim()
        _host.value = v
        save("host" to v)
    }
    fun setPort(value: Int) {
        val v = value.coerceIn(1, 65535)
        _port.value = v
        save("port" to v)
    }
    fun setBaud(value: Int) {
        if (UsbSerialSpec.isValid(value)) {
            _baud.value = value
            save("baud" to value)
        }
    }
    fun setWifiMode(mode: String) {
        if (mode == TCP || mode == UDP) {
            _wifiMode.value = mode
            save("wifiMode" to mode)
        }
    }

    fun label(value: String = _key.value): String = labels[value] ?: value

    fun create(value: String = _key.value): TCodeOutput = when (value) {
        TCP -> TcpTCodeOutput(_host.value, _port.value)
        UDP -> UdpTCodeOutput(_host.value, _port.value)
        USB -> UsbSerialTCodeOutput(appContext, _baud.value)
        else -> ClassicBluetoothTCodeOutput()
    }

    /** 切换输出时由 TCodeRuntime 调用：记录当前选择，构建新实现。 */
    fun switch(value: String): TCodeOutput {
        setKey(value)
        return create(value)
    }
}
