package com.tcodeplayer.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 输出方式选择。第十四轮按 V74 反馈重构：
 *  - 蓝牙 / WiFi / USB 三者的切换改成**下拉菜单**（DropdownMenu），不再是折叠卡片，也不再是三个并排按钮
 *  - 面板常驻，不折叠；选中哪一项，下面直接显示那一项的配置
 *  - TCP / UDP 收在 WiFi 下面做二级；④ 已配对设备只在蓝牙分组内部出现
 *
 * 术语：蓝牙 SPP 既设不了也读不到波特率，不显示该字段；
 * USB 串口（OTG→CH340）的波特率是真实生效的芯片参数（8N1）。
 */
@Composable
fun OutputSelector(
    modifier: Modifier = Modifier,
    onConnect: (String) -> Unit,
    bluetoothDevices: @Composable () -> Unit = {},
) {
    val key by OutputRegistry.key.collectAsState()
    val wifiMode by OutputRegistry.wifiMode.collectAsState()
    val host by OutputRegistry.host.collectAsState()
    val port by OutputRegistry.port.collectAsState()
    val baud by OutputRegistry.baud.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var hostText by remember(host) { mutableStateOf(host) }
    var portText by remember(port) { mutableStateOf(port.toString()) }
    var baudText by remember(baud) { mutableStateOf(baud.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    val group = OutputRegistry.groupOf(key)
    val groupName = OutputRegistry.groupLabels[group] ?: group
    val detail = when (key) {
        OutputRegistry.TCP -> "WiFi TCP"
        OutputRegistry.UDP -> "WiFi UDP"
        else -> OutputRegistry.labels[key] ?: key
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("输出方式", style = MaterialTheme.typography.titleSmall)
            Text(
                "$groupName · $detail" + if (TCodeRuntime.isConnected()) " · 已连接" else " · 未连接",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )

            // V74：三者用下拉菜单切换
            Box(modifier = Modifier.padding(top = 10.dp)) {
                OutlinedButton(onClick = { menuExpanded = true }) {
                    Text("切换：$groupName  ▾")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    OutputRegistry.uiGroups.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(OutputRegistry.groupLabels[g] ?: g) },
                            onClick = {
                                menuExpanded = false
                                when (g) {
                                    OutputRegistry.BLUETOOTH ->
                                        TCodeRuntime.selectOutput(OutputRegistry.BLUETOOTH)
                                    OutputRegistry.WIFI ->
                                        TCodeRuntime.selectOutput(wifiMode)
                                    else ->
                                        TCodeRuntime.selectOutput(OutputRegistry.USB)
                                }
                                error = null
                            },
                        )
                    }
                }
            }

            when (group) {
                OutputRegistry.BLUETOOTH -> {
                    Text(
                        "经典蓝牙 SPP：Android 不设、也读不到波特率。" +
                            "模块与设备主控之间的 UART 速率由模块固件 / AT 指令配置" +
                            "（如 AT+UART=9600,0,0），配好后 APP 无需任何设置即可通讯。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    bluetoothDevices()
                }

                OutputRegistry.WIFI -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        listOf(OutputRegistry.TCP, OutputRegistry.UDP).forEach { m ->
                            if (m == wifiMode) {
                                Button(onClick = { }) { Text(OutputRegistry.labels[m] ?: m) }
                            } else {
                                OutlinedButton(onClick = {
                                    OutputRegistry.setWifiMode(m)
                                    TCodeRuntime.selectOutput(m)
                                }) { Text(OutputRegistry.labels[m] ?: m) }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        OutlinedTextField(
                            value = hostText,
                            onValueChange = { hostText = it; OutputRegistry.setHost(it) },
                            label = { Text("主机 IP") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f),
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = {
                                portText = it
                                it.toIntOrNull()?.let { p -> OutputRegistry.setPort(p) }
                            },
                            label = { Text("端口") },
                            singleLine = true,
                            modifier = Modifier.weight(0.7f).padding(start = 8.dp),
                        )
                    }
                    Text(
                        "同一局域网即可；设备端需运行 TCP/UDP 服务。网络设备不回 D1/D2，按 0.3 不过滤处理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Button(onClick = { onConnect(wifiMode) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("连接 ${OutputRegistry.labels[wifiMode]}")
                    }
                }

                OutputRegistry.USB -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        OutlinedTextField(
                            value = baudText,
                            onValueChange = {
                                baudText = it
                                val v = it.toIntOrNull()
                                error = if (v == null || !UsbSerialSpec.isValid(v))
                                    "波特率需在 300~4000000 之间" else null
                                if (v != null && UsbSerialSpec.isValid(v)) {
                                    OutputRegistry.setBaud(v)
                                    TCodeRuntime.reconfigureUsbBaud(v)
                                }
                            },
                            label = { Text("波特率（真实生效）") },
                            singleLine = true,
                            isError = error != null,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            UsbSerialSpec.FRAMING,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                    if (error != null) {
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        "OTG 线 → CH340；8 数据位 / 1 停止位 / 无校验。" +
                            "首次连接会弹系统 USB 授权框，点允许后再按一次「连接」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Button(onClick = { onConnect(OutputRegistry.USB) }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("连接 USB 串口")
                    }
                }
            }
        }
    }
}
