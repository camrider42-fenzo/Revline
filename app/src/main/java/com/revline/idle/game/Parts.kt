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
        // Drag strip only below this line. The circuit never reads them. See [strip].
        /** Launch control: the share of a spinning launch's lost push that the module gives back, 0..1. */
        val spinTrim: Double = 0.0,
        /** 1 with a transbrake: the engine is held at launch rpm and let go on the button. */
        val preload: Double = 0.0,
        /** Wheelie bars: extra push, in g, the car takes before the nose comes up. */
        val liftG: Double = 0.0,
        /** Flywheel inertia taken off the driveline, in kg m². It spins up faster and bogs on the launch. */
        val flywheel: Double = 0.0,
        /** Shift time. Below 1 is quicker, and less forgiving of a sloppy hand. */
        val shiftMult: Double = 1.0,
        /** 1 with a line lock: a burnout warms the tires and raises their pressure a little. */
        val burnout: Double = 0.0,
        /** Nitrous: the share of torque the shot adds while it flows. */
        val shot: Double = 0.0,
        /** Nitrous: seconds of flow in the bottle. */
        val bottleS: Double = 0.0,
    ) {
        operator fun plus(o: Fx) = Fx(
            shave + o.shave, addKg + o.addKg, dragMult * o.dragMult, cool + o.cool, launch * o.launch,
            lateral * o.lateral, brake * o.brake, traction * o.traction, downforce + o.downforce,
            mistakes * o.mistakes, wear * o.wear, hazard * o.hazard,
            maxOf(spinTrim, o.spinTrim), maxOf(preload, o.preload), liftG + o.liftG, flywheel + o.flywheel,
            shiftMult * o.shiftMult, maxOf(burnout, o.burnout), shot + o.shot, maxOf(bottleS, o.bottleS),
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
        if (spinTrim > 0.0) out += "spin trimmed ${Math.round(spinTrim * 100.0)}%"
        if (preload > 0.0) out += "launch rpm held, set on the Drag tune"
        if (liftG > 0.0) out += "wheelie limit +${"%.2f".format(java.util.Locale.US, liftG)} g"
        if (flywheel > 0.0) out += "revs faster, bogs on the launch"
        if (shiftMult != 1.0) out += "shifts ${signed(shiftMult)} time, less forgiving"
        if (burnout > 0.0) out += "warm tires, +1 psi hot"
        if (shot > 0.0) out += "+${Math.round(shot * 100.0)}% torque for ${"%.0f".format(java.util.Locale.US, bottleS)} s"
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

    /** [strip] lines are drag parts: full price, and the circuit never sees them. */
    class Line(val id: String, val title: String, val blurb: String, val levels: List<Level>, val strip: Boolean = false) {
        /** Level 1 is the first part you buy. Level 0 is what the car came with. */
        fun level(n: Int): Level? = levels.getOrNull(n - 1)
        fun studyId(n: Int) = "kit_${id}_$n"
    }

    val lines: List<Line> = listOf(
        Line(
            "brakes", "BRAKES", "Later braking points, and a car that stops in the same place lap after lap.",
            listOf(
                Level("Sport pads", "A harder pad that bites hot.", 40.0, 0, 450.0, Fx(brake = 1.12, mistakes = 0.97)),
                Level("Vented rotors", "More iron to soak the heat.", 280.0, 2, 2250.0, Fx(brake = 1.27, mistakes = 0.94, addKg = 2.0)),
                Level("Big brake kit", "Four-pot calipers and big discs.", 1330.0, 4, 5900.0, Fx(brake = 1.46, mistakes = 0.90, addKg = 5.0)),
            ),
        ),
        Line(
            "suspension", "SUSPENSION", "Springs and dampers keep the tires flat on the road, and put the power down out of a corner.",
            listOf(
                Level("Stiff springs", "Less roll, less squat.", 80.0, 1, 950.0, Fx(lateral = 1.04, traction = 1.05, launch = 1.01)),
                Level("Dampers", "Gas dampers that hold a spring in check.", 590.0, 3, 3400.0, Fx(lateral = 1.08, traction = 1.10, launch = 1.02)),
                Level("Coilovers", "Height and rate to suit the track.", 2100.0, 5, 8600.0, Fx(lateral = 1.12, traction = 1.16, launch = 1.035, addKg = -4.0)),
            ),
        ),
        Line(
            "arb", "ANTI-ROLL BARS", "A stiffer car turns in flatter and does what the driver expects.",
            listOf(
                Level("Front bar", "A thicker bar up front.", 50.0, 1, 650.0, Fx(lateral = 1.04, mistakes = 0.94, addKg = 2.0)),
                Level("Adjustable pair", "A bar at each end, tuned to the track.", 650.0, 4, 3200.0, Fx(lateral = 1.12, traction = 1.04, mistakes = 0.88, launch = 0.995, addKg = 4.0)),
            ),
        ),
        Line(
            "aero", "AERO", "Downforce pushes the car into the road in a fast corner. The wing is drag on a straight.",
            listOf(
                Level("Splitter", "A lip under the nose.", 100.0, 2, 700.0, Fx(downforce = 0.0003, dragMult = 1.04, addKg = 3.0)),
                Level("Rear wing", "A wing on struts.", 650.0, 4, 3100.0, Fx(downforce = 0.00065, dragMult = 1.12, addKg = 8.0)),
                Level("Full aero kit", "Splitter, wing, flat floor and diffuser.", 2500.0, 6, 8300.0, Fx(downforce = 0.0011, dragMult = 1.25, addKg = 14.0)),
            ),
        ),
        Line(
            "weight", "WEIGHT", "Everything not bolted down comes out. A light car turns, stops and pulls harder.",
            listOf(
                Level("Seats and trim", "A bucket seat. The carpet goes.", 100.0, 0, 1100.0, Fx(shave = 0.015, lateral = 1.02, brake = 1.04, wear = 0.95)),
                Level("Stripped interior", "No headlining, no sound deadening.", 900.0, 3, 5200.0, Fx(shave = 0.03, lateral = 1.04, brake = 1.08, wear = 0.90)),
                Level("Panels and glass", "Thin panels and plastic windows.", 3200.0, 5, 13500.0, Fx(shave = 0.05, lateral = 1.07, brake = 1.14, wear = 0.85)),
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
                Level("Quick rack", "Fewer turns lock to lock.", 50.0, 2, 350.0, Fx(lateral = 1.02, mistakes = 0.93)),
                Level("Alignment and corner weights", "The shop sets the car up on a flat floor.", 1060.0, 4, 5100.0, Fx(lateral = 1.07, mistakes = 0.90, wear = 0.80)),
                Level("Full setup", "Geometry, bump steer and a day on the track.", 3800.0, 6, 12000.0, Fx(lateral = 1.11, mistakes = 0.86, wear = 0.65)),
            ),
        ),
    )

    /**
     * Drag parts. They take the same road as the circuit lines (a study, a price, one level fitted, swapped free), cost
     * their full list price, and only the strip reads what they do. Each effect is a term in [RaceDrive].
     */
    val strip: List<Line> = listOf(
        Line(
            "launch", "LAUNCH CONTROL", "A module that rations the push so a flat-footed launch does not just light the tires up.",
            listOf(
                Level("Two-step limiter", "Holds the revs on the line and trims what the tires cannot take. Crude.", 90.0, 1, 600.0, Fx(spinTrim = 0.35)),
                Level("Traction-aware module", "Reads wheel speed and pulls timing the moment a tire lets go.", 700.0, 3, 2800.0, Fx(spinTrim = 0.70, addKg = 1.0)),
                Level("Pro launch module", "A map for each tire and each track. The tires barely smoke.", 3000.0, 5, 7600.0, Fx(spinTrim = 0.95, addKg = 1.0)),
            ),
            strip = true,
        ),
        Line(
            "tbrake", "TRANSBRAKE", "The car is held with the engine already on the rev. Let go and the whole hit arrives at once.",
            listOf(
                Level("Transbrake", "Holds the box in first and the car on the line. Set the launch rpm on the Drag tune.", 1100.0, 3, 3800.0, Fx(preload = 1.0, addKg = 6.0)),
            ),
            strip = true,
        ),
        Line(
            "wbars", "WHEELIE BARS", "Past a certain push the nose comes up and the push is wasted. Bars catch it.",
            listOf(
                Level("Wheelie bars", "Two tubes and two wheels behind the axle.", 450.0, 3, 1500.0, Fx(liftG = 0.35, addKg = 6.0, dragMult = 1.01)),
                Level("Adjustable long bars", "Longer, with a spring in each wheel. The nose stays down on a big launch.", 1800.0, 5, 4600.0, Fx(liftG = 0.70, addKg = 9.0, dragMult = 1.015)),
            ),
            strip = true,
        ),
        Line(
            "flywheel", "LIGHT FLYWHEEL", "Less metal to spin. The engine revs freely and every gear pulls harder, but the launch has less swing.",
            listOf(
                Level("Lightened flywheel", "Steel, with the rim skimmed.", 160.0, 1, 1100.0, Fx(flywheel = 0.04, addKg = -3.0)),
                Level("Aluminium flywheel", "Half the inertia. Easy to stall, quick to rev.", 1520.0, 3, 5600.0, Fx(flywheel = 0.07, addKg = -6.0)),
            ),
            strip = true,
        ),
        Line(
            "shifter", "SHORT SHIFTER", "A short throw, and a light that tells you when. Quick, and unforgiving of a slow hand.",
            listOf(
                Level("Short-throw shifter and shift light", "Less throw, a notchy gate, and a light you can set on the Drag tune.", 110.0, 1, 700.0, Fx(shiftMult = 0.88)),
                Level("Air-shifted quick-shift", "An air ram on the lever. The shift is a snap.", 620.0, 4, 2800.0, Fx(shiftMult = 0.75, addKg = 3.0)),
            ),
            strip = true,
        ),
        Line(
            "linelock", "LINE LOCK", "Locks the front brakes so the rears can smoke a burnout and heat the tires.",
            listOf(
                Level("Line lock", "A solenoid on the front brake line. Hot tires grip. It also warms the air in them.", 230.0, 2, 700.0, Fx(burnout = 1.0, addKg = 1.0)),
            ),
            strip = true,
        ),
        Line(
            "nos", "DRAG NITROUS KIT", "A bottle and a solenoid on the strip car. Open it where the tires can take it. It runs dry. Apart from the dyno's nitrous.",
            listOf(
                Level("50 shot", "A small jet and a seven second bottle.", 1500.0, 3, 5200.0, Fx(shot = 0.12, bottleS = 7.0, addKg = 9.0)),
                Level("100 shot", "A bigger jet. It burns the bottle sooner.", 4000.0, 5, 12500.0, Fx(shot = 0.22, bottleS = 6.0, addKg = 10.0)),
                Level("150 shot", "A big plate and a short bottle. It has to go on late.", 9000.0, 6, 28000.0, Fx(shot = 0.32, bottleS = 5.0, addKg = 12.0)),
            ),
            strip = true,
        ),
    )

    /** Circuit lines, then drag lines. */
    val all: List<Line> = lines + strip

    fun line(id: String): Line? = all.firstOrNull { it.id == id }

    fun isLine(id: String): Boolean = all.any { it.id == id }

    fun level(state: GameState, line: String): Int = (state.kit[line] ?: 0).coerceAtLeast(0)

    /** The sum of everything fitted. */
    fun kit(state: GameState): Fx = lines.fold(NONE) { acc, line ->
        val fitted = line.level(level(state, line.id))
        if (fitted == null) acc else acc + fitted.fx
    }

    /** The sum of the drag parts fitted. The strip adds this to [kit]. */
    fun stripKit(state: GameState): Fx = strip.fold(NONE) { acc, line ->
        val fitted = line.level(level(state, line.id))
        if (fitted == null) acc else acc + fitted.fx
    }

    /** The two-line hint a level carries on the parts screen. */
    fun hint(line: Line, level: Int): String {
        val l = line.level(level) ?: return "Stock."
        if (line.strip) return "drag: ${l.dragHint}"
        return "circuit: ${l.circuitHint}  ·  drag: ${l.dragHint}"
    }
}
