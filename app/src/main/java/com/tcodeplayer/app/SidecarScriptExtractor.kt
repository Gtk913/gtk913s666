package com.tcodeplayer.app

import com.tcodeplayer.app.probe.SidecarBundle
import com.tcodeplayer.core.funscript.AxisScript
import com.tcodeplayer.core.funscript.FunscriptAction
import com.tcodeplayer.core.funscript.FunscriptJson

/**
 * 从 SidecarBundle 还原「原始脚本」（未经 AxisProcessor 处理）给热力图 Canvas 画。
 * 解析规则与 SidecarLoadUseCase 完全一致：单文件内嵌 axes + 分文件覆盖同名轴。
 * 放在已验证可编译的 com.tcodeplayer.app 包，不新建源码目录。
 */
object SidecarScriptExtractor {

    fun extract(bundle: SidecarBundle, activeAxes: List<String>): List<AxisScript> {
        val scripts = LinkedHashMap<String, List<FunscriptAction>>()

        bundle.mainScript?.let { content ->
            val multi = FunscriptJson.parseMulti(content)
            if (multi != null && multi.keys.any { it != "L0" }) {
                scripts.putAll(multi)
            } else {
                FunscriptJson.parse(content)?.takeIf { it.isNotEmpty() }?.let { scripts["L0"] = it }
            }
        }

        for ((axisId, content) in bundle.axisScripts) {
            FunscriptJson.parse(content)?.takeIf { it.isNotEmpty() }?.let {
                scripts[axisId.uppercase()] = it
            }
        }

        if (scripts.isEmpty()) return emptyList()
        val ids = activeAxes.ifEmpty { scripts.keys.toList() }
        return ids.mapNotNull { id ->
            scripts[id]?.let { AxisScript(id, it.sortedBy { a -> a.at }) }
        }
    }
}
