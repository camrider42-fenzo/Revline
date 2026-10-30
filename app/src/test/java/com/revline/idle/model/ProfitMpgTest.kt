package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.economy.Prestige
import com.revline.idle.game.Bottleneck
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.SheetAim
import com.revline.idle.game.Upgrade
import com.revline.idle.game.Upgrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rebuild sheet reads mpg at the pedal that pays the most, not at cruise. */
class ProfitMpgTest {
    private val research = ResearchTree.all.map { it.id }.toSet()
    private val maxed = Upgrades.all.associate { it.id to it.maxLevel }

    private fun builds(index: Int) = listOf(
        GameState(prestiges = index),
        GameState(prestiges = index, knowledge = index, researched = research, upgrades = BlockPaceTest.sixty),
        GameState(prestiges = index, knowledge = index, researched = research, upgrades = maxed),
        GameState(prestiges = index, racing = true, researched = research, upgrades = maxed),
    )

    @Test
    fun theGatePedalIsTheBestPayingStopUnderTheCap() {
        for (index in Block.entries.indices) {
            for (state in builds(index)) {
                val sheet = Logic.sheet(state)
                assertEquals(Logic.profitStop(state, Logic.GATE_CAP).throttle, sheet.profit.throttle, 1e-9)
                assertTrue(sheet.profit.throttle <= Logic.GATE_CAP + 1e-9)
                val params = sheet.params
                val mult = Prestige.multiplier(state.knowledge)
                var t = 0.05
                while (t <= Logic.GATE_CAP + 0.001) {
                    val pay = Logic.incomeAt(params, Logic.targetRpm(params.redlineRpm, t, params.idleRpm), sheet.stats.reliability, mult, t, sheet.stats)
                    assertTrue("${Block.forPrestige(index).title} t=$t pays $pay vs ${sheet.profit.incomePerSec}", pay <= sheet.profit.incomePerSec + 1e-9)
                    t += 0.05
                }
                // The open search the game's pace tests use is not capped and never pays less.
                val open = Logic.profitStop(state)
                assertEquals(Logic.bestThrottle(state), open.throttle, 1e-9)
                assertTrue(open.incomePerSec >= sheet.profit.incomePerSec - 1e-9)
            }
        }
    }

    @Test
    fun theCapKeepsNitrousAndBoostOutOfTheReading() {
        // Uncapped, the best-paying stop on a fully built big engine is flat out and reads the
        // 0.4 mpg floor. At the cap it reads a real figure and clears the goal.
        for (block in listOf(Block.TRIPLE, Block.INLINE_FOUR, Block.V6, Block.V8, Block.V12)) {
            val state = GameState(prestiges = block.ordinal, knowledge = block.ordinal, researched = research, upgrades = maxed)
            val sheet = Logic.sheet(state)
            val open = Logic.profitStop(state)
            assertTrue("${block.title} open ${open.mpg} at ${open.throttle}", open.mpg < 3.0 && open.mpg < sheet.profit.mpg / 5 && open.throttle > Logic.GATE_CAP)
            assertTrue("${block.title} capped ${sheet.profit.mpg}", sheet.profit.mpg >= block.mpgGoal)
        }
    }

    private val economyIds = listOf("boot", "tip", "needle", "cold_air", "catback", "jets", "afr_map")
    private fun economyOnly(level: (Upgrade) -> Int): Map<String, Int> =
        economyIds.associateWith { id -> Upgrades.all.first { it.id == id }.let(level) }

    @Test
    fun economyPartsLiftTheMpgReadUnderLoadAndEachLevelCounts() {
        for (index in Block.THUMPER.ordinal..Block.V12.ordinal) {
            val block = Block.forPrestige(index)
            for (id in economyIds) {
                val max = Upgrades.all.first { it.id == id }.maxLevel
                var last = Logic.sheet(GameState(prestiges = index, researched = research)).params.profitEconomy
                assertEquals(1.0, last, 1e-9)
                for (level in 1..max) {
                    val params = Logic.sheet(GameState(prestiges = index, researched = research, upgrades = mapOf(id to level))).params
                    assertTrue("${block.title} $id L$level ${params.profitEconomy} after $last", params.profitEconomy > last + 0.01)
                    last = params.profitEconomy
                }
            }
            val all = Logic.sheet(GameState(prestiges = index, researched = research, upgrades = economyOnly { it.maxLevel })).params
            assertEquals(1.0 + Logic.PROFIT_ECONOMY_GAIN, all.profitEconomy, 1e-9)
            val params = all.copy(profitEconomy = 1.0)
            for (load in listOf(0.2, 0.6, 1.0)) {
                val plain = EngineSim.point(params, params.redlineRpm * 0.6, load, 0.0)
                val tuned = EngineSim.point(all, params.redlineRpm * 0.6, load, 0.0)
                // A class that already sits near its realism cap has little room left to lift.
                val room = if (plain.mpg < block.mpgCap() * 0.5) 1.15 else 1.02
                assertTrue("${block.title} load $load ${tuned.mpg} vs ${plain.mpg}", tuned.mpg > plain.mpg * room)
                assertTrue(tuned.mpg < block.mpgCap())
                assertEquals("the paycheck reads the raw burn", plain.efficiency, tuned.efficiency, 1e-9)
            }
        }
    }

    @Test
    fun everyEngineStockMissesTheGoalAndTheEconomyPartsAreAPathToIt() {
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val stock = Logic.sheet(GameState(prestiges = index))
            assertTrue("${block.title} stock ${stock.profit.mpg} vs ${block.mpgGoal}", stock.profit.mpg < block.mpgGoal)
            val eco = Logic.sheet(GameState(prestiges = index, researched = research, upgrades = economyOnly { it.maxLevel }))
            assertTrue("${block.title} economy only ${eco.profit.mpg} vs ${block.mpgGoal}", eco.profit.mpg >= block.mpgGoal)
            // The economy parts alone do not rebuild the engine. The power is still the other half.
            assertFalse(block.title, Logic.canPrestige(GameState(prestiges = index, researched = research, upgrades = economyOnly { it.maxLevel }, money = 1e12)))
        }
    }

    @Test
    fun theFortyAndSixtyBuildsKeepTheirPlaceAgainstTheGoal() {
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val sixty = Logic.sheet(GameState(prestiges = index, knowledge = index, researched = research, upgrades = BlockPaceTest.sixty))
            assertTrue("${block.title} sixty ${sixty.profit.mpg} vs ${block.mpgGoal}", sixty.profit.mpg >= block.mpgGoal)
            for (racing in listOf(false, true)) {
                val full = Logic.sheet(GameState(prestiges = index, racing = racing, researched = research, upgrades = maxed))
                assertTrue("${block.title} race=$racing ${full.profit.mpg}", full.profit.mpg >= block.sheetMpg(racing))
            }
        }
    }

    @Test
    fun profitMpgIsTheLiveMpgAtThatPedalAndNotTheCruiseFigure() {
        var differs = 0
        for (index in Block.entries.indices) {
            for (state in builds(index)) {
                val sheet = Logic.sheet(state)
                val live = EngineSim.point(
                    sheet.params,
                    Logic.targetRpm(sheet.params.redlineRpm, sheet.profit.throttle, sheet.params.idleRpm),
                    Logic.loadFor(sheet.profit.throttle),
                )
                assertEquals(live.mpg, sheet.profit.mpg, 1e-9)
                assertTrue(sheet.profit.mpg < Block.forPrestige(index).mpgCap())
                if (kotlin.math.abs(sheet.profit.mpg - sheet.stats.cruiseMpg) > 1.0) differs++
            }
        }
        assertTrue("profit mpg should differ from cruise on most builds, differed on $differs", differs >= 20)
    }

    @Test
    fun theRebuildGateUsesProfitMpg() {
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val ready = GameState(prestiges = index, knowledge = index, researched = research, upgrades = maxed, money = 1e12)
            val sheet = Logic.sheet(ready)
            val expected = Prestige.meets(sheet.stats.peakHorsepower, sheet.stats.peakTorqueLbFt, sheet.profit.mpg, block)
            assertEquals(block.title, expected, Logic.canPrestige(ready))
            assertTrue("${block.title} maxed ${sheet.profit.mpg} vs ${block.mpgGoal}", Logic.canPrestige(ready))
        }
        val thumper = Block.THUMPER
        assertFalse(Prestige.meets(thumper.hpGoal, thumper.torqueGoal, thumper.mpgGoal - 0.1, thumper))
        assertTrue(Prestige.meets(thumper.hpGoal, thumper.torqueGoal, thumper.mpgGoal, thumper))
    }

    @Test
    fun mpgOnlyGatesAndDoesNotRaiseTheKnowledgePayout() {
        for (block in Block.entries) {
            val at = Prestige.knowledgeGain(block.hpGoal, block.torqueGoal, block.mpgGoal, block)
            val far = Prestige.knowledgeGain(block.hpGoal, block.torqueGoal, block.mpgGoal * 50, block)
            assertEquals(1, at)
            assertEquals(at, far)
        }
    }

    @Test
    fun theGoalsStayUnderTheClassRealismCap() {
        for (block in Block.entries) {
            assertTrue(block.title, block.mpgGoal < block.mpgCap())
            assertTrue(block.title, block.sheetMpg(true) < block.sheetMpg(false))
        }
    }

    @Test
    fun thePrettyLabelsSayProfitPedal() {
        val state = GameState(prestiges = Block.V6.ordinal, researched = research, upgrades = maxed)
        val sheet = Logic.sheet(state)
        val live = EngineSim.point(sheet.params, 3000.0, 0.4, 0.0)
        // Power and torque met, mpg short: the strip names the profit pedal, not cruise.
        val tip = Bottleneck.suggest(state.copy(upgrades = maxed, soakC = 0.0), sheet.stats.copy(reliabilityNotes = emptyList()), 0.0, live.copy(fuelLimited = false), 0.0)
        assertTrue(tip.detail, tip.detail.contains("profit pedal"))
        assertFalse(tip.detail.contains("Cruise"))
        val aim = SheetAim.recommend(GameState(prestiges = Block.TWIN.ordinal), com.revline.idle.game.AimGoal.CRUISE)
        assertNotEquals("", aim.detail)
        assertTrue(aim.headline.isNotEmpty())
        assertEquals("MPG@$", com.revline.idle.game.AimGoal.CRUISE.short)
    }
}
