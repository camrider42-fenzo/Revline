package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.data.SaveRepository
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import com.revline.idle.model.FuelKind
import com.revline.idle.model.OperatingPoint
import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyTest {
    @Test
    fun incomeMatchesThePublishedFormula() {
        val point = point(100.0, 100.0, mpg = 28.0)
        val expected = Income.K * 100.0.pow(Income.HP_EXP) * (1.0 + 100.0 / 950.0)
        assertEquals(expected, Income.perSecond(point, 100.0, 1.0), 1e-6)
    }

    @Test
    fun incomeRisesWithPowerEconomyAndReliability() {
        val base = Income.perSecond(point(140.0, 140.0, mpg = 28.0), 90.0, 1.0)
        assertTrue(Income.perSecond(point(200.0, 140.0, mpg = 28.0), 90.0, 1.0) > base)
        assertTrue(Income.perSecond(point(140.0, 140.0, mpg = 36.0), 90.0, 1.0) > base)
        assertTrue(Income.perSecond(point(140.0, 140.0, mpg = 28.0), 60.0, 1.0) < base)
        assertTrue(Income.perSecond(point(140.0, 140.0, mpg = 28.0, coolant = 122.0), 90.0, 1.0) < base)
        assertTrue(
            Income.perSecond(point(140.0, 140.0, mpg = 28.0), 90.0, 1.0, dollarsPerGallon = 15.0) < base,
        )
        assertEquals(base * 1.12, Income.perSecond(point(140.0, 140.0, mpg = 28.0), 90.0, 1.12), 1e-6)
    }

    @Test
    fun holdingThePedalOnThePayingRpmBeatsIdle() {
        val stock = GameState(savedAtEpochMs = 1L, throttle = 0.0)
        val idle = Logic.sheet(stock).incomePerSec
        val best = Logic.bestThrottle(stock)
        val peaked = Logic.sheet(stock.copy(throttle = best)).incomePerSec
        assertTrue("idle $idle best $peaked at $best", peaked > idle * 2.5)
        assertTrue(best > 0.15)
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
        state = state.copy(throttle = Logic.bestThrottle(state))
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
                    state = bought.copy(throttle = Logic.bestThrottle(bought))
                    step++
                    stuckFor = 0.0
                    if (state.researched != beforeResearch) {
                        val best = FuelKind.entries
                            .filter { Logic.fuelUnlocked(state, it) }
                            .maxBy { kind ->
                                val trial = state.copy(fuel = kind)
                                Logic.sheet(trial.copy(throttle = Logic.bestThrottle(trial))).incomePerSec
                            }
                        val trial = state.copy(fuel = best)
                        val tuned = trial.copy(throttle = Logic.bestThrottle(trial))
                        if (Logic.sheet(tuned).incomePerSec > Logic.sheet(state).incomePerSec * 1.01) {
                            state = tuned
                        }
                    }
                    sheet = Logic.sheet(state)
                } else {
                    stuckFor += dt
                    if (stuckFor > 20.0 * 60.0) {
                    val item = plan[step]
                    val id = item.substring(2)
                    val why = if (item.startsWith("r:")) {
                        "${Logic.researchStatus(state, id)} cost=${ResearchTree.byId(id).cost}"
                    } else {
                        "lock=${Logic.lockReason(state, id)} cost=${Logic.upgradeCost(state, id)} level=${state.upgrades[id] ?: 0}"
                    }
                    throw AssertionError(
                        "stuck on $item at ${t / 60.0} min money=${state.money} rp=${state.researchPoints} income=${sheet.incomePerSec} $why",
                    )
                }
                }
            }
            t += dt
            if (Logic.canPrestige(state) && step >= plan.size - 3) return t / 60.0
            if (Logic.canPrestige(state) && t > 20.0 * 60.0) return t / 60.0
        }
        return if (Logic.canPrestige(state)) t / 60.0 else 999.0
    }

    private fun point(
        hp: Double,
        tq: Double,
        mpg: Double = 28.0,
        coolant: Double = 90.0,
        oil: Double = 110.0,
        egt: Double = 720.0,
    ) = OperatingPoint(
        rpm = 4000.0,
        horsepower = hp,
        torqueLbFt = tq,
        boostPsi = 0.0,
        volumetricEfficiency = 0.9,
        afr = 12.8,
        timingDeg = 20.0,
        mbtDeg = 20.0,
        chargeTempC = 35.0,
        compressorOutC = 25.0,
        coolantTempC = coolant,
        oilTempC = oil,
        egtC = egt,
        coolantKw = 20.0,
        oilToAirKw = 2.0,
        oilGeneratedKw = 4.0,
        exhaustKw = 40.0,
        bayKw = 1.0,
        fuelPumpedKw = 80.0,
        fuelReleasedKw = 80.0,
        brakeKw = hp * 0.7457,
        frictionKw = 5.0,
        wallKw = 15.0,
        brakeEfficiency = 0.30,
        knockMargin = 5.0,
        octane = 87.0,
        octaneDemand = 82.0,
        unresolvedKnock = 0.0,
        fuelLimited = false,
        mpg = mpg,
    )

}
