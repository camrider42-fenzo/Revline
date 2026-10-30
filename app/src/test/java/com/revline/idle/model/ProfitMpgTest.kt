package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.economy.Prestige
import com.revline.idle.game.Bottleneck
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.SheetAim
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
    fun theProfitPedalIsTheSameOneTheGameUsesAndNoStepPaysMore() {
        for (index in Block.entries.indices) {
            for (state in builds(index)) {
                val sheet = Logic.sheet(state)
                assertEquals(Logic.bestThrottle(state), sheet.profit.throttle, 1e-9)
                val params = sheet.params
                val mult = Prestige.multiplier(state.knowledge)
                var t = 0.05
                while (t <= 1.001) {
                    val pay = Logic.incomeAt(params, Logic.targetRpm(params.redlineRpm, t, params.idleRpm), sheet.stats.reliability, mult, t, sheet.stats)
                    assertTrue("${Block.forPrestige(index).title} t=$t pays $pay vs ${sheet.profit.incomePerSec}", pay <= sheet.profit.incomePerSec + 1e-9)
                    t += 0.05
                }
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
        assertEquals("CRUISE", com.revline.idle.game.AimGoal.CRUISE.short)
    }
}
