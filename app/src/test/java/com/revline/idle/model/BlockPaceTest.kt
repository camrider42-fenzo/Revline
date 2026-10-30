package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.economy.Prestige
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchStatus
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.SheetAim
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
        // Buying every part must never leave a player stuck below the sheet.
        val research = ResearchTree.all.map { it.id }.toSet()
        val maxed = Upgrades.all.associate { it.id to it.maxLevel }
        val misses = mutableListOf<String>()
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val sheet = Logic.sheet(GameState(prestiges = index, researched = research, upgrades = maxed))
            val stats = sheet.stats
            val line = "${block.title} maxed ${"%.1f".format(stats.peakHorsepower)}/${"%.1f".format(stats.peakTorqueLbFt)}/${"%.1f".format(sheet.profit.mpg)}"
            if (!Prestige.meets(stats.peakHorsepower, stats.peakTorqueLbFt, sheet.profit.mpg, block)) misses += line
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
            fun sheetOf(upgrades: Map<String, Int>) = Logic.sheet(
                GameState(
                    prestiges = index,
                    knowledge = index,
                    researched = research,
                    upgrades = upgrades,
                    fuel = FuelKind.PREMIUM_93,
                ),
            )
            val stockSheet = Logic.sheet(GameState(prestiges = index))
            val stock = stockSheet.stats
            assertFalse(block.title, Prestige.meets(stock.peakHorsepower, stock.peakTorqueLbFt, stockSheet.profit.mpg, block))
            val thinSheet = sheetOf(forty)
            val wideSheet = sheetOf(sixty)
            val thin = thinSheet.stats
            val wide = wideSheet.stats
            assertFalse(
                "${block.title} forty ${thin.peakHorsepower.toInt()}/${thin.peakTorqueLbFt.toInt()}",
                Prestige.meets(thin.peakHorsepower, thin.peakTorqueLbFt, thinSheet.profit.mpg, block),
            )
            assertTrue(
                "${block.title} sixty ${"%.1f".format(wide.peakHorsepower)}/${"%.1f".format(wide.peakTorqueLbFt)}/${"%.1f".format(wideSheet.profit.mpg)} " +
                    "vs ${block.hpGoal}/${block.torqueGoal}/${block.mpgGoal}",
                Prestige.meets(wide.peakHorsepower, wide.peakTorqueLbFt, wideSheet.profit.mpg, block),
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
                state = state.copy(researchPoints = state.researchPoints + Logic.dynoReward(sheet.rpPerSec, sheet.params.dynoFactor))
                sinceDyno = 0.0
            }
            seconds += tick
        }
        return Run(false, seconds / 3600.0, state.researched, "horizon")
    }

    @Test
    fun aShopThatRebuildsWhenTheSheetClearsTakesAboutTwoHoursAndGrowsEachEngine() {
        var researched = emptySet<String>()
        var carriedRp = 0.0
        val lines = mutableListOf<String>()
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val start = GameState(
                prestiges = index,
                knowledge = index,
                researched = researched,
                researchPoints = carriedRp,
                money = Logic.startingMoney(index),
                savedAtEpochMs = 1L,
            )
            val run = clearSheet(start)
            val owned = run.state.upgrades.entries.filter { it.value > 0 }.joinToString(" ") { "${it.key}:${it.value}" }
            lines += "${block.title}  ${"%.2f".format(run.hours)} h  ${run.note}  $owned"
            researched = run.state.researched
            carriedRp = run.state.researchPoints * 0.20
        }
        println(lines.joinToString("\n"))
        val hours = lines.map { it.substringAfter("  ").substringBefore(" h").toDouble() }
        val bad = hours.indices.filter { hours[it] !in (TARGET_HOURS[it] * 0.85)..(TARGET_HOURS[it] * 1.15) }
        assertTrue(lines.joinToString("\n"), bad.isEmpty())
        assertTrue("each rebuild takes longer than the last: $hours", hours.zipWithNext().all { (a, b) -> b > a })
        // The old sheet cleared in about 0.8 to 1.9 hours per engine, 7 hours across all eight.
        assertTrue("total ${hours.sum()} h", hours.sum() > 7.0 * 2.0)
    }

    private fun clearSheet(start: GameState): Clear {
        var state = tune(start)
        var sheet = Logic.sheet(state)
        var seconds = 0.0
        var sinceDyno = 0.0
        val horizon = 8.0 * 3600.0
        var guard = 0
        while (seconds < horizon && guard < 20_000) {
            guard++
            if (Logic.canPrestige(state)) {
                return Clear(true, seconds / 3600.0, state, stuck(state, sheet))
            }
            val study = ResearchTree.all
                .filter { Logic.researchStatus(state, it.id) == ResearchStatus.OPEN }
                .minByOrNull { it.cost }
            val aim = SheetAim.recommend(state).upgradeId
            val buy = when {
                aim != null && Logic.canBuyUpgrade(state, aim) -> aim
                else -> affordableHelp(state, sheet.incomePerSec)
            }
            if (buy != null) {
                state = tune(Logic.buyUpgrade(state, buy))
                sheet = Logic.sheet(state)
                continue
            }
            if (aim == null && study != null && Logic.canBuyResearch(state, study.id)) {
                state = tune(Logic.buyResearch(state, study.id))
                sheet = Logic.sheet(state)
                continue
            }
            val moneyWait = if (aim != null && sheet.incomePerSec > 1e-9) {
                (Logic.upgradeCost(state, aim) - state.money).coerceAtLeast(0.0) / sheet.incomePerSec
            } else Double.POSITIVE_INFINITY
            val rpWait = if (aim == null && study != null && sheet.rpPerSec > 1e-9) {
                (Logic.researchCost(state, study.id) - state.researchPoints).coerceAtLeast(0.0) / sheet.rpPerSec
            } else Double.POSITIVE_INFINITY
            val wait = minOf(moneyWait, rpWait)
            if (!wait.isFinite()) return Clear(false, seconds / 3600.0, state, stuck(state, sheet))
            val tick = wait.coerceIn(1.0, 30.0)
            state = state.copy(
                money = state.money + sheet.incomePerSec * tick,
                researchPoints = state.researchPoints + sheet.rpPerSec * tick,
            )
            sinceDyno += tick
            if (sinceDyno >= 35.0) {
                state = state.copy(
                    researchPoints = state.researchPoints + Logic.dynoReward(sheet.rpPerSec, sheet.params.dynoFactor),
                )
                sinceDyno = 0.0
            }
            seconds += tick
        }
        return Clear(false, seconds / 3600.0, state, stuck(state, sheet))
    }

    /** A cheap part the sheet still feels. Saving for the real recommendation otherwise. */
    private fun affordableHelp(state: GameState, incomePerSec: Double): String? {
        val budget = incomePerSec * 8.0 * 60.0
        if (budget < 1.0) return null
        val tools = setOf("lap", "gap", "boot", "tip", "needle", "oil_service", "fins", "wrench")
        val before = EngineSim.evaluate(Logic.paramsFrom(state))
        return Upgrades.all
            .filter { it.id in tools && Logic.canBuyUpgrade(state, it.id) && Logic.upgradeCost(state, it.id) <= budget }
            .filter { upgrade ->
                val level = (state.upgrades[upgrade.id] ?: 0) + 1
                val after = EngineSim.evaluate(
                    Logic.paramsFrom(state.copy(upgrades = state.upgrades + (upgrade.id to level))),
                )
                after.peakHorsepower > before.peakHorsepower + 0.2 ||
                    after.peakTorqueLbFt > before.peakTorqueLbFt + 0.5 ||
                    after.cruiseMpg > before.cruiseMpg + 0.15
            }
            .minByOrNull { Logic.upgradeCost(state, it.id) }
            ?.id
    }

    private data class Clear(val cleared: Boolean, val hours: Double, val state: GameState, val note: String)

    @Test
    fun eachEngineShopFitsInAFewHours() {
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
            assertTrue("${block.title} ${run.hours} h", run.hours in 1.3..7.0)
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
            "mpg@profit ${"%.1f".format(sheet.profit.mpg)}/${block.mpgGoal}"
    }

    private data class Run(val cleared: Boolean, val hours: Double, val researched: Set<String>, val note: String)

    companion object {
        /** Hours of play to clear each engine's sheet, moped first. Was 0.8 to 1.0 each. */
        val TARGET_HOURS = listOf(1.6, 1.8, 2.0, 2.2, 2.4, 2.6, 2.8, 3.0)

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
