package com.tcodeplayer.core.tcode

import kotlinx.coroutines.runBlocking

/**
 * 输出层接口。实现类不得 import mpv / mpvEx / Android UI 类
 * （android.bluetooth.* 属于设备层，允许）。
 */
interface TCodeOutput {
    /** 目标由各实现自己持有（蓝牙 MAC / host:port / USB 设备），接口不绑定具体传输。 */
    suspend fun connect(): Boolean
    suspend fun disconnect()
    /** batch 内含换行分隔的多条指令，且已含末尾 \n。 */
    suspend fun send(batch: String)

    /**
     * 第十六轮：专用驱动线程上的**同步**发送。
     * 默认委托给协程版本；四个输出实现全部 override 成直接写流，
     * 避免每帧 withContext(Dispatchers.IO) 切线程造成 50Hz 时钟抖动。
     */
    fun sendNow(batch: String) { runBlocking { send(batch) } }

    /** "0.2" / "0.3" / "0.4" / null。 */
    suspend fun queryProtocolVersion(): String?
    /** 如 ["L0","L1","R0"]；失败返回 null。 */
    suspend fun queryAxes(): List<String>?
    /** Phase 2 v0.4 预留；Phase 1 无论 D1 返回什么都统一按 v0.3 短命令输出。 */
    fun setProtocolVersion(version: String?)
}
