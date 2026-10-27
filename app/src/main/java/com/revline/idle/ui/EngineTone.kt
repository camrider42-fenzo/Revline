package com.revline.idle.ui

import kotlin.math.PI
import kotlin.math.sin

/**
 * The voice of one crank. Bore sets the pitch. Compression sets how hard the
 * crack is. The pulses are the same cylinder events the drawing paints.
 */
object EngineTone {
    /** Bigger bore, lower note. A 39 mm jug sits about an octave above a 94 mm one. */
    fun fundamentalHz(boreMm: Double): Double =
        9800.0 / boreMm.coerceIn(32.0, 120.0)

    /** 0 is a soft low-compression thump. 1 is a sharp high-compression crack. */
    fun brightness(compression: Double): Double =
        ((compression - 7.6) / 6.4).coerceIn(0.0, 1.0)
}

/**
 * Oscillator state for [EngineTone]. One instance per audio stream.
 */
class EngineVoice {
    private var p1 = 0.2
    private var p2 = 1.1
    private var p3 = 2.4
    private var noise = 0x27BB2EE
    private var level = 0.0

    fun sample(ear: EngineEar, angleDeg: Float, sampleRate: Int): Float {
        val target = if (ear.running) 0.22 + 0.78 * ear.throttle.coerceIn(0.0, 1.0) else 0.0
        level += (target - level) * 0.002
        if (level < 0.004) return 0f
        val bore = EngineTone.fundamentalHz(ear.boreMm)
        val bright = EngineTone.brightness(ear.compression).toFloat()
        val step = 2.0 * PI / sampleRate
        p1 += step * bore
        p2 += step * bore * 2.02
        p3 += step * bore * 3.07
        if (p1 > 1e6) {
            p1 %= 2.0 * PI
            p2 %= 2.0 * PI
            p3 %= 2.0 * PI
        }
        val cycles = EngineMotion.flameCycles(angleDeg, ear.cylinders, ear.banks)
        var crack = 0.0
        var exhaust = 0.0
        for (cycle in cycles) {
            val burn = EngineMotion.burn(cycle)
            if (burn > 0.02f) {
                val env = burn.toDouble().let { it * it }
                val body = sin(p1) * (0.55 - 0.28 * bright) +
                    sin(p2) * (0.12 + 0.70 * bright) +
                    sin(p3) * (0.04 + 0.48 * bright)
                crack += body * env
                crack += nextNoise() * EngineMotion.spark(cycle) * (0.08 + 0.50 * bright)
            }
            val puff = EngineMotion.puff(cycle)
            if (puff != null) {
                val hush = sin(Math.PI * puff).toFloat()
                exhaust += nextNoise() * hush * (0.45 + 0.55 * (1f - bright))
            }
        }
        val share = 1.0 / kotlin.math.sqrt(cycles.size.toDouble())
        val mixed = (crack * 0.72 + exhaust * 0.10) * share
        val out = mixed * level * (0.85 + 0.15 * (1.0 - bright))
        return out.coerceIn(-1.0, 1.0).toFloat()
    }

    private fun nextNoise(): Double {
        noise = noise * 1664525 + 1013904223
        return ((noise ushr 9) and 0x7FFF) / 16384.0 - 1.0
    }
}

/** What the audio thread needs. Written on the frame clock, read while mixing. */
data class EngineEar(
    val boreMm: Double,
    val compression: Double,
    val cylinders: Int,
    val banks: Int,
    val throttle: Double,
    val running: Boolean,
)
