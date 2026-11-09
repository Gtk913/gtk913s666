package com.tcodeplayer.app.player

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * mpvEx 播放核心的唯一适配边界（app 模块）。
 * 用反射调用 MPVLib，使本源码包在尚未合并进 mpvEx fork 时也能独立编译；
 * fork 后建议改为直接调用，见 INTEGRATION.md。
 * 不修改 mpvEx 的播放核心、字幕渲染、文件浏览逻辑。
 */
interface MpvFacade {
    fun getPropertyDouble(name: String): Double?
    fun getPropertyBoolean(name: String): Boolean?
    fun getPropertyString(name: String): String?
    fun hasMedia(): Boolean
    /** 调用 mpvEx 既有外挂字幕加载接口。 */
    fun addSubtitle(uri: String): Boolean
    fun cycleSubtitleTrack(): Boolean
}

class MpvLibFacade : MpvFacade {

    companion object {
        // 按 mpvEx 实际包名核对，第一个命中即使用。
        private val CLASS_NAMES = listOf(
            "is.xyz.mpv.MPVLib",
            "is.xyz.mpv.MPVLib\$Companion",
            "com.marlboroadvance.mpvex.MPVLib",
            "MPVLib",
        )
    }

    /** 懒加载：不要在 Application.onCreate 里 Class.forName，避免提前触发 native 库初始化。 */
    private val clazz: Class<*>? by lazy {
        CLASS_NAMES.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name) }.getOrNull()
        }
    }

    /** MPVLib 可能是 Kotlin object：此时方法在 INSTANCE 上，而非 static。 */
    private val instance: Any? by lazy {
        clazz?.let { c ->
            runCatching {
                c.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
            }.getOrNull()
        }
    }

    private val methodCache = HashMap<String, Method?>()

    private fun targetMethod(name: String, arity: Int): Method? {
        val key = "$name/$arity"
        synchronized(methodCache) {
            if (methodCache.containsKey(key)) return methodCache[key]
            val found = clazz?.declaredMethods?.firstOrNull { m ->
                m.name == name &&
                    m.parameterCount == arity &&
                    (Modifier.isStatic(m.modifiers) || instance != null)
            }?.apply { isAccessible = true }
            methodCache[key] = found
            return found
        }
    }

    private fun call(name: String, vararg args: Any?): Any? {
        val m = targetMethod(name, args.size) ?: return null
        val receiver = if (Modifier.isStatic(m.modifiers)) null else instance
        return runCatching { m.invoke(receiver, *args) }.getOrNull()
    }

    override fun getPropertyDouble(name: String): Double? =
        when (val v = call("getPropertyDouble", name)) {
            is Double -> v
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }

    override fun getPropertyBoolean(name: String): Boolean? =
        when (val v = call("getPropertyBoolean", name)) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v.equals("yes", true) || v.equals("true", true) || v == "1"
            else -> null
        }

    override fun getPropertyString(name: String): String? {
        val v = call("getPropertyString", name) ?: return null
        return (v as? String)?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
    }

    override fun hasMedia(): Boolean {
        getPropertyBoolean("idle-active")?.let { return !it }
        if (getPropertyString("path") != null) return true
        return getPropertyDouble("time-pos") != null
    }

    override fun addSubtitle(uri: String): Boolean {
        // 优先 mpvEx 既有属性接口；不同版本 API 名称不同，逐一尝试。
        if (call("setPropertyString", "sub-file", uri) != null) return true
        if (call("command", arrayOf("sub-add", uri, "select")) != null) return true
        if (call("command", "sub-add", uri, "select") != null) return true
        return false
    }

    override fun cycleSubtitleTrack(): Boolean {
        if (call("command", arrayOf("cycle", "sub")) != null) return true
        return call("command", "cycle", "sub") != null
    }
}
