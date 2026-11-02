package com.revline.idle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import com.revline.idle.game.CircuitPace
import kotlin.math.min
import kotlin.math.roundToInt

/** Width over height of the dash. */
internal const val DASH_ASPECT = 2.5f

/** The player's car on the race screen: tachometer, speedometer in mph (the unit of the drag screen), gear and a brake lamp. [dash] is null before the start. */
@Composable
internal fun CircuitDash(dash: CircuitPace.Dash?, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().aspectRatio(DASH_ASPECT)) { drawCircuitDash(dash) }
}

/** The dash of [CircuitDash], drawn into whatever canvas it is handed. */
internal fun DrawScope.drawCircuitDash(dash: CircuitPace.Dash?) {
    drawRect(Color.Black)
    val h = size.height
    val redline = dash?.redline ?: 8000.0
    val rpm = dash?.rpm ?: dash?.idle ?: 0.0
    val hot = dash != null && dash.rpm >= redline * 0.975
    // The dial takes the left square, the readouts the rest.
    inset(0f, 0f, size.width - h, 0f) { drawTach(rpm.toFloat(), redline, (redline * 0.9).toFloat(), hot) }
    val x0 = h * 1.02f
    val w = size.width - x0
    // The speed: big, right aligned, with its unit.
    val mph = dash?.mph?.roundToInt() ?: 0
    textLabel("SPEED", x0 + w * 0.05f, h * 0.20f, h * 0.10f, Olive, 0)
    textLabel(mph.toString(), x0 + w * 0.62f, h * 0.60f, h * 0.44f, if (hot) Red else Lime, 2)
    textLabel("MPH", x0 + w * 0.64f, h * 0.60f, h * 0.12f, Olive, 0)
    // The gear and the brake lamp, along the bottom.
    val gearText = when {
        dash == null || dash.speed < 0.5 -> "N"
        else -> dash.gear.toString()
    }
    val boxTop = h * 0.70f
    val boxH = h * 0.24f
    val boxW = w * 0.20f
    val gx = x0 + w * 0.05f
    drawRect(Lime.copy(alpha = 0.10f), Offset(gx, boxTop), Size(boxW, boxH))
    drawRect(Lime, Offset(gx, boxTop), Size(boxW, boxH), style = Stroke(width = (h / 90f).coerceAtLeast(1.5f)))
    textLabel(gearText, gx + boxW / 2f, boxTop + boxH * 0.78f, boxH * 0.80f, Lime, 1)
    textLabel("GEAR", gx + boxW + w * 0.03f, boxTop + boxH * 0.72f, h * 0.10f, Olive, 0)
    val braking = dash?.braking == true
    val lx = x0 + w * 0.60f
    drawRect(if (braking) Red else Red.copy(alpha = 0.12f), Offset(lx, boxTop), Size(w * 0.34f, boxH))
    textLabel("BRAKE", lx + w * 0.17f, boxTop + boxH * 0.68f, boxH * 0.52f, if (braking) Color.Black else Red.copy(alpha = 0.5f), 1)
}

/** The session's best laps: each car's speed in mph against the distance round its quickest lap. Draws nothing without traces. */
@Composable
internal fun BestLapTraces(replay: Replay, modifier: Modifier = Modifier) {
    val laps = replay.bestLaps()
    if (laps.isEmpty()) return
    Canvas(modifier.fillMaxWidth().aspectRatio(if (laps.size > 2) 1.5f else 2.1f)) { drawBestLaps(laps) }
}

/** Every car's quickest lap with a trace, in the car's livery. */
internal fun Replay.bestLaps(): List<BestLap> = all.mapNotNull { c ->
    c.bestLap()?.let { BestLap(c.name, c.you, it.first, it.second, if (c.you) Lime else ReplayLook.livery(c.name)) }
}

internal class BestLap(val name: String, val you: Boolean, val lap: Int, val run: CircuitPace.Run, val color: Color = if (you) Lime else Red)

/** The gridlines and labels of a speed chart: mph up the side every 50, a kilometre mark along the bottom. */
private fun DrawScope.drawSpeedAxes(left: Float, right: Float, top: Float, bottom: Float, len: Double, vMax: Double, u: Float, tick: Float) {
    fun x(m: Double) = left + (right - left) * (m / len).toFloat()
    fun y(mph: Double) = bottom - (bottom - top) * (mph / vMax).toFloat()
    val grid = Color(0xFF2A3010)
    var g = 0.0
    while (g <= vMax) {
        drawLine(grid, Offset(left, y(g)), Offset(right, y(g)), 1.5f)
        textLabel(g.toInt().toString(), left - 1f * u, y(g) + tick * 0.35f, tick, Olive, 2)
        g += 50.0
    }
    for (km in 0..(len / 1000.0).toInt()) {
        drawLine(grid, Offset(x(km * 1000.0), top), Offset(x(km * 1000.0), bottom), 1.5f)
        textLabel("$km km", x(km * 1000.0), bottom + tick * 1.5f, tick, Olive, 1)
    }
}

internal fun DrawScope.drawBestLaps(laps: List<BestLap>) {
    drawRect(Color.Black)
    val u = size.width / 100f
    val left = 9f * u
    val right = size.width - 2f * u
    val legendRows = (laps.size + 1) / 2
    val top = (if (laps.size > 2) 8f + 4.1f * legendRows else 20f) * u
    val bottom = size.height - 9f * u
    val len = laps.maxOf { it.run.trace.lengthM }
    val tick = 3.4f * u
    fun x(m: Double) = left + (right - left) * (m / len).toFloat()
    val peak = laps.maxOf { l -> l.run.trace.v.max() / l.run.tau * 2.23694 }
    val vMax = (kotlin.math.ceil(peak / 50.0) * 50.0).coerceAtLeast(100.0)
    fun y(mph: Double) = bottom - (bottom - top) * (mph / vMax).toFloat()
    drawSpeedAxes(left, right, top, bottom, len, vMax, u, tick)
    textLabel("BEST LAP SPEED (mph)", left, tick * 1.3f, tick * 1.1f, Lime, 0)
    // The player's line is full and thick; the rest are thin and faded, drawn under it.
    for (l in laps.sortedBy { it.you }) {
        val t = l.run.trace
        val path = androidx.compose.ui.graphics.Path()
        for (k in 0..t.n) {
            val px = x(k * t.ds)
            val py = y(t.v[k % t.n] / l.run.tau * 2.23694)
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        val strong = l.you || laps.size <= 2
        drawPath(path, if (strong) l.color else l.color.copy(alpha = 0.6f), style = Stroke(width = if (l.you) 3.4f else if (strong) 2.6f else 1.6f))
    }
    // Names and lap times, quickest first, in two columns that follow the livery colours.
    val sorted = laps.sortedBy { it.run.span }
    if (laps.size <= 2) sorted.forEachIndexed { i, l -> legend(l, left + 1f * u, tick * 2.7f + i * tick * 1.2f, tick) }
    else sorted.forEachIndexed { i, l -> legend(l, left + 1f * u + (i % 2) * 44f * u, tick * 2.7f + (i / 2) * tick * 1.2f, tick * 0.8f) }
}

private fun DrawScope.legend(l: BestLap, x: Float, baseline: Float, tick: Float) {
    val name = l.name.substringAfterLast(' ')
    textLabel("$name  L${l.lap}  ${com.revline.idle.game.Circuits.clock(l.run.span)}", x, baseline, tick, if (l.you) l.color else l.color.copy(alpha = 0.8f), 0)
}

/**
 * The speed trace of the lap in progress, drawn as the car runs it (mph against distance round the lap) and wiped at the line.
 * The player's is bold; the followed rival's is faint underneath. [t] is the race clock, null before the start.
 */
@Composable
internal fun LiveTrace(replay: Replay, t: Double?, modifier: Modifier = Modifier) {
    if (replay.you.runs == null) return
    val vMax = remember(replay) { liveCeiling(replay) }
    Canvas(modifier.fillMaxWidth().aspectRatio(3.2f)) {
        val you = t?.let { replay.you.lapTrace(it) }
        val rival = t?.let { replay.rival.lapTrace(it) }
        drawLiveTrace(you, rival, vMax, ReplayLook.livery(replay.rival.name), (t?.let { replay.you.lapNumber(it) } ?: 1))
    }
}

/** The chart's top: the fastest any car goes on any lap, to the next 50 mph, so the axis stays put from lap to lap. */
internal fun liveCeiling(replay: Replay): Double {
    val peak = replay.all.maxOf { c -> c.runs?.maxOfOrNull { it.trace.v.max() / it.tau * 2.23694 } ?: 0.0 }
    return (kotlin.math.ceil(peak / 50.0) * 50.0).coerceAtLeast(100.0)
}

internal fun DrawScope.drawLiveTrace(you: Pair<CircuitPace.Run, Double>?, rival: Pair<CircuitPace.Run, Double>?, vMax: Double, rivalColor: Color, lap: Int) {
    drawRect(Color.Black)
    val u = size.width / 100f
    val left = 9f * u
    val right = size.width - 2f * u
    val top = 8f * u
    val bottom = size.height - 9f * u
    val tick = 3.4f * u
    val len = (you ?: rival)?.first?.trace?.lengthM ?: 5000.0
    fun x(m: Double) = left + (right - left) * (m / len).toFloat()
    fun y(mph: Double) = bottom - (bottom - top) * (mph / vMax).toFloat()
    drawSpeedAxes(left, right, top, bottom, len, vMax, u, tick)
    textLabel("SPEED THIS LAP (mph)  LAP $lap", left, tick * 1.3f, tick * 1.1f, Lime, 0)
    fun line(c: Pair<CircuitPace.Run, Double>, color: Color, width: Float) {
        val (run, frac) = c
        val tr = run.trace
        val reach = frac.coerceIn(0.0, 1.0) * tr.n
        val whole = reach.toInt()
        if (whole <= 0 && reach <= 0.0) return
        val path = androidx.compose.ui.graphics.Path()
        fun mph(k: Int) = tr.v[k % tr.n] / run.tau * 2.23694
        path.moveTo(x(0.0), y(mph(0)))
        for (k in 1..min(whole, tr.n)) path.lineTo(x(k * tr.ds), y(mph(k)))
        if (whole < tr.n && reach > whole) {
            val a = mph(whole)
            val b = mph(whole + 1)
            path.lineTo(x(reach * tr.ds), y(a + (b - a) * (reach - whole)))
        }
        drawPath(path, color, style = Stroke(width = width))
    }
    rival?.let { line(it, rivalColor.copy(alpha = 0.35f), 1.6f) }
    you?.let { line(it, Lime, 3.2f) }
}
