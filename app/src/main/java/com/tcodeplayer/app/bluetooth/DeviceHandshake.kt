package com.tcodeplayer.app.bluetooth

import com.tcodeplayer.core.tcode.TCodeOutput

/**
 * 第 7 节版本兼容规则：连接后按顺序执行 D1 → D2 → 用轴列表过滤脚本轴。
 * 版本 null → 按 v0.3 处理（短命令）；MVP 所有版本统一短命令，不发送 G、<>。
 */
class DeviceHandshake(private val output: TCodeOutput) {

    data class Capabilities(
        val protocolVersion: String,
        /** null 表示不过滤轴。 */
        val axes: Set<String>?,
    )

    suspend fun probe(): Capabilities {
        val version = output.queryProtocolVersion() ?: "0.3"
        val axes = output.queryAxes()?.map { it.uppercase() }?.toSet()
        return Capabilities(version, axes)
    }
}
