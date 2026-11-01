package com.revline.idle.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
) {
    val completed: Int get() = cum.size - 1

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
internal class Replay(val you: ReplayCar, val rival: ReplayCar, val laps: Int, val duration: Double, val retired: Boolean) {
    /** Replay seconds per real second, so a long race still plays in about half a minute. */
    val speed: Double get() = max(1.0, duration / TARGET_S)

    companion object {
        const val TARGET_S = 30.0

        fun build(r: Circuits.Result): Replay? {
            val laps = r.circuit.laps
            if (r.lapTimes.isEmpty()) return null
            val yourCum = DoubleArray(r.lapTimes.size + 1)
            for (i in r.lapTimes.indices) yourCum[i + 1] = yourCum[i] + r.lapTimes[i]
            val partial = r.retired != null
            val pacing = r.pacing
            val youRuns = pacing?.let { p -> runsOf(r.circuit.id, yourCum, partial, partial) { CircuitPace.youSpec(p, it) } }
            val you = ReplayCar(r.driver, "", true, yourCum, partial, youRuns, partial && youRuns != null)
            val plain = (if (r.log.rows.isNotEmpty()) fromLog(r, yourCum) else fromGrid(r, yourCum, laps)) ?: return null
            val rivalRuns = pacing?.let { p -> runsOf(r.circuit.id, plain.cum, false, plain.out) { CircuitPace.rivalSpec(p, plain.name, it) } }
            val rival = ReplayCar(plain.name, plain.team, false, plain.cum, plain.out, rivalRuns)
            val lastLap = yourCum[yourCum.size - 1] - yourCum[yourCum.size - 2]
            val duration = if (r.retired != null) yourCum[yourCum.size - 1] + 0.4 * lastLap + 2.5 else yourCum[yourCum.size - 1] + 3.0
            return Replay(you, rival, laps, duration, r.retired != null)
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

        /** Every car's lap-end times from the log's gaps to the leader, which are tied to the player's own clock. */
        private fun fromLog(r: Circuits.Result, yourCum: DoubleArray): ReplayCar? {
            val rows = r.log.rows
            val usable = rows.filter { it.lap < yourCum.size && it.gapLeader != null }
            if (usable.isEmpty()) return null
            val times = HashMap<String, ArrayList<Double>>()
            val teams = HashMap<String, String>()
            val dead = HashSet<String>()
            for (row in usable) {
                val offset = yourCum[row.lap] - row.gapLeader!!
                for (slot in row.order) {
                    if (slot.you) continue
                    teams[slot.name] = slot.team
                    val gap = slot.gap
                    if (gap == null || slot.name in dead) { dead.add(slot.name); continue }
                    val list = times.getOrPut(slot.name) { arrayListOf(0.0) }
                    if (list.size == row.lap) list.add(offset + gap)
                }
            }
            val best = times.entries
                .filter { it.value.size > 1 }
                .minWithOrNull(compareBy<Map.Entry<String, ArrayList<Double>>> { e ->
                    var sum = 0.0
                    val n = min(e.value.size, yourCum.size) - 1
                    for (lap in 1..n) sum += abs(e.value[lap] - yourCum[lap])
                    // A car that dropped out is a poor rival however close it ran before.
                    (sum / n) + (if (e.key in dead) 1e6 else 0.0)
                }.thenBy { it.key }) ?: return null
            return ReplayCar(best.key, teams[best.key] ?: "", false, best.value.toDoubleArray(), best.key in dead)
        }

        /** With no lap log, the car finishing next to the player, running even laps. */
        private fun fromGrid(r: Circuits.Result, yourCum: DoubleArray, laps: Int): ReplayCar? {
            val mine = r.grid.indexOfFirst { it.you }
            if (mine < 0) return null
            val other = r.grid.getOrNull(if (mine > 0) mine - 1 else mine + 1) ?: return null
            val cum = DoubleArray(laps + 1) { other.time * it / laps }
            return ReplayCar(other.name, other.team, false, if (other.out) cum.copyOf(max(2, laps / 2)) else cum, other.out)
        }
    }
}

internal object ReplayLook {
    /** The rival's chassis, a steady pick from his name so he looks the same every time. */
    fun rivalId(name: String): String = DragLooks.ids.toList()[(name.hashCode() and 0x7fffffff) % DragLooks.ids.size]

    val lime = Color(0xFFE4FF47)
    val red = Color(0xFFFF3B30)
    val ink = Color(0xFF101010)

    /** The two cars at [t] seconds. Both leave the grid side by side and share the lanes when they run close. */
    fun cars(geo: CircuitGeo, replay: Replay, t: Double, yourId: String, yourMid: Boolean): List<CircuitCar> {
        val len = geo.line.length
        val youLaps = replay.you.progress(t)
        val rivalLaps = replay.rival.progress(t)
        val youS = visual(geo, (youLaps * len).toFloat(), gridArc(geo, 0, 1))
        val rivalS = visual(geo, (rivalLaps * len).toFloat(), gridArc(geo, 0, -1))
        val gapCars = abs(youLaps - rivalLaps).toFloat() * len / geo.carUnits
        val close = 1f - smooth(1.3f, 3.5f, gapCars)
        val spread = 0.35f + 0.65f * close
        return listOf(
            CircuitCar(ReplayLook.rivalId(replay.rival.name), false, red, Color.White, rivalS, -geo.laneOffset * spread),
            CircuitCar(yourId, yourMid, lime, ink, youS, geo.laneOffset * spread),
        )
    }

    /** From the grid slot to the line over the first stretch, so nobody jumps at the start. */
    fun visual(geo: CircuitGeo, base: Float, slot: Float): Float {
        val ramp = 0.12f * geo.line.length
        return base + slot * (1f - min(1f, base / ramp))
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/** How fast the film runs and how its clock moves; plain functions so a test can drive them without a screen. */
internal object ReplaySpeed {
    /** Speed chips: multiples of the normal half-minute playback, then LIVE, which is the race at its own pace. */
    val labels = listOf("0.5x", "1x", "2x", "4x", "8x", "LIVE")
    private val multiples = doubleArrayOf(0.5, 1.0, 2.0, 4.0, 8.0)
    const val DEFAULT = 1
    const val LIVE = 5

    /** Film seconds per real second at chip [index]. LIVE runs the race at its real pace, however long it was. */
    fun multiplier(index: Int, replay: Replay): Double =
        if (index == LIVE) 1.0 / replay.speed else multiples[index.coerceIn(0, multiples.size - 1)]

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
@Composable
internal fun CircuitReplayView(result: Circuits.Result, yourId: String, yourMid: Boolean, modifier: Modifier = Modifier) {
    val replay = remember(result) { Replay.build(result) } ?: return
    val scenes = remember { CircuitSceneHolder() }
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
    Column(modifier.fillMaxWidth()) {
        val title = result.circuit.name.substringBefore('-').uppercase(Locale.US)
        val sub = String.format(Locale.US, "%.3f km  %d corners", result.circuit.lapKm, result.circuit.corners)
        Canvas(Modifier.fillMaxWidth().aspectRatio(CircuitLayouts.aspect(result.circuit.id)).padding(top = 4.dp).clipToBounds()) {
            val zoom = if (zoomed) CircuitView.zoomFor(size.width, size.height) else 1f
            val scene = scenes.get(this, result.circuit.id, title, sub, zoom)
            val cars = ReplayLook.cars(scene.geo, replay, t, yourId, yourMid)
            drawCircuitView(scene, cars, size.width, size.height, followYou = zoomed)
        }
        CircuitDash(if (clock < 0f) null else replay.you.dash(t), Modifier.padding(top = 4.dp))
        val state = when {
            clock < 0f -> "ON THE GRID"
            finished && replay.retired -> "OUT"
            finished -> "FINISHED"
            paused -> "PAUSED  LAP $lap of ${replay.laps}"
            else -> "LAP $lap of ${replay.laps}"
        }
        val ahead = youLaps >= rivalLaps
        Text(
            "$state   you vs ${replay.rival.name.substringAfterLast(' ')}  " + if (clock < 0f) "" else if (ahead) "ahead" else "behind",
            color = if (ahead) ReplayLook.lime else ReplayLook.red,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.revline.idle.ui.LineButton(if (paused) "Play" else "Pause", !finished, { paused = !paused })
            com.revline.idle.ui.LineButton("Replay", true, { run++ })
            com.revline.idle.ui.LineButton("Skip to the end", !finished, { clock = ReplaySpeed.endClock(replay) })
            com.revline.idle.ui.LineButton(if (zoomed) "Whole track" else "Follow", true, { zoomed = !zoomed })
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((i, label) in ReplaySpeed.labels.withIndex()) SpeedChip(label, i == speedIndex) { speedIndex = i }
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
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}
