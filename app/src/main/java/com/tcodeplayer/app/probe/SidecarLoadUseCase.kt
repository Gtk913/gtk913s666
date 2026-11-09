package com.tcodeplayer.app.probe

import com.tcodeplayer.core.funscript.AxisScript
import com.tcodeplayer.core.funscript.FunscriptAction
import com.tcodeplayer.core.funscript.FunscriptEngine
import com.tcodeplayer.core.funscript.FunscriptJson
import com.tcodeplayer.core.lyrics.LyricLine
import com.tcodeplayer.core.lyrics.LyricsParser
import com.tcodeplayer.app.player.MpvFacade
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 第 6 节数据流落地：Funscript 就绪 → engine.load()；
 * 字幕就绪 → mpvEx 既有字幕加载接口；LRC 就绪 → 解析 → 音频 UI。
 *
 * 脚本来源支持两种，且可同时存在：
 *  ① 合并轴单文件：NAME.funscript 内 `axes:[{id:"L1",...}]`
 *  ② 多轴多文件：NAME.funscript(L0) + NAME.L1.funscript / NAME.R0.funscript …
 *
 * 合并优先级（明确写死，避免歧义）：先读单文件内嵌轴，**分文件覆盖同名轴**。
 * 即分文件优先级更高，方便用户用单独文件覆盖/微调某一个轴。
 */
class SidecarLoadUseCase(
    private val engine: FunscriptEngine,
    private val mpv: MpvFacade,
    private val lyricsState: MutableStateFlow<List<LyricLine>>,
    private val subtitleState: MutableStateFlow<String?>,
) {
    /** D2 返回的轴列表；null 时不过滤（版本兼容规则第 3 条）。 */
    @Volatile
    var supportedAxes: Set<String>? = null

    /** 最近一次 applyBundle 后**真正生效**的轴（已应用 D2 过滤），供「已加载脚本」界面显示。 */
    @Volatile
    var lastLoadedAxes: List<String> = emptyList()
        private set

    /** 生效轴 → 来源说明（单文件内嵌 / 实际分文件名），供「已加载脚本」界面显示。 */
    @Volatile
    var lastAxisSources: Map<String, String> = emptyMap()
        private set

    fun applyBundle(bundle: SidecarBundle) {
        val scripts = LinkedHashMap<String, List<FunscriptAction>>()
        val sources = LinkedHashMap<String, String>()

        // ① 主脚本：优先按「合并轴单文件」解析
        bundle.mainScript?.let { content ->
            val fileName = bundle.mainScriptName ?: "NAME.funscript"
            val multi = FunscriptJson.parseMulti(content)
            // 只要解析出非 L0 轴就按合并轴单文件处理；纯 L0 文件走原有 parse() 路径，
            // 保证 C01–C17 的行为一字不变。
            if (multi != null && multi.keys.any { it != "L0" }) {
                // 单文件里带了有效 axes：顶层 → L0，axes[] → 各轴
                for ((axisId, actions) in multi) {
                    scripts[axisId] = actions
                    sources[axisId] = "$fileName（内嵌 axes）"
                }
            } else {
                // 普通单轴文件：顶层 actions → L0（保持原有行为不变）
                FunscriptJson.parse(content)?.takeIf { it.isNotEmpty() }?.let {
                    scripts["L0"] = it
                    sources["L0"] = fileName
                }
            }
        }

        // ② 多轴多文件：覆盖同名轴（分文件优先）
        for ((axisId, content) in bundle.axisScripts) {
            FunscriptJson.parse(content)?.takeIf { it.isNotEmpty() }?.let {
                val id = axisId.uppercase()
                scripts[id] = it
                sources[id] = bundle.axisScriptNames[axisId] ?: "NAME.$id.funscript"
            }
        }

        val allowed = supportedAxes
        // 第十五轮：无论来源是内嵌 axes、分文件还是 D2 轴列表，最终只保留 6 个合法轴。
        val filtered = scripts.filterKeys { id ->
            val up = id.uppercase()
            up in SidecarSpec.ALLOWED_AXES && (allowed == null || up in allowed)
        }
        engine.load(filtered.map { (axisId, actions) -> AxisScript(axisId, actions) })
        lastLoadedAxes = filtered.keys.toList()
        lastAxisSources = filtered.keys.associateWith { sources[it] ?: "未知来源" }

        // 外挂字幕：交给 mpvEx 既有加载接口；内封字幕由 mpvEx 自身处理
        bundle.subtitleUri?.let { uri ->
            if (mpv.addSubtitle(uri)) subtitleState.value = uri else subtitleState.value = null
        } ?: run { subtitleState.value = null }

        // 外挂歌词（MVP 不解析内嵌歌词）
        lyricsState.value = bundle.lyrics?.let { LyricsParser.parse(it) } ?: emptyList()
    }
}
