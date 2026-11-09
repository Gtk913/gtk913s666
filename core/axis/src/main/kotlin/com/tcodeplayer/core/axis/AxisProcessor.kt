package com.tcodeplayer.core.axis

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * 方案第 5 节 AxisProcessor（纯 Kotlin，不 import mpv / mpvEx / Android UI）。
 *
 * 处理顺序（硬性，不得调换）：
 *   1. 反转 Invert        val = 0.99 - val
 *   2. 行程限幅 Range     val = val.coerceIn(minLimit, maxLimit)
 *   3. 速度限制 Speed     与上一帧比较，超过 maxDelta 则截断
 *   4. 插值平滑（可选）  仅当 interpolation != LINEAR 时，对最近 3 帧做 PCHIP / MAKIMA 滑动窗口
 *
 * 默认配置下四步全部是恒等变换（不限速、不反转、全行程、LINEAR），
 * 保证既有三份真机串口日志的数值基线一字不变。
 */
class AxisProcessor {

    private val _configs = MutableStateFlow<Map<String, AxisConfig>>(emptyMap())
    val configs: StateFlow<Map<String, AxisConfig>> = _configs

    /** 每轴上一帧输出值，用于速度限制。 */
    private val lastValues = HashMap<String, Double>()

    /** 每轴最近 3 帧 (相对毫秒, 值)，用于可选平滑。 */
    private val windows = HashMap<String, ArrayDeque<Pair<Double, Double>>>()

    private var clockMs = 0.0

    fun setConfig(config: AxisConfig) {
        _configs.value = _configs.value + (config.axisId to config)
        // 配置变化后清掉该轴的状态，避免用旧参数残留的 lastVal 造成跳变
        lastValues.remove(config.axisId)
        windows.remove(config.axisId)
    }

    fun setConfigs(configs: Collection<AxisConfig>) {
        _configs.value = configs.associateBy { it.axisId }
        resetState()
    }

    fun configOf(axisId: String): AxisConfig =
        _configs.value[axisId] ?: AxisConfig(axisId = axisId)

    /** 方案 4.4 / 5.2 接口签名；默认按 50Hz（20ms）计算速度限制。 */
    fun process(raw: Map<String, Double>): Map<String, Double> = process(raw, 20L)

    /**
     * 重载：允许调用方传入真实帧间隔，Seek / 卡顿时速度限制仍准确。
     * elapsedMs <= 0 时按 20ms 兜底。
     */
    fun process(raw: Map<String, Double>, elapsedMs: Long): Map<String, Double> {
        val dtSec = (if (elapsedMs > 0) elapsedMs else 20L) / 1000.0
        clockMs += if (elapsedMs > 0) elapsedMs.toDouble() else 20.0
        val snapshot = _configs.value
        val result = LinkedHashMap<String, Double>(raw.size)

        for ((axisId, value) in raw) {
            val cfg = snapshot[axisId] ?: AxisConfig(axisId = axisId)
            var v = value.coerceIn(0.0, 0.99)

            // 1) 反转
            if (cfg.isInverted) v = (0.99 - v).coerceIn(0.0, 0.99)

            // 2) 行程限幅
            v = v.coerceIn(cfg.limits())

            // 3) 速度限制：maxDelta = maxSpeedPerSec * dt；0 表示不限速
            if (cfg.maxSpeedPerSec > 0.0) {
                val last = lastValues[axisId]
                if (last != null) {
                    val maxDelta = cfg.maxSpeedPerSec * dtSec
                    val diff = v - last
                    if (abs(diff) > maxDelta) v = last + sign(diff) * maxDelta
                }
            }
            v = v.coerceIn(0.0, 0.99).coerceIn(cfg.limits())

            // 4) 可选平滑：仅非 LINEAR 时启用，默认链路完全不进这个分支
            if (cfg.interpolation != InterpolationMode.LINEAR) {
                v = smooth(axisId, v, cfg.interpolation)
            }

            lastValues[axisId] = v
            result[axisId] = v
        }
        return result
    }

    /** 保留最近 3 帧，用 PCHIP / MAKIMA 求值于当前时刻；不足 2 帧时原样返回。 */
    private fun smooth(axisId: String, value: Double, mode: InterpolationMode): Double {
        val w = windows.getOrPut(axisId) { ArrayDeque() }
        w.addLast(clockMs to value)
        while (w.size > 3) w.removeFirst()
        if (w.size < 2) return value
        val pts = w.map { Interpolation.Point(it.first, it.second) }
        return Interpolation.build(pts, mode).at(clockMs)
    }

    /** 媒体切换或 Seek 时清除 lastVal / 滑动窗口缓存（方案 5.2）。 */
    fun resetState() {
        lastValues.clear()
        windows.clear()
        clockMs = 0.0
    }
}
