package com.tcodeplayer.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcodeplayer.core.lyrics.LyricLine

data class AudioUiState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val lyrics: List<LyricLine> = emptyList(),
)

/** 音频播放界面：非黑屏，深色渐变 + 大尺寸封面（缺失时占位图标）+ 滚动歌词。 */
@Composable
fun AudioPlayerScreen(
    state: AudioUiState,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state.lyrics.indexOfLast { it.timeMs <= state.positionMs }
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem(current)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B1030), Color(0xFF0B0B14), Color(0xFF050509))
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 大尺寸专辑封面；MVP 不解析文件内嵌封面，缺失时显示占位图标
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0x55443388), Color(0x22111122))),
                        shape = MaterialTheme.shapes.extraLarge,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = "封面占位",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(96.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(state.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                state.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                state.album,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
            )

            // 中部滚动歌词区：当前行高亮放大，其余行半透明
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.lyrics.isEmpty()) {
                    Text(
                        "暂无歌词",
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 48.dp),
                    ) {
                        itemsIndexed(state.lyrics) { index, line ->
                            val active = index == current
                            Text(
                                text = line.text,
                                color = if (active) Color.White else Color.White.copy(alpha = 0.45f),
                                fontSize = if (active) 22.sp else 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Slider(
                value = state.positionMs.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首", tint = Color.White)
                }
                IconButton(onClick = onTogglePlay, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首", tint = Color.White)
                }
                IconButton(onClick = onSpeed, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Text("${state.speed}x", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
