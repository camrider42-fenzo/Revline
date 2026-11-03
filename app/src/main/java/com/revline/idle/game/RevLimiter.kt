package com.revline.idle.game

import kotlin.math.floor

/**
 * A fuel cut at the top of the tach. The needle and the exhaust dip together,
 * then the engine fires again. Sitting there does not make power.
 */
object RevLimiter {
    const val HZ = 12.5

    /** How far under the redline the needle sits when the cut is open: the highest rpm a car held on the limiter reads. */
    const val HOLD_BELOW = 20.0

    fun cutting(time: Double): Boolean {
        val part = time * HZ
        return part - floor(part) < 0.34
    }

    /** Just under the tach, with a dip each time the cut opens. */
    fun rpm(redline: Double, time: Double): Double =
        if (cutting(time)) redline - 340.0 else redline - HOLD_BELOW
}
