package com.tcodeplayer.core.axis

/**
 * 单轴处理配置（方案 5.2）。
 *
 * 关于 maxSpeedPerSec 默认值的重要说明：
 *  方案示例写的是 2.0，本实现默认 0.0 = **不限速**。
 *  原因：工程已有三份真机串口日志（本地 / 分轴 / 合并轴）是已验收的数值基线，
 *  若默认启用 2.0，50Hz 下 maxDelta=0.04 会大幅削平脚本里的快速段，
 *  让既有基线全部作废。功能完整实现，由用户在运动控制面板里按需开启。
 *  把滑块调到 >0 即生效，自检 C23 用显式配置验证。
 */
enum class InterpolationMode { LINEAR, PCHIP, MAKIMA }

data class AxisConfig(
    val axisId: String,
    val isInverted: Boolean = false,
    val minLimit: Double = 0.0,
    val maxLimit: Double = 0.99,
    /** 每秒最大行程变化；0 = 不启用速度限制。50Hz 下 maxDelta = maxSpeedPerSec * 0.02。 */
    val maxSpeedPerSec: Double = 0.0,
    val interpolation: InterpolationMode = InterpolationMode.LINEAR,
) {
    init {
        require(minLimit in 0.0..0.99) { "minLimit 必须在 [0, 0.99]" }
        require(maxLimit in 0.0..0.99) { "maxLimit 必须在 [0, 0.99]" }
        require(maxLimit >= minLimit) { "maxLimit 必须 >= minLimit" }
        require(maxSpeedPerSec >= 0.0) { "maxSpeedPerSec 不能为负" }
    }

    fun limits(): ClosedFloatingPointRange<Double> = minLimit..maxLimit
}
