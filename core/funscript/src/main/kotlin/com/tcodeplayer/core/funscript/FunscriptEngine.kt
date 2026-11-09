package com.tcodeplayer.core.funscript

/**
 * 纯 Kotlin 模块：不得 import mpv / mpvEx / Android UI 类。
 * sample 返回 [0.0, 0.99] 区间的归一化幅度，key 为轴 ID。
 */
interface FunscriptEngine {
    fun load(scripts: List<AxisScript>)
    fun clear()
    fun sample(positionMs: Long): Map<String, Double>
    /** 所有轴里最后一个 action 的时间戳（毫秒）；无脚本返回 0。 */
    fun getMaxDurationMs(): Long
}
