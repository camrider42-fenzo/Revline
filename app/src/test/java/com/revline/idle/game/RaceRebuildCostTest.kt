package com.revline.idle.game

import com.revline.idle.data.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A race rebuild costs research: a few hours of that block's income, more for each one in a row. */
class RaceRebuildCostTest {
    private val research = ResearchTree.all.map { it.id }.toSet()
    private val maxed = Upgrades.all.associate { it.id to it.maxLevel }
    private fun ready(rp: Double, streak: Int = 0) = GameState(researched = research, upgrades = maxed, researchPoints = rp, raceStreak = streak)

    @Test fun costIsHoursOfIncomeAndGrowsWithTheBlockAndTheStreak() {
        val base = Logic.raceRebuildCost(GameState())
        assertEquals(3.0 * 3600.0 * 0.7, base, 1e-6)
        assertEquals(base * 1.5, Logic.raceRebuildCost(GameState(raceStreak = 1)), 1e-6)
        assertEquals(base * 2.25, Logic.raceRebuildCost(GameState(raceStreak = 2)), 1e-6)
        var last = 0.0
        for (p in 0..7) { val c = Logic.raceRebuildCost(GameState(prestiges = p)); assertTrue("block $p", c > last); last = c }
        assertEquals(0.0, Logic.raceRebuildCost(GameState(devMode = true)), 0.0)
    }

    @Test fun aRaceRebuildIsBlockedWhenShortAndPaidWhenAffordable() {
        val cost = Logic.raceRebuildCost(ready(0.0))
        val short = ready(cost - 1.0)
        assertFalse(Logic.canRaceRebuild(short))
        assertEquals(short, Logic.prestige(short, true))
        val rich = Logic.prestige(ready(cost + 1000.0), true)
        assertEquals(1, rich.prestiges)
        assertTrue(rich.racing)
        assertEquals(1, rich.raceStreak)
        assertEquals(1000.0 * 0.20, rich.researchPoints, 1e-6)
    }

    @Test fun aStreetRebuildIsFreeAndResetsTheStreak() {
        val s = Logic.prestige(ready(0.0, streak = 3), false)
        assertEquals(1, s.prestiges)
        assertEquals(0, s.raceStreak)
        assertEquals(0.0, s.researchPoints, 0.0)
    }

    @Test fun theNextRaceRebuildInARowCostsMore() {
        val first = Logic.prestige(ready(1e9), true)
        assertTrue(Logic.raceRebuildCost(first) > Logic.raceRebuildCost(ready(0.0)) * 1.5)
    }
}
