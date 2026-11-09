package com.tcodeplayer.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class SettingsUiState(
    /** Phase 1 采样循环固定 20ms（50Hz）；此处为设置项预留。 */
    val sampleHz: Int = 50,
    val darkMode: Boolean = true,
    val followSystem: Boolean = false,
)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSampleHz: (Int) -> Unit,
    onDarkMode: (Boolean) -> Unit,
    onFollowSystem: (Boolean) -> Unit,
    onOpenDebugLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Text("设置", style = MaterialTheme.typography.titleLarge)

        Text("采样频率", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 50, 100).forEach { hz ->
                FilterChip(
                    selected = state.sampleHz == hz,
                    onClick = { onSampleHz(hz) },
                    label = { Text("${hz}Hz") },
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("深色模式")
            Switch(checked = state.darkMode, onCheckedChange = onDarkMode)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("跟随系统")
            Switch(checked = state.followSystem, onCheckedChange = onFollowSystem)
        }

        TextButton(
            onClick = onOpenDebugLog,
            modifier = Modifier.padding(top = 24.dp).sizeIn(minHeight = 48.dp),
        ) {
            Text("调试日志")
        }
    }
}
