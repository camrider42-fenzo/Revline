package com.revline.idle.ui

import com.revline.idle.game.CircuitPace
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** A track's corner radii in true metres every [ds], for the speed solver. 0 is a straight. */
internal class CircuitRadii(val ds: Double, val radius: DoubleArray, val apex: IntArray) {
    val n: Int get() = radius.size

    /** The sample at arc distance [s] units on [line]. */
    fun sampleAt(line: CircuitLine, s: Float): Int = Math.floorMod(((s * line.metersPerUnit) / ds).roundToInt(), n)

    companion object {
        /** A racing line is wider than the centerline: it cuts the inside and carries more speed than the road's own radius. */
        const val LINE_GAIN = 1.0
        const val HALF_MIN = 30.0
        /** Beyond this radius the road counts as straight. */
        const val STRAIGHT_M = 900.0
        /** Even the tightest hairpin has this much room on the racing line. */
        const val TIGHTEST_M = 11.0

        private val cache = HashMap<String, CircuitRadii>()

        fun of(line: CircuitLine): CircuitRadii = synchronized(cache) { cache.getOrPut(line.id) { build(line) } }

        internal fun build(line: CircuitLine, halfMin: Double = HALF_MIN, gain: Double = LINE_GAIN, straight: Double = STRAIGHT_M): CircuitRadii {
            val lengthM = line.lengthM
            val n = max(8, (lengthM / CircuitPace.STEP).roundToInt())
            val ds = lengthM / n
            val mpu = line.metersPerUnit.toDouble()
            // Heading over a stretch of road either side: long enough to see past the spline's samples.
            val halfM = max(halfMin, 1.25 * line.step * mpu)
            val half = (halfM / mpu).toFloat()
            val a = FloatArray(3)
            val b = FloatArray(3)
            val radius = DoubleArray(n) { i ->
                val s = ((i + 0.5) * ds / mpu).toFloat()
                line.at(s - half, a); line.at(s + half, b)
                val turn = abs(CircuitLine.angleDiff(a[2], b[2]).toDouble())
                val r = if (turn < 1e-6) 0.0 else 2.0 * halfM / turn
                if (r <= 0.0 || r > straight) 0.0 else max(TIGHTEST_M, r * gain)
            }
            val apex = IntArray(line.cornerArc.size) { k ->
                val near = Math.floorMod(((line.cornerArc[k] * mpu) / ds).roundToInt(), n)
                var best = near
                val reach = max(2, (45.0 / ds).roundToInt())
                for (d in -reach..reach) {
                    val j = Math.floorMod(near + d, n)
                    if (radius[j] > 0.0 && (radius[best] == 0.0 || radius[j] < radius[best])) best = j
                }
                best
            }
            return CircuitRadii(ds, radius, apex)
        }
    }
}
