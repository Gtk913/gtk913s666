package com.tcodeplayer.core.axis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AxisProcessorTest {

    private fun near(a: Double, b: Double, eps: Double = 1e-9) =
        assertTrue(kotlin.math.abs(a - b) < eps, "期望 $b，实际 $a")

    @Test
    fun `默认配置是恒等变换 不改变既有数值基线`() {
        val p = AxisProcessor()
        val raw = mapOf("L0" to 0.7700, "L1" to 0.1234, "R0" to 0.99)
        val out = p.process(raw)
        assertEquals(raw.keys, out.keys)
        near(out["L0"]!!, 0.7700)
        near(out["L1"]!!, 0.1234)
        near(out["R0"]!!, 0.99)
    }

    @Test
    fun `反转 0_99 减去原值`() {
        val p = AxisProcessor()
        p.setConfig(AxisConfig("L0", isInverted = true))
        near(p.process(mapOf("L0" to 0.25))["L0"]!!, 0.74)
    }

    @Test
    fun `行程限幅 coerceIn`() {
        val p = AxisProcessor()
        p.setConfig(AxisConfig("L0", minLimit = 0.20, maxLimit = 0.60))
        near(p.process(mapOf("L0" to 0.0))["L0"]!!, 0.20)
        near(p.process(mapOf("L0" to 0.99))["L0"]!!, 0.60)
        near(p.process(mapOf("L0" to 0.40))["L0"]!!, 0.40)
    }

    @Test
    fun `速度限制按 maxSpeedPerSec 乘帧间隔截断`() {
        val p = AxisProcessor()
        // 2.0 /秒，20ms → maxDelta = 0.04
        p.setConfig(AxisConfig("L0", maxSpeedPerSec = 2.0))
        near(p.process(mapOf("L0" to 0.0), 20)["L0"]!!, 0.0)
        near(p.process(mapOf("L0" to 0.50), 20)["L0"]!!, 0.04)
        near(p.process(mapOf("L0" to 0.50), 20)["L0"]!!, 0.08)
        // 1 秒间隔 → maxDelta = 2.0（大于行程，不截断）
        p.resetState()
        near(p.process(mapOf("L0" to 0.99), 1000)["L0"]!!, 0.99)
    }

    @Test
    fun `速度限制对负方向同样生效`() {
        val p = AxisProcessor()
        p.setConfig(AxisConfig("R0", maxSpeedPerSec = 1.0))
        p.process(mapOf("R0" to 0.80), 20)
        near(p.process(mapOf("R0" to 0.10), 20)["R0"]!!, 0.78)
    }

    @Test
    fun `处理顺序 反转后限幅再限速`() {
        val p = AxisProcessor()
        p.setConfig(AxisConfig("L0", isInverted = true, minLimit = 0.30, maxLimit = 0.70, maxSpeedPerSec = 1.0))
        // 原 0.0 → 反转 0.99 → 限幅 0.70 → 首帧无限速基准
        near(p.process(mapOf("L0" to 0.0), 20)["L0"]!!, 0.70)
        // 原 0.99 → 反转 0.0 → 限幅 0.30 → 限速 maxDelta=0.02 → 0.68
        near(p.process(mapOf("L0" to 0.99), 20)["L0"]!!, 0.68)
    }

    @Test
    fun `resetState 清空 lastVal 缓存`() {
        val p = AxisProcessor()
        p.setConfig(AxisConfig("L0", maxSpeedPerSec = 1.0))
        p.process(mapOf("L0" to 0.0), 20)
        p.resetState()
        near(p.process(mapOf("L0" to 0.90), 20)["L0"]!!, 0.90)
    }

    @Test
    fun `configs 是 StateFlow 且可批量设置`() {
        val p = AxisProcessor()
        p.setConfigs(listOf(AxisConfig("L0", isInverted = true), AxisConfig("R0")))
        assertEquals(setOf("L0", "R0"), p.configs.value.keys)
        assertTrue(p.configs.value["L0"]!!.isInverted)
    }

    @Test
    fun `非法配置直接拒绝`() {
        assertFailsWith<IllegalArgumentException> { AxisConfig("L0", minLimit = 0.8, maxLimit = 0.2) }
        assertFailsWith<IllegalArgumentException> { AxisConfig("L0", maxSpeedPerSec = -1.0) }
    }

    @Test
    fun `PCHIP 保持单调且不过冲`() {
        val pts = listOf(
            Interpolation.Point(0.0, 0.0),
            Interpolation.Point(1000.0, 0.50),
            Interpolation.Point(2000.0, 0.50),
            Interpolation.Point(3000.0, 0.99),
        )
        val c = Interpolation.build(pts, InterpolationMode.PCHIP)
        var prev = -1.0
        for (t in 0..3000 step 50) {
            val v = c.at(t.toDouble())
            assertTrue(v >= prev - 1e-9, "PCHIP 在 $t 处非单调")
            assertTrue(v in 0.0..0.99, "PCHIP 在 $t 处过冲: $v")
            prev = v
        }
    }

    @Test
    fun `MAKIMA 与 LINEAR 在两点上退化为线性`() {
        val pts = listOf(Interpolation.Point(0.0, 0.0), Interpolation.Point(1000.0, 0.99))
        for (m in listOf(InterpolationMode.LINEAR, InterpolationMode.PCHIP, InterpolationMode.MAKIMA)) {
            near(Interpolation.build(pts, m).at(500.0), 0.495)
        }
    }
}
