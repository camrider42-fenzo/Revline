package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.model.Block
import com.revline.idle.model.FuelKind
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostIncomeTest {
    @Test
    fun aFueledChargerPaysMoreThanTheSameEngine() {
        for (block in listOf(Block.MOPED, Block.INLINE_FOUR, Block.V8)) {
            val na = pay(block, emptyMap())
            val turbo = pay(block, mapOf("turbo" to 1, "fuel_pump" to 3, "intercooler" to 2))
            val blower = pay(block, mapOf("supercharger" to 2, "fuel_pump" to 3, "intercooler" to 2))
            assertTrue("${block.title} turbo $turbo vs $na", turbo > na * 1.08)
            assertTrue("${block.title} blower $blower vs $na", blower > na * 1.05)
        }
    }

    @Test
    fun theDynoSheetStillWarnsAndWideOpenIsNotThePaycheck() {
        val state = GameState(
            prestiges = Block.INLINE_FOUR.ordinal,
            fuel = FuelKind.PREMIUM_93,
            upgrades = mapOf("turbo" to 3, "fuel_pump" to 4, "intercooler" to 2),
        )
        val notes = Logic.sheet(state).stats.reliabilityNotes
        assertTrue(notes.toString(), notes.isNotEmpty())
        val best = Logic.bestThrottle(state)
        val held = payAt(state, best)
        val floored = payAt(state, 1.0)
        assertTrue("best pedal $best pay $held vs floor $floored", best < 0.90 && held > floored * 1.15)
    }

    private fun pay(block: Block, parts: Map<String, Int>): Double {
        val state = GameState(prestiges = block.ordinal, fuel = FuelKind.PREMIUM_93, upgrades = parts)
        return payAt(state, Logic.bestThrottle(state))
    }

    private fun payAt(state: GameState, throttle: Double): Double =
        Logic.sheet(state.copy(throttle = throttle)).incomePerSec
}
