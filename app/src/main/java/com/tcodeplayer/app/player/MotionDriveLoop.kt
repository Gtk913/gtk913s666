package com.tcodeplayer.app.player

import android.os.Process
import com.tcodeplayer.app.MotionDriveConfig
import com.tcodeplayer.app.SendTimestampLog
import com.tcodeplayer.core.axis.AxisProcessor
import com.tcodeplayer.core.funscript.FunscriptEngine
import com.tcodeplayer.core.tcode.TCodeCommandFormatter
import com.tcodeplayer.core.tcode.TCodeOutput
import java.util.concurrent.locks.LockSupport

/**
 * 专用高优先级线程 + 绝对周期时钟（第十六轮引入，第十七轮修正）。
 *
 * 第十七轮相对第十六轮的 4 处改动：
 *  1. 批量走 MFP 单行空格格式（cfg.singleLine），不再一轴一行
 *  2. I 值用真实 elapsedMs，删掉 +4ms 余量（余量会让设备每帧慢一点、累积滞后）
 *  3. 静默心跳：dirty-only 把整帧过滤掉且超过 heartbeatMs → 强制补一帧全量
 *  4. Stats 暴露「实际发出的第一行样例 / 心跳数 / 静默帧数」，一次截图即可判读
 *
 * 不变：专用线程、绝对周期（parkNanos）、不丢帧、脚本末尾 +1s DSTOP、
 *       暂停不发也不发 DSTOP、只有 IOException 才判断连。
 */
class MotionDriveLoop(
    private val timeProvider: PlayerTimeProvider,
    private val engine: FunscriptEngine,
    private val processor: AxisProcessor,
    private val outputProvider: () -> TCodeOutput,
    private val configProvider: () -> MotionDriveConfig.Snapshot,
    private val onDisconnected: () -> Unit = {},
) {
    data class Stats(
        val batches: Long = 0L,
        val hz: Double = 0.0,
        val maxGapMs: Long = 0L,
        val gapsOver60ms: Long = 0L,
        val writeFailures: Long = 0L,
        val maxWriteMs: Long = 0L,
        val intervalEnabled: Boolean = true,
        val dirtyOnly: Boolean = true,
        val intervalMs: Long = 20L,
        val singleLine: Boolean = true,
        val lastFrame: String = "",
        val heartbeats: Long = 0L,
        val silentTicks: Long = 0L,
        val threadName: String = "",
    )

    /** 绝对周期算法的纯函数部分，供 C28 自检与 JVM 单测验证。 */
    object ClockMath {
        fun nextDeadline(prevDeadlineNs: Long, nowNs: Long, intervalNs: Long): Long {
            val next = prevDeadlineNs + intervalNs
            return if (nowNs - next > 2 * intervalNs) nowNs + intervalNs else next
        }
    }

    @Volatile private var running = false
    @Volatile private var thread: Thread? = null
    @Volatile private var timedOut = false
    @Volatile private var statsValue = Stats()

    private val lastSent = HashMap<String, Double>()
    private var lastTickNano = 0L
    private var lastSendNano = 0L
    private var windowStartNano = 0L
    private var windowBatches = 0L
    private var maxGapMs = 0L
    private var gapsOver60 = 0L
    private var writeFailures = 0L
    private var maxWriteMs = 0L
    private var totalBatches = 0L
    private var heartbeats = 0L
    private var silentTicks = 0L
    private var lastFrame = ""

    fun start() {
        if (running) return
        running = true
        timedOut = false
        synchronized(this) {
            lastSent.clear()
            lastTickNano = 0L
            lastSendNano = System.nanoTime()
            windowStartNano = System.nanoTime()
            windowBatches = 0L
            maxGapMs = 0L
            gapsOver60 = 0L
            writeFailures = 0L
            maxWriteMs = 0L
            totalBatches = 0L
            heartbeats = 0L
            silentTicks = 0L
            lastFrame = ""
        }
        thread = Thread({ loop() }, "tcode-motion-clock").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.let { LockSupport.unpark(it) }
        thread = null
        timedOut = false
    }

    fun isTimedOut(): Boolean = timedOut
    fun stats(): Stats = statsValue

    fun resetProcessingState() {
        processor.resetState()
        timedOut = false
        synchronized(this) { lastSent.clear() }
    }

    fun onMediaChanged() {
        stop()
        engine.clear()
        processor.resetState()
        synchronized(this) { lastSent.clear() }
    }

    private fun loop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (e: Throwable) {
            // JVM 单测 / 非 Android 环境忽略
        }
        var cfg = configProvider()
        var intervalNs = cfg.intervalMs * 1_000_000L
        var nextTick = System.nanoTime() + intervalNs

        while (running) {
            val sleepNs = nextTick - System.nanoTime()
            if (sleepNs > 0) LockSupport.parkNanos(sleepNs)
            if (!running) break

            var tickNow = System.nanoTime()
            if (tickNow - nextTick > 2 * intervalNs) {
                nextTick = tickNow
                windowStartNano = tickNow
            }
            val elapsedMs = if (lastTickNano == 0L) cfg.intervalMs
            else ((tickNow - lastTickNano) / 1_000_000L).coerceIn(1L, 1000L)
            lastTickNano = tickNow

            tick(elapsedMs, cfg)

            nextTick = ClockMath.nextDeadline(nextTick, System.nanoTime(), intervalNs)
            val newCfg = configProvider()
            if (newCfg.intervalMs != cfg.intervalMs) {
                intervalNs = newCfg.intervalMs * 1_000_000L
                nextTick = System.nanoTime() + intervalNs
            }
            cfg = newCfg
            publishStats(cfg)
        }
    }

    private fun tick(elapsedMs: Long, cfg: MotionDriveConfig.Snapshot) {
        // 1) 暂停 / 停止 → 跳过本轮，不发送、不发 DSTOP。
        //    第十七轮：MpvTimeProvider 已放宽判定，不再因 mpv 瞬时 core-idle 整帧跳过。
        if (!timeProvider.isPlaying()) return

        // 2) mpv 属性读取异常 / positionMs <= 0 → 本轮跳过
        val positionMs = try {
            timeProvider.currentPositionMs()
        } catch (e: Exception) {
            return
        }
        if (positionMs <= 0L) return

        // 3) 脚本末尾 +1000ms 超时：一次 DSTOP，然后暂停采样（方案 4.4）
        val maxDuration = try {
            engine.getMaxDurationMs()
        } catch (e: Exception) {
            0L
        }
        if (maxDuration > 0L && positionMs > maxDuration + 1000L) {
            if (!timedOut) {
                timedOut = true
                runCatching { outputProvider().sendNow(TCodeCommandFormatter.STOP) }
            }
            return
        }
        if (timedOut) {
            timedOut = false
            processor.resetState()
            synchronized(this) { lastSent.clear() }
        }

        // 4) 采样 → 处理管线（默认恒等，数值基线不变）
        val raw = try {
            engine.sample(positionMs)
        } catch (e: Exception) {
            return
        }
        if (raw.isEmpty()) return

        val processed = try {
            processor.process(raw, elapsedMs)
        } catch (e: Exception) {
            return
        }

        // 5) dirty-only 过滤 + 静默心跳
        val snapshot = synchronized(this) { HashMap(lastSent) }
        var payload = TCodeCommandFormatter.filterDirty(processed, snapshot, cfg.dirtyOnly)
        val nowNano = System.nanoTime()
        if (payload.isEmpty()) {
            val silentForMs = (nowNano - lastSendNano) / 1_000_000L
            if (cfg.heartbeatMs > 0L && silentForMs >= cfg.heartbeatMs && processed.isNotEmpty()) {
                payload = processed
                heartbeats++
            } else {
                silentTicks++
                return
            }
        }

        // 6) 生成指令：MFP 单行空格批量；I 用真实帧间隔（不加余量）
        val batch = TCodeCommandFormatter.buildBatch(
            payload,
            if (cfg.intervalEnabled) elapsedMs else null,
            cfg.singleLine,
        ) ?: return

        // 7) 一次写出。绝不丢帧：写慢只是本帧晚到
        try {
            SendTimestampLog.record(batch, "batch")
            val writeStart = System.nanoTime()
            outputProvider().sendNow(batch)
            val writeMs = (System.nanoTime() - writeStart) / 1_000_000L
            if (writeMs > maxWriteMs) maxWriteMs = writeMs
            synchronized(this) { lastSent.putAll(payload) }
            lastSendNano = System.nanoTime()
            lastFrame = batch.lineSequence().firstOrNull().orEmpty().take(160)
            totalBatches++
            windowBatches++
            if (elapsedMs > maxGapMs) maxGapMs = elapsedMs
            if (elapsedMs > 60L) gapsOver60++
        } catch (e: Exception) {
            writeFailures++
            running = false
            runCatching { outputProvider().sendNow(TCodeCommandFormatter.STOP) }
            onDisconnected()
        }
    }

    private fun publishStats(cfg: MotionDriveConfig.Snapshot) {
        val now = System.nanoTime()
        val span = now - windowStartNano
        val hz = if (span >= 1_000_000_000L) {
            val value = windowBatches * 1_000_000_000.0 / span
            windowStartNano = now
            windowBatches = 0L
            maxGapMs = 0L
            gapsOver60 = 0L
            value
        } else statsValue.hz
        statsValue = Stats(
            batches = totalBatches,
            hz = hz,
            maxGapMs = maxGapMs,
            gapsOver60ms = gapsOver60,
            writeFailures = writeFailures,
            maxWriteMs = maxWriteMs,
            intervalEnabled = cfg.intervalEnabled,
            dirtyOnly = cfg.dirtyOnly,
            intervalMs = cfg.intervalMs,
            singleLine = cfg.singleLine,
            lastFrame = lastFrame,
            heartbeats = heartbeats,
            silentTicks = silentTicks,
            threadName = thread?.name ?: "",
        )
    }
}
