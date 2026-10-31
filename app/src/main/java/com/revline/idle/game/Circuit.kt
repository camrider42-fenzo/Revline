package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.economy.formatDollars
import com.revline.idle.model.Block
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Circuit racing, simulated and not drawn. A circuit is a ring of straights and corners. A lap is solved the way a
 * lap-time program does it: every 10 m has a speed ceiling (the corner's grip limit), the car brakes backward from each
 * ceiling as hard as tires and brakes allow, and accelerates forward from each corner as hard as the engine, the gears
 * and the traction allow. The car is the real one from the shop: [RaceDrive.Setup] gives the torque curve, the gear
 * ratios, the final drive, the mass, the grip and the drag. The driver changes how close he gets to those limits.
 * Everything a player fits moves the lap, and each circuit weighs them differently.
 */
object Circuits {
    /** What a circuit can favor. Each is tested by nudging one thing on a reference car, see [emphasis]. */
    enum class Aspect(val title: String) {
        ACCELERATION("ACCELERATION"),
        TOP_SPEED("TOP SPEED"),
        WEIGHT("WEIGHT"),
        CORNERING("CORNERING"),
        BRAKING("BRAKING"),
    }

    class Seg(val corner: Boolean, val metres: Double, val radius: Double, val name: String)

    private fun s(metres: Double) = Seg(false, metres, 0.0, "")
    private fun c(radius: Double, arc: Double, name: String) = Seg(true, arc, radius, name)

    private const val DS = 10.0
    private const val G = 9.81
    private const val SPEED_CAP = 120.0

    /**
     * [lapKm], [corners] and [fullThrottlePct] are the real circuit's published numbers (approximate). The layout is a
     * sketch of its corners in order. The straights are stretched so the lap is exactly the real length.
     * [heat] is how hard the place is on cooling (airflow, air and track temperature). [punish] is how badly a mistake
     * is paid for (walls close and no run-off scale it up).
     */
    class Circuit(
        val id: String,
        val name: String,
        val place: String,
        val lapKm: Double,
        val corners: Int,
        val fullThrottlePct: Int,
        val heat: Double,
        val punish: Double,
        val blurb: String,
        raw: List<Seg>,
    ) {
        val segs: List<Seg>
        val laps: Int = (22.0 / lapKm).roundToInt().coerceIn(3, 6)
        val nodes: Int
        val ds: Double
        /** Corner radius at each 10 m node, 0 on a straight. */
        val radius: DoubleArray
        val cornerAt: Array<String>

        init {
            val cornerM = raw.filter { it.corner }.sumOf { it.metres }
            val straightM = raw.filter { !it.corner }.sumOf { it.metres }
            val stretch = (lapKm * 1000.0 - cornerM) / straightM
            segs = raw.map { if (it.corner) it else Seg(false, it.metres * stretch, 0.0, it.name) }
            val lap = lapKm * 1000.0
            nodes = (lap / DS).roundToInt()
            ds = lap / nodes
            radius = DoubleArray(nodes)
            cornerAt = Array(nodes) { "" }
            var from = 0.0
            var index = 0
            for (seg in segs) {
                val end = from + seg.metres
                while (index < nodes && (index + 0.5) * ds < end) {
                    if (seg.corner) {
                        radius[index] = seg.radius
                        cornerAt[index] = seg.name
                    }
                    index++
                }
                from = end
            }
        }

        val lapMetres: Double get() = lapKm * 1000.0
    }

    val all: List<Circuit> = listOf(
        Circuit(
            "monza", "Monza", "Italy", 5.793, 11, 80, heat = 0.8, punish = 0.8,
            blurb = "The Temple of Speed. Long straights and slow chicanes: power, low drag and big brakes.",
            raw = listOf(
                s(900.0), c(28.0, 60.0, "Rettifilo"), s(150.0), c(320.0, 300.0, "Curva Grande"), s(330.0), c(35.0, 60.0, "Roggia"),
                s(330.0), c(100.0, 120.0, "Lesmo 1"), s(250.0), c(75.0, 110.0, "Lesmo 2"), s(1000.0), c(55.0, 100.0, "Ascari"),
                s(1000.0), c(190.0, 440.0, "Parabolica"),
            ),
        ),
        Circuit(
            "monaco", "Monaco", "Monaco", 3.337, 19, 50, heat = 1.2, punish = 1.8,
            blurb = "Slow, tight and walled in. Light weight, traction, brakes and a calm head. Mistakes cost the race.",
            raw = listOf(
                s(300.0), c(35.0, 60.0, "Sainte Devote"), s(280.0), c(50.0, 70.0, "Massenet"), s(120.0), c(45.0, 80.0, "Casino"),
                s(80.0), c(25.0, 60.0, "Mirabeau"), s(90.0), c(15.0, 50.0, "Hairpin"), s(80.0), c(40.0, 80.0, "Portier"),
                s(520.0), c(30.0, 60.0, "Chicane"), s(90.0), c(50.0, 90.0, "Tabac"), s(120.0), c(60.0, 70.0, "Pool 1"),
                s(40.0), c(50.0, 70.0, "Pool 2"), s(60.0), c(20.0, 60.0, "Rascasse"), s(50.0), c(25.0, 50.0, "Noghes"), s(100.0),
            ),
        ),
        Circuit(
            "silverstone", "Silverstone", "Britain", 5.891, 18, 65, heat = 0.8, punish = 0.7,
            blurb = "Fast sweepers and a fast esses. Grip, balance and high-speed cornering, with two long straights.",
            raw = listOf(
                s(560.0), c(150.0, 120.0, "Abbey"), s(150.0), c(60.0, 90.0, "Village"), s(120.0), c(45.0, 110.0, "The Loop"),
                s(700.0), c(55.0, 90.0, "Brooklands"), s(80.0), c(45.0, 120.0, "Luffield"), s(280.0), c(130.0, 160.0, "Woodcote"),
                s(260.0), c(200.0, 200.0, "Copse"), s(350.0), c(240.0, 180.0, "Maggotts"), s(40.0), c(140.0, 110.0, "Becketts"),
                s(60.0), c(180.0, 140.0, "Chapel"), s(850.0), c(110.0, 110.0, "Stowe"), s(300.0), c(40.0, 90.0, "Vale"),
                s(130.0), c(70.0, 100.0, "Club"),
            ),
        ),
        Circuit(
            "spa", "Spa-Francorchamps", "Belgium", 7.004, 19, 70, heat = 0.7, punish = 1.0,
            blurb = "The longest lap. Power up the hill and through fast, committing corners: Eau Rouge, Pouhon, Blanchimont.",
            raw = listOf(
                s(420.0), c(22.0, 90.0, "La Source"), s(450.0), c(330.0, 180.0, "Eau Rouge"), s(1100.0), c(65.0, 110.0, "Les Combes"),
                s(250.0), c(110.0, 120.0, "Malmedy"), s(160.0), c(55.0, 100.0, "Rivage"), s(250.0), c(140.0, 200.0, "Pouhon"),
                s(500.0), c(90.0, 120.0, "Fagnes"), s(250.0), c(75.0, 110.0, "Stavelot"), s(600.0), c(320.0, 300.0, "Blanchimont"),
                s(250.0), c(35.0, 90.0, "Bus Stop"), s(250.0),
            ),
        ),
        Circuit(
            "hungaroring", "Hungaroring", "Hungary", 4.381, 14, 60, heat = 1.3, punish = 0.8,
            blurb = "A go-kart track in a bowl. Medium and slow corners back to back: cornering and traction, no passing.",
            raw = listOf(
                s(700.0), c(45.0, 110.0, "Turn 1"), s(120.0), c(55.0, 100.0, "Turn 2"), s(100.0), c(110.0, 130.0, "Turn 3"),
                s(300.0), c(70.0, 110.0, "Turn 4"), s(120.0), c(50.0, 90.0, "Turn 6"), s(180.0), c(60.0, 100.0, "Turn 7"),
                s(260.0), c(80.0, 120.0, "Turn 8"), s(160.0), c(40.0, 100.0, "Turn 10"), s(220.0), c(120.0, 140.0, "Turn 11"),
                s(250.0), c(65.0, 110.0, "Turn 12"), s(320.0), c(100.0, 120.0, "Turn 13"), s(250.0), c(55.0, 100.0, "Turn 14"),
            ),
        ),
        Circuit(
            "suzuka", "Suzuka", "Japan", 5.807, 18, 65, heat = 1.0, punish = 1.2,
            blurb = "A figure of eight with everything in it. Fast esses, a hairpin, 130R: a balanced car wins.",
            raw = listOf(
                s(650.0), c(110.0, 140.0, "Turn 1"), s(60.0), c(80.0, 120.0, "Turn 2"), s(40.0), c(130.0, 130.0, "S Curves 1"),
                s(40.0), c(120.0, 120.0, "S Curves 2"), s(50.0), c(130.0, 130.0, "S Curves 3"), s(60.0), c(125.0, 110.0, "Dunlop"),
                s(310.0), c(55.0, 150.0, "Degner 1"), s(150.0), c(50.0, 100.0, "Degner 2"), s(480.0), c(28.0, 100.0, "Hairpin"),
                s(350.0), c(95.0, 150.0, "Spoon 1"), s(60.0), c(110.0, 150.0, "Spoon 2"), s(800.0), c(240.0, 170.0, "130R"),
                s(160.0), c(38.0, 100.0, "Casio"), s(150.0),
            ),
        ),
        Circuit(
            "montreal", "Montreal", "Canada", 4.361, 14, 62, heat = 0.9, punish = 1.3,
            blurb = "Stop and go. Hard acceleration out of hairpins and chicanes, then hard braking, with the Wall of Champions waiting.",
            raw = listOf(
                s(1000.0), c(40.0, 90.0, "Turn 1-2"), s(260.0), c(60.0, 90.0, "Turn 3"), s(310.0), c(45.0, 100.0, "Turn 4-5"),
                s(250.0), c(60.0, 100.0, "Turn 6-7"), s(250.0), c(100.0, 110.0, "Turn 8"), s(380.0), c(18.0, 70.0, "Hairpin"),
                s(1000.0), c(35.0, 90.0, "Wall of Champions"), s(200.0),
            ),
        ),
        Circuit(
            "singapore", "Marina Bay", "Singapore", 4.940, 19, 55, heat = 1.6, punish = 1.6,
            blurb = "A street circuit in a steam bath. Braking and traction, tight walls, and an engine that cooks. Care counts.",
            raw = listOf(
                s(540.0), c(32.0, 60.0, "Turn 1"), s(150.0), c(40.0, 70.0, "Turn 2"), s(100.0), c(45.0, 70.0, "Turn 3"),
                s(340.0), c(30.0, 60.0, "Turn 5"), s(220.0), c(35.0, 60.0, "Turn 7"), s(360.0), c(60.0, 100.0, "Turn 8"),
                s(260.0), c(40.0, 70.0, "Turn 10"), s(380.0), c(30.0, 60.0, "Turn 13"), s(280.0), c(35.0, 60.0, "Turn 14"),
                s(120.0), c(45.0, 70.0, "Turn 16"), s(230.0), c(55.0, 120.0, "Anderson Bridge"), s(220.0), c(38.0, 60.0, "Turn 18"),
                s(230.0), c(70.0, 80.0, "Turn 19"),
            ),
        ),
    )

    fun byId(id: String): Circuit? = all.firstOrNull { it.id == id }

    // ---- The car -------------------------------------------------------------------------------------------------

    /**
     * What the lap needs from the shop. [fade] is the radiator's fade when the engine is hot ([Racing.Car.fade]) and
     * [reliability] is the engine sheet's number (5..99). The last three are nudges for the circuit analysis and the tests.
     */
    data class Car(
        val setup: RaceDrive.Setup,
        val fade: Double = 0.0,
        val reliability: Double = 90.0,
        val brake: Double = 1.0,
        val lateral: Double = 1.0,
        val lowTorque: Double = 1.0,
        /** Extra down-load per kg for each (m/s)^2: aero. */
        val downforce: Double = 0.0,
        /** Grip lost per lap by the tires, as a share, before the circuit's heat. */
        val wear: Double = 0.0,
        /** How often the driver makes a mistake, against his own rate. */
        val mistakes: Double = 1.0,
        /** The engine's chance of letting go, against a stock cooler. */
        val hazard: Double = 1.0,
    )

    /** The car on a circuit: the circuit tire, and everything the race components do there. */
    fun carOf(state: GameState, sheet: Sheet = Logic.sheet(state)): Car {
        val kit = Parts.kit(state)
        val road = Racing.car(state, circuit = true)
        return Car(
            RaceDrive.setup(state, sheet, circuit = true), road.fade, sheet.stats.reliability,
            brake = kit.brake, lateral = kit.lateral, lowTorque = kit.traction, downforce = kit.downforce,
            wear = road.spec.tire.wear * kit.wear, mistakes = kit.mistakes, hazard = kit.hazard,
        )
    }

    /** What a driver does with it. All 0..1, from his [Drivers.Driver] stats and the specialty boost the car earns him. */
    data class Hands(
        val cornering: Double,
        val braking: Double,
        val acceleration: Double,
        val shifting: Double,
        val dyno: Double,
        val care: Double,
        val mistakes: Double,
    )

    fun hands(driver: Drivers.Driver, setup: RaceDrive.Setup): Hands {
        val skill = Drivers.skills(driver, setup)
        return Hands(
            cornering = driver.cornering / 100.0,
            braking = driver.braking / 100.0,
            acceleration = 0.6 * driver.acceleration / 100.0 + 0.4 * skill.throttle,
            shifting = skill.shifting,
            dyno = skill.dyno,
            care = driver.care / 100.0,
            mistakes = driver.mistakes / 100.0,
        )
    }

    // ---- The lap -------------------------------------------------------------------------------------------------

    /** One solved lap. [strain] is engine-seconds at full throttle weighted by how hard the revs were. */
    data class Lap(val time: Double, val topKph: Double, val fullThrottle: Double, val strain: Double)

    private fun cornerFrac(h: Hands) = 0.85 + 0.15 * h.cornering
    private fun brakeFrac(h: Hands) = 0.74 + 0.26 * h.braking
    private fun accelFrac(h: Hands) = 0.86 + 0.14 * h.acceleration
    private fun shiftScale(h: Hands) = 1.5 - 0.8 * h.shifting
    private fun shiftLate(h: Hands) = (1.0 - h.dyno) * 0.05
    private fun revCeiling(h: Hands, st: RaceDrive.Setup) = st.redline * (0.985 - 0.035 * h.care)

    /**
     * Brake ceiling in m/s2: the tires, or the brakes, whichever gives first. A heavy car out-brakes nobody.
     * [down] is the downforce at this speed in m/s2 per kg, which presses the tires harder.
     */
    private fun brakeLimit(mass: Double, mu: Double, down: Double = 0.0) = min(mu * (G + down), 14.5 - 0.0032 * mass).coerceAtLeast(3.0)

    /** [power] is the share of engine power left after heat and wear. */
    fun lap(car: Car, circuit: Circuit, hands: Hands, power: Double = 1.0): Lap {
        val st = car.setup
        val m = st.massKg
        val n = circuit.nodes
        val ds = circuit.ds
        // Lighter cars use their tires better. A fixed contact patch carries a light car above its static grip.
        val sens = (1000.0 / m).pow(0.08)
        val muBase = st.gripN / (m * G) * sens
        val muCorner = muBase * cornerFrac(hands) * car.lateral
        val kBrake = car.brake * brakeFrac(hands)
        val df = car.downforce
        val roll = 0.012 * m * G
        val dragK = 0.5 * 1.22 * st.drag

        // v^2 / r = mu (g + df v^2): downforce lifts the corner's speed ceiling as the speed rises.
        val vLim = DoubleArray(n) { i ->
            val r = circuit.radius[i]
            if (r <= 0.0) SPEED_CAP else if (df <= 0.0) sqrt(muCorner * G * r) else {
                val room = 1.0 / r - muCorner * df
                if (room <= 1e-9) SPEED_CAP else min(SPEED_CAP, sqrt(muCorner * G / room))
            }
        }
        // Backward pass, round the lap twice: the speed you can still brake from.
        val vb = vLim.copyOf()
        repeat(2) {
            for (k in n - 1 downTo 0) {
                val next = vb[(k + 1) % n]
                val r = circuit.radius[k]
                val fc = if (r > 0.0) sqrt((1.0 - (next * next / r / (muBase * (G + df * next * next))).pow(2)).coerceAtLeast(0.0)) else 1.0
                val a = brakeLimit(m, muBase, df * next * next) * kBrake * fc + (dragK * next * next + roll) / m
                val cand = sqrt(next * next + 2.0 * a * ds)
                if (cand < vb[k]) vb[k] = cand
            }
        }

        // Forward pass, twice round: the speed you can still reach.
        val gears = st.ratios.size
        val ceiling = revCeiling(hands, st)
        val late = shiftLate(hands)
        val upAt = DoubleArray(gears) { g -> min(Drivers.optimalShiftRpm(st, g + 1) * (1.0 + late), ceiling) }
        val shiftT = st.shiftSeconds * shiftScale(hands)
        val aFrac = accelFrac(hands)
        var gear = 1
        var v = vb[0]
        var coast = 0.0
        var time = 0.0
        var full = 0.0
        var strain = 0.0
        var top = 0.0
        var lapNo = 0
        while (lapNo < 2) {
            if (lapNo == 1) { time = 0.0; full = 0.0; strain = 0.0; top = 0.0 }
            for (i in 0 until n) {
                var rpm = max(v / RaceDrive.WHEEL_R * st.ratios[gear - 1] * st.finalDrive * 9.5493, st.idle * 1.2)
                while (gear < gears && rpm > upAt[gear - 1]) {
                    gear++
                    coast = shiftT * v
                    rpm = max(v / RaceDrive.WHEEL_R * st.ratios[gear - 1] * st.finalDrive * 9.5493, st.idle * 1.2)
                }
                while (gear > 1 && rpm < st.redline * 0.40) {
                    gear--
                    rpm = max(v / RaceDrive.WHEEL_R * st.ratios[gear - 1] * st.finalDrive * 9.5493, st.idle * 1.2)
                }
                val r = circuit.radius[i]
                val fc = if (r > 0.0) sqrt((1.0 - (v * v / r / (muBase * (G + df * v * v))).pow(2)).coerceAtLeast(0.0)) else 1.0
                var force = 0.0
                var tracted = false
                if (coast > 0.0) {
                    coast -= ds
                } else if (rpm <= ceiling) {
                    val raw = RaceDrive.torqueNm(st, rpm) * st.ratios[gear - 1] * st.finalDrive * st.efficiency / RaceDrive.WHEEL_R *
                        power * aFrac * (if (v < 40.0) car.lowTorque else 1.0)
                    val cap = muBase * m * (G + df * v * v) * fc
                    force = min(raw, cap)
                    tracted = raw > cap
                }
                val a = (force - dragK * v * v - roll) / m
                var vn = sqrt((v * v + 2.0 * a * ds).coerceAtLeast(9.0))
                val cap = vb[(i + 1) % n]
                val limited = vn >= cap
                if (limited) vn = cap
                val dt = 2.0 * ds / (v + vn)
                time += dt
                if (!limited && force > 0.0 && !tracted) {
                    full += ds
                    val x = ((rpm - st.idle) / (st.redline - st.idle)).coerceIn(0.0, 1.0)
                    strain += dt * x * x
                }
                if (vn > top) top = vn
                v = vn
            }
            lapNo++
        }
        return Lap(time, top * 3.6, full / circuit.lapMetres, strain)
    }

    // ---- A race: a plan for the car, then the dice ---------------------------------------------------------------

    /** Chance per engine-strain-second of the engine letting go, for a strong engine and a driver of middling care. */
    private const val HAZARD = 1.7e-4

    /** Seconds the standing start costs every car on the first lap. */
    private const val START = 3.0

    /**
     * What the car and driver do when nothing goes wrong. Heat and wear drag the power down lap by lap. Deterministic:
     * the dice are rolled afterwards in [race].
     */
    class Plan(val circuit: Circuit, val laps: List<Lap>, val failure: List<Double>, val hands: Hands, val mistakes: Double = 1.0) {
        val mean: Double get() = laps.map { it.time }.average()
        val fullThrottle: Double get() = laps.map { it.fullThrottle }.average()
        val top: Double get() = laps.maxOf { it.topKph }
    }

    fun plan(car: Car, circuit: Circuit, driver: Drivers.Driver): Plan {
        val hands = hands(driver, car.setup)
        val weak = ((99.0 - car.reliability) / 94.0).coerceIn(0.0, 1.0)
        val rate = HAZARD * (0.3 + 3.0 * weak * weak) * (1.9 - 1.5 * hands.care) * car.hazard
        val laps = ArrayList<Lap>()
        val failure = ArrayList<Double>()
        var wear = 0.0
        var share = 0.6
        for (k in 1..circuit.laps) {
            val heat = 1.0 - exp(-0.55 * circuit.heat * (k - 0.5) * (share / 0.6))
            val power = (1.0 - car.fade * heat) * (1.0 - min(0.12, 1.5 * wear))
            // Tires go off over the race, faster on a hot track. A level with no wear keeps the same car.
            val worn = car.wear * (0.6 + 0.4 * circuit.heat) * (k - 0.5)
            val shod = if (worn > 0.0) car.copy(setup = car.setup.copy(gripN = car.setup.gripN * (1.0 - worn))) else car
            val lap = lap(shod, circuit, hands, power)
            share = lap.fullThrottle.coerceAtLeast(0.2)
            val hazard = 1.0 - exp(-rate * lap.strain * (1.0 + 0.6 * heat))
            wear += hazard
            laps.add(lap)
            failure.add(hazard)
        }
        return Plan(circuit, laps, failure, hands, car.mistakes)
    }

    data class Finisher(val name: String, val time: Double, val out: Boolean, val you: Boolean = false)

    data class Result(
        val circuit: Circuit,
        val driver: String,
        val position: Int,
        val field: Int,
        val totalTime: Double,
        val bestLap: Double,
        val lapTimes: List<Double>,
        val incidents: List<String>,
        /** Why the race ended early, or null at the flag. */
        val retired: String?,
        val engineFailed: Boolean,
        val grid: List<Finisher>,
        /** Lap by lap: the order, the gaps and what happened. Empty on a result built by hand. */
        val log: RaceLog.Log = RaceLog.Log.EMPTY,
        /** Saturday: the flying lap each car set and the grid it earned. */
        val qualifying: Qualifying = Qualifying.EMPTY,
    )

    /** One car's qualifying lap. [slot] is its place on the grid, 1 on pole. */
    data class QualiRow(val name: String, val time: Double, val slot: Int, val you: Boolean)

    data class Qualifying(val rows: List<QualiRow>) {
        companion object { val EMPTY = Qualifying(emptyList()) }
        val you: QualiRow? get() = rows.firstOrNull { it.you }
        val pole: QualiRow? get() = rows.firstOrNull()
        /** Seconds the player's lap was off pole. */
        val gapToPole: Double get() = (you?.time ?: 0.0) - (pole?.time ?: 0.0)
    }

    /** Seconds each place back from the middle of the grid costs on the first lap, before the circuit's [Circuit.punish]. */
    const val GRID_SLOT = 0.20

    /** The first-lap time a grid slot costs (negative at the front): traffic and no room to pass, worse where mistakes are paid for. */
    fun gridOffset(slot: Int, circuit: Circuit): Double = GRID_SLOT * circuit.punish * (slot - (FIELD + 1) / 2.0)

    /** The seven rivals: how far off the par car each runs (fraction of lap time). Negative is quicker. */
    private val rivals = listOf(
        "Jim Clark" to -0.130,
        "John Surtees" to -0.075,
        "Jochen Rindt" to -0.015,
        "Emerson Fittipaldi" to 0.005,
        "Mario Andretti" to 0.025,
        "Nelson Piquet" to 0.045,
        "Nigel Mansell" to 0.075,
    )

    const val FIELD = 8

    private class Dice(seed: Long) {
        // SplittableRandom mixes its seed; java.util.Random's first draws are nearly equal for neighbouring seeds.
        private val r = java.util.SplittableRandom(seed)
        private val g = java.util.Random(r.nextLong())
        fun next(): Double = r.nextDouble()
        fun gauss(): Double = g.nextGaussian()
        fun poisson(lambda: Double): Int {
            val limit = exp(-lambda)
            var k = 0
            var p = 1.0
            do { k++; p *= r.nextDouble() } while (p > limit && k < 8)
            return k - 1
        }
    }

    private fun slice(circuit: Circuit, dice: Dice): String {
        val named = circuit.segs.filter { it.corner }
        return named[(dice.next() * named.size).toInt().coerceAtMost(named.lastIndex)].name
    }

    /**
     * Runs the race. [par] is the lap the field is built around ([parPlan]). Same inputs and seed, same race.
     */
    fun race(plan: Plan, driver: Drivers.Driver, par: Plan, seed: Long): Result {
        val dice = Dice(seed)
        val circuit = plan.circuit
        val qualiDice = Dice(seed xor 0x2545F4914F6CDD1DL)
        val myName = driver.name.ifEmpty { "You" }
        val mean = par.mean
        val freshness = par.laps.first().time / mean
        val paces = rivals.map { (name, offset) ->
            val aptitude = ((((name.hashCode() * 31 + circuit.id.hashCode()) and 0xFFFF) / 65535.0) - 0.5) * 0.012
            mean * (1.0 + offset + aptitude + 0.004 * qualiDice.gauss())
        }
        val qualiNoise = 0.003 + 0.004 * plan.hands.mistakes
        var myQuali = plan.laps.first().time * (1.0 + qualiNoise * qualiDice.gauss())
        // A scruffy lap: the stat that makes mistakes on Sunday makes them on Saturday too.
        if (qualiDice.next() < 0.04 + 0.16 * plan.hands.mistakes * plan.mistakes) myQuali *= 1.0 + 0.004 + 0.012 * qualiDice.next()
        val qualiTimes = rivals.indices.map { paces[it] * freshness * (1.0 + 0.003 * qualiDice.gauss()) } + myQuali
        val qualiNames = rivals.map { it.first } + myName
        val qualiOrder = qualiTimes.indices.sortedBy { qualiTimes[it] }
        val slots = IntArray(qualiTimes.size).also { arr -> qualiOrder.forEachIndexed { slot, i -> arr[i] = slot + 1 } }
        val qualifying = Qualifying(qualiOrder.map { QualiRow(qualiNames[it], qualiTimes[it], slots[it], it == qualiTimes.lastIndex) })
        val mySlot = slots.last()
        val incidents = ArrayList<String>()
        val times = ArrayList<Double>()
        val notes = ArrayList<RaceLog.Event>()
        notes.add(
            RaceLog.Event(
                1,
                if (mySlot == 1) "You start from pole, ${clock(myQuali)}."
                else "You start P$mySlot, ${clock(myQuali)} in qualifying, +${fmt2(myQuali - qualifying.pole!!.time)}s off pole.",
            ),
        )
        var outLap = 0
        var partial = 0.0
        var total = 0.0
        var retired: String? = null
        var engine = false
        val h = plan.hands
        val lambda = circuit.corners * 0.02 * (0.2 + 1.6 * h.mistakes) * plan.mistakes
        for ((index, base) in plan.laps.withIndex()) {
            val lapNo = index + 1
            if (dice.next() < plan.failure[index]) {
                partial = base.time * dice.next()
                total += partial
                outLap = lapNo
                retired = "Engine let go on lap $lapNo."
                engine = true
                notes.add(RaceLog.Event(lapNo, "Your engine lets go."))
                break
            }
            var lap = base.time * (1.0 + (0.002 + 0.004 * h.mistakes) * dice.gauss())
            if (lapNo == 1) lap += START + gridOffset(mySlot, circuit)
            var crashed = false
            repeat(dice.poisson(lambda)) {
                val spot = slice(circuit, dice)
                val roll = dice.next()
                when {
                    roll < 0.68 -> {
                        val loss = base.time * (0.004 + 0.012 * dice.next())
                        lap += loss
                        incidents.add("Lap $lapNo: ran wide at $spot, lost ${fmt(loss)}s.")
                        notes.add(RaceLog.Event(lapNo, "You run wide at $spot, ${fmt(loss)}s lost."))
                    }
                    dice.next() >= 0.06 * circuit.punish -> {
                        val loss = base.time * (0.03 + 0.04 * dice.next()) * sqrt(circuit.punish)
                        lap += loss
                        incidents.add("Lap $lapNo: spun at $spot, lost ${fmt(loss)}s.")
                        notes.add(RaceLog.Event(lapNo, "You spin at $spot, ${fmt(loss)}s lost."))
                    }
                    else -> {
                        crashed = true
                        retired = "Hit the wall at $spot on lap $lapNo."
                        incidents.add("Lap $lapNo: hit the wall at $spot.")
                        notes.add(RaceLog.Event(lapNo, "You hit the wall at $spot."))
                    }
                }
            }
            if (crashed) {
                partial = lap * dice.next()
                total += partial
                outLap = lapNo
                break
            }
            times.add(lap)
            total += lap
        }
        val finished = retired == null
        val best = (if (times.size > 1) times.drop(1) else times).minOrNull() ?: 0.0
        val grid = ArrayList<Finisher>()
        val runs = ArrayList<RaceLog.CarRun>()
        rivals.forEachIndexed { i, (name, offset) ->
            val pace = paces[i]
            val launch = START + gridOffset(slots[i], circuit)
            var t = launch
            var out = false
            val laps = circuit.laps
            val lapTimes = ArrayList<Double>()
            var outOn = 0
            val dnfLap = if (dice.next() < 0.04) 1 + (dice.next() * laps).toInt().coerceAtMost(laps - 1) else 0
            for (lapNo in 1..laps) {
                if (lapNo == dnfLap) {
                    val part = pace * dice.next()
                    t += part
                    lapTimes.add(part + if (lapNo == 1) launch else 0.0)
                    out = true
                    outOn = lapNo
                    break
                }
                val lt = pace * (1.0 + 0.003 * dice.gauss())
                t += lt
                lapTimes.add(lt + if (lapNo == 1) launch else 0.0)
            }
            grid.add(Finisher(name, t, out))
            runs.add(RaceLog.CarRun(name, "", false, lapTimes, outOn))
        }
        grid.add(Finisher(myName, total, !finished, true))
        runs.add(RaceLog.CarRun(myName, "", true, if (outLap > 0) times + partial else times.toList(), outLap))
        val order = grid.sortedWith(compareBy<Finisher> { it.out }.thenBy { it.time })
        val place = order.indexOfFirst { it.you } + 1
        val log = RaceLog.build(runs, circuit.laps, notes)
        return Result(circuit, myName, place, grid.size, total, best, times, incidents, retired, engine, order, log, qualifying)
    }

    private fun fmt2(x: Double) = String.format(Locale.US, "%.2f", x)
    private fun fmt(x: Double) = String.format(Locale.US, "%.1f", x)

    // ---- The field scales with progression -----------------------------------------------------------------------

    private val parStates = ConcurrentHashMap<Int, Car>()
    private val parPlans = ConcurrentHashMap<String, Plan>()

    /**
     * The car the field is built around at this point of the game: a medium chassis with the stock bits (radial tires,
     * limited slip, close-ratio box) on a block at 60% of the upgrades, race-tuned. Beat it with parts and hands.
     */
    fun parCar(prestiges: Int): Car = parStates.getOrPut(prestiges.coerceIn(0, Block.entries.lastIndex)) {
        val p = prestiges.coerceIn(0, Block.entries.lastIndex)
        val body = Racing.groupChassis(p, Racing.Build.MEDIUM)
        // The mpg-only parts do nothing on a circuit; leaving them out keeps the par car where it was before they existed.
        val all = Upgrades.all.filter { it.id !in Logic.NEW_ECONOMY_IDS }
        val state = GameState(
            prestiges = p,
            knowledge = p,
            racing = true,
            researched = ResearchTree.all.map { it.id }.toSet(),
            upgrades = all.take((all.size * 0.6).toInt()).associate { it.id to it.maxLevel },
            chassis = body.id,
            chassisLevel = mapOf(body.id to 2),
            coolingSlots = Racing.slotMax(body, 2),
            transmission = "close",
            driveline = "lsd",
            tire = "radial",
            circuitTire = "street",
            raceStudies = Racing.studies.map { it.id }.toSet(),
        )
        carOf(state)
    }

    fun parPlan(prestiges: Int, circuit: Circuit): Plan =
        parPlans.getOrPut("${prestiges.coerceIn(0, Block.entries.lastIndex)}:${circuit.id}") {
            plan(parCar(prestiges), circuit, Drivers.you)
        }

    // ---- Money ---------------------------------------------------------------------------------------------------

    /**
     * Prize for each of the top five finishers, as a multiple of the entry fee. Sixth place and below, and a retirement,
     * get nothing. Set so the mean of a par street car, a balanced build and a power build takes home what the 1.71.2
     * top-three table (4.0, 2.8, 2.2) paid, with the par car paid for its usual fourth and fifth.
     */
    val prizes = listOf(3.5, 2.4, 1.8, 1.4, 1.0)

    /** The pool the driver's fee is taken from, in entries. Kept at the old winner's multiple so fees did not move. */
    private const val FEE_PURSE = 3.2

    fun entry(state: GameState): Double = if (state.devMode) 0.0 else 0.5 * Racing.stakeCost(state.prestiges)

    /** What first place pays. */
    fun winnerPurse(state: GameState): Double = prizes[0] * 0.5 * Racing.stakeCost(state.prestiges)

    /** What [position] (1-based) pays. Only the top five are paid. */
    fun prize(state: GameState, position: Int, retired: Boolean): Double =
        if (state.devMode || retired) 0.0 else 0.5 * Racing.stakeCost(state.prestiges) * prizes.getOrElse(position - 1) { 0.0 }

    /** The paying places, first to last, in dollars. */
    fun podium(state: GameState): List<Double> = (1..prizes.size).map { prize(state, it, false) }

    /** A circuit seat costs less than a strip seat: the driver takes this share of his usual rate. */
    const val FEE_SHARE = 0.7

    /** A driver is paid a share of the fee purse, whatever he finishes. */
    fun fee(state: GameState, driver: Drivers.Driver): Double =
        if (state.devMode) 0.0 else FEE_SHARE * driver.rate * FEE_PURSE * 0.5 * Racing.stakeCost(state.prestiges)

    /** The bill when the engine lets go: the rebuild, a quarter of the entry. */
    fun repair(state: GameState): Double = 0.25 * entry(state)

    sealed class Entry {
        data class Refused(val note: String) : Entry()
        /** The till after entry and fee, before the race pays. */
        data class Paid(val state: GameState, val driver: Drivers.Driver, val fee: Double, val entry: Double) : Entry()
    }

    const val HIRE_NOTE = "Hire a driver to enter circuit races"

    /**
     * The driver who takes the wheel in a circuit race: always a hired one, never the player. [preferredId] is the
     * circuit picker's choice; when it is blank, stale or no longer hired, it is the first hired driver. Null if none is hired.
     */
    fun entrant(state: GameState, preferredId: String = ""): Drivers.Driver? {
        val hired = Drivers.hired(state)
        return hired.firstOrNull { it.id == preferredId } ?: hired.firstOrNull()
    }

    /** Takes the entry fee and the driver's fee at the start. Nothing is refunded. Needs a hired driver. */
    fun open(state: GameState, preferredId: String = ""): Entry {
        val driver = entrant(state, preferredId) ?: return Entry.Refused("$HIRE_NOTE.")
        val entry = entry(state)
        val fee = fee(state, driver)
        if (!state.devMode && state.money < entry + fee) {
            return Entry.Refused(
                if (fee > 0.0) "${driver.name} wants ${formatDollars(fee)} on top of the ${formatDollars(entry)} entry. The till is short."
                else "The entry is ${formatDollars(entry)}. The till is short.",
            )
        }
        return Entry.Paid(state.copy(money = state.money - entry - fee), driver, fee, entry)
    }

    data class Outcome(val result: Result, val prize: Double, val repair: Double, val fee: Double, val entry: Double, val note: String)

    /** Runs the race for a paid entry and works out what comes back. [state] is the car and the field's level, as at the start. */
    fun finish(state: GameState, car: Car, circuit: Circuit, driver: Drivers.Driver, fee: Double, entry: Double, seed: Long): Outcome {
        val result = race(plan(car, circuit, driver), driver, parPlan(state.prestiges, circuit), seed)
        val prize = prize(state, result.position, result.retired != null)
        val repair = if (result.engineFailed) repair(state) else 0.0
        return Outcome(result, prize, repair, fee, entry, note(result, prize, repair, fee, entry))
    }

    fun note(r: Result, prize: Double, repair: Double, fee: Double, entry: Double): String {
        val head = if (r.retired != null) "${r.circuit.name}: DNF. ${r.retired}"
        else "${r.circuit.name}: P${r.position} of ${r.field}, ${clock(r.totalTime)}, best lap ${clock(r.bestLap)}."
        val money = buildString {
            append(" Entry ${formatDollars(entry)}")
            if (fee > 0.0) append(", driver ${formatDollars(fee)}")
            append(". Prize ${formatDollars(prize)}")
            if (repair > 0.0) append(", engine rebuild ${formatDollars(repair)}")
            append(".")
        }
        return head + money
    }

    /** The grid, pole first: "1. Jim Clark 1:21.45", then each car's gap to pole. */
    fun gridLines(q: Qualifying): List<String> = q.rows.map { r ->
        "${r.slot}. ${if (r.you) "YOU (${r.name})" else r.name}  ${clock(r.time)}" +
            if (r.slot == 1) "  pole" else "  +${String.format(Locale.US, "%.2f", r.time - q.rows.first().time)}"
    }

    fun clock(seconds: Double): String {
        val m = (seconds / 60.0).toInt()
        return if (m > 0) String.format(Locale.US, "%d:%05.2f", m, seconds - m * 60.0) else String.format(Locale.US, "%.2fs", seconds)
    }

    // ---- What a circuit favors -----------------------------------------------------------------------------------

    private val gains = ConcurrentHashMap<String, Map<Aspect, Double>>()

    private fun nudged(car: Car, aspect: Aspect): Car = when (aspect) {
        Aspect.ACCELERATION -> car.copy(lowTorque = 1.12)
        Aspect.TOP_SPEED -> car.copy(setup = car.setup.copy(drag = car.setup.drag * 0.85))
        Aspect.WEIGHT -> car.copy(setup = car.setup.copy(massKg = car.setup.massKg * 0.9, gripN = car.setup.gripN * 0.9))
        Aspect.CORNERING -> car.copy(lateral = 1.05)
        Aspect.BRAKING -> car.copy(brake = 1.10)
    }

    private fun gain(circuit: Circuit): Map<Aspect, Double> = gains.getOrPut(circuit.id) {
        val car = parCar(3)
        val hands = hands(Drivers.you, car.setup)
        val base = lap(car, circuit, hands).time
        Aspect.entries.associateWith { (base - lap(nudged(car, it), circuit, hands).time) / base }
    }

    /**
     * How much each aspect is worth here, as the lap time a fixed nudge saves on a reference car, relative to the
     * average of all circuits for that same nudge. 1.0 is an average circuit, 2.0 pays twice as much.
     */
    fun emphasis(circuit: Circuit): Map<Aspect, Double> {
        val mine = gain(circuit)
        return Aspect.entries.associateWith { a ->
            val mean = all.map { gain(it)[a] ?: 0.0 }.average()
            if (mean <= 0.0) 0.0 else (mine[a] ?: 0.0) / mean
        }
    }

    /** The two aspects this circuit pays most for, most first. */
    fun favors(circuit: Circuit): List<Aspect> = emphasis(circuit).entries.sortedByDescending { it.value }.take(2).map { it.key }

    /** Your lap against the field's par car, in percent of a lap. Positive is quicker. Mid-stat hands both sides. */
    fun fitPercent(car: Car, circuit: Circuit, prestiges: Int, driver: Drivers.Driver): Double {
        val mine = plan(car, circuit, driver).mean
        val par = parPlan(prestiges, circuit).mean
        return (par - mine) / par * 100.0
    }

    /** Below this, a build is not on the same page as the field and a percentage only confuses. */
    const val FIT_FLOOR = -25.0

    /** The fit line for the circuit screen. A hopeless build reads as far off the pace, not as a huge negative number. */
    fun fitText(fit: Double): String =
        if (!fit.isFinite() || fit < FIT_FLOOR) "Your build here: far off the pace of the field's par car"
        else String.format(java.util.Locale.US, "Your build here: %+.1f%% a lap against the field's par car", fit)
}
