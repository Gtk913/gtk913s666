package com.tcodeplayer.core.funscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinearFunscriptEngineTest {

    private val threeActions = listOf(
        FunscriptAction(pos = 0, at = 0L),
        FunscriptAction(pos = 50, at = 1000L),
        FunscriptAction(pos = 100, at = 2000L),
    )

    private fun engine(): LinearFunscriptEngine = LinearFunscriptEngine().apply {
        load(listOf(AxisScript("L0", threeActions)))
    }

    @Test
    fun `three actions interpolate linearly with pos over 100`() {
        val e = engine()
        assertEquals(0.0, e.sample(0L)["L0"]!!, 1e-9)
        assertEquals(0.25, e.sample(500L)["L0"]!!, 1e-9)
        assertEquals(0.50, e.sample(1000L)["L0"]!!, 1e-9)
        assertEquals(0.75, e.sample(1500L)["L0"]!!, 1e-9)
        assertEquals(0.99, e.sample(2000L)["L0"]!!, 1e-9)
    }

    @Test
    fun `before first and after last take edge values`() {
        val e = engine()
        assertEquals(0.0, e.sample(-500L)["L0"]!!, 1e-9)
        assertEquals(0.99, e.sample(99_999L)["L0"]!!, 1e-9)
    }

    @Test
    fun `out of range pos is clamped to 0_00 - 0_99`() {
        val e = LinearFunscriptEngine().apply {
            load(
                listOf(
                    AxisScript("L0", listOf(FunscriptAction(pos = -50, at = 0L))),
                    AxisScript("L1", listOf(FunscriptAction(pos = 250, at = 0L))),
                )
            )
        }
        val sample = e.sample(0L)
        assertEquals(0.0, sample["L0"]!!, 1e-9)
        assertEquals(0.99, sample["L1"]!!, 1e-9)
    }

    @Test
    fun `clear removes all axes and multiple axes sample independently`() {
        val e = LinearFunscriptEngine().apply {
            load(
                listOf(
                    AxisScript("L0", threeActions),
                    AxisScript("R2", listOf(FunscriptAction(pos = 77, at = 0L))),
                )
            )
        }
        val sample = e.sample(500L)
        assertEquals(2, sample.size)
        assertEquals(0.25, sample["L0"]!!, 1e-9)
        assertEquals(0.77, sample["R2"]!!, 1e-9)
        e.clear()
        assertTrue(e.sample(500L).isEmpty())
    }
    @Test
    fun `getMaxDurationMs 取所有轴里最后的时间戳`() {
        val e = LinearFunscriptEngine()
        assertEquals(0L, e.getMaxDurationMs())
        e.load(listOf(
            AxisScript("L0", listOf(FunscriptAction(0, 0L), FunscriptAction(99, 30000L))),
            AxisScript("L1", listOf(FunscriptAction(0, 0L), FunscriptAction(50, 12000L))),
        ))
        assertEquals(30000L, e.getMaxDurationMs())
        e.clear()
        assertEquals(0L, e.getMaxDurationMs())
    }

}
