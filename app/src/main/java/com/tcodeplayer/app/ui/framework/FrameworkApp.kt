package com.tcodeplayer.app.ui.framework

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tcodeplayer.app.ui.SharedAxisTransition

/**
 * 框架根：一个轻量路由把「模块地图 / 逐项自检 / 5 套策划案 UI」串起来。
 * 页面切换统一走 Material 3 共享轴（X 轴）过渡。
 * 后续替换成 Navigation Compose 时，各 Frame 的签名不用改。
 */
@Composable
fun FrameworkApp(modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf<AppRoute>(AppRoute.Home) }

    SharedAxisTransition(target = route, modifier = modifier) { r ->
        when (r) {
            AppRoute.Home -> FrameworkHomeScreen(onOpen = { route = it })
            AppRoute.Check -> FrameworkCheckScreen(onBack = { route = AppRoute.Home })
            AppRoute.Video -> VideoFrame(onBack = { route = AppRoute.Home })
            AppRoute.Audio -> AudioFrame(onBack = { route = AppRoute.Home })
            AppRoute.Browser -> BrowserFrame(onBack = { route = AppRoute.Home })
            AppRoute.Device -> DeviceFrame(onBack = { route = AppRoute.Home })
            AppRoute.Settings -> SettingsFrame(onBack = { route = AppRoute.Home })
        }
    }
}
