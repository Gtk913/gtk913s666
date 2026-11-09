package com.tcodeplayer.app.ui.framework

import com.tcodeplayer.core.axis.AxisConfig
import com.tcodeplayer.core.axis.AxisProcessor
import com.tcodeplayer.app.OutputRegistry
import com.tcodeplayer.app.MotionDriveConfig
import com.tcodeplayer.app.player.MotionDriveLoop
import com.tcodeplayer.app.UsbSerialSpec
import com.tcodeplayer.app.TCodeFeatures
import com.tcodeplayer.app.bluetooth.DeviceHandshake
import com.tcodeplayer.app.player.MpvFacade
import com.tcodeplayer.app.player.PlayerSyncController
import com.tcodeplayer.app.player.PreviewTimeProvider
import com.tcodeplayer.app.probe.DirectoryListing
import com.tcodeplayer.app.probe.MediaSidecarResolver
import com.tcodeplayer.app.probe.SidecarBundle
import com.tcodeplayer.app.probe.SidecarLoadUseCase
import com.tcodeplayer.app.probe.SidecarSpec
import com.tcodeplayer.core.funscript.AxisScript
import com.tcodeplayer.core.funscript.FunscriptAction
import com.tcodeplayer.core.funscript.FunscriptJson
import com.tcodeplayer.core.funscript.LinearFunscriptEngine
import com.tcodeplayer.core.lyrics.LyricLine
import com.tcodeplayer.core.lyrics.LyricsParser
import com.tcodeplayer.core.tcode.TCodeCommandFormatter
import com.tcodeplayer.core.tcode.TCodeOutput
import com.tcodeplayer.core.tcode.TimedBlockingWriter
import com.tcodeplayer.core.tcode.WriteOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Collections

data class CheckResult(val ok: Boolean, val detail: String)

data class CheckItem(
    val id: String,
    val title: String,
    val specRef: String,
    val run: suspend () -> CheckResult,
)

/**
 * 框架阶段「逐项功能自检」：每一项都直接调用工程里**真实的 Kotlin 类**跑一遍，
 * 不是浏览器模拟、不是假数据演示。可单测某一项，也可一次全部运行。
 * 不碰蓝牙、不碰 mpvEx，因此在任何手机上都能跑。
 */
object FrameworkCheck {

    private val THREE = listOf(
        FunscriptAction(0, 0L),
        FunscriptAction(50, 1000L),
        FunscriptAction(100, 2000L),
    )

    private fun near(a: Double?, b: Double) = a != null && kotlin.math.abs(a - b) < 1e-9

    private fun result(ok: Boolean, detail: String) = CheckResult(ok, detail)

    // ---------- 测试替身（只替换设备/播放底座，被测对象是真类）----------

    private class FakeListing(private val files: Map<String, String>) : DirectoryListing {
        override suspend fun names(): List<String> = files.keys.toList()
        override suspend fun readText(name: String): String? = files[name]
        override fun uriOf(name: String): String = "file:///$name"
    }

    private class FakeMpv : MpvFacade {
        val subtitles = mutableListOf<String>()
        override fun getPropertyDouble(name: String): Double? = null
        override fun getPropertyBoolean(name: String): Boolean? = null
        override fun getPropertyString(name: String): String? = null
        override fun hasMedia(): Boolean = true
        override fun addSubtitle(uri: String): Boolean = subtitles.add(uri)
        override fun cycleSubtitleTrack(): Boolean = true
    }

    private class FakeOutput(
        private val version: String? = null,
        private val axes: List<String>? = null,
    ) : TCodeOutput {
        val sent = Collections.synchronizedList(mutableListOf<String>())
        override suspend fun connect(): Boolean = true
        override suspend fun disconnect() = Unit
        override suspend fun send(batch: String) { sent.add(batch) }
        override suspend fun queryProtocolVersion(): String? = version
        override suspend fun queryAxes(): List<String>? = axes
        @Volatile var recordedVersion: String? = null
        override fun setProtocolVersion(version: String?) { recordedVersion = version }
    }

    private fun engineWithL0(): LinearFunscriptEngine = LinearFunscriptEngine().apply {
        load(listOf(AxisScript("L0", THREE)))
    }

    private fun audioFiles() = mapOf(
        "song.mp3" to "",
        "song.funscript" to """{"actions":[{"pos":77,"at":0}]}""",
        "song.R2.funscript" to """{"actions":[{"pos":50,"at":0}]}""",
        "song.lrc" to "[00:01.50]第一句\n[00:03.00]第二句\n",
        "song.srt" to "1\n00:00:00,000 --> 00:00:01,000\nsub\n",
    )

    private fun audioBundle() = SidecarBundle(
        mainScript = """{"actions":[{"pos":0,"at":0},{"pos":77,"at":1000}]}""",
        axisScripts = mapOf("R2" to """{"actions":[{"pos":50,"at":0}]}"""),
        subtitleUri = "file:///song.srt",
        lyrics = "[00:01.50]第一句\n[00:03.00]第二句\n",
    )

    // ---------- 逐项清单 ----------

    val items: List<CheckItem> = listOf(
        CheckItem("C01", "归一化 pos÷100.0（不是 ÷99）", "第 3 步 · 4.2") {
            val e = LinearFunscriptEngine().apply {
                load(listOf(AxisScript("L0", listOf(FunscriptAction(50, 0L)))))
            }
            val v = e.sample(0L)["L0"]
            result(near(v, 0.50), "pos=50 → $v，期望 0.50")
        },
        CheckItem("C02", "3 个 action 线性插值", "第 3 步 · 4.2") {
            val e = engineWithL0()
            val got = listOf(0L, 500L, 1000L, 1500L, 2000L).map { e.sample(it)["L0"] }
            val want = listOf(0.0, 0.25, 0.50, 0.75, 0.99)
            result(got.zip(want).all { (a, b) -> near(a, b) }, "实测 $got，期望 $want")
        },
        CheckItem("C03", "首尾取值 + clamp 到 [0,0.99]", "第 3 步 · 4.2") {
            val e = engineWithL0()
            val hi = LinearFunscriptEngine().apply {
                load(listOf(
                    AxisScript("L0", listOf(FunscriptAction(-50, 0L))),
                    AxisScript("L1", listOf(FunscriptAction(250, 0L))),
                ))
            }
            val s = hi.sample(0L)
            result(
                near(e.sample(-500L)["L0"], 0.0) && near(e.sample(99_999L)["L0"], 0.99)
                    && near(s["L0"], 0.0) && near(s["L1"], 0.99),
                "越界 → ${s["L0"]} / ${s["L1"]}；首尾 → ${e.sample(-500L)["L0"]} / ${e.sample(99_999L)["L0"]}"
            )
        },
        CheckItem("C04", "funscript JSON 解析（未知字段容错）", "第 3 步 · 5") {
            val a = FunscriptJson.parse(
                """{"version":"1.0","inverted":false,"metadata":{"title":"x"},"actions":[{"pos":0,"at":0},{"pos":99,"at":1500}]}"""
            )
            result(a?.size == 2 && a?.get(1) == FunscriptAction(99, 1500L), "解析 ${a?.size} 个 action，末个 ${a?.getOrNull(1)}")
        },
        CheckItem("C05", "LRC 时间戳统一毫秒、升序", "第 4 步 · 4.5") {
            val lines = LyricsParser.parse("[ar:Artist]\n\n无时间戳行\n[00:12.34]第一句\n[00:01.00]前一句\n")
            result(
                lines.size == 2 && lines[0].timeMs == 1_000L && lines[1].timeMs == 12_340L,
                "${lines.size} 行 → ${lines.map { it.timeMs }}"
            )
        },
        CheckItem("C06", "幅度固定 4 位短命令 + 6 轴白名单", "第 5 步 · 4.4 / 第十五轮") {
            val ok = TCodeCommandFormatter.format("L0", 0.77) == "L07700"
                && TCodeCommandFormatter.format("L0", 0.0) == "L00000"
                && TCodeCommandFormatter.format("L0", 0.99) == "L09900"
                && TCodeCommandFormatter.format("L0", 1.5) == "L09900"
                && TCodeCommandFormatter.format("R2", 0.1739) == "R21739"
                && TCodeCommandFormatter.format("V0", 0.1739) == null
                && TCodeCommandFormatter.format("A0", 0.5) == null
            result(ok, "L07700 / L00000 / L09900 / R21739；V0、A0 被白名单拒绝")
        },
        CheckItem("C07", "批量指令 \\n 连接且末尾追加 \\n", "第 5 步 · 4.4") {
            val b = TCodeCommandFormatter.buildBatch(linkedMapOf("L0" to 0.77, "R0" to 0.9))
            result(b == "L07700\nR09000\n" && TCodeCommandFormatter.STOP == "DSTOP\n",
                b?.replace("\n", "⏎") ?: "null")
        },
        CheckItem("C08", "文件探测命名规则：主名严格、扩展名忽略大小写", "第 6 步 · 5 / Q01") {
            val base = SidecarSpec.baseName("movie.mp4")
            val resolver = MediaSidecarResolver(Dispatchers.Unconfined)
            val hit = resolver.resolve("song.mp3", FakeListing(mapOf(
                "song.LRC" to "[00:01.00]第一句\n",
                "song.FUNSCRIPT" to """{"actions":[{"pos":50,"at":0}]}""",
            )))
            val miss = resolver.resolve("song.mp3", FakeListing(mapOf(
                "SONG.lrc" to "[00:01.00]第一句\n",
                "Movie.FUNSCRIPT" to """{"actions":[{"pos":50,"at":0}]}""",
            )))
            val ok = base == "movie"
                && SidecarSpec.mainScript(base) == "movie.funscript"
                && SidecarSpec.axisScript(base, "L1") == "movie.L1.funscript"
                && SidecarSpec.subtitleCandidates(base) == listOf("movie.srt", "movie.ass", "movie.ssa", "movie.vtt")
                && SidecarSpec.lyrics(base) == "movie.lrc"
                && SidecarSpec.axisIdOrNull("movie.funscript", base, true) == null
                && SidecarSpec.axisIdOrNull("movie.r0.FUNSCRIPT", base, false) == "R0"
                && SidecarSpec.axisIdOrNull("MOVIE.r0.funscript", base, false) == null
                && hit.lyricsName == "song.LRC" && hit.mainScriptName == "song.FUNSCRIPT"
                && miss.lyricsName == null && miss.mainScriptName == null
            result(ok, "srt>ass>ssa>vtt；song.LRC/song.FUNSCRIPT 命中（仅扩展名忽略）；" +
                "SONG.lrc / Movie.FUNSCRIPT 不命中（主名严格）")
        },
        CheckItem("C09", "音频同名文件全命中（脚本 + 轴 + 歌词 + 字幕）", "第 6/10 步 · 补充要求") {
            val b = MediaSidecarResolver(Dispatchers.Unconfined).resolve("song.mp3", FakeListing(audioFiles()))
            result(
                b.mainScript != null && b.axisScripts.keys == setOf("R2")
                    && b.lyrics != null && b.subtitleUri == "file:///song.srt",
                "主脚本=${b.mainScript != null}，轴=${b.axisScripts.keys}，歌词=${b.lyrics != null}，字幕=${b.subtitleUri}"
            )
        },
        CheckItem("C10", "同名文件缺失时静默降级、不报错", "第 6 步 · 5") {
            val b = MediaSidecarResolver(Dispatchers.Unconfined)
                .resolve("bare.mp3", FakeListing(mapOf("bare.mp3" to "")))
            result(b.mainScript == null && b.axisScripts.isEmpty() && b.lyrics == null && b.subtitleUri == null,
                "空 bundle，无异常")
        },
        CheckItem("C11", "同一 bundle 同时驱动脚本引擎与歌词 UI", "第 6/10 步 · 补充要求") {
            val engine = LinearFunscriptEngine()
            val lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
            val subtitle = MutableStateFlow<String?>(null)
            SidecarLoadUseCase(engine, FakeMpv(), lyrics, subtitle).applyBundle(audioBundle())
            val s = engine.sample(1500L)
            result(s.keys == setOf("L0", "R2") && lyrics.value.size == 2
                    && lyrics.value[0].timeMs == 1_500L,
                "引擎轴=${s.keys}，歌词 ${lyrics.value.size} 行 @${lyrics.value.firstOrNull()?.timeMs}ms")
        },
        CheckItem("C12", "D2 轴列表过滤（null 时不过滤）", "第 7 步 · 11.4") {
            val engine = LinearFunscriptEngine()
            val lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
            val subtitle = MutableStateFlow<String?>(null)
            val uc = SidecarLoadUseCase(engine, FakeMpv(), lyrics, subtitle)
            uc.supportedAxes = setOf("L0")
            uc.applyBundle(audioBundle())
            val filtered = engine.sample(1500L).keys
            uc.supportedAxes = null
            uc.applyBundle(audioBundle())
            val all = engine.sample(1500L).keys
            result(filtered == setOf("L0") && all == setOf("L0", "R2"),
                "过滤后=$filtered，不过滤=$all")
        },
        CheckItem("C13", "D1/D2 探测：不回显回落 0.3、轴列表不过滤", "第 7 步 · 7") {
            val caps = DeviceHandshake(FakeOutput()).probe()
            result(caps.protocolVersion == "0.3" && caps.axes == null,
                "协议=${caps.protocolVersion}，轴列表=${caps.axes}")
        },
        CheckItem("C14", "D1/D2 探测：有响应时解析轴列表", "第 7 步 · 7") {
            val caps = DeviceHandshake(FakeOutput(version = "0.2", axes = listOf("L0", "R2"))).probe()
            result(caps.protocolVersion == "0.2" && caps.axes == setOf("L0", "R2"),
                "协议=${caps.protocolVersion}，轴列表=${caps.axes}")
        },
        CheckItem("C15", "50Hz 控制器：真协程跑 140ms 并批量发送", "第 7 步 · 4.4") {
            val output = FakeOutput()
            val engine = LinearFunscriptEngine().apply {
                load(listOf(AxisScript("L0", listOf(FunscriptAction(77, 0L)))))
            }
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val c = PlayerSyncController(PreviewTimeProvider(1_000L, true), engine, AxisProcessor(), output, scope)
            c.start()
            delay(140)
            c.stop()
            delay(80)
            scope.cancel()
            val n = output.sent.count { it.startsWith("L0") }
            val stop = output.sent.contains("DSTOP\n")
            result(n >= 4 && stop, "140ms 内 $n 批 L07700，stop 发 DSTOP=$stop")
        },
        CheckItem("C16", "暂停：跳过发送，且暂停路径不发 DSTOP", "第 7 步 · 4.4") {
            val output = FakeOutput()
            val engine = LinearFunscriptEngine().apply {
                load(listOf(AxisScript("L0", listOf(FunscriptAction(77, 0L)))))
            }
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val c = PlayerSyncController(PreviewTimeProvider(1_000L, false), engine, AxisProcessor(), output, scope)
            c.start()
            delay(80)
            val duringPause = output.sent.toList()
            c.stop()
            delay(60)
            scope.cancel()
            val tail = output.sent.drop(duringPause.size)
            result(duringPause.isEmpty() && tail.none { it.startsWith("L0") },
                "暂停期间 ${duringPause.size} 批；收尾 DSTOP=${tail.contains("DSTOP\n")}")
        },
        CheckItem("C17", "媒体切换：清脚本 + 发 DSTOP，蓝牙不断开", "第 7 步 · 4.4") {
            val output = FakeOutput()
            val engine = LinearFunscriptEngine().apply {
                load(listOf(AxisScript("L0", listOf(FunscriptAction(77, 0L)))))
            }
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val c = PlayerSyncController(PreviewTimeProvider(1_000L, true), engine, AxisProcessor(), output, scope)
            c.start()
            delay(40)
            c.onMediaChanged()
            delay(60)
            scope.cancel()
            result(engine.sample(1000L).isEmpty() && output.sent.contains("DSTOP\n"),
                "切换后引擎轴数=${engine.sample(1000L).size}，DSTOP=${output.sent.contains("DSTOP\n")}")
        },
        CheckItem("C18", "合并轴单文件：axes[] 解析出 L0/L1/R0", "本轮新增 · 双格式支持") {
            val content = """{"version":"1.1","actions":[{"pos":77,"at":0}],"axes":[{"id":"L1","actions":[{"pos":50,"at":0}]},{"id":"R0","actions":[{"pos":25,"at":0}]}]}"""
            val engine = LinearFunscriptEngine()
            val lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
            val subtitle = MutableStateFlow<String?>(null)
            val uc = SidecarLoadUseCase(engine, FakeMpv(), lyrics, subtitle)
            uc.applyBundle(SidecarBundle(mainScript = content, mainScriptName = "test.funscript"))
            val s = engine.sample(0L)
            result(
                s.keys == setOf("L0", "L1", "R0") &&
                    uc.lastAxisSources["L1"]?.contains("内嵌 axes") == true,
                "轴=${s.keys}，L1 来源=${uc.lastAxisSources["L1"]}"
            )
        },
        CheckItem("C19", "多轴多文件：分文件覆盖单文件内嵌同名轴", "本轮新增 · 双格式支持") {
            val content = """{"actions":[{"pos":77,"at":0}],"axes":[{"id":"L1","actions":[{"pos":50,"at":0}]}]}"""
            val engine = LinearFunscriptEngine()
            val lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
            val subtitle = MutableStateFlow<String?>(null)
            val uc = SidecarLoadUseCase(engine, FakeMpv(), lyrics, subtitle)
            uc.applyBundle(SidecarBundle(
                mainScript = content,
                mainScriptName = "test.funscript",
                axisScripts = mapOf("L1" to """{"actions":[{"pos":20,"at":0}]}"""),
                axisScriptNames = mapOf("L1" to "test.L1.funscript"),
            ))
            val s = engine.sample(0L)
            result(
                s.keys == setOf("L0", "L1") && near(s["L1"], 0.20) &&
                    uc.lastAxisSources["L1"] == "test.L1.funscript",
                "L1=${s["L1"]}（应 0.20，被分文件覆盖），来源=${uc.lastAxisSources["L1"]}"
            )
        },
        CheckItem("C20", "输出注册表：蓝牙 / WiFi TCP / WiFi UDP / USB 串口可插拔", "第十二轮 · 输出层") {
            val ok = OutputRegistry.keys == listOf(
                OutputRegistry.BLUETOOTH, OutputRegistry.TCP, OutputRegistry.UDP, OutputRegistry.USB
            ) && OutputRegistry.defaultKey == OutputRegistry.BLUETOOTH
                && !OutputRegistry.BLUETOOTH_HAS_BAUD_RATE
                && OutputRegistry.USB_HAS_BAUD_RATE
                && UsbSerialSpec.DEFAULT_BAUD == 9600
                && UsbSerialSpec.isValid(9600) && !UsbSerialSpec.isValid(0)
                && UsbSerialSpec.FRAMING == "8N1"
                && OutputRegistry.uiGroups == listOf(
                    OutputRegistry.BLUETOOTH, OutputRegistry.WIFI, OutputRegistry.USB
                )
                && OutputRegistry.groupOf(OutputRegistry.TCP) == OutputRegistry.WIFI
                && OutputRegistry.groupOf(OutputRegistry.UDP) == OutputRegistry.WIFI
            result(
                ok,
                "输出=${OutputRegistry.keys.joinToString("/")}；蓝牙无波特率（链路层不暴露）；" +
                    "一级分组=蓝牙/WiFi/USB（TCP+UDP 收在 WiFi 下）；USB 串口默认 9600 / 8N1 真实生效",
            )
        },
        CheckItem("C21", "AxisProcessor 默认恒等：不改变已验收数值基线", "2.0 方案 · 5.1 / 任务 7") {
            val p = AxisProcessor()
            val raw = mapOf("L0" to 0.7700, "L1" to 0.1234, "R0" to 0.9900)
            val out = p.process(raw)
            result(
                out == raw,
                "输入 ${raw.values} → 输出 ${out.values}（默认不限速/不反转/全行程/LINEAR）",
            )
        },
        CheckItem("C22", "AxisProcessor 反转 → 行程限幅 → 速度限制（顺序）", "2.0 方案 · 5.1 / 5.2") {
            val p = AxisProcessor()
            p.setConfig(
                AxisConfig("L0", isInverted = true, minLimit = 0.30, maxLimit = 0.70, maxSpeedPerSec = 1.0)
            )
            // 0.0 → 反转 0.99 → 限幅 0.70（首帧无速度基准）
            val a = p.process(mapOf("L0" to 0.0), 20)["L0"]!!
            // 0.99 → 反转 0.0 → 限幅 0.30 → 限速 maxDelta=0.02 → 0.68
            val b = p.process(mapOf("L0" to 0.99), 20)["L0"]!!
            result(near(a, 0.70) && near(b, 0.68), "首帧=$a（期望 0.70），次帧=$b（期望 0.68）")
        },
        CheckItem("C23", "速度限制：maxDelta = maxSpeedPerSec × 帧间隔", "2.0 方案 · 5.1 第 3 条 / 验收 15") {
            val p = AxisProcessor()
            p.setConfig(AxisConfig("L0", maxSpeedPerSec = 2.0))
            val v0 = p.process(mapOf("L0" to 0.0), 20)["L0"]!!
            val v1 = p.process(mapOf("L0" to 0.90), 20)["L0"]!!
            val v2 = p.process(mapOf("L0" to 0.90), 20)["L0"]!!
            result(
                near(v0, 0.0) && near(v1, 0.04) && near(v2, 0.08),
                "0.00 → $v1 → $v2（每 20ms 最多 +0.04，曲线明显变缓）",
            )
        },
        CheckItem("C24", "PCHIP / MAKIMA 可选插值可用且不过冲", "2.0 方案 · 5.2 / 5.3") {
            val pts = listOf(
                com.tcodeplayer.core.axis.Interpolation.Point(0.0, 0.0),
                com.tcodeplayer.core.axis.Interpolation.Point(1000.0, 0.50),
                com.tcodeplayer.core.axis.Interpolation.Point(2000.0, 0.50),
                com.tcodeplayer.core.axis.Interpolation.Point(3000.0, 0.99),
            )
            var ok = true
            for (mode in listOf(
                com.tcodeplayer.core.axis.InterpolationMode.PCHIP,
                com.tcodeplayer.core.axis.InterpolationMode.MAKIMA,
            )) {
                val c = com.tcodeplayer.core.axis.Interpolation.build(pts, mode)
                for (t in 0..3000 step 100) {
                    val v = c.at(t.toDouble())
                    if (v < 0.0 || v > 0.99) ok = false
                }
            }
            val p = AxisProcessor()
            p.setConfig(AxisConfig("L0", interpolation = com.tcodeplayer.core.axis.InterpolationMode.PCHIP))
            val out = p.process(mapOf("L0" to 0.50))
            result(ok && out.containsKey("L0"), "两种曲线均在 [0,0.99]；LINEAR 默认未启用平滑")
        },
        CheckItem("C25", "getMaxDurationMs：取所有轴最后时间戳", "2.0 方案 · 4.2 / 4.4 第 4 条") {
            val e = LinearFunscriptEngine()
            val empty = e.getMaxDurationMs()
            e.load(listOf(
                AxisScript("L0", listOf(FunscriptAction(0, 0L), FunscriptAction(99, 30000L))),
                AxisScript("L1", listOf(FunscriptAction(0, 0L), FunscriptAction(50, 12000L))),
            ))
            val full = e.getMaxDurationMs()
            e.clear()
            result(empty == 0L && full == 30000L && e.getMaxDurationMs() == 0L,
                "空=$empty ms → 加载后=$full ms → clear 后=${e.getMaxDurationMs()} ms")
        },
        CheckItem("C26", "脚本末尾 +1s：一次 DSTOP、暂停采样、Seek 自动恢复", "2.0 方案 · 4.4 / 硬性约束 4 / 验收 8") {
            // 第十轮：用户实测「多次跑偶发 FAIL，总是 C26」。
            // 根因是旧版用固定 delay(70/80) + 精确批数相等（afterTimeout == before）去断言
            // 真 50Hz 协程，调度抖动时「设 positionMs 那一刻刚好有一轮读到旧值」就多算一批。
            // 改为「等条件成立 + 稳定窗口两次读数」，与墙上时间无关，结果确定。
            val output = FakeOutput()
            val engine = LinearFunscriptEngine().apply {
                load(listOf(
                    AxisScript("L0", listOf(FunscriptAction(0, 0L), FunscriptAction(99, 2000L))),
                ))
            }
            val clock = PreviewTimeProvider(1000L, true)
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val c = PlayerSyncController(clock, engine, AxisProcessor(), output, scope)
            fun l0Count() = output.sent.count { it.startsWith("L0") }

            suspend fun waitUntil(limitMs: Long = 1000L, cond: () -> Boolean): Boolean {
                var waited = 0L
                while (!cond() && waited < limitMs) { delay(10L); waited += 10L }
                return cond()
            }

            c.start()
            val started = waitUntil { l0Count() > 0 }
            val before = l0Count()

            clock.positionMs = 4000L              // > 2000 + 1000
            val wasTimedOut = waitUntil { c.isTimedOut() }
            val stops = output.sent.count { it == "DSTOP\n" }

            // 稳定窗口：超时后连读两次，批数不再增长即证明采样确实停了
            delay(60L)
            val snap1 = l0Count()
            delay(60L)
            val snap2 = l0Count()
            val samplingStopped = snap1 == snap2

            clock.positionMs = 1000L              // Seek 回范围内
            val seekResumed = waitUntil { l0Count() > snap2 }
            val afterSeek = l0Count()
            c.stop()
            delay(40L)
            scope.cancel()
            result(
                started && before > 0 && wasTimedOut && stops == 1 && samplingStopped && seekResumed,
                "启动 $before 批 → 超时=${wasTimedOut} / DSTOP=$stops 次（应 1）" +
                    " → 停采样 $snap1→$snap2 批（不变=${samplingStopped}）→ Seek 后 $afterSeek 批",
            )
        },
        CheckItem("C27", "setProtocolVersion 预留：Phase 1 仍只发 v0.3 短命令", "2.0 方案 · 4.3 / 11 节") {
            val output = FakeOutput(version = "0.4")
            output.setProtocolVersion(output.queryProtocolVersion())
            val formatter = TCodeCommandFormatter.format("L0", 0.77)
            val noG = formatter != null && !formatter.contains("G")
            result(
                output.recordedVersion == "0.4" && noG && formatter == "L07700",
                "记录版本=${output.recordedVersion}（不启用 v0.4）；输出=$formatter，无 G 命令",
            )
        },
        CheckItem("C28", "专用时钟不丢帧 + I 间隔 + dirty 过滤（MFP 对齐）", "第十六轮") {
            withContext(Dispatchers.IO) {
                // A) formatter：I 间隔追加在四位幅度之后，原签名输出不变
                val withI = TCodeCommandFormatter.buildBatch(linkedMapOf("L0" to 0.77), 20L)
                val plain = TCodeCommandFormatter.buildBatch(linkedMapOf("L0" to 0.77))
                // 第十七轮：多轴必须是 MFP 单行空格格式，不能一轴一行（否则固件按行排队）
                val mfpLine = TCodeCommandFormatter.buildBatch(
                    linkedMapOf("L0" to 0.77, "L1" to 0.9, "R0" to 0.1739), 20L,
                )
                val legacyLine = TCodeCommandFormatter.buildBatch(
                    linkedMapOf("L0" to 0.77, "L1" to 0.9), null, false,
                )
                // B) MFP IsValueDirty：幅度变化 >=1 个输出单位才算脏
                val dSmall = TCodeCommandFormatter.isDirty(0.77000, 0.77005)
                val dBig = TCodeCommandFormatter.isDirty(0.77020, 0.77000)
                // C) 绝对周期时钟：本轮耗时 8ms 不累积漂移，长卡顿重置不追赶
                val step = 20_000_000L
                val d1 = MotionDriveLoop.ClockMath.nextDeadline(1_000L, 1_000L + step, step)
                val d2 = MotionDriveLoop.ClockMath.nextDeadline(1_000L, 1_000L + 5 * step, step)
                val clockOk = d1 == 1_000L + step && d2 == 1_000L + 5 * step + step

                // D) 真跑专用线程：每帧注入 30ms 写延迟（超过 20ms 周期），
                //    旧实现会 busy/timeout 丢帧，新实现 0 丢帧且每帧都送到
                val clock = PreviewTimeProvider(positionMs = 1_000L, playing = true)
                val eng = engineWithL0()
                val slow = object : TCodeOutput {
                    val sent = Collections.synchronizedList(mutableListOf<String>())
                    override suspend fun connect(): Boolean = true
                    override suspend fun disconnect() = Unit
                    override suspend fun send(batch: String) { Thread.sleep(30L); sent.add(batch) }
                    override fun sendNow(batch: String) { Thread.sleep(30L); sent.add(batch) }
                    override suspend fun queryProtocolVersion(): String? = null
                    override suspend fun queryAxes(): List<String>? = null
                    override fun setProtocolVersion(version: String?) = Unit
                }
                val loop = MotionDriveLoop(
                    timeProvider = clock,
                    engine = eng,
                    processor = AxisProcessor(),
                    outputProvider = { slow },
                    configProvider = { MotionDriveConfig.Snapshot(20L, true, false) },
                )
                loop.start()
                Thread.sleep(600L)
                loop.stop()
                val n = slow.sent.size
                val noDrop = n >= 15 && loop.stats().writeFailures == 0L
                val hasI = slow.sent.firstOrNull()?.contains("I") == true
                result(
                    withI == "L07700I20\n" && plain == "L07700\n" &&
                        mfpLine == "L07700I20 L19000I20 R01739I20\n" &&
                        legacyLine == "L07700\nL19000\n" &&
                        !dSmall && dBig && clockOk && noDrop && hasI,
                    "I间隔=$withI / 原格式=$plain · 单行=$mfpLine" +
                        " · 回退=$legacyLine · dirty(0.00005)=$dSmall /(0.0002)=$dBig" +
                        " · 绝对周期=$clockOk · 30ms写延迟下发出 $n 帧/失败 ${loop.stats().writeFailures}",
                )
            }
        },
        CheckItem("C29", "退后台停驱动开关已登记且默认开启", "2.2 硬性约束 6") {
            val key = TCodeFeatures.STOP_ON_BACKGROUND
            val def = TCodeFeatures.definitions.find { it.key == key }
            val enabled = TCodeFeatures.isEnabled(key)
            result(
                def != null && def.enabledByDefault && enabled,
                "开关存在=${def != null} · 默认=${def?.enabledByDefault} · 当前=$enabled" +
                    "（PiP / 后台播放时可在功能开关里关闭）",
            )
        },
    )
}
