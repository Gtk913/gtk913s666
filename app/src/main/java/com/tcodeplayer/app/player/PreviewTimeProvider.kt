package com.tcodeplayer.app.player

/**
 * 框架阶段用的可控假时钟，仅供逐项功能自检使用。
 * 真机 / 合并进 mpvEx 后替换为 MpvTimeProvider（读 MPVLib time-pos），
 * PlayerSyncController 与业务层代码一行都不用改。
 */
class PreviewTimeProvider(
    @Volatile var positionMs: Long = 1_000L,
    @Volatile var playing: Boolean = true,
) : PlayerTimeProvider {
    override fun currentPositionMs(): Long = positionMs
    override fun isPlaying(): Boolean = playing
}
