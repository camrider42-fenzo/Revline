package com.revline.idle.ui

import com.revline.idle.model.Induction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EngineToneTest {
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
