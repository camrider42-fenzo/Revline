package com.revline.idle.game

import kotlin.math.max
import kotlin.math.min

/**
 * The speed a car runs round a circuit, lap by lap, for the replay and its gauges.
 *
 * It is the same lap solver the sim uses ([Circuits.solve]: tire grip against corner radius, brakes, engine torque through the
 * gears, drag, the shift points) run on the real centerline's radii, so a better brake starts the stop later, better tires
 * carry more speed through the apex, and more power pulls harder on the straights and tops out higher. The sim's lap times
 * stay what they were: each lap's trace is stretched in time ([Run.tau]) so it takes exactly the lap time the race recorded.
 * That keeps the shape the car's stats give it and changes nothing in the results or the economy.
 */
object CircuitPace {
    /** Metres between the speed samples along the real centerline. */
    const val STEP = 10.0

    /** One car's race on this circuit: its car and hands, and for each lap the power left and the tire's worth against its base. */
    data class Entry(
        val car: Circuits.Car,
        val hands: Circuits.Hands,
        val powers: List<Double>,
        val mults: List<Double>,
        /** What each lap takes with nothing going wrong, seconds. */
        val nominal: List<Double>,
    )

    data class RivalEntry(val mults: List<Double>, val nominal: List<Double>, val style: Rivals.Style)

    /**
     * What the race knew about the cars. A rival has no shop car: he is the field's par car on his own tires, with his
     * style's hands ([rivalSpec]), so his shape is a stand-in and only his lap times are the sim's.
     */
    data class Pacing(val you: Entry, val par: Entry?, val rivals: Map<String, RivalEntry>)

    /** What it takes to solve one car's lap: [nominal] is the lap time the sim planned for it. */
    class Spec(val car: Circuits.Car, val hands: Circuits.Hands, val power: Double, val nominal: Double)

    private fun shod(car: Circuits.Car, m: Double) = if (m == 1.0) car else car.copy(setup = car.setup.copy(gripN = car.setup.gripN * m))

    /** Your car on lap [lap] (1 is the first). */
    fun youSpec(p: Pacing, lap: Int): Spec {
        val e = p.you
        val i = (lap - 1).coerceIn(0, e.powers.lastIndex)
        return Spec(shod(e.car, e.mults[i]), e.hands, e.powers[i], e.nominal[i])
    }

    /** [name]'s car on lap [lap], or null when the race did not keep the par car. */
    fun rivalSpec(p: Pacing, name: String, lap: Int): Spec? {
        val par = p.par ?: return null
        val r = p.rivals[name] ?: return null
        if (r.mults.isEmpty() || par.powers.isEmpty()) return null
        val i = (lap - 1).coerceIn(0, r.mults.lastIndex)
        val push = r.style.aggression - 0.3
        val car = shod(par.car.copy(brake = par.car.brake * (1.0 + 0.05 * push)), r.mults[i])
        val h = par.hands
        val hands = h.copy(
            cornering = (h.cornering + 0.12 * push).coerceIn(0.2, 1.0),
            braking = (h.braking + 0.20 * push).coerceIn(0.2, 1.0),
            care = (h.care - 0.20 * push).coerceIn(0.1, 1.0),
        )
        return Spec(car, hands, par.powers[min(i, par.powers.lastIndex)], r.nominal.getOrElse(i) { r.nominal.lastOrNull() ?: 90.0 })
    }

    /** A solved lap: speed, gear and the clock at each of the [n] samples [ds] metres apart. */
    class Trace(val ds: Double, private val sink: Circuits.Sink, val setup: RaceDrive.Setup, val lap: Circuits.Lap) {
        val n: Int get() = sink.v.size
        /** Speed at the start of sample i in m/s. */
        val v: DoubleArray get() = sink.v
        val gear: IntArray get() = sink.gear
        /** The clock at the start of each sample, and the lap time last. */
        val t: DoubleArray get() = sink.t
        val lapTime: Double get() = sink.t[sink.v.size]
        val upAt: DoubleArray get() = sink.upAt
        val ceiling: Double get() = sink.ceiling
        val vEnd: Double get() = sink.vEnd
        val topSpeed: Double get() = sink.v.max()
        val lengthM: Double get() = n * ds

        /** The sample where the speed is lowest within [reach] samples of [near]: the apex of a corner. */
        fun apexNear(near: Int, reach: Int): Int {
            var best = near
            for (d in -reach..reach) {
                val i = Math.floorMod(near + d, n)
                if (sink.v[i] < sink.v[best]) best = i
            }
            return best
        }

        /** Metres from where the car starts to slow for the corner with its apex at sample [apex] to that apex. */
        fun brakeDistance(apex: Int): Double {
            var i = apex
            var steps = 0
            while (steps < n - 1 && sink.v[Math.floorMod(i - 1, n)] > sink.v[Math.floorMod(i, n)] + 1e-6) { i--; steps++ }
            return steps * ds
        }
    }

    /** Solves one lap of the radii (0 on a straight) every [ds] metres. Lap one of a race is a [standing] start. */
    fun trace(spec: Spec, radius: DoubleArray, ds: Double, standing: Boolean = false): Trace =
        trace(spec.car, spec.hands, spec.power, radius, ds, standing)

    fun trace(car: Circuits.Car, hands: Circuits.Hands, power: Double, radius: DoubleArray, ds: Double, standing: Boolean = false): Trace {
        val sink = Circuits.Sink(radius.size)
        val lap = Circuits.solve(car, ds, radius, hands, power, sink, standing)
        return Trace(ds, sink, car.setup, lap)
    }

    /** What the gauges read: [speed] in m/s. */
    class Dash(val speed: Double, val rpm: Double, val gear: Int, val gears: Int, val redline: Double, val idle: Double, val braking: Boolean) {
        val mph: Double get() = speed * 2.23694
    }

    /**
     * A [trace] stretched in time by [tau] (the lap's time over the trace's own), so a lap that took 92 s of race clock is
     * driven in 92 s with the trace's shape. Speeds shown are the trace's over [tau].
     */
    class Run(val trace: Trace, val tau: Double) {
        /** The span of a lap this run covers: the lap time the sim recorded. */
        val span: Double get() = trace.lapTime * tau

        private fun index(p: Double): Int {
            val t = trace.t
            var lo = 0
            var hi = trace.n - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) ushr 1
                if (t[mid] <= p) lo = mid else hi = mid - 1
            }
            return lo
        }

        /** How far round the lap the car is [tin] seconds after the line, 0 to 1. */
        fun fracAt(tin: Double): Double {
            val p = tin / tau
            if (p <= 0.0) return 0.0
            if (p >= trace.lapTime) return 1.0
            val i = index(p)
            val dt = trace.t[i + 1] - trace.t[i]
            val x = p - trace.t[i]
            val v0 = trace.v[i]
            val a = if (dt > 0.0) (nextSpeed(i) - v0) / dt else 0.0
            val d = (v0 * x + 0.5 * a * x * x).coerceIn(0.0, trace.ds)
            return ((i + d / trace.ds) / trace.n).coerceIn(0.0, 1.0)
        }

        private fun nextSpeed(i: Int): Double = if (i + 1 < trace.n) trace.v[i + 1] else trace.vEnd

        /** Speed in m/s [tin] seconds after the line, as the run drives it. */
        fun speedAt(tin: Double): Double {
            val p = (tin / tau).coerceIn(0.0, trace.lapTime)
            val i = index(p)
            val dt = trace.t[i + 1] - trace.t[i]
            val f = if (dt > 0.0) ((p - trace.t[i]) / dt).coerceIn(0.0, 1.0) else 0.0
            return (trace.v[i] + (nextSpeed(i) - trace.v[i]) * f) / tau
        }

        /**
         * The gauges [tin] seconds after the line. The gear starts from the solver's and is moved by its own shift rules
         * for the speed shown, so the rpm is always between idle and the redline.
         */
        fun dash(tin: Double): Dash {
            val st = trace.setup
            val p = (tin / tau).coerceIn(0.0, trace.lapTime)
            val i = index(p)
            val speed = speedAt(tin)
            var gear = trace.gear[i]
            val gears = st.ratios.size
            fun rpmIn(g: Int) = max(speed / RaceDrive.WHEEL_R * st.ratios[g - 1] * st.finalDrive * 9.5493, st.idle * 1.2)
            var rpm = rpmIn(gear)
            while (gear < gears && rpm > trace.upAt[gear - 1]) { gear++; rpm = rpmIn(gear) }
            while (gear > 1 && rpm < st.redline * 0.40) { gear--; rpm = rpmIn(gear) }
            val dt = trace.t[i + 1] - trace.t[i]
            val braking = dt > 0.0 && nextSpeed(i) < trace.v[i] - 0.02
            return Dash(speed, rpm.coerceIn(st.idle, st.redline), gear, gears, st.redline, st.idle, braking)
        }

        /** The gauges of a car standing still: the engine at idle in first. */
        fun parked(): Dash = trace.setup.let { Dash(0.0, it.idle, 1, it.ratios.size, it.redline, it.idle, false) }
    }

    /** The run of one lap that lasted [span] seconds of race clock. [nominal] sets the pace when the car stopped part-way and span is short. */
    fun run(trace: Trace, span: Double, nominal: Double? = null): Run =
        Run(trace, (nominal ?: span).coerceAtLeast(1.0) / trace.lapTime)
}
