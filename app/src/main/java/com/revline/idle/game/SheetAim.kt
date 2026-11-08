package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.economy.Prestige
import com.revline.idle.economy.formatDollars
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
import com.revline.idle.model.EngineStats
import java.util.Locale
import kotlin.math.abs

/** Which rebuild number the engine is farthest from. */
enum class SheetMark { POWER, TORQUE, CRUISE }

/** What the next-part line is trying to raise. */
enum class AimGoal {
    POWER,
    TORQUE,
    CRUISE,
    PAY,
    ;

    val short: String
        get() = when (this) {
            POWER -> "HP"
            TORQUE -> "TQ"
            CRUISE -> "MPG@\$"
            PAY -> "$/s"
        }

    fun next(): AimGoal = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(raw: String): AimGoal = entries.firstOrNull { it.name == raw } ?: POWER
    }
}

/**
 * How the next-part line spends toward the stat you picked.
 * Best is the old line: the biggest step, cost only breaks a tie, and the price is shown.
 */
enum class AimSpend {
    CHEAPEST,
    AFFORDABLE,
    ALMOST,
    BEST,
    ;

    val label: String
        get() = when (this) {
            CHEAPEST -> "CHEAPEST"
            AFFORDABLE -> "AFFORDABLE"
            ALMOST -> "ALMOST AFFORDABLE"
            BEST -> "BEST"
        }

    fun next(): AimSpend = entries[(ordinal + 1) % entries.size]

    companion object {
        /** A save from before the switch has no mode. It keeps the biggest step. */
        fun from(raw: String): AimSpend = entries.firstOrNull { it.name == raw } ?: BEST
    }
}

/** One unlocked next level, priced, and what the stat reads after it. */
internal data class AimOffer(
    val id: String,
    val cost: Double,
    val gain: Double,
    val after: Double,
)

internal data class AimChoice(
    val offer: AimOffer?,
    val reaches: Boolean,
    val short: Boolean,
)

/** Ranking for the next-part line. The sheet number is the target when the stat has one. */
internal object AimSpends {
    /** Cash covers at least this much of the price, and not the whole price. */
    const val BAND = 0.8

    fun covers(money: Double, cost: Double): Boolean = money + 1e-9 >= cost

    /**
     * The price is above the cash, and the cash still covers 80% of it.
     * Exactly 80% is in. A hair under is out. A price the cash already covers is out.
     */
    fun almost(money: Double, cost: Double): Boolean {
        if (cost <= 1e-9) return false
        if (covers(money, cost)) return false
        return money + 1e-9 >= BAND * cost
    }

    /** The sheet number is already met. Pay has no sheet number. */
    fun met(before: Double, target: Double?): Boolean =
        target != null && target - before < 0.05

    fun choose(offers: List<AimOffer>, money: Double, mode: AimSpend, target: Double?): AimChoice {
        val open = offers.filter { it.gain > 0.0 }
        val pick = when (mode) {
            AimSpend.BEST -> bestOf(open) { a, b -> a.gain > b.gain + 1e-9 || (close(a.gain, b.gain) && cheaper(a, b)) }
            AimSpend.CHEAPEST -> {
                val clearing = if (target == null) emptyList() else open.filter { it.after + 1e-9 >= target }
                val pool = if (clearing.isNotEmpty()) clearing else open
                bestOf(pool) { a, b -> a.cost < b.cost - 1e-9 || (close(a.cost, b.cost) && a.gain > b.gain + 1e-9) || (close(a.cost, b.cost) && close(a.gain, b.gain) && a.id < b.id) }
            }
            AimSpend.AFFORDABLE -> bestOf(open.filter { covers(money, it.cost) }) { a, b ->
                a.after > b.after + 1e-9 || (close(a.after, b.after) && cheaper(a, b))
            }
            AimSpend.ALMOST -> bestOf(open.filter { almost(money, it.cost) }) { a, b ->
                a.after > b.after + 1e-9 || (close(a.after, b.after) && cheaper(a, b))
            }
        }
        val reaches = pick != null && target != null && pick.after + 1e-9 >= target
        return AimChoice(pick, reaches, pick != null && target != null && !reaches)
    }

    private fun bestOf(offers: List<AimOffer>, beats: (AimOffer, AimOffer) -> Boolean): AimOffer? {
        var chosen: AimOffer? = null
        for (offer in offers) {
            val current = chosen
            if (current == null || beats(offer, current)) chosen = offer
        }
        return chosen
    }

    /** True when [a] should replace [b] on a tie of the main number: lower price, then the earlier id. */
    private fun cheaper(a: AimOffer, b: AimOffer): Boolean {
        if (a.cost < b.cost - 1e-9) return true
        if (b.cost < a.cost - 1e-9) return false
        return a.id < b.id
    }

    private fun close(a: Double, b: Double): Boolean = abs(a - b) <= 1e-9
}

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

    fun recommend(state: GameState, goal: AimGoal? = null, spend: AimSpend = AimSpend.BEST): Aim {
        if (goal != null) return toward(state, goal, spend)
        val block = Block.of(state.blockId, state.prestiges)
        val nowParams = Logic.paramsFrom(state)
        val now = EngineSim.evaluate(nowParams)
        val nowMpg = profitMpg(state, nowParams, now)
        val hpRatio = now.peakHorsepower / block.sheetHp(state.racing)
        val tqRatio = now.peakTorqueLbFt / block.sheetTorque(state.racing)
        val mpgRatio = nowMpg / block.sheetMpg(state.racing)
        val mark = farthest(hpRatio, tqRatio, mpgRatio)
        val goal = goal(block, mark, state.racing)
        val before = value(now.peakHorsepower, now.peakTorqueLbFt, nowMpg, mark)
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
            val nextState = state.copy(upgrades = state.upgrades + (upgrade.id to level + 1))
            val nextParams = Logic.paramsFrom(nextState)
            val next = EngineSim.evaluate(nextParams)
            val after = value(
                next.peakHorsepower,
                next.peakTorqueLbFt,
                if (mark == SheetMark.CRUISE) profitMpg(nextState, nextParams, next) else 0.0,
                mark,
            )
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

    /** mpg at the best-paying pedal, which is the number the rebuild sheet judges. */
    private fun profitMpg(state: GameState, params: com.revline.idle.model.EngineParams, stats: EngineStats): Double =
        Logic.profitStop(params, stats, Prestige.multiplier(state.knowledge), Logic.GATE_CAP).mpg

    /**
     * The next unlocked level for [goal], ranked by [spend].
     * Horsepower and torque are the sheet peaks. mpg is read at the profit pedal, the way the rebuild sheet reads it. $/s is the contract at the pedal you are holding, and it has no sheet number.
     */
    private fun toward(state: GameState, goal: AimGoal, spend: AimSpend): Aim {
        val nowParams = Logic.paramsFrom(state)
        val now = EngineSim.evaluate(nowParams)
        val beforePay = if (goal == AimGoal.PAY) contract(state, now) else 0.0
        val beforeMpg = if (goal == AimGoal.CRUISE) profitMpg(state, nowParams, now) else 0.0
        val before = reading(now, beforePay, beforeMpg, goal)
        val target = sheetTarget(state, goal)
        if (AimSpends.met(before, target)) {
            return Aim(
                markOf(goal),
                null,
                "Clear",
                "${goal.short} is already on the sheet, at ${fmt(goal, before)}.",
            )
        }
        val offers = ArrayList<AimOffer>()
        for (upgrade in Upgrades.all) {
            val level = state.upgrades[upgrade.id] ?: 0
            if (level >= upgrade.maxLevel) continue
            if (Logic.lockReason(state, upgrade.id) != null) continue
            val nextState = state.copy(upgrades = state.upgrades + (upgrade.id to level + 1))
            val nextParams = Logic.paramsFrom(nextState)
            val next = EngineSim.evaluate(nextParams)
            val afterPay = if (goal == AimGoal.PAY) contract(nextState, next) else 0.0
            val afterMpg = if (goal == AimGoal.CRUISE) profitMpg(nextState, nextParams, next) else 0.0
            val after = reading(next, afterPay, afterMpg, goal)
            val gain = after - before
            if (gain < floor(goal)) continue
            offers += AimOffer(upgrade.id, Logic.upgradeCost(state, upgrade.id), gain, after)
        }
        val choice = AimSpends.choose(offers, state.money, spend, target)
        val chosen = choice.offer
        if (chosen == null) {
            return Aim(markOf(goal), null, goal.short, none(goal, spend, before, state.money))
        }
        val name = Upgrades.byId(chosen.id).name
        val move = "${goal.short}  ${fmt(goal, before)} → ${fmt(goal, chosen.after)}"
        if (spend == AimSpend.BEST) {
            return Aim(markOf(goal), chosen.id, name, "$move. ${formatDollars(chosen.cost)}.")
        }
        return Aim(markOf(goal), chosen.id, name, "$move. ${why(spend, choice, goal, target, chosen.cost)}")
    }

    private fun none(goal: AimGoal, spend: AimSpend, before: Double, money: Double): String {
        val hand = formatDollars(money)
        return when (spend) {
            AimSpend.AFFORDABLE -> "Nothing that raises ${goal.short} is affordable. $hand on hand."
            AimSpend.ALMOST -> "Nothing that raises ${goal.short} is within 20% over $hand."
            else -> "Nothing unlocked raises ${goal.short} from ${fmt(goal, before)}."
        }
    }

    private fun why(spend: AimSpend, choice: AimChoice, goal: AimGoal, target: Double?, cost: Double): String {
        val price = formatDollars(cost)
        val short = if (choice.short && target != null) " Still short of ${fmt(goal, target)}." else ""
        val lead = when (spend) {
            AimSpend.CHEAPEST -> if (choice.reaches) "Cheapest that clears the sheet." else "Cheapest step."
            AimSpend.AFFORDABLE -> if (choice.reaches) "Best you can buy now." else "Best the cash reaches."
            AimSpend.ALMOST -> "Within 20% of the cash."
            AimSpend.BEST -> ""
        }
        return "$lead$short $price."
    }

    private fun sheetTarget(state: GameState, goal: AimGoal): Double? {
        val block = Block.of(state.blockId, state.prestiges)
        return when (goal) {
            AimGoal.POWER -> block.sheetHp(state.racing)
            AimGoal.TORQUE -> block.sheetTorque(state.racing)
            AimGoal.CRUISE -> block.sheetMpg(state.racing)
            AimGoal.PAY -> null
        }
    }

    private fun contract(state: GameState, stats: EngineStats): Double {
        val params = Logic.paramsFrom(state)
        val rpm = Logic.targetRpm(params.redlineRpm, state.throttle, params.idleRpm)
        val mult = Prestige.multiplier(state.knowledge)
        return Logic.incomeAt(params, rpm, stats.reliability, mult, state.throttle, stats) *
            Block.of(state.blockId, state.prestiges).payScale()
    }

    private fun reading(stats: EngineStats, pay: Double, profitMpg: Double, goal: AimGoal): Double = when (goal) {
        AimGoal.POWER -> stats.peakHorsepower
        AimGoal.TORQUE -> stats.peakTorqueLbFt
        AimGoal.CRUISE -> profitMpg
        AimGoal.PAY -> pay
    }

    private fun floor(goal: AimGoal): Double = when (goal) {
        AimGoal.POWER -> 0.05
        AimGoal.TORQUE -> 0.05
        AimGoal.CRUISE -> 0.05
        AimGoal.PAY -> 1e-6
    }

    private fun markOf(goal: AimGoal): SheetMark = when (goal) {
        AimGoal.POWER -> SheetMark.POWER
        AimGoal.TORQUE -> SheetMark.TORQUE
        AimGoal.CRUISE -> SheetMark.CRUISE
        AimGoal.PAY -> SheetMark.POWER
    }

    private fun fmt(goal: AimGoal, value: Double): String = when (goal) {
        AimGoal.POWER -> String.format(Locale.US, "%.1f hp", value)
        AimGoal.TORQUE -> String.format(Locale.US, "%.1f lb-ft", value)
        AimGoal.CRUISE -> String.format(Locale.US, "%.1f mpg", value)
        AimGoal.PAY -> "${formatDollars(value)}/s"
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

    private fun goal(block: Block, mark: SheetMark, racing: Boolean): Double = when (mark) {
        SheetMark.POWER -> block.sheetHp(racing)
        SheetMark.TORQUE -> block.sheetTorque(racing)
        SheetMark.CRUISE -> block.sheetMpg(racing)
    }

    private fun value(hp: Double, torque: Double, mpg: Double, mark: SheetMark): Double = when (mark) {
        SheetMark.POWER -> hp
        SheetMark.TORQUE -> torque
        SheetMark.CRUISE -> mpg
    }

    private fun label(mark: SheetMark): String = when (mark) {
        SheetMark.POWER -> "Peak power"
        SheetMark.TORQUE -> "Peak torque"
        SheetMark.CRUISE -> "Mpg at the profit pedal"
    }

    private fun fmt(mark: SheetMark, value: Double): String = when (mark) {
        SheetMark.CRUISE -> String.format(Locale.US, "%.1f mpg", value)
        SheetMark.POWER -> String.format(Locale.US, "%.0f hp", value)
        SheetMark.TORQUE -> String.format(Locale.US, "%.0f lb-ft", value)
    }
}
