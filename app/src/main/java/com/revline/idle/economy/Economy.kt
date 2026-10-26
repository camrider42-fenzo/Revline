package com.revline.idle.economy

import com.revline.idle.model.EngineStats
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

object Income {
    const val K = 0.0030

    /**
     * Contract dollars per second from the dyno sheet.
     * powerScore = HP^1.72 * (1 + torque/950)
     * economyScore = (MPG/28)^0.58
     * durabilityScore = (reliability/100)^1.28 * (redline/6500)^0.24
     * $/s = 0.0030 * powerScore * economyScore * durabilityScore * multiplier
     */
    fun perSecond(stats: EngineStats, multiplier: Double): Double {
        val hp = stats.peakHorsepower.coerceAtLeast(1.0)
        val tq = stats.peakTorqueLbFt.coerceAtLeast(1.0)
        val mpg = stats.cruiseMpg.coerceAtLeast(1.0)
        val redline = stats.redlineRpm.coerceAtLeast(1.0)
        val rel = (stats.reliability / 100.0).coerceIn(0.05, 1.0)
        val power = hp.pow(1.72) * (1.0 + tq / 950.0)
        val economy = (mpg / 28.0).pow(0.58)
        val durability = rel.pow(1.28) * (redline / 6500.0).pow(0.24)
        return K * power * economy * durability * multiplier.coerceAtLeast(0.0)
    }
}

object Prestige {
    const val MIN_EARNINGS = 175_000.0

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
