package com.tcodeplayer.core.funscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FunscriptJsonTest {

    @Test
    fun `parses standard funscript with unknown fields`() {
        val content = """
            {"version":"1.0","inverted":false,"metadata":{"title":"demo"},
             "actions":[{"pos":0,"at":0},{"pos":99,"at":1500}]}
        """.trimIndent()
        val actions = FunscriptJson.parse(content)!!
        assertEquals(2, actions.size)
        assertEquals(FunscriptAction(99, 1500L), actions[1])
    }

    @Test
    fun `invalid json degrades silently`() {
        assertNull(FunscriptJson.parse("not a json"))
    }

    @Test
    fun `parses merged-axis single file`() {
        val content = """
            {"version":"1.1","actions":[{"pos":77,"at":0}],
             "axes":[{"id":"L1","actions":[{"pos":50,"at":0}]},
                     {"id":"R0","actions":[{"pos":25,"at":0}]}]}
        """.trimIndent()
        val multi = FunscriptJson.parseMulti(content)!!
        assertEquals(setOf("L0", "L1", "R0"), multi.keys)
        assertEquals(FunscriptAction(77, 0L), multi["L0"]!![0])
        assertEquals(FunscriptAction(25, 0L), multi["R0"]!![0])
        // parse() 保持原有行为：只读顶层 actions
        assertEquals(1, FunscriptJson.parse(content)!!.size)
    }

    @Test
    fun `only six allowed axis ids are accepted`() {
        val content = """
            {"actions":[{"pos":10,"at":0}],
             "axes":[{"axisId":"r2","actions":[{"pos":30,"at":0}]},
                     {"id":"v0","actions":[{"pos":50,"at":0}]},
                     {"id":"stroke","actions":[{"pos":90,"at":0}]}]}
        """.trimIndent()
        val multi = FunscriptJson.parseMulti(content)!!
        // 第十五轮白名单：R2 合法，V0 与非法字符串一律跳过
        assertEquals(setOf("L0", "R2"), multi.keys)
    }

    @Test
    fun `parseMulti returns null for broken content`() {
        assertNull(FunscriptJson.parseMulti("not a json"))
    }
}
