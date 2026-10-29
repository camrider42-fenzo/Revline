package com.revline.idle.ui

import com.revline.idle.model.Induction
import com.revline.idle.model.exhaustNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EngineToneTest {
    private val stepDeg = 3000f * EngineMotion.VISUAL_CRANK / 44100f
    @Test
    fun cylinderCountChangesTheVoice() {
        val single = EngineTone.character(1)
        val four = EngineTone.character(4)
        val eight = EngineTone.character(8)
        val twelve = EngineTone.character(12)
        assertTrue(single.sub > four.sub * 2.0)
        assertTrue(EngineTone.fundamentalHz(39.0) * single.pitch > EngineTone.fundamentalHz(93.0) * eight.pitch * 2.0)
        assertTrue(four.pitch > eight.pitch * 2.0)
        assertTrue(twelve.pitch > eight.pitch * 1.5)
        assertTrue(eight.sub > four.sub * 3.0)
        assertTrue(four.crack > eight.crack * 2.0)
        assertTrue(single.boom < four.boom)
    }

    @Test
    fun boreSetsPitchAndCompressionSetsTheCrack() {
        val moped = EngineTone.fundamentalHz(39.0)
        val v8 = EngineTone.fundamentalHz(94.0)
        assertTrue("moped $moped v8 $v8", moped > v8 * 2.0)
        assertTrue(EngineTone.brightness(13.5) > EngineTone.brightness(8.2) + 0.5)
        assertEquals(0.0, EngineTone.brightness(7.0), 0.0)
        assertEquals(1.0, EngineTone.brightness(16.0), 0.0)
    }

    @Test
    fun exhaustAndCompressionChangeTheHit() {
        val stock = exhaustNote(1.0, 0, 0, 0, 0, 0, false)
        val headers = exhaustNote(0.76, 4, 0, 0, 0, 0, false)
        val cat = exhaustNote(0.82, 0, 0, 0, 4, 0, false)
        val cam = exhaustNote(1.0, 0, 0, 0, 0, 4, false)
        val full = exhaustNote(0.40, 4, 3, 3, 4, 4, true)
        assertTrue("stock open ${stock.open}", stock.open < 0.12)
        assertTrue("headers rasp ${headers.rasp} vs cat ${cat.rasp}", headers.rasp > cat.rasp + 0.35)
        assertTrue("headers open ${headers.open}", headers.open > stock.open + 0.35)
        assertTrue("cat bark ${cat.bark}", cat.bark > stock.bark + 0.25)
        assertTrue(cat.rasp < 0.05)
        assertTrue("cam lope ${cam.lope}", cam.lope > 0.7)
        assertTrue(full.open > 0.85)

        val mild = band(EngineEar(90.0, 8.2, 1, 1, throttle = 0.8, running = true))
        val squeezed = band(EngineEar(90.0, 13.5, 1, 1, throttle = 0.8, running = true))
        assertTrue(
            "low compression should stay woollier (${mild.low}/${mild.high} vs ${squeezed.low}/${squeezed.high})",
            mild.low / mild.high > squeezed.low / squeezed.high * 1.2,
        )
        val muffled = band(EngineEar(86.0, 11.0, 4, 1, throttle = 0.8, running = true))
        val openPipe = band(
            EngineEar(
                86.0, 11.0, 4, 1,
                throttle = 0.8,
                running = true,
                exhaustOpen = headers.open,
                exhaustRasp = headers.rasp,
                exhaustBark = headers.bark,
            ),
        )
        assertTrue(
            "headers should bring the crack (${openPipe.high} vs ${muffled.high})",
            openPipe.high > muffled.high * 2.0,
        )
    }

    @Test
    fun theCrackLandsOnTheFlame() {
        val ear = EngineEar(86.0, 11.5, 4, 1, throttle = 0.7, running = true)
        val loud = loudness(ear, 0f)
        val quiet = loudness(ear, 160f)
        assertTrue("on the flame $loud vs between fires $quiet", loud > quiet * 2.0)
    }

    @Test
    fun theHitIsALowBlast() {
        val ear = EngineEar(90.0, 12.0, 1, 1, throttle = 0.8, running = true)
        val voice = EngineVoice()
        repeat(3000) { voice.sample(ear, 200f, 44100) }
        val front = FloatArray(1800) { voice.sample(ear, it * stepDeg, 44100) }
        val tailStart = 7000
        val tail = FloatArray(1500) { voice.sample(ear, (tailStart + it) * stepDeg, 44100) }
        val frontEnergy = energy(front)
        val tailEnergy = energy(tail)
        assertTrue("front $frontEnergy vs later in the flame $tailEnergy", frontEnergy > tailEnergy * 1.4)
        var low = 0.0
        var high = 0.0
        var lp = 0.0
        for (s in front) {
            lp += (s - lp) * 0.018
            low += lp * lp
            val rest = s - lp
            high += rest * rest
        }
        assertTrue("low $low vs high $high", low > high * 1.15)
    }

    @Test
    fun theExplosionIsLouderThanTheAir() {
        val ear = EngineEar(86.0, 11.0, 1, 1, throttle = 1.0, running = true, induction = Induction.TURBO)
        val voice = EngineVoice()
        repeat(2500) { voice.sample(ear, 420f, 44100) }
        val gap = window(voice, ear, 420f, 900)
        val bang = window(voice, ear, 0f, 900)
        assertTrue("blowdown $bang vs intake air $gap", bang > gap * 3.0)
    }

    @Test
    fun intakeAirFollowsThePedalAndTheCharger() {
        assertEquals(0.0, EngineTone.airGain(Induction.TURBO, 0.0), 0.0)
        assertTrue(EngineTone.airGain(Induction.NA, 1.0) > EngineTone.airGain(Induction.NA, 0.25) * 2.0)
        assertTrue(EngineTone.airGain(Induction.TURBO, 1.0) > EngineTone.airGain(Induction.NA, 1.0))
        assertTrue(EngineTone.airGain(Induction.SUPERCHARGER, 1.0) > EngineTone.airGain(Induction.NA, 1.0))
        val quiet = loudness(EngineEar(86.0, 10.0, 4, 1, 0.25, true, Induction.NA), 160f)
        val open = loudness(EngineEar(86.0, 10.0, 4, 1, 1.0, true, Induction.NA), 160f)
        val blown = loudness(EngineEar(86.0, 10.0, 4, 1, 1.0, true, Induction.TURBO), 160f)
        assertTrue("open intake $open vs cracked $quiet", open > quiet)
        assertTrue("turbo $blown vs open intake $open", blown > open)
    }

    @Test
    fun theLimiterChopsTheExhaust() {
        val ear = EngineEar(86.0, 10.5, 4, 1, throttle = 1.0, running = true, rpm = 7000.0, redlineRpm = 7000.0)
        fun energy(cut: Boolean): Float {
            val voice = EngineVoice()
            var sum = 0f
            var angle = 40f
            repeat(8_000) {
                sum += abs(voice.sample(ear.copy(limiter = cut), angle, 44100))
                angle += 0.5f
                if (angle >= 720f) angle -= 720f
            }
            return sum
        }
        val open = energy(false)
        val cut = energy(true)
        assertTrue("cut $cut vs open $open", cut < open * 0.82)
        assertTrue("cut $cut still has a note", cut > open * 0.15)
    }

    @Test
    fun aStallIsSilentAndTheCrankMatchesTheDrawing() {
        val ear = EngineEar(86.0, 10.0, 1, 1, throttle = 0.0, running = false)
        assertEquals(0f, EngineVoice().sample(ear, 0f, 44100), 0f)
        assertEquals(20f, EngineMotion.advanceCrank(0f, 1000f, 0.1f), 0.01f)
        val inline = EngineMotion.flameCycles(10f, 4, 1)
        val offsets = EngineMotion.firingOffsets(4)
        inline.forEachIndexed { index, cycle ->
            assertEquals(EngineMotion.cycle(10f + offsets[index]), cycle, 0.01f)
        }
        assertEquals(8, EngineMotion.flameCycles(0f, 8, 2).size)
    }

    private fun window(voice: EngineVoice, ear: EngineEar, angle: Float, count: Int): Float {
        var sum = 0f
        var a = angle
        repeat(count) {
            sum += abs(voice.sample(ear, a, 44100))
            a += stepDeg
            if (a >= 720f) a -= 720f
        }
        return sum / count
    }

    private data class Band(val low: Double, val high: Double)

    /** Low versus high energy across the front of a flame, after the voice has settled. */
    private fun band(ear: EngineEar): Band {
        val voice = EngineVoice()
        repeat(3000) { voice.sample(ear, 200f, 44100) }
        val front = FloatArray(1800) { voice.sample(ear, it * stepDeg, 44100) }
        var low = 0.0
        var high = 0.0
        var lp = 0.0
        for (s in front) {
            lp += (s - lp) * 0.018
            low += lp * lp
            val rest = s - lp
            high += rest * rest
        }
        return Band(low, high)
    }

    private fun energy(samples: FloatArray): Float {
        var sum = 0f
        for (s in samples) sum += abs(s)
        return sum / samples.size
    }

    private fun loudness(ear: EngineEar, angle: Float): Float {
        val voice = EngineVoice()
        var sum = 0f
        var a = angle
        val step = 3000f * EngineMotion.VISUAL_CRANK / 44100f
        repeat(400) {
            sum += abs(voice.sample(ear, a, 44100))
            a += step
            if (a >= 720f) a -= 720f
        }
        return sum
    }
}
