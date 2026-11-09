package com.tcodeplayer.app.ui.framework

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 逐项功能自检：每一项按钮都调用 FrameworkCheck 里对应的真 Kotlin 实现。
 * 不需要蓝牙、不需要 mpvEx、不需要网络，装到手机就能一项一项点。
 *
 * P03 修正（2026-11-07 截图反馈）：
 *  - 顶部 / 底部加系统栏 insets padding，最后一项不再被导航栏截断
 *  - 卡片标题单独成行，「测这项」按钮移到下一行右侧，不再压住长标题
 *  - LazyColumn 用 weight(1f) + 底部 contentPadding，长列表可完整滚到底
 */
@Composable
fun FrameworkCheckScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<Map<String, CheckResult>>(emptyMap()) }
    var runningAll by remember { mutableStateOf(false) }

    suspend fun runOne(item: CheckItem): CheckResult =
        runCatching { item.run() }
            .getOrElse { CheckResult(false, "异常：${it.javaClass.simpleName} / ${it.message}") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            // MainScreen 的 Scaffold 忽略了 paddingValues，底部 5 个 tab 是悬浮导航条，
            // 系统 navigationBarsPadding 挡不住它 —— 额外留 88dp，C29「测这项」才点得到（V05/V36）
            .padding(horizontal = 16.dp)
            .padding(bottom = 88.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) { Text("← 返回") }
            Text(
                "逐项功能自检",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
        }

        Button(
            onClick = {
                if (runningAll) return@Button
                scope.launch {
                    runningAll = true
                    val acc = LinkedHashMap<String, CheckResult>()
                    for (item in FrameworkCheck.items) {
                        acc[item.id] = runOne(item)
                        results = acc.toMap()          // 逐项刷新，能看到进度
                    }
                    runningAll = false
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).sizeIn(minHeight = 48.dp),
        ) {
            Text(if (runningAll) "运行中…" else "▶ 全部运行（共 ${FrameworkCheck.items.size} 项）")
        }

        val pass = results.values.count { it.ok }
        Text(
            "已测 ${results.size} / ${FrameworkCheck.items.size} · PASS $pass · FAIL ${results.size - pass}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(FrameworkCheck.items, key = { it.id }) { item ->
                val r = results[item.id]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        // 标题独占一行：C18/C19 这种长标题不会再被按钮压住
                        Text(
                            "${item.id}  ${item.title}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "对应 ${item.specRef}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (r != null) {
                                Text(
                                    if (r.ok) "PASS" else "FAIL",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (r.ok) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        results = results + (item.id to runOne(item))
                                    }
                                },
                                modifier = Modifier.sizeIn(minWidth = 88.dp, minHeight = 44.dp),
                            ) { Text("测这项") }
                        }

                        if (r != null) {
                            Text(
                                r.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
