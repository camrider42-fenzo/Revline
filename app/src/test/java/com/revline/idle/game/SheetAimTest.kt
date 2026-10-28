package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
import com.revline.idle.model.EngineStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SheetAimTest {
    @Test
    fun theLowestRatioIsTheFarTarget() {
        assertEquals(SheetMark.TORQUE, SheetAim.farthest(0.5, 0.2, 0.9))
        assertEquals(SheetMark.CRUISE, SheetAim.farthest(1.2, 1.1, 0.4))
        assertEquals(SheetMark.POWER, SheetAim.farthest(0.3, 0.3, 0.9))
    }

    @Test
    fun aChosenGoalPicksTheBiggestStep() {
        val state = GameState(prestiges = Block.INLINE_FOUR.ordinal, throttle = 0.55)
        for (goal in AimGoal.entries) {
            val aim = SheetAim.recommend(state, goal)
            val id = aim.upgradeId
            assertNotNull("${goal.short} had no part", id)
            assertEquals(null, Logic.lockReason(state, id!!))
            val picked = step(state, id, goal)
            assertTrue("${goal.short} via $id gained $picked", picked > 0.0)
            for (upgrade in Upgrades.all) {
                if (upgrade.id == id) continue
                if (Logic.lockReason(state, upgrade.id) != null) continue
                val other = step(state, upgrade.id, goal)
                assertTrue(
                    "${goal.short}: ${upgrade.id} gains $other and $id gains $picked",
                    other <= picked + 1e-6,
                )
            }
        }
    }

    private fun step(state: GameState, id: String, goal: AimGoal): Double {
        val before = EngineSim.evaluate(Logic.paramsFrom(state))
        val next = state.copy(upgrades = state.upgrades + (id to 1))
        val after = EngineSim.evaluate(Logic.paramsFrom(next))
        fun read(stats: EngineStats, from: GameState): Double = when (goal) {
            AimGoal.POWER -> stats.peakHorsepower
            AimGoal.TORQUE -> stats.peakTorqueLbFt
            AimGoal.CRUISE -> stats.cruiseMpg
            AimGoal.PAY -> Logic.sheet(from).incomePerSec
        }
        return read(after, next) - read(before, state)
    }

    @Test
    fun theRecommendationClosesMoreOfTheFarGapThanAnyOtherOpenPart() {
        val state = GameState()
        val aim = SheetAim.recommend(state)
        val block = Block.MOPED
        val now = EngineSim.evaluate(Logic.paramsFrom(state))
        val mark = SheetAim.farthest(
            now.peakHorsepower / block.hpGoal,
            now.peakTorqueLbFt / block.torqueGoal,
            now.cruiseMpg / block.mpgGoal,
        )
        assertEquals(mark, aim.mark)
        val id = aim.upgradeId
        assertNotNull(id)
        assertEquals(null, Logic.lockReason(state, id!!))
        val goal = when (mark) {
            SheetMark.POWER -> block.hpGoal
            SheetMark.TORQUE -> block.torqueGoal
            SheetMark.CRUISE -> block.mpgGoal
        }
        fun read(upgrades: Map<String, Int>): Double {
            val stats = EngineSim.evaluate(Logic.paramsFrom(state.copy(upgrades = upgrades)))
            return when (mark) {
                SheetMark.POWER -> stats.peakHorsepower
                SheetMark.TORQUE -> stats.peakTorqueLbFt
                SheetMark.CRUISE -> stats.cruiseMpg
            }
        }
        fun left(value: Double) = (goal - value).coerceAtLeast(0.0)
        val picked = read(mapOf(id to 1))
        assertTrue(left(read(emptyMap())) - left(picked) >= 0.05)
        for (upgrade in Upgrades.all) {
            if (upgrade.id == id) continue
            if ((state.upgrades[upgrade.id] ?: 0) >= upgrade.maxLevel) continue
            if (Logic.lockReason(state, upgrade.id) != null) continue
            val other = read(mapOf(upgrade.id to 1))
            assertTrue(
                "${upgrade.id} leaves ${left(other)} and $id leaves ${left(picked)}",
                left(other) >= left(picked) - 1e-6,
            )
        }
    }
}

class RemoveUpgradeTest {
    @Test
    fun pullingALevelRefundsWhatThatLevelCost() {
        val rich = GameState(money = 50_000.0)
        val once = Logic.buyUpgrade(rich, "filter")
        val twice = Logic.buyUpgrade(once, "filter")
        assertEquals(2, twice.upgrades["filter"])
        val pulled = Logic.removeUpgrade(twice, "filter")
        assertEquals(1, pulled.upgrades["filter"])
        assertEquals(once.money, pulled.money, 0.001)
        val bare = Logic.removeUpgrade(pulled, "filter")
        assertEquals(0, bare.upgrades["filter"] ?: 0)
        assertEquals(rich.money, bare.money, 0.001)
        assertEquals(bare, Logic.removeUpgrade(bare, "filter"))
    }

    @Test
    fun devModePullsThePartWithoutMintingMoney() {
        val state = GameState(devMode = true, money = 10.0, upgrades = mapOf("lap" to 2))
        val pulled = Logic.removeUpgrade(state, "lap")
        assertEquals(1, pulled.upgrades["lap"])
        assertEquals(10.0, pulled.money, 0.0)
    }

    @Test
    fun aRemovedPartLandsBackOnTheStatsItHad() {
        val state = GameState(money = 50_000.0)
        val bought = Logic.buyUpgrade(state, "filter")
        assertEquals(1, bought.upgrades["filter"])
        val back = Logic.removeUpgrade(bought, "filter")
        val before = EngineSim.evaluate(Logic.paramsFrom(state))
        val after = EngineSim.evaluate(Logic.paramsFrom(back))
        assertEquals(before.peakHorsepower, after.peakHorsepower, 1e-6)
        assertEquals(before.peakTorqueLbFt, after.peakTorqueLbFt, 1e-6)
        assertTrue(abs(before.cruiseMpg - after.cruiseMpg) < 1e-6)
    }
}
