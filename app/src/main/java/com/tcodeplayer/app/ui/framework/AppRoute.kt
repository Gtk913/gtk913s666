package com.tcodeplayer.app.ui.framework

/** 框架阶段自写的轻量路由：零额外依赖，后续可原样替换为 Navigation Compose。 */
sealed class AppRoute(val id: String, val title: String, val specRef: String) {
    data object Home : AppRoute("home", "框架首页 · 模块地图", "整体")
    data object Check : AppRoute("check", "逐项功能自检", "第 3–7 步 / 第 11 步")
    data object Video : AppRoute("video", "视频播放界面", "第 8 步")
    data object Audio : AppRoute("audio", "音频播放界面", "第 8 / 10 步")
    data object Browser : AppRoute("browser", "文件浏览界面", "第 8 步")
    data object Device : AppRoute("device", "设备连接界面", "第 8 步")
    data object Settings : AppRoute("settings", "设置界面", "第 8 步")

    companion object {
        val ALL = listOf(Home, Check, Video, Audio, Browser, Device, Settings)
    }
}
