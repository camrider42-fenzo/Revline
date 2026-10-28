package com.revline.idle.game

import com.revline.idle.data.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerkTest {
    @Test
    fun eachNewPerkMovesTheSystemItNames() {
        val stock = Logic.paramsFrom(GameState())
        val sheet = Logic.sheet(GameState())

        val brokenIn = Logic.paramsFrom(GameState(perks = mapOf("breakin" to 1)))
        val brokenInMore = Logic.paramsFrom(GameState(perks = mapOf("breakin" to 2)))
        assertTrue(brokenIn.friction < stock.friction)
        assertTrue(brokenInMore.friction < brokenIn.friction)
        assertTrue(Logic.sheet(GameState(perks = mapOf("breakin" to 2))).stats.peakHorsepower > sheet.stats.peakHorsepower)

        val bay = Logic.paramsFrom(GameState(perks = mapOf("bay_air" to 1)))
        assertEquals(stock.underhoodDeltaC - 1.5, bay.underhoodDeltaC, 1e-9)

        val cores = Logic.paramsFrom(GameState(perks = mapOf("cores" to 1)))
        val coresMore = Logic.paramsFrom(GameState(perks = mapOf("cores" to 2)))
        assertEquals(stock.coolingHeadroom + 0.10, cores.coolingHeadroom, 1e-9)
        assertTrue(coresMore.coolingHeadroom > cores.coolingHeadroom)

        val readers = Logic.paramsFrom(GameState(perks = mapOf("readers" to 1)))
        assertEquals(stock.ignitionQuality + 0.012, readers.ignitionQuality, 1e-9)

        val spool = Logic.paramsFrom(GameState(perks = mapOf("spool" to 2)))
        assertTrue(spool.spoolRpm < stock.spoolRpm - 300.0)

        val slips = Logic.sheet(GameState(perks = mapOf("slips" to 1)))
        assertEquals(sheet.params.dynoFactor * 1.10, slips.params.dynoFactor, 1e-9)
        assertEquals(sheet.incomePerSec, slips.incomePerSec, 1e-6)
        val stockPull = Logic.dynoReward(sheet.rpPerSec, sheet.params.dynoFactor)
        assertEquals(stockPull * 1.10, Logic.dynoReward(slips.rpPerSec, slips.params.dynoFactor), 0.01)

        val pump = Logic.paramsFrom(GameState(perks = mapOf("pump" to 1)))
        assertEquals(stock.fuelFlowFactor + 0.06, pump.fuelFlowFactor, 1e-9)
    }

    @Test
    fun aPerkSpendsKnowledgeAndStopsWhenTheRanksRunOut() {
        var state = GameState(knowledge = 1)
        state = Logic.buyPerk(state, "breakin")
        assertEquals(1, state.perks["breakin"])
        assertEquals(0, Logic.knowledgeFree(state))
        assertEquals(state, Logic.buyPerk(state, "breakin"))
        val maxed = GameState(knowledge = 20, perks = mapOf("readers" to 2))
        assertEquals(maxed, Logic.buyPerk(maxed, "readers"))
    }
}
