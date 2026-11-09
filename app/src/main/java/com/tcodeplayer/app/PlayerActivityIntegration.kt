package com.tcodeplayer.app

import com.tcodeplayer.app.player.PlayerSyncController
import com.tcodeplayer.app.probe.DirectoryListing
import com.tcodeplayer.app.probe.MediaSidecarResolver
import com.tcodeplayer.app.probe.SidecarBundle
import com.tcodeplayer.app.probe.SidecarLoadUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 第 10 节第 8 步接入桥。mpvEx 的 PlayerActivity 只增加生命周期回调，
 * 播放核心、字幕渲染、文件浏览逻辑均不修改（见 INTEGRATION.md 的粘贴片段）。
 */
class PlayerActivityIntegration(
    private val scope: CoroutineScope,
    private val resolver: MediaSidecarResolver,
    private val loadUseCase: SidecarLoadUseCase,
    private val controller: PlayerSyncController,
    private val listingFactory: (mediaUri: String) -> DirectoryListing,
) {
    private var probeJob: Job? = null

    /** 播放开始 → 异步探测 → 加载 → start()。 */
    fun onPlaybackStarted(mediaUri: String, mediaFileName: String) {
        probeJob?.cancel()
        probeJob = scope.launch {
            // 探测失败静默忽略，不弹错误、不崩溃
            val bundle = runCatching {
                resolver.resolve(mediaFileName, listingFactory(mediaUri))
            }.getOrDefault(SidecarBundle.EMPTY)
            loadUseCase.applyBundle(bundle)
            controller.start()
        }
    }

    /** 暂停：采样循环继续运行，isPlaying()==false 自动跳过发送；不发 DSTOP。 */
    fun onPlaybackPaused() = Unit

    /** 恢复：下一轮采样立即生效。 */
    fun onPlaybackResumed() = Unit

    /** 媒体切换：旧媒体 stop() → DSTOP → engine.clear()；蓝牙连接保持。 */
    fun onMediaChanging() {
        probeJob?.cancel()
        controller.onMediaChanged()
    }

    /** 停止 / 退出 → stop() + DSTOP。 */
    fun onStopped() {
        probeJob?.cancel()
        controller.stop()
    }

    fun onDestroy() {
        probeJob?.cancel()
        controller.stop()
    }
}
