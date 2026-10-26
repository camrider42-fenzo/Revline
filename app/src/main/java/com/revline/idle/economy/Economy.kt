package com.revline.idle.economy

import com.revline.idle.model.FuelKind
import com.revline.idle.model.OperatingPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

object Income {
    const val K = 0.016
    const val HP_EXP = 1.62
    const val ECONOMY_EXP = 0.58
    const val REL_EXP = 1.15
    const val MPG_REF = 28.0

    /**
     * Contract dollars per second at the rpm the crank is turning.
     * powerScore      = HP^1.62 * (1 + torqueLbFt / 950)
     * economyScore    = (mpg / 28)^0.58 * (pumpPrice / thisFuelPrice)
     * durabilityScore = (reliability/100 * condition)^1.15
     * $/s = 0.016 * powerScore * economyScore * durabilityScore * multiplier
     *
     * mpg is this pull's fuel flow scored as miles per gallon at 60 mph.
     * A dearer gallon pays less for the same burn.
     */
    fun perSecond(
        point: OperatingPoint,
        reliability: Double,
        multiplier: Double,
        dollarsPerGallon: Double = FuelKind.PUMP_87.dollarsPerGallon,
    ): Double {
        val hp = point.horsepower.coerceAtLeast(0.2)
        val tq = point.torqueLbFt.coerceAtLeast(0.2)
        val power = hp.pow(HP_EXP) * (1.0 + tq / 950.0)
        val mpgScore = (point.mpg.coerceAtLeast(0.8) / MPG_REF).pow(ECONOMY_EXP)
        val pump = FuelKind.PUMP_87.dollarsPerGallon
        val priceScore = pump / dollarsPerGallon.coerceAtLeast(0.5)
        val rel = (reliability / 100.0 * conditionFactor(point)).coerceIn(0.05, 1.0)
        return K * power * mpgScore * priceScore * rel.pow(REL_EXP) * multiplier.coerceAtLeast(0.0)
    }

    /** 1 when this rpm is inside thermal and knock limits, down toward 0.2 when it is not. */
    fun conditionFactor(point: OperatingPoint): Double {
        val cool = softLimit(point.coolantTempC, 105.0, 125.0)
        val oil = softLimit(point.oilTempC, 145.0, 175.0)
        val egt = softLimit(point.egtC, 900.0, 1050.0)
        val knock = 1.0 - point.unresolvedKnock.coerceIn(0.0, 1.0) * 0.65
        val fuel = if (point.fuelLimited) 0.72 else 1.0
        return (cool * oil * egt * knock * fuel).coerceIn(0.20, 1.0)
    }

    private fun softLimit(value: Double, ok: Double, bad: Double): Double {
        if (value <= ok) return 1.0
        val u = ((value - ok) / (bad - ok)).coerceIn(0.0, 1.0)
        return 1.0 - 0.72 * u
    }
}

object Prestige {
    const val MIN_EARNINGS = 95_000.0

    fun knowledgeGain(runEarnings: Double): Int {
        if (runEarnings < MIN_EARNINGS) return 0
        val raw = 3.0 * (runEarnings / MIN_EARNINGS).pow(0.5)
        return kotlin.math.floor(raw).toInt().coerceAtLeast(1)
    }

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
