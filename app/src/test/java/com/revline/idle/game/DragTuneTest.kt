package com.revline.idle.game

import com.revline.idle.data.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every Drag tune knob moves the strip one way and gives something back the other way. */
class DragTuneTest {
    private val suspension = mapOf("suspension" to 1)

    private fun setup(
        hp: Double,
        tune: Map<String, Double> = emptyMap(),
        kit: Map<String, Int> = suspension,
        tire: String = "slick",
    ): RaceDrive.Setup {
        val s = GameState(prestiges = 6, chassis = "seven", transmission = "close", driveline = "lsd", tire = tire, kit = kit, dragTune = tune)
        val rig = DragTune.rig(s)
        return RaceDrive.setupFor(Racing.car(s), hp, hp * 0.9, 7500.0, 900.0, Racing.finalRatio("road") + rig.finalTrim, 402.3, 4000.0, false, rig)
    }

    private fun clean(hp: Double, vararg tune: Pair<String, Double>, kit: Map<String, Int> = suspension) =
        RaceDrive.clean(setup(hp, tune.toMap(), kit))

    private val strong = 900.0
    private val weak = 300.0

    @Test fun tirePressureTradesLaunchGripForRollingDrag() {
        val soft = setup(strong, mapOf("psi" to -4.0))
        val firm = setup(strong, mapOf("psi" to 4.0))
        assertTrue(soft.gripN > firm.gripN * 1.1)
        assertTrue("60 ft soft ${clean(strong, "psi" to -4.0).sixtyFt} firm ${clean(strong, "psi" to 4.0).sixtyFt}", clean(strong, "psi" to -4.0).sixtyFt < clean(strong, "psi" to 4.0).sixtyFt)
        assertTrue(soft.rollN > firm.rollN)
        // A power-limited car does not need the grip and pays for the drag at the other end.
        val softW = clean(weak, "psi" to -4.0)
        val firmW = clean(weak, "psi" to 4.0)
        assertTrue(softW.trapMps < firmW.trapMps)
        assertTrue(softW.time > firmW.time)
    }

    @Test fun theFinalDriveTradesAccelerationForTheTrapSpeed() {
        val short = clean(strong, "gear" to 0.45)
        val tall = clean(strong, "gear" to -0.45)
        assertTrue(short.sixtyFt < tall.sixtyFt)
        assertTrue("trap short ${short.trapMps} tall ${tall.trapMps}", tall.trapMps > short.trapMps * 1.1)
        assertTrue(setup(strong, mapOf("gear" to 0.45)).finalDrive > setup(strong, mapOf("gear" to -0.45)).finalDrive)
        // On a car that is not at the limiter the tall gear costs time.
        assertTrue(clean(weak, "gear" to -0.45).time > clean(weak).time)
        assertTrue(clean(weak, "gear" to 0.45).time < clean(weak).time)
    }

    @Test fun frontStiffnessTradesRearGripForTheNoseComingUp() {
        val soft = setup(strong, mapOf("front" to -5.0))
        val stiff = setup(strong, mapOf("front" to 5.0))
        assertTrue(soft.gripN > stiff.gripN)
        assertTrue("a soft front lifts the nose sooner", soft.liftG < stiff.liftG)
        assertTrue(clean(strong, "front" to -5.0).sixtyFt < clean(strong, "front" to 5.0).sixtyFt)
    }

    @Test fun rearStiffnessTradesGripForSquat() {
        val soft = setup(strong, mapOf("rear" to -5.0))
        val stiff = setup(strong, mapOf("rear" to 5.0))
        assertTrue(soft.gripN > stiff.gripN)
        assertTrue(clean(strong, "rear" to -5.0).sixtyFt < clean(strong, "rear" to 5.0).sixtyFt)
        // The squat costs drive once the car is moving: on a car the grip does not limit, soft is slower.
        assertTrue("soft ${clean(weak, "rear" to -5.0).time} stiff ${clean(weak, "rear" to 5.0).time}", clean(weak, "rear" to -5.0).time > clean(weak, "rear" to 5.0).time)
    }

    @Test fun rideHeightTradesDragAndANoseThatStaysDownForGrip() {
        val low = setup(weak, mapOf("height" to -20.0))
        val high = setup(weak, mapOf("height" to 20.0))
        assertTrue(low.drag < high.drag)
        assertTrue("a low nose stays down", low.liftG > high.liftG)
        assertTrue(low.gripN < high.gripN)
        assertTrue(clean(weak, "height" to -20.0).trapMps > clean(weak, "height" to 20.0).trapMps)
    }

    @Test fun launchRpmTradesASoftHitForOneTheTiresMayNotHold() {
        val kit = suspension + mapOf("tbrake" to 1)
        val low = clean(weak, "launch" to -1500.0, kit = kit)
        val fit = clean(weak, kit = kit)
        assertTrue(low.sixtyFt > fit.sixtyFt + 0.1)
        val hot = clean(strong, "launch" to 500.0, kit = kit)
        val fitS = clean(strong, kit = kit)
        assertTrue("hot ${hot.sixtyFt} fit ${fitS.sixtyFt}", hot.sixtyFt > fitS.sixtyFt + 0.5)
        assertTrue(setup(strong, mapOf("launch" to 500.0), kit).launchRpm > setup(strong, emptyMap(), kit).launchRpm)
    }

    @Test fun shiftRpmTradesShiftingEarlyOutOfThePowerAgainstHittingTheLimiter() {
        val kit = suspension + mapOf("shifter" to 1)
        val early = setup(weak, mapOf("shift" to -1500.0), kit)
        val stock = setup(weak, emptyMap(), kit)
        val late = setup(weak, mapOf("shift" to 600.0), kit)
        assertTrue(RaceDrive.shiftPoint(early) < RaceDrive.shiftPoint(stock))
        assertTrue(RaceDrive.shiftPoint(late) > RaceDrive.shiftPoint(stock))
        assertTrue(RaceDrive.shiftPoint(late) <= late.redline)
        assertTrue("early ${RaceDrive.clean(early).time} stock ${RaceDrive.clean(stock).time}", RaceDrive.clean(early).time > RaceDrive.clean(stock).time + 0.2)
        assertTrue(RaceDrive.clean(setup(weak, mapOf("shift" to -600.0), kit)).time > RaceDrive.clean(stock).time)
    }

    @Test fun theNitrousPointMovesWhereTheBottleIsSpent() {
        val kit = suspension + mapOf("nos" to 3)
        val early = clean(weak, "nos" to 0.0, kit = kit)
        val late = clean(weak, "nos" to 150.0, kit = kit)
        assertTrue(early.sixtyFt < late.sixtyFt - 0.1)
        assertTrue(late.trapMps > early.trapMps)
        assertTrue(clean(weak, "nos" to 300.0, kit = kit).time > late.time)
    }

    @Test fun aKnobStaysLockedUntilItsPartIsFittedAndReadsItsDefaultUntilThen() {
        val bare = GameState(prestiges = 6, tire = "slick")
        for (id in listOf("front", "rear", "height", "launch", "shift", "nos")) {
            assertFalse(id, DragTune.unlocked(bare, id))
            assertEquals(bare, DragTune.set(bare, id, DragTune.byId(id)!!.max))
            assertEquals(DragTune.byId(id)!!.default, DragTune.value(bare, id), 0.0)
        }
        assertTrue(DragTune.unlocked(bare, "gear"))
        assertTrue(DragTune.unlocked(bare, "psi"))
        assertFalse(DragTune.unlocked(bare.copy(tire = "bias"), "psi"))
        val full = bare.copy(kit = mapOf("suspension" to 1, "tbrake" to 1, "shifter" to 1, "nos" to 1))
        assertTrue(DragTune.all.all { DragTune.unlocked(full, it.id) })
        // A value saved before the part was sold is ignored.
        val stale = bare.copy(dragTune = mapOf("nos" to 200.0, "front" to 3.0))
        assertEquals(60.0, DragTune.value(stale, "nos"), 0.0)
        assertEquals(0.0, DragTune.value(stale, "front"), 0.0)
        assertEquals(DragTune.Rig(), DragTune.rig(stale))
    }

    @Test fun aKnobSnapsToItsStepStaysInRangeNudgesAndResets() {
        val full = GameState(prestiges = 6, tire = "slick", kit = mapOf("suspension" to 1, "tbrake" to 1, "shifter" to 1, "nos" to 1))
        for (k in DragTune.all) {
            assertEquals(k.max, DragTune.value(DragTune.set(full, k.id, k.max + 1e6), k.id), 1e-9)
            assertEquals(k.min, DragTune.value(DragTune.set(full, k.id, k.min - 1e6), k.id), 1e-9)
            val mid = DragTune.set(full, k.id, k.default + k.step * 1.4)
            assertEquals(k.default + k.step, DragTune.value(mid, k.id), 1e-9)
            assertEquals(k.default, DragTune.value(DragTune.nudge(mid, k.id, -1), k.id), 1e-9)
        }
        val moved = DragTune.set(full, "psi", 2.0)
        assertEquals(emptyMap<String, Double>(), DragTune.reset(moved).dragTune)
    }

    @Test fun theForecastIsACleanPassOnTheCarAsTuned() {
        val s = GameState(prestiges = 6, chassis = "seven", transmission = "close", driveline = "lsd", tire = "slick", kit = suspension)
        val a = DragTune.forecast(s)
        val b = DragTune.forecast(DragTune.set(s, "gear", 0.45))
        assertTrue(a.time > 0.0 && a.trapMps > 0.0)
        assertTrue(a.sixtyFt != b.sixtyFt)
    }
}
