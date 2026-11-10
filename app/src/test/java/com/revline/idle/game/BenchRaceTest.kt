package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
import com.revline.idle.model.FuelKind
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The car on the strip and the circuit is the engine on the dyno, not a second curve. */
class BenchRaceTest {
    @Test fun theStripUsesTheBenchTorqueRedlineAndDisplacement() {
        val state = blown()
        val sheet = Logic.sheet(state)
        val setup = RaceDrive.setup(state)
        assertEquals(sheet.params.redlineRpm, setup.redline, 0.1)
        assertEquals(sheet.params.idleRpm, setup.idle, 0.1)
        assertEquals(sheet.stats.displacementCc, setup.displacementCc, 0.1)
        val peak = sheet.stats.curve.maxBy { it.torqueLbFt }
        assertEquals(peak.torqueLbFt, RaceDrive.benchLbFt(setup, peak.rpm), 0.05)
        val mid = sheet.stats.curve[sheet.stats.curve.size / 2]
        assertEquals(mid.torqueLbFt, RaceDrive.benchLbFt(setup, mid.rpm), 0.05)
        assertTrue(setup.pullRpm.size >= 2)
    }

    @Test fun aTurboIsNotTheGenericMidRangeCurve() {
        val setup = RaceDrive.setup(blown())
        val generic = setup.copy(pullRpm = emptyList(), pullLbFt = emptyList())
        val gaps = setup.pullRpm.map { rpm ->
            val real = RaceDrive.benchLbFt(setup, rpm)
            val old = RaceDrive.torqueNm(generic, rpm) / 1.3558
            abs(real - old) / old.coerceAtLeast(1.0)
        }
        assertTrue("the bench curve should leave the generic shape, max gap ${gaps.max()}", gaps.max() > 0.03)
    }

    @Test fun fuelOnTheDynoIsTheFuelInTheRace() {
        val pump = inline()
        val race = pump.copy(fuel = FuelKind.RACE_100)
        val pumpPull = RaceDrive.setup(pump).pullLbFt.max()
        val racePull = RaceDrive.setup(race).pullLbFt.max()
        assertEquals(Logic.sheet(pump).stats.peakTorqueLbFt, pumpPull, 0.2)
        assertEquals(Logic.sheet(race).stats.peakTorqueLbFt, racePull, 0.2)
    }

    @Test fun aBoredBlockRacesAtTheBoredSize() {
        val stock = RaceDrive.setup(inline())
        val bored = RaceDrive.setup(inline().copy(upgrades = mapOf("bore" to 3)))
        assertTrue(bored.displacementCc > stock.displacementCc + 20.0)
    }

    @Test fun anOldSaveWithARaceEngineBlobStillOpensOnTheBench() {
        val text = SaveCodec.encode(GameState(money = 1234.0, prestiges = 2)).dropLast(1) +
            ",\"raceEngine\":{\"peakHp\":999,\"curve\":[1,2,3]}}"
        val back = SaveCodec.decode(text)
        assertEquals(1234.0, back.money, 0.0)
        assertEquals(2, back.prestiges)
        val sheet = Logic.sheet(back)
        val setup = RaceDrive.setup(back)
        assertEquals(sheet.stats.peakTorqueLbFt, setup.pullLbFt.max(), 0.2)
        assertEquals(sheet.params.redlineRpm, setup.redline, 0.1)
    }

    @Test fun rivalsKeepTheGenericCurveAndThePlayerStillFinishes() {
        val state = inline()
        val you = RaceDrive.setup(state)
        val old = you.copy(pullRpm = emptyList(), pullLbFt = emptyList())
        val yours = RaceDrive.clean(you)
        val before = RaceDrive.clean(old)
        val ladder = Racing.fieldForLadder(state)
        val them = RaceDrive.clean(DragRivals.setup(ladder.rival, you.distanceM))
        val quick = Racing.fieldForQuick(state, Racing.Tier.CLUB)
        val ghost = RaceDrive.clean(DragRivals.setup(quick.rival, you.distanceM))
        assertTrue(yours.reached)
        assertTrue(them.reached)
        assertTrue(ghost.reached)
        assertTrue(DragRivals.setup(ladder.rival, you.distanceM).pullRpm.isEmpty())
        val ratio = yours.time / before.time
        assertTrue("player ET moved too far: $ratio", ratio in 0.82..1.22)
    }

    @Test fun heatPastTheLimitRaisesFadeAndHazardAndACoolSheetDoesNot() {
        val cool = Logic.sheet(GameState())
        assertEquals(0.1, Circuits.raceFade(0.1, cool.params, cool.stats), 1e-9)
        assertEquals(1.0, Circuits.raceHazard(1.0, cool.params, cool.stats), 1e-9)
        val hot = Logic.sheet(GameState(prestiges = Block.V8.ordinal, blockId = Block.V8.name))
        if (EngineSim.heatMarginC(hot.params, hot.stats) < 0.0) {
            assertTrue(Circuits.raceFade(0.1, hot.params, hot.stats) > 0.1)
            assertTrue(Circuits.raceHazard(1.0, hot.params, hot.stats) > 1.0)
            assertTrue(Circuits.raceFade(0.1, hot.params, hot.stats) <= 0.22)
            assertTrue(Circuits.raceHazard(1.0, hot.params, hot.stats) <= 1.35)
        }
    }

    @Test fun aSmallSingleKeepsTheDynoShapeAndTheStripFloor() {
        val sheet = Logic.sheet(GameState())
        val you = RaceDrive.setup(GameState())
        assertEquals(8.0, you.peakTqLbFt, 0.01)
        val run = RaceDrive.clean(you)
        assertTrue("moped ${run.time}s", run.reached)
        val low = sheet.stats.curve.first { it.rpm >= 2000.0 }
        val high = sheet.stats.curve.first { it.rpm >= 4400.0 }
        val shape = low.torqueLbFt / high.torqueLbFt
        val raced = RaceDrive.benchLbFt(you, low.rpm) / RaceDrive.benchLbFt(you, high.rpm)
        assertEquals(shape, raced, 0.04)
        assertTrue(RaceDrive.benchLbFt(you, high.rpm) > high.torqueLbFt)
    }

    private fun inline() = GameState(prestiges = Block.INLINE_FOUR.ordinal, blockId = Block.INLINE_FOUR.name)

    private fun blown() = inline().copy(upgrades = mapOf("turbo" to 3, "headers" to 2, "fuel_pump" to 3, "injectors" to 2))
}
