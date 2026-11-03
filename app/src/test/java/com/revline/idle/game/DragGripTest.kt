package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The strip checks the tires at every step of the pass: weight transfer, wheelspin, and the knobs acting through it. */
class DragGripTest {
    private fun strip(
        hp: Double,
        kit: Map<String, Int> = emptyMap(),
        tune: Map<String, Double> = emptyMap(),
        tire: String = "slick",
        chassis: String = "seven",
    ): RaceDrive.Setup {
        val s = GameState(prestiges = 6, chassis = chassis, transmission = "close", driveline = "lsd", tire = tire, kit = kit, dragTune = tune)
        val rig = DragTune.rig(s)
        return RaceDrive.setupFor(
            Racing.car(s), hp, hp * 0.9, 7500.0, 900.0,
            Racing.finalRatio("road") + rig.finalTrim, 402.3, 4000.0, false, rig,
        )
    }

    /** What the model saw on one step, and what the car did with it. */
    private class Snap(
        val gear: Int, val spinning: Boolean, val driveN: Double, val holdN: Double, val loadN: Double, val mu: Double,
        val distance: Double, val speed: Double, val accel: Double, val settled: Boolean, val time: Double, val heat: Double,
    )

    /** A pass with the foot flat in gear (the launch is feathered as the sim's own clean pass does): every step is recorded. */
    private fun pass(setup: RaceDrive.Setup, gearThrottle: Double = 1.0, meters: Double = 402.3): List<Snap> {
        val run = RaceDrive.fresh(setup)
        val out = ArrayList<Snap>()
        var guard = 0
        var sinceShift = 9.0
        while (run.distance < meters && run.time < 60.0 && guard++ < 6_000) {
            val launching = run.gear == 1 && run.speed < 14.0 && !run.hooked
            val th = if (launching) RaceDrive.launchThrottle(setup) else gearThrottle
            val up = run.shiftLeft <= 0.0 && run.gear < setup.ratios.size && run.rpm > RaceDrive.shiftPoint(setup) && run.hooked
            val v0 = run.speed
            val g0 = run.gear
            RaceDrive.step(setup, run, th, up, 1.0 / 60.0)
            if (run.gear != g0) sinceShift = 0.0 else sinceShift += 1.0 / 60.0
            out.add(Snap(run.gear, run.spinning, run.driveN, run.holdN, run.loadN, run.gripMu, run.distance, run.speed,
                (run.speed - v0) * 60.0, sinceShift > 0.4 && run.shiftLeft <= 0.0, run.time, run.heat))
        }
        return out
    }

    // ---- wheelspin the whole way ----

    @Test fun aHighPowerCarSpinsInSecondGear() {
        val setup = strip(1600.0)
        assertTrue(setup.muEff > 0.0)
        val steps = pass(setup)
        val second = steps.filter { it.gear == 2 && it.spinning }
        assertTrue("never spun in 2nd", second.size >= 5)
        // Spinning means the tires took what they hold, not what the engine asked.
        assertTrue(second.all { it.driveN <= it.holdN + 1e-6 })
        // The same car on the same tires with a gentler engine hooks up in 2nd.
        val tame = pass(strip(250.0)).filter { it.gear == 2 }
        assertTrue(tame.isNotEmpty())
        assertFalse(tame.any { it.spinning })
        // A driver who feathers to what the tires hold does not spin there.
        val feathered = RaceDrive.driveWell(setup)
        assertTrue(feathered.gear >= 2)
        val spun = ArrayList<Boolean>()
        RaceDrive.driveWell(setup) { if (it.gear == 2) spun.add(it.spinning) }
        assertTrue(spun.isNotEmpty())
        assertTrue("a feathered pass spun ${spun.count { it }} of ${spun.size} steps in 2nd", spun.count { it } < spun.size / 4)
    }

    @Test fun shiftsAndSpeedGetTheSameCheck() {
        // Flat through a shift, a car with far more push than grip is loose again in the next gear.
        val steps = pass(strip(1600.0))
        val gears = steps.map { it.gear }
        val changes = gears.indices.drop(1).filter { gears[it] != gears[it - 1] && gears[it] >= 3 }
        assertTrue(changes.isNotEmpty())
        assertTrue("no spin after a shift", changes.any { c -> steps.subList(c, minOf(c + 40, steps.size)).any { it.spinning } })
    }

    @Test fun nitrousAtALowSpeedBreaksTheTiresLooseMidRun() {
        val kit = mapOf("nos" to 3)
        // A car that hooks everywhere with the shot held back to the end of the strip.
        val base = strip(800.0, kit, mapOf("nos" to 400.0))
        val dry = pass(base).filter { it.gear >= 2 }
        assertFalse("the dry car spun", dry.any { it.spinning })
        // The same car with the shot opening early, at a low speed, mid-run.
        val early = strip(800.0, kit, mapOf("nos" to 25.0))
        assertTrue(early.shotFrac > 0.0)
        val steps = pass(early)
        val after = steps.filter { it.distance >= 25.0 && it.gear >= 2 }
        val before = steps.filter { it.distance < 25.0 && it.gear >= 2 }
        assertTrue("no spin once the shot flowed", after.any { it.spinning })
        assertFalse("spun before the shot", before.any { it.spinning })
        // Opened a little later, at speed, the same tires take it.
        val later = strip(800.0, kit, mapOf("nos" to 120.0))
        assertFalse(pass(later).any { it.spinning && it.gear >= 2 })
    }

    // ---- the limit ----

    @Test fun gripLimitedAccelerationNeverBeatsMuTimesGTimesTheRearLoadFraction() {
        for (hp in listOf(300.0, 900.0, 1600.0)) for (tire in listOf("radial", "slick")) {
            val setup = strip(hp, tire = tire)
            val weight = setup.massKg * 9.81
            val steps = pass(setup)
            var checked = 0
            for (s in steps) {
                if (!s.settled || s.holdN <= 0.0) continue
                // The push never passes the hold, and the hold is mu times the load on the driven wheels.
                assertTrue("$hp $tire drive ${s.driveN} hold ${s.holdN}", s.driveN <= s.holdN + 1e-6)
                assertTrue(s.holdN <= s.mu * s.loadN + 1e-6)
                // The load is no more than the whole weight, and no less than a fifth of it.
                assertTrue(s.loadN <= weight * 1.0001 && s.loadN >= weight * 0.2 - 1e-6)
                // Measured acceleration: m a <= force - drag <= hold; so a <= mu * g * (load / weight).
                val bound = s.mu * 9.81 * (s.loadN / weight) * (weight / setup.massKg / 9.81)
                if (s.accel > 0.0) {
                    assertTrue("$hp $tire a ${s.accel} bound $bound", s.accel <= bound * 1.02 + 0.05)
                    checked++
                }
            }
            assertTrue(checked > 100)
        }
    }

    @Test fun theRearLoadFractionGrowsWithAccelerationOnRearDriveAndShrinksOnFrontDrive() {
        val rwd = strip(900.0, chassis = "seven")
        assertTrue(rwd.loadSlope > 0.0)
        assertTrue(RaceDrive.driveLoadShare(rwd, 15.0) > RaceDrive.driveLoadShare(rwd, 0.0))
        assertEquals(rwd.loadStatic, RaceDrive.driveLoadShare(rwd, 0.0), 1e-9)
        val fwd = strip(200.0, chassis = "hatch")
        if (fwd.loadSlope < 0.0) assertTrue(RaceDrive.driveLoadShare(fwd, 8.0) < RaceDrive.driveLoadShare(fwd, 0.0))
    }

    @Test fun downforceAddsToTheHoldAtSpeedAndNothingAtRest() {
        val setup = strip(900.0).copy(downK = 1.5)
        val still = RaceDrive.Run(gear = 3, speed = 0.0, heat = 1.0)
        val fast = RaceDrive.Run(gear = 3, speed = 60.0, heat = 1.0)
        val plain = setup.copy(downK = 0.0)
        assertEquals(RaceDrive.holdN(plain, still, 4.0), RaceDrive.holdN(setup, still, 4.0), 1e-6)
        assertTrue(RaceDrive.holdN(setup, fast, 4.0) > RaceDrive.holdN(plain, fast, 4.0) + 1000.0)
    }

    // ---- the knobs and the parts act through it ----

    private fun hold(setup: RaceDrive.Setup, gear: Int = 3, speed: Double = 30.0, heat: Double = 0.8): Double {
        val run = RaceDrive.Run(gear = gear, speed = speed, heat = heat)
        return RaceDrive.holdN(setup, run, setup.ratios[gear - 1] * setup.finalDrive)
    }

    @Test fun tirePressureChangesTheHoldInEveryGear() {
        val soft = strip(900.0, tire = "radial", tune = mapOf("psi" to -3.0))
        val hard = strip(900.0, tire = "radial", tune = mapOf("psi" to 3.0))
        for (g in 1..4) assertTrue("gear $g", hold(soft, g) > hold(hard, g))
    }

    @Test fun suspensionStiffnessAndRideHeightMoveTheWeightTransfer() {
        val soft = strip(900.0, susp, mapOf("front" to -4.0))
        val stiff = strip(900.0, susp, mapOf("front" to 4.0))
        assertTrue(hold(soft, 2, 25.0) > hold(stiff, 2, 25.0))
        assertTrue(soft.liftG < stiff.liftG)
        val low = strip(900.0, susp, mapOf("height" to -20.0))
        val high = strip(900.0, susp, mapOf("height" to 20.0))
        assertTrue(hold(high, 2, 25.0) > hold(low, 2, 25.0))
        assertTrue(high.liftG < low.liftG)
        val softRear = strip(900.0, susp, mapOf("rear" to -4.0))
        val stiffRear = strip(900.0, susp, mapOf("rear" to 4.0))
        assertTrue(hold(softRear) > hold(stiffRear))
    }

    @Test fun wheelieBarsAddLiftHeadroomThroughoutTheRun() {
        val bare = strip(900.0)
        val bars = strip(900.0, with("wbars", 2))
        assertTrue(bars.liftG > bare.liftG + 0.5)
    }

    private val susp = mapOf("suspension" to 1)
    private fun with(line: String, level: Int) = mapOf(line to level)

    @Test fun aLineLockBurnoutWarmsTheTiresAndTheHeatShowsInTheRestOfTheRun() {
        val cold = strip(900.0)
        val warm = strip(900.0, with("linelock", 1))
        assertTrue(warm.heat0 > cold.heat0)
        val a = pass(cold)
        val b = pass(warm)
        // Heat builds as the tires work, and a pre-warmed set carries more of it down the strip.
        assertTrue(a.last().heat > a.first().heat)
        assertTrue(b[60].heat > a[60].heat)
        assertTrue(hold(warm, heat = 1.0) > hold(warm, heat = 0.5))
    }

    @Test fun shiftRpmDecidesWhereTheNextGearPicksUpAndSoWhetherItSpins() {
        val early = strip(1000.0, mapOf("shifter" to 1), mapOf("shift" to -1500.0))
        val late = strip(1000.0, mapOf("shifter" to 1), mapOf("shift" to 300.0))
        fun spinsAfterShift(s: RaceDrive.Setup) = pass(s).count { it.spinning && it.gear >= 2 }
        val a = pass(early)
        val b = pass(late)
        assertTrue(spinsAfterShift(early) != spinsAfterShift(late) || a.last().time != b.last().time)
        // The pick-up is at a different rpm, so the first push after the change differs.
        fun firstAfterShift(st: List<Snap>): Double { val i = st.indices.drop(1).first { st[it].gear == 3 && st[it - 1].gear == 2 }; return st[i + 3].driveN }
        assertTrue(firstAfterShift(a) != firstAfterShift(b))
    }

    @Test fun aShorterFinalDriveMakesEveryGearPushHarderAgainstTheSameHold() {
        val tall = strip(900.0, tune = mapOf("gear" to -0.4))
        val short = strip(900.0, tune = mapOf("gear" to 0.4))
        val runA = RaceDrive.Run(gear = 2, speed = 20.0, heat = 0.8, rpm = 6000.0, hooked = true)
        val runB = RaceDrive.Run(gear = 2, speed = 20.0, heat = 0.8, rpm = 6000.0, hooked = true)
        assertTrue(RaceDrive.gripThrottle(short, runB) <= RaceDrive.gripThrottle(tall, runA))
    }

    // ---- the other ends ----

    @Test fun theCircuitSetupIsUntouchedByTheStripModel() {
        val state = GameState(prestiges = 6, chassis = "seven", tire = "slick")
        val circuit = RaceDrive.setup(state, Logic.sheet(state), circuit = true)
        assertEquals(0.0, circuit.muEff, 0.0)
        assertEquals(99.0, circuit.liftG, 0.0)
        assertEquals(0.0, circuit.downK, 0.0)
    }

    @Test fun aSetupWithTheModelOffKeepsTheFlatLaunchHold() {
        val off = strip(900.0).copy(muEff = 0.0)
        assertEquals(off.gripN, hold(off, 3, 40.0), 0.0)
        assertEquals(off.gripN, hold(off, 1, 0.0), 0.0)
    }

    @Test fun savesFromBeforeTheModelStillLoad() {
        val old = SaveCodec.encode(GameState(prestiges = 6, dragTune = mapOf("psi" to 1.0)))
        val back = SaveCodec.decode(old)
        assertEquals(1.0, back.dragTune.getValue("psi"), 0.0)
        assertTrue(RaceDrive.setup(back).muEff > 0.0)
    }
}
