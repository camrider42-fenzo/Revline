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
 * The eight circuits as real overhead centerlines (see [CircuitData]), fitted into a 160 wide box whose height follows
 * the track's own shape, y pointing down. The shape is true to the ground; only the drawn road is wider than the real
 * one. Plain numbers, so tests can read them without a canvas.
 */
internal object CircuitLayouts {
    const val BOX_W = 160f
    const val MIN_ASPECT = 0.7f
    const val MAX_ASPECT = 1.8f

    private val specs: Map<String, TrackData> = CircuitData.all.associateBy { it.id }

    val ids: Set<String> get() = specs.keys

    private val built = HashMap<String, CircuitLine>()

    fun has(id: String) = id in specs

    /** The smoothed line for a circuit; unknown ids get Monza's. Built once. */
    fun of(id: String): CircuitLine = synchronized(built) {
        val key = if (id in specs) id else "monza"
        built.getOrPut(key) { CircuitLine(specs.getValue(key)) }
    }

    /** Width over height of the picture a circuit gets. */
    fun aspect(id: String): Float = of(id).aspect
}

/**
 * One circuit's centerline sampled at an even step, starting on the start/finish line and running in the driving
 * direction. Lengths are in layout units, [metersPerUnit] turns them back into ground metres. [pos] and [heading]
 * answer "where is the car at this distance".
 */
internal class CircuitLine(data: TrackData) {
    val id: String = data.id
    val officialKm: Double = data.officialKm
    val roadShare: Float = data.roadShare
    /** Which side of the start straight the real pit lane is on, +1 right of travel. */
    val pitSide: Int = data.pitSide
    val corners: Int
    /** Even samples; the line closes from the last sample back to the first. */
    val xs: FloatArray
    val ys: FloatArray
    val length: Float
    val step: Float
    /** Ground metres in one layout unit. */
    val metersPerUnit: Float
    /** The line's true length in metres. */
    val lengthM: Double
    val aspect: Float
    val boxW: Float = CircuitLayouts.BOX_W
    val boxH: Float
    /** Arc distance of each official turn, from the start line. */
    val cornerArc: FloatArray
    /** +1 where the corner turns right (clockwise on the page), -1 left. */
    val cornerSide: IntArray
    /** Arc distance of the start of the self-crossing's bridge, or -1 when the loop never crosses itself. */
    val bridgeArc: Float
    val minX: Float
    val maxX: Float
    val minY: Float
    val maxY: Float
    /** How far the road stays straight enough behind and ahead of the start line, in units. */
    val startStraight: ClosedFloatingPointRange<Float>
    /** How far behind the start line the road keeps a heading within about 30 degrees of the line's, where the grid can stand. */
    val gridRoom: Float

    init {
        // The nodes, in metres.
        val count = data.steps.length / 2 + 1
        val mx = DoubleArray(count)
        val my = DoubleArray(count)
        var qx = data.x0
        var qy = data.y0
        mx[0] = qx * 0.5; my[0] = qy * 0.5
        for (i in 1 until count) {
            qx += data.steps[2 * i - 2].code - 64
            qy += data.steps[2 * i - 1].code - 64
            mx[i] = qx * 0.5; my[i] = qy * 0.5
        }
        val bw = mx.max() - mx.min()
        val bh = my.max() - my.min()
        aspect = (bw / bh).toFloat().coerceIn(CircuitLayouts.MIN_ASPECT, CircuitLayouts.MAX_ASPECT)
        boxH = boxW / aspect
        val scale = min(boxW / bw, boxH / bh)
        metersPerUnit = (1.0 / scale).toFloat()
        val offX = (boxW - bw * scale) / 2.0 - mx.min() * scale
        val offY = (boxH - bh * scale) / 2.0 - my.min() * scale
        val nodeCount = count
        val px = FloatArray(nodeCount) { (mx[it] * scale + offX).toFloat() }
        val py = FloatArray(nodeCount) { (my[it] * scale + offY).toFloat() }
        // Dense spline through the nodes.
        val per = 10
        val dx = ArrayList<Float>(nodeCount * per)
        val dy = ArrayList<Float>(nodeCount * per)
        for (i in 0 until nodeCount) {
            val i0 = (i - 1 + nodeCount) % nodeCount; val i2 = (i + 1) % nodeCount; val i3 = (i + 2) % nodeCount
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
        xs = rx
        ys = ry
        length = total
        lengthM = total.toDouble() * metersPerUnit
        // The grid and pit lane need road that holds its heading around the start line.
        val h = FloatArray(n) { i -> atan2(ry[(i + 1) % n] - ry[i], rx[(i + 1) % n] - rx[i]) }
        val w = (3f / step).roundToInt()
        fun held(dir: Int, limit: Float = 0.12f): Int {
            var k = 0
            while (k < n / 2) {
                val i = dir * (k + 1)
                // Heading averaged over a short window, so a kink in the data does not end the run.
                var a = 0f
                for (q in -w..w) a += angleDiff(h[0], h[(((i + q) % n) + n) % n])
                if (abs(a / (2 * w + 1)) > limit) break
                k++
            }
            return k
        }
        startStraight = (-held(-1) * step)..(held(1) * step)
        gridRoom = held(-1, 0.5f) * step
        val crossing = findCrossing(rx, ry, step)
        bridgeArc = if (crossing != null) crossing.second * step else -1f
        // Official turns: the table holds metres on the line it was measured on, which is a hair shorter than this one.
        val arcs = data.cornerArcM.split(',').map { it.toFloat() }
        val ratio = (lengthM / data.measuredM).toFloat()
        corners = arcs.size
        cornerArc = FloatArray(corners) { (arcs[it] * ratio / metersPerUnit).coerceIn(0f, total - step) }
        cornerSide = IntArray(corners) { if (data.cornerSides[it] == 'R') 1 else -1 }
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

    /** The share of the loop (0 to 1) that runs closer than [gap] units to a part of itself at least [apart] units away along the line, ignoring the bridge. */
    fun tightShare(gap: Float, apart: Float = 30f): Float {
        val n = xs.size
        val stride = 3
        var tight = 0
        var seen = 0
        val br = if (bridgeArc >= 0f) (bridgeArc / step).toInt() else -1
        fun nearBridge(i: Int) = br >= 0 && hypot(xs[i] - xs[br], ys[i] - ys[br]) < 30f
        var i = 0
        while (i < n) {
            seen++
            var near = false
            var j = 0
            while (j < n && !near) {
                val arc = min(abs(i - j) * step, length - abs(i - j) * step)
                if (arc >= apart && hypot(xs[i] - xs[j], ys[i] - ys[j]) < gap) near = !(nearBridge(i) && nearBridge(j))
                j += stride
            }
            if (near) tight++
            i += stride
        }
        return tight.toFloat() / seen
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
