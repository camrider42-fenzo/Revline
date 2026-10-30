package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.data.SaveRepository
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import com.revline.idle.economy.ContractOutput
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
    fun thePedalPaysForOutputUsedAndForMpg() {
        val sheet = ContractOutput(peakHorsepower = 180.0, peakTorqueLbFt = 160.0, cruiseMpg = 30.0)
        val lugging = point(hp = 28.0, tq = 80.0, mpg = 40.0, rpm = 1400.0)
        val onThePeak = point(hp = 170.0, tq = 150.0, mpg = 24.0, rpm = 5200.0)
        val rich = point(hp = 170.0, tq = 150.0, mpg = 11.0, rpm = 6400.0)
        val closed = Income.perSecond(lugging, 95.0, 1.0, output = sheet)
        val open = Income.perSecond(onThePeak, 95.0, 1.0, output = sheet)
        val wasted = Income.perSecond(rich, 95.0, 1.0, output = sheet)
        assertTrue("using the output $open vs lugging $closed", open > closed * 2.2)
        assertTrue("current mpg $open vs rich $wasted", open > wasted * 1.5)
        val bigger = sheet.copy(peakHorsepower = 180.0 * 1.30, peakTorqueLbFt = 160.0 * 1.10)
        val biggerPoint = point(hp = 170.0 * 1.30, tq = 150.0 * 1.10, mpg = 24.0, rpm = 5200.0)
        val grown = Income.perSecond(biggerPoint, 95.0, 1.0, output = bigger)
        assertTrue("a larger maximum $grown vs $open", grown > open * 1.25)
    }

    @Test
    fun researchingTurboOpensTheTurboAndBuyingItMakesBoost() {
        val studied = Logic.buyResearch(
            GameState(researchPoints = 1e6, money = 1e9, researched = setOf("exhaust_fab")),
            "turbo_node",
        )
        assertEquals(com.revline.idle.model.Induction.NA, Logic.paramsFrom(studied).induction)
        assertTrue(Logic.canBuyUpgrade(studied, "turbo"))
        val fitted = Logic.buyUpgrade(studied, "turbo")
        assertEquals(1, fitted.upgrades["turbo"])
        val params = Logic.paramsFrom(fitted)
        assertEquals(com.revline.idle.model.Induction.TURBO, params.induction)
        val wideOpen = com.revline.idle.model.EngineSim.point(params, 5200.0, 1.0)
        assertTrue("wot boost ${wideOpen.boostPsi}", wideOpen.boostPsi > 3.0)
        val rpm = Logic.targetRpm(params.redlineRpm, 0.7)
        val held = com.revline.idle.model.EngineSim.point(params, rpm, Logic.loadFor(0.7))
        assertTrue("pedal boost ${held.boostPsi} at $rpm", held.boostPsi > 1.0)
    }

    @Test
    fun holdingThePedalOnThePayingRpmBeatsIdle() {
        val stock = GameState(savedAtEpochMs = 1L, throttle = 0.12, prestiges = com.revline.idle.model.Block.INLINE_FOUR.ordinal)
        assertEquals(0.0, Logic.targetRpm(6500.0, 0.0), 0.0)
        assertEquals(0.0, Logic.sheet(stock.copy(throttle = 0.0)).incomePerSec, 0.0)
        val idle = Logic.sheet(stock).incomePerSec
        val best = Logic.bestThrottle(stock)
        val peaked = Logic.sheet(stock.copy(throttle = best)).incomePerSec
        assertTrue("idle $idle best $peaked at $best", peaked > idle * 1.7)
        val floored = Logic.sheet(stock.copy(throttle = 1.0)).incomePerSec
        assertTrue("best $peaked at $best should beat wide open $floored", peaked > floored * 1.15)
        assertTrue("best pedal $best", best in 0.55..0.90)
    }

    @Test
    fun prestigePaysNothingUnderTheThreshold() {
        val block = com.revline.idle.model.Block.MOPED
        assertEquals(0, Prestige.knowledgeGain(block.hpGoal - 0.1, block.torqueGoal, block.mpgGoal, block))
        assertEquals(0, Prestige.knowledgeGain(block.hpGoal, block.torqueGoal - 0.1, block.mpgGoal, block))
        assertEquals(0, Prestige.knowledgeGain(block.hpGoal, block.torqueGoal, block.mpgGoal - 0.1, block))
        assertEquals(1, Prestige.knowledgeGain(block.hpGoal, block.torqueGoal, block.mpgGoal, block))
        assertEquals(5, Prestige.knowledgeGain(block.hpGoal * 1.5, block.torqueGoal * 1.5, block.mpgGoal, block))
        assertEquals(9, Prestige.knowledgeGain(block.hpGoal * 2, block.torqueGoal * 2, block.mpgGoal * 2, block))
        assertEquals(9, Prestige.knowledgeGain(block.hpGoal * 2, block.torqueGoal * 2, block.mpgGoal, block))
        assertEquals(16, Prestige.knowledgeGain(block.hpGoal * 6, block.torqueGoal * 6, block.mpgGoal * 2, block))
        assertEquals(1.12, Prestige.multiplier(1), 1e-9)
        val dyno = Logic.sheet(GameState())
        val learned = Logic.sheet(GameState(knowledge = 5))
        val habit = Logic.sheet(GameState(perks = mapOf("notebook" to 2)))
        val pull = Logic.dynoReward(dyno.rpPerSec, dyno.params.dynoFactor)
        assertEquals(pull * (learned.rpPerSec / dyno.rpPerSec), Logic.dynoReward(learned.rpPerSec, learned.params.dynoFactor), 1e-6)
        assertEquals(pull * (habit.rpPerSec / dyno.rpPerSec), Logic.dynoReward(habit.rpPerSec, habit.params.dynoFactor), 1e-6)
        assertTrue(learned.rpPerSec > dyno.rpPerSec * 1.5)
        assertTrue(habit.rpPerSec > dyno.rpPerSec * 1.4)
    }

    @Test
    fun offlinePaysACappedFractionAndSkipsShortGaps() {
        val now = 10_000_000L
        val saved = GameState(savedAtEpochMs = now - 3_600_000L)
        val (paid, msg) = Logic.applyOffline(saved, now)
        assertTrue(paid.money > 0.0)
        assertTrue(msg != null)
        val sheet = Logic.sheet(saved)
        assertEquals(sheet.rpPerSec * 3600.0 * Offline.RESEARCH_FACTOR, paid.researchPoints, 0.01)
        assertEquals(0.20, Offline.RESEARCH_FACTOR, 0.0)
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
    fun aNewEngineStartsWithTheFourCheapestTools() {
        val fresh = Logic.newGame()
        val costs = Upgrades.all
            .filter { Logic.lockReason(fresh, it.id) == null }
            .map { Logic.upgradeCost(fresh, it.id) }
            .sorted()
        assertEquals(costs.take(4).sum(), fresh.money, 0.001)
        assertTrue(fresh.money > costs.first())
        val ready = fresh.copy(
            money = 1.0e7,
            researched = setOf("exhaust_fab", "perf_cam", "wasted_spark", "tbi", "ic_node", "premium"),
            upgrades = com.revline.idle.model.BlockPaceTest.sixty,
            fuel = FuelKind.PREMIUM_93,
        )
        val rebuilt = Logic.prestige(ready)
        assertEquals(1, rebuilt.prestiges)
        assertEquals(Logic.startingMoney(1), rebuilt.money, 0.001)
        assertTrue(rebuilt.upgrades.isEmpty())
    }

    @Test
    fun theOpeningToolOnEveryEngineIsACoupleOfMinutes() {
        for (index in com.revline.idle.model.Block.entries.indices) {
            val state = GameState(prestiges = index, knowledge = index)
            val pay = Logic.sheet(state.copy(throttle = Logic.bestThrottle(state))).incomePerSec
            val cheapest = com.revline.idle.game.Upgrades.all
                .filter { Logic.lockReason(state, it.id) == null }
                .minOf { Logic.upgradeCost(state, it.id) }
            val minutes = cheapest / pay / 60.0
            val pace = com.revline.idle.model.Block.forPrestige(index).rebuildPace()
            assertTrue("${com.revline.idle.model.Block.forPrestige(index).title} first tool $minutes min", minutes < 3.5 * pace)
        }
    }

    private fun point(
        hp: Double,
        tq: Double,
        mpg: Double = 28.0,
        coolant: Double = 90.0,
        oil: Double = 110.0,
        egt: Double = 720.0,
        rpm: Double = 4000.0,
    ) = OperatingPoint(
        rpm = rpm,
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
        efficiency = mpg,
    )

}
