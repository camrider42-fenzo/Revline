package com.revline.idle.ui

import com.revline.idle.model.Induction
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * The voice of one crank. Bore sets the pitch. Compression sets how hard the
 * blast is. The pulses are the same cylinder events the drawing paints.
 */
object EngineTone {
    /** Bigger bore, lower note. A 39 mm jug sits about an octave above a 94 mm one. */
    fun fundamentalHz(boreMm: Double): Double =
        9800.0 / boreMm.coerceIn(32.0, 120.0)

    /** 0 is a soft low-compression thump. 1 is a harder, shorter blast. Both stay low. */
    fun brightness(compression: Double): Double =
        ((compression - 7.6) / 6.4).coerceIn(0.0, 1.0)

    /**
     * How much intake rush to mix in. Closed is silent. Wide open stays under
     * the blast. A turbo whooshes more than an open intake.
     */
    fun airGain(induction: Induction, throttle: Double): Double {
        if (throttle <= 0.0) return 0.0
        val open = throttle.coerceIn(0.0, 1.0).pow(1.35)
        val kind = when (induction) {
            Induction.NA -> 0.014
            Induction.SUPERCHARGER -> 0.022
            Induction.TURBO -> 0.028
            Induction.TWIN_TURBO -> 0.025
        }
        return kind * open
    }
}

/**
 * Oscillator state for [EngineTone]. One instance per audio stream.
 */
class EngineVoice {
    private var pSub = 0.7
    private var p1 = 0.2
    private var p2 = 1.1
    private var whine = 0.4
    private var airLp = 0.0
    private var blastLp = 0.0
    private var noise = 0x27BB2EE
    private var level = 0.0

    fun sample(ear: EngineEar, angleDeg: Float, sampleRate: Int): Float {
        val target = if (ear.running) 0.22 + 0.78 * ear.throttle.coerceIn(0.0, 1.0) else 0.0
        level += (target - level) * 0.002
        if (level < 0.004) return 0f
        val bore = EngineTone.fundamentalHz(ear.boreMm)
        val bright = EngineTone.brightness(ear.compression).toFloat()
        val cycles = EngineMotion.flameCycles(angleDeg, ear.cylinders, ear.banks)
        val hottest = cycles.maxOf { EngineMotion.burn(it) }.toDouble()
        // The boom starts near the bore note and falls through the flame.
        val fall = 0.62 + 1.35 * hottest.pow(2.0)
        val step = 2.0 * PI / sampleRate
        pSub += step * bore * 0.55 * fall
        p1 += step * bore
        p2 += step * bore * 1.97
        if (pSub > 1e6) {
            pSub %= 2.0 * PI
            p1 %= 2.0 * PI
            p2 %= 2.0 * PI
        }
        val blastRaw = nextNoise()
        blastLp += (blastRaw - blastLp) * (0.022 + 0.12 * hottest.pow(2.4))
        var hit = 0.0
        var exhaust = 0.0
        for (cycle in cycles) {
            val burn = EngineMotion.burn(cycle)
            if (burn > 0.02f) {
                val decay = burn.toDouble()
                val front = decay.pow(3.6)
                val boom = decay.pow(1.05)
                val body = sin(pSub) * (1.05 - 0.08 * bright) +
                    sin(p1) * (0.30 + 0.10 * bright)
                hit += body * boom
                hit += blastLp * front * (1.15 + 0.45 * bright)
                hit += sin(p2) * front * (0.05 + 0.14 * bright)
            }
            val puff = EngineMotion.puff(cycle)
            if (puff != null) {
                val hush = sin(Math.PI * puff).toFloat()
                exhaust += nextNoise() * hush * (0.45 + 0.55 * (1f - bright))
            }
        }
        val share = 1.0 / kotlin.math.sqrt(cycles.size.toDouble())
        val mixed = (softClip(hit) * 0.82 + exhaust * 0.08) * share + air(ear, cycles, sampleRate)
        val out = mixed * level * (0.90 + 0.10 * (1.0 - bright))
        return out.coerceIn(-1.0, 1.0).toFloat()
    }

    /** A little grit on the attack, without a hard digital edge. */
    private fun softClip(x: Double): Double {
        val a = kotlin.math.abs(x)
        if (a < 1e-8) return 0.0
        return x / (1.0 + a * 0.50)
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
        return (airLp * 0.85 + tone * 0.72) * gain * breath * 2.3
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
