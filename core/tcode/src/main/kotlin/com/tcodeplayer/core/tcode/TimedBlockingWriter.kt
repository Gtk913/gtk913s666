package com.tcodeplayer.core.tcode

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** 一次写入的结果。丢帧是正常结果，绝不抛给 PlayerSyncController。 */
enum class WriteOutcome {
    OK,
    /** 上一帧的阻塞写还没结束，本帧直接丢弃，不排队堆积。 */
    DROPPED_BUSY,
    /** 本帧写超过 timeoutMs，丢弃；写线程继续跑完，不中断。 */
    DROPPED_TIMEOUT,
    /** 写线程真的抛异常、或连续丢帧超过阈值（视为断连），由调用方走原有异常路径。 */
    FAILED,
}

/**
 * 给**阻塞式 IO** 用的真超时写器。
 *
 * 为什么不用 `withTimeout`：BluetoothSocket.outputStream.write() 是同步阻塞调用，
 * 协程取消不会中断已经卡在 write 上的线程；`withTimeout` 只能取消挂起点，
 * 对没有挂起点的同步写无效，而且 Mutex 会一直被占住，后续 50Hz 帧全部排队卡死。
 *
 * 这里把写放到独立 daemon 线程，调用方用 Future.get(timeout) 等待：
 *  - 正常完成 → busy 由写线程 finally 清除
 *  - 超时 → **不 cancel**。cancel(true) 对尚未启动的任务会把它从队列移除，
 *    Callable 的 finally 永不执行，busy 永久为 true，之后每一帧都 DROPPED_BUSY
 *    —— 这正是「设备主机只动了几下」的真因（第十五轮修复）。
 *    不取消则写线程一定会执行完并在 finally 里清 busy，短暂丢帧后自动恢复。
 *  - 连续丢帧超过阈值 → 视为真断连，返回 FAILED，让调用方发 DSTOP 并提示断连，
 *    避免写线程永久卡死时永远静默丢帧。
 *
 * 本类只用 java.util.concurrent，不 import 任何 Android / UI / mpv 类。
 * 应在 Dispatchers.IO 上调用 submit()；生命周期与 output 单例一致，不主动 shutdown。
 */
class TimedBlockingWriter(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    threadName: String = "tcode-spp-writer",
) {
    companion object {
        /** 2.2 方案硬性约束：send() 超时 ≤100ms，超时直接丢帧。 */
        const val DEFAULT_TIMEOUT_MS = 100L
        /** 50Hz 下约 1 秒；超过则判链路异常，不再静默丢帧。 */
        const val MAX_CONSECUTIVE_DROPS = 50
    }

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, threadName).apply { isDaemon = true }
    }

    private val busy = AtomicBoolean(false)
    private val consecutiveDrops = AtomicInteger(0)
    private val droppedBusy = AtomicLong(0L)
    private val droppedTimeout = AtomicLong(0L)
    private val failed = AtomicLong(0L)

    fun droppedBusyCount(): Long = droppedBusy.get()
    fun droppedTimeoutCount(): Long = droppedTimeout.get()
    fun failedCount(): Long = failed.get()

    /** 连接/断开时清状态，避免上一次连接的残留 busy 影响新连接。 */
    fun reset() {
        busy.set(false)
        consecutiveDrops.set(0)
        droppedBusy.set(0)
        droppedTimeout.set(0)
        failed.set(0)
    }

    fun submit(block: () -> Unit): WriteOutcome {
        if (!busy.compareAndSet(false, true)) {
            droppedBusy.incrementAndGet()
            return noteDrop(WriteOutcome.DROPPED_BUSY)
        }

        val future: Future<*> = try {
            executor.submit(Callable {
                try {
                    block()
                } finally {
                    busy.set(false)
                }
            })
        } catch (e: Exception) {
            busy.set(false)
            failed.incrementAndGet()
            consecutiveDrops.set(0)
            return WriteOutcome.FAILED
        }

        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
            consecutiveDrops.set(0)
            WriteOutcome.OK
        } catch (e: TimeoutException) {
            droppedTimeout.incrementAndGet()
            // 第十五轮：绝不 cancel(true)。让写线程自然跑完并清 busy。
            noteDrop(WriteOutcome.DROPPED_TIMEOUT)
        } catch (e: Exception) {
            // Future 已完成（含异常），finally 应已清 busy，这里双保险
            busy.set(false)
            failed.incrementAndGet()
            consecutiveDrops.set(0)
            WriteOutcome.FAILED
        }
    }

    private fun noteDrop(outcome: WriteOutcome): WriteOutcome =
        if (consecutiveDrops.incrementAndGet() >= MAX_CONSECUTIVE_DROPS) {
            busy.set(false)
            WriteOutcome.FAILED
        } else {
            outcome
        }
}
