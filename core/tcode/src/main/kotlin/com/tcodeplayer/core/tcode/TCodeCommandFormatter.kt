package com.tcodeplayer.core.tcode

import java.util.Locale
import kotlin.math.abs

/**
 * TCode v0.3 短命令（轴 ID + 4 位幅度）生成器。不发送 G / <>。
 *
 * 第十七轮：批量格式对齐 MultiFunPlayer 的 DeviceAxis.ToString(IEnumerable, interval)：
 *     "L07700I20 L19900I20 R02640I20\n"   ← 所有轴空格分隔、**一行、一个 \n**
 * 第十六轮及以前是 "L07700I20\nL19900I20\nR02640I20\n"（一轴一行、每行带 I）。
 * 多数 TCode 固件按 \n 分帧并对每帧的 I 排队，6 行会被当成 6 个依次执行的动作，
 * 主机 20ms 一批而设备要 6×I 才能消化 → 队列积压 → 「动几下停一会」。
 *
 * 兼容保留：buildBatch(sample) 仍返回旧的一轴一行格式（手动调试 / 历史基线 /
 * C17 自检用）；驱动主链路显式传 singleLine=true。
 */
object TCodeCommandFormatter {

    const val STOP = "DSTOP\n"

    /** 第十五轮：设备实际只有这 6 个轴，其它 ID 一律不发指令。 */
    val ALLOWED_AXES: Set<String> = setOf("L0", "L1", "L2", "R0", "R1", "R2")

    fun format(axisId: String, normalized: Double): String? {
        if (axisId.length < 2) return null
        if (axisId.uppercase() !in ALLOWED_AXES) return null
        val type = axisId[0].uppercaseChar()
        val channel = axisId.substring(1)
        if (channel.isEmpty() || !channel.all { it.isDigit() }) return null
        val value = normalized.coerceIn(0.0, 0.99)
        val magnitude = String.format(Locale.ROOT, "%04d", (value * 10000).toInt())
        return "$type$channel$magnitude"
    }

    /** 旧格式：一轴一行、无 I。手动调试与已验收的历史基线用。 */
    fun buildBatch(sample: Map<String, Double>): String? = buildBatch(sample, null, false)

    /**
     * 驱动主链路。
     * @param intervalMs 非 null → 每条命令追加 I<真实帧间隔>（MFP: elapsed*1000，不加余量）。
     * @param singleLine true（默认）→ MFP 单行空格拼接；false → 旧的一轴一行。
     */
    fun buildBatch(sample: Map<String, Double>, intervalMs: Long?): String? =
        buildBatch(sample, intervalMs, true)

    fun buildBatch(sample: Map<String, Double>, intervalMs: Long?, singleLine: Boolean): String? {
        if (sample.isEmpty()) return null
        val suffix = intervalMs?.let { "I${it.coerceIn(1L, 1000L)}" } ?: ""
        val commands = sample.entries.mapNotNull { (axisId, value) ->
            val base = format(axisId, value) ?: return@mapNotNull null
            base + suffix
        }
        if (commands.isEmpty()) return null
        return if (singleLine) {
            commands.joinToString(" ") + "\n"
        } else {
            commands.joinToString("\n") + "\n"
        }
    }

    /** MFP DeviceAxis.IsValueDirty：幅度至少变化 1 个输出单位（四位幅度下的 1）。 */
    fun isDirty(value: Double, last: Double): Boolean =
        abs(last - value) * 10000.0 >= 1.0 || (value.isFinite() != last.isFinite())

    /** 按 dirtyOnly 过滤；返回真正要发送的轴值，供驱动层更新 lastSent。 */
    fun filterDirty(
        sample: Map<String, Double>,
        lastSent: Map<String, Double>,
        dirtyOnly: Boolean,
    ): Map<String, Double> {
        if (!dirtyOnly) return sample
        return sample.filter { (axisId, value) ->
            val prev = lastSent[axisId] ?: return@filter true
            isDirty(value, prev)
        }
    }
}
