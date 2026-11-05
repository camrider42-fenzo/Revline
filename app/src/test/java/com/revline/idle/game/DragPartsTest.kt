package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drag parts and the Drag tune knobs: each one moves the strip, and each one costs something. */
class DragPartsTest {
    /** A light rear-drive car on a drag slick. 900 hp is more than the tires hold, 300 hp is less. */
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

    private val strong = 900.0
    private val weak = 300.0
    private fun clean(s: RaceDrive.Setup) = RaceDrive.clean(s)

    private fun with(line: String, level: Int, vararg extra: Pair<String, Int>) = mapOf(line to level) + extra

    // ---- the parts as parts ----

    @Test fun everyStripLineIsAStudyThenAPurchaseOnTheSameGate() {
        assertEquals(setOf("launch", "tbrake", "wbars", "flywheel", "shifter", "linelock", "nos"), Parts.strip.map { it.id }.toSet())
        for (line in Parts.strip) {
            assertTrue(line.strip)
            line.levels.forEachIndexed { i, lv ->
                val study = Racing.studies.first { it.id == line.studyId(i + 1) }
                assertEquals(lv.studyRp, study.cost, 0.0)
                assertEquals(lv.minPrestige, study.minPrestige)
                assertEquals(if (i == 0) null else line.studyId(i), study.requires)
                assertTrue("${lv.name} has a drag hint", lv.dragHint.isNotBlank())
                if (i > 0) {
                    assertTrue(lv.studyRp > line.levels[i - 1].studyRp)
                    assertTrue(lv.price > line.levels[i - 1].price)
                }
            }
        }
        assertFalse(Parts.lines.any { it.strip })
        assertEquals(Parts.lines.size + Parts.strip.size, Parts.all.size)
    }

    @Test fun aStripPartIsBoughtForItsFullPriceAndFittedOnTheStripOnly() {
        var s = GameState(money = 1e9, researchPoints = 1e9, prestiges = 6)
        val line = Parts.strip.first { it.id == "tbrake" }
        assertTrue(Racing.partBlocked(s.copy(researchPoints = 0.0), "tbrake", "1")!!.contains("RP"))
        s = Racing.study(s, "kit_tbrake_1")
        assertNull(Racing.partBlocked(s, "tbrake", "1"))
        val before = s.money
        s = Racing.fitPart(s, "tbrake", "1")
        assertEquals(1, Parts.level(s, "tbrake"))
        assertTrue("paid ${before - s.money}", before - s.money >= line.levels[0].price)
        assertEquals(line.levels[0].fx.preload, Parts.stripKit(s).preload, 0.0)
        // A circuit kit never carries a strip part.
        assertEquals(Parts.NONE, Parts.kit(s))
        assertEquals(Parts.kit(s) + Parts.stripKit(s), Racing.specOf(s).kit)
        assertEquals(Parts.kit(s), Racing.specOf(s, circuit = true).kit)
    }

    // ---- what each part does to the strip ----

    @Test fun launchControlHoldsTheTiresWhereAFlatFootedLaunchSpins() {
        fun flat(kit: Map<String, Int>): Double {
            val setup = strip(strong, kit)
            val run = RaceDrive.fresh(setup)
            while (run.time < 3.0) RaceDrive.step(setup, run, 1.0, run.rpm > setup.redline * 0.91 && run.gear < setup.ratios.size, 1.0 / 60.0)
            return run.distance
        }
        val none = flat(emptyMap())
        val one = flat(with("launch", 1))
        val three = flat(with("launch", 3))
        assertTrue("one $one none $none", one > none)
        assertTrue("three $three one $one", three > one)
    }

    @Test fun launchControlTakesTheLaunchOutOfTheDriversHands() {
        val sloppy = strip(strong, with("launch", 3))
        val bare = strip(strong)
        assertTrue(Drivers.launchPlan(sloppy).module)
        assertFalse(Drivers.launchPlan(bare).module)
        assertTrue(RaceDrive.launchThrottle(sloppy) >= RaceDrive.launchThrottle(bare))
        // On a pilot with a bad launch the module is worth more than on a perfect one: it is about not losing the launch.
        assertTrue(sloppy.spinTrim > bare.spinTrim)
    }

    @Test fun aTransbrakeHitsHarderAndTheHoldRpmIsTheTradeOff() {
        val bare = strip(weak)
        val brake = strip(weak, with("tbrake", 1))
        assertTrue(brake.launchRpm > 0.0)
        assertTrue("60 ft ${clean(brake).sixtyFt} vs ${clean(bare).sixtyFt}", clean(brake).sixtyFt < clean(bare).sixtyFt)
        val soft = strip(weak, with("tbrake", 1), mapOf("launch" to -1500.0))
        assertTrue("a soft hit leaves time", clean(soft).sixtyFt > clean(brake).sixtyFt)
        // On a car with more push than grip a high hold rpm breaks the tires loose.
        val hot = strip(strong, with("tbrake", 1), mapOf("launch" to 500.0))
        val fitted = strip(strong, with("tbrake", 1))
        assertTrue("hot ${clean(hot).sixtyFt} fitted ${clean(fitted).sixtyFt}", clean(hot).sixtyFt > clean(fitted).sixtyFt + 0.5)
    }

    @Test fun wheelieBarsKeepTheNoseDownOnAPowerfulLightCarAndCostWeightAndDrag() {
        // Bars raise the lift ceiling and cost weight and drag. With the 1.91 grip cut the tires usually
        // break loose before the nose, so ET may not move on the same huge power — the ceiling still rises.
        val huge = 1600.0
        val bare = strip(huge, with("tbrake", 1))
        val bars = strip(huge, with("tbrake", 1, "wbars" to 2))
        assertTrue(bars.liftG > bare.liftG)
        assertTrue(bars.drag > bare.drag)
        assertTrue(bars.massKg > bare.massKg)
        // On a car the nose never lifts they are only the weight and the drag.
        val weak = strip(weak)
        val weakBars = strip(this.weak, with("wbars", 2))
        assertTrue(clean(weakBars).time > clean(weak).time)
        assertTrue(weakBars.liftG > weak.liftG)
    }

    @Test fun aLightFlywheelRevsFasterAndBogsTheLaunch() {
        val bare = strip(weak)
        val light = strip(weak, with("flywheel", 2))
        assertTrue(clean(light).time < clean(bare).time)
        assertTrue(clean(light).trapMps > clean(bare).trapMps)
        val force = { s: RaceDrive.Setup -> RaceDrive.launchForce(s, 1.0, 1.0, s.ratios[0] * s.finalDrive, false) }
        assertTrue("less to leave on", force(light) < force(bare))
        assertTrue(strip(weak, with("flywheel", 2)).flywheelKgm2 > strip(weak, with("flywheel", 1)).flywheelKgm2)
    }

    @Test fun aShortShifterSavesShiftTimeAndAMissedShiftCostsMore() {
        val bare = strip(weak)
        val short = strip(weak, with("shifter", 2))
        assertTrue(short.shiftSeconds < bare.shiftSeconds)
        assertTrue(clean(short).time < clean(bare).time)
        assertTrue(strip(weak, with("shifter", 2)).shiftSeconds < strip(weak, with("shifter", 1)).shiftSeconds)
        // A notchy gate in a clumsy hand loses time a smooth hand does not.
        assertTrue(short.notchy > bare.notchy)
    }

    @Test fun aLineLockHeatsTheRearTiresOnAStickyTireAndIsWastedOnABiasPly() {
        val bare = strip(strong)
        val locked = strip(strong, with("linelock", 1))
        assertTrue(locked.gripN > bare.gripN * 1.03)
        assertTrue(clean(locked).sixtyFt < clean(bare).sixtyFt)
        val radialGain = strip(strong, with("linelock", 1), tire = "radial").gripN / strip(strong, tire = "radial").gripN
        assertTrue("radials gain less ($radialGain)", radialGain < locked.gripN / bare.gripN)
        val biasGain = strip(strong, with("linelock", 1), tire = "bias").gripN / strip(strong, tire = "bias").gripN
        assertEquals(1.0, biasGain, 0.01)
    }

    @Test fun aNitrousShotPaysByThePointItOpensAndEveryLevelPaysMore() {
        // A car with spare grip takes the shot at the line and gets the best pull the earlier it opens.
        val kitW = { l: Int -> with("nos", l, "suspension" to 1) }
        val weakEarly = clean(strip(weak, kitW(3), mapOf("nos" to 0.0)))
        val weakMid = clean(strip(weak, kitW(3), mapOf("nos" to 60.0)))
        val weakLate = clean(strip(weak, kitW(3), mapOf("nos" to 150.0)))
        val dry = clean(strip(weak))
        assertTrue(weakEarly.time < weakMid.time && weakMid.time < dry.time)
        assertTrue("an early shot on spare grip is the best 60 ft", weakEarly.sixtyFt < weakMid.sixtyFt - 0.1)
        // The bottle runs dry: opened at 150 m it is still on at the line and the trap speed is highest.
        assertTrue("late ${weakLate.trapMps} early ${weakEarly.trapMps}", weakLate.trapMps > weakEarly.trapMps)
        // A shot opened too late leaves the bottle over, and the ET gives it back.
        val tooLate = clean(strip(weak, kitW(3), mapOf("nos" to 300.0)))
        assertTrue(tooLate.time > weakLate.time)
        // On a car the tires already cannot hold, opening in first and second is wasted: a mid shot beats an early one.
        val strongEarly = clean(strip(strong, kitW(3), mapOf("nos" to 0.0)))
        val strongMid = clean(strip(strong, kitW(3), mapOf("nos" to 60.0)))
        val strongLate = clean(strip(strong, kitW(3), mapOf("nos" to 300.0)))
        assertTrue("mid ${strongMid.time} early ${strongEarly.time}", strongMid.time < strongEarly.time)
        assertTrue("mid ${strongMid.time} late ${strongLate.time}", strongMid.time < strongLate.time)
        assertTrue(strongMid.time < clean(strip(strong)).time)
        // More shot, more push; and the heavier the kit, the more weight.
        val l1 = clean(strip(weak, kitW(1), mapOf("nos" to 60.0))).time
        val l2 = clean(strip(weak, kitW(2), mapOf("nos" to 60.0))).time
        val l3 = weakMid.time
        assertTrue("$l1 $l2 $l3", l1 > l2 && l2 > l3)
        assertTrue(strip(weak, kitW(3)).massKg > strip(weak, kitW(1)).massKg)
        assertTrue(strip(weak, kitW(3)).bottleS < strip(weak, kitW(1)).bottleS)
    }

    @Test fun theCircuitAndTheRivalsNeverSeeAStripPartOrKnob() {
        val s = GameState(
            prestiges = 6, chassis = "seven", transmission = "close", driveline = "lsd", tire = "slick",
            kit = Parts.strip.associate { it.id to it.levels.size } + mapOf("suspension" to 1),
            dragTune = mapOf("psi" to 4.0, "gear" to 0.3, "front" to 5.0),
        )
        val bare = s.copy(kit = mapOf("suspension" to 1), dragTune = emptyMap())
        assertEquals(RaceDrive.setup(s, Logic.sheet(s), circuit = true), RaceDrive.setup(bare, Logic.sheet(bare), circuit = true))
        val rival = RaceDrive.setupFor(Racing.car(bare), 300.0, 270.0, 7500.0, 900.0, 3.73, 402.3, 4000.0, false)
        assertEquals(99.0, strip(weak, tune = emptyMap(), chassis = "mule", tire = "bias").liftG.coerceAtLeast(99.0), 0.0)
        assertEquals(0.0, rival.launchRpm, 0.0)
        assertEquals(0.0, rival.spinTrim, 0.0)
        assertEquals(0.0, rival.shotFrac, 0.0)
        assertEquals(0.0, rival.flywheelKgm2, 0.0)
    }

    @Test fun aSaveWithStripPartsAndKnobsRoundTripsAndAnOldSaveLoadsOnTheDefaults() {
        val s = GameState(
            prestiges = 6, kit = mapOf("tbrake" to 1, "nos" to 2, "suspension" to 1),
            dragTune = mapOf("psi" to -2.0, "launch" to -300.0, "nos" to 120.0),
        )
        assertEquals(s, SaveCodec.decode(SaveCodec.encode(s)))
        val old = SaveCodec.encode(GameState(prestiges = 3, money = 4321.0))
            .replace(Regex(""",?\s*"dragTune"\s*:\s*\{[^}]*\}"""), "")
        assertFalse(old.contains("dragTune"))
        val loaded = SaveCodec.decode(old)
        assertEquals(emptyMap<String, Double>(), loaded.dragTune)
        assertEquals(4321.0, loaded.money, 0.0)
        assertEquals(GameState(prestiges = 3, money = 4321.0), loaded)
        // The old save drives the strip as it always did.
        assertEquals(0.0, RaceDrive.setup(loaded).launchRpm, 0.0)
        assertEquals(RaceDrive.setup(GameState(prestiges = 3, money = 4321.0)), RaceDrive.setup(loaded))
        // And a bare, older save without the field at all.
        assertEquals(emptyMap<String, Double>(), SaveCodec.decode("{}").dragTune)
    }
}
