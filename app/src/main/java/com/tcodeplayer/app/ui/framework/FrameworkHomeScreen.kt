package com.tcodeplayer.app.ui.framework

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 框架首页：一张能点开的模块地图。视觉不在本轮打磨范围，只保证结构清晰、可逐项进入。 */
@Composable
fun FrameworkHomeScreen(
    onOpen: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "TCode Player · 框架",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        Text(
            "策划案原方案：4 个 Gradle 模块 · Kotlin · Compose Material 3 · Koin。\n" +
                "本轮只搭框架与测试入口，逐项验证后再精修视觉。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB9B3CC),
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("模块地图", style = MaterialTheme.typography.titleSmall)
                Text(
                    "core:funscript  插值 / JSON（纯 JVM）\n" +
                        "core:lyrics      LRC（纯 JVM）\n" +
                        "core:tcode       短命令 / 经典 SPP\n" +
                        "app              MpvFacade / 50Hz / 探测 / UI / DI\n\n" +
                        "播放底座：MpvFacade 反射适配层已就位，真机接 mpvEx 的 time-pos；\n" +
                        "框架阶段用 PreviewTimeProvider 顶替，业务层不改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB9B3CC),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AppRoute.ALL.filter { it !is AppRoute.Home }, key = { it.id }) { route ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(route.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "对应策划案 ${route.specRef}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB9B3CC),
                            )
                        }
                        Button(
                            onClick = { onOpen(route) },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) { Text("打开") }
                    }
                }
            }
        }
    }
}
