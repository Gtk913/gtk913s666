package com.tcodeplayer.app.player

/**
 * 包装 MPVLib.getPropertyDouble("time-pos") * 1000。
 * 放在 app 模块，依赖 mpvEx；不 import 任何 UI 类。
 */
class MpvTimeProvider(private val mpv: MpvFacade) : PlayerTimeProvider {

    @Volatile
    private var lastValidMs: Long = 0L

    override fun currentPositionMs(): Long {
        if (!mpv.hasMedia()) {
            lastValidMs = 0L
            return 0L
        }
        return try {
            val seconds = mpv.getPropertyDouble("time-pos")
            if (seconds == null || seconds.isNaN() || seconds.isInfinite()) {
                lastValidMs                       // NaN → 返回上次有效值
            } else {
                val ms = (seconds * 1000.0).toLong()
                if (ms >= 0L) {
                    lastValidMs = ms
                    ms
                } else {
                    lastValidMs
                }
            }
        } catch (e: Exception) {
            lastValidMs                           // mpv 属性读取异常 → 由控制器本轮跳过
        }
    }

    /**
     * 第十七轮：只用 pause + hasMedia。
     * core-idle / idle-active 是「解码器暂时无数据」标志，在音频、seek、轨道切换、
     * 缓冲恢复时会瞬时为 true；旧实现每帧读 3 个 mpv 属性，任一为 true 就整帧不发送，
     * 表现为「动几下停一会」。播完由 MotionDriveLoop 的脚本末尾 +1s DSTOP 兜底，
     * 不需要靠 core-idle 判定结束。
     */
    override fun isPlaying(): Boolean = try {
        val paused = mpv.getPropertyBoolean("pause") ?: false
        mpv.hasMedia() && !paused
    } catch (e: Exception) {
        false                                     // 属性读取异常 → 本轮跳过
    }
}
