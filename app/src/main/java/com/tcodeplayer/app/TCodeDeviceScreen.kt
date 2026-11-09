package com.tcodeplayer.app

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tcodeplayer.app.ui.framework.FrameworkCheckScreen

/**
 * mpvEx 主界面底部导航的第 5 个 tab「TCode」。
 *
 * 同一个 APP、同一屏，包含三块（不再有第二个图标、也不再有第二个测试入口）：
 *  ① 蓝牙连接状态与已配对设备
 *  ② **已加载脚本**：实际命中的文件名 + 真正生效的轴 + 歌词/字幕，播放时一眼可见
 *  ③ 已加载脚本  ④ 已配对设备  ⑤ 功能开关
 *  逐项功能自检 C01–C29 内联展开，不跳 Activity
 */
@Composable
fun TCodeDeviceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status by TCodeRuntime.status.collectAsState()
    val script by TCodeRuntime.scriptState.collectAsState()
    val features by TCodeFeatures.states.collectAsState()
    val lifecycleEvent by TCodeRuntime.lastLifecycleEvent.collectAsState()
    val debugLog by TCodeRuntime.debugLog.collectAsState()
    val rawScripts by TCodeRuntime.rawScripts.collectAsState()
    val positionMs by TCodeRuntime.positionMs.collectAsState()
    val axisConfigs by TCodeRuntime.axisProcessor.configs.collectAsState()
    var showCheck by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<Pair<String, BluetoothDevice>>>(emptyList()) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.BLUETOOTH_CONNECT
    } else null

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) devices = loadBonded(context)
    }

    fun ensurePermission(andThen: () -> Unit = {}) {
        val p = permission
        if (p == null) { devices = loadBonded(context); andThen(); return }
        if (context.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            devices = loadBonded(context)
            andThen()
        } else {
            launcher.launch(p)
        }
    }

    LaunchedEffect(Unit) { ensurePermission() }

    if (showCheck && featureEnabled(TCodeFeatures.SELFTEST)) {
        // 内联自检：同一个 APP、同一屏，不另起 Activity、不另开图标
        FrameworkCheckScreen(onBack = { showCheck = false }, modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()          // V36：原来标题和系统状态栏重叠
            .navigationBarsPadding()
            .padding(16.dp)
            .padding(bottom = 88.dp),    // V05/V36：避开 MainScreen 悬浮的 5 个 tab
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TCode",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            // 自检入口放在页面最顶部：不用滚到长页面最底，也不被底部导航条压住
            if (featureEnabled(TCodeFeatures.SELFTEST)) {
                Button(onClick = { showCheck = true }) { Text("逐项自检") }
            }
            IconButton(onClick = { ensurePermission() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新已配对设备")
            }
        }

        // ---------------- ① 连接状态 ----------------
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                // V14：断开按钮原来只藏在整页最底部那一行，用户找不到；这里也放一个
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "① 连接与输出",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { TCodeRuntime.disconnect() },
                        enabled = status.startsWith("已连接"),
                    ) { Text("断开") }
                }
                Text(status, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp))
                Text(
                    TCodeRuntime.sendStats(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (permissionDenied) {
                    Text(
                        "权限被拒绝：请在 系统设置 → 应用 → 权限 开启「附近设备」后点右上角刷新。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // ---------------- 输出方式（原 ② 串口参数面板已删除：蓝牙读不到也设不了波特率） ----------------
        if (featureEnabled(TCodeFeatures.OUTPUT_SELECTOR)) {
            Spacer(Modifier.height(12.dp))
            OutputSelector(
                onConnect = { TCodeRuntime.connectOutput(it) },
                bluetoothDevices = {
                    Spacer(Modifier.height(14.dp))
                    Text("④ 已配对设备", style = MaterialTheme.typography.titleSmall)
                    Text("先在系统蓝牙里配对 HC-05 / TCodeESP32 串口模块（不是 BLE）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))

                    if (devices.isEmpty()) {
                        Text("没有已配对设备，点右上角刷新。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        devices.forEach { (label, device) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { TCodeRuntime.connect(device) }
                                    .sizeIn(minHeight = 48.dp),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(label, style = MaterialTheme.typography.titleSmall)
                                    Text("点此连接 → 自动执行 D1 / D2 探测",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                },
            )
        }

        // P01 诊断：播放界面进入 / 结束记录，便于定位「播完自动退出」
        Text(
            "诊断：$lifecycleEvent",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        // ---------------- ③ 已加载脚本（用户重点：最起码知道加载了） ----------------
        // 用功能开关包裹；内部保留原有缩进，便于逐项 diff 对照
        if (featureEnabled(TCodeFeatures.SCRIPT_PANEL)) {
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("③ 已加载脚本", style = MaterialTheme.typography.titleSmall)
                Text(
                    script.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (script.hasScript) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
                HorizontalDivider()

                KeyValue("媒体", script.mediaName.ifEmpty { "—" })
                KeyValue("目录", script.directory.ifEmpty { "—" })

                Spacer(Modifier.height(6.dp))
                Text("脚本文件", style = MaterialTheme.typography.labelMedium)
                if (script.mainScriptName == null && script.axisScriptNames.isEmpty()) {
                    // V28（第十四轮）：网络媒体不是「没找到脚本」，是还没做网络目录探测（R01）。
                    // 之前这里无条件写「未找到」，与上方 note 的「探测暂未启用」自相矛盾。
                    val isNetwork = script.directory == "网络媒体"
                    Text(if (isNetwork)
                             "  （网络媒体）同名脚本探测暂未启用 · R01 待开发；字幕由播放器自动匹配"
                         else "  （无）同目录未找到同名 .funscript",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace)
                } else {
                    script.mainScriptName?.let {
                        MonospaceLine("  $it  →  L0（主脚本）")
                    }
                    script.axisScriptNames.toSortedMap().forEach { (axis, fileName) ->
                        MonospaceLine("  $fileName  →  $axis")
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("生效轴（D2 过滤后）", style = MaterialTheme.typography.labelMedium)
                MonospaceLine(
                    if (script.activeAxes.isEmpty()) (script.problem?.let { "  （无）$it" } ?: "  （无）")
                    else "  " + script.activeAxes.sorted().joinToString("  ")
                )
                if (script.axisSources.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("轴的来源", style = MaterialTheme.typography.labelMedium)
                    script.axisSources.toSortedMap().forEach { (axis, from) ->
                        MonospaceLine("  $axis  ←  $from")
                    }
                }

                Spacer(Modifier.height(6.dp))
                KeyValue("歌词", script.lyricsName?.let { "$it · ${script.lyricsLineCount} 行" } ?: "（无）")
                // P04：网络媒体不能再写「（无）」，字幕其实是播放器自己匹配上的
                KeyValue(
                    "字幕",
                    script.subtitleName
                        ?: if (script.directory == "网络媒体") "（由播放器按同名规则自动匹配并加载）"
                            else "（无）",
                )

                Text(
                    "规则：NAME.funscript → L0（文件内可带 axes:[{id:\"L1\"}] 合并轴）；" +
                        "NAME.L1/R0/R2.funscript → 对应轴，同名时覆盖单文件内嵌轴；" +
                    "轴 ID 仅 L0/L1/L2/R0/R1/R2；" +
                        "NAME.lrc → 歌词；字幕 srt > ass > ssa > vtt。音频与视频共用此链路。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        }

        // ---------------- ⑤ 功能开关（模块化：增减功能不动其它代码） ----------------
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("⑤ 功能开关", style = MaterialTheme.typography.titleSmall)
                Text(
                    "关掉即从界面和运行时同时移除，代码保留，随时可开回来。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                TCodeFeatures.definitions.forEach { def ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(def.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                def.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = features[def.key] ?: def.enabledByDefault,
                            onCheckedChange = { TCodeFeatures.setEnabled(def.key, it) },
                        )
                    }
                }
            }
        }

        // ---------------- ⑥ 运动控制面板（方案 6.3 热力图） ----------------
        // 关掉 ⑤ 里的开关后本块整体消失，页面变短，底部按钮不再被埋
        if (featureEnabled(TCodeFeatures.MOTION_PANEL)) {
            Spacer(Modifier.height(12.dp))
            MotionControlSheet(
                scripts = rawScripts,
                positionMs = positionMs,
                configs = axisConfigs,
                onConfigChange = { TCodeRuntime.axisProcessor.setConfig(it) },
            )
        }

        // ---------------- ⑦ 设备调试（2.2 增量 ②，可插拔） ----------------
        if (featureEnabled(TCodeFeatures.DEBUG_SHEET)) {
            Spacer(Modifier.height(12.dp))
            DeviceDebugSheet(
                axes = script.activeAxes,
                log = debugLog,
                connected = status.startsWith("已连接"),
                onSend = { TCodeRuntime.sendManual(it) },
                onDisconnect = { TCodeRuntime.disconnect() },
                onClearLog = { TCodeRuntime.clearDebugLog() },
            )
        }

        Spacer(Modifier.height(12.dp))
        // 第十五轮 V82：底部原来还有一个「逐项自检」按钮，与页面顶部入口重复。
        // 只保留顶部那一个（进自检页后左上角「← 返回」回来），这里只留断开。
        Button(
            onClick = { TCodeRuntime.disconnect() },
            enabled = TCodeRuntime.isConnected(),
            modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
        ) { Text("断开") }
        if (featureEnabled(TCodeFeatures.SELFTEST)) {
            Text(
                "逐项自检入口在本页顶部标题右侧；自检 C01–C29 与蓝牙真机测试都在本 APP 内完成。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$key：", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MonospaceLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
}

private fun loadBonded(context: Context): List<Pair<String, BluetoothDevice>> = runCatching {
    val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    val bonded = adapter?.bondedDevices ?: emptySet()
    bonded.map { d ->
        val name = runCatching { d.name }.getOrNull() ?: "(无名)"
        "$name  ${d.address}" to d
    }
}.getOrDefault(emptyList())
