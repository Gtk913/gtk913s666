package com.tcodeplayer.app.player

import com.tcodeplayer.app.MotionDriveConfig
import com.tcodeplayer.core.axis.AxisProcessor
import com.tcodeplayer.core.funscript.FunscriptEngine
import com.tcodeplayer.core.tcode.TCodeCommandFormatter
import com.tcodeplayer.core.tcode.TCodeOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch

/**
 * 方案 4.4 协调器（第十六轮改为外观）：接口一字不变，实际驱动委托给 MotionDriveLoop。
 *
 * 采样循环硬性顺序由 MotionDriveLoop 保证：
 *  1. 专用线程 + 绝对周期（默认 20ms / 50Hz）
 *  2. !isPlaying() → 跳过本轮，不发送（暂停不发送 DSTOP）
 *  3. positionMs <= 0 / 读取异常 → 跳过本轮
 *  4. positionMs > 脚本末尾 + 1000ms → 一次 DSTOP 并暂停采样，Seek 回来自动恢复
 *  5. raw = engine.sample()；processed = processor.process(raw, 真实 elapsedMs)
 *  6. dirty-only 过滤（默认关）+ 静默心跳 → 生成短命令（I 默认关）
 *  7. 所有轴空格拼成一行、末尾一个 \n（MFP 格式），**一次 sendNow()**，绝不丢帧
 */
class PlayerSyncController(
    private val timeProvider: PlayerTimeProvider,
    private val engine: FunscriptEngine,
    private val processor: AxisProcessor,
    private val output: TCodeOutput,
    private val scope: CoroutineScope,
    private val intervalMs: Long = 20L,
    private val onDeviceDisconnected: () -> Unit = {},
) {

    private val loop = MotionDriveLoop(
        timeProvider = timeProvider,
        engine = engine,
        processor = processor,
        outputProvider = { output },
        configProvider = { MotionDriveConfig.snapshot() },
        onDisconnected = onDeviceDisconnected,
    )

    fun start() { loop.start() }

    /** 停止循环 + 发送 DSTOP；暂停路径不得调用本方法。 */
    fun stop() {
        loop.stop()
        // NonCancellable：退出/停止时保证 DSTOP 有机会送达
        scope.launch(NonCancellable) {
            runCatching { output.send(TCodeCommandFormatter.STOP) }
        }
    }

    /** 媒体切换时调用：旧媒体 DSTOP + 清脚本；蓝牙连接保持不断开。 */
    fun onMediaChanged() {
        loop.onMediaChanged()
        scope.launch(NonCancellable) {
            runCatching { output.send(TCodeCommandFormatter.STOP) }
        }
    }

    /** Seek / 配置变化后清状态，让速度限制不拿旧帧做基准。 */
    fun resetProcessingState() { loop.resetProcessingState() }

    fun isTimedOut(): Boolean = loop.isTimedOut()

    /** ① 卡片 / ⑦ 面板读取：实测节奏、间隙与丢帧（丢帧机制已移除，恒 0）。 */
    fun stats(): MotionDriveLoop.Stats = loop.stats()
}
