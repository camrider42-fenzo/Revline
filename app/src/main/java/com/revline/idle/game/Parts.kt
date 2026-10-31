package com.revline.idle.game

import com.revline.idle.data.GameState

/**
 * Race components: seven lines with two or three levels each. Every level is a study (research points) and then a
 * purchase (money), like a gearbox or a tire. A level needs the one below it, and the later ones open with a rebuild.
 * One level of a line is fitted at a time, and level 0 is the stock part. A part is bought once and swaps for free.
 *
 * Each level is meant for the circuit first. The few effects that also reach a drag strip are small on purpose: weight
 * comes off both cars, a wing costs a little top end, a stiffer car hooks a little better, and a bigger radiator holds
 * the power a little longer.
 */
object Parts {
    /**
     * What a set of components does to the car. Neutral values are 1.0 for multipliers and 0.0 for sums.
     * The first group touches a drag run as well as a lap. The second group is the circuit only.
     */
    data class Fx(
        /** Share of the chassis weight taken off. */
        val shave: Double = 0.0,
        /** Kilograms added (or taken off, when negative). */
        val addKg: Double = 0.0,
        /** Body drag. Above 1 is worse. */
        val dragMult: Double = 1.0,
        /** Cooling, counted in ducts. It lowers the engine's fade. */
        val cool: Double = 0.0,
        /** Grip on a standing start. */
        val launch: Double = 1.0,
        // Circuit only below this line.
        /** Cornering grip. */
        val lateral: Double = 1.0,
        /** Braking force. */
        val brake: Double = 1.0,
        /** Grip out of slow corners. */
        val traction: Double = 1.0,
        /** Downforce: extra down-load per kg for each (m/s)^2 of speed. */
        val downforce: Double = 0.0,
        /** How often the driver makes a mistake. Below 1 is better. */
        val mistakes: Double = 1.0,
        /** How fast the tires go off. Below 1 is better. */
        val wear: Double = 1.0,
        /** The engine's chance of letting go. Below 1 is better. */
        val hazard: Double = 1.0,
    ) {
        operator fun plus(o: Fx) = Fx(
            shave + o.shave, addKg + o.addKg, dragMult * o.dragMult, cool + o.cool, launch * o.launch,
            lateral * o.lateral, brake * o.brake, traction * o.traction, downforce + o.downforce,
            mistakes * o.mistakes, wear * o.wear, hazard * o.hazard,
        )
    }

    val NONE = Fx()

    private fun pct(x: Double) = Math.round((x - 1.0) * 1000.0) / 10.0
    private fun signed(x: Double): String {
        val v = pct(x)
        val t = if (v == Math.rint(v)) v.toInt().toString() else v.toString()
        return if (v > 0) "+$t%" else "$t%"
    }

    /** What these effects do on a circuit, in words. Empty effects read "no change". */
    fun Fx.circuitText(): String {
        val out = mutableListOf<String>()
        if (brake != 1.0) out += "braking ${signed(brake)}"
        if (lateral != 1.0) out += "corners ${signed(lateral)}"
        if (traction != 1.0) out += "traction ${signed(traction)}"
        if (downforce > 0.0) out += "downforce"
        if (mistakes != 1.0) out += "mistakes ${signed(mistakes)}"
        if (wear != 1.0) out += "tire wear ${signed(wear)}"
        if (hazard != 1.0) out += "engine failures ${signed(hazard)}"
        if (cool > 0.0) out += "less fade"
        if (shave > 0.0) out += "lighter"
        return if (out.isEmpty()) "no change" else out.joinToString(", ")
    }

    /** What these effects do on a drag strip, in words. */
    fun Fx.dragText(): String {
        val out = mutableListOf<String>()
        if (shave > 0.0) out += "weight -${Math.round(shave * 100.0)}%"
        if (addKg > 0.0) out += "+${addKg.toInt()} kg"
        if (addKg < 0.0) out += "${addKg.toInt()} kg"
        if (dragMult != 1.0) out += "drag ${signed(dragMult)}"
        if (launch != 1.0) out += "launch ${signed(launch)}"
        if (cool > 0.0) out += "a little less fade"
        return if (out.isEmpty()) "no change" else out.joinToString(", ")
    }

    class Level(
        val name: String,
        val detail: String,
        val studyRp: Double,
        val minPrestige: Int,
        val price: Double,
        val fx: Fx,
    ) {
        /** What it does on a circuit, and on a strip. Written from the numbers, so the screen cannot drift from the sim. */
        val circuitHint: String get() = fx.circuitText()
        val dragHint: String get() = fx.dragText()
    }

    class Line(val id: String, val title: String, val blurb: String, val levels: List<Level>) {
        /** Level 1 is the first part you buy. Level 0 is what the car came with. */
        fun level(n: Int): Level? = levels.getOrNull(n - 1)
        fun studyId(n: Int) = "kit_${id}_$n"
    }

    val lines: List<Line> = listOf(
        Line(
            "brakes", "BRAKES", "Later braking points, and a car that stops in the same place lap after lap.",
            listOf(
                Level("Sport pads", "A harder pad that bites hot.", 40.0, 0, 500.0, Fx(brake = 1.12, mistakes = 0.97)),
                Level("Vented rotors", "More iron to soak the heat.", 300.0, 2, 2400.0, Fx(brake = 1.27, mistakes = 0.94, addKg = 2.0)),
                Level("Big brake kit", "Four-pot calipers and big discs.", 1400.0, 4, 6200.0, Fx(brake = 1.46, mistakes = 0.90, addKg = 5.0)),
            ),
        ),
        Line(
            "suspension", "SUSPENSION", "Springs and dampers keep the tires flat on the road, and put the power down out of a corner.",
            listOf(
                Level("Stiff springs", "Less roll, less squat.", 60.0, 1, 700.0, Fx(lateral = 1.04, traction = 1.05, launch = 1.01)),
                Level("Dampers", "Gas dampers that hold a spring in check.", 520.0, 3, 3000.0, Fx(lateral = 1.08, traction = 1.10, launch = 1.02)),
                Level("Coilovers", "Height and rate to suit the track.", 2000.0, 5, 8200.0, Fx(lateral = 1.12, traction = 1.16, launch = 1.035, addKg = -4.0)),
            ),
        ),
        Line(
            "arb", "ANTI-ROLL BARS", "A stiffer car turns in flatter and does what the driver expects.",
            listOf(
                Level("Front bar", "A thicker bar up front.", 50.0, 1, 600.0, Fx(lateral = 1.04, mistakes = 0.94, addKg = 2.0)),
                Level("Adjustable pair", "A bar at each end, tuned to the track.", 900.0, 4, 4400.0, Fx(lateral = 1.12, traction = 1.04, mistakes = 0.88, launch = 0.995, addKg = 4.0)),
            ),
        ),
        Line(
            "aero", "AERO", "Downforce pushes the car into the road in a fast corner. The wing is drag on a straight.",
            listOf(
                Level("Splitter", "A lip under the nose.", 280.0, 2, 2000.0, Fx(downforce = 0.0003, dragMult = 1.04, addKg = 3.0)),
                Level("Rear wing", "A wing on struts.", 1100.0, 4, 5200.0, Fx(downforce = 0.00065, dragMult = 1.12, addKg = 8.0)),
                Level("Full aero kit", "Splitter, wing, flat floor and diffuser.", 4200.0, 6, 14000.0, Fx(downforce = 0.0011, dragMult = 1.25, addKg = 14.0)),
            ),
        ),
        Line(
            "weight", "WEIGHT", "Everything not bolted down comes out. A light car turns, stops and pulls harder.",
            listOf(
                Level("Seats and trim", "A bucket seat. The carpet goes.", 40.0, 0, 450.0, Fx(shave = 0.015, lateral = 1.02, brake = 1.04, wear = 0.95)),
                Level("Stripped interior", "No headlining, no sound deadening.", 450.0, 3, 2600.0, Fx(shave = 0.03, lateral = 1.04, brake = 1.08, wear = 0.90)),
                Level("Panels and glass", "Thin panels and plastic windows.", 1800.0, 5, 7600.0, Fx(shave = 0.05, lateral = 1.07, brake = 1.14, wear = 0.85)),
            ),
        ),
        Line(
            "cooling", "COOLING", "A circuit is a long, hot run. A cool engine keeps its power and its life.",
            listOf(
                Level("Oil cooler", "A small matrix on the sump.", 70.0, 1, 800.0, Fx(cool = 0.5, hazard = 0.85)),
                Level("Big radiator", "A thick core and a better fan.", 600.0, 3, 3200.0, Fx(cool = 1.1, hazard = 0.70, addKg = 3.0)),
                Level("Ducting and baffles", "Air goes through the core, not round it.", 2200.0, 5, 9000.0, Fx(cool = 1.8, hazard = 0.55, dragMult = 1.01, addKg = 3.0)),
            ),
        ),
        Line(
            "steer", "STEERING AND SETUP", "A quick rack, and a car set up for the track. The driver makes fewer mistakes.",
            listOf(
                Level("Quick rack", "Fewer turns lock to lock.", 200.0, 2, 1500.0, Fx(lateral = 1.02, mistakes = 0.93)),
                Level("Alignment and corner weights", "The shop sets the car up on a flat floor.", 1000.0, 4, 4800.0, Fx(lateral = 1.07, mistakes = 0.90, wear = 0.80)),
                Level("Full setup", "Geometry, bump steer and a day on the track.", 3800.0, 6, 12000.0, Fx(lateral = 1.11, mistakes = 0.86, wear = 0.65)),
            ),
        ),
    )

    fun line(id: String): Line? = lines.firstOrNull { it.id == id }

    fun isLine(id: String): Boolean = lines.any { it.id == id }

    fun level(state: GameState, line: String): Int = (state.kit[line] ?: 0).coerceAtLeast(0)

    /** The sum of everything fitted. */
    fun kit(state: GameState): Fx = lines.fold(NONE) { acc, line ->
        val fitted = line.level(level(state, line.id))
        if (fitted == null) acc else acc + fitted.fx
    }

    /** The two-line hint a level carries on the parts screen. */
    fun hint(line: Line, level: Int): String {
        val l = line.level(level) ?: return "Stock."
        return "circuit: ${l.circuitHint}  ·  drag: ${l.dragHint}"
    }
}
