package com.tcodeplayer.app

/**
 * USB 有线串口（CH340 / CP210x / FTDI）的真实链路参数。
 * 与蓝牙 SPP 的本质区别：Android USB Host 直接跟串口芯片通信，
 * setParameters(baud, 8, 1, none) 是**真正生效**的，不是档案字段。
 */
object UsbSerialSpec {
    const val DEFAULT_BAUD = 9600
    val COMMON_BAUD = listOf(1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600)
    const val DATA_BITS = 8
    const val STOP_BITS = 1
    const val PARITY_NONE = 0

    fun isValid(baud: Int): Boolean = baud in 300..4_000_000

    /** 8 数据位 / 1 停止位 / 无校验，写成给界面看的短标签。 */
    const val FRAMING = "8N1"
}
