package com.revline.idle.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Four-stroke timing for the bench drawing. Cycle degrees run 0..720 with 0 at
 * firing TDC: power to 180, exhaust to 360, intake to 540, compression to 720.
 */
object EngineMotion {
    /** Per-cylinder cycle offsets. Their crank positions are the offset mod 360. */
    fun firingOffsets(count: Int): FloatArray = when (count) {
        1 -> floatArrayOf(0f)
        2 -> floatArrayOf(0f, 540f)
        3 -> floatArrayOf(0f, 480f, 240f)
        4 -> floatArrayOf(0f, 180f, 540f, 360f)
        else -> FloatArray(count) { it * 720f / count }
    }

    fun cycle(deg: Float): Float = ((deg % 720f) + 720f) % 720f

    /** Wrist-pin distance from the crank center along the bore. */
    fun travel(crankDeg: Float, throwLen: Float, rodLen: Float): Float {
        val a = Math.toRadians(crankDeg.toDouble())
        val side = sin(a) * throwLen
        return (cos(a) * throwLen + sqrt((rodLen * rodLen - side * side).coerceAtLeast(0.0))).toFloat()
    }

    fun intakeLift(cycleDeg: Float): Float = lift(cycleDeg, 350f, 580f)

    fun exhaustLift(cycleDeg: Float): Float = lift(cycleDeg, 140f, 370f)

    /** Flame brightness: full at firing TDC, gone by 150° into the power stroke. */
    fun burn(cycleDeg: Float): Float {
        val c = cycle(cycleDeg)
        if (c >= 150f) return 0f
        return (1f - c / 150f).pow(1.6f)
    }

    /** Plug flash in the last 24° before firing TDC and a few degrees after. */
    fun spark(cycleDeg: Float): Float {
        val c = cycle(cycleDeg)
        return when {
            c >= 696f -> 1f - (720f - c) / 24f
            c < 6f -> 1f - c / 6f
            else -> 0f
        }
    }

    /** 0..1 position of this cylinder's exhaust pulse along the pipe, or null when none is out. */
    fun puff(cycleDeg: Float): Float? {
        val c = cycle(cycleDeg)
        return if (c in 150f..510f) (c - 150f) / 360f else null
    }

    /** 0..1 position of the intake charge on its way to the valve, or null outside the intake stroke. */
    fun charge(cycleDeg: Float): Float? {
        val c = cycle(cycleDeg)
        return if (c in 330f..570f) (c - 330f) / 240f else null
    }

    private fun lift(cycleDeg: Float, open: Float, close: Float): Float {
        var c = cycle(cycleDeg)
        if (c < open) c += 720f
        if (c > close) return 0f
        val u = (c - open) / (close - open)
        return sin(PI * u).toFloat().coerceAtLeast(0f)
    }
}
