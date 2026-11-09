package com.tcodeplayer.app.ui.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

data class VideoUiState(
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val speed: Float = 1f,
    val subtitleLabel: String = "字幕",
    val isFullscreen: Boolean = true,
)

/**
 * 全屏沉浸视频界面。手势：双击播放/暂停、左半屏上下滑亮度、右半屏上下滑音量、水平滑 Seek。
 * 视频画面由 mpvEx 的 MPVView 通过 videoLayer 传入，本文件不修改播放核心。
 */
@Composable
fun VideoPlayerScreen(
    state: VideoUiState,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: () -> Unit,
    onSubtitle: () -> Unit,
    onFullscreen: () -> Unit,
    onBack: () -> Unit,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
    videoLayer: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onTogglePlay() })
            }
            .pointerInput(Unit) {
                var leftHalf = false
                detectDragGestures(
                    onDragStart = { offset -> leftHalf = offset.x < size.width / 2f },
                    onDragEnd = {},
                    onDragCancel = {},
                ) { change, dragAmount ->
                    change.consume()
                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                        onSeek((state.positionMs + dragAmount.x.toLong() * 20L).coerceAtLeast(0L))
                    } else if (leftHalf) {
                        onBrightness(-dragAmount.y / size.height)
                    } else {
                        onVolume(-dragAmount.y / size.height)
                    }
                }
            }
    ) {
        videoLayer()

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Text(
                text = state.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // 半透明毛玻璃控制栏，圆角 24dp；Android 12+ 叠加真实模糊
        val glass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(16.dp)
        } else {
            Modifier
        }
        Surface(
            color = Color.Black.copy(alpha = 0.35f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .then(glass),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val duration = state.durationMs.coerceAtLeast(1L).toFloat()
                    LinearProgressIndicator(
                        progress = { (state.bufferedMs / duration).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.35f),
                        trackColor = Color.White.copy(alpha = 0.12f),
                    )
                    LinearProgressIndicator(
                        progress = { (state.positionMs / duration).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "播放/暂停",
                            tint = Color.White,
                        )
                    }
                    Text(
                        "${formatMs(state.positionMs)} / ${formatMs(state.durationMs)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(onClick = onSpeed, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Text("${state.speed}x", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = onSubtitle, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Filled.Subtitles, contentDescription = "切换字幕文件", tint = Color.White)
                    }
                    IconButton(onClick = onFullscreen, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = "全屏切换", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%02d:%02d".format(total / 60L, total % 60L)
}
