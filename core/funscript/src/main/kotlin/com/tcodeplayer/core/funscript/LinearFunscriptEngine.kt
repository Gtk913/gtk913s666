package com.tcodeplayer.core.funscript

/**
 * 硬性插值规则：
 * - normalized = pos / 100.0（不是 /99）
 * - 相邻 action 之间线性插值
 * - 早于首个 action 取首值；晚于末个取末值
 * - 结果 clamp 到 [0.0, 0.99]
 */
class LinearFunscriptEngine : FunscriptEngine {

    private val lock = Any()
    private var axes: List<AxisScript> = emptyList()

    override fun load(scripts: List<AxisScript>) {
        val sorted = scripts.map { axis ->
            axis.copy(actions = axis.actions.sortedBy { it.at })
        }
        synchronized(lock) { axes = sorted }
    }

    override fun getMaxDurationMs(): Long = synchronized(lock) {
        axes.maxOfOrNull { axis -> axis.actions.maxOfOrNull { it.at } ?: 0L } ?: 0L
    }

    override fun clear() {
        synchronized(lock) { axes = emptyList() }
    }

    override fun sample(positionMs: Long): Map<String, Double> = synchronized(lock) {
        if (axes.isEmpty()) return@synchronized emptyMap()
        val result = LinkedHashMap<String, Double>(axes.size)
        for (axis in axes) {
            val value = sampleAxis(axis.actions, positionMs)
            if (value != null) result[axis.axisId] = value
        }
        result
    }

    private fun sampleAxis(actions: List<FunscriptAction>, positionMs: Long): Double? {
        if (actions.isEmpty()) return null
        val first = actions.first()
        val last = actions.last()

        if (positionMs <= first.at) return clamp(first.pos.toDouble())
        if (positionMs >= last.at) return clamp(last.pos.toDouble())

        // 二分定位相邻 action（脚本按 at 升序，50Hz 下保持 O(log n)）
        var lo = 0
        var hi = actions.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (actions[mid].at <= positionMs) lo = mid else hi = mid
        }
        val a = actions[lo]
        val b = actions[hi]
        val span = b.at - a.at
        val t = if (span <= 0L) 0.0 else (positionMs - a.at).toDouble() / span.toDouble()
        val pos = a.pos + (b.pos - a.pos) * t
        return clamp(pos)
    }

    /** pos / 100.0 后 clamp 到 [0.0, 0.99]。 */
    private fun clamp(pos: Double): Double = (pos / 100.0).coerceIn(0.0, 0.99)
}
