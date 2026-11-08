package com.revline.idle.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The speaker follows the car under 8x, and stays on the dyno note from 8x up. */
class RaceAudioTest {
    @Test fun speedsUnderEightFollowTheCar() {
        for (speed in listOf(0.5, 1.0, 2.0, 4.0)) {
            assertEquals(speed.toString(), RaceAudio.Source.TRACK, RaceAudio.source(speed, playing = true))
        }
    }

    @Test fun eightAndFasterStayOnTheDynoNote() {
        for (speed in listOf(8.0, 16.0, 32.0)) {
            assertEquals(speed.toString(), RaceAudio.Source.DYNO, RaceAudio.source(speed, playing = true))
        }
    }

    @Test fun theSpeedChipsMatchThatSplit() {
        val eight = ReplaySpeed.labels.indexOf("8x")
        val four = ReplaySpeed.labels.indexOf("4x")
        assertEquals(8.0, ReplaySpeed.multiple(eight), 0.0)
        assertEquals(4.0, ReplaySpeed.multiple(four), 0.0)
        assertEquals(RaceAudio.Source.DYNO, RaceAudio.source(ReplaySpeed.multiple(eight), playing = true))
        assertEquals(RaceAudio.Source.TRACK, RaceAudio.source(ReplaySpeed.multiple(four), playing = true))
        assertEquals(RaceAudio.Source.DYNO, RaceAudio.source(ReplaySpeed.multiple(ReplaySpeed.labels.indexOf("16x")), playing = true))
        assertEquals(RaceAudio.Source.DYNO, RaceAudio.source(ReplaySpeed.multiple(ReplaySpeed.labels.indexOf("32x")), playing = true))
        assertEquals(RaceAudio.Source.TRACK, RaceAudio.source(ReplaySpeed.multiple(ReplaySpeed.labels.indexOf("1x")), playing = true))
    }

    @Test fun pauseAndTheFlagSilenceTheSpeaker() {
        assertEquals(RaceAudio.Source.QUIET, RaceAudio.source(1.0, playing = false))
        assertEquals(RaceAudio.Source.QUIET, RaceAudio.source(8.0, playing = false))
        assertEquals(RaceAudio.Source.QUIET, RaceAudio.dragSource(running = false))
        assertEquals(RaceAudio.Source.TRACK, RaceAudio.dragSource(running = true))
    }

    @Test fun theSpokenRpmEasesTowardTheCarWithoutAJumpOrAnOvershoot() {
        val rise = RaceAudio.smoothRpm(1000f, 7000f, 0.016f)
        assertTrue("a frame only starts the rise ($rise)", rise > 1000f && rise < 3000f)
        val drop = RaceAudio.smoothRpm(7000f, 4500f, 0.016f)
        assertTrue("a shift is a drop, not a click ($drop)", drop < 7000f && drop > 6000f)
        assertEquals(4000f, RaceAudio.smoothRpm(4000f, 8000f, 0f), 0f)
        var rpm = 3000f
        repeat(80) { rpm = RaceAudio.smoothRpm(rpm, 6000f, 0.016f) }
        assertEquals(6000f, rpm, 40f)
        val over = RaceAudio.smoothRpm(5000f, 2000f, 0.2f)
        assertTrue(over > 2000f && over < 5000f)
    }

    @Test fun aBrakeLiftsThePedalAndTheRedlineCutsFuel() {
        assertTrue(RaceAudio.circuitThrottle(braking = true) < 0.3f)
        assertTrue(RaceAudio.circuitThrottle(braking = false) > 0.8f)
        assertTrue(RaceAudio.circuitLimiter(6900.0, 7000.0))
        assertTrue(!RaceAudio.circuitLimiter(5000.0, 7000.0))
    }
}
