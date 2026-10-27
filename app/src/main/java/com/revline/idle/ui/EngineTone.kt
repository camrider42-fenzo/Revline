package com.revline.idle.ui

import com.revline.idle.model.Induction
import kotlin.math.PI
import kotlin.math.pow
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

    /**
     * How much intake rush to mix in. Closed is silent. Wide open is still
     * small next to the crack. A turbo whooshes more than an open intake.
     */
    fun airGain(induction: Induction, throttle: Double): Double {
        if (throttle <= 0.0) return 0.0
        val open = throttle.coerceIn(0.0, 1.0).pow(1.35)
        val kind = when (induction) {
            Induction.NA -> 0.040
            Induction.SUPERCHARGER -> 0.062
            Induction.TURBO -> 0.078
            Induction.TWIN_TURBO -> 0.070
        }
        return kind * open
    }
}

/**
 * Oscillator state for [EngineTone]. One instance per audio stream.
 */
class EngineVoice {
    private var p1 = 0.2
    private var p2 = 1.1
    private var p3 = 2.4
    private var whine = 0.4
    private var airLp = 0.0
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
        val mixed = (crack * 0.72 + exhaust * 0.10) * share + air(ear, cycles, sampleRate)
        val out = mixed * level * (0.85 + 0.15 * (1.0 - bright))
        return out.coerceIn(-1.0, 1.0).toFloat()
    }

    /** A little intake rush. Throttle sets the amount. The charger sets the color. */
    private fun air(ear: EngineEar, cycles: FloatArray, sampleRate: Int): Double {
        val gain = EngineTone.airGain(ear.induction, ear.throttle)
        if (gain <= 0.0) return 0.0
        val open = ear.throttle.coerceIn(0.0, 1.0)
        val raw = nextNoise()
        val cutoff = when (ear.induction) {
            Induction.NA -> 0.16 + 0.22 * open
            Induction.SUPERCHARGER -> 0.07 + 0.08 * open
            Induction.TURBO -> 0.035 + 0.05 * open
            Induction.TWIN_TURBO -> 0.05 + 0.07 * open
        }
        airLp += (raw - airLp) * cutoff
        val whineHz = when (ear.induction) {
            Induction.NA -> 0.0
            Induction.SUPERCHARGER -> 280.0 + 1600.0 * open
            Induction.TURBO -> 90.0 + 420.0 * open
            Induction.TWIN_TURBO -> 140.0 + 640.0 * open
        }
        var tone = 0.0
        if (whineHz > 0.0) {
            whine += 2.0 * PI * whineHz / sampleRate
            if (whine > 1e6) whine %= 2.0 * PI
            val bite = if (ear.induction == Induction.SUPERCHARGER) 0.55 else 0.28
            tone = sin(whine) * bite
        }
        var gulp = 0.0
        for (cycle in cycles) {
            val charge = EngineMotion.charge(cycle) ?: continue
            gulp += sin(Math.PI * charge)
        }
        val breath = 0.62 + 0.38 * (gulp / cycles.size)
        return (airLp * 0.85 + tone) * gain * breath * 6.0
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
    val induction: Induction = Induction.NA,
)
