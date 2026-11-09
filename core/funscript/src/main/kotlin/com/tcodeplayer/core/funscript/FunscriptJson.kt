package com.tcodeplayer.core.funscript

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ActionDto(
    val pos: Int = 0,
    val at: Long = 0L,
)

/** 单文件多轴 funscript（version 1.1）里的一个轴。字段名兼容 id / axisId。 */
@Serializable
private data class AxisDto(
    val id: String = "",
    val axisId: String = "",
    val actions: List<ActionDto> = emptyList(),
)

@Serializable
private data class FunscriptDto(
    val actions: List<ActionDto> = emptyList(),
    val version: String? = null,
    @SerialName("inverted") val inverted: Boolean = false,
    /** 合并轴单文件：顶层 actions 是主轴 L0，这里挂 L1/R0/R2… */
    val axes: List<AxisDto> = emptyList(),
)

/**
 * 读取 .funscript 文件；失败返回 null 由调用方静默忽略。
 *
 * 同时支持两种组织方式（用户要求「最终都要支持」）：
 *  ① 合并轴单文件：{"actions":[...], "axes":[{"id":"L1","actions":[...]}, ...]}
 *  ② 多轴多文件：NAME.funscript（L0） + NAME.L1.funscript / NAME.R0.funscript …
 *
 * 解析层只负责读；「分文件覆盖同名轴」的合并优先级在 SidecarLoadUseCase 里。
 */
object FunscriptJson {
    private val json = Json { ignoreUnknownKeys = true }

    /** 第十五轮白名单：仅 L0/L1/L2/R0/R1/R2 六个轴。 */
    private val AXIS_ID = Regex("""[LR][0-2]""", RegexOption.IGNORE_CASE)

    private fun List<ActionDto>.toDomain(): List<FunscriptAction> =
        map { FunscriptAction(it.pos, it.at) }

    /** 原有行为：只读顶层 actions（单轴 L0）。 */
    fun parse(content: String): List<FunscriptAction>? =
        runCatching {
            json.decodeFromString<FunscriptDto>(content).actions.toDomain()
        }.getOrNull()

    /**
     * 解析合并轴单文件 → axisId -> actions。
     * - 顶层 actions 一律映射为 L0
     * - axes[] 里 id / axisId 合法（仅 L0/L1/L2/R0/R1/R2）的才收，非法的静默跳过
     * - 没有任何有效轴时返回 null，调用方回落到 parse()
     */
    fun parseMulti(content: String): Map<String, List<FunscriptAction>>? =
        runCatching {
            val dto = json.decodeFromString<FunscriptDto>(content)
            val out = LinkedHashMap<String, List<FunscriptAction>>()
            val main = dto.actions.toDomain()
            if (main.isNotEmpty()) out["L0"] = main
            for (axis in dto.axes) {
                val raw = axis.id.ifEmpty { axis.axisId }
                val id = raw.trim().uppercase()
                if (!AXIS_ID.matches(id)) continue
                val actions = axis.actions.toDomain()
                if (actions.isNotEmpty()) out[id] = actions
            }
            if (out.isEmpty()) null else out
        }.getOrNull()
}
