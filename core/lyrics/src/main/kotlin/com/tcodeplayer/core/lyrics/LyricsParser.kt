package com.tcodeplayer.core.lyrics

/** 纯 Kotlin 模块：不得 import mpv / mpvEx / Android UI 类。 */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * 标准 LRC：[mm:ss.xx]text，xx 为百分秒（*10ms）。
 * 无时间戳行忽略；空行忽略；元数据标签（[ar:..] 等）不匹配、自动忽略。
 * 兼容 [mm:ss:xx] / [mm:ss.xxx] / [mm:ss]，以及同一行多个时间戳。
 * 结果按 timeMs 升序。
 */
object LyricsParser {

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")

    fun parse(lrcContent: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        for (raw in lrcContent.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val tags = TIME_TAG.findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = TIME_TAG.replace(line, "").trim()
            for (tag in tags) {
                result += LyricLine(timeMsOf(tag), text)
            }
        }
        return result.sortedBy { it.timeMs }
    }

    private fun timeMsOf(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fractionRaw = match.groupValues[3]
        val millis = when {
            fractionRaw.isEmpty() -> 0L
            fractionRaw.length == 1 -> fractionRaw.toLong() * 100L
            fractionRaw.length == 2 -> fractionRaw.toLong() * 10L
            else -> fractionRaw.take(3).padEnd(3, '0').toLong()
        }
        return minutes * 60_000L + seconds * 1_000L + millis
    }
}
