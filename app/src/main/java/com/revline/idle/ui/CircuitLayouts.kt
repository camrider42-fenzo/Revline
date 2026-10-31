package com.revline.idle.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Hand-drawn overhead centerlines for the eight circuits, in a 160 x 100 box with y pointing down. They are
 * simplified: each is a closed loop of nodes smoothed with a Catmull-Rom spline, and a `*` marks the nodes that
 * are corners, so the number of corners matches the circuit's own count. Plain numbers, so tests can read them
 * without a canvas.
 */
internal object CircuitLayouts {
    const val BOX_W = 160f
    const val BOX_H = 100f

    private val specs: Map<String, String> = mapOf(
        "monza" to "20,22 60,20 100,20 116,20 124,26* 132,24* 146,32* 150,42 148,48* 153,55* 150,64 147,72* 138,80* 120,83 90,83 66,82 58,80* 52,76* 46,80* 34,81 22,80 10,78 4,68* 4,52 9,38 18,26",
        "monaco" to "70,88 56,88 46,86* 36,76 38,64* 48,54* 56,46* 64,40* 74,40* 82,46* 82,54 76,62* 96,60* 114,52 132,44 142,46* 148,52* 154,62* 148,72* 140,76* 130,74* 124,78* 116,84* 110,88* 100,86* 88,88",
        "silverstone" to "10,70 10,56 14,44* 22,38* 30,28* 40,30* 46,22* 60,14 90,12 112,12 122,14* 130,22* 126,32* 114,36* 102,44* 90,50* 80,60* 74,52* 64,56* 52,62 36,66* 30,76* 24,88* 12,86*",
        "spa" to "120,88 90,88 60,88 44,88* 36,80* 48,72* 62,76 72,66* 84,52 100,34 114,20 120,14* 128,16* 136,22* 144,32* 148,42* 140,52* 132,56* 120,62* 108,58* 98,64* 112,76 140,70* 154,74* 150,82* 142,86* 134,90*",
        "hungaroring" to "110,88 70,88 40,88 24,86* 18,74* 28,64* 40,66 52,58* 52,46 44,38* 56,28* 74,28 84,36* 82,48* 94,56* 110,50 122,40* 136,36 146,44* 140,58* 148,70* 138,80*",
        "suzuka" to "118,86 136,86 146,80* 150,66* 142,52* 134,44* 126,36* 118,30* 108,24* 92,22* 80,30* 66,44 56,60 46,74 30,82* 22,68* 24,52* 28,38* 40,30* 58,32* 80,50 100,64 108,72* 116,80* 124,86*",
        "montreal" to "30,24 60,24 100,24 108,24* 114,30* 120,40* 110,50* 100,54* 92,62* 84,66* 72,70* 90,76* 120,78* 130,86* 100,88 60,88 30,86 20,80* 16,72* 16,50 20,30*",
        "singapore" to "14.8,85.5 18.6,88.8* 23.5,90 59.5,90 62.8,88.8* 64,85.5 64,84.5 65.2,81.2* 68.5,80 76.5,80 80.5,78.5* 82,74.5 82,73.5 83.5,69.5* 87.5,68 98.5,68 102.5,66.5* 104,62.5 104,61.5 105.5,57.5* 109.5,56 124.5,56 128.5,54.5* 130,50.5 130,35.5 128.5,31.5* 124.5,30 116.5,30 113.2,31.2* 112,34.5 112,35.5 110.8,38.8* 107.5,40 99.5,40 95.5,38.5* 94,34.5 94,25.5 92.5,21.5* 88.5,20 74.5,20 71.2,21.2* 70,24.5 70,25.5 68.8,28.8* 65.5,30 25.5,30 21.5,31.5* 20,35.5 20,44.5 18.5,48.5* 14.5,50 13.5,50 9.5,51.5* 8,55.5 8,70.5 8.9,75.7* 11.2,80.5",
    )

    val ids: Set<String> get() = specs.keys

    /** Circuits whose start line is placed by hand: the node, then how far toward the next node. */
    val startAt: Map<String, Pair<Int, Float>> = emptyMap()

    private val built = HashMap<String, CircuitLine>()

    fun has(id: String) = id in specs

    /** The smoothed line for a circuit; unknown ids get Monza's. Built once. */
    fun of(id: String): CircuitLine = synchronized(built) {
        val key = if (id in specs) id else "monza"
        built.getOrPut(key) { CircuitLine(key, specs.getValue(key), startAt[key]) }
    }
}

/**
 * One circuit's centerline sampled at an even step, starting on the start/finish line and running in the driving
 * direction. Lengths are in layout units. [pos] and [heading] answer "where is the car at this distance".
 */
internal class CircuitLine(val id: String, spec: String, startAt: Pair<Int, Float>? = null) {
    val nodeCount: Int
    val corners: Int
    /** Even samples; the line closes from the last sample back to the first. */
    val xs: FloatArray
    val ys: FloatArray
    val length: Float
    val step: Float
    /** Arc distance of each flagged node, from the start line. */
    val cornerArc: FloatArray
    /** +1 where the corner turns right (clockwise on the page), -1 left. */
    val cornerSide: IntArray
    /** Arc distance of the start of the self-crossing's bridge, or -1 when the loop never crosses itself. */
    val bridgeArc: Float
    val minX: Float
    val maxX: Float
    val minY: Float
    val maxY: Float
    /** Length of the straight the start line sits on, in units. */
    val startStraight: ClosedFloatingPointRange<Float>

    init {
        val nodes = spec.trim().split(' ').filter { it.isNotEmpty() }
        nodeCount = nodes.size
        val px = FloatArray(nodeCount)
        val py = FloatArray(nodeCount)
        val flag = BooleanArray(nodeCount)
        nodes.forEachIndexed { i, tok ->
            flag[i] = tok.endsWith("*")
            val (a, b) = tok.removeSuffix("*").split(',')
            px[i] = a.toFloat(); py[i] = b.toFloat()
        }
        corners = flag.count { it }
        // Dense spline, remembering where each node falls.
        val per = 24
        val dx = ArrayList<Float>(nodeCount * per)
        val dy = ArrayList<Float>(nodeCount * per)
        val nodeAt = IntArray(nodeCount)
        for (i in 0 until nodeCount) {
            val i0 = (i - 1 + nodeCount) % nodeCount; val i2 = (i + 1) % nodeCount; val i3 = (i + 2) % nodeCount
            nodeAt[i] = dx.size
            val t0 = 0.0
            val t1 = t0 + hypot((px[i] - px[i0]).toDouble(), (py[i] - py[i0]).toDouble()).pow(0.5)
            val t2 = t1 + hypot((px[i2] - px[i]).toDouble(), (py[i2] - py[i]).toDouble()).pow(0.5)
            val t3 = t2 + hypot((px[i3] - px[i2]).toDouble(), (py[i3] - py[i2]).toDouble()).pow(0.5)
            for (k in 0 until per) {
                val t = t1 + (t2 - t1) * k / per
                dx.add(bg(t0, t1, t2, t3, t, px[i0], px[i], px[i2], px[i3]))
                dy.add(bg(t0, t1, t2, t3, t, py[i0], py[i], py[i2], py[i3]))
            }
        }
        val m = dx.size
        val cum = FloatArray(m + 1)
        for (i in 0 until m) cum[i + 1] = cum[i] + hypot(dx[(i + 1) % m] - dx[i], dy[(i + 1) % m] - dy[i])
        val total = cum[m]
        val n = (total / 0.5f).roundToInt()
        step = total / n
        // Resample evenly by walking the dense line.
        val rx = FloatArray(n)
        val ry = FloatArray(n)
        var j = 0
        for (i in 0 until n) {
            val s = i * step
            while (j < m - 1 && cum[j + 1] < s) j++
            val f = ((s - cum[j]) / (cum[j + 1] - cum[j]).coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
            rx[i] = dx[j] + (dx[(j + 1) % m] - dx[j]) * f
            ry[i] = dy[j] + (dy[(j + 1) % m] - dy[j]) * f
        }
        // Where do the straights run, and where is the loop crossing itself (the bridge)?
        val h = FloatArray(n) { i -> atan2(ry[(i + 1) % n] - ry[i], rx[(i + 1) % n] - rx[i]) }
        fun turn(i: Int, w: Int): Float = angleDiff(h[(i - w + n * 4) % n], h[(i + w) % n])
        val w = (3f / step).roundToInt()
        var straight = BooleanArray(n) { abs(turn(it, w)) < 0.025f }
        val crossing = findCrossing(rx, ry, step)
        val cross = crossing?.second ?: -1
        fun roomAt(idx: Int): Boolean {
            val nx = -kotlin.math.sin(h[idx]); val ny = kotlin.math.cos(h[idx])
            for (side in intArrayOf(-1, 1)) {
                val qx = rx[idx] + side * nx * 11f
                val qy = ry[idx] + side * ny * 11f
                var ok = qx > 0f && qx < CircuitLayouts.BOX_W && qy > 0f && qy < CircuitLayouts.BOX_H
                var i = 0
                while (ok && i < n) {
                    if (circDist(i, idx, n) * step > 24f && hypot(rx[i] - qx, ry[i] - qy) < 9f) ok = false
                    i += 3
                }
                if (ok) return true
            }
            return false
        }
        // Straights, longest first, clear of the bridge. The start line goes on the first one with room beside it
        // for a pit lane, near its far end so the grid has road behind it.
        fun collectRuns(flags: BooleanArray): ArrayList<IntArray> {
            val found = ArrayList<IntArray>()
            var start = -1
            for (i in 0 until 2 * n) {
                val idx = i % n
                val ok = flags[idx] && !(cross >= 0 && circDist(idx, cross, n) * step < 22f)
                if (ok && start < 0) start = i
                if ((!ok || i == 2 * n - 1) && start >= 0) {
                    val len = i - start
                    if (start < n && len <= n) found.add(intArrayOf(start, len))
                    start = -1
                }
            }
            return found
        }
        var runs = collectRuns(straight)
        // A tight circuit has no true straight, so its start goes on the gentlest long stretch.
        if ((runs.maxOfOrNull { it[1] } ?: 0) * step < 35f) {
            straight = BooleanArray(n) { abs(turn(it, w)) < 0.07f }
            runs = collectRuns(straight)
        }
        val longest = runs.maxOfOrNull { it[1] } ?: 0
        val pick = runs.sortedByDescending { it[1] }.firstOrNull { it[1] * 2 >= longest && roomAt((it[0] + it[1] / 2) % n) }
            ?: runs.maxByOrNull { it[1] } ?: intArrayOf(0, n / 2)
        val hint = startAt
        val first = if (hint != null) ((cum[nodeAt[hint.first] + (per * hint.second).toInt()] / step).roundToInt()) % n
        else (pick[0] + (pick[1] * 0.78f).toInt()) % n
        var back = 0
        while (back < n / 2 && straight[((first - back - 1) % n + n) % n]) back++
        var fwd = 0
        while (fwd < n / 2 && straight[(first + fwd + 1) % n]) fwd++
        xs = FloatArray(n) { rx[(it + first) % n] }
        ys = FloatArray(n) { ry[(it + first) % n] }
        length = total
        startStraight = (-back * step)..(fwd * step)
        bridgeArc = if (cross >= 0) (((cross - first) % n + n) % n) * step else -1f
        val ca = ArrayList<Float>()
        val cs = ArrayList<Int>()
        for (k in 0 until nodeCount) if (flag[k]) {
            val here = ((cum[nodeAt[k]] / step).roundToInt() - first + n * 2) % n
            ca.add(here * step)
            val back = (7f / step).roundToInt()
            val a = angleDiff(headingAt(here - back), headingAt(here + back))
            cs.add(if (a >= 0f) 1 else -1)
        }
        val order = ca.indices.sortedBy { ca[it] }
        cornerArc = FloatArray(order.size) { ca[order[it]] }
        cornerSide = IntArray(order.size) { cs[order[it]] }
        minX = xs.min(); maxX = xs.max(); minY = ys.min(); maxY = ys.max()
    }

    private fun headingAt(i: Int): Float {
        val n = xs.size
        val a = ((i % n) + n) % n
        val b = (a + 1) % n
        return atan2(ys[b] - ys[a], xs[b] - xs[a])
    }

    /** Position at arc distance [s] (wraps), written into [out] as x, y, heading. */
    fun at(s: Float, out: FloatArray) {
        val n = xs.size
        var f = (s / step) % n
        if (f < 0f) f += n
        val i = f.toInt().coerceIn(0, n - 1)
        val t = f - i
        val j = (i + 1) % n
        out[0] = xs[i] + (xs[j] - xs[i]) * t
        out[1] = ys[i] + (ys[j] - ys[i]) * t
        val k = (i + 2) % n
        val h0 = atan2(ys[j] - ys[i], xs[j] - xs[i])
        val h1 = atan2(ys[k] - ys[j], xs[k] - xs[j])
        out[2] = h0 + angleDiff(h0, h1) * t
    }

    /** How hard the line is turning at [s], radians per unit; positive is a right turn. */
    fun curvature(s: Float): Float {
        val a = FloatArray(3)
        val b = FloatArray(3)
        at(s - 1.5f, a); at(s + 1.5f, b)
        return angleDiff(a[2], b[2]) / 3f
    }

    /** Smallest gap between two parts of the loop that are well apart along it, ignoring the bridge. */
    fun minClearance(apart: Float = 30f): Float {
        val n = xs.size
        val stride = 4
        var best = Float.MAX_VALUE
        var i = 0
        while (i < n) {
            var j = i + stride
            while (j < n) {
                val arc = min((j - i) * step, length - (j - i) * step)
                if (arc >= apart) {
                    val onBridge = bridgeArc >= 0f && (circDist(i, (bridgeArc / step).toInt(), n) * step < 18f || circDist(j, (bridgeArc / step).toInt(), n) * step < 18f)
                    if (!onBridge) best = min(best, hypot(xs[i] - xs[j], ys[i] - ys[j]))
                }
                j += stride
            }
            i += stride
        }
        return best
    }

    companion object {
        private fun bg(t0: Double, t1: Double, t2: Double, t3: Double, t: Double, p0: Float, p1: Float, p2: Float, p3: Float): Float {
            val a1 = (t1 - t) / (t1 - t0) * p0 + (t - t0) / (t1 - t0) * p1
            val a2 = (t2 - t) / (t2 - t1) * p1 + (t - t1) / (t2 - t1) * p2
            val a3 = (t3 - t) / (t3 - t2) * p2 + (t - t2) / (t3 - t2) * p3
            val b1 = (t2 - t) / (t2 - t0) * a1 + (t - t0) / (t2 - t0) * a2
            val b2 = (t3 - t) / (t3 - t1) * a2 + (t - t1) / (t3 - t1) * a3
            return ((t2 - t) / (t2 - t1) * b1 + (t - t1) / (t2 - t1) * b2).toFloat()
        }

        fun angleDiff(a: Float, b: Float): Float {
            var d = b - a
            while (d > PI) d -= (2 * PI).toFloat()
            while (d < -PI) d += (2 * PI).toFloat()
            return d
        }

        private fun circDist(a: Int, b: Int, n: Int): Int {
            val d = abs(a - b) % n
            return min(d, n - d)
        }

        /** The two sample indexes of a self-crossing, the later one is where the road goes over. Null for a loop that never crosses. */
        private fun findCrossing(x: FloatArray, y: FloatArray, step: Float): Pair<Int, Int>? {
            val n = x.size
            val stride = 2
            val apart = (30f / step).toInt()
            var i = 0
            while (i < n) {
                var j = i + apart
                while (j < n - apart) {
                    val ni = (i + stride) % n
                    val nj = (j + stride) % n
                    if (segHit(x[i], y[i], x[ni], y[ni], x[j], y[j], x[nj], y[nj])) return i to j
                    j += stride
                }
                i += stride
            }
            return null
        }

        private fun segHit(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
            fun side(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float) = (qx - px) * (ry - py) - (qy - py) * (rx - px)
            val d1 = side(ax, ay, bx, by, cx, cy)
            val d2 = side(ax, ay, bx, by, dx, dy)
            val d3 = side(cx, cy, dx, dy, ax, ay)
            val d4 = side(cx, cy, dx, dy, bx, by)
            return d1 * d2 < 0f && d3 * d4 < 0f
        }
    }
}
