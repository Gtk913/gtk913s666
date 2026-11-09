package com.tcodeplayer.core.funscript

/** pos 0..99（原始脚本值），at 毫秒。所有时间戳单位统一为毫秒(Long)。 */
data class FunscriptAction(val pos: Int, val at: Long)

/** axisId 仅 L0/L1/L2/R0/R1/R2 六个，与设备实际轴一致。 */
data class AxisScript(val axisId: String, val actions: List<FunscriptAction>)
