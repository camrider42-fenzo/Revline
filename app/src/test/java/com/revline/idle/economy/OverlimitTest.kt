package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchStatus
import com.revline.idle.model.Block
import com.revline.idle.model.BlockPaceTest
import com.revline.idle.model.FuelKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The V8 branch trades the bottom end for dyno numbers. */
class OverlimitTest {
    private val studied = setOf(
        "exhaust_fab", "billet_node", "forged", "hyper", "turbo_node",
        "overlimit", "nitrous_node", "lean_node", "limiter_node", "overboost_node",
    )

    @Test
    fun theBranchIsClosedUntilAV8IsOnTheStand() {
        val ready = GameState(
            researchPoints = 1e9,
            researched = setOf("billet_node", "turbo_node", "exhaust_fab"),
            prestiges = Block.V6.ordinal,
        )
        assertEquals(ResearchStatus.LOCKED, Logic.researchStatus(ready, "overlimit"))
        assertEquals(ResearchStatus.LOCKED, Logic.researchStatus(ready, "nitrous_node"))
        val v8 = ready.copy(prestiges = Block.V8.ordinal)
        assertEquals(ResearchStatus.OPEN, Logic.researchStatus(v8, "overlimit"))
        val owned = Logic.buyResearch(v8, "overlimit")
        assertEquals(ResearchStatus.OPEN, Logic.researchStatus(owned, "nitrous_node"))
        assertEquals(ResearchStatus.OPEN, Logic.researchStatus(owned, "overboost_node"))
    }

    @Test
    fun theKitMakesALotMorePowerAndALotMoreDynoAndItRunsHot() {
        val plain = sheet(emptyMap())
        val kit = sheet(mapOf("nitrous" to 3, "overboost" to 3, "limiter" to 3, "dyno_fuel" to 3))
        val hpGain = kit.stats.peakHorsepower / plain.stats.peakHorsepower
        val dynoGain = Logic.dynoReward(kit.rpPerSec, kit.params.dynoFactor) /
            Logic.dynoReward(plain.rpPerSec, plain.params.dynoFactor)
        assertTrue(
            "hp ${plain.stats.peakHorsepower.toInt()} -> ${kit.stats.peakHorsepower.toInt()} (${"%.2f".format(hpGain)})",
            hpGain >= 1.45,
        )
        assertTrue(
            "dyno ${Logic.dynoReward(plain.rpPerSec, plain.params.dynoFactor).toInt()} -> ${Logic.dynoReward(kit.rpPerSec, kit.params.dynoFactor).toInt()} (${"%.2f".format(dynoGain)})",
            dynoGain >= 2.5,
        )
        assertTrue("reliability ${kit.stats.reliability}", kit.stats.reliability < 55.0)
        assertTrue(kit.stats.reliabilityNotes.any { it.contains("Past the limit") })
        assertTrue(
            "coolant ${kit.stats.peakCoolantC.toInt()} vs ${plain.stats.peakCoolantC.toInt()}, egt ${kit.stats.peakEgtC.toInt()} vs ${plain.stats.peakEgtC.toInt()}",
            kit.stats.peakCoolantC > plain.stats.peakCoolantC + 8.0 || kit.stats.peakEgtC > plain.stats.peakEgtC + 40.0,
        )
        assertTrue(
            "cruise mpg ${kit.stats.cruiseMpg} vs goal ${Block.V8.mpgGoal}",
            kit.stats.cruiseMpg >= Block.V8.mpgGoal,
        )
    }

    @Test
    fun failuresFollowHeatAndTheBillClimbs() {
        val calm = base(emptyMap())
        val calmSheet = Logic.sheet(calm)
        val light = Logic.failurePerSec(calm, calmSheet.stats) * 3600.0
        assertTrue("light pedal failures/hour $light", light < 0.05)
        val abused = base(mapOf("nitrous" to 3, "overboost" to 3, "limiter" to 3, "dyno_fuel" to 3))
            .copy(throttle = 1.0, money = 1.0e9)
        val hot = Logic.sheet(abused)
        val perHour = Logic.failurePerSec(abused, hot.stats) * 3600.0
        assertTrue("failures/hour $perHour", perHour >= 4.0)
        assertTrue("failures/hour $perHour", perHour < 40.0)
        val pulled = base(emptyMap()).copy(throttle = 1.0, soakC = 0.0)
        val pulledSheet = Logic.sheet(pulled)
        val pulledCool = Logic.failurePerSec(pulled, pulledSheet.stats)
        val pulledHot = Logic.failurePerSec(pulled.copy(soakC = 36.0), pulledSheet.stats)
        val pulledEased = Logic.failurePerSec(pulled.copy(throttle = 0.85), pulledSheet.stats)
        assertTrue("soaked pull $pulledHot vs cool pull $pulledCool", pulledHot > pulledCool * 1.4)
        assertTrue(
            "soak delta ${pulledHot - pulledCool} vs pedal delta ${pulledCool - pulledEased}",
            pulledHot - pulledCool > pulledCool - pulledEased,
        )

        val ideal = Logic.idealIncomePerSec(abused, hot.stats)
        val firstBill = ideal * Logic.REPAIR_WINDOW_SEC * Logic.REPAIR_FIRST_SHARE
        val broken = Logic.breakDown(abused, 1_000_000L)
        assertEquals(abused.money - firstBill, broken.money, 0.05)
        assertEquals(1_040_000L, broken.downUntilEpochMs)
        assertEquals(1, broken.breakdowns)
        assertTrue(broken.lastFailure.contains("Repair"))
        val again = Logic.breakDown(broken.copy(money = abused.money), 2_000_000L)
        val secondBill = abused.money - again.money
        assertTrue("second bill $secondBill vs first $firstBill", secondBill > firstBill * 1.4)
        assertEquals(firstBill * Logic.REPAIR_GROWTH, secondBill, 0.05)

        val rebuilt = Logic.prestige(abused)
        assertTrue(Logic.canPrestige(abused))
        assertTrue(rebuilt.prestiges > abused.prestiges)
        assertEquals(0L, rebuilt.downUntilEpochMs)
        assertEquals(0, rebuilt.breakdowns)
        assertEquals("", rebuilt.lastFailure)
    }

    private fun base(extra: Map<String, Int>): GameState {
        val parts = BlockPaceTest.sixty + mapOf("turbo" to 3, "intercooler" to 2, "fuel_pump" to 4) + extra
        return GameState(
            prestiges = Block.V8.ordinal,
            knowledge = Block.V8.ordinal,
            researched = studied,
            upgrades = parts,
            fuel = FuelKind.PREMIUM_93,
            money = 1.0,
        )
    }

    private fun sheet(extra: Map<String, Int>): com.revline.idle.game.Sheet {
        val state = base(extra)
        return Logic.sheet(state.copy(throttle = Logic.bestThrottle(state)))
    }
}
