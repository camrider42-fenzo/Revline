package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrade
import com.revline.idle.game.Upgrades
import com.revline.idle.model.FuelKind
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prints a payback price for each upgrade level from the income it adds. Not a balance gate. */
class UpgradePriceProbe {
    private val researched = ResearchTree.all.map { it.id }.toSet()
    private val paybackSec = 12.0 * 60.0

    @Test
    fun firstLevelPriceIsAboutTwelveMinutesOfTheIncomeItAdds() {
        val misses = mutableListOf<String>()
        for (upgrade in Upgrades.all) {
            val delta = benefit(upgrade, 0)
            val target = priceFor(delta)
            val actual = upgrade.baseCost
            val low = target * 0.5
            val high = target * 2.2
            if (actual !in low..high) {
                misses += "${upgrade.id} costs ${actual.toInt()} vs ${target.toInt()} for ${String.format(Locale.US, "%+.2f", delta)}/s"
            }
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    private fun benefit(upgrade: Upgrade, level: Int): Double {
        val streetBefore = pay(street(upgrade.id, level, boosted = true))
        val streetAfter = pay(street(upgrade.id, level + 1, boosted = true))
        val naBefore = pay(street(upgrade.id, level, boosted = false))
        val naAfter = pay(street(upgrade.id, level + 1, boosted = false))
        return maxOf(streetAfter - streetBefore, naAfter - naBefore)
    }

    /** A cooled, fueled street car. The part under test is the only thing that changes. */
    private fun street(id: String, level: Int, boosted: Boolean): Map<String, Int> {
        val map = mutableMapOf(
            "cold_air" to 2,
            "radiator" to 3,
            "duct" to 1,
            "oil_cooler" to 1,
            "fuel_pump" to 4,
            "injectors" to 3,
            "headers" to 2,
            "intercooler" to 2,
            "springs" to 2,
            "pistons" to 1,
        )
        if (boosted && id != "supercharger") map["turbo"] = 3
        if (id == "vvt" || id == "vvl") map["cam"] = 2
        if (id == "vvl") map["vvt"] = 1
        if (id == "itb") map["manifold"] = 2
        if (id == "hpfp") map["injectors"] = 3
        map.remove(id)
        if (level > 0) map[id] = level
        if (id == "twin") map["turbo"] = 3
        return map
    }

    private fun pay(upgrades: Map<String, Int>): Double {
        val state = GameState(researched = researched, upgrades = upgrades, fuel = FuelKind.PREMIUM_93)
        val throttle = Logic.bestThrottle(state)
        return Logic.sheet(state.copy(throttle = throttle)).incomePerSec
    }

    private fun priceFor(deltaPerSec: Double): Double {
        if (deltaPerSec <= 0.02) return 200.0
        val raw = paybackSec * deltaPerSec
        return roundPrice(raw.coerceIn(150.0, 80_000.0))
    }

    private fun fitGrowth(deltas: DoubleArray): Double {
        if (deltas.size < 2 || deltas[0] <= 0.02) return 1.35
        val ratios = (1 until deltas.size).mapNotNull { i ->
            if (deltas[i] <= 0.02 || deltas[i - 1] <= 0.02) null
            else (deltas[i] / deltas[i - 1]).coerceIn(0.85, 2.2)
        }
        if (ratios.isEmpty()) return 1.35
        val mean = ratios.average().coerceIn(1.05, 2.0)
        return String.format(Locale.US, "%.2f", mean).toDouble()
    }

    private fun roundPrice(value: Double): Double {
        val mag = Math.pow(10.0, Math.floor(Math.log10(value)))
        val lead = value / mag
        val nice = when {
            lead < 1.5 -> 1.0
            lead < 2.5 -> 2.0
            lead < 4.0 -> 3.0
            lead < 6.0 -> 5.0
            lead < 8.5 -> 7.0
            else -> 10.0
        }
        return nice * mag
    }
}
