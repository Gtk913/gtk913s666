package com.tcodeplayer.app.ui.framework

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tcodeplayer.app.ui.browser.FileBrowserScreen
import com.tcodeplayer.app.ui.browser.FileEntry
import com.tcodeplayer.app.ui.browser.Protocol
import com.tcodeplayer.app.ui.device.DeviceConnectionSheet
import com.tcodeplayer.app.ui.device.DeviceUiState
import com.tcodeplayer.app.ui.player.AudioPlayerScreen
import com.tcodeplayer.app.ui.player.AudioUiState
import com.tcodeplayer.app.ui.player.VideoPlayerScreen
import com.tcodeplayer.app.ui.player.VideoUiState
import com.tcodeplayer.app.ui.settings.SettingsScreen
import com.tcodeplayer.app.ui.settings.SettingsUiState
import com.tcodeplayer.core.lyrics.LyricLine

/** 统一的框架页外壳：每个页面都有返回首页的入口，方便一项一项看。 */
@Composable
fun FrameScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B14))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回框架首页", tint = Color.White)
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun VideoFrame(onBack: () -> Unit) = FrameScaffold("视频播放界面 · 第 8 步", onBack) {
    VideoPlayerScreen(
        state = VideoUiState(
            title = "示例.mp4",
            isPlaying = true,
            positionMs = 32_000L,
            durationMs = 124_000L,
            bufferedMs = 48_000L,
            speed = 1f,
        ),
        onTogglePlay = {}, onSeek = {}, onSpeed = {}, onSubtitle = {},
        onFullscreen = {}, onBack = {}, onBrightness = {}, onVolume = {},
        videoLayer = {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF161622)),
                contentAlignment = Alignment.Center,
            ) {
                Text("真机时此处为 mpvEx 的 MPVView（本轮框架不接播放核心）", color = Color(0xFFB9B3CC))
            }
        },
    )
}

@Composable
fun AudioFrame(onBack: () -> Unit) = FrameScaffold("音频播放界面 · 第 8 / 10 步", onBack) {
    AudioPlayerScreen(
        state = AudioUiState(
            title = "夜航",
            artist = "示例艺术家",
            album = "样例专辑",
            isPlaying = true,
            positionMs = 3_000L,
            durationMs = 10_000L,
            lyrics = listOf(
                LyricLine(0L, "（前奏）"),
                LyricLine(1_200L, "灯火掠过窗边"),
                LyricLine(2_800L, "把名字留在风里"),
                LyricLine(4_600L, "夜航的人不说话"),
                LyricLine(6_800L, "等一束光靠岸"),
            ),
        ),
        onTogglePlay = {}, onPrevious = {}, onNext = {}, onSeek = {}, onSpeed = {},
    )
}

@Composable
fun BrowserFrame(onBack: () -> Unit) {
    var grid by remember { mutableStateOf(false) }
    FrameScaffold("文件浏览界面 · 第 8 步", onBack) {
        FileBrowserScreen(
            entries = listOf(
                FileEntry("movie.mp4", isDirectory = false, isVideo = true),
                FileEntry("song.mp3", isDirectory = false, isAudio = true),
                FileEntry("movie.funscript", isDirectory = false),
                FileEntry("movie.lrc", isDirectory = false),
                FileEntry("movie.srt", isDirectory = false),
                FileEntry("网络共享", isDirectory = true, protocol = Protocol.SMB),
                FileEntry("WebDAV", isDirectory = true, protocol = Protocol.WEBDAV),
                FileEntry("子文件夹", isDirectory = true),
            ),
            breadcrumbs = listOf("存储", "demo"),
            gridMode = grid,
            onToggleViewMode = { grid = !grid },
            onCrumbClick = {},
            onOpen = {},
        )
    }
}

@Composable
fun DeviceFrame(onBack: () -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    FrameScaffold("设备连接界面 · 第 8 步", onBack) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("设备连接以底部弹窗（ModalBottomSheet）呈现", color = Color.White)
            androidx.compose.material3.Button(
                onClick = { showSheet = true },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("打开设备面板") }
            Text(
                "真机时先在系统蓝牙里配对经典 SPP 模块；\n连接后执行 D1 → D2 → 轴列表过滤。",
                color = Color(0xFFB9B3CC),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (showSheet) {
            DeviceConnectionSheet(
                state = DeviceUiState(),
                onConnect = {},
                onDisconnect = {},
                onDismiss = { showSheet = false },
                onPermissionResult = {},
            )
        }
    }
}

@Composable
fun SettingsFrame(onBack: () -> Unit) {
    var state by remember { mutableStateOf(SettingsUiState()) }
    FrameScaffold("设置界面 · 第 8 步", onBack) {
        SettingsScreen(
            state = state,
            onSampleHz = { state = state.copy(sampleHz = it) },
            onDarkMode = { state = state.copy(darkMode = it) },
            onFollowSystem = { state = state.copy(followSystem = it) },
            onOpenDebugLog = {},
        )
    }
}
