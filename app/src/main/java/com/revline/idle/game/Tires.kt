package com.revline.idle.game

import kotlin.math.max
import kotlin.math.pow

/**
 * Compounds, tire wear and the compound-change rule for circuit races.
 *
 * The owned circuit tire ([Racing.circuitTires], street to racing slick) is the construction: it sets the base grip and
 * how hard the tire is on itself. The compound is what is bolted on top: soft, medium and hard are slicks that scale that
 * grip and the tire's life, and the wet is the rain tire. Every compound is free on every tier, so there is nothing more to buy.
 *
 * Wear is measured in life: 1.0 is a tire at the end of its design life, however many laps that is. Each lap uses up
 * [lapUse] of it, which scales with the circuit's corner count, so a track of nineteen corners is hard on rubber and one of
 * eleven is kind. The grip left is [condition] of the life used, with a wear that quickens and then falls off a cliff.
 */
object Tires {
    enum class Compound(val title: String, val letter: String, val dry: Boolean, val grip: Double, val wear: Double, val blurb: String) {
        SOFT("Soft", "S", true, 1.016, 1.00, "Fastest. Wears out first."),
        MEDIUM("Medium", "M", true, 1.000, 0.60, "The middle road."),
        HARD("Hard", "H", true, 0.984, 0.40, "Slowest. Lasts."),
        WET("Wet", "W", false, 1.000, 0.40, "Grooved rubber for a wet or damp track. Overheats and wears away on a dry one."),
    }

    val dry: List<Compound> = Compound.entries.filter { it.dry }

    /** Corner count that wears a tire at the base rate. */
    const val REF_CORNERS = 14.0

    /** Life a soft tire uses up on a lap of [REF_CORNERS] corners, in hands of middling care, on a street-tire car. */
    const val BASE_USE = 0.19

    /** Wear grip floor: a destroyed tire still holds the road, barely. */
    const val FLOOR = 0.35

    /** Share of grip left (1.0 new) after [life] of the tire's life is used: a straight loss, then the cliff. */
    fun condition(life: Double): Double {
        val l = max(0.0, life)
        return max(FLOOR, 1.0 - 0.30 * l - 0.40 * l * l * l)
    }

    /** How much of the grip lost to wear the lap time feels: a tire shown at 60% grip costs 0.30 x 40% = 12% of the car's grip. */
    const val WEAR_FELT = 0.30

    /** The car's grip multiplier from the tire's condition (1.0 new). */
    fun felt(condition: Double): Double = 1.0 - WEAR_FELT * (1.0 - condition)

    /** How hard this compound wears under the sky: a wet tire cooks on a dry track, slicks run a little cooler in the wet. */
    fun wearIn(c: Compound, sev: Double): Double =
        if (c == Compound.WET) c.wear * (1.0 + 4.5 * (1.0 - sev)) else c.wear * (1.0 - 0.3 * sev)

    /** The circuit's share of the base: wear grows with its corner count, a touch more on a hot one. */
    fun cornerFactor(circuit: Circuits.Circuit): Double = circuit.corners / REF_CORNERS * (0.85 + 0.15 * circuit.heat)

    /**
     * Life one lap uses. [carWear] is the owned tire's own wear against the street tire, times the parts that save it
     * ([Circuits.Car.wear], 0 for a car whose tires never wear). [driver] is the push, the care and the hands.
     */
    fun lapUse(circuit: Circuits.Circuit, c: Compound, sev: Double, carWear: Double, driver: Double): Double =
        BASE_USE * cornerFactor(circuit) * wearIn(c, sev) * carWear * driver

    /** The owned tire's wear against the street tire: its [Racing.Tire.wear] taken to the power 0.3, so a racing slick is about 1.5 times as hard on itself, not four. */
    fun tierWear(tireWear: Double): Double = (tireWear / 0.004).pow(0.3)

    /** Wet-weather grip rating of a slick of this compound on the owned tire, 0..1. */
    fun slickRating(c: Compound, tierWet: Double): Double =
        (0.10 + 0.4 * tierWet + when (c) { Compound.SOFT -> -0.05; Compound.HARD -> 0.05; else -> 0.0 }).coerceIn(0.05, 0.95)

    /**
     * A wet tire is a wet tire whatever it was built on: most of the owned tier's extra dry grip does not carry over. The
     * tier's grip is read back from its wet rating (street .8 to racing .2), and the grip it has over the street tire is halved (square root).
     */
    fun wetEqual(tierWet: Double): Double {
        val mu = 0.94 + (0.8 - tierWet).coerceIn(0.0, 0.6) / 0.6 * 0.33
        return (0.94 / mu).pow(0.5)
    }

    /** How well a wet-weather tire copes with the wet: good, not perfect, so a steady hand still tells. */
    const val WET_RATING = 0.7

    /** Grip of a new tire of this compound against the owned tire's base, under this sky for this driver. */
    fun grip(c: Compound, weather: Circuits.Weather, tierWet: Double, skill: Double): Double =
        if (c == Compound.WET) {
            (1.0 - 0.10 * (1.0 - weather.severity)) * (1.0 - Circuits.wetLoss(weather, WET_RATING, skill)) * wetEqual(tierWet)
        } else {
            c.grip * (1.0 - Circuits.wetLoss(weather, slickRating(c, tierWet), skill))
        }

    /** A tire plan: [start] compound, in at the end of lap [stop] for [finish]. [stop] 0 is no stop (only in the wet). */
    data class Fit(val start: Compound, val stop: Int, val finish: Compound)

    /**
     * The rule: at least one change of compound a race. In the dry it is two different dry compounds. On a damp track any two
     * different compounds, the wet counting. In the wet it is waived, as in F1: the wets stay on and a stop is the team's call.
     */
    fun rule(weather: Circuits.Weather): String = when (weather) {
        Circuits.Weather.DRY -> "Rule: stop once and change compound, two different dry compounds."
        Circuits.Weather.DAMP -> "Rule: stop once and change compound. The wet counts as one."
        Circuits.Weather.WET -> "Wet race: the compound change is waived. A stop is optional."
    }

    /** Null if the [fit] obeys the rule on a race of [laps], else why not. */
    fun breach(fit: Fit, laps: Int, weather: Circuits.Weather): String? {
        if (fit.stop != 0 && fit.stop !in 1 until laps) return "Stop on lap 1 to ${laps - 1}."
        if (weather == Circuits.Weather.WET) return if (fit.stop == 0 && fit.finish != fit.start) "Without a stop the finish tire is the start tire." else null
        if (fit.stop == 0) return "A stop is required."
        if (fit.start == fit.finish) return "Change compound at the stop."
        if (weather == Circuits.Weather.DRY && (!fit.start.dry || !fit.finish.dry)) return "Two different dry compounds are needed."
        return null
    }

    /** What the stewards do to an order that breaks the rule: the smallest fix, so every race is a legal one. */
    fun legalise(fit: Fit, laps: Int, weather: Circuits.Weather): Fit {
        var start = fit.start
        var finish = fit.finish
        var stop = fit.stop
        if (weather == Circuits.Weather.WET) {
            stop = if (stop <= 0) 0 else stop.coerceIn(1, laps - 1)
            if (stop == 0) finish = start
            return Fit(start, stop, finish)
        }
        stop = (if (stop <= 0) (laps + 1) / 2 else stop).coerceIn(1, laps - 1)
        if (weather == Circuits.Weather.DRY) {
            if (!start.dry) start = Compound.MEDIUM
            if (!finish.dry) finish = Compound.HARD
        }
        if (finish == start) finish = if (start == Compound.HARD) Compound.MEDIUM else Compound.HARD
        return Fit(start, stop, finish)
    }

    /** Every legal one-stop plan, plus the no-stop plans where the rule allows them. */
    fun options(laps: Int, weather: Circuits.Weather): List<Fit> {
        val pool = if (weather == Circuits.Weather.DRY) dry else Compound.entries
        val out = ArrayList<Fit>()
        for (a in pool) for (b in pool) {
            if (a == b) continue
            for (stop in 1 until laps) out.add(Fit(a, stop, b))
        }
        if (weather == Circuits.Weather.WET) for (a in pool) out.add(Fit(a, 0, a))
        return out
    }

    /** Where each lap's tire stands: the compound on each lap, the grip left in the middle of it and at the end of it. */
    class Trace(val compounds: List<Compound>, val mid: List<Double>, val end: List<Double>, val pits: Set<Int>)

    fun trace(fit: Fit, laps: Int, use: (Compound) -> Double): Trace {
        val compounds = ArrayList<Compound>()
        val mid = ArrayList<Double>()
        val end = ArrayList<Double>()
        var life = 0.0
        for (k in 1..laps) {
            val c = if (fit.stop in 1 until laps && k > fit.stop) fit.finish else fit.start
            val u = use(c)
            compounds.add(c)
            mid.add(condition(life + u / 2.0))
            life += u
            end.add(condition(life))
            if (k == fit.stop) life = 0.0
        }
        return Trace(compounds, mid, end, if (fit.stop in 1 until laps) setOf(fit.stop) else emptySet())
    }

    /** Grip left at the stop, in percent of a new tire of that compound, or null for no stop. */
    fun atStop(t: Trace): Double? = t.pits.firstOrNull()?.let { t.end[it - 1] }

    fun atFlag(t: Trace): Double = t.end.last()

    /** How many corners of racing the tire sees: used to describe a circuit's wear. */
    fun wearWord(circuit: Circuits.Circuit): String = when {
        circuit.corners <= 12 -> "kind to tires"
        circuit.corners <= 15 -> "average on tires"
        circuit.corners <= 18 -> "hard on tires"
        else -> "brutal on tires"
    }

}
