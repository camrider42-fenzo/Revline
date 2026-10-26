package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.economy.Prestige
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import com.revline.idle.model.FuelKind
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stock misses the sheet. The same bolt-on shopping list clears it.
 * With that list, and with studies carried forward, each next engine takes a little less time.
 */
class BlockPaceTest {
    @Test
    fun aFilterMovesTheMopedAndThePreviewSaysSo() {
        val stock = GameState(money = 10_000.0)
        val preview = Logic.preview(stock, "filter")
        assertTrue(preview != null && preview.peakHp > 0.03)
        val bought = Logic.buyUpgrade(stock, "filter")
        assertEquals(1, bought.upgrades["filter"])
        val before = Logic.sheet(stock).stats.peakHorsepower
        val after = Logic.sheet(bought).stats.peakHorsepower
        assertTrue("filter $before -> $after", after > before + 0.03)
    }

    @Test
    fun aMaxedEngineStillClearsTheSheet() {
        val research = ResearchTree.all.map { it.id }.toSet()
        val maxed = Upgrades.all.associate { it.id to it.maxLevel }
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val stats = Logic.sheet(GameState(prestiges = index, researched = research, upgrades = maxed)).stats
            assertTrue(
                "${block.title} maxed ${"%.1f".format(stats.peakHorsepower)}/${"%.1f".format(stats.peakTorqueLbFt)}/${"%.1f".format(stats.cruiseMpg)} " +
                    "vs ${block.hpGoal}/${block.torqueGoal}/${block.mpgGoal}",
                Prestige.meets(stats.peakHorsepower, stats.peakTorqueLbFt, stats.cruiseMpg, block),
            )
        }
    }

    @Test
    fun stockMissesAndABoltOnBuildClearsEverySheet() {
        val research = ResearchTree.all.map { it.id }.toSet()
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val stock = Logic.sheet(GameState(prestiges = index)).stats
            assertFalse(
                "${block.title} stock already clears ${"%.1f".format(stock.peakHorsepower)} hp",
                Prestige.meets(stock.peakHorsepower, stock.peakTorqueLbFt, stock.cruiseMpg, block),
            )
            val built = Logic.sheet(
                GameState(prestiges = index, researched = research, upgrades = boltOn, fuel = FuelKind.PREMIUM_93),
            ).stats
            assertTrue(
                "${block.title} bolt-on misses ${"%.1f".format(built.peakHorsepower)} hp / " +
                    "${"%.1f".format(built.peakTorqueLbFt)} lb-ft / ${"%.1f".format(built.cruiseMpg)} mpg " +
                    "vs ${block.hpGoal} / ${block.torqueGoal} / ${block.mpgGoal}",
                Prestige.meets(built.peakHorsepower, built.peakTorqueLbFt, built.cruiseMpg, block),
            )
        }
    }

    @Test
    fun eachEngineTakesALittleLessTime() {
        var researched = emptySet<String>()
        var previous = Double.POSITIVE_INFINITY
        val lines = mutableListOf<String>()
        val problems = mutableListOf<String>()
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val start = GameState(
                prestiges = index,
                knowledge = index,
                researched = researched,
                savedAtEpochMs = 1L,
            )
            val run = play(start)
            lines += String.format(
                Locale.US,
                "%s  %.2f h  scale %.2f  %s",
                block.title,
                run.hours,
                block.upgradePriceScale(index),
                run.note,
            )
            if (!run.cleared) problems += "${block.title} did not clear: ${run.note}"
            if (index > 0 && run.cleared && previous.isFinite()) {
                if (run.hours > previous * 0.975) {
                    problems += "${block.title} took ${"%.2f".format(run.hours)} h after ${"%.2f".format(previous)} h"
                }
                if (run.hours < previous * 0.78) {
                    problems += "${block.title} collapsed to ${"%.2f".format(run.hours)} h from ${"%.2f".format(previous)} h"
                }
            }
            if (run.cleared) previous = run.hours
            researched = run.researched
        }
        println(lines.joinToString("\n"))
        assertTrue((problems + lines).joinToString("\n"), problems.isEmpty())
    }

    private fun play(start: GameState): Run {
        var state = tune(start)
        var sheet = Logic.sheet(state)
        var seconds = 0.0
        var sinceDyno = 0.0
        var step = 0
        val horizon = 48.0 * 3600.0
        while (seconds < horizon && step <= plan.size) {
            if (Logic.canPrestige(state)) {
                return Run(true, seconds / 3600.0, state.researched, "step $step")
            }
            if (step == plan.size) {
                return Run(false, seconds / 3600.0, state.researched, stuck(state, sheet))
            }
            val item = plan[step]
            val id = item.substring(2)
            if (item.startsWith("r:") && id in state.researched) {
                step++
                continue
            }
            val upgrade = if (item.startsWith("u:")) Upgrades.byId(id) else null
            if (upgrade != null && (state.upgrades[id] ?: 0) >= upgrade.maxLevel) {
                step++
                continue
            }
            val bought = when {
                item.startsWith("r:") && Logic.canBuyResearch(state, id) -> Logic.buyResearch(state, id)
                item.startsWith("u:") && Logic.canBuyUpgrade(state, id) -> Logic.buyUpgrade(state, id)
                else -> null
            }
            if (bought != null) {
                state = tune(bought)
                sheet = Logic.sheet(state)
                step++
                continue
            }
            val wait = waitFor(state, sheet, item)
            if (!wait.isFinite() || wait <= 0.0) {
                val why = if (item.startsWith("r:")) {
                    Logic.researchStatus(state, id).name
                } else {
                    "lock=${Logic.lockReason(state, id)} cost=${Logic.upgradeCost(state, id)}"
                }
                return Run(false, seconds / 3600.0, state.researched, "stuck on $item $why")
            }
            val tick = wait.coerceIn(1.0, 30.0)
            state = state.copy(
                money = state.money + sheet.incomePerSec * tick,
                researchPoints = state.researchPoints + sheet.rpPerSec * tick,
            )
            sinceDyno += tick
            if (sinceDyno >= 35.0) {
                state = state.copy(researchPoints = state.researchPoints + Logic.dynoReward(sheet.stats))
                sinceDyno = 0.0
            }
            seconds += tick
        }
        return Run(false, seconds / 3600.0, state.researched, stuck(state, sheet))
    }

    private fun waitFor(state: GameState, sheet: com.revline.idle.game.Sheet, item: String): Double {
        val id = item.substring(2)
        return if (item.startsWith("r:")) {
            val gap = ResearchTree.byId(id).cost - state.researchPoints
            if (gap <= 0.0) 0.0 else gap / sheet.rpPerSec
        } else {
            if (Logic.lockReason(state, id) != null) Double.POSITIVE_INFINITY
            val gap = Logic.upgradeCost(state, id) - state.money
            if (gap <= 0.0) 0.0 else gap / sheet.incomePerSec
        }
    }

    private fun tune(state: GameState): GameState {
        val base = Logic.ensureStudies(state)
        val fuel = if (Logic.fuelUnlocked(base, FuelKind.PREMIUM_93)) FuelKind.PREMIUM_93 else base.fuel
        val fueled = base.copy(fuel = fuel)
        return fueled.copy(throttle = Logic.bestThrottle(fueled))
    }

    private fun stuck(state: GameState, sheet: com.revline.idle.game.Sheet): String {
        val stats = sheet.stats
        val block = Block.forPrestige(state.prestiges)
        return "hp ${"%.1f".format(stats.peakHorsepower)}/${block.hpGoal} " +
            "tq ${"%.1f".format(stats.peakTorqueLbFt)}/${block.torqueGoal} " +
            "mpg ${"%.1f".format(stats.cruiseMpg)}/${block.mpgGoal}"
    }

    private data class Run(val cleared: Boolean, val hours: Double, val researched: Set<String>, val note: String)

    companion object {
        val boltOn = mapOf(
            "filter" to 5,
            "catback" to 3,
            "headers" to 3,
            "cold_air" to 2,
            "plugs" to 2,
            "springs" to 2,
            "synthetic" to 1,
        )
        val plan = listOf(
            "r:premium",
            "u:filter", "u:plugs", "u:catback", "u:synthetic",
            "u:filter", "u:plugs", "u:catback",
            "r:exhaust_fab",
            "u:headers", "u:headers", "u:headers",
            "u:filter", "u:filter", "u:filter",
            "u:catback",
            "u:cold_air", "u:cold_air",
            "u:springs", "u:springs",
        )
    }
}
