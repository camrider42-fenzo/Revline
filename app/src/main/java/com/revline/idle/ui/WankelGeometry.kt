package com.revline.idle.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * True Wankel KKM geometry: two-lobe epitrochoid housing and a three-flank rotor
 * whose apices ride the bore. Generating radius [R], eccentricity [e], R/e ≈ 7.
 *
 * Housing (parameter α):
 *   x = e·cos(3α) + R·cos(α)
 *   y = e·sin(3α) + R·sin(α)
 *
 * Eccentric shaft angle φ; rotor centre orbits at radius e and the rotor body
 * turns at φ/3. Apex i lands on the housing at α = φ/3 + 2πi/3.
 */
object WankelGeometry {
    /** Mazda-class generating ratio (housing peanut elongation). */
    const val DEFAULT_R_OVER_E = 7.0

    data class Pt(val x: Double, val y: Double) {
        fun dist(o: Pt) = hypot(x - o.x, y - o.y)
        operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
        operator fun times(s: Double) = Pt(x * s, y * s)
    }

    fun housing(alpha: Double, R: Double, e: Double): Pt {
        val a = alpha
        return Pt(e * cos(3.0 * a) + R * cos(a), e * sin(3.0 * a) + R * sin(a))
    }

    fun rotorCenter(shaftRad: Double, e: Double): Pt =
        Pt(e * cos(shaftRad), e * sin(shaftRad))

    fun apex(shaftRad: Double, face: Int, R: Double, e: Double): Pt {
        val c = rotorCenter(shaftRad, e)
        val a = shaftRad / 3.0 + face * (2.0 * PI / 3.0)
        return Pt(c.x + R * cos(a), c.y + R * sin(a))
    }

    /**
     * Reuleaux-like flank: circular arc from apex [i] to [i+1], centred on the
     * opposite apex, with a shallow combustion recess toward the rotor centre.
     */
    fun flankPoint(
        shaftRad: Double,
        face: Int,
        t: Double,
        R: Double,
        e: Double,
        recess: Double = 0.10,
    ): Pt {
        val a0 = apex(shaftRad, face, R, e)
        val a1 = apex(shaftRad, (face + 1) % 3, R, e)
        val opp = apex(shaftRad, (face + 2) % 3, R, e)
        val side = a0.dist(a1)
        // Arc of radius = side about the opposite apex (Reuleaux construction).
        val ang0 = kotlin.math.atan2(a0.y - opp.y, a0.x - opp.x)
        val ang1 = kotlin.math.atan2(a1.y - opp.y, a1.x - opp.x)
        var d = ang1 - ang0
        while (d <= 0) d += 2.0 * PI
        while (d > 2.0 * PI) d -= 2.0 * PI
        // Take the short arc (~60°) along the flank.
        if (d > PI) d -= 2.0 * PI
        val ang = ang0 + d * t.coerceIn(0.0, 1.0)
        var p = Pt(opp.x + side * cos(ang), opp.y + side * sin(ang))
        // Combustion recess: pull mid-flank toward rotor centre.
        val midPull = sin(PI * t.coerceIn(0.0, 1.0))
        val c = rotorCenter(shaftRad, e)
        val inward = Pt(c.x - p.x, c.y - p.y)
        val len = hypot(inward.x, inward.y).coerceAtLeast(1e-9)
        p = Pt(
            p.x + inward.x / len * recess * R * midPull,
            p.y + inward.y / len * recess * R * midPull,
        )
        return p
    }

    /** Max apex→housing gap over a full shaft turn (should be ~0 for true geometry). */
    fun maxApexHousingGap(R: Double, e: Double, steps: Int = 180): Double {
        var worst = 0.0
        for (s in 0 until steps) {
            val phi = 2.0 * PI * s / steps
            for (face in 0 until 3) {
                val tip = apex(phi, face, R, e)
                // Exact housing parameter for this apex.
                val alpha = phi / 3.0 + face * (2.0 * PI / 3.0)
                val on = housing(alpha, R, e)
                worst = maxOf(worst, tip.dist(on))
            }
        }
        return worst
    }

    fun sampleHousing(R: Double, e: Double, n: Int = 96): List<Pt> =
        (0 until n).map { housing(2.0 * PI * it / n, R, e) }

    fun sampleFlank(shaftRad: Double, face: Int, R: Double, e: Double, n: Int = 16): List<Pt> =
        (0..n).map { flankPoint(shaftRad, face, it.toDouble() / n, R, e) }
}
