package com.tcodeplayer.app

/**
 * 驱动时钟的运行参数。对齐 MultiFunPlayer 的 TCodeThreadFixedUpdateContext。
 *
 * 第十七轮默认组合（按「设备固件能力未知时最稳」选择）：
 *   单行批量 = 开 / I 间隔 = 关 / dirty-only = 关 / 心跳 = 开 / 周期 20ms(50Hz)
 * 等价于 MFP 里把 OffloadElapsedTime 打开、SendDirtyValuesOnly 关掉的配置：
 * 每帧全量、连续喂、格式与 MFP 一致，不依赖固件智能解析 I。
 * 三个开关都可在 ⑤ 逐项打开做 A/B，判读流程见「卡顿问题_专项排查档案.md」。
 */
object MotionDriveConfig {

    /** 静默超过该时长强制补一帧全量，防止固件超时保护停机。 */
    const val HEARTBEAT_MS = 60L

    data class Snapshot(
        val intervalMs: Long,
        val intervalEnabled: Boolean,
        val dirtyOnly: Boolean,
        // 末尾追加且都有默认值，FrameworkCheck C28 的 Snapshot(20L, true, false) 仍合法
        val singleLine: Boolean = true,
        val heartbeatMs: Long = HEARTBEAT_MS,
    )

    fun snapshot(): Snapshot {
        val intervalMs = if (TCodeFeatures.isEnabled(TCodeFeatures.HIGH_RATE)) 10L else 20L
        val intervalEnabled = TCodeFeatures.isEnabled(TCodeFeatures.TCODE_INTERVAL)
        var dirtyOnly = TCodeFeatures.isEnabled(TCodeFeatures.DIRTY_ONLY)
        val singleLine = TCodeFeatures.isEnabled(TCodeFeatures.SINGLE_LINE)
        val heartbeatMs = if (TCodeFeatures.isEnabled(TCodeFeatures.HEARTBEAT)) HEARTBEAT_MS else 0L
        // 安全联动：固件不认 I 时必须每帧全量喂
        if (!intervalEnabled) dirtyOnly = false
        // MFP：UDP 无法确认上一条是否到达 → 每帧全量发送
        if (OutputRegistry.key.value == OutputRegistry.UDP) dirtyOnly = false
        return Snapshot(
            intervalMs = intervalMs,
            intervalEnabled = intervalEnabled,
            dirtyOnly = dirtyOnly,
            singleLine = singleLine,
            heartbeatMs = heartbeatMs,
        )
    }
}
