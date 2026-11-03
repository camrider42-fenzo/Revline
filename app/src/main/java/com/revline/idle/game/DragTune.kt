package com.revline.idle.game

import com.revline.idle.data.GameState
import java.util.Locale
import kotlin.math.round

/**
 * The Drag tune: the strip car's knobs. Each one trades something for something, and each is a real term in
 * [RaceDrive.setupFor] or the drive itself. The values live in [GameState.dragTune] (a missing key is the default),
 * and a knob whose part is not on the car reads as its default. This is apart from [Tuners], which tunes the engine.
 *
 *  - tire pressure: lower grips better off the line and drags more at speed.
 *  - final drive: a shorter ratio pulls harder and runs out of speed sooner.
 *  - front and rear stiffness: soft gives grip and weight transfer, and the nose rises (front) or the car squats (rear).
 *  - ride height: lower is less drag and less wheelie, and less grip.
 *  - launch rpm (transbrake): higher hits harder, and spins the tires sooner.
 *  - shift rpm (short shifter): where the pilot changes gear.
 *  - nitrous point (nitrous kit): where the shot opens. Early is wasted on the tires. Late is wasted bottle.
 */
object DragTune {
    data class Knob(
        val id: String,
        val name: String,
        val detail: String,
        val unit: String,
        val min: Double,
        val max: Double,
        val step: Double,
        val default: Double,
        /** Shown when the knob is locked. */
        val lock: String,
        /** The two ends of the trade, one line each, for the page. */
        val low: String,
        val high: String,
        val needs: (GameState) -> Boolean,
    )

    /** The numbers [RaceDrive.setupFor] reads. The defaults are the car as it comes: no change anywhere. */
    data class Rig(
        val psi: Double = 0.0,
        val finalTrim: Double = 0.0,
        val front: Double = 0.0,
        val rear: Double = 0.0,
        val heightMm: Double = 0.0,
        val launchRpmOffset: Double = 0.0,
        val shiftTrimRpm: Double = 0.0,
        val nosAtM: Double = 60.0,
    )

    private fun suspension(s: GameState) = Parts.level(s, "suspension") >= 1

    val all: List<Knob> = listOf(
        Knob(
            "psi", "Tire pressure",
            "Air in the rear tires, off the pressure the tire was built for. A softer tire grows its patch.",
            "psi", -4.0, 4.0, 0.5, 0.0, "Needs a radial tire",
            "More grip off the line, more rolling drag at the top", "Less grip off the line, lower rolling drag",
        ) { it.tire != "bias" },
        Knob(
            "gear", "Final drive",
            "Ratio added to the fitted final drive. A higher number is a shorter gear.",
            "ratio", -0.45, 0.45, 0.05, 0.0, "Always on",
            "Taller: slower to pull, more speed at the end", "Shorter: harder pull, runs out of gear sooner",
        ) { true },
        Knob(
            "front", "Front stiffness",
            "Front springs and dampers, softer to stiffer. A soft front lets the weight come back onto the tires.",
            "clicks", -5.0, 5.0, 1.0, 0.0, "Needs Stiff springs",
            "Soft: more rear grip, the nose comes up sooner", "Stiff: less grip, the nose stays down",
        ) { suspension(it) },
        Knob(
            "rear", "Rear stiffness",
            "Rear springs and dampers. Soft squats the car onto the tires and costs some drive.",
            "clicks", -5.0, 5.0, 1.0, 0.0, "Needs Stiff springs",
            "Soft: more grip, a little drive lost to squat", "Stiff: less grip, no squat",
        ) { suspension(it) },
        Knob(
            "height", "Ride height",
            "The car off the ground, in millimetres from standard.",
            "mm", -20.0, 20.0, 5.0, 0.0, "Needs Stiff springs",
            "Low: less drag, a lower nose, less grip", "High: more grip and more drag",
        ) { suspension(it) },
        Knob(
            "launch", "Launch rpm",
            "Moves the transbrake's hold rpm off the rpm that just fits the tires.",
            "rpm", -1800.0, 800.0, 100.0, 0.0, "Needs a Transbrake",
            "Low: a soft hit that leaves push on the table", "High: a hard hit the tires may not hold",
        ) { Parts.level(it, "tbrake") >= 1 },
        Knob(
            "shift", "Shift rpm",
            "Moves the shift light and the point the driver changes gear.",
            "rpm", -1500.0, 600.0, 100.0, 0.0, "Needs a Short shifter",
            "Early: out of the power band too soon", "Late: on the limiter, in the flat of the curve",
        ) { Parts.level(it, "shifter") >= 1 },
        Knob(
            "nos", "Nitrous point",
            "Where on the strip the shot opens. The bottle runs dry.",
            "m", 0.0, 300.0, 10.0, 60.0, "Needs a Nitrous kit",
            "Early: the best pull on a car the tires hold, wasted on one that already spins", "Late: spares the tires, leaves bottle over at the line",
        ) { Parts.level(it, "nos") >= 1 },
    )

    fun byId(id: String): Knob? = all.firstOrNull { it.id == id }

    fun unlocked(state: GameState, id: String): Boolean = byId(id)?.needs?.invoke(state) ?: false

    private fun snap(knob: Knob, raw: Double): Double {
        val stepped = round((raw - knob.min) / knob.step) * knob.step + knob.min
        return stepped.coerceIn(knob.min, knob.max)
    }

    /** The knob's position. The default while it is locked or has never been set. */
    fun value(state: GameState, id: String): Double {
        val knob = byId(id) ?: return 0.0
        if (!unlocked(state, id)) return knob.default
        return snap(knob, state.dragTune[id] ?: knob.default)
    }

    /** Sets a knob from a slider. A locked knob is left alone. The value snaps to the step. */
    fun set(state: GameState, id: String, raw: Double): GameState {
        val knob = byId(id) ?: return state
        if (!unlocked(state, id)) return state
        return state.copy(dragTune = state.dragTune + (id to snap(knob, raw)))
    }

    fun nudge(state: GameState, id: String, dir: Int): GameState {
        val knob = byId(id) ?: return state
        return set(state, id, value(state, id) + dir * knob.step)
    }

    fun reset(state: GameState): GameState = state.copy(dragTune = emptyMap())

    fun format(knob: Knob, v: Double): String = when (knob.id) {
        "gear" -> String.format(Locale.US, "%+.2f", v)
        "psi" -> String.format(Locale.US, "%+.1f psi", v)
        "height" -> String.format(Locale.US, "%+.0f mm", v)
        "launch", "shift" -> String.format(Locale.US, "%+.0f rpm", v)
        "nos" -> String.format(Locale.US, "%.0f m", v)
        else -> String.format(Locale.US, "%+.0f", v)
    }

    fun rig(state: GameState): Rig = Rig(
        psi = value(state, "psi"),
        finalTrim = value(state, "gear"),
        front = value(state, "front"),
        rear = value(state, "rear"),
        heightMm = value(state, "height"),
        launchRpmOffset = value(state, "launch"),
        shiftTrimRpm = value(state, "shift"),
        nosAtM = value(state, "nos"),
    )

    /** How much a burnout warms this tire. A bias-ply barely does. A slick does most. */
    fun warmth(tireId: String): Double = when (tireId) {
        "radial" -> 0.4
        "race" -> 1.0
        "slick" -> 1.4
        else -> 0.0
    }

    /**
     * Where the nose comes up, in g, for a car as it stands: a short, light, rear-drive car lifts first, and a car
     * driving all four wheels or sitting heavy holds the front down. Front drive does not lift.
     */
    fun baseLift(body: Racing.Car): Double {
        if (body.spec.drive == Racing.Drive.FWD) return 99.0
        val place = when (body.spec.place) {
            Racing.Place.REAR -> 1.9
            Racing.Place.MID -> 2.0
            Racing.Place.FRONT -> 2.15
        }
        val awd = if (body.spec.drive == Racing.Drive.AWD) 0.4 else 0.0
        val light = if (body.spec.chassis.build == Racing.Build.LIGHT) -0.2 else 0.0
        return place + awd + light
    }

    /** The Drag tune page's forecast: a clean pass on this car as tuned. */
    fun forecast(state: GameState): RaceDrive.Clean = RaceDrive.clean(RaceDrive.setup(state))
}
