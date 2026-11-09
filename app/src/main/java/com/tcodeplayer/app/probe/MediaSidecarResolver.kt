package com.tcodeplayer.app.probe

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SidecarBundle(
    val mainScript: String? = null,
    /** axisId -> .funscript 文件内容 */
    val axisScripts: Map<String, String> = emptyMap(),
    val subtitleUri: String? = null,
    val lyrics: String? = null,

    // ---- 以下 4 项仅供「已加载脚本」界面展示，记录目录里**实际命中的文件名** ----
    // 忽略大小写轮命中的可能是 SONG.LRC 这类名字，UI 必须显示真实名字而不是候选名。
    val mainScriptName: String? = null,
    /** axisId -> 实际文件名 */
    val axisScriptNames: Map<String, String> = emptyMap(),
    val subtitleName: String? = null,
    val lyricsName: String? = null,
) {
    companion object {
        val EMPTY = SidecarBundle()
    }

    val hasAnyScript: Boolean get() = mainScript != null || axisScripts.isNotEmpty()
}

/**
 * 异步探测（Dispatchers.IO），失败静默、不阻塞播放；
 * 探测成功后在下一个采样周期（≤20ms）自动生效，无需用户操作。
 */
class MediaSidecarResolver(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun resolve(mediaFileName: String, listing: DirectoryListing): SidecarBundle =
        withContext(io) {
            val names = runCatching { listing.names() }.getOrDefault(emptyList())
            if (names.isEmpty()) return@withContext SidecarBundle.EMPTY

            val base = SidecarSpec.baseName(mediaFileName)

            /**
             * Q01（用户 2026-11-09 明确要求）：**主名严格区分大小写，仅扩展名忽略大小写**。
             * 例：song.mp3 ↔ song.LRC 命中；SONG.lrc、Movie.funscript 不命中 song.lrc / movie.funscript。
             * 先精确整名匹配；失败再按「主名相等 + 扩展名忽略大小写」匹配。
             * 返回目录中实际存在的文件名，避免大小写敏感文件系统上命中名字却读不到内容。
             */
            fun sameStemIgnoreCaseExt(actual: String, candidate: String): Boolean {
                val cDot = candidate.lastIndexOf('.')
                val aDot = actual.lastIndexOf('.')
                if (cDot <= 0 || aDot <= 0) return false
                return candidate.substring(0, cDot) == actual.substring(0, aDot) &&
                    candidate.substring(cDot).equals(actual.substring(aDot), ignoreCase = true)
            }

            fun pick(candidates: List<String>): String? {
                for (candidate in candidates) {
                    if (names.any { it == candidate }) return candidate
                }
                for (candidate in candidates) {
                    val actual = names.firstOrNull { sameStemIgnoreCaseExt(it, candidate) }
                    if (actual != null) return actual
                }
                return null
            }

            fun collectAxisFiles(caseSensitive: Boolean): List<Pair<String, String>> =
                names.mapNotNull { name ->
                    SidecarSpec.axisIdOrNull(name, base, caseSensitive)?.let { name to it }
                }

            suspend fun read(name: String?): String? =
                if (name == null) null
                else runCatching { listing.readText(name) }.getOrNull()

            val mainName = pick(listOf(SidecarSpec.mainScript(base)))
            val subtitleName = pick(SidecarSpec.subtitleCandidates(base))
            val lyricsName = pick(listOf(SidecarSpec.lyrics(base)))

            val exactAxisFiles = collectAxisFiles(caseSensitive = true)
            val axisFiles = exactAxisFiles.ifEmpty { collectAxisFiles(caseSensitive = false) }

            val axisScripts = LinkedHashMap<String, String>()
            val axisScriptNames = LinkedHashMap<String, String>()
            for ((fileName, axisId) in axisFiles) {
                read(fileName)?.let {
                    axisScripts[axisId] = it
                    axisScriptNames[axisId] = fileName
                }
            }

            SidecarBundle(
                mainScript = read(mainName),
                axisScripts = axisScripts,
                subtitleUri = subtitleName?.let(listing::uriOf),
                lyrics = read(lyricsName),
                mainScriptName = mainName,
                axisScriptNames = axisScriptNames,
                subtitleName = subtitleName,
                lyricsName = lyricsName,
            )
        }
}
