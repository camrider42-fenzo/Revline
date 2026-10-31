package com.revline.idle.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
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
import com.revline.idle.game.Circuits
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One car's race as times at the end of each lap: [cum] has the lap-0 time (zero) then one entry per lap it finished. */
internal class ReplayCar(val name: String, val team: String, val you: Boolean, val cum: DoubleArray, val out: Boolean) {
    val completed: Int get() = cum.size - 1

    /** How far round the race the car is at [t] seconds, in laps. Linear inside a lap. A car that stopped creeps a bit further and halts. */
    fun progress(t: Double): Double {
        if (t <= 0.0 || completed == 0 && !out) return 0.0
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
            val you = ReplayCar(r.driver, "", true, yourCum, r.retired != null)
            val rival = (if (r.log.rows.isNotEmpty()) fromLog(r, yourCum) else fromGrid(r, yourCum, laps)) ?: return null
            val lastLap = yourCum[yourCum.size - 1] - yourCum[yourCum.size - 2]
            val duration = if (r.retired != null) yourCum[yourCum.size - 1] + 0.4 * lastLap + 2.5 else yourCum[yourCum.size - 1] + 3.0
            return Replay(you, rival, laps, duration, r.retired != null)
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

/** A short film of the race: you and the closest rival round the circuit, lap by lap. */
@Composable
internal fun CircuitReplayView(result: Circuits.Result, yourId: String, yourMid: Boolean, modifier: Modifier = Modifier) {
    val replay = remember(result) { Replay.build(result) } ?: return
    val scenes = remember { CircuitSceneHolder() }
    var clock by remember(result) { mutableFloatStateOf(-1.5f) }
    var run by remember(result) { mutableIntStateOf(0) }
    var playing by remember(result) { mutableStateOf(true) }
    LaunchedEffect(result, run) {
        clock = -1.5f
        playing = true
        var last = 0L
        while (isActive && clock * replay.speed < replay.duration) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
                last = now
                clock += dt
            }
        }
        clock = (replay.duration / replay.speed).toFloat()
        playing = false
    }
    val t = max(0.0, clock * replay.speed)
    val youLaps = replay.you.progress(t)
    val rivalLaps = replay.rival.progress(t)
    val lap = min(replay.laps, youLaps.toInt() + 1)
    Column(modifier.fillMaxWidth()) {
        val title = result.circuit.name.substringBefore('-').uppercase(Locale.US)
        val sub = String.format(Locale.US, "%.3f km  %d corners", result.circuit.lapKm, result.circuit.corners)
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f).padding(top = 4.dp)) {
            val scene = scenes.get(this, result.circuit.id, title, sub)
            drawCircuitScene(scene, ReplayLook.cars(scene.geo, replay, t, yourId, yourMid))
        }
        val state = when {
            clock < 0f -> "ON THE GRID"
            playing -> "LAP $lap of ${replay.laps}"
            replay.retired -> "OUT"
            else -> "FINISHED"
        }
        val ahead = youLaps >= rivalLaps
        Text(
            "$state   you vs ${replay.rival.name.substringAfterLast(' ')}  " + if (clock < 0f) "" else if (ahead) "ahead" else "behind",
            color = if (ahead) ReplayLook.lime else ReplayLook.red,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.revline.idle.ui.LineButton("Replay", true, { run++ })
            com.revline.idle.ui.LineButton("Skip to the end", playing, { clock = (replay.duration / replay.speed).toFloat() })
        }
    }
}
