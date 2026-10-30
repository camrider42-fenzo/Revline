package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.economy.Prestige
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * mpg used to be a fixed road-load number times an additive economy multiplier with only a
 * global 8..95 clamp, and live mpg could read 400. Every stock engine already beat its own
 * goal. These tests hold the numbers to what each class of vehicle can really do.
 */
class MpgRealismTest {
    private val research = ResearchTree.all.map { it.id }.toSet()
    private val maxed = Upgrades.all.associate { it.id to it.maxLevel }
    private val econ = listOf("boot", "tip", "needle", "cold_air", "catback", "jets", "afr_map")
    private val econMaxed = econ.associateWith { id -> Upgrades.all.first { it.id == id }.maxLevel }

    private fun builds(index: Int): Map<String, GameState> = mapOf(
        "stock" to GameState(prestiges = index),
        "sixty" to GameState(prestiges = index, knowledge = index, researched = research, upgrades = BlockPaceTest.sixty),
        "maxed" to GameState(prestiges = index, knowledge = index, researched = research, upgrades = maxed),
        "economy only" to GameState(prestiges = index, knowledge = index, researched = research, upgrades = econMaxed),
    )

    @Test
    fun cruiseMpgStaysInTheRealWorldRangeForTheClass() {
        val bands = listOf(
            30.0..100.0, 25.0..80.0, 25.0..70.0, 22.0..62.0, 18.0..55.0, 15.0..40.0, 12.0..32.0, 10.0..26.0,
        )
        val misses = mutableListOf<String>()
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            for ((name, state) in builds(index)) {
                val mpg = Logic.sheet(state).stats.cruiseMpg
                if (mpg !in bands[index] || mpg >= block.mpgCap()) misses += "${block.title} $name ${"%.1f".format(mpg)} not in ${bands[index]}"
            }
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
        // A moped tops out around a hundred. The cars sit between 15 and 60.
        assertTrue(Block.MOPED.mpgCap() <= 110.0)
        assertTrue(Block.entries.drop(2).all { it.mpgCap() <= 70.0 })
    }

    @Test
    fun liveMpgNeverPassesTheClassCapAtAnyPedal() {
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            for ((name, state) in builds(index)) {
                val params = Logic.sheet(state).params
                for (throttle in listOf(0.05, 0.1, 0.2, 0.3, 0.5, 0.7, 0.85, 1.0)) {
                    val mpg = EngineSim.point(params, Logic.targetRpm(params.redlineRpm, throttle), Logic.loadFor(throttle)).mpg
                    assertTrue("${block.title} $name pedal $throttle mpg $mpg", mpg.isFinite() && mpg > 0.0 && mpg < block.mpgCap())
                }
            }
        }
    }

    @Test
    fun economyPartsCannotStackPastAFewPercentEach() {
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val stock = Logic.sheet(GameState(prestiges = index)).stats.cruiseMpg
            val trimmed = Logic.sheet(builds(index).getValue("economy only")).stats.cruiseMpg
            assertTrue("${block.title} economy parts $stock -> $trimmed", trimmed in stock * 0.95..stock * 1.30)
        }
    }

    @Test
    fun theBoundIsMonotoneNearlyFlatWellBelowTheCapAndNeverReachesIt() {
        var last = 0.0
        for (raw in listOf(1.0, 5.0, 20.0, 50.0, 100.0, 300.0, 1_000.0, 100_000.0)) {
            val bound = EngineSim.boundMpg(raw, 100.0)
            assertTrue("raw $raw -> $bound", bound > last && bound < 100.0)
            last = bound
        }
        assertEquals(10.0, EngineSim.boundMpg(10.0, 100.0), 0.01)
        assertEquals(0.0, EngineSim.boundMpg(-5.0, 100.0), 0.0)
    }

    @Test
    fun aStockEngineMissesItsMpgGoalAndPowerStillGatesTheRebuild() {
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val sheet = Logic.sheet(GameState(prestiges = index))
            assertTrue("${block.title} stock ${sheet.profit.mpg} vs ${block.mpgGoal}", sheet.profit.mpg < block.mpgGoal)
            assertFalse(
                "${block.title} stock",
                Prestige.meets(sheet.stats.peakHorsepower, sheet.stats.peakTorqueLbFt, sheet.profit.mpg, block),
            )
            // Power with a short mpg number does not clear.
            assertFalse(
                Prestige.meets(sheet.stats.peakHorsepower * 10, sheet.stats.peakTorqueLbFt * 10, block.mpgGoal - 0.05, block),
            )
        }
    }

    @Test
    fun anOldSaveWithEveryEconomyPartMaxedStillReadsASaneMpg() {
        // Saves keep parts, not mpg. Whatever they own now goes through the same cap on load.
        for (index in Block.entries.indices) {
            val state = GameState(prestiges = index, knowledge = 500, researched = research, upgrades = maxed, money = 1e12)
            val stats = Logic.sheet(state).stats
            assertTrue(stats.cruiseMpg.isFinite() && stats.cruiseMpg < Block.forPrestige(index).mpgCap())
            assertTrue(Logic.canPrestige(state) || stats.cruiseMpg >= 0.0)
        }
    }
}
