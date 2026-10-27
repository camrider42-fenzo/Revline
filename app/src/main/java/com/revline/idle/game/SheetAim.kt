package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.economy.formatDollars
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
import java.util.Locale
import kotlin.math.abs

/** Which rebuild number the engine is farthest from. */
enum class SheetMark { POWER, TORQUE, CRUISE }

/** The next level that lands nearest the rebuild number the engine is missing by the most. */
data class Aim(
    val mark: SheetMark,
    val upgradeId: String?,
    val headline: String,
    val detail: String,
)

object SheetAim {
    /**
     * Lowest ratio is farthest from its goal. A tie goes to power, then torque.
     * A ratio of 1 has met the sheet.
     */
    fun farthest(hpRatio: Double, torqueRatio: Double, mpgRatio: Double): SheetMark {
        val marks = listOf(
            SheetMark.POWER to hpRatio,
            SheetMark.TORQUE to torqueRatio,
            SheetMark.CRUISE to mpgRatio,
        )
        return marks.minWith(compareBy<Pair<SheetMark, Double>> { it.second }.thenBy { it.first.ordinal }).first
    }

    fun recommend(state: GameState): Aim {
        val block = Block.forPrestige(state.prestiges)
        val now = EngineSim.evaluate(Logic.paramsFrom(state))
        val hpRatio = now.peakHorsepower / block.hpGoal
        val tqRatio = now.peakTorqueLbFt / block.torqueGoal
        val mpgRatio = now.cruiseMpg / block.mpgGoal
        val mark = farthest(hpRatio, tqRatio, mpgRatio)
        val goal = goal(block, mark)
        val before = value(now.peakHorsepower, now.peakTorqueLbFt, now.cruiseMpg, mark)
        val gap = (goal - before).coerceAtLeast(0.0)
        if (gap < 0.05) {
            return Aim(
                mark,
                null,
                "Clear",
                "Every rebuild number is met. ${label(mark)} is the closest of the three, at ${fmt(mark, before)}.",
            )
        }
        var best: Pick? = null
        for (upgrade in Upgrades.all) {
            val level = state.upgrades[upgrade.id] ?: 0
            if (level >= upgrade.maxLevel) continue
            if (Logic.lockReason(state, upgrade.id) != null) continue
            val next = EngineSim.evaluate(
                Logic.paramsFrom(state.copy(upgrades = state.upgrades + (upgrade.id to level + 1))),
            )
            val after = value(next.peakHorsepower, next.peakTorqueLbFt, next.cruiseMpg, mark)
            val left = (goal - after).coerceAtLeast(0.0)
            if (gap - left < 0.05) continue
            val pick = Pick(
                id = upgrade.id,
                left = left,
                distance = abs(after - goal),
                cost = Logic.upgradeCost(state, upgrade.id),
                after = after,
            )
            if (best == null || pick.beats(best)) best = pick
        }
        val chosen = best
        if (chosen == null) {
            return Aim(
                mark,
                null,
                label(mark),
                "${label(mark)} is farthest from the sheet, ${fmt(mark, before)} against ${fmt(mark, goal)}. Nothing unlocked moves it closer.",
            )
        }
        val name = Upgrades.byId(chosen.id).name
        return Aim(
            mark,
            chosen.id,
            name,
            "${label(mark)} is farthest from the sheet, ${fmt(mark, before)} against ${fmt(mark, goal)}. " +
                "Next level lands at ${fmt(mark, chosen.after)}. ${formatDollars(chosen.cost)}.",
        )
    }

    private data class Pick(
        val id: String,
        val left: Double,
        val distance: Double,
        val cost: Double,
        val after: Double,
    ) {
        fun beats(other: Pick): Boolean {
            if (left < other.left - 1e-6) return true
            if (left > other.left + 1e-6) return false
            if (distance < other.distance - 1e-6) return true
            if (distance > other.distance + 1e-6) return false
            if (cost < other.cost - 1e-6) return true
            if (cost > other.cost + 1e-6) return false
            return id < other.id
        }
    }

    private fun goal(block: Block, mark: SheetMark): Double = when (mark) {
        SheetMark.POWER -> block.hpGoal
        SheetMark.TORQUE -> block.torqueGoal
        SheetMark.CRUISE -> block.mpgGoal
    }

    private fun value(hp: Double, torque: Double, mpg: Double, mark: SheetMark): Double = when (mark) {
        SheetMark.POWER -> hp
        SheetMark.TORQUE -> torque
        SheetMark.CRUISE -> mpg
    }

    private fun label(mark: SheetMark): String = when (mark) {
        SheetMark.POWER -> "Peak power"
        SheetMark.TORQUE -> "Peak torque"
        SheetMark.CRUISE -> "Cruise"
    }

    private fun fmt(mark: SheetMark, value: Double): String = when (mark) {
        SheetMark.CRUISE -> String.format(Locale.US, "%.1f mpg", value)
        SheetMark.POWER -> String.format(Locale.US, "%.0f hp", value)
        SheetMark.TORQUE -> String.format(Locale.US, "%.0f lb-ft", value)
    }
}
