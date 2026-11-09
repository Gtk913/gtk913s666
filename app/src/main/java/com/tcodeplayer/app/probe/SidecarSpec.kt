package com.tcodeplayer.app.probe

/** 第 5 节文件探测规则（精确）。纯命名规则，不含 IO。 */
object SidecarSpec {

    val SUBTITLE_EXTENSIONS = listOf("srt", "ass", "ssa", "vtt")

    private const val SCRIPT_EXT = ".funscript"
    /** 第十五轮白名单：仅 L0/L1/L2/R0/R1/R2 六个轴。V0 等不再识别。 */
    val ALLOWED_AXES: Set<String> = setOf("L0", "L1", "L2", "R0", "R1", "R2")
    private val AXIS_ID = Regex("""[LR][0-2]""", RegexOption.IGNORE_CASE)

    fun baseName(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    /** NAME.funscript → 默认主轴 L0（v0.3）。 */
    fun mainScript(base: String): String = base + SCRIPT_EXT

    /** NAME.<轴ID>.funscript，轴 ID 仅 L0/L1/L2/R0/R1/R2。 */
    fun axisScript(base: String, axisId: String): String = "$base.$axisId$SCRIPT_EXT"

    /** 优先级：srt > ass > ssa > vtt，选第一个存在的。 */
    fun subtitleCandidates(base: String): List<String> =
        SUBTITLE_EXTENSIONS.map { "$base.$it" }

    fun lyrics(base: String): String = "$base.lrc"

    /**
     * 从 NAME.<轴ID>.funscript 提取轴 ID。
     * Q01：**主名（base）永远严格区分大小写**；`extCaseSensitive` 只控制扩展名。
     * 轴 ID 本身仍允许 l1/r0 这类小写（AXIS_ID 带 IGNORE_CASE），不属于主名。
     * 第十五轮：轴 ID 白名单仅 6 个 L0/L1/L2/R0/R1/R2（设备实际轴），V0/A0 等一律不处理。
     * @param extCaseSensitive true = 扩展名严格；false = 扩展名忽略大小写。
     */
    fun axisIdOrNull(fileName: String, base: String, extCaseSensitive: Boolean): String? {
        val prefix = "$base."
        if (!fileName.startsWith(prefix)) return null

        val matchesSuffix = if (extCaseSensitive) {
            fileName.endsWith(SCRIPT_EXT)
        } else {
            fileName.endsWith(SCRIPT_EXT, ignoreCase = true)
        }
        if (!matchesSuffix) return null

        val end = fileName.length - SCRIPT_EXT.length
        if (end <= prefix.length) return null       // 排除 NAME.funscript 主脚本
        val id = fileName.substring(prefix.length, end)
        return id.takeIf { AXIS_ID.matches(it) }?.uppercase()
    }
}
