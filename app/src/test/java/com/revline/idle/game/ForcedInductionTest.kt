package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.FuelKind
import org.junit.Assert.assertTrue
import org.junit.Test

/** Forced induction must add power. Unsupported boost is limited by the ECU, not turned into a loss. */
class ForcedInductionTest {
    private val fi = setOf("supercharger", "turbo", "twin", "boost_controller", "intercooler", "overboost", "nitrous")
    private val research = ResearchTree.all.map { it.id }.toSet()
    private val base = Upgrades.all.filter { it.id !in fi && it.id !in Logic.NEW_ECONOMY_IDS }
        .associate { it.id to it.maxLevel }

    private fun hp(fuel: FuelKind, extra: Map<String, Int>): Double =
        Logic.sheet(GameState(prestiges = 5, researched = research, upgrades = base + extra, fuel = fuel, throttle = 1.0))
            .stats.peakHorsepower

    @Test fun nearMaxedV6GainsFromTurboAndSuperchargerOnPumpFuelWithoutIntercooler() {
        val na = hp(FuelKind.PUMP_87, emptyMap())
        assertTrue(hp(FuelKind.PUMP_87, mapOf("turbo" to 1)) > na)
        assertTrue(hp(FuelKind.PUMP_87, mapOf("supercharger" to 1)) > na * 0.995)
        // Bigger boost than the engine can support is limited, never a big loss.
        assertTrue(hp(FuelKind.PUMP_87, mapOf("turbo" to 2)) > na * 0.99)
    }

    @Test fun supportedBuildGainsClearly() {
        for (fuel in listOf(FuelKind.PUMP_87, FuelKind.PREMIUM_93)) {
            val na = hp(fuel, emptyMap())
            val ic = mapOf("intercooler" to 4)
            assertTrue(hp(fuel, ic + mapOf("turbo" to 1)) > na * 1.10)
            assertTrue(hp(fuel, ic + mapOf("supercharger" to 1)) > na * 1.10)
        }
    }
}
