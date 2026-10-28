package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.Tuners
import com.revline.idle.model.Block
import com.revline.idle.model.BlockPaceTest
import com.revline.idle.model.FuelKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every engine, stock and bolted, has a stop. The floor is not it. */
class PedalShapeTest {
    @Test
    fun wideOpenIsNotTheContractOnAnyEngine() {
        val misses = mutableListOf<String>()
        for (built in listOf(false, true)) {
            for (index in Block.entries.indices) {
                val block = Block.forPrestige(index)
                var state = GameState(
                    prestiges = index,
                    knowledge = index,
                    fuel = if (built) FuelKind.PREMIUM_93 else FuelKind.PUMP_87,
                )
                if (built) {
                    state = state.copy(
                        researched = setOf("exhaust_fab", "perf_cam", "wasted_spark", "tbi", "ic_node", "premium"),
                        upgrades = BlockPaceTest.sixty,
                    )
                }
                val best = Logic.bestThrottle(state)
                val bestPay = pay(state, best)
                val wide = pay(state, 1.0)
                val light = pay(state, 0.20)
                val label = "${block.title} ${if (built) "built" else "stock"} best $best"
                if (best !in 0.35..0.80) misses += "$label outside the stop"
                if (wide > bestPay * 0.75) misses += "$label wide open pays ${wide / bestPay}"
                if (bestPay < light * 1.40) misses += "$label light pedal pays too close ($light vs $bestPay)"
            }
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun aClosedPedalStallsAndThereIsNoIdleScrew() {
        assertTrue(Tuners.all.none { it.id == "idle" })
        assertEquals(0.0, Logic.targetRpm(7000.0, 0.0, 800.0), 0.0)
        val stalled = Logic.sheet(GameState(throttle = 0.0))
        assertEquals(0.0, stalled.incomePerSec, 0.0)
        assertTrue(Logic.targetRpm(9000.0, 0.2, 1700.0) > 1700.0)
    }

    private fun pay(state: GameState, throttle: Double): Double =
        Logic.sheet(state.copy(throttle = throttle)).incomePerSec
}
