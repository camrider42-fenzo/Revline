package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
import com.revline.idle.model.FuelKind
import com.revline.idle.model.Induction
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plays a shop from a stock long-block through two rebuilds and then every
 * study and every part level. This is the balance and bug gate for an APK.
 */
class FullShopRunTest {
    @Test
    fun rebuildKeepsStudiedHardware() {
        val studied = Logic.buyResearch(
            GameState(researchPoints = 1_000.0, researched = setOf("exhaust_fab")),
            "turbo_node",
        )
        assertEquals(1, studied.upgrades["turbo"])
        val ready = studied.copy(
            researched = setOf("exhaust_fab", "perf_cam", "wasted_spark", "tbi", "ic_node", "premium", "turbo_node"),
            fuel = FuelKind.PREMIUM_93,
            upgrades = com.revline.idle.model.BlockPaceTest.sixty,
        )
        assertTrue(Logic.canPrestige(ready))
        val rebuilt = Logic.prestige(ready)
        assertEquals(1, rebuilt.prestiges)
        assertEquals(1, rebuilt.upgrades["turbo"])
        assertEquals(Induction.TURBO, Logic.paramsFrom(rebuilt).induction)
    }

    @Test
    fun shopCanMaxEveryStudyAndEveryPart() {
        val result = play()
        println(result.report)
        assertTrue(result.report, result.finished)
        assertTrue("full shop took ${result.hours} h\n${result.report}", result.hours < 240.0)
        assertTrue(
            "maxed engine should out-earn stock (${result.finalPay} vs ${result.stockPay})",
            result.finalPay > result.stockPay * 1.5,
        )
        assertTrue("full-build boost ${result.fullBoost}", result.fullBoost > 3.0)
    }

    private fun play(): Run {
        val stockSheet = Logic.sheet(GameState().copy(throttle = Logic.bestThrottle(GameState())))
        var state = GameState(savedAtEpochMs = 1L)
        state = tune(state)
        var sheet = Logic.sheet(state)
        var seconds = 0.0
        var sinceDyno = 0.0
        var prestiges = 0
        val notes = StringBuilder()
        val horizon = 240.0 * 3600.0
        var guard = 0
        while (seconds < horizon && guard < 20_000) {
            guard++
            checkSanity(state, sheet, seconds)
            if (complete(state)) {
                val boost = boostAtPedal(state, 0.85)
                notes.append(summary(state, sheet, seconds, boost))
                return Run(true, seconds / 3600.0, sheet.incomePerSec, stockSheet.incomePerSec, boost, notes.toString())
            }
            val research = nextResearch(state)
            val upgrade = nextUpgrade(state)
            val bought = when {
                research != null && Logic.canBuyResearch(state, research) -> Logic.buyResearch(state, research)
                upgrade != null && Logic.canBuyUpgrade(state, upgrade) -> Logic.buyUpgrade(state, upgrade)
                else -> null
            }
            if (bought != null) {
                state = tune(bought)
                sheet = Logic.sheet(state)
                if ("turbo_node" in state.researched) {
                    assertTrue("studied turbo missing at ${seconds / 3600.0} h", (state.upgrades["turbo"] ?: 0) >= 1)
                    val pulled = EngineSim.point(sheet.params, 5200.0, 1.0).boostPsi
                    assertTrue("turbo makes no boost (${pulled} psi)", pulled > 1.0)
                }
                continue
            }
            if (prestiges < 2 && Logic.canPrestige(state)) {
                val rebuilt = tune(Logic.prestige(state))
                val nextSheet = Logic.sheet(rebuilt)
                notes.append(
                    String.format(
                        Locale.US,
                        "rebuild %d at %.2f h  $%.0f/s  hp %.1f -> %s hp %.1f  parts %s%n",
                        prestiges + 1,
                        seconds / 3600.0,
                        sheet.incomePerSec,
                        sheet.stats.peakHorsepower,
                        Block.forPrestige(rebuilt.prestiges).title,
                        nextSheet.stats.peakHorsepower,
                        rebuilt.upgrades,
                    ),
                )
                state = rebuilt
                sheet = Logic.sheet(state)
                prestiges++
                checkSanity(state, sheet, seconds)
                continue
            }
            val wait = waitSeconds(state, sheet)
            if (!wait.isFinite() || wait > horizon) {
                notes.append(stuck(state, sheet, seconds))
                return Run(false, seconds / 3600.0, sheet.incomePerSec, stockSheet.incomePerSec, 0.0, notes.toString())
            }
            val step = wait.coerceIn(1.0, 30.0)
            state = state.copy(
                money = state.money + sheet.incomePerSec * step,
                runEarnings = state.runEarnings + sheet.incomePerSec * step,
                researchPoints = state.researchPoints + sheet.rpPerSec * step,
            )
            sinceDyno += step
            if (sinceDyno >= 35.0) {
                state = state.copy(researchPoints = state.researchPoints + Logic.dynoReward(sheet.stats))
                sinceDyno = 0.0
            }
            seconds += step
        }
        notes.append(stuck(state, sheet, seconds))
        return Run(false, seconds / 3600.0, sheet.incomePerSec, stockSheet.incomePerSec, 0.0, notes.toString())
    }

    private fun tune(state: GameState): GameState {
        val base = Logic.ensureStudies(state)
        val throttle = Logic.bestThrottle(base)
        var tuned = base.copy(throttle = throttle)
        val fuel = FuelKind.entries
            .filter { Logic.fuelUnlocked(tuned, it) }
            .maxBy { kind ->
                val trial = tuned.copy(fuel = kind)
                Logic.sheet(trial.copy(throttle = Logic.bestThrottle(trial))).incomePerSec
            }
        if (fuel != tuned.fuel) {
            tuned = tuned.copy(fuel = fuel, throttle = Logic.bestThrottle(tuned.copy(fuel = fuel)))
        }
        return tuned
    }

    private fun nextResearch(state: GameState): String? =
        ResearchTree.all
            .filter { Logic.canBuyResearch(state, it.id) }
            .minByOrNull { it.cost }
            ?.id

    private fun nextUpgrade(state: GameState): String? =
        Upgrades.all
            .filter { Logic.canBuyUpgrade(state, it.id) }
            .minByOrNull { Logic.upgradeCost(state, it.id) }
            ?.id

    private fun waitSeconds(state: GameState, sheet: com.revline.idle.game.Sheet): Double {
        var moneyGap = Double.POSITIVE_INFINITY
        var rpGap = Double.POSITIVE_INFINITY
        for (upgrade in Upgrades.all) {
            val level = state.upgrades[upgrade.id] ?: 0
            if (level >= upgrade.maxLevel) continue
            if (Logic.lockReason(state, upgrade.id) != null) continue
            val gap = Logic.upgradeCost(state, upgrade.id) - state.money
            if (gap > 0.0) moneyGap = minOf(moneyGap, gap)
        }
        for (node in ResearchTree.all) {
            if (Logic.researchStatus(state, node.id) != com.revline.idle.game.ResearchStatus.OPEN) continue
            val gap = node.cost - state.researchPoints
            if (gap > 0.0) rpGap = minOf(rpGap, gap)
        }
        val moneyWait = if (moneyGap.isFinite() && sheet.incomePerSec > 1e-6) moneyGap / sheet.incomePerSec else Double.POSITIVE_INFINITY
        val rpWait = if (rpGap.isFinite() && sheet.rpPerSec > 1e-9) rpGap / sheet.rpPerSec else Double.POSITIVE_INFINITY
        return minOf(moneyWait, rpWait)
    }

    private fun complete(state: GameState): Boolean {
        if (state.researched.size < ResearchTree.all.size) return false
        return Upgrades.all.all { (state.upgrades[it.id] ?: 0) >= it.maxLevel }
    }

    private fun boostAtPedal(state: GameState, throttle: Double): Double {
        val params = Logic.paramsFrom(state)
        val rpm = Logic.targetRpm(params.redlineRpm, throttle)
        return EngineSim.point(params, rpm, Logic.loadFor(throttle)).boostPsi
    }

    private fun checkSanity(state: GameState, sheet: com.revline.idle.game.Sheet, seconds: Double) {
        val where = "t=${seconds / 3600.0} h fuel=${state.fuel} throttle=${state.throttle}"
        assertTrue("$where income ${sheet.incomePerSec}", sheet.incomePerSec.isFinite() && sheet.incomePerSec >= 0.0)
        assertTrue("$where rp ${sheet.rpPerSec}", sheet.rpPerSec.isFinite() && sheet.rpPerSec > 0.0)
        assertTrue("$where money ${state.money}", state.money.isFinite() && state.money >= -0.01)
        assertTrue("$where hp ${sheet.stats.peakHorsepower}", sheet.stats.peakHorsepower.isFinite() && sheet.stats.peakHorsepower > 1.0)
        val live = EngineSim.point(sheet.params, Logic.targetRpm(sheet.params.redlineRpm, state.throttle), Logic.loadFor(state.throttle))
        assertTrue("$where soak ${live.heatSoakC}", live.heatSoakC.isFinite() && live.heatSoakC >= 0.0)
        assertTrue("$where mpg ${live.mpg}", live.mpg.isFinite() && live.mpg > 0.0)
        if ("turbo_node" in state.researched || "twin_node" in state.researched) {
            assertTrue(where, sheet.params.induction == Induction.TURBO || sheet.params.induction == Induction.TWIN_TURBO)
        }
    }

    private fun summary(state: GameState, sheet: com.revline.idle.game.Sheet, seconds: Double, boost: Double): String {
        return String.format(
            Locale.US,
            "full shop in %.2f h  $%.1f/s  hp %.0f  boost %.1f psi  prestiges %d  fuel %s  pedal %.2f%n",
            seconds / 3600.0,
            sheet.incomePerSec,
            sheet.stats.peakHorsepower,
            boost,
            state.prestiges,
            state.fuel,
            state.throttle,
        )
    }

    private fun stuck(state: GameState, sheet: com.revline.idle.game.Sheet, seconds: Double): String {
        val missingResearch = ResearchTree.all.filter { it.id !in state.researched }.map { it.id }
        val missingParts = Upgrades.all.mapNotNull { upgrade ->
            val level = state.upgrades[upgrade.id] ?: 0
            if (level >= upgrade.maxLevel) null else "${upgrade.id} $level/${upgrade.maxLevel} lock=${Logic.lockReason(state, upgrade.id)} cost=${Logic.upgradeCost(state, upgrade.id)}"
        }
        return "stuck at %.2f h money=%.0f rp=%.0f income=%.2f%nresearch %s%nparts %s".format(
            seconds / 3600.0,
            state.money,
            state.researchPoints,
            sheet.incomePerSec,
            missingResearch,
            missingParts.take(12),
        )
    }

    private data class Run(
        val finished: Boolean,
        val hours: Double,
        val finalPay: Double,
        val stockPay: Double,
        val fullBoost: Double,
        val report: String,
    )
}
