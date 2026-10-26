package com.revline.idle.game

import com.revline.idle.data.GameState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Screws on the running engine. Each one stays locked until its part is on the
 * engine. More levels of that part, and of the parts that refine it, shrink the step.
 */
object Tuners {
    data class Screw(
        val id: String,
        val name: String,
        val detail: String,
        val unit: String,
        /** Shown when the screw is still locked. */
        val lock: String,
        val parts: List<String>,
    )

    val all: List<Screw> = listOf(
        Screw(
            "idle",
            "Idle speed",
            "The throttle stop. Lower is calmer in the shop. Higher cleans up a stumble.",
            "rpm",
            "Needs Torque wrench pass",
            listOf("wrench"),
        ),
        Screw(
            "spark",
            "Spark advance",
            "Degrees added to the distributor or the map. Too much is knock. Too little is heat in the pipe.",
            "°",
            "Needs Distributor advance",
            listOf("base_timing", "mapped_timing"),
        ),
        Screw(
            "mixture",
            "Mixture trim",
            "Richer is a smaller number here and a fatter WOT mixture. Leaner cruise is the other way.",
            "trim",
            "Needs Needle clip",
            listOf("needle", "jets", "afr_map"),
        ),
        Screw(
            "cam",
            "Cam center",
            "Lobe center as a fraction of redline. A cam lets you pick a center. VVT makes the steps small.",
            "frac",
            "Needs Performance cam",
            listOf("cam", "vvt", "vvl"),
        ),
        Screw(
            "boost",
            "Wastegate",
            "Pounds added on top of the charger. The controller is what makes the clicks small.",
            "psi",
            "Needs a turbo or a supercharger",
            listOf("turbo", "supercharger", "twin", "boost_controller"),
        ),
        Screw(
            "redline",
            "Rev limiter",
            "How much of the spring package you actually use. Zero is the full redline those springs allow.",
            "rpm",
            "Needs Valve springs",
            listOf("springs"),
        ),
        Screw(
            "thermostat",
            "Thermostat",
            "Degrees below the part's setpoint. A colder stat trades a little efficiency for knock margin.",
            "°C",
            "Needs Low-temperature thermostat",
            listOf("thermostat"),
        ),
    )

    fun byId(id: String): Screw = all.first { it.id == id }

    fun unlocked(state: GameState, id: String): Boolean = when (id) {
        "boost" -> level(state, "turbo") + level(state, "supercharger") + level(state, "twin") > 0
        else -> level(state, byId(id).parts.first()) > 0
    }

    /** Higher means a smaller step and, for most screws, a wider range. */
    fun precision(state: GameState, id: String): Int = when (id) {
        "spark" -> level(state, "base_timing") + 2 * level(state, "mapped_timing")
        "mixture" -> level(state, "needle") + level(state, "jets") + 2 * level(state, "afr_map")
        "boost" -> chargerLevel(state) + 2 * level(state, "boost_controller")
        "cam" -> level(state, "cam") + 2 * level(state, "vvt") + 2 * level(state, "vvl")
        "redline" -> level(state, "springs")
        "thermostat" -> level(state, "thermostat")
        "idle" -> level(state, "wrench")
        else -> 0
    }

    fun step(state: GameState, id: String): Double {
        val p = precision(state, id).coerceAtLeast(1)
        return when (id) {
            "spark" -> nice(2.0 / p, 0.1)
            "mixture" -> nice(0.40 / p, 0.02)
            "boost" -> nice(1.0 / p, 0.1)
            "cam" -> nice(0.04 / p, 0.005)
            "redline" -> nice(200.0 / p, 25.0)
            "thermostat" -> if (p <= 1) 4.0 else 2.0
            "idle" -> nice(80.0 / p, 10.0)
            else -> 1.0
        }
    }

    fun range(state: GameState, id: String): ClosedFloatingPointRange<Double> {
        val p = precision(state, id).coerceAtLeast(1)
        return when (id) {
            "spark" -> -(1.0 + p * 0.6)..(2.0 + p * 1.4)
            "mixture" -> -(0.25 + p * 0.08)..(0.30 + p * 0.10)
            "boost" -> -2.0..(1.0 + p * 0.8)
            "cam" -> -(0.02 + p * 0.008)..(0.02 + p * 0.008)
            "redline" -> -(120.0 * p)..0.0
            "thermostat" -> -(4.0 * p)..0.0
            "idle" -> -(50.0 * p)..(50.0 * p)
            else -> 0.0..0.0
        }
    }

    fun value(state: GameState, id: String): Double {
        if (!unlocked(state, id)) return 0.0
        val raw = state.tune[id] ?: 0.0
        return snap(state, id, raw)
    }

    fun snap(state: GameState, id: String, raw: Double): Double {
        val span = range(state, id)
        val step = step(state, id).coerceAtLeast(1e-6)
        val clamped = raw.coerceIn(span.start, span.endInclusive)
        val snapped = round(clamped / step) * step
        return snapped.coerceIn(span.start, span.endInclusive)
    }

    fun nudge(state: GameState, id: String, direction: Int): GameState {
        if (!unlocked(state, id) || direction == 0) return state
        val next = snap(state, id, value(state, id) + direction * step(state, id))
        if (abs(next - value(state, id)) < 1e-9) return state
        return state.copy(tune = state.tune + (id to next))
    }

    fun format(state: GameState, id: String): String {
        val screw = byId(id)
        val shown = value(state, id)
        val digits = when {
            step(state, id) >= 10.0 -> 0
            step(state, id) >= 0.1 -> 1
            else -> 2
        }
        val body = "%.${digits}f".format(Locale.US, shown)
        val signed = if (shown > 0.0 && id != "idle") "+$body" else body
        return "$signed ${screw.unit}"
    }

    private fun level(state: GameState, id: String): Int = state.upgrades[id] ?: 0

    private fun chargerLevel(state: GameState): Int =
        maxOf(level(state, "turbo"), level(state, "supercharger"), level(state, "twin"))

    private fun nice(raw: Double, floor: Double): Double {
        val value = raw.coerceAtLeast(floor)
        val mag = Math.pow(10.0, Math.floor(Math.log10(value)))
        val lead = value / mag
        val niceLead = when {
            lead < 1.5 -> 1.0
            lead < 3.5 -> 2.0
            lead < 7.5 -> 5.0
            else -> 10.0
        }
        return niceLead * mag
    }
}
