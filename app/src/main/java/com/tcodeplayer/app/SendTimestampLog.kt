package com.tcodeplayer.app

import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * V50（第十四轮补做）：APP 侧 send() 时间戳记录与导出。
 *
 * 之前几轮一直用 PC 端 XCOM 的时间戳判 50Hz，但那是**接收缓冲的分组时间**
 * （15.6ms 的整数倍），一组里 1~10 批指令没有逐行时刻，数学上算不出「条/秒」。
 * 这里在调用 output.send() 之前打点，记录的是 APP 侧真实的发送节奏。
 *
 * 与 50Hz 链路完全解耦：只做无锁开销极小的环形缓冲记录，不参与插值、不抛异常、
 * 不影响 send 的返回值和超时丢帧逻辑。导出为 CSV 后可直接粘给判读脚本。
 */
object SendTimestampLog {

    data class Entry(
        val seq: Long,
        val wallMs: Long,
        val elapsedMs: Long,
        val kind: String, // batch=50Hz 批次 / manual=手动指令
        val batch: String,
    )

    private const val CAPACITY = 20_000 // 50Hz 下约 6.7 分钟，足够覆盖 2 分钟脚本
    private val lock = Any()
    private val ring = ArrayDeque<Entry>(CAPACITY)
    private var seq: Long = 0L

    @Volatile
    var enabled: Boolean = true

    fun record(batch: String, kind: String = "batch") {
        if (!enabled) return
        synchronized(lock) {
            if (ring.size >= CAPACITY) ring.removeFirst()
            ring.addLast(
                Entry(
                    seq = ++seq,
                    wallMs = System.currentTimeMillis(),
                    elapsedMs = SystemClock.elapsedRealtime(),
                    kind = kind,
                    batch = batch,
                )
            )
        }
    }

    fun clear() {
        synchronized(lock) {
            ring.clear()
            seq = 0L
        }
    }

    fun snapshot(): List<Entry> = synchronized(lock) { ring.toList() }

    /** 只统计 50Hz 批次，手动指令不计入速率。 */
    fun stats(): String {
        val batches = snapshot().filter { it.kind == "batch" }
        if (batches.size < 2) return "样本不足（${batches.size} 批）：播放一个带脚本的媒体后再看"
        val span = batches.last().elapsedMs - batches.first().elapsedMs
        if (span <= 0) return "跨度异常，无法计算"
        val hz = batches.size * 1000.0 / span
        val deltas = batches.zipWithNext { a, b -> b.elapsedMs - a.elapsedMs }
        val avg = deltas.average()
        val max = deltas.maxOrNull() ?: 0L
        return "批次=${batches.size} · 跨度=${span}ms · 实测=${"%.2f".format(hz)} 批/秒 · " +
            "相邻间隔 avg=${"%.1f".format(avg)}ms / max=${max}ms"
    }

    /** 最近若干条，用于面板预览。 */
    fun preview(n: Int = 5): List<String> = synchronized(lock) {
        ring.takeLast(n).map { e ->
            val t = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(e.wallMs))
            "$t  #${e.seq}  ${e.kind}  ${e.batch.replace("\n", "|").trim()}"
        }
    }

    /**
     * CSV：seq, wall_clock, elapsed_ms, delta_ms, kind, batch
     * batch 里的换行替换为 | 并用双引号包裹，避免破坏 CSV 行结构。
     * delta_ms 只对连续 batch 有意义，手动指令与首条留空。
     */
    fun exportCsv(): String {
        val rows = snapshot()
        val sb = StringBuilder("seq,wall_clock,elapsed_ms,delta_ms,kind,batch\n")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        var prevBatchElapsed: Long? = null
        for (e in rows) {
            val delta = if (e.kind == "batch" && prevBatchElapsed != null)
                (e.elapsedMs - prevBatchElapsed!!).toString() else ""
            if (e.kind == "batch") prevBatchElapsed = e.elapsedMs
            val payload = e.batch.replace("\"", "\"\"").replace("\n", "|").replace("\r", "")
            sb.append(e.seq).append(',')
                .append(fmt.format(Date(e.wallMs))).append(',')
                .append(e.elapsedMs).append(',')
                .append(delta).append(',')
                .append(e.kind).append(',')
                .append('"').append(payload).append('"').append('\n')
        }
        return sb.toString()
    }
}
