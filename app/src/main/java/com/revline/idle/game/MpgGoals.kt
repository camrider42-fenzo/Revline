package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.Block
import com.revline.idle.model.FuelKind
import kotlin.math.floor

/**
 * One rule for every engine's profit-pedal mpg goal.
 *
 * The reference build is a mid shop, not a maxed economy run: half of each
 * power and boost part (never the top level) and a quarter of each economy
 * part, on premium. Parts whose study is still closed on that ladder step are
 * left off. Nitrous, overboost, the rev limiter, and a second charger are not in it.
 *
 * Difficulty of a sheet number is how much of that build the goal asks for:
 * `goal / what the reference build already shows`. Horsepower and torque set
 * the band. The mpg goal is the same fraction of the reference mpg, so it is
 * exactly as far along that build as the other two. It is then capped at the
 * mpg the build actually shows, and the single rotor is also capped at 57,
 * the reading from 1.93.7 with the power parts kept on. The published goal is
 * that number rounded down to a tenth, which keeps the reference build on the
 * clearing side of it.
 */
object MpgGoals {
    /** Share of each power or boost part's ladder fitted on the reference build. */
    const val POWER_SHARE = 0.5

    /** Share of each economy part. Below the top, so this is not the peak mpg. */
    const val ECONOMY_SHARE = 0.25

    /** Single-rotor ceiling. The 1.93.7 power-kept reading was 57.5. */
    const val SINGLE_ROTOR_CEILING = 57.0

    private val leftOff = setOf(
        "nitrous", "overboost", "limiter", "dyno_fuel",
        "rotor3", "rotor4", "port_peri", "port_bridge", "twin", "supercharger",
    )

    private val economyIds = (Logic.ECONOMY_IDS + Logic.NEW_ECONOMY_IDS).toSet()

    /** The save the reference build is measured on: this engine, premium, studies owned. */
    fun state(block: Block): GameState = GameState(
        prestiges = block.ladderStep,
        blockId = block.name,
        knowledge = block.ladderStep,
        researched = ResearchTree.all.map { it.id }.toSet(),
        upgrades = reference(block),
        fuel = FuelKind.PREMIUM_93,
    )

    fun reference(block: Block): Map<String, Int> =
        Upgrades.all.mapNotNull { upgrade ->
            if (!block.family.allows(upgrade.id) || upgrade.id in leftOff) return@mapNotNull null
            val effect = UpgradeMap.effectOf(upgrade.id)
            val share = when {
                upgrade.id in economyIds || effect == Effect.ECONOMY -> ECONOMY_SHARE
                effect == Effect.POWER || effect == Effect.BOOST -> POWER_SHARE
                else -> return@mapNotNull null
            }
            val study = upgrade.requires
            if (study != null && opensAt(study) > block.ladderStep) return@mapNotNull null
            upgrade.id to level(upgrade.maxLevel, share)
        }.toMap()

    fun forEngine(block: Block): Double = published.getValue(block)

    /**
     * `goal / reference`. One means the reference build just meets the goal.
     * Under one, the build has room. Over one, the goal sits past the build.
     */
    fun difficulty(goal: Double, reached: Double): Double =
        if (reached <= 1e-9) Double.POSITIVE_INFINITY else goal / reached

    private val published: Map<Block, Double> by lazy {
        Block.entries.associateWith { derive(it) }
    }

    private fun derive(block: Block): Double {
        val reached = reading(block, reference(block))
        val hp = difficulty(block.hpGoal, reached.hp)
        val tq = difficulty(block.torqueGoal, reached.torque)
        val fraction = (hp + tq) / 2.0
        var ceiling = reached.mpg
        if (block == Block.ROTARY_1R) ceiling = minOf(ceiling, SINGLE_ROTOR_CEILING)
        val matched = minOf(fraction * reached.mpg, ceiling)
        return floor(matched * 10.0 + 1e-9) / 10.0
    }

    private data class Reading(val hp: Double, val torque: Double, val mpg: Double)

    private fun reading(block: Block, parts: Map<String, Int>): Reading {
        val sheet = Logic.sheet(state(block).copy(upgrades = parts))
        return Reading(sheet.stats.peakHorsepower, sheet.stats.peakTorqueLbFt, sheet.profit.mpg)
    }

    private fun level(max: Int, share: Double): Int = when {
        max <= 1 -> max
        else -> (max * share).toInt().coerceIn(1, max - 1)
    }

    private fun opensAt(id: String, seen: MutableSet<String> = mutableSetOf()): Int {
        if (!seen.add(id)) return 0
        val node = ResearchTree.byId(id)
        return maxOf(node.minPrestige, node.requires.maxOfOrNull { opensAt(it, seen) } ?: 0)
    }
}
