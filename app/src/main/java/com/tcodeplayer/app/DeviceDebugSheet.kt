package com.tcodeplayer.app

import com.tcodeplayer.core.tcode.TCodeCommandFormatter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 2.2 增量 ②：设备调试面板。
 *
 * 独立可插拔模块，只在 TCodeDeviceScreen 的 ⑥ 之后挂一行；
 * 关掉功能开关 DEBUG_SHEET 即从界面和运行时一起消失，代码保留。
 *
 * 它同时解决用户表格里三条「不理解怎么测试 / 没找到按钮」：
 *  - V12/V13：设备卡片在 ④，先系统蓝牙配对再点卡片连接
 *  - V14：「断开」在这里，也在 ① 卡片里
 *  - V22：D2 快捷按钮 + 串口助手回显说明
 *
 * 手动发送走 TCodeRuntime.sendManual()，**不碰 50Hz 采样链路**，
 * 日志只是最近 20 条回显，不参与任何播放驱动。
 */
@Composable
fun DeviceDebugSheet(
    axes: List<String>,
    log: List<String>,
    connected: Boolean,
    onSend: (String) -> Unit,
    onDisconnect: () -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }
    // V50：APP 侧 send 时间戳（第十四轮补做，之前用户提过但未实现）
    var tsTick by remember { mutableStateOf(0) }
    val tsStats = remember(tsTick) { SendTimestampLog.stats() }
    val tsPreview = remember(tsTick) { SendTimestampLog.preview(5) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val sliderValues = remember { mutableStateMapOf<String, Float>() }
    // 第十五轮：与设备实际轴一致，只列 6 个
    val axisIds = axes.filter { it.uppercase() in TCodeCommandFormatter.ALLOWED_AXES }
        .ifEmpty { listOf("L0", "L1", "L2", "R0", "R1", "R2") }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "⑦ 设备调试（手动指令）",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起 ▲" else "展开 ▼")
                }
            }
            Text(
                "默认折叠。设备卡片在 ④ 已配对设备：先在系统蓝牙里配对 HC-05 / TCodeESP32 → " +
                    "点卡片连接 → 在这里断开或手动发指令。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "测试指引：V12 系统蓝牙配对后 ④ 出现设备名+MAC；" +
                        "V13 点该卡片连接（串口助手可见 D1×3 → DSTOP → D2×3 → DSTOP）；" +
                        "V14 点下方「断开连接」；" +
                        "V22 若设备会回显 D2 轴列表，用串口助手手动回一行即可，PC 助手不回显就记「不适用」。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))
                Text("快捷指令", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("D0", "D1", "D2", "DSTOP").forEach { cmd ->
                        Button(
                            onClick = { onSend(cmd) },
                            modifier = Modifier.weight(1f).sizeIn(minHeight = 44.dp),
                        ) { Text(cmd, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Text(
                    "D0 在 TCode v0.3 里语义未定义（2.x 方案只列名未定义行为），请谨慎使用；" +
                        "D1/D2 为设备能力查询，DSTOP 立即停止。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("手动轴（点「发送」只发这一条）", style = MaterialTheme.typography.bodySmall)
                axisIds.forEach { axis ->
                    val value = sliderValues[axis] ?: 50f
                    val amp = (value.toInt().coerceIn(0, 99) * 100).toString().padStart(4, '0')
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$axis$amp",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.sizeIn(minWidth = 76.dp),
                        )
                        Slider(
                            value = value,
                            onValueChange = { sliderValues[axis] = it },
                            valueRange = 0f..99f,
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        )
                        TextButton(onClick = { onSend("$axis$amp") }) { Text("发送") }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    label = { Text("自定义指令（如 L05000 / R29900）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSend(custom); custom = "" },
                        enabled = custom.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text("发送自定义") }
                    Button(
                        onClick = onDisconnect,
                        enabled = connected,
                        modifier = Modifier.weight(1f),
                    ) { Text("断开连接") }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "发送回显（最近 ${log.size} 条）",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearLog) { Text("清空") }
                }
                if (log.isEmpty()) {
                    Text(
                        "暂无手动指令。上面的按钮发出去后这里会显示时间戳与内容。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    log.takeLast(8).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.contains("失败")) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "V50 · APP 侧 send 时间戳（导出 CSV 判 50Hz）",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "PC 端 XCOM 的时间戳是接收缓冲分组时间，一批里 1~10 条没有逐行时刻；" +
                        "这里在调用 send() 之前打点，才是真实的发送节奏。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    tsStats,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                tsPreview.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(SendTimestampLog.exportCsv())) },
                        modifier = Modifier.weight(1f),
                    ) { Text("复制 CSV") }
                    OutlinedButton(
                        onClick = {
                            val csv = SendTimestampLog.exportCsv()
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(intent, "导出发送时间戳"))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("分享 CSV") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { tsTick++ }) { Text("刷新统计") }
                    TextButton(onClick = { SendTimestampLog.clear(); tsTick++ }) { Text("清空时间戳") }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "网络输出 TCP / UDP 已在「输出方式 → WiFi」里可用（V69 已验证）；" +
                        "WebSocket 与网络盘同名脚本（R01）仍待开发。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
