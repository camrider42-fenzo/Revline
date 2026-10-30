package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tree: a live engine, a car that stays put until the clutch grabs, and a red light if it does. */
class RaceStagingTest {
    private val dt = 1.0 / 60.0

    private fun car(cc: Double = 1998.0) = RaceDrive.Setup(
        peakHp = 180.0,
        peakTqLbFt = 190.0,
        redline = 7000.0,
        idle = 900.0,
        massKg = 980.0,
        ratios = listOf(2.52, 1.74, 1.27, 1.00),
        finalDrive = 3.73,
        efficiency = 0.88,
        gripN = 980.0 * 9.81 * 0.55,
        drag = 0.72,
        shiftSeconds = 0.36,
        displacementCc = cc,
    )

    private fun hold(setup: RaceDrive.Setup, run: RaceDrive.Run, seconds: Double, throttle: Double, clutch: Double): Boolean {
        var t = 0.0
        var jumped = false
        while (t < seconds && !jumped) {
            jumped = RaceDrive.stage(setup, run, throttle, dt, clutch)
            t += dt
        }
        return jumped
    }

    @Test
    fun revsFollowTheThrottleOnTheTreeWithTheClutchIn() {
        val setup = car()
        val run = RaceDrive.fresh(setup)
        assertEquals(setup.idle, run.rpm, 1.0)
        hold(setup, run, 0.4, 0.5, 1.0)
        val half = run.rpm
        assertTrue("half throttle should lift the needle, was $half", half > setup.idle + 1500.0)
        assertTrue("half throttle stays under the limiter, was $half", half < setup.redline * 0.9)
        hold(setup, run, 1.5, 1.0, 1.0)
        assertTrue("full throttle should reach the limiter, was ${run.rpm}", run.limiter && run.rpm > setup.redline * 0.9)
        assertTrue("the limiter holds the tach under the redline, was ${run.rpm}", run.rpm <= setup.redline)
        assertEquals(0.0, run.distance, 1e-9)
        assertEquals(0.0, run.speed, 1e-9)
    }

    @Test
    fun theNeedleFallsBackToIdleWhenYouLift() {
        val setup = car()
        val run = RaceDrive.fresh(setup)
        hold(setup, run, 1.5, 1.0, 1.0)
        val top = run.rpm
        hold(setup, run, 0.3, 0.0, 1.0)
        assertTrue("falling, was $top now ${run.rpm}", run.rpm < top - 1000.0 && run.rpm > setup.idle)
        assertFalse(run.limiter)
        hold(setup, run, 3.0, 0.0, 1.0)
        assertEquals(setup.idle, run.rpm, 60.0)
    }

    @Test
    fun theLimiterCutsInsteadOfSittingStill() {
        val setup = car()
        val run = RaceDrive.fresh(setup)
        hold(setup, run, 1.5, 1.0, 1.0)
        val seen = mutableSetOf<Int>()
        repeat(60) {
            RaceDrive.stage(setup, run, 1.0, dt, 1.0)
            seen += run.rpm.toInt()
        }
        assertTrue("limiter should bounce the tach, saw $seen", seen.size >= 2)
        assertTrue(seen.all { it <= setup.redline })
    }

    @Test
    fun theTreeDoesNotRunTheRaceClock() {
        val setup = car()
        val run = RaceDrive.fresh(setup)
        hold(setup, run, 2.0, 1.0, 1.0)
        assertEquals(0.0, run.time, 1e-9)
        assertTrue(run.clock > 1.9)
    }

    @Test
    fun aSmallEngineWindsUpFasterThanABigOne() {
        fun secondsToLimiter(cc: Double): Double {
            val setup = car(cc)
            val run = RaceDrive.fresh(setup)
            var t = 0.0
            while (!run.limiter && t < 10.0) {
                RaceDrive.stage(setup, run, 1.0, dt, 1.0)
                t += dt
            }
            return t
        }
        val moped = secondsToLimiter(49.0)
        val four = secondsToLimiter(1998.0)
        val v12 = secondsToLimiter(6500.0)
        assertTrue("moped $moped four $four v12 $v12", moped < four && four < v12)
        assertTrue("v12 should not take forever, was $v12", v12 < 1.6)
        assertTrue("moped is nearly instant, was $moped", moped < 0.45)
    }

    @Test
    fun setupCarriesTheEnginesDisplacementSoEveryBlockHasItsOwnInertia() {
        val rates = Block.entries.indices.map { RaceDrive.revRate(RaceDrive.setup(GameState(prestiges = it))) }
        assertTrue("rates $rates", rates.zipWithNext().all { (a, b) -> b <= a + 1e-9 } && rates.first() > rates.last())
    }

    @Test
    fun withTheClutchInOrBelowTheBiteTheCarStaysPut() {
        val setup = car()
        for (pedal in listOf(1.0, 0.9, RaceDrive.BITE_START, RaceDrive.BITE_START + 0.001)) {
            val run = RaceDrive.fresh(setup)
            val jumped = hold(setup, run, 4.0, 1.0, pedal)
            assertFalse("pedal $pedal jumped", jumped)
            assertEquals("pedal $pedal moved", 0.0, run.distance, 1e-9)
            assertTrue("pedal $pedal revs ${run.rpm}", run.rpm > setup.redline * 0.9)
        }
    }

    @Test
    fun releasingPastTheBitePointBeforeGreenMovesTheCarAndIsAJumpStart() {
        val setup = car()
        for (pedal in listOf(0.6, 0.5, 0.3, 0.0)) {
            val run = RaceDrive.fresh(setup)
            hold(setup, run, 1.0, 1.0, 1.0)
            val jumped = hold(setup, run, 3.0, 1.0, pedal)
            assertTrue("pedal $pedal should roll", jumped && run.jumped)
            assertTrue(run.distance > RaceDrive.JUMP_M)
            assertEquals(0.0, run.time, 1e-9)
        }
    }

    @Test
    fun engagedClutchWithOffThrottleDoesNotRollTheCar() {
        val setup = car()
        val run = RaceDrive.fresh(setup)
        assertFalse(hold(setup, run, 5.0, 0.0, 0.0))
        assertEquals(0.0, run.distance, 1e-9)
    }

    @Test
    fun theBiteBandRunsFromOpenToLockedAndTheCurveIsInvertible() {
        assertEquals(0.0, RaceDrive.engagement(RaceDrive.BITE_START), 1e-9)
        assertEquals(0.0, RaceDrive.engagement(1.0), 1e-9)
        assertEquals(1.0, RaceDrive.engagement(RaceDrive.BITE_FULL), 1e-9)
        assertEquals(1.0, RaceDrive.engagement(0.0), 1e-9)
        var last = -1.0
        var pedal = 1.0
        while (pedal >= 0.0) {
            val now = RaceDrive.engagement(pedal)
            assertTrue(now >= last - 1e-9)
            last = now
            pedal -= 0.01
        }
        for (share in listOf(0.2, 0.5, 0.9)) {
            assertEquals(share, RaceDrive.engagement(RaceDrive.pedalFor(share)), 1e-3)
        }
        assertTrue(RaceDrive.BITE_START - RaceDrive.BITE_FULL > 0.3)
    }

    @Test
    fun aJumpIsALossAndTheNoteSaysSo() {
        val start = GameState()
        val field = Racing.fieldForLadder(start)
        val note = Racing.stripNote(start, field, won = true, time = 0.0, rival = 12.0, ladder = true, jumped = true)
        assertTrue(note, note.contains("RED LIGHT") && note.contains("jumped the start"))
        assertFalse(note, note.contains("Won"))
    }

    @Test
    fun feathersOutOfTheBandAfterGreenStillLaunchesAndAHeldBitePointHooks() {
        val setup = car()
        val run = RaceDrive.fresh(setup)
        hold(setup, run, 1.0, 1.0, 1.0)
        val bite = RaceDrive.pedalFor(0.6)
        var t = 0.0
        while (t < 1.5) {
            RaceDrive.step(setup, run, 1.0, false, dt, bite)
            t += dt
        }
        assertTrue("moved ${run.distance}", run.distance > 4.0)
        assertFalse(run.jumped)
    }
}
