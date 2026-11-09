package com.tcodeplayer.core.axis

import kotlin.math.abs
import kotlin.math.pow

/**
 * 方案第 5 节「MultiFunPlayer 算法参考」：不移植 C# 代码，用 Kotlin 重写插值逻辑。
 *
 * 提供两种单调/保形插值，供 AxisProcessor 在「速度限制之后」做滑动窗口平滑：
 *  - PCHIP：Fritsch–Carlson 分段三次 Hermite，保持单调性，不产生过冲
 *  - MAKIMA：改进 Akima，对局部突变更稳健
 *
 * Phase 1 默认 LINEAR（方案任务清单第 7 步）；这两种作为 AxisConfig.interpolation 可选项，
 * 不主动改默认链路。纯 Kotlin，无 Android / mpv 依赖。
 */
object Interpolation {

    /** 数据点：x 为相对毫秒时间，y 为归一化幅度 [0, 0.99]。 */
    data class Point(val x: Double, val y: Double)

    fun interface Curve {
        fun at(x: Double): Double
    }

    /** 构造一条曲线；点数不足时退化为线性/常值，保证不会崩。 */
    fun build(points: List<Point>, mode: InterpolationMode): Curve {
        if (points.isEmpty()) return Curve { 0.0 }
        if (points.size == 1) return Curve { points[0].y }
        val sorted = points.sortedBy { it.x }
        val n = sorted.size
        // x 必须严格递增；重复点直接退化成线性
        for (i in 1 until n) {
            if (sorted[i].x <= sorted[i - 1].x) return linear(sorted)
        }
        if (n == 2) return linear(sorted)

        val h = DoubleArray(n - 1)
        val delta = DoubleArray(n - 1)
        for (i in 0 until n - 1) {
            h[i] = sorted[i + 1].x - sorted[i].x
            delta[i] = (sorted[i + 1].y - sorted[i].y) / h[i]
        }

        val m = when (mode) {
            InterpolationMode.PCHIP -> pchipSlopes(h, delta)
            InterpolationMode.MAKIMA -> makimaSlopes(h, delta)
            InterpolationMode.LINEAR -> DoubleArray(n) { 0.0 }
        }

        return Curve { x ->
            val clampedX = x.coerceIn(sorted.first().x, sorted.last().x)
            var i = n - 2
            for (k in 0 until n - 1) {
                if (clampedX <= sorted[k + 1].x) { i = k; break }
            }
            val t = (clampedX - sorted[i].x) / h[i]
            // Hermite 基函数
            val h00 = 2 * t.pow(3) - 3 * t.pow(2) + 1
            val h10 = t.pow(3) - 2 * t.pow(2) + t
            val h01 = -2 * t.pow(3) + 3 * t.pow(2)
            val h11 = t.pow(3) - t.pow(2)
            (h00 * sorted[i].y + h10 * h[i] * m[i] + h01 * sorted[i + 1].y + h11 * h[i] * m[i + 1])
                .coerceIn(0.0, 0.99)
        }
    }

    private fun linear(sorted: List<Point>): Curve = Curve { x ->
        val cx = x.coerceIn(sorted.first().x, sorted.last().x)
        var i = sorted.size - 2
        for (k in 0 until sorted.size - 1) {
            if (cx <= sorted[k + 1].x) { i = k; break }
        }
        val a = sorted[i]
        val b = sorted[i + 1]
        val span = b.x - a.x
        val t = if (span <= 0.0) 0.0 else (cx - a.x) / span
        (a.y + (b.y - a.y) * t).coerceIn(0.0, 0.99)
    }

    /** Fritsch–Carlson：内部点用加权调和平均，端点用单边三点公式。 */
    private fun pchipSlopes(h: DoubleArray, d: DoubleArray): DoubleArray {
        val n = h.size + 1
        val m = DoubleArray(n)
        for (i in 1 until n - 1) {
            if (d[i - 1] * d[i] <= 0.0) {
                m[i] = 0.0                       // 局部极值 → 导数为 0，保持单调
            } else {
                val w1 = 2 * h[i] + h[i - 1]
                val w2 = h[i] + 2 * h[i - 1]
                m[i] = (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
            }
        }
        m[0] = endpointSlope(h[0], h.getOrElse(1) { h[0] }, d[0], d.getOrElse(1) { d[0] })
        m[n - 1] = endpointSlope(
            h[n - 2], h.getOrElse(n - 3) { h[n - 2] },
            d[n - 2], d.getOrElse(n - 3) { d[n - 2] },
        )
        return m
    }

    private fun endpointSlope(h0: Double, h1: Double, d0: Double, d1: Double): Double {
        var m = ((2 * h0 + h1) * d0 - h0 * d1) / (h0 + h1)
        if (m * d0 <= 0.0) m = 0.0
        else if (d0 * d1 < 0.0 && abs(m) > abs(3 * d0)) m = 3 * d0
        return m
    }

    /** Makima：权重取相邻斜率差的绝对值之和，对孤立尖峰更稳健。 */
    private fun makimaSlopes(h: DoubleArray, d: DoubleArray): DoubleArray {
        val n = h.size + 1
        // 补两段虚拟斜率（Akima 外推），再按 Makima 权重合成节点导数
        val ext = DoubleArray(n + 3)
        for (i in d.indices) ext[i + 2] = d[i]
        ext[1] = 2 * ext[2] - ext[3]
        ext[0] = 2 * ext[1] - ext[2]
        ext[n + 1] = 2 * ext[n] - ext[n - 1]
        ext[n + 2] = 2 * ext[n + 1] - ext[n]

        val m = DoubleArray(n)
        for (i in 0 until n) {
            val idx = i + 2
            val w1 = abs(ext[idx + 1] - ext[idx])
            val w2 = abs(ext[idx - 1] - ext[idx - 2])
            m[i] = if (w1 + w2 == 0.0) {
                (ext[idx - 1] + ext[idx]) / 2.0
            } else {
                (w1 * ext[idx - 1] + w2 * ext[idx]) / (w1 + w2)
            }
        }
        return m
    }
}
