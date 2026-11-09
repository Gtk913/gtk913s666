package com.tcodeplayer.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {

    @Test
    fun `parses standard mm ss xx tags into milliseconds`() {
        val lines = LyricsParser.parse(
            """
            [ar:Artist]
            [00:12.34]First line
            [01:05.07]Second line
            """.trimIndent()
        )
        assertEquals(2, lines.size)
        assertEquals(LyricLine(12_340L, "First line"), lines[0])
        assertEquals(LyricLine(65_070L, "Second line"), lines[1])
    }

    @Test
    fun `ignores blank lines and untimed lines`() {
        val lines = LyricsParser.parse("\n\nplain text without tag\n[00:01.00]ok\n")
        assertEquals(listOf(LyricLine(1_000L, "ok")), lines)
    }

    @Test
    fun `multiple tags on one line and sorted ascending`() {
        val lines = LyricsParser.parse("[00:10.50][00:02.25]chorus\n[00:01.00]intro\n")
        assertEquals(3, lines.size)
        assertEquals(listOf(1_000L, 2_250L, 10_500L), lines.map { it.timeMs })
        assertTrue(lines.all { it.text.isNotEmpty() })
    }

    @Test
    fun `millisecond and colon variants`() {
        val lines = LyricsParser.parse("[00:01:50]a\n[00:02.500]b\n[00:03]c\n")
        assertEquals(listOf(1_500L, 2_500L, 3_000L), lines.map { it.timeMs })
    }

    @Test
    fun `empty content is safe`() {
        assertTrue(LyricsParser.parse("").isEmpty())
    }
}
