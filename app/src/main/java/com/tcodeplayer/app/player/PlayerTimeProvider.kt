package com.tcodeplayer.app.player

/** 播放器媒体位置是唯一时间主轴；脚本、指令、同步全部以 currentPositionMs 为基准。 */
interface PlayerTimeProvider {
    /** 无媒体时返回 0；mpv 返回 NaN 时返回上次有效值。 */
    fun currentPositionMs(): Long
    /** 非暂停、非缓冲、非停止。 */
    fun isPlaying(): Boolean
}
