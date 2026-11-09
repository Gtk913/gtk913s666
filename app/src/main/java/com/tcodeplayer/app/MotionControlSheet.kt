package com.tcodeplayer.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tcodeplayer.core.axis.AxisConfig
import com.tcodeplayer.core.axis.InterpolationMode
import com.tcodeplayer.core.funscript.AxisScript
import kotlin.math.max

/**
 * 方案 6.3 MotionControlSheet（Phase 1 任务清单第 13 步重点）。
 *
 * 全部 Compose 原生，不引入第三方图表库：
 *  - 轴 Tab 栏切换配置对象
 *  - 热力图 Canvas：X=时间 / Y=位置(0–99)、红色半透明 min/max 线、垂直播放游标
 *  - detectTransformGestures：双指缩放横向时间轴 + 单指平移
 *  - RangeSlider / Slider / Switch / DropdownMenu 绑定 AxisConfig
 *
 * 默认配置（不限速、不反转、全行程、LINEAR）下输出与未接入处理器时完全一致，
 * 调参后由 AxisProcessor 实时生效。
 */
@Composable
fun MotionControlSheet(
    scripts: List<AxisScript>,
    positionMs: Long,
    configs: Map<String, AxisConfig>,
    onConfigChange: (AxisConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!featureEnabled(TCodeFeatures.MOTION_PANEL)) return

    val axisIds = remember(scripts) { scripts.map { it.axisId } }
    var selected by remember { mutableStateOf("") }
    val currentId = if (selected in axisIds) selected else axisIds.firstOrNull().orEmpty()
    var menuOpen by remember { mutableStateOf(false) }

    // 热力图视窗：scaleX 越大看得越细，offsetX 为可见窗口起点（毫秒）
    var scaleX by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    val cfg = configs[currentId] ?: AxisConfig(axisId = currentId)
    val maxTimeMs = remember(scripts) {
        max(1L, scripts.flatMap { it.actions }.maxOfOrNull { it.at } ?: 1L)
    }
    val script = scripts.firstOrNull { it.axisId == currentId }
    // Canvas 的 lambda 不是 @Composable，颜色必须在外层取出后传入
    val curveColor = MaterialTheme.colorScheme.primary
    // 预先格式化，避免在字符串模板里嵌套引号
    val scaleText = "%.1f".format(scaleX)
    val minText = "%.2f".format(cfg.minLimit)
    val maxText = "%.2f".format(cfg.maxLimit)
    val speedText = "%.2f".format(cfg.maxSpeedPerSec)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("⑥ 运动控制面板", style = MaterialTheme.typography.titleSmall)
            Text(
                "脚本热力图 · 行程/速度限制 · 缩放与平移（Canvas 原生实现）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---------- ① 轴 Tab ----------
            Spacer(Modifier.height(8.dp))
            if (axisIds.isEmpty()) {
                Text(
                    "（无已加载脚本）播放一个带同名 .funscript 的媒体后这里会出现曲线",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    axisIds.forEach { id ->
                        Button(onClick = { selected = id; scaleX = 1f; offsetX = 0f }) {
                            Text(
                                id,
                                color = if (id == currentId) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // ---------- ② 热力图 Canvas ----------
                Spacer(Modifier.height(8.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scaleX = (scaleX * zoom).coerceIn(1f, 40f)
                                // 单指平移：按当前可见时长把像素位移折算成毫秒位移
                                val visibleMs = maxTimeMs.toFloat() / scaleX
                                offsetX = (offsetX - pan.x / size.width * visibleMs)
                                    .coerceIn(0f, maxTimeMs.toFloat() - visibleMs.coerceAtMost(maxTimeMs.toFloat()))
                            }
                        },
                ) {
                    val w = size.width
                    val h = size.height
                    val visibleMs = (maxTimeMs.toFloat() / scaleX).coerceAtLeast(1f)
                    val startMs = offsetX.coerceIn(0f, maxTimeMs.toFloat() - visibleMs)

                    fun xOf(t: Long): Float = ((t.toFloat() - startMs) / visibleMs) * w
                    fun yOf(pos: Int): Float = h * (1f - (pos / 100f).coerceIn(0f, 0.99f))

                    // 背景网格
                    for (i in 0..4) {
                        val y = h * i / 4f
                        drawLine(
                            color = Color.White.copy(alpha = 0.08f),
                            start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f,
                        )
                    }

                    // 脚本折线（原始 actions，未经处理管线）
                    if (script != null && script.actions.size >= 2) {
                        val pts = script.actions
                        for (i in 0 until pts.lastIndex) {
                            drawLine(
                                color = curveColor,
                                start = Offset(xOf(pts[i].at), yOf(pts[i].pos)),
                                end = Offset(xOf(pts[i + 1].at), yOf(pts[i + 1].pos)),
                                strokeWidth = 3f,
                            )
                        }
                    } else if (script != null) {
                        drawLine(
                            color = curveColor,
                            start = Offset(0f, yOf(script.actions.first().pos)),
                            end = Offset(w, yOf(script.actions.first().pos)),
                            strokeWidth = 3f,
                        )
                    }

                    // 红色半透明 minLimit / maxLimit 水平线
                    val limitColor = Color.Red.copy(alpha = 0.65f)
                    drawLine(
                        color = limitColor,
                        start = Offset(0f, h * (1f - cfg.minLimit.toFloat())),
                        end = Offset(w, h * (1f - cfg.minLimit.toFloat())),
                        strokeWidth = 3f,
                    )
                    drawLine(
                        color = limitColor,
                        start = Offset(0f, h * (1f - cfg.maxLimit.toFloat())),
                        end = Offset(w, h * (1f - cfg.maxLimit.toFloat())),
                        strokeWidth = 3f,
                    )

                    // 垂直播放游标
                    val cx = xOf(positionMs)
                    if (cx >= 0f && cx <= w) {
                        drawLine(
                            color = Color.Green.copy(alpha = 0.9f),
                            start = Offset(cx, 0f), end = Offset(cx, h), strokeWidth = 2f,
                        )
                    }
                }
                Text(
                    "缩放 $scaleText× · 视窗起点 ${startMsText(offsetX)} · 游标 ${positionMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // ---------- ③ 参数调节区 ----------
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text("当前轴：$currentId", style = MaterialTheme.typography.labelMedium)

                Text(
                    "行程限制  $minText ~ $maxText",
                    style = MaterialTheme.typography.bodySmall,
                )
                RangeSlider(
                    value = cfg.minLimit.toFloat()..cfg.maxLimit.toFloat(),
                    onValueChange = { range ->
                        // 夹一次边界，浮点误差不会让 AxisConfig.init 抛异常
                        val lo = range.start.toDouble().coerceIn(0.0, 0.99)
                        val hi = range.endInclusive.toDouble().coerceIn(lo, 0.99)
                        onConfigChange(cfg.copy(minLimit = lo, maxLimit = hi))
                    },
                    valueRange = 0f..0.99f,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    "速度限制  $speedText /秒（0 = 不限速，保持原脚本曲线）",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = cfg.maxSpeedPerSec.toFloat().coerceIn(0f, 10f),
                    onValueChange = { onConfigChange(cfg.copy(maxSpeedPerSec = it.toDouble())) },
                    valueRange = 0f..10f,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("反转（0.99 − val）", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                    Switch(
                        checked = cfg.isInverted,
                        onCheckedChange = { onConfigChange(cfg.copy(isInverted = it)) },
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("插值平滑", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                    Button(onClick = { menuOpen = true }) { Text(cfg.interpolation.name) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        InterpolationMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name) },
                                onClick = {
                                    onConfigChange(cfg.copy(interpolation = mode))
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "处理顺序：反转 → 行程限幅 → 速度限制 → 插值平滑（默认 LINEAR，不平滑）。" +
                        "PCHIP / MAKIMA 为可选项；默认配置是恒等变换，不会改变已验收的串口数值。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "按钮：重置本轴",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = { onConfigChange(AxisConfig(axisId = currentId)) },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("恢复默认（不限速 / 不反转 / 全行程 / LINEAR）") }
            }
        }
    }
}

private fun startMsText(offsetX: Float): String = "${offsetX.toLong()}ms"
