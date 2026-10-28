package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import com.revline.idle.model.BlockPaceTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevModeTest {
    @Test
    fun everyPartLevelIsFreeAndBuyingSpendsNothing() {
        var state = GameState(devMode = true, researched = ResearchTree.all.map { it.id }.toSet(), prestiges = 4)
        for (upgrade in Upgrades.all) {
            repeat(upgrade.maxLevel) {
                assertEquals(0.0, Logic.upgradeCost(state, upgrade.id), 0.0)
                assertTrue("${upgrade.id} level ${state.upgrades[upgrade.id]}", Logic.canBuyUpgrade(state, upgrade.id))
                state = Logic.buyUpgrade(state, upgrade.id)
            }
            assertEquals(upgrade.maxLevel, state.upgrades[upgrade.id])
        }
        assertEquals(0.0, state.money, 0.0)
    }

    @Test
    fun pastTheLimitStaysLockedUntilTheV8EvenInDevMode() {
        val early = GameState(devMode = true, researchPoints = 1e9, researched = setOf("billet_node", "forged", "hyper", "turbo_node", "exhaust_fab"))
        assertFalse(Logic.canBuyResearch(early, "overlimit"))
        val v8 = early.copy(prestiges = com.revline.idle.model.Block.V8.ordinal)
        assertTrue(Logic.canBuyResearch(v8, "overlimit"))
        assertEquals(0.0, Logic.researchCost(v8, "overlimit"), 0.0)
    }

    @Test
    fun studiesAreFreeButGatesStillApply() {
        val state = GameState(devMode = true)
        assertTrue(Logic.canBuyResearch(state, "heat_basics"))
        assertFalse(Logic.canBuyResearch(state, "oil_science"))
        assertFalse(Logic.canBuyUpgrade(state, "turbo"))
        val studied = Logic.buyResearch(state, "heat_basics")
        assertTrue("heat_basics" in studied.researched)
        assertEquals(0.0, studied.researchPoints, 0.0)
    }

    @Test
    fun offChargesFullPriceAndTheFlagSurvivesSaveAndRebuild() {
        val off = GameState()
        assertTrue(Logic.upgradeCost(off, "lap") > 0.0)
        assertFalse(Logic.canBuyUpgrade(off, "lap"))
        val on = GameState(devMode = true)
        assertTrue(SaveCodec.decode(SaveCodec.encode(on)).devMode)
        val ready = on.copy(researched = setOf("exhaust_fab", "perf_cam", "wasted_spark", "tbi", "ic_node", "premium"), upgrades = BlockPaceTest.sixty, fuel = com.revline.idle.model.FuelKind.PREMIUM_93)
        val rebuilt = Logic.prestige(ready)
        assertEquals(1, rebuilt.prestiges)
        assertTrue(rebuilt.devMode)
        val quiet = Logic.prestige(ready.copy(batterySaver = true))
        assertTrue(quiet.batterySaver)
        assertTrue(SaveCodec.decode(SaveCodec.encode(quiet)).batterySaver)
    }
}
