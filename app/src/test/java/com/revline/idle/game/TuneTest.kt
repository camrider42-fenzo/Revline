package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.BlockPaceTest
import com.revline.idle.model.FuelKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneTest {
    @Test
    fun sparkStaysLockedUntilTheDistributorIsFitted() {
        assertFalse(Tuners.unlocked(GameState(), "spark"))
        val fitted = GameState(upgrades = mapOf("base_timing" to 1))
        assertTrue(Tuners.unlocked(fitted, "spark"))
        val stored = GameState(tune = mapOf("spark" to 4.0))
        assertEquals(0.0, Tuners.value(stored, "spark"), 1e-9)
        assertEquals(Logic.paramsFrom(GameState()).timingDeg, Logic.paramsFrom(stored).timingDeg, 1e-9)
    }

    @Test
    fun moreDistributorAndAMapShrinkTheSparkStep() {
        val coarse = GameState(upgrades = mapOf("base_timing" to 1))
        val fine = GameState(upgrades = mapOf("base_timing" to 4, "mapped_timing" to 2))
        assertTrue("${Tuners.step(fine, "spark")} vs ${Tuners.step(coarse, "spark")}", Tuners.step(fine, "spark") < Tuners.step(coarse, "spark"))
        val reachCoarse = Tuners.range(coarse, "spark").endInclusive
        val reachFine = Tuners.range(fine, "spark").endInclusive
        assertTrue(reachFine > reachCoarse)
    }

    @Test
    fun sparkAdvanceChangesTheTimingTheEngineRuns() {
        val fitted = GameState(money = 1.0, upgrades = mapOf("base_timing" to 2))
        val advanced = Tuners.nudge(fitted, "spark", 1)
        assertTrue(advanced.tune.getValue("spark") > 0.0)
        assertTrue(Logic.paramsFrom(advanced).timingDeg > Logic.paramsFrom(fitted).timingDeg)
    }

    @Test
    fun mixtureMovesTheWideOpenTarget() {
        val fitted = GameState(upgrades = mapOf("needle" to 2, "jets" to 2))
        val richer = Tuners.nudge(Tuners.nudge(fitted, "mixture", 1), "mixture", 1)
        val stockAfr = com.revline.idle.model.EngineSim.wotAfr(Logic.paramsFrom(fitted))
        val richAfr = com.revline.idle.model.EngineSim.wotAfr(Logic.paramsFrom(richer))
        assertTrue("stock $stockAfr rich $richAfr", richAfr < stockAfr)
    }

    @Test
    fun aChargerUnlocksTheWastegateAndTheControllerTightensIt() {
        assertFalse(Tuners.unlocked(GameState(), "boost"))
        val turbo = GameState(upgrades = mapOf("turbo" to 1))
        val controlled = GameState(upgrades = mapOf("turbo" to 2, "boost_controller" to 2))
        assertTrue(Tuners.unlocked(turbo, "boost"))
        assertTrue(Tuners.step(controlled, "boost") < Tuners.step(turbo, "boost"))
        val turned = Tuners.nudge(turbo, "boost", 1)
        assertTrue(Logic.paramsFrom(turned).boostControllerPsi > Logic.paramsFrom(turbo).boostControllerPsi)
    }

    @Test
    fun springsUnlockALimiterYouCanPullDown() {
        val sprung = GameState(upgrades = mapOf("springs" to 2))
        val limited = Tuners.nudge(sprung, "redline", -1)
        assertTrue(limited.tune.getValue("redline") < 0.0)
        assertTrue(Logic.paramsFrom(limited).redlineRpm < Logic.paramsFrom(sprung).redlineRpm)
    }

    @Test
    fun rebuildThrowsTheScrewsAwayWithTheParts() {
        val ready = GameState(
            researched = setOf("exhaust_fab", "perf_cam", "wasted_spark", "tbi", "ic_node", "premium"),
            upgrades = BlockPaceTest.sixty,
            tune = mapOf("spark" to 2.0),
            fuel = FuelKind.PREMIUM_93,
        )
        assertTrue(Logic.canPrestige(ready))
        val rebuilt = Logic.prestige(ready)
        assertTrue(rebuilt.tune.isEmpty())
        assertEquals(0.0, Tuners.value(rebuilt, "spark"), 0.0)
    }
}
