package com.tcodeplayer.app

import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import com.tcodeplayer.app.bluetooth.DeviceHandshake
import com.tcodeplayer.app.player.MpvTimeProvider
import com.tcodeplayer.core.axis.AxisProcessor
import com.tcodeplayer.core.funscript.AxisScript
import com.tcodeplayer.app.player.PlayerSyncController
import com.tcodeplayer.app.probe.LocalDirectoryListing
import com.tcodeplayer.app.probe.MediaSidecarResolver
import com.tcodeplayer.app.probe.SidecarBundle
import com.tcodeplayer.app.probe.SidecarLoadUseCase
import com.tcodeplayer.app.probe.SidecarSpec
import com.tcodeplayer.core.funscript.LinearFunscriptEngine
import com.tcodeplayer.core.lyrics.LyricLine
import com.tcodeplayer.core.tcode.ClassicBluetoothTCodeOutput
import com.tcodeplayer.core.tcode.TCodeOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「已加载脚本」界面用的状态快照。
 * 记录的是**目录里实际命中的文件名**（不是候选名）和**真正生效的轴**（已应用 D2 过滤），
 * 让用户播放时能在同一个 APP 里直接看到加载结果，不用猜、不用看日志。
 */
data class ScriptLoadState(
    val mediaName: String = "",
    val directory: String = "",
    val mainScriptName: String? = null,
    /** axisId -> 实际文件名 */
    val axisScriptNames: Map<String, String> = emptyMap(),
    val lyricsName: String? = null,
    val subtitleName: String? = null,
    /** D2 过滤后真正驱动设备的轴 */
    val activeAxes: List<String> = emptyList(),
    /** 生效轴 → 来源（单文件内嵌 axes / 实际分文件名） */
    val axisSources: Map<String, String> = emptyMap(),
    val lyricsLineCount: Int = 0,
    /** V11：文件找到了但解析失败 / 内容为空。供状态条与 ③ 卡片显示，不再误报「无脚本」。 */
    val problem: String? = null,
    val note: String = "尚未播放媒体",
    val playing: Boolean = false,
    val updatedAtMs: Long = 0L,
) {
    val hasScript: Boolean get() = activeAxes.isNotEmpty()
}

/**
 * 合并版运行时：不修改 mpvEx 的 PlayerActivity / MPVView / 字幕渲染 / 文件浏览任何一行。
 *
 * 通过 Application.ActivityLifecycleCallbacks 感知 PlayerActivity 的进入与销毁：
 *  - 进入 → 启动 50Hz 循环 + 轮询 mpv 的 path 属性
 *  - path 变化 → 旧媒体 DSTOP + 清脚本 + 重新探测同目录文件（蓝牙不断开）
 *  - 退出 → stop() + DSTOP
 *  - 暂停 / 缓冲 / 停止由 MpvTimeProvider.isPlaying() 在循环内自动跳过，不需要 mpvEx 回调
 *
 * 播放位置直接读 MPVLib time-pos；音频与视频共用同一条链路，不判媒体类型。
 */
object TCodeRuntime {

    private const val PLAYER_ACTIVITY = "app.marlboroadvance.mpvex.ui.player.PlayerActivity"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = LinearFunscriptEngine()
    /** 方案第 5 节处理管线；默认恒等，配置由运动控制面板写入。 */
    val axisProcessor = AxisProcessor()
    /** 可切换：经典蓝牙 / WiFi TCP / WiFi UDP / USB 串口。切换时重建 50Hz 控制器。 */
    @Volatile
    private var output: TCodeOutput = ClassicBluetoothTCodeOutput()
    private val mpv = DirectMpvFacade()
    private val clock = MpvTimeProvider(mpv)
    private val resolver = MediaSidecarResolver()
    private val lyricsState = MutableStateFlow<List<LyricLine>>(emptyList())
    private val subtitleState = MutableStateFlow<String?>(null)
    private val loadUseCase = SidecarLoadUseCase(engine, mpv, lyricsState, subtitleState)
    /** 每次取当前 output，避免切换输出后握手仍指向旧链路。 */
    private fun handshake() = DeviceHandshake(output)

    private val _status = MutableStateFlow("未连接蓝牙")
    val status: StateFlow<String> = _status

    private val _scriptState = MutableStateFlow(ScriptLoadState())
    val scriptState: StateFlow<ScriptLoadState> = _scriptState

    /** 热力图用：当前加载的原始脚本（未经 AxisProcessor 处理）。 */
    private val _rawScripts = MutableStateFlow<List<AxisScript>>(emptyList())
    val rawScripts: StateFlow<List<AxisScript>> = _rawScripts

    /** 当前播放位置毫秒（由 50Hz 循环轻量回写，供热力图游标显示）。 */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    /** P01 可观测性：记录最近一次播放界面进入 / 结束，以及是否有异常被兜底。 */
    private val _lastLifecycleEvent = MutableStateFlow("尚未进入播放界面")
    val lastLifecycleEvent: StateFlow<String> = _lastLifecycleEvent

    /** 2.2 增量 ②：设备调试面板的手动指令回显。只读 append，不碰 50Hz 采样链路。 */
    private val _debugLog = MutableStateFlow<List<String>>(emptyList())
    val debugLog: StateFlow<List<String>> = _debugLog

    private var controller: PlayerSyncController? = null
    private var pathJob: Job? = null
    private var lastPath: String? = null
    private var lastBundle: SidecarBundle = SidecarBundle.EMPTY
    private var installed = false
    /** 退后台已发过 DSTOP：避免随后 destroy 再发一次，破坏「退出恰好 1 次」的验收基线。 */
    @Volatile
    private var backgrounded = false

    /** 在 mpvEx 的 App.onCreate() 里调用一次。 */
    fun install(app: Application) {
        if (installed) return
        installed = true
        TCodeFeatures.install(app)
        OutputRegistry.install(app)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (activity.javaClass.name == PLAYER_ACTIVITY) onPlayerEntered()
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) {
                // 2.2 硬性约束 6：退后台发 DSTOP 并停掉 50Hz 循环。
                // mpvEx 自带 PiP / 后台播放，故做成可关闭的功能开关。
                if (activity.javaClass.name == PLAYER_ACTIVITY &&
                    TCodeFeatures.isEnabled(TCodeFeatures.STOP_ON_BACKGROUND)) {
                    onPlayerBackgrounded()
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (activity.javaClass.name == PLAYER_ACTIVITY) onPlayerExited()
            }
        })
    }

    private fun onPlayerEntered() {
        // P01 防御：ActivityLifecycleCallbacks 里抛出的异常会直接崩整个进程，全部兜底。
        runCatching {
            backgrounded = false
            if (controller == null) controller = newController()
            if (TCodeFeatures.isEnabled(TCodeFeatures.SCRIPT_DRIVER)) controller?.start()
            _scriptState.value = _scriptState.value.copy(playing = true)
            startPathWatch()
            _lastLifecycleEvent.value = "进入播放界面 @ ${System.currentTimeMillis()}"
        }.onFailure { e ->
            _lastLifecycleEvent.value = "进入播放界面异常（已兜底）：${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /** 退后台：停循环 + 发一次 DSTOP；不清脚本，回前台由 onPlayerEntered 自动恢复。 */
    private fun onPlayerBackgrounded() {
        if (backgrounded) return
        backgrounded = true
        runCatching {
            pathJob?.cancel()
            pathJob = null
            controller?.stop()                       // DSTOP + 停 50Hz 循环
            _scriptState.value = _scriptState.value.copy(playing = false)
            _lastLifecycleEvent.value = "退到后台 @ ${System.currentTimeMillis()} · 已发 DSTOP 并停循环"
        }.onFailure { e ->
            _lastLifecycleEvent.value = "退到后台异常（已兜底）：${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun onPlayerExited() {
        // 每一步单独兜底：任何一步出错都不允许冒泡到 onActivityDestroyed 造成闪退。
        runCatching { pathJob?.cancel() }
        pathJob = null
        lastPath = null
        // 退后台时已发过 DSTOP → destroy 不再重复发，保持「退出恰好 1 次」的基线。
        val alreadyStopped = backgrounded
        backgrounded = false
        val stopError = if (alreadyStopped) null
        else runCatching { controller?.stop() }.exceptionOrNull()   // 停止 / 退出 → DSTOP
        runCatching { engine.clear() }
        runCatching { _rawScripts.value = emptyList() }
        runCatching { axisProcessor.resetState() }
        runCatching {
            _scriptState.value = _scriptState.value.copy(
                playing = false,
                problem = null,
                note = if (alreadyStopped) "播放已结束 · 退后台已发 DSTOP · 脚本已清空"
                       else "播放已结束 · 脚本已清空 · DSTOP 已发送",
            )
        }
        _lastLifecycleEvent.value = buildString {
            append("播放界面结束 @ ").append(System.currentTimeMillis())
            if (stopError != null) {
                append(" · stop 异常已兜底：").append(stopError.javaClass.simpleName)
            }
        }
    }

    /** 每 250ms 读一次 path；变化即视为媒体切换。 */
    private fun startPathWatch() {
        pathJob?.cancel()
        pathJob = scope.launch {
            while (isActive) {
                val path = withContext(Dispatchers.IO) {
                    runCatching { mpv.getPropertyString("path") }.getOrNull()
                }
                // 轻量回写播放位置，供运动控制面板的垂直游标显示（不参与 50Hz 发送）
                val pos = withContext(Dispatchers.IO) {
                    runCatching { mpv.getPropertyDouble("time-pos") }.getOrNull()
                }
                if (pos != null && pos > 0.0) _positionMs.value = (pos * 1000).toLong()
                if (!path.isNullOrEmpty() && path != lastPath) {
                    val first = lastPath == null
                    lastPath = path
                    onMediaPathChanged(path, first)
                }
                delay(250)
            }
        }
    }

    private suspend fun onMediaPathChanged(path: String, first: Boolean) {
        if (!first) {
            controller?.onMediaChanged()          // 旧媒体 DSTOP + 清脚本，蓝牙保持连接
            delay(30)
        }
        engine.clear()
        lastBundle = SidecarBundle.EMPTY

        val file = runCatching { File(path) }.getOrNull()
        val dir = file?.parentFile
        val mediaName = file?.name ?: path.substringAfterLast('/').substringBefore('?')

        if (dir != null && dir.isDirectory) {
            // 本地文件：完整同目录探测（脚本 / 轴脚本 / 歌词 / 字幕）
            val bundle = resolver.resolve(mediaName, LocalDirectoryListing(dir))
            lastBundle = bundle
            loadUseCase.applyBundle(bundle)
            _rawScripts.value = SidecarScriptExtractor.extract(bundle, loadUseCase.lastLoadedAxes)
            axisProcessor.resetState()
            publishScriptState(bundle, mediaName, dir.path)
        } else {
            // 网络流（SMB/FTP/WebDAV 经 proxy 的 URL）：静默降级；外挂字幕仍由 mpvEx 自带 SubtitleOps 处理
            loadUseCase.applyBundle(SidecarBundle.EMPTY)
            _rawScripts.value = emptyList()
            axisProcessor.resetState()
            _scriptState.value = ScriptLoadState(
                mediaName = mediaName,
                directory = "网络媒体",
                note = "网络媒体：同名 .funscript / .lrc 探测暂未启用（R01 待开发）；" +
                    "同名字幕已由播放器自动匹配加载",
                playing = true,
                updatedAtMs = System.currentTimeMillis(),
            )
        }
        controller?.start()
    }

    private fun publishScriptState(bundle: SidecarBundle, mediaName: String, directory: String) {
        val axes = loadUseCase.lastLoadedAxes
        val sources = loadUseCase.lastAxisSources
        val base = SidecarSpec.baseName(mediaName)
        val fromSingleFile = bundle.mainScriptName != null &&
            sources.values.any { it.contains("内嵌 axes") }
        val formatTag = when {
            fromSingleFile && bundle.axisScriptNames.isNotEmpty() -> "单文件内嵌 + 分文件覆盖"
            fromSingleFile -> "合并轴单文件"
            bundle.axisScriptNames.isNotEmpty() -> "多轴多文件"
            else -> "单轴文件"
        }
        val hasScriptFile = bundle.mainScriptName != null || bundle.axisScriptNames.isNotEmpty()
        val problem = if (axes.isEmpty() && hasScriptFile)
            "找到脚本文件但内容为空或格式不合法，未生效" else null
        val note = when {
            axes.isNotEmpty() -> "已加载 ${axes.size} 个轴 · 50Hz 驱动中（${axes.joinToString(" / ")}）· $formatTag"
            problem != null -> problem
            else -> "同目录未找到 $base.funscript · 播放正常，设备不驱动"
        }
        _scriptState.value = ScriptLoadState(
            mediaName = mediaName,
            directory = directory,
            mainScriptName = bundle.mainScriptName,
            axisScriptNames = bundle.axisScriptNames,
            lyricsName = bundle.lyricsName,
            subtitleName = bundle.subtitleName,
            activeAxes = axes,
            axisSources = sources,
            lyricsLineCount = lyricsState.value.size,
            problem = problem,
            note = note,
            playing = true,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    private fun newController() = PlayerSyncController(
        timeProvider = clock,
        engine = engine,
        processor = axisProcessor,
        output = output,
        scope = scope,
        onDeviceDisconnected = { _status.value = "设备已断开" },
    )

    /** ④ 已配对设备点击：自动切回蓝牙输出，再连 SPP 并执行 D1 → D2。 */
    fun connect(device: BluetoothDevice) {
        if (OutputRegistry.key.value != OutputRegistry.BLUETOOTH) {
            switchOutputNow(OutputRegistry.BLUETOOTH)
        }
        (output as? ClassicBluetoothTCodeOutput)?.setTarget(device)
        _status.value = "连接中…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { output.connect() }.getOrDefault(false)
            }
            if (ok) {
                val caps = runCatching { handshake().probe() }.getOrNull()
                loadUseCase.supportedAxes = caps?.axes
                // 若已有媒体在播放，立即按 D2 轴列表重算一次，让界面显示的生效轴同步更新
                if (lastBundle.hasAnyScript) {
                    loadUseCase.applyBundle(lastBundle)
                    _rawScripts.value = SidecarScriptExtractor.extract(lastBundle, loadUseCase.lastLoadedAxes)
                    val s = _scriptState.value
                    _scriptState.value = s.copy(
                        activeAxes = loadUseCase.lastLoadedAxes,
                        axisSources = loadUseCase.lastAxisSources,
                        note = "已加载 ${loadUseCase.lastLoadedAxes.size} 个轴 · 50Hz 驱动中" +
                            "（${loadUseCase.lastLoadedAxes.joinToString(" / ")}）",
                    )
                }
                _status.value = "已连接 · 协议 ${caps?.protocolVersion ?: "0.3"}" +
                    (caps?.axes?.let { " · 轴 ${it.size}" } ?: " · 轴列表未返回（不过滤）")
            } else {
                _status.value = "连接失败（确认模块已上电、是经典蓝牙而非 BLE）"
            }
        }
    }

    fun disconnect() {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { output.disconnect() } }
        }
        _status.value = "未连接 · ${OutputRegistry.label()}"
    }

    // ---------------- 输出层：可插拔注册表（第十二轮） ----------------

    /** 切换输出方式：停旧驱动（发一次 DSTOP）→ 换实现 → 重建 50Hz 控制器。 */
    fun selectOutput(key: String) {
        if (OutputRegistry.key.value == key) return
        switchOutputNow(key)
        _status.value = "输出：${OutputRegistry.label()} · 未连接"
    }

    private fun switchOutputNow(key: String) {
        val old = output
        runCatching { controller?.stop() }
        controller = null
        scope.launch(Dispatchers.IO) { runCatching { old.disconnect() } }
        output = OutputRegistry.switch(key)
        if (_scriptState.value.playing) {
            controller = newController()
            controller?.start()
        }
    }

    /** WiFi TCP / UDP / USB 串口的连接入口（蓝牙走 connect(device)）。 */
    fun connectOutput(key: String) {
        if (OutputRegistry.key.value != key) switchOutputNow(key)
        _status.value = "连接中…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { output.connect() }.getOrDefault(false)
            }
            _status.value = if (ok) "已连接 · ${OutputRegistry.label()} · 协议 0.3（网络设备不回 D1/D2）"
            else when (key) {
                OutputRegistry.USB -> "连接失败：确认 OTG 线 / CH340 已插好，并在系统授权框点「允许」后重试"
                else -> "连接失败：确认同局域网、IP 与端口正确、设备端服务已启动"
            }
            if (ok) {
                loadUseCase.supportedAxes = null
                if (lastBundle.hasAnyScript) loadUseCase.applyBundle(lastBundle)
            }
        }
    }

    /** USB 串口波特率是真实生效的芯片参数，改动即刻重配。 */
    fun reconfigureUsbBaud(baud: Int) {
        (output as? UsbSerialTCodeOutput)?.reconfigure(baud)
    }

    fun outputLabel(): String =
        OutputRegistry.label() + if (isConnected()) "（已连接）" else "（未连接）"

    private fun currentConnected(): Boolean = when (val o = output) {
        is ClassicBluetoothTCodeOutput -> o.isConnected()
        is TcpTCodeOutput -> o.isConnected()
        is UdpTCodeOutput -> o.isConnected()
        is UsbSerialTCodeOutput -> o.isConnected()
        else -> false
    }

    fun isConnected(): Boolean = currentConnected()

    /** 设备调试面板：手动发一条指令（自动补 \n），结果回显到日志。 */
    fun sendManual(command: String) {
        val raw = command.trim()
        if (raw.isEmpty()) return
        val line = if (raw.endsWith("\n")) raw else "$raw\n"
        // V50：手动指令也记录，但 kind=manual，不计入 50Hz 速率
        SendTimestampLog.record(line, "manual")
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { output.send(line) }.isSuccess
            }
            val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val entry = "$stamp  ${if (ok) "TX" else "未连接/失败"}  ${line.trim()}"
            _debugLog.value = (_debugLog.value + entry).takeLast(20)
        }
    }

    fun clearDebugLog() { _debugLog.value = emptyList() }

    /**
     * 面板诊断（第十七轮增强）：把「实际发出的第一行字节」直接显示出来。
     * 卡顿判读时一次截图就能区分「APP 没发」「发了但格式不对」「发了设备没吃」，
     * 不用再靠猜。完整逐帧内容仍由 ⑦ 面板的 SendTimestampLog CSV 导出。
     */
    fun sendStats(): String {
        val s = controller?.stats()
        if (s == null) return "驱动未启动"
        val hz = (kotlin.math.round(s.hz * 10.0) / 10.0).toString()
        return "实测 $hz 批/秒 · 周期 ${s.intervalMs}ms" +
            " · 格式=${if (s.singleLine) "单行(MFP)" else "一轴一行"}" +
            " · I间隔=${if (s.intervalEnabled) "开" else "关"}" +
            " · 只发变化=${if (s.dirtyOnly) "开" else "关"}" +
            " · 心跳 ${s.heartbeats} / 静默帧 ${s.silentTicks}" +
            " · 最大帧间隔 ${s.maxGapMs}ms · >60ms 间隙 ${s.gapsOver60ms}" +
            " · 最大写耗时 ${s.maxWriteMs}ms · 写失败 ${s.writeFailures} · 丢帧=0" +
            "\n线程=${s.threadName} · 共 ${s.batches} 批" +
            "\n首行样例：${s.lastFrame.ifEmpty { "（尚未发送）" }}"
    }
}
