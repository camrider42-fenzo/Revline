package com.revline.idle.economy

import com.revline.idle.model.Block
import com.revline.idle.model.FuelKind
import com.revline.idle.model.OperatingPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * The engine's maximum output. The pedal is scored against it.
 * Cruise mpg is not the paycheck. When this is omitted, the live point is the maximum.
 */
data class ContractOutput(
    val peakHorsepower: Double,
    val peakTorqueLbFt: Double,
    val cruiseMpg: Double,
)

object Income {
    const val K = 0.0397
    const val HP_EXP = 1.62
    const val USE_EXP = 1.85
    const val ECONOMY_EXP = 1.0
    const val PRICE_EXP = 0.42
    const val REL_EXP = 1.15
    const val MPG_REF = 28.0

    /**
     * Contract dollars per second at this pedal.
     * use        = (hpAtStop / peakHp)^0.85 * (torqueAtStop / peakTq)^0.15
     * powerScore = peakHp^1.62 * (1 + peakTq / 950) * use^1.85
     * economyScore = (efficiencyAtStop / 28)^1.0 * (3.40 / dollarsPerGallon)^0.42
     * heat       = condition^0.75
     * $/s = 0.0397 * powerScore * economyScore * (reliability/100)^1.15 * heat * multiplier
     *
     * The maximum sets how big the check can be. The pedal collects the share of
     * that maximum the engine is making right now, and the mpg at that same stop.
     * Rpm is not a separate term. A closed pedal leaves most of the output on the
     * table. A rich, hot floor spends the mpg and the heat.
     *
     * [ideal] is that same stop with a sound engine: reliability does not cut the
     * check, and temperature, knock, and a lean mixture do not either.
     */
    fun perSecond(
        point: OperatingPoint,
        reliability: Double,
        multiplier: Double,
        dollarsPerGallon: Double = FuelKind.PUMP_87.dollarsPerGallon,
        output: ContractOutput? = null,
        ideal: Boolean = false,
    ): Double {
        val peakHp = (output?.peakHorsepower ?: point.horsepower).coerceAtLeast(0.2)
        val peakTq = (output?.peakTorqueLbFt ?: point.torqueLbFt).coerceAtLeast(0.2)
        val hpUse = (point.horsepower / peakHp).coerceIn(0.0, 1.0)
        val tqUse = (point.torqueLbFt / peakTq).coerceIn(0.0, 1.0)
        val use = (hpUse.pow(0.85) * tqUse.pow(0.15)).coerceIn(0.04, 1.0)
        val power = peakHp.pow(HP_EXP) * (1.0 + peakTq / 950.0) * use.pow(USE_EXP)
        val mpgScore = (point.efficiency.coerceAtLeast(0.8) / MPG_REF).pow(ECONOMY_EXP)
        val pump = FuelKind.PUMP_87.dollarsPerGallon
        val priceScore = (pump / dollarsPerGallon.coerceAtLeast(0.5)).pow(PRICE_EXP)
        val health = if (ideal) 1.0 else (reliability / 100.0).coerceIn(0.42, 1.0)
        val heat = if (ideal) 1.0 else conditionFactor(point).pow(0.75)
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
    /** [mpg] is the live mpg at the pedal that pays the most, not the cruise figure. */
    fun meets(hp: Double, torque: Double, mpg: Double, block: Block, racing: Boolean = false): Boolean =
        hp >= block.sheetHp(racing) && torque >= block.sheetTorque(racing) && mpg >= block.sheetMpg(racing)

    /**
     * One point for clearing the sheet. Each stat then adds four points per time
     * it beats its own target, so staying to build past the bar raises the
     * multiplier. A stat that is only just over does not cancel the others.
     * mpg only gates. It is read at the profit pedal, where it falls as the build gets
     * stronger, so beating the mpg goal is not rewarded.
     */
    fun knowledgeGain(hp: Double, torque: Double, mpg: Double, block: Block, racing: Boolean = false): Int {
        if (!meets(hp, torque, mpg, block, racing)) return 0
        val depth = surplus(hp, block.sheetHp(racing)) + surplus(torque, block.sheetTorque(racing))
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
    const val RESEARCH_FACTOR = 0.20
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
