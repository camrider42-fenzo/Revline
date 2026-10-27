package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchStatus
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import com.revline.idle.model.Block
import com.revline.idle.model.BlockPaceTest
import com.revline.idle.model.FuelKind
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plays the ladder with the 60% shop on each engine and buys a study whenever one
 * is affordable. Studies should spread over the first half of the ladder instead of
 * finishing on the moped.
 */
class ResearchPaceTest {
    @Test
    fun studiesSpreadAcrossTheLadder() {
        var researched = emptySet<String>()
        var carriedRp = 0.0
        val owned = mutableListOf<Int>()
        val lines = mutableListOf<String>()
        for (index in Block.entries.indices) {
            val start = GameState(
                prestiges = index,
                knowledge = index,
                researched = researched,
                researchPoints = carriedRp,
                savedAtEpochMs = 1L,
            )
            val run = play(start)
            owned += run.state.researched.size
            val s = Logic.sheet(run.state).stats
            val b = Block.forPrestige(index)
            lines += String.format(
                Locale.US,
                "%-20s %.2f h  studies %2d/%d  rp/s %.2f  dyno %.0f  banked %.0f  " +
                    "hp %.0f/%.0f tq %.0f/%.0f mpg %.1f/%.1f",
                Block.forPrestige(index).title,
                run.hours,
                run.state.researched.size,
                ResearchTree.all.size,
                run.rpPerSec,
                run.dyno,
                run.state.researchPoints,
                s.peakHorsepower, b.hpGoal, s.peakTorqueLbFt, b.torqueGoal, s.cruiseMpg, b.mpgGoal,
            )
            assertTrue(lines.joinToString("\n"), run.cleared)
            researched = run.state.researched
            carriedRp = run.state.researchPoints * 0.20
        }
        println(lines.joinToString("\n"))
        val total = ResearchTree.all.size
        val ordinary = ResearchTree.all.count { it.minPrestige == 0 }
        val report = lines.joinToString("\n")
        assertTrue("moped should study the basics\n$report", owned[0] in 8..20 && owned[0] < ordinary)
        assertTrue("thumper should still be studying\n$report", owned[1] < ordinary)
        assertTrue("the twin should not finish the notebook\n$report", owned[2] < ordinary)
        assertTrue("the ordinary notebook should close by the V6\n$report", owned[5] == ordinary)
        assertTrue("the V8 should open the past-the-limit branch\n$report", owned[6] > ordinary)
        assertTrue("the branch should be finished on the V12\n$report", owned[7] == total)
    }

    private fun play(start: GameState): Run {
        val basket = BlockPaceTest.sixty
        var state = tune(start)
        var sheet = Logic.sheet(state)
        var seconds = 0.0
        var sinceDyno = 0.0
        var guard = 0
        while (seconds < 12.0 * 3600.0 && guard < 20_000) {
            guard++
            val study = ResearchTree.all
                .filter { Logic.researchStatus(state, it.id) == ResearchStatus.OPEN }
                .minByOrNull { it.cost }
            if (study != null && Logic.canBuyResearch(state, study.id)) {
                state = tune(Logic.buyResearch(state, study.id))
                sheet = Logic.sheet(state)
                continue
            }
            val done = basket.all { (state.upgrades[it.key] ?: 0) >= it.value } && Logic.canPrestige(state)
            if (done) return Run(true, seconds / 3600.0, state, sheet.rpPerSec, Logic.dynoReward(sheet.stats, sheet.params, sheet.multiplier))
            val part = basket
                .filter { (state.upgrades[it.key] ?: 0) < it.value && Logic.lockReason(state, it.key) == null }
                .minByOrNull { Logic.upgradeCost(state, it.key) }
                ?.key
                ?: Upgrades.all
                    .filter { (state.upgrades[it.id] ?: 0) < it.maxLevel && Logic.lockReason(state, it.id) == null }
                    .minByOrNull { Logic.upgradeCost(state, it.id) }
                    ?.id
            if (part != null && Logic.canBuyUpgrade(state, part)) {
                state = tune(Logic.buyUpgrade(state, part))
                sheet = Logic.sheet(state)
                continue
            }
            val moneyWait = if (part != null && sheet.incomePerSec > 1e-9) {
                (Logic.upgradeCost(state, part) - state.money).coerceAtLeast(0.0) / sheet.incomePerSec
            } else {
                Double.POSITIVE_INFINITY
            }
            val rpWait = if (study != null && sheet.rpPerSec > 1e-9) {
                (study.cost - state.researchPoints).coerceAtLeast(0.0) / sheet.rpPerSec
            } else {
                Double.POSITIVE_INFINITY
            }
            val wait = minOf(moneyWait, rpWait)
            if (!wait.isFinite()) break
            val tick = wait.coerceIn(1.0, 30.0)
            state = state.copy(
                money = state.money + sheet.incomePerSec * tick,
                researchPoints = state.researchPoints + sheet.rpPerSec * tick,
            )
            sinceDyno += tick
            if (sinceDyno >= 35.0) {
                state = state.copy(researchPoints = state.researchPoints + Logic.dynoReward(sheet.stats, sheet.params, sheet.multiplier))
                sinceDyno = 0.0
            }
            seconds += tick
        }
        return Run(false, seconds / 3600.0, state, sheet.rpPerSec, Logic.dynoReward(sheet.stats, sheet.params, sheet.multiplier))
    }

    private fun tune(state: GameState): GameState {
        val base = state
        val fuel = if (Logic.fuelUnlocked(base, FuelKind.PREMIUM_93)) FuelKind.PREMIUM_93 else base.fuel
        val fueled = base.copy(fuel = fuel)
        return fueled.copy(throttle = Logic.bestThrottle(fueled))
    }

    private data class Run(
        val cleared: Boolean,
        val hours: Double,
        val state: GameState,
        val rpPerSec: Double,
        val dyno: Double,
    )
}
