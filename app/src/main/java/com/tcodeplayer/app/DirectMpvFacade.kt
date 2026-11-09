package com.tcodeplayer.app

import com.tcodeplayer.app.player.MpvFacade
import `is`.xyz.mpv.MPVLib

/**
 * 合并进 mpvEx 后的 MpvFacade 实现：直接调用 AAR 里真实的 is.xyz.mpv.MPVLib。
 * 独立框架工程用的 MpvLibFacade（反射版）保留不变，两者实现同一个接口，
 * 因此 PlayerSyncController / MpvTimeProvider / SidecarLoadUseCase 一行都不用改。
 */
class DirectMpvFacade : MpvFacade {

    override fun getPropertyDouble(name: String): Double? = runCatching {
        MPVLib.getPropertyDouble(name)
    }.getOrNull()

    override fun getPropertyBoolean(name: String): Boolean? = runCatching {
        val s = MPVLib.getPropertyString(name)
        if (s != null) {
            return@runCatching s.equals("yes", true) || s == "1" || s.equals("true", true)
        }
        val i = MPVLib.getPropertyInt(name)
        if (i != null) return@runCatching i != 0
        null
    }.getOrNull()

    override fun getPropertyString(name: String): String? = runCatching {
        MPVLib.getPropertyString(name)
    }.getOrNull()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }

    override fun hasMedia(): Boolean = runCatching {
        !MPVLib.getPropertyString("path").isNullOrEmpty()
    }.getOrDefault(false)

    /**
     * 外挂字幕由 mpvEx 自带的 SubtitleOps 处理（本地 + SMB/FTP/WebDAV 全支持）。
     * 这里不重复调用 sub-add，避免同一字幕被加载两次。
     */
    override fun addSubtitle(uri: String): Boolean = true

    override fun cycleSubtitleTrack(): Boolean = runCatching {
        MPVLib.command("cycle", "sub")
        true
    }.getOrDefault(false)
}
