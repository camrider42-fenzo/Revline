package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.economy.Prestige
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchStatus
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
    fun aRadiatorMovesTheMopedSheet() {
        val stock = GameState(money = 100_000.0)
        val before = Logic.sheet(stock).stats.peakHorsepower
        val preview = Logic.preview(stock, "radiator")
        assertTrue(preview != null && preview.peakHp > 0.1)
        val bought = Logic.buyUpgrade(stock, "radiator")
        val after = Logic.sheet(bought).stats.peakHorsepower
        assertTrue("radiator $before -> $after", after > before + 0.1)
    }

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
        val misses = mutableListOf<String>()
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val stats = Logic.sheet(GameState(prestiges = index, researched = research, upgrades = maxed)).stats
            val line = "${block.title} maxed ${"%.1f".format(stats.peakHorsepower)}/${"%.1f".format(stats.peakTorqueLbFt)}/${"%.1f".format(stats.cruiseMpg)}"
            if (!Prestige.meets(stats.peakHorsepower, stats.peakTorqueLbFt, stats.cruiseMpg, block)) misses += line
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun aboutSixtyPercentOfTheShopClearsTheSheet() {
        val research = setOf("exhaust_fab", "perf_cam", "wasted_spark", "tbi", "ic_node", "premium")
        val share = sixty.size.toDouble() / Upgrades.all.size
        assertTrue("sixty share $share", share in 0.55..0.65)
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            fun stats(upgrades: Map<String, Int>) = Logic.sheet(
                GameState(
                    prestiges = index,
                    knowledge = index,
                    researched = research,
                    upgrades = upgrades,
                    fuel = FuelKind.PREMIUM_93,
                ),
            ).stats
            val stock = Logic.sheet(GameState(prestiges = index)).stats
            assertFalse(block.title, Prestige.meets(stock.peakHorsepower, stock.peakTorqueLbFt, stock.cruiseMpg, block))
            val thin = stats(forty)
            val wide = stats(sixty)
            assertFalse(
                "${block.title} forty ${thin.peakHorsepower.toInt()}/${thin.peakTorqueLbFt.toInt()}",
                Prestige.meets(thin.peakHorsepower, thin.peakTorqueLbFt, thin.cruiseMpg, block),
            )
            assertTrue(
                "${block.title} sixty ${"%.1f".format(wide.peakHorsepower)}/${"%.1f".format(wide.peakTorqueLbFt)}/${"%.1f".format(wide.cruiseMpg)} " +
                    "vs ${block.hpGoal}/${block.torqueGoal}/${block.mpgGoal}",
                Prestige.meets(wide.peakHorsepower, wide.peakTorqueLbFt, wide.cruiseMpg, block),
            )
        }
    }

    private fun buyBasket(start: GameState, basket: Map<String, Int>): Run {
        var state = tune(start)
        var sheet = Logic.sheet(state)
        var seconds = 0.0
        var sinceDyno = 0.0
        val horizon = 8.0 * 3600.0
        var guard = 0
        while (seconds < horizon && guard < 6000) {
            guard++
            if (basket.all { (state.upgrades[it.key] ?: 0) >= it.value }) {
                return Run(true, seconds / 3600.0, state.researched, "owned ${state.upgrades.count { it.value > 0 }}/${Upgrades.all.size}")
            }
            val nextPart = basket
                .filter { (state.upgrades[it.key] ?: 0) < it.value && Logic.lockReason(state, it.key) == null }
                .minByOrNull { Logic.upgradeCost(state, it.key) }
                ?.key
            val nextStudy = ResearchTree.all
                .filter { Logic.researchStatus(state, it.id) == ResearchStatus.OPEN }
                .filter { study ->
                    basket.any { (id, level) ->
                        (state.upgrades[id] ?: 0) < level && Logic.lockReason(state, id)?.contains(ResearchTree.byId(study.id).name) == true
                    }
                }
                .minByOrNull { it.cost }
            if (nextPart != null && Logic.canBuyUpgrade(state, nextPart)) {
                state = tune(Logic.buyUpgrade(state, nextPart))
                sheet = Logic.sheet(state)
                continue
            }
            if (nextStudy != null && Logic.canBuyResearch(state, nextStudy.id)) {
                state = tune(Logic.buyResearch(state, nextStudy.id))
                sheet = Logic.sheet(state)
                continue
            }
            val moneyWait = if (nextPart != null && sheet.incomePerSec > 1e-9) {
                (Logic.upgradeCost(state, nextPart) - state.money).coerceAtLeast(0.0) / sheet.incomePerSec
            } else Double.POSITIVE_INFINITY
            val rpWait = if (nextStudy != null && sheet.rpPerSec > 1e-9) {
                (nextStudy.cost - state.researchPoints).coerceAtLeast(0.0) / sheet.rpPerSec
            } else Double.POSITIVE_INFINITY
            val wait = minOf(moneyWait, rpWait)
            if (!wait.isFinite()) return Run(false, seconds / 3600.0, state.researched, stuck(state, sheet) + " part=$nextPart study=${nextStudy?.id}")
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
        return Run(false, seconds / 3600.0, state.researched, "horizon")
    }

    @Test
    fun eachEngineShopFitsInAnHourOrTwo() {
        var researched = setOf("premium")
        val lines = mutableListOf<String>()
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val start = GameState(
                prestiges = index,
                knowledge = index,
                researched = researched,
                fuel = FuelKind.PREMIUM_93,
                savedAtEpochMs = 1L,
            )
            val run = buyBasket(start, sixty)
            lines += "${block.title}  ${"%.2f".format(run.hours)} h  ${run.note}"
            assertTrue(lines.joinToString("\n"), run.cleared)
            assertTrue("${block.title} ${run.hours} h", run.hours in 0.85..2.0)
            researched = run.researched
        }
        println(lines.joinToString("\n"))
    }

    private fun tune(state: GameState): GameState {
        val base = state
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
        /** About 60% of the part list, including the research-gated pieces a shop actually reaches. */
        val sixty = mapOf(
            "lap" to 3, "gap" to 3, "boot" to 2, "tip" to 2, "needle" to 2,
            "oil_service" to 2, "fins" to 2, "wrench" to 3,
            "filter" to 3, "cold_air" to 2, "catback" to 2, "plugs" to 2,
            "base_timing" to 2, "springs" to 2, "synthetic" to 1,
            "jets" to 2, "fuel_pump" to 2, "cap" to 1, "radiator" to 2,
            "duct" to 1, "water_pump" to 1, "thermostat" to 1,
            "compression" to 2, "studs" to 1, "afr_map" to 2,
            "headers" to 2, "long_tube" to 1, "ceramic" to 1,
            "cam" to 1, "coils" to 2, "tbi_unit" to 1, "manifold" to 1,
            "intercooler" to 1,
        )
        val forty = sixty.filterKeys {
            it !in setOf(
                "headers", "long_tube", "ceramic", "cam", "coils", "tbi_unit",
                "manifold", "intercooler", "springs", "cold_air", "compression",
            )
        }
    }
}
