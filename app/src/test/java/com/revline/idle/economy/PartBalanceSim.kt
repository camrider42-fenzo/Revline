package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrade
import com.revline.idle.game.UpgradeMap
import com.revline.idle.game.Upgrades
import com.revline.idle.model.Block
import com.revline.idle.model.FuelKind
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every part level, on the engine where that part is supposed to matter.
 * Minutes is the real price divided by the paycheck it adds at the best pedal.
 */
class PartBalanceSim {
    private val researched = ResearchTree.all.map { it.id }.toSet()

    @Test
    fun simulatePriceAgainstPaycheck() {
        val lines = mutableListOf<String>()
        lines += "part lvl block minutes cost delta pay hp dHp mpg dMpg rel dRel"
        for (upgrade in Upgrades.all) {
            if (upgrade.id in SKIP) continue
            val block = if (upgrade.id in OPENING) Block.MOPED else Block.INLINE_FOUR
            for (level in 0 until upgrade.maxLevel) {
                val before = support(upgrade, level)
                val after = before + (upgrade.id to level + 1)
                val row = measure(upgrade, level, block, before, after)
                lines += row.text
            }
        }
        // The same first levels on a V8, to see whether the price scale tracked the contract.
        for (upgrade in Upgrades.all) {
            if (upgrade.id in OPENING || upgrade.id in SKIP) continue
            val before = support(upgrade, 0)
            val after = before + (upgrade.id to 1)
            val row = measure(upgrade, 0, Block.V8, before, after)
            if (row.minutes.isFinite() && (row.minutes < 4.0 || row.minutes > 30.0)) {
                lines += "V8 " + row.text
            }
        }
        println(lines.joinToString("\n"))
        val bad = lines.filter { line ->
            val bits = line.split(Regex("\\s+"))
            if (bits.size < 5 || bits[0] == "part" || bits[0] == "V8") return@filter false
            val level = bits[1]
            val block = bits[2]
            val minutes = bits[3].toDoubleOrNull() ?: return@filter false
            level == "1" && block == "MOPED" && minutes !in (1.5 * Block.MOPED.rebuildPace())..(6.5 * Block.MOPED.rebuildPace())
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    private fun measure(upgrade: Upgrade, level: Int, block: Block, before: Map<String, Int>, after: Map<String, Int>): Row {
        val prior = state(block, before)
        val next = state(block, after)
        val cost = Logic.upgradeCost(prior, upgrade.id)
        val pay0 = paycheck(prior)
        val pay1 = paycheck(next)
        val delta = pay1.pay - pay0.pay
        val minutes = if (delta > 0.002) cost / delta / 60.0 else Double.POSITIVE_INFINITY
        val text = String.format(
            Locale.US,
            "%-16s %d %-12s %7s %8.0f %+7.3f %7.2f %+6.1f %5.1f %+5.2f %3.0f %+5.1f",
            upgrade.id,
            level + 1,
            block.name,
            if (minutes.isFinite()) String.format(Locale.US, "%6.1f", minutes) else "   none",
            cost,
            delta,
            pay0.pay,
            pay1.hp - pay0.hp,
            pay0.mpg,
            pay1.mpg - pay0.mpg,
            pay0.rel,
            pay1.rel - pay0.rel,
        )
        return Row(text, minutes)
    }

    private fun paycheck(state: GameState): Pay {
        val throttle = Logic.bestThrottle(state)
        val sheet = Logic.sheet(state.copy(throttle = throttle))
        return Pay(sheet.incomePerSec, sheet.stats.peakHorsepower, sheet.stats.cruiseMpg, sheet.stats.reliability)
    }

    private fun state(block: Block, parts: Map<String, Int>): GameState = GameState(
        prestiges = block.ordinal,
        knowledge = block.ordinal,
        researched = researched,
        upgrades = parts,
        fuel = if (block == Block.MOPED) FuelKind.PUMP_87 else FuelKind.PREMIUM_93,
    )

    /** Parts already on the engine so the one under test is the only change. */
    private fun support(upgrade: Upgrade, level: Int): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        if (upgrade.id !in OPENING) {
            map["radiator"] = 2
            map["cold_air"] = 2
            map["fuel_pump"] = 3
            map["headers"] = 1
        }
        val effect = UpgradeMap.effectOf(upgrade.id)
        val boosted = effect == com.revline.idle.game.Effect.BOOST ||
            effect == com.revline.idle.game.Effect.COOLING ||
            effect == com.revline.idle.game.Effect.DURABILITY ||
            upgrade.id in setOf("injectors", "hpfp", "fuel_pump", "compression", "bore")
        if (boosted && upgrade.id !in OPENING && upgrade.id != "supercharger") {
            map["turbo"] = 2
            map["intercooler"] = 2
            map["injectors"] = 2
            map["fuel_pump"] = 4
        }
        if (upgrade.id == "vvt" || upgrade.id == "vvl") map["cam"] = 2
        if (upgrade.id == "vvl") map["vvt"] = 1
        if (upgrade.id == "itb") map["manifold"] = 2
        if (upgrade.id == "hpfp") map["injectors"] = 3
        if (upgrade.id == "twin") map["turbo"] = 2
        map.remove(upgrade.id)
        if (level > 0) map[upgrade.id] = level
        return map
    }

    private data class Pay(val pay: Double, val hp: Double, val mpg: Double, val rel: Double)
    private data class Row(val text: String, val minutes: Double)

    companion object {
        private val SKIP = setOf("nitrous", "overboost", "limiter", "dyno_fuel")
        private val OPENING = setOf("lap", "gap", "boot", "tip", "needle", "oil_service", "fins", "wrench")
    }
}
