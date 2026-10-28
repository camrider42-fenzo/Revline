package com.revline.idle.ui

import com.revline.idle.model.Induction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EngineToneTest {
    private val stepDeg = 3000f * EngineMotion.VISUAL_CRANK / 44100f
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
