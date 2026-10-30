package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.EngineSim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BottleneckTest {
    @Test
    fun aShortPowerSheetPointsAtPower() {
        val state = GameState()
        val sheet = Logic.sheet(state)
        val live = EngineSim.point(sheet.params, 2500.0, 0.4, 0.0)
        val tip = Bottleneck.suggest(state, sheet.stats, sheet.profit.mpg, live, soakPer10s = 0.0)
        assertEquals(Effect.POWER, tip.effect)
        assertTrue(tip.detail.contains("hp"))
    }

    @Test
    fun aClimbingHeadPointsAtCooling() {
        val state = GameState(soakC = 24.0, throttle = 0.8)
        val sheet = Logic.sheet(state)
        val live = EngineSim.point(sheet.params, sheet.params.redlineRpm * 0.8, 0.85, 24.0)
        val tip = Bottleneck.suggest(state, sheet.stats, sheet.profit.mpg, live, soakPer10s = 2.4)
        assertEquals(Effect.COOLING, tip.effect)
        assertTrue(tip.detail.contains("10 seconds"))
    }

    @Test
    fun dynoNotesNameTheirBay() {
        assertEquals(Effect.COOLING, Bottleneck.areaFor(listOf("Coolant 118°C"))!!.first)
        assertEquals(Effect.BOOST, Bottleneck.areaFor(listOf("Lean under boost"))!!.first)
        assertEquals(Effect.DURABILITY, Bottleneck.areaFor(listOf("Piston speed"))!!.first)
        assertEquals(Effect.DURABILITY, Bottleneck.areaFor(listOf("Boost vs. internals"))!!.first)
        assertEquals(Effect.POWER, Bottleneck.areaFor(listOf("Knock"))!!.first)
        assertEquals(Effect.COOLING, Bottleneck.areaFor(listOf("Knock", "EGT 940°C"))!!.first)
    }
}
