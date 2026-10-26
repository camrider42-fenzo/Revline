package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.data.SaveRepository
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import com.revline.idle.model.EngineStats
import com.revline.idle.model.FuelKind
import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyTest {
    @Test
    fun incomeMatchesThePublishedFormula() {
        val stats = sample(100.0, 100.0, 28.0, 6500.0, 100.0)
        val expected = Income.K * 100.0.pow(1.72) * (1.0 + 100.0 / 950.0)
        assertEquals(expected, Income.perSecond(stats, 1.0), 1e-6)
    }

    @Test
    fun incomeRisesWithPowerEconomyAndReliability() {
        val base = Income.perSecond(sample(140.0, 140.0, 30.0, 6500.0, 90.0), 1.0)
        assertTrue(Income.perSecond(sample(200.0, 140.0, 30.0, 6500.0, 90.0), 1.0) > base)
        assertTrue(Income.perSecond(sample(140.0, 140.0, 36.0, 6500.0, 90.0), 1.0) > base)
        assertTrue(Income.perSecond(sample(140.0, 140.0, 30.0, 6500.0, 60.0), 1.0) < base)
        assertEquals(base * 1.12, Income.perSecond(sample(140.0, 140.0, 30.0, 6500.0, 90.0), 1.12), 1e-6)
    }

    @Test
    fun prestigePaysNothingUnderTheThreshold() {
        assertEquals(0, Prestige.knowledgeGain(Prestige.MIN_EARNINGS - 1.0))
        assertTrue(Prestige.knowledgeGain(Prestige.MIN_EARNINGS) >= 1)
        assertEquals(1.12, Prestige.multiplier(1), 1e-9)
    }

    @Test
    fun offlinePaysACappedFractionAndSkipsShortGaps() {
        val now = 10_000_000L
        val saved = GameState(savedAtEpochMs = now - 3_600_000L)
        val (paid, msg) = Logic.applyOffline(saved, now)
        assertTrue(paid.money > 0.0)
        assertTrue(msg != null)
        val eight = Logic.applyOffline(saved.copy(savedAtEpochMs = now - Offline.CAP_MS), now).first.money
        val ten = Logic.applyOffline(saved.copy(savedAtEpochMs = now - Offline.CAP_MS - 3_600_000L), now).first.money
        assertEquals(eight, ten, 0.01)
        val brief = Logic.applyOffline(saved.copy(savedAtEpochMs = now - 5_000L), now).first
        assertEquals(0.0, brief.money, 0.0)
    }

    @Test
    fun numbersUseShortSuffixes() {
        assertEquals("50.00", formatCompact(50.0))
        assertEquals("999", formatCompact(999.0))
        assertEquals("1.20K", formatCompact(1200.0))
        assertEquals("3.40M", formatCompact(3.4e6))
        assertEquals("$1.20K", formatDollars(1200.0))
    }

    @Test
    fun saveRoundTripKeepsTheBuild() {
        val state = GameState(
            money = 1234.5,
            runEarnings = 9000.0,
            researchPoints = 42.0,
            knowledge = 3,
            researched = setOf("premium", "tbi"),
            upgrades = mapOf("filter" to 2),
            perks = mapOf("flow" to 1),
            fuel = FuelKind.PREMIUM_93,
        )
        val dir = File.createTempFile("revline", "dir")
        dir.delete()
        dir.mkdirs()
        val repo = SaveRepository(dir)
        repo.save(state)
        assertEquals(state, repo.load())
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))
    }

    @Test
    fun catalogLinksResolveAndEveryPartChangesTheEngine() {
        assertEquals(Upgrades.all.size, Upgrades.all.map { it.id }.toSet().size)
        assertEquals(ResearchTree.all.size, ResearchTree.all.map { it.id }.toSet().size)
        val known = ResearchTree.all.map { it.id }.toSet()
        for (upgrade in Upgrades.all) {
            upgrade.requires?.let { assertTrue(upgrade.id, it in known) }
            upgrade.levelRequires.values.forEach { assertTrue(it in known) }
        }
        for (node in ResearchTree.all) node.requires.forEach { assertTrue(node.id, it in known) }
        val open = GameState(researched = known)
        val stock = Logic.paramsFrom(open)
        for (upgrade in Upgrades.all) {
            val next = Logic.paramsFrom(open.copy(upgrades = mapOf(upgrade.id to 1)))
            assertNotEquals(upgrade.id, stock, next)
        }
    }

    @Test
    fun scriptedShopReachesTheFirstRebuildInAboutAnHour() {
        val minutes = scriptedMinutes()
        assertTrue("first rebuild in $minutes min", minutes in 25.0..75.0)
    }

    private fun scriptedMinutes(): Double {
        val plan = listOf(
            "u:synthetic", "u:filter", "u:plugs", "u:cap", "u:catback", "u:jets", "u:radiator",
            "u:base_timing", "u:cold_air", "u:fuel_pump", "r:exhaust_fab", "r:premium",
            "r:heat_basics", "u:headers", "u:duct", "u:water_pump", "r:tbi", "u:tbi_unit",
            "r:piggyback", "u:mapped_timing", "u:afr_map", "r:wasted_spark", "u:coils",
            "r:oil_science", "u:oil_cooler", "r:perf_cam", "u:springs", "u:cam",
            "r:ic_node", "u:intercooler", "r:turbo_node", "u:turbo", "r:port", "u:injectors",
            "u:fuel_pump", "r:hyper", "r:forged", "u:studs", "u:pistons", "u:compression",
        )
        var state = GameState(savedAtEpochMs = 1L)
        var sheet = Logic.sheet(state)
        var t = 0.0
        val dt = 5.0
        var step = 0
        var stuckFor = 0.0
        while (t < 90.0 * 60.0) {
            if (!Logic.canPrestige(state)) {
                state = state.copy(
                    money = state.money + sheet.incomePerSec * dt,
                    runEarnings = state.runEarnings + sheet.incomePerSec * dt,
                    researchPoints = state.researchPoints + sheet.rpPerSec * dt,
                )
            }
            if (step % 7 == 0) {
                state = state.copy(researchPoints = state.researchPoints + Logic.dynoReward(sheet.stats))
            }
            val beforeResearch = state.researched
            if (step < plan.size) {
                val item = plan[step]
                val id = item.substring(2)
                val bought = if (item.startsWith("r:")) {
                    if (Logic.canBuyResearch(state, id)) Logic.buyResearch(state, id) else state
                } else if (Logic.canBuyUpgrade(state, id)) {
                    Logic.buyUpgrade(state, id)
                } else {
                    state
                }
                if (bought != state) {
                    state = bought
                    step++
                    stuckFor = 0.0
                    if (state.researched != beforeResearch) {
                        val best = FuelKind.entries
                            .filter { Logic.fuelUnlocked(state, it) }
                            .maxBy { kind -> Logic.sheet(state.copy(fuel = kind)).incomePerSec }
                        if (Logic.sheet(state.copy(fuel = best)).incomePerSec > sheet.incomePerSec * 1.01) {
                            state = state.copy(fuel = best)
                        }
                    }
                    sheet = Logic.sheet(state)
                } else {
                    stuckFor += dt
                    if (stuckFor > 20.0 * 60.0) return 999.0
                }
            }
            t += dt
            if (Logic.canPrestige(state) && step >= plan.size - 3) return t / 60.0
            if (Logic.canPrestige(state) && t > 20.0 * 60.0) return t / 60.0
        }
        return if (Logic.canPrestige(state)) t / 60.0 else 999.0
    }

    private fun sample(hp: Double, tq: Double, mpg: Double, redline: Double, rel: Double) = EngineStats(
        displacementCc = 1998.0,
        peakHorsepower = hp,
        peakHorsepowerRpm = 6000.0,
        peakTorqueLbFt = tq,
        peakTorqueRpm = 4000.0,
        cruiseMpg = mpg,
        redlineRpm = redline,
        reliability = rel,
        reliabilityNotes = emptyList(),
        meanPistonSpeedMps = 18.0,
        peakCoolantC = 90.0,
        peakOilC = 110.0,
        peakEgtC = 800.0,
        peakChargeC = 30.0,
        peakCoolantKw = 30.0,
        peakOilKw = 8.0,
        peakExhaustKw = 40.0,
        peakFuelKw = 100.0,
        peakBrakeEfficiency = 0.3,
        peakBoostPsi = 0.0,
        curve = emptyList(),
    )
}
