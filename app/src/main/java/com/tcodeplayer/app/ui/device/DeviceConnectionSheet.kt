package com.tcodeplayer.app.ui.device

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

data class PairedDeviceUi(
    val name: String,
    val address: String,
    val device: BluetoothDevice,
)

data class DeviceUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val protocolVersion: String? = null,
    val axisCount: Int = 0,
    val permissionDenied: Boolean = false,
    val pairedDevices: List<PairedDeviceUi> = emptyList(),
)

/** 设备连接界面：底部弹窗、已配对设备列表、连接状态、断开按钮。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConnectionSheet(
    state: DeviceUiState,
    onConnect: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.BLUETOOTH_CONNECT
    } else {
        null
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onPermissionResult(granted) }

    // 进入设备连接界面时请求 BLUETOOTH_CONNECT（Android 12+）
    LaunchedEffect(permission) {
        if (permission != null) launcher.launch(permission) else onPermissionResult(true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("设备连接", style = MaterialTheme.typography.titleLarge)

            val statusText = when (state.status) {
                ConnectionStatus.DISCONNECTED -> "未连接"
                ConnectionStatus.CONNECTING -> "连接中"
                ConnectionStatus.CONNECTED ->
                    "已连接 · 协议 ${state.protocolVersion ?: "0.3"} · 轴 ${state.axisCount}"
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))

            if (state.permissionDenied) {
                Text(
                    "蓝牙权限被拒绝，无法连接设备。请在系统设置中授予附近设备权限。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().sizeIn(maxHeight = 320.dp)) {
                    items(state.pairedDevices, key = { it.address }) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = state.status != ConnectionStatus.CONNECTING) {
                                    onConnect(device.device)
                                }
                                .padding(vertical = 10.dp)
                                .sizeIn(minHeight = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(device.name)
                            Text(device.address, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Button(
                    onClick = onDisconnect,
                    enabled = state.status == ConnectionStatus.CONNECTED,
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) {
                    Text("断开")
                }
            }
        }
    }
}
