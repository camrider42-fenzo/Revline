package com.revline.idle.ui

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The centerline that gets painted. Same samples and the same arc parameter as [line], so a fraction of the lap is
 * still the same corner, but samples that run close to another part of the lap are eased apart. The physical line is
 * not moved. Built once per circuit and road width.
 */
internal class CircuitDraw(val line: CircuitLine, val xs: FloatArray, val ys: FloatArray) {
    /** Position at physical arc [s] (wraps), written into [out] as x, y, heading of the drawn line. */
    fun at(s: Float, out: FloatArray) {
        val n = xs.size
        var f = (s / line.step) % n
        if (f < 0f) f += n
        val i = f.toInt().coerceIn(0, n - 1)
        val t = f - i
        val j = (i + 1) % n
        out[0] = xs[i] + (xs[j] - xs[i]) * t
        out[1] = ys[i] + (ys[j] - ys[i]) * t
        val k = (i + 2) % n
        val h0 = atan2(ys[j] - ys[i], xs[j] - xs[i])
        val h1 = atan2(ys[k] - ys[j], xs[k] - xs[j])
        out[2] = h0 + CircuitLine.angleDiff(h0, h1) * t
    }

    /** How hard the drawn line is turning at [s], radians per layout unit. Positive is a right turn. */
    fun curvature(s: Float): Float {
        val a = FloatArray(3)
        val b = FloatArray(3)
        at(s - 1.5f, a)
        at(s + 1.5f, b)
        return CircuitLine.angleDiff(a[2], b[2]) / 3f
    }

    fun tightShare(gap: Float, apart: Float = 30f): Float = shareCloserThan(xs, ys, line, gap, apart)

    fun minClearance(apart: Float = 30f): Float {
        val n = xs.size
        val step = line.step
        val stride = 4
        var best = Float.MAX_VALUE
        var i = 0
        while (i < n) {
            var j = i + stride
            while (j < n) {
                val arc = min((j - i) * step, line.length - (j - i) * step)
                if (arc >= apart) best = min(best, hypot(xs[i] - xs[j], ys[i] - ys[j]))
                j += stride
            }
            i += stride
        }
        return best
    }

    /** True when the drawn loop still crosses itself. Suzuka's bridge is the one that should. */
    fun crosses(): Boolean {
        val n = xs.size
        val stride = 2
        val apart = (30f / line.step).toInt()
        var i = 0
        while (i < n) {
            var j = i + apart
            while (j < n - apart) {
                val ni = (i + stride) % n
                val nj = (j + stride) % n
                if (segmentsCross(xs[i], ys[i], xs[ni], ys[ni], xs[j], ys[j], xs[nj], ys[nj])) return true
                j += stride
            }
            i += stride
        }
        return false
    }

    companion object {
        private val cache = HashMap<String, CircuitDraw>()

        fun of(line: CircuitLine, roadUnits: Float, slackX: Float, slackY: Float): CircuitDraw {
            val key = line.id + ":" + (roadUnits * 1000f).roundToInt() + ":" + (slackX * 100f).roundToInt() + ":" + (slackY * 100f).roundToInt()
            return synchronized(cache) { cache.getOrPut(key) { build(line, roadUnits, slackX, slackY) } }
        }

        private fun build(line: CircuitLine, roadUnits: Float, slackX: Float, slackY: Float): CircuitDraw {
            val n = line.xs.size
            val dx = FloatArray(n)
            val dy = FloatArray(n)
            val protect = BooleanArray(n)
            if (line.bridgeArc >= 0f) {
                val br = (line.bridgeArc / line.step).toInt().coerceIn(0, n - 1)
                for (i in 0 until n) {
                    val along = min(abs(i - br) * line.step, line.length - abs(i - br) * line.step)
                    val space = hypot(line.xs[i] - line.xs[br], line.ys[i] - line.ys[br])
                    // The crossover and both strands that meet there stay put, so the bridge still crosses.
                    if (along < 22f || space < 14f) protect[i] = true
                }
            }
            val room = FloatArray(n) { i ->
                // Runoff is stroked at 1.55 road widths, inside a pad of 2.2 by 2.6. What is left is how far this
                // sample may move before the paint leaves the picture.
                val inset = roadUnits * 1.60f
                val left = slackX / 2f + (line.xs[i] - line.minX) + 2.6f * roadUnits - inset
                val right = slackX / 2f + (line.maxX - line.xs[i]) + 2.6f * roadUnits - inset
                val top = slackY / 2f + (line.ys[i] - line.minY) + 2.2f * roadUnits - inset
                val bot = slackY / 2f + (line.maxY - line.ys[i]) + 2.2f * roadUnits - inset
                min(min(left, right), min(top, bot)).coerceAtLeast(0f)
            }
            val target = roadUnits * 1.20f
            val apart = 30f
            val cap = roadUnits * 0.90f
            repeat(12) { pass ->
                val fx = FloatArray(n)
                val fy = FloatArray(n)
                for (i in 0 until n) {
                    if (protect[i]) continue
                    for (j in 0 until n) {
                        if (j == i || protect[j]) continue
                        val arc = min(abs(i - j) * line.step, line.length - abs(i - j) * line.step)
                        if (arc < apart) continue
                        val x1 = line.xs[i] + dx[i]
                        val y1 = line.ys[i] + dy[i]
                        val x2 = line.xs[j] + dx[j]
                        val y2 = line.ys[j] + dy[j]
                        val ddx = x1 - x2
                        val ddy = y1 - y2
                        val d = hypot(ddx, ddy)
                        if (d < target && d > 1e-3f) {
                            val push = 0.5f * (target - d) / d
                            var px = ddx * push
                            var py = ddy * push
                            // Keep the push across the road. Sliding samples along the lap bunches neighbours on the same bend.
                            val i2 = (i + 1) % n
                            val ux = line.xs[i2] - line.xs[i]
                            val uy = line.ys[i2] - line.ys[i]
                            val ul = hypot(ux, uy)
                            if (ul > 1e-4f) {
                                val along = (px * ux + py * uy) / (ul * ul)
                                px -= along * ux * 0.85f
                                py -= along * uy * 0.85f
                            }
                            fx[i] += px
                            fy[i] += py
                        }
                    }
                }
                val gain = if (pass < 8) 0.55f else 0.85f
                for (i in 0 until n) {
                    if (protect[i]) continue
                    dx[i] += fx[i] * gain
                    dy[i] += fy[i] * gain
                }
                blur(dx, if (pass < 8) 10 else 5, protect)
                blur(dy, if (pass < 8) 10 else 5, protect)
                for (i in 0 until n) {
                    if (protect[i]) {
                        dx[i] = 0f
                        dy[i] = 0f
                        continue
                    }
                    val mag = hypot(dx[i], dy[i])
                    val limit = min(room[i], cap)
                    if (mag > limit && mag > 1e-4f) {
                        dx[i] *= limit / mag
                        dy[i] *= limit / mag
                    }
                }
            }
            return CircuitDraw(line, FloatArray(n) { line.xs[it] + dx[it] }, FloatArray(n) { line.ys[it] + dy[it] })
        }

        private fun blur(a: FloatArray, radius: Int, protect: BooleanArray) {
            val n = a.size
            val tmp = FloatArray(n)
            for (i in 0 until n) {
                var sum = 0f
                for (k in -radius..radius) sum += a[(i + k + n) % n]
                tmp[i] = sum / (2 * radius + 1)
            }
            for (i in 0 until n) a[i] = if (protect[i]) 0f else tmp[i]
        }
    }
}

private fun shareCloserThan(xs: FloatArray, ys: FloatArray, line: CircuitLine, gap: Float, apart: Float): Float {
    val n = xs.size
    val step = line.step
    val stride = 3
    var tight = 0
    var seen = 0
    val br = if (line.bridgeArc >= 0f) (line.bridgeArc / step).toInt() else -1
    fun nearBridge(i: Int) = br >= 0 && hypot(xs[i] - xs[br], ys[i] - ys[br]) < 30f
    var i = 0
    while (i < n) {
        seen++
        var near = false
        var j = 0
        while (j < n && !near) {
            val arc = min(abs(i - j) * step, line.length - abs(i - j) * step)
            if (arc >= apart && hypot(xs[i] - xs[j], ys[i] - ys[j]) < gap) near = !(nearBridge(i) && nearBridge(j))
            j += stride
        }
        if (near) tight++
        i += stride
    }
    return tight.toFloat() / seen
}

private fun segmentsCross(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
    fun side(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float) = (qx - px) * (ry - py) - (qy - py) * (rx - px)
    val d1 = side(ax, ay, bx, by, cx, cy)
    val d2 = side(ax, ay, bx, by, dx, dy)
    val d3 = side(cx, cy, dx, dy, ax, ay)
    val d4 = side(cx, cy, dx, dy, bx, by)
    return d1 * d2 < 0f && d3 * d4 < 0f
}
