package com.revline.idle.game

import com.revline.idle.data.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drag bottle the dash draws. It follows the seconds the strip already spends. */
class NitrousBottleTest {
    private fun setup(nos: Int = 1, circuit: Boolean = false): RaceDrive.Setup {
        val state = GameState(
            prestiges = 6,
            chassis = "seven",
            transmission = "close",
            driveline = "lsd",
            tire = "slick",
            kit = if (nos > 0) mapOf("nos" to nos) else emptyMap(),
        )
        val rig = DragTune.rig(state)
        // The named tub, even when a race would stand the mule in. This is the bottle, not a race entry.
        return RaceDrive.setupFor(
            Racing.assess(Racing.specOf(state)), 400.0, 360.0, 7500.0, 900.0,
            Racing.finalRatio("road") + rig.finalTrim, 402.3, 4000.0, circuit, rig,
        )
    }

    @Test fun noKitStaysOff() {
        val bare = setup(0)
        val read = RaceDrive.bottle(bare, RaceDrive.fresh(bare), racing = true)
        assertEquals(RaceDrive.BottlePhase.OFF, read.phase)
        assertEquals(0.0, read.fill, 0.0)
        assertEquals(0.0, read.secondsLeft, 0.0)
    }

    @Test fun theCircuitNeverArmsAStripBottle() {
        val read = RaceDrive.bottle(setup(circuit = true), RaceDrive.fresh(setup(circuit = true)), racing = true)
        assertEquals(RaceDrive.BottlePhase.OFF, read.phase)
    }

    @Test fun aFreshPassIsArmedAndFullAndTheTreeDoesNotSpendIt() {
        val setup = setup()
        val run = RaceDrive.fresh(setup)
        val armed = RaceDrive.bottle(setup, run, racing = false)
        assertEquals(RaceDrive.BottlePhase.ARMED, armed.phase)
        assertEquals(1.0, armed.fill, 1e-9)
        assertEquals(7.0, armed.secondsLeft, 1e-6)
        repeat(40) { RaceDrive.stage(setup, run, 1.0, 0.05, 0.0) }
        assertEquals(0.0, run.nitrousS, 1e-9)
        assertFalse(run.shotOpen)
        assertEquals(RaceDrive.BottlePhase.ARMED, RaceDrive.bottle(setup, run, racing = false).phase)
    }

    @Test fun theBottleDrainsWhileTheShotIsOpenAndANewPassRefillsIt() {
        val setup = setup().copy(shotAtM = 0.0)
        val run = RaceDrive.fresh(setup)
        var sawActive = false
        var lowest = 1.0
        var t = 0.0
        while (t < 20.0) {
            RaceDrive.step(setup, run, 1.0, false, 0.05, 0.0)
            val read = RaceDrive.bottle(setup, run, racing = true)
            if (read.phase == RaceDrive.BottlePhase.ACTIVE) {
                sawActive = true
                lowest = minOf(lowest, read.fill)
                assertTrue(read.secondsLeft < setup.bottleS)
            }
            if (read.phase == RaceDrive.BottlePhase.EMPTY) break
            t += 0.05
        }
        assertTrue(sawActive)
        assertTrue("fill fell to $lowest", lowest < 0.85)
        assertEquals(RaceDrive.BottlePhase.EMPTY, RaceDrive.bottle(setup, run, racing = true).phase)
        val again = RaceDrive.fresh(setup)
        val full = RaceDrive.bottle(setup, again, racing = false)
        assertEquals(RaceDrive.BottlePhase.ARMED, full.phase)
        assertEquals(1.0, full.fill, 1e-9)
        assertEquals(setup.bottleS, full.secondsLeft, 1e-6)
    }

    @Test fun liftingOffLeavesTheBottleArmed() {
        val setup = setup().copy(shotAtM = 0.0)
        val run = RaceDrive.Run(distance = 80.0, speed = 40.0, gear = 2, rpm = 5000.0, hooked = true, nitrousS = 2.0)
        RaceDrive.step(setup, run, 0.2, false, 0.05, 0.0)
        val read = RaceDrive.bottle(setup, run, racing = true)
        assertEquals(RaceDrive.BottlePhase.ARMED, read.phase)
        assertEquals(5.0, read.secondsLeft, 0.05)
        assertFalse(run.shotOpen)
    }

    @Test fun aDryBottleReadsEmptyEvenIfThePedalIsDown() {
        val setup = setup()
        val run = RaceDrive.Run(distance = 200.0, speed = 50.0, gear = 3, rpm = 6000.0, hooked = true, nitrousS = setup.bottleS, shotOpen = true)
        val read = RaceDrive.bottle(setup, run, racing = true)
        assertEquals(RaceDrive.BottlePhase.EMPTY, read.phase)
        assertEquals(0.0, read.fill, 0.0)
    }
}
