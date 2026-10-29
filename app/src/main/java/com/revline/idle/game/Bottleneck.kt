package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.Block
import com.revline.idle.model.EngineStats
import com.revline.idle.model.OperatingPoint

/** The bay the engine is waiting on, and why. */
data class Suggestion(
    val effect: Effect,
    val headline: String,
    val detail: String,
)

object Bottleneck {
    /**
     * What to work on. A climbing head beats a dyno note, a dyno note beats the
     * rebuild sheet, and a clear sheet points at whichever bay still has the most left.
     */
    fun suggest(state: GameState, stats: EngineStats, live: OperatingPoint, soakPer10s: Double): Suggestion {
        if (live.heatSoakC >= 10.0 && soakPer10s > 0.35) {
            return Suggestion(
                Effect.COOLING,
                "Cooling",
                "The head is still taking on heat, ${signed(soakPer10s)}° every 10 seconds. Cooling is what lets this stop stay.",
            )
        }
        areaFor(stats.reliabilityNotes)?.let { (effect, why) ->
            return Suggestion(effect, effect.title, why)
        }
        if (live.fuelLimited) {
            return Suggestion(
                Effect.BOOST,
                "Boost",
                "The mixture is out of fuel. The pump and the injectors are the limit.",
            )
        }
        val block = Block.forPrestige(state.prestiges)
        val hp = stats.peakHorsepower / block.sheetHp(state.racing)
        val tq = stats.peakTorqueLbFt / block.sheetTorque(state.racing)
        val mpg = stats.cruiseMpg / block.sheetMpg(state.racing)
        if (minOf(hp, tq, mpg) < 1.0) {
            val worst = listOf(hp to "power", tq to "torque", mpg to "cruise").minBy { it.first }
            return if (worst.second == "cruise") {
                Suggestion(
                    Effect.ECONOMY,
                    "Economy",
                    "Cruise is the short number. ${one(stats.cruiseMpg)} mpg against a ${one(block.sheetMpg(state.racing))} mpg sheet.",
                )
            } else {
                Suggestion(
                    Effect.POWER,
                    "Power",
                    "Power is the short number. ${one(stats.peakHorsepower)} hp and ${one(stats.peakTorqueLbFt)} lb-ft, against ${one(block.sheetHp(state.racing))} and ${one(block.sheetTorque(state.racing))}.",
                )
            }
        }
        val left = Effect.entries.map { effect ->
            effect to Upgrades.all
                .filter { UpgradeMap.effectOf(it.id) == effect }
                .sumOf { (it.maxLevel - (state.upgrades[it.id] ?: 0)).coerceAtLeast(0) }
        }
        val open = left.filter { it.second > 0 }.maxByOrNull { it.second }
        if (open == null) {
            return Suggestion(Effect.POWER, "Clear", "Nothing on the map is left. This engine is out of parts.")
        }
        return Suggestion(
            open.first,
            open.first.title,
            "The sheet is clear. ${open.first.title} still has the most left.",
        )
    }

    /** The bay a dyno note belongs to, if the note names a limit. */
    fun areaFor(notes: List<String>): Pair<Effect, String>? {
        val heat = notes.firstOrNull { it.startsWith("Coolant") || it.startsWith("Oil") || it.startsWith("EGT") }
        if (heat != null) {
            return Effect.COOLING to "Heat is the limit. $heat."
        }
        if (notes.any { it == "Lean under boost" }) {
            return Effect.BOOST to "Boost is ahead of the fuel system. The mixture goes lean once the charger is in."
        }
        val metal = notes.firstOrNull { it == "Piston speed" || it == "Boost vs. internals" || it == "Past the limit" }
        if (metal != null) {
            return Effect.DURABILITY to "The bottom end is the limit. $metal."
        }
        if (notes.any { it == "Knock" }) {
            return Effect.POWER to "Knock is the limit. Spark and octane are the way through."
        }
        return null
    }

    private fun signed(rate: Double): String {
        val body = String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(rate))
        return if (rate >= 0.0) "+$body" else "−$body"
    }

    private fun one(value: Double): String = String.format(java.util.Locale.US, "%.0f", value)
}
