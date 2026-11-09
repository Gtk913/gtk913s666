package com.tcodeplayer.app
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 模块化契约层：所有 TCode 特性集中登记，UI / 运行时 / 播放界面读同一份状态。
 *
 * 「增减一个功能」的标准动作（见 MODULE_MAP.md）：
 *   1. 在 `feature/<名字>/` 新建一个包，只依赖 `contract` 与 core 各模块
 *   2. 在这里加一个 const key + 一条 Definition
 *   3. 在需要的地方用 `TCodeFeatures.isEnabled(key)` 包一层
 *   4. 自检里加一项 Cxx 验证它
 *
 * 关闭某个开关后，对应功能从界面和运行时同时消失，但**不删除代码**，随时可开回来。
 */
object TCodeFeatures {

    data class Definition(
        val key: String,
        val title: String,
        val description: String,
        val enabledByDefault: Boolean = true,
    )

    // ---- 功能开关 key（新增功能在此登记）----
    const val SCRIPT_DRIVER = "script_driver"
    const val SCRIPT_PANEL = "script_panel"
    const val OUTPUT_SELECTOR = "output_selector"
    const val PLAYER_OVERLAY = "player_overlay"
    const val KEEP_PLAYER_ON_EOF = "keep_player_on_eof"
    const val SELFTEST = "selftest"
    const val MOTION_PANEL = "motion_panel"
    /** 2.2 硬性约束 6：退后台发 DSTOP 并停循环；mpvEx 的 PiP / 后台播放用户可关掉此项。 */
    const val STOP_ON_BACKGROUND = "stop_on_background"
    /** 2.2 增量 ②：手动轴滑块 / D1·D2·DSTOP / 断开 / 实时回显。 */
    const val DEBUG_SHEET = "debug_sheet"
    /** 第十六轮 · MultiFunPlayer 对齐：指令带 I<真实帧间隔>，固件自行平滑。
     *  第十七轮默认关闭：设备固件是否支持 I 未知，不开时每帧全量喂最稳。 */
    const val TCODE_INTERVAL = "tcode_interval"
    /** 第十六轮 · MFP SendDirtyValuesOnly：只发幅度变化 >=1 个输出单位的轴。
     *  第十七轮默认关闭：脚本恒定段会长时间不发指令，部分固件有超时保护会停机。 */
    const val DIRTY_ONLY = "dirty_only"
    /** 第十六轮 · MFP 默认 100Hz；关掉则维持本工程规格 50Hz。 */
    const val HIGH_RATE = "high_rate"
    /** 第十七轮 · MFP 单行空格批量格式；关掉回退第十六轮的一轴一行，用于 A/B 判读。 */
    const val SINGLE_LINE = "tcode_single_line"
    /** 第十七轮 · 静默心跳：超过 60ms 没发任何指令就强制补一帧全量，防固件超时保护。 */
    const val HEARTBEAT = "tcode_heartbeat"

    val definitions: List<Definition> = listOf(
        Definition(
            SCRIPT_DRIVER, "脚本驱动（50Hz）",
            "读播放进度 → 线性插值 → 经典蓝牙 SPP 批量发送 TCode 指令",
        ),
        Definition(
            SCRIPT_PANEL, "已加载脚本面板",
            "显示实际命中的脚本文件、生效轴、轴的来源与格式标签",
        ),
        Definition(
            OUTPUT_SELECTOR, "输出方式",
            "经典蓝牙 / WiFi TCP / WiFi UDP / USB 串口（CH340）四种输出，切换时重建驱动",
        ),
        Definition(
            PLAYER_OVERLAY, "播放界面状态条",
            "播放中不退出即可查看连接、生效轴与输出方式；可折叠、可整项关闭",
        ),
        Definition(
            KEEP_PLAYER_ON_EOF, "播放结束保持界面",
            "阻止 mpvEx 的「播完自动关闭」设置把播放界面直接关掉",
        ),
        Definition(
            SELFTEST, "逐项自检",
            "「联动」页内联的 C01–C29 自检入口",
        ),
        Definition(
            MOTION_PANEL, "运动控制面板",
            "脚本热力图 Canvas、行程/速度限制、缩放与平移、PCHIP/Makima 可选插值",
        ),
        Definition(
            STOP_ON_BACKGROUND, "退后台停止驱动",
            "按 2.2 方案：离开播放界面即发 DSTOP 并停 50Hz 循环；" +
                "用画中画 / 后台播放时请关掉此项，否则设备会停",
        ),
        Definition(
            DEBUG_SHEET, "手动调试",
            "手动轴滑块、D0/D1/D2/DSTOP 快捷指令、断开连接、最近 20 条发送回显；默认折叠",
        ),
        Definition(
            TCODE_INTERVAL, "固件平滑间隔 I",
            "每条指令带 I<真实帧间隔>，设备自行在两条指令之间平滑；" +
                "固件不认 I 时会整帧丢弃，默认关闭，确认固件支持后再开",
            enabledByDefault = false,
        ),
        Definition(
            DIRTY_ONLY, "只发变化轴",
            "与 MultiFunPlayer 一致：只发幅度变化 >=1 个输出单位的轴；" +
                "脚本恒定段会完全静默，若固件有超时保护请保持关闭。UDP 输出自动强制全量",
            enabledByDefault = false,
        ),
        Definition(
            SINGLE_LINE, "单行批量格式（MFP）",
            "所有轴用空格拼成一行、末尾一个换行，与 MultiFunPlayer 完全一致；" +
                "关掉回退「一轴一行」用于对比判读",
        ),
        Definition(
            HEARTBEAT, "静默心跳",
            "连续 60ms 没发出任何指令时强制补一帧全量，防止设备固件超时归中/停机",
        ),
        Definition(
            HIGH_RATE, "100Hz 高更新率",
            "MultiFunPlayer 默认 10ms 更新周期；开启后由 50Hz 提到 100Hz。" +
                "默认关闭以维持本工程 50Hz 规格",
            enabledByDefault = false,
        ),
    )

    private val defaults: Map<String, Boolean> =
        definitions.associate { it.key to it.enabledByDefault }

    private val _states = MutableStateFlow(defaults)
    val states: StateFlow<Map<String, Boolean>> = _states

    private var prefs: SharedPreferences? = null

    /**
     * 在 Application.onCreate() 里调用一次，恢复用户上次的开关状态。
     *
     * 第十七轮：一次性迁移 PREF_VERSION=2。第十六轮把 tcode_interval / dirty_only
     * 默认写成 true 并落过盘，覆盖安装时 sp.getBoolean(key, false) 会读到旧 true，
     * 导致「改了默认值但现象完全不变」。这里显式清除旧值再按新默认重建。
     */
    fun install(context: Context) {
        val sp = context.getSharedPreferences("tcode_features", Context.MODE_PRIVATE)
        prefs = sp
        if (sp.getInt("__pref_version", 1) < 2) {
            sp.edit()
                .remove(TCODE_INTERVAL)
                .remove(DIRTY_ONLY)
                .remove(HIGH_RATE)
                .putInt("__pref_version", 2)
                .commit()
        }
        _states.value = defaults.mapValues { (key, fallback) -> sp.getBoolean(key, fallback) }
    }

    fun isEnabled(key: String): Boolean = _states.value[key] ?: defaults[key] ?: true

    fun setEnabled(key: String, value: Boolean) {
        _states.value = _states.value + (key to value)
        // V38：同步落盘，避免「关了开关、划掉任务」后状态回弹
        prefs?.edit()?.putBoolean(key, value)?.commit()
    }
}

/**
 * V38：Composable 里请一律用这个函数读开关。
 * 直接调 `TCodeFeatures.isEnabled()` 只是普通函数调用，开关变化时**不会触发重组**，
 * 于是表现为「关了没反应 / 偶尔又生效」——要等别处偶然重组才刷新。
 * 非 Composable 的运行时代码（TCodeRuntime / PlayerActivity / 自检）仍用 isEnabled()。
 */
@Composable
fun featureEnabled(key: String): Boolean {
    val states by TCodeFeatures.states.collectAsState()
    return states[key] ?: true
}
