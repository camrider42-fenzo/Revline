package com.revline.idle.game

import com.revline.idle.data.GameState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dash SPIN lamp follows per-step slip for the whole pass, not the discrete spinning flag. */
class SpinLampTest {
    private fun strip(hp: Double, tire: String = "slick"): RaceDrive.Setup {
        val s = GameState(prestiges = 6, chassis = "seven", transmission = "close", driveline = "lsd", tire = tire)
        val rig = DragTune.rig(s)
        return RaceDrive.setupFor(
            Racing.car(s), hp, hp * 0.9, 7500.0, 900.0,
            Racing.finalRatio("road") + rig.finalTrim, 402.3, 4000.0, false, rig,
        )
    }

    private fun passSlip(setup: RaceDrive.Setup, gearThrottle: Double = 1.0): List<Double> {
        val run = RaceDrive.fresh(setup)
        val out = ArrayList<Double>()
        var guard = 0
        while (run.distance < setup.distanceM && run.time < 60.0 && guard++ < 6_000) {
            val launching = run.gear == 1 && run.speed < 14.0 && !run.hooked
            val th = if (launching) RaceDrive.launchThrottle(setup) else gearThrottle
            val up = run.shiftLeft <= 0.0 && run.gear < setup.ratios.size && run.rpm > RaceDrive.shiftPoint(setup) && run.hooked
            RaceDrive.step(setup, run, th, up, 1.0 / 60.0)
            out.add(run.slip)
        }
        return out
    }

    @Test fun spinProneBuildLightsTheLampInGear() {
        val setup = strip(1600.0)
        assertTrue(setup.muEff > 0.0)
        val slips = passSlip(setup)
        assertTrue("spin-prone pass never lit the slip lamp", slips.any { RaceDrive.wheelspinLit(it) })
        // And the lamp tracks slip, not only a launch-only moment: some lit samples are after 1st.
        val run = RaceDrive.fresh(setup)
        var litPastFirst = false
        var guard = 0
        while (run.distance < setup.distanceM && run.time < 60.0 && guard++ < 6_000) {
            val launching = run.gear == 1 && run.speed < 14.0 && !run.hooked
            val th = if (launching) RaceDrive.launchThrottle(setup) else 1.0
            val up = run.shiftLeft <= 0.0 && run.gear < setup.ratios.size && run.rpm > RaceDrive.shiftPoint(setup) && run.hooked
            RaceDrive.step(setup, run, th, up, 1.0 / 60.0)
            if (run.gear >= 2 && RaceDrive.wheelspinLit(run.slip)) litPastFirst = true
        }
        assertTrue("expected wheelspin lamp in 2nd+ on a spin-prone build", litPastFirst)
    }

    @Test fun hookedBuildDoesNotLightTheLamp() {
        val setup = strip(250.0)
        val slips = passSlip(setup)
        assertTrue(slips.isNotEmpty())
        assertFalse("hooked mild build lit the spin lamp", slips.any { RaceDrive.wheelspinLit(it) })
        val well = RaceDrive.driveWell(strip(1600.0))
        // driveWell feathers; after the pass, slip should have faded to dark.
        assertFalse(RaceDrive.wheelspinLit(well.slip))
    }
}
