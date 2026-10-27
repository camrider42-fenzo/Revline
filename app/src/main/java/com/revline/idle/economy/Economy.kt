package com.revline.idle.economy

import com.revline.idle.model.Block
import com.revline.idle.model.FuelKind
import com.revline.idle.model.OperatingPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * The engine's output and its cruise economy. The rpm on the tach only leans these.
 * When the live point is the whole story, pass nothing and the point is used as both.
 */
data class ContractOutput(
    val peakHorsepower: Double,
    val peakTorqueLbFt: Double,
    val cruiseMpg: Double,
)

object Income {
    const val K = 0.00535
    const val HP_EXP = 1.62
    const val ECONOMY_EXP = 0.72
    const val PRICE_EXP = 0.42
    const val REL_EXP = 1.15
    const val MPG_REF = 28.0

    /**
     * Contract dollars per second.
     * paidHp   = peakHp * (0.90 + 0.10 * hpAtStop / peakHp)
     * paidTq   = peakTq * (0.75 + 0.25 * torqueAtStop / peakTq)
     * paidMpg  = cruiseMpg * (0.94 + 0.06 * min(1, mpgAtStop / cruiseMpg))
     * powerScore      = paidHp^1.62 * (1 + paidTq / 950)
     * economyScore    = (paidMpg / 28)^0.72 * (3.40 / dollarsPerGallon)^0.42
     * durabilityScore = (reliability/100)^1.15
     * heat            = 0.84 + 0.16 * condition
     * $/s = 0.00535 * powerScore * economyScore * durabilityScore * heat * multiplier
     *
     * Peak horsepower, peak torque, and cruise mpg set the check. The stop can
     * only lean them, and idling does not improve the economy term. Heat can
     * take 16% off a pull that is already past its limits.
     * The price exponent is gentle. Octane has to earn its keep as power, not as a tax.
     */
    fun perSecond(
        point: OperatingPoint,
        reliability: Double,
        multiplier: Double,
        dollarsPerGallon: Double = FuelKind.PUMP_87.dollarsPerGallon,
        output: ContractOutput? = null,
    ): Double {
        val peakHp = (output?.peakHorsepower ?: point.horsepower).coerceAtLeast(0.2)
        val peakTq = (output?.peakTorqueLbFt ?: point.torqueLbFt).coerceAtLeast(0.2)
        val cruise = (output?.cruiseMpg ?: point.mpg).coerceAtLeast(0.8)
        val hp = peakHp * (0.90 + 0.10 * (point.horsepower / peakHp).coerceIn(0.0, 1.0))
        val tq = peakTq * (0.75 + 0.25 * (point.torqueLbFt / peakTq).coerceIn(0.0, 1.0))
        val mpg = cruise * (0.94 + 0.06 * (point.mpg / cruise).coerceIn(0.0, 1.0))
        val power = hp.pow(HP_EXP) * (1.0 + tq / 950.0)
        val mpgScore = (mpg / MPG_REF).pow(ECONOMY_EXP)
        val pump = FuelKind.PUMP_87.dollarsPerGallon
        val priceScore = (pump / dollarsPerGallon.coerceAtLeast(0.5)).pow(PRICE_EXP)
        val health = (reliability / 100.0).coerceIn(0.42, 1.0)
        val heat = 0.84 + 0.16 * conditionFactor(point)
        return K * power * mpgScore * priceScore * health.pow(REL_EXP) * heat * multiplier.coerceAtLeast(0.0)
    }

    /** 1 when this rpm is inside thermal and knock limits, down toward 0.2 when it is not. */
    fun conditionFactor(point: OperatingPoint): Double {
        val cool = softLimit(point.coolantTempC, 105.0, 125.0)
        val oil = softLimit(point.oilTempC, 145.0, 175.0)
        val egt = softLimit(point.egtC, 900.0, 1050.0)
        val knock = (1.0 - point.unresolvedKnock.coerceAtLeast(0.0) / 16.0).coerceIn(0.22, 1.0)
        val fuel = if (point.fuelLimited) 0.72 else 1.0
        val soak = softLimit(point.heatSoakC, 7.0, 30.0)
        return (cool * oil * egt * knock * fuel * soak).coerceIn(0.18, 1.0)
    }

    private fun softLimit(value: Double, ok: Double, bad: Double): Double {
        if (value <= ok) return 1.0
        val u = ((value - ok) / (bad - ok)).coerceIn(0.0, 1.0)
        return 1.0 - 0.72 * u
    }
}

object Prestige {
    fun meets(hp: Double, torque: Double, mpg: Double, block: Block): Boolean =
        hp >= block.hpGoal && torque >= block.torqueGoal && mpg >= block.mpgGoal

    /**
     * One point for clearing the sheet. Each stat then adds four points per time
     * it beats its own target, so staying to build past the bar raises the
     * multiplier. A stat that is only just over does not cancel the others.
     */
    fun knowledgeGain(hp: Double, torque: Double, mpg: Double, block: Block): Int {
        if (!meets(hp, torque, mpg, block)) return 0
        val depth = surplus(hp, block.hpGoal) + surplus(torque, block.torqueGoal) + surplus(mpg, block.mpgGoal)
        return kotlin.math.floor(1.0 + depth * 4.0 + 1e-9).toInt().coerceIn(1, 16)
    }

    private fun surplus(value: Double, goal: Double): Double =
        (value / goal - 1.0).coerceAtLeast(0.0)

    fun multiplier(knowledge: Int): Double = 1.0 + knowledge.coerceAtLeast(0) * 0.12
}

object Offline {
    const val CAP_MS = 8L * 60L * 60L * 1000L
    const val MIN_MS = 20_000L
    const val MONEY_FACTOR = 0.80
    const val RESEARCH_FACTOR = 0.50
}

fun formatCompact(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "—"
    val sign = if (value < 0) "-" else ""
    val n = abs(value)
    if (n < 1000.0) {
        val body = if (n < 100.0) {
            String.format(Locale.US, "%.2f", n)
        } else {
            String.format(Locale.US, "%.0f", n)
        }
        return sign + body
    }
    val units = arrayOf("K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc")
    var scaled = n
    var unit = -1
    while (scaled >= 1000.0 && unit < units.lastIndex) {
        scaled /= 1000.0
        unit++
    }
    val body = if (scaled >= 100.0) {
        String.format(Locale.US, "%.0f", scaled)
    } else {
        String.format(Locale.US, "%.2f", scaled)
    }
    return sign + body + units[unit]
}

fun formatDollars(value: Double): String = "$" + formatCompact(value)

fun formatDuration(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(0L)
    val hours = totalMin / 60L
    val mins = totalMin % 60L
    return when {
        hours <= 0L -> "$mins min"
        mins == 0L -> "$hours h"
        else -> "$hours h $mins min"
    }
}
