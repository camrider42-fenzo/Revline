package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchStatus
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import com.revline.idle.game.UpgradeMap
import com.revline.idle.game.Effect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The four 1.72.0 economy parts: each one lifts the mpg read at the profit pedal, they open across the ladder, and the goals still ask for work. */
class NewEconomyPartsTest {
    private val research = ResearchTree.all.map { it.id }.toSet()
    private val newIds = Logic.NEW_ECONOMY_IDS

    @Test fun theyAreEconomyPartsAndAreOnTheMap() {
        assertEquals(listOf("rings", "lean_spark", "egr", "cyl_off"), newIds)
        for (id in newIds) assertEquals(id, Effect.ECONOMY, UpgradeMap.effectOf(id))
    }

    @Test fun everyLevelLiftsTheProfitEconomyAndTheTopOfAllElevenIsPlusTwoPointFour() {
        for (id in newIds) {
            val max = Upgrades.byId(id).maxLevel
            var last = 1.0
            for (level in 1..max) {
                val p = Logic.paramsFrom(GameState(prestiges = 5, researched = research, upgrades = mapOf(id to level))).profitEconomy
                assertTrue("$id L$level $p after $last", p > last + 0.02)
                last = p
            }
        }
        val all = (Logic.ECONOMY_IDS + newIds).associateWith { Upgrades.byId(it).maxLevel }
        val p = Logic.paramsFrom(GameState(prestiges = 5, researched = research, upgrades = all)).profitEconomy
        assertEquals(1.0 + Logic.PROFIT_ECONOMY_GAIN + Logic.NEW_ECONOMY_GAIN, p, 1e-9)
        // The new parts do not touch power: same peak hp with and without them.
        val a = Logic.sheet(GameState(prestiges = 5, researched = research)).stats.peakHorsepower
        val b = Logic.sheet(GameState(prestiges = 5, researched = research, upgrades = newIds.associateWith { Upgrades.byId(it).maxLevel })).stats.peakHorsepower
        assertEquals(a, b, 1e-6)
    }

    @Test fun theMpgReadUnderLoadRisesOnEveryEngineFromTheTwinOn() {
        for (block in Block.entries) {
            val base = GameState(prestiges = block.ordinal, researched = research)
            val before = Logic.sheet(base).profit.mpg
            val after = Logic.sheet(base.copy(upgrades = newIds.associateWith { Upgrades.byId(it).maxLevel })).profit.mpg
            if (block.ordinal <= Block.THUMPER.ordinal) assertTrue(after >= before) else assertTrue("${block.title} $before -> $after", after > before * 1.2)
            assertTrue(after < block.mpgCap())
        }
    }

    @Test fun theStudiesOpenAcrossTheLadder() {
        fun at(id: String, index: Int, owned: Set<String>) = Logic.researchStatus(GameState(prestiges = index, researched = owned), id)
        assertEquals(ResearchStatus.LOCKED, at("lean_burn", 0, setOf("piggyback")))
        assertEquals(ResearchStatus.OPEN, at("lean_burn", Block.THUMPER.ordinal, setOf("piggyback")))
        assertEquals(ResearchStatus.LOCKED, at("egr_node", Block.TWIN.ordinal, setOf("piggyback", "lean_burn")))
        assertEquals(ResearchStatus.OPEN, at("egr_node", Block.TRIPLE.ordinal, setOf("piggyback", "lean_burn")))
        assertEquals(ResearchStatus.LOCKED, at("cda_node", Block.INLINE_FOUR.ordinal, setOf("piggyback", "lean_burn", "egr_node", "perf_cam", "vvt_node")))
        assertEquals(ResearchStatus.OPEN, at("cda_node", Block.V6.ordinal, setOf("piggyback", "lean_burn", "egr_node", "perf_cam", "vvt_node")))
        // A part is locked until its study is owned, and rings need none.
        val s = GameState(money = 1e9)
        assertEquals(null, Logic.lockReason(s, "rings"))
        for (id in listOf("lean_spark", "egr", "cyl_off")) assertNotNull(id, Logic.lockReason(s, id))
        assertTrue(Logic.canBuyUpgrade(s, "rings"))
        assertFalse(Logic.canBuyUpgrade(s, "egr"))
    }

    @Test fun theyAreDearerTheLaterTheyOpenAndTheFirstIsCheap() {
        val s = GameState(money = 1e12, researched = research)
        val first = newIds.map { Logic.upgradeCost(s, it) }
        assertTrue(first.toString(), first.zipWithNext().all { (a, b) -> b > a })
        assertTrue(Logic.upgradeCost(s, "rings") < Logic.upgradeCost(s, "boot") * 10)
    }

    @Test fun noSingleFirstLevelClearsTheGoalFromTwinOnAndStockStillMissesIt() {
        for (block in Block.entries.filter { it.ordinal >= Block.TWIN.ordinal }) {
            val stock = Logic.sheet(GameState(prestiges = block.ordinal, researched = research))
            assertTrue("${block.title} stock ${stock.profit.mpg} vs ${block.mpgGoal}", stock.profit.mpg < block.mpgGoal)
            for (id in Logic.ECONOMY_IDS + newIds) {
                val one = Logic.sheet(GameState(prestiges = block.ordinal, researched = research, upgrades = mapOf(id to 1)))
                assertTrue("${block.title} $id L1 ${one.profit.mpg} vs ${block.mpgGoal}", one.profit.mpg < block.mpgGoal)
            }
        }
    }

    @Test fun theOldSevenAloneStillClearTheRaisedGoalsAndTheNewFourAreExtraRoom() {
        for (block in Block.entries) {
            val old = Logic.sheet(GameState(prestiges = block.ordinal, researched = research, upgrades = Logic.ECONOMY_IDS.associateWith { Upgrades.byId(it).maxLevel })).profit.mpg
            val all = Logic.sheet(GameState(prestiges = block.ordinal, researched = research, upgrades = (Logic.ECONOMY_IDS + newIds).associateWith { Upgrades.byId(it).maxLevel })).profit.mpg
            assertTrue("${block.title} old seven $old vs ${block.mpgGoal}", old >= block.mpgGoal)
            assertTrue("${block.title} all eleven $all vs old $old", all >= old)
        }
    }
}
