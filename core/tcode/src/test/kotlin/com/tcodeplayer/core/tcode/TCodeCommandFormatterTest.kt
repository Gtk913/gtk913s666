package com.tcodeplayer.core.tcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TCodeCommandFormatterTest {

    @Test
    fun `L0 with 0_77 is L07700`() {
        assertEquals("L07700", TCodeCommandFormatter.format("L0", 0.77))
    }

    @Test
    fun `magnitude is fixed 4 digits and clamped`() {
        assertEquals("L00000", TCodeCommandFormatter.format("L0", 0.0))
        assertEquals("L09900", TCodeCommandFormatter.format("L0", 0.99))
        assertEquals("L09900", TCodeCommandFormatter.format("L0", 1.5))
        assertEquals("L00000", TCodeCommandFormatter.format("L0", -1.0))
        assertEquals("R21739", TCodeCommandFormatter.format("R2", 0.1739))
        assertNull(TCodeCommandFormatter.format("V0", 0.1739))
        assertNull(TCodeCommandFormatter.format("A0", 0.1739))
    }

    @Test
    fun `batch joins with newlines and ends with newline`() {
        val batch = TCodeCommandFormatter.buildBatch(linkedMapOf("L0" to 0.77, "R0" to 0.9))!!
        assertEquals("L07700\nR09000\n", batch)
    }

    @Test
    fun `invalid axes and empty sample produce no command`() {
        assertNull(TCodeCommandFormatter.format("X0", 0.5))
        assertNull(TCodeCommandFormatter.format("L", 0.5))
        assertNull(TCodeCommandFormatter.buildBatch(emptyMap()))
        assertEquals("DSTOP\n", TCodeCommandFormatter.STOP)
    }

    @Test
    fun `MFP style I interval uses real frame elapsed`() {
        // 第十七轮：多轴必须空格拼成一行（MFP DeviceAxis.ToString(values, interval)）
        assertEquals(
            "L07700I20 R09000I20\n",
            TCodeCommandFormatter.buildBatch(linkedMapOf("L0" to 0.77, "R0" to 0.9), 20L)!!,
        )
        assertEquals(
            "L07700I35\n",
            TCodeCommandFormatter.buildBatch(linkedMapOf("L0" to 0.77), 35L)!!,
        )
        assertEquals(
            "L07700I1\n",
            TCodeCommandFormatter.buildBatch(linkedMapOf("L0" to 0.77), 0L)!!,
        )
        assertEquals(
            "L07700\n",
            TCodeCommandFormatter.buildBatch(linkedMapOf("L0" to 0.77))!!,
        )
    }

    @Test
    fun `single line format can be switched back to legacy one axis per line`() {
        val sample = linkedMapOf("L0" to 0.77, "L1" to 0.9, "R0" to 0.1739)
        assertEquals(
            "L07700 L19000 R01739\n",
            TCodeCommandFormatter.buildBatch(sample, null, true)!!,
        )
        assertEquals(
            "L07700\nL19000\nR01739\n",
            TCodeCommandFormatter.buildBatch(sample, null, false)!!,
        )
        assertEquals(
            "L07700I35 L19000I35\n",
            TCodeCommandFormatter.buildBatch(linkedMapOf("L0" to 0.77, "L1" to 0.9), 35L)!!,
        )
    }

    @Test
    fun `MFP IsValueDirty threshold is one output unit`() {
        assertFalse(TCodeCommandFormatter.isDirty(0.77000, 0.77005))
        assertTrue(TCodeCommandFormatter.isDirty(0.77020, 0.77000))
        assertTrue(TCodeCommandFormatter.isDirty(0.5, Double.NaN))
    }

    @Test
    fun `filterDirty only keeps changed axes`() {
        val sample = linkedMapOf("L0" to 0.50, "R0" to 0.50)
        val last = mapOf("L0" to 0.50, "R0" to 0.40)
        val dirty = TCodeCommandFormatter.filterDirty(sample, last, true)
        assertEquals(listOf("R0"), dirty.keys.toList())
        assertEquals(2, TCodeCommandFormatter.filterDirty(sample, last, false).size)
    }
}
