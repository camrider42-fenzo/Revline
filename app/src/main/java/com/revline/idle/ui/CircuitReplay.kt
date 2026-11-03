package com.revline.idle.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revline.idle.game.CircuitPace
import com.revline.idle.game.Circuits
import com.revline.idle.game.Rivals
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One car's race as times at the end of each lap: [cum] has the lap-0 time (zero) then one entry per lap it finished.
 * With [runs] (one speed trace per lap, stretched to the lap's time) the car is placed by integrating its speed, so it brakes,
 * corners and pulls the way its stats say; without them it is linear inside a lap. [partial] marks a last lap the car did not finish.
 */
internal class ReplayCar(
    val name: String, val team: String, val you: Boolean, val cum: DoubleArray, val out: Boolean,
    val runs: List<CircuitPace.Run>? = null, val partial: Boolean = false,
    /** Place on the starting grid, 1 on pole. */
    val slot: Int = 1,
) {
    val completed: Int get() = cum.size - 1

    /** The lap number (from 1) and the speed trace of the quickest lap this car finished, or null without traces. */
    fun bestLap(): Pair<Int, CircuitPace.Run>? {
        val runs = runs ?: return null
        var best = -1
        for (k in 0 until min(completed, runs.size)) if (best < 0 || cum[k + 1] - cum[k] < cum[best + 1] - cum[best]) best = k
        return if (best < 0) null else (best + 1) to runs[best]
    }

    /** Where a car is on the speed traces: the lap's run and the seconds since its line, or null when it is parked. */
    class Spot(val run: CircuitPace.Run, val tin: Double, val lap: Int, val parked: Boolean)

    private fun spot(t: Double): Spot? {
        val runs = runs ?: return null
        if (runs.isEmpty() || completed == 0 && !out) return null
        if (t <= 0.0) return Spot(runs[0], 0.0, 0, true)
        val last = cum[completed]
        if (t >= last) {
            if (out) {
                if (partial) return Spot(runs[completed - 1], cum[completed] - cum[completed - 1], completed - 1, true)
                val run = runs.getOrNull(completed) ?: return Spot(runs[completed - 1], runs[completed - 1].span, completed - 1, true)
                val cap = run.span * 0.4
                val tin = t - last
                return Spot(run, min(tin, cap), completed, tin >= cap)
            }
            return Spot(runs[completed - 1], t - last, completed, false)
        }
        var k = 0
        while (k < completed - 1 && cum[k + 1] <= t) k++
        return Spot(runs[k], t - cum[k], k, false)
    }

    /** The lap the car is on at [t], from 1. */
    fun lapNumber(t: Double): Int = (spot(t)?.lap ?: 0) + 1

    /** The lap in progress at [t]: its run and how far round it the car is, 0 to 1, or null without traces. */
    fun lapTrace(t: Double): Pair<CircuitPace.Run, Double>? = spot(t)?.let { it.run to it.run.fracAt(it.tin) }

    /** The gauges at [t]: speed, rpm and gear on the trace, or null with no traces. */
    fun dash(t: Double): CircuitPace.Dash? {
        val at = spot(t) ?: return null
        return if (at.parked) at.run.parked() else at.run.dash(at.tin)
    }

    /** How far round the race the car is at [t] seconds, in laps. A car that stopped creeps a bit further and halts. */
    fun progress(t: Double): Double {
        if (t <= 0.0 || completed == 0 && !out) return 0.0
        val at = spot(t)
        if (at != null) return at.lap + at.run.fracAt(at.tin)
        val last = cum[completed]
        val lastLap = if (completed > 0) cum[completed] - cum[completed - 1] else 90.0
        if (t >= last) {
            val extra = (t - last) / lastLap
            return completed + if (out) min(0.4, extra) else extra
        }
        var k = 0
        while (k < completed - 1 && cum[k + 1] <= t) k++
        val span = cum[k + 1] - cum[k]
        return k + if (span > 0.0) (t - cum[k]) / span else 0.0
    }
}

/** The race rebuilt from the result, for a picture. Nothing here feeds back into the sim. */
internal class Replay(
    val you: ReplayCar, val rival: ReplayCar, val laps: Int, val duration: Double, val retired: Boolean,
    /** Every rival in the race, the closest one ([rival]) included. */
    val rivals: List<ReplayCar> = listOf(rival),
) {
    /** Everyone on the track, the player last. */
    val all: List<ReplayCar> get() = rivals + you

    /** Race seconds per film second at the 1x chip: the real pace, so a lap takes the sim's lap time. */
    val speed: Double get() = 1.0

    companion object {
        fun build(r: Circuits.Result): Replay? {
            val laps = r.circuit.laps
            if (r.lapTimes.isEmpty()) return null
            val yourCum = DoubleArray(r.lapTimes.size + 1)
            for (i in r.lapTimes.indices) yourCum[i + 1] = yourCum[i] + r.lapTimes[i]
            val partial = r.retired != null
            val pacing = r.pacing
            val slots = slotsOf(r)
            val youRuns = pacing?.let { p -> runsOf(r.circuit.id, yourCum, partial, partial) { CircuitPace.youSpec(p, it) } }
            val you = ReplayCar(r.driver, "", true, yourCum, partial, youRuns, partial && youRuns != null, slots[r.driver] ?: 1)
            val plain = if (r.log.rows.isNotEmpty()) fieldFromLog(r, yourCum) else fromGrid(r, laps)
            val field = plain.map { c ->
                val runs = pacing?.let { p -> runsOf(r.circuit.id, c.cum, false, c.out) { CircuitPace.rivalSpec(p, c.name, it) } }
                ReplayCar(c.name, c.team, false, c.cum, c.out, runs, false, slots[c.name] ?: c.slot)
            }
            val rival = closest(field, yourCum) ?: return null
            val lastLap = yourCum[yourCum.size - 1] - yourCum[yourCum.size - 2]
            val duration = if (r.retired != null) yourCum[yourCum.size - 1] + 0.4 * lastLap + 2.5 else yourCum[yourCum.size - 1] + 3.0
            return Replay(you, rival, laps, duration, r.retired != null, field)
        }

        /**
         * One speed trace per lap of a car whose lap-end times are [cum], each stretched to the lap's time ([CircuitPace]),
         * or null when the circuit has no centerline or the race kept no car for it. The first lap is a standing start. A last lap
         * the car did not finish ([partial]) or a lap past the last one it finished (a retirement) runs at the pace the sim planned.
         */
        private fun runsOf(id: String, cum: DoubleArray, partial: Boolean, out: Boolean, spec: (Int) -> CircuitPace.Spec?): List<CircuitPace.Run>? {
            if (!CircuitLayouts.has(id)) return null
            val radii = CircuitRadii.of(CircuitLayouts.of(id))
            val finished = cum.size - 1
            val laps = finished + if (out && !partial) 1 else 0
            val runs = ArrayList<CircuitPace.Run>(laps)
            for (k in 0 until laps) {
                val s = spec(k + 1) ?: return null
                val trace = CircuitPace.trace(s, radii.radius, radii.ds, standing = k == 0)
                val planned = k >= finished || partial && k == finished - 1
                runs.add(CircuitPace.run(trace, if (k < finished) cum[k + 1] - cum[k] else s.nominal, if (planned) s.nominal else null))
            }
            return runs
        }

        /** Grid places by name: from qualifying, else the player on pole and the rest as they come. */
        private fun slotsOf(r: Circuits.Result): Map<String, Int> {
            if (r.qualifying.rows.isNotEmpty()) return r.qualifying.rows.associate { it.name to it.slot }
            val names = listOf(r.driver) + r.grid.filter { !it.you }.map { it.name }
            return names.withIndex().associate { (i, n) -> n to i + 1 }
        }

        /** The rival that ran closest to the player, a car that dropped out counting last. */
        private fun closest(field: List<ReplayCar>, yourCum: DoubleArray): ReplayCar? =
            field.filter { it.completed > 0 }.minWithOrNull(compareBy<ReplayCar> { c ->
                var sum = 0.0
                val n = min(c.cum.size, yourCum.size) - 1
                for (lap in 1..n) sum += abs(c.cum[lap] - yourCum[lap])
                (sum / n) + (if (c.out) 1e6 else 0.0)
            }.thenBy { it.name }) ?: field.firstOrNull()

        /** Every rival's lap-end times from the log's gaps to the leader, which are tied to the player's own clock. */
        private fun fieldFromLog(r: Circuits.Result, yourCum: DoubleArray): List<ReplayCar> {
            val usable = r.log.rows.filter { it.lap < yourCum.size && it.gapLeader != null }
            val times = LinkedHashMap<String, ArrayList<Double>>()
            val teams = HashMap<String, String>()
            val dead = HashSet<String>()
            for (row in usable) {
                val offset = yourCum[row.lap] - row.gapLeader!!
                for (slot in row.order) {
                    if (slot.you) continue
                    teams[slot.name] = slot.team
                    val list = times.getOrPut(slot.name) { arrayListOf(0.0) }
                    val gap = slot.gap
                    if (gap == null || slot.name in dead) { dead.add(slot.name); continue }
                    if (list.size == row.lap) list.add(offset + gap)
                }
            }
            return times.map { (name, list) -> ReplayCar(name, teams[name] ?: "", false, list.toDoubleArray(), name in dead) }
        }

        /** With no lap log, every rival running even laps to his finishing time. */
        private fun fromGrid(r: Circuits.Result, laps: Int): List<ReplayCar> = r.grid.filter { !it.you }.map { other ->
            val cum = DoubleArray(laps + 1) { other.time * it / laps }
            ReplayCar(other.name, other.team, false, if (other.out) cum.copyOf(max(2, laps / 2)) else cum, other.out)
        }
    }
}

internal object ReplayLook {
    /** The rival's chassis, a steady pick from his name so he looks the same every time. */
    fun rivalId(name: String): String = DragLooks.ids.toList()[(name.hashCode() and 0x7fffffff) % DragLooks.ids.size]

    val lime = Color(0xFFE4FF47)
    val red = Color(0xFFFF3B30)
    val ink = Color(0xFF101010)

    /** A livery per rival, none of them the player's lime, so the whole field tells apart on the map and the chart. */
    private val liveries = listOf(
        Color(0xFFFF3B30), Color(0xFF4DFFF0), Color(0xFFFF9F1C), Color(0xFFFF4FD8), Color(0xFF4D8DFF), Color(0xFFF2F2F2), Color(0xFFA56BFF),
    )

    /** A rival's colour, steady for his name: the named rivals each have their own, any other name picks from the same set. */
    fun livery(name: String): Color {
        val i = Rivals.all.indexOfFirst { it.name == name }
        return liveries[(if (i >= 0) i else name.hashCode() and 0x7fffffff) % liveries.size]
    }

    fun accent(c: Color): Color = if (c.red * 0.3f + c.green * 0.59f + c.blue * 0.11f > 0.6f) ink else Color.White

    /** Every car at [t] with a fresh [LaneKeeper], settled as a car that has been running a while. For stills and tests. */
    fun cars(geo: CircuitGeo, replay: Replay, t: Double, yourId: String, yourMid: Boolean): List<CircuitCar> =
        LaneKeeper(replay).cars(geo, t, yourId, yourMid)

    /** From the grid slot to the line over the first stretch, so nobody jumps at the start. */
    fun visual(geo: CircuitGeo, base: Float, slot: Float): Float {
        val ramp = 0.12f * geo.line.length
        return base + slot * (1f - min(1f, base / ramp))
    }

    fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/**
 * Puts the cars on the track and keeps each in its lane. A car starts in its grid lane and stays in it; it only changes lane
 * when it comes up level with a car in the same lane, and then it is the one behind that moves over. A car in a bunch keeps its
 * full lane until the road has been clear for a while, then drifts back toward the middle slowly. Every move is a glide, never
 * a jump. It remembers the last frame, so feed it the clock forward; going back or skipping ahead starts it afresh.
 */
internal class LaneKeeper(private val replay: Replay) {
    private val n = replay.all.size
    private val lane = IntArray(n)
    /** Sideways place in lanes: 1 is a full lane to the right, -1 to the left, 0 the middle. */
    private val off = FloatArray(n)
    private val bunched = BooleanArray(n)
    private var last = Double.NaN

    fun cars(geo: CircuitGeo, t: Double, yourId: String, yourMid: Boolean): List<CircuitCar> {
        val len = geo.line.length
        val field = replay.all
        val ramp = 0.12f * len
        val base = FloatArray(n) { (field[it].progress(t) * len).toFloat() }
        val grid = IntArray(n) { if ((field[it].slot - 1) % 2 == 0) 1 else -1 }
        val s = FloatArray(n) { ReplayLook.visual(geo, base[it], gridArc(geo, (field[it].slot - 1) / 2, grid[it])) }
        fun apart(i: Int, j: Int): Float {
            val d = abs(s[i] - s[j]) % len
            return min(d, len - d) / geo.carUnits
        }
        val fresh = last.isNaN() || t < last || t - last > 3.0
        val settle = fresh && t > 1.0
        if (fresh) for (i in 0 until n) { lane[i] = grid[i]; off[i] = grid[i].toFloat(); bunched[i] = false }
        val dt = if (fresh) 0f else (t - last).toFloat()
        last = t
        // The car behind moves over when it comes level with a car in its lane.
        val placed = ArrayList<Int>()
        for (i in (0 until n).sortedWith(compareByDescending<Int> { base[it] }.thenBy { it })) {
            for (j in placed) if (apart(i, j) < CONFLICT && lane[i] == lane[j]) lane[i] = -lane[j]
            placed.add(i)
        }
        for (i in 0 until n) {
            var near = Float.MAX_VALUE
            for (j in 0 until n) if (j != i) near = min(near, apart(i, j))
            // Hysteresis: a car counts as bunched from close in and as clear only from well out.
            if (near < JOIN) bunched[i] = true else if (near > LEAVE) bunched[i] = false
            val target = lane[i] * (if (bunched[i]) 1f else 0.35f)
            if (settle) { off[i] = target; continue }
            val inward = off[i] * target > 0f && abs(target) < abs(off[i])
            val step = (if (inward) DRIFT else SWING) * dt
            off[i] += (target - off[i]).coerceIn(-step, step)
        }
        return (0 until n).sortedBy { field[it].you }.map { i ->
            val onGrid = max(0f, 1f - base[i] / ramp)
            val lat = geo.laneOffset * (grid[i] * onGrid + off[i] * (1f - onGrid))
            val car = field[i]
            if (car.you) CircuitCar(yourId, yourMid, ReplayLook.lime, ReplayLook.ink, s[i], lat)
            else ReplayLook.livery(car.name).let { CircuitCar(ReplayLook.rivalId(car.name), false, it, ReplayLook.accent(it), s[i], lat) }
        }
    }

    private companion object {
        /** Car lengths: level enough to need separate lanes, close enough to count as a bunch, and far enough to count as clear. */
        const val CONFLICT = 3f
        const val JOIN = 2.5f
        const val LEAVE = 5f
        /** Lanes per second: a change of lane, and the slow drift back to the middle. */
        const val SWING = 2.5f
        const val DRIFT = 0.12f
    }
}

/** How fast the film runs and how its clock moves; plain functions so a test can drive them without a screen. */
internal object ReplaySpeed {
    /** Speed chips: multiples of the real pace, where 1x takes the sim's lap time in real seconds. */
    val labels = listOf("0.5x", "1x", "2x", "4x", "8x")
    private val multiples = doubleArrayOf(0.5, 1.0, 2.0, 4.0, 8.0)
    const val DEFAULT = 1

    /** Film seconds per real second at chip [index]. */
    @Suppress("UNUSED_PARAMETER")
    fun multiplier(index: Int, replay: Replay): Double = multiples[index.coerceIn(0, multiples.size - 1)]

    /** The film clock after [dt] real seconds; a paused film stays where it is. The clock counts at the normal rate, [Replay.speed] converts it to race time. */
    fun advance(clock: Float, dt: Float, playing: Boolean, multiplier: Double): Float =
        if (playing) clock + (dt * multiplier).toFloat() else clock

    /** Where the clock stands when the race is over. */
    fun endClock(replay: Replay): Float = (replay.duration / replay.speed).toFloat()

    /** Real seconds until the end at the given multiplier, from film clock [clock]. */
    fun secondsLeft(clock: Float, replay: Replay, multiplier: Double): Double = max(0.0, (endClock(replay) - clock) / multiplier)

    /** Share of the race run by film clock [clock], 0 to 1. */
    fun progress(clock: Float, replay: Replay): Float = ((clock * replay.speed) / replay.duration).toFloat().coerceIn(0f, 1f)
}

/** A short film of the race: you and the closest rival round the circuit, lap by lap. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CircuitReplayView(
    result: Circuits.Result, yourId: String, yourMid: Boolean, modifier: Modifier = Modifier,
    /** Side by side (the map left, the dials, trace and controls right) rather than stacked: for a wide or near-square screen. */
    wide: Boolean = false,
    /** The title bar, put at the top of the stack or of the right-hand column. */
    header: @Composable () -> Unit = {},
    /** What replaces the live trace once the race is over; given a modifier that fits it to its slot. */
    endContent: @Composable (Modifier) -> Unit = {},
) {
    val replay = remember(result) { Replay.build(result) } ?: return
    val scenes = remember { CircuitSceneHolder() }
    val lanes = remember(replay) { LaneKeeper(replay) }
    var clock by remember(result) { mutableFloatStateOf(-1.5f) }
    var run by remember(result) { mutableIntStateOf(0) }
    var finished by remember(result) { mutableStateOf(false) }
    var paused by remember(result) { mutableStateOf(false) }
    // The chosen speed and the close-up survive leaving the screen and rotating.
    var speedIndex by rememberSaveable { mutableIntStateOf(ReplaySpeed.DEFAULT) }
    var zoomed by rememberSaveable { mutableStateOf(false) }
    val speedNow by rememberUpdatedState(speedIndex)
    val pausedNow by rememberUpdatedState(paused)
    LaunchedEffect(result, run) {
        clock = -1.5f
        finished = false
        paused = false
        var last = 0L
        while (isActive && clock * replay.speed < replay.duration) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                last = now
                clock = ReplaySpeed.advance(clock, dt, !pausedNow, ReplaySpeed.multiplier(speedNow, replay))
            }
        }
        clock = ReplaySpeed.endClock(replay)
        finished = true
    }
    val t = max(0.0, clock * replay.speed)
    val youLaps = replay.you.progress(t)
    val rivalLaps = replay.rival.progress(t)
    val lap = min(replay.laps, youLaps.toInt() + 1)
    val place = 1 + replay.rivals.count { it.progress(t) > youLaps }
    val title = result.circuit.name.substringBefore('-').uppercase(Locale.US)
    val sub = String.format(Locale.US, "%.3f km  %d corners", result.circuit.lapKm, result.circuit.corners)
    val map: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().padding(2.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.aspectRatio(CircuitLayouts.aspect(result.circuit.id)).clipToBounds()) {
                val zoom = if (zoomed) CircuitView.zoomFor(size.width, size.height) else 1f
                val scene = scenes.get(this, result.circuit.id, title, sub, zoom)
                val cars = lanes.cars(scene.geo, t, yourId, yourMid)
                drawCircuitView(scene, cars, size.width, size.height, followYou = zoomed)
            }
        }
    }
    val dials: @Composable () -> Unit = {
        Box(Modifier.padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
            CircuitDash(if (clock < 0f) null else replay.you.dash(t), Modifier, fit = true)
        }
    }
    val trace: @Composable () -> Unit = {
        Box(Modifier.padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
            if (finished) endContent(Modifier) else LiveTrace(replay, if (clock < 0f) null else t, Modifier, fit = true)
        }
    }
    val state = when {
        clock < 0f -> "ON THE GRID"
        finished && replay.retired -> "OUT"
        finished -> "FINISHED"
        paused -> "PAUSED  LAP $lap of ${replay.laps}"
        else -> "LAP $lap of ${replay.laps}"
    }
    val ahead = youLaps >= rivalLaps
    val controls: @Composable () -> Unit = {
        Text(
            "$state   P$place of ${replay.all.size}  vs ${replay.rival.name.substringAfterLast(' ')}  " + if (clock < 0f) "" else if (ahead) "ahead" else "behind",
            color = if (ahead) ReplayLook.lime else ReplayLook.red,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        val pause: @Composable () -> Unit = { com.revline.idle.ui.LineButton(if (paused) "Play" else "Pause", !finished, { paused = !paused }) }
        val again: @Composable () -> Unit = { com.revline.idle.ui.LineButton("Replay", true, { run++ }) }
        val skip: @Composable () -> Unit = { com.revline.idle.ui.LineButton("Skip to the end", !finished, { clock = ReplaySpeed.endClock(replay) }) }
        val follow: @Composable () -> Unit = { com.revline.idle.ui.LineButton(if (zoomed) "Whole track" else "Follow", true, { zoomed = !zoomed }) }
        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { pause(); again(); skip(); follow() }
    }
    // The speed chips sit right under the header so they are always on screen, whatever the bars and insets do to the bottom edge.
    val chips: @Composable () -> Unit = {
        FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for ((i, label) in ReplaySpeed.labels.withIndex()) SpeedChip(label, i == speedIndex) { speedIndex = i }
        }
    }
    // Nothing scrolls: every part has a share of the height (or the width) and keeps its own proportions inside it. At the flag
    // the live trace gives way to the best-lap chart, which takes a bigger share, and the map gives up some.
    // Top down: header, speed chips, dials, trace, then the status and buttons right after, with no weighted spacers. The dials and
    // the trace take what they need up to their share of the spare height, so the controls are never pushed off the bottom.
    val slot = Alignment.Center
    if (wide) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            // The map is only as wide as its shape and the height allow (but never more than 55% of the width), so a tall map
            // does not leave a blank strip beside it.
            val mapW = minOf(maxWidth * 0.55f, maxHeight * CircuitLayouts.aspect(result.circuit.id) + 4.dp)
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(mapW).fillMaxHeight()) { map() }
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp)) {
                    header()
                    chips()
                    Box(Modifier.weight(1.1f, fill = false).fillMaxWidth(), contentAlignment = slot) { dials() }
                    Box(Modifier.weight(if (finished) 2.1f else 1f, fill = false).fillMaxWidth(), contentAlignment = slot) { trace() }
                    controls()
                }
            }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            header()
            chips()
            Box(Modifier.weight(if (finished) 1.5f else 2.4f).fillMaxWidth()) { map() }
            Box(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = slot) { dials() }
            Box(Modifier.weight(if (finished) 1.9f else 0.9f, fill = false).fillMaxWidth(), contentAlignment = slot) { trace() }
            controls()
        }
    }
}

@Composable
private fun SpeedChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Lime else Olive
    Text(
        text,
        color = if (selected) Color.Black else color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier
            .background(if (selected) Lime else Color.Transparent)
            .border(BorderStroke(1.dp, color))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
    )
}
