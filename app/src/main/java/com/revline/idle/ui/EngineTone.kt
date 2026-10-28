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

    /**
     * How this cylinder count speaks, on top of the bore.
     * A single chugs high and long. A four screams. A V8 rumbles. A V12 is a smoother, higher sheen.
     */
    data class Character(
        val pitch: Double,
        val sub: Double,
        val crack: Double,
        val boom: Double,
        val rasp: Double,
    )

    fun character(cylinders: Int): Character = when (cylinders) {
        1 -> Character(pitch = 1.0, sub = 1.45, crack = 0.40, boom = 0.50, rasp = 0.04)
        2 -> Character(pitch = 1.10, sub = 1.05, crack = 0.90, boom = 0.72, rasp = 0.32)
        3 -> Character(pitch = 0.90, sub = 0.80, crack = 0.70, boom = 0.78, rasp = 0.42)
        4 -> Character(pitch = 1.70, sub = 0.38, crack = 1.25, boom = 1.20, rasp = 0.12)
        6 -> Character(pitch = 1.05, sub = 0.62, crack = 0.72, boom = 0.95, rasp = 0.08)
        8 -> Character(pitch = 0.55, sub = 1.75, crack = 0.32, boom = 0.48, rasp = 0.06)
        else -> Character(pitch = 1.25, sub = 0.28, crack = 1.05, boom = 1.35, rasp = 0.05)
    }

    /** 0 is a soft low-compression thump. 1 is a harder, shorter blast. Both stay low. */
    fun brightness(compression: Double): Double =
        ((compression - 7.6) / 6.4).coerceIn(0.0, 1.0)

    /**
     * Intake and charger noise, kept under the exhaust pulse the way a tailpipe
     * recording is. Closed is silent. A turbo whooshes more than an open intake,
     * and both stay quieter than the blowdown.
     */
    fun airGain(induction: Induction, throttle: Double): Double {
        if (throttle <= 0.0) return 0.0
        val open = throttle.coerceIn(0.0, 1.0).pow(1.35)
        val kind = when (induction) {
            Induction.NA -> 0.0045
            Induction.SUPERCHARGER -> 0.0065
            Induction.TURBO -> 0.0080
            Induction.TWIN_TURBO -> 0.0072
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
    private var whine = 0.4
    private var airLp = 0.0
    private var blastLp = 0.0
    private var noise = 0x27BB2EE
    private var level = 0.0

    fun sample(ear: EngineEar, angleDeg: Float, sampleRate: Int): Float {
        val target = if (ear.running) 0.22 + 0.78 * ear.throttle.coerceIn(0.0, 1.0) else 0.0
        level += (target - level) * 0.002
        if (level < 0.004) return 0f
        val voice = EngineTone.character(ear.cylinders)
        val spun = if (ear.redlineRpm > 1.0) (ear.rpm / ear.redlineRpm).coerceIn(0.0, 1.15) else 0.5
        val nearRedline = (spun - 0.55).coerceAtLeast(0.0)
        val bore = EngineTone.fundamentalHz(ear.boreMm) * voice.pitch * (1.0 + 0.22 * nearRedline * voice.crack)
        val bright = EngineTone.brightness(ear.compression).toFloat()
        val cycles = EngineMotion.flameCycles(angleDeg, ear.cylinders, ear.banks)
        val hottest = cycles.maxOf { EngineMotion.burn(it) }.toDouble()
        // The boom starts near the bore note and falls through the flame.
        val fall = 0.62 + 1.35 * hottest.pow(2.0)
        val step = 2.0 * PI / sampleRate
        pSub += step * bore * 0.55 * fall
        p1 += step * bore * (1.0 + voice.rasp)
        if (pSub > 1e6) {
            pSub %= 2.0 * PI
            p1 %= 2.0 * PI
        }
        val blastRaw = nextNoise()
        // Blowdown is a short band-limited crack. The rest of the flame is the pipe.
        val crackOpen = hottest.pow(4.2)
        blastLp += (blastRaw - blastLp) * (0.010 + 0.055 * crackOpen)
        var hit = 0.0
        var pipe = 0.0
        cycles.forEachIndexed { index, cycle ->
            val bank = if (ear.banks >= 2 && index % 2 == 1) 0.62 else 1.0
            val burn = EngineMotion.burn(cycle)
            if (burn > 0.02f) {
                val decay = burn.toDouble()
                val boom = decay.pow(voice.boom)
                val crack = decay.pow(5.2)
                val sine = sin(pSub)
                val body = sine * voice.sub * (1.08 - 0.05 * bright) +
                    sine * kotlin.math.abs(sine) * 0.14 * crack
                hit += bank * body * boom
                hit += bank * sin(p1) * boom * voice.rasp.coerceAtLeast(0.08) * (0.8 + bright)
                hit += bank * blastLp * crack * voice.crack * (0.62 + 0.22 * bright)
            }
            val puff = EngineMotion.puff(cycle)
            if (puff != null) {
                val hush = sin(Math.PI * puff)
                pipe += blastLp * hush * 0.035
            }
        }
        val share = 1.0 / kotlin.math.sqrt(cycles.size.toDouble())
        val bang = softClip(hit) * 0.92 * share
        val mixed = bang + pipe * share + air(ear, cycles, sampleRate)
        val out = mixed * level * (0.92 + 0.08 * (1.0 - bright))
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
            Induction.NA -> 0.035 + 0.045 * open
            Induction.SUPERCHARGER -> 0.028 + 0.030 * open
            Induction.TURBO -> 0.018 + 0.022 * open
            Induction.TWIN_TURBO -> 0.022 + 0.026 * open
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
            val bite = if (ear.induction == Induction.SUPERCHARGER) 0.28 else 0.14
            tone = sin(whine) * bite
        }
        var gulp = 0.0
        for (cycle in cycles) {
            val charge = EngineMotion.charge(cycle) ?: continue
            gulp += sin(Math.PI * charge)
        }
        val breath = 0.62 + 0.38 * (gulp / cycles.size)
        return (airLp * 0.50 + tone * 0.28) * gain * breath
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
    val rpm: Double = 3000.0,
    val redlineRpm: Double = 7000.0,
)
