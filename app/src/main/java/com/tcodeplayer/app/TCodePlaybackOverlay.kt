package com.tcodeplayer.app
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tcodeplayer.app.TCodeRuntime
import kotlin.math.roundToInt

/**
 * P02：播放界面里直接看 TCode 状态，不用退出播放、不用切 tab。
 *
 * 这是一个**可插拔的独立小组件**：只在 setupPlayerControls() 的 Box 里加一行调用，
 * 不碰 mpvEx 的播放逻辑、控件层、字幕渲染。关掉 PLAYER_OVERLAY 开关即整项消失。
 * 默认收起成一个小徽标，点一下展开，再点收起。
 */
@Composable
fun TCodePlaybackOverlay(modifier: Modifier = Modifier) {
    if (!featureEnabled(TCodeFeatures.PLAYER_OVERLAY)) return

    val script by TCodeRuntime.scriptState.collectAsState()
    val status by TCodeRuntime.status.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    // V35：原来固定 top=36dp 正好压住播放器左上角返回键。
    // 默认下移到 88dp，并给一个专用拖动手柄，用户可自己挪到不挡的位置。
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .padding(top = 88.dp, start = 12.dp)
            .widthIn(max = 300.dp)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 只在这个手柄上拖，主区域点击仍是展开/收起，两者不抢手势
                Text(
                    "⠿",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                offsetX = (offsetX + drag.x).coerceIn(-800f, 1600f)
                                offsetY = (offsetY + drag.y).coerceIn(-600f, 1600f)
                            }
                        },
                )
                Text(
                    "TCode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (script.hasScript) {
                        "${script.activeAxes.size} 轴 · " + script.activeAxes.sorted().joinToString("/")
                    } else (script.problem?.let { "脚本无效" } ?: "无脚本"),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                Text(if (expanded) "▲" else "▼", style = MaterialTheme.typography.labelSmall)
            }

            if (expanded) {
                Spacer(Modifier.height(6.dp))
                OverlayLine("连接", status)
                OverlayLine("媒体", script.mediaName.ifEmpty { "—" })
                OverlayLine(
                    "脚本",
                    if (script.hasScript) script.axisSources.toSortedMap().entries.joinToString("; ") {
                        "${it.key}←${it.value}"
                    } else (script.problem ?: "同目录未找到同名 .funscript"),
                )
                OverlayLine("输出", TCodeRuntime.outputLabel())
                OverlayLine("字幕", script.subtitleName ?: "（由播放器自动匹配）")
                Spacer(Modifier.height(4.dp))
                Text(
                    "点此收起 · 在「联动」页可查看完整信息",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OverlayLine(key: String, value: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(
            "$key：",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}
