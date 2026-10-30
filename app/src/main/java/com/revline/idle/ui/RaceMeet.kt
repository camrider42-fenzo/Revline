package com.revline.idle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revline.idle.economy.formatDollars
import com.revline.idle.game.RaceDrive
import com.revline.idle.game.Racing
import com.revline.idle.game.RevLimiter
import com.revline.idle.model.EngineParams
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Quarter-mile in the same ink as the tach. Feather the launch, then shift.
 * The other car runs the time its pace earned. Your clock is whatever you drove.
 */
@Composable
fun RaceMeet(
    setup: RaceDrive.Setup,
    field: Racing.Field,
    yourPace: Double,
    onFinished: (Boolean, Double, Double) -> Unit,
    crank: EngineCrank,
    params: EngineParams,
    hear: Boolean,
    yourId: String,
    yourMid: Boolean,
    rivalId: String,
) {
    val finish = setup.distanceM
    val hands = remember { Hands(RaceDrive.fresh(setup)) }
    val rivalTime = remember(setup, yourPace, field.pace) {
        RaceDrive.rivalSeconds(setup, yourPace, field.pace)
    }
    var throttle by remember { mutableFloatStateOf(0f) }
    var clutchShown by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableStateOf(Frame(rpm = setup.idle)) }
    var settled by remember { mutableStateOf(false) }
    fun settle(won: Boolean) {
        if (settled) return
        settled = true
        onFinished(won, hands.run.time, rivalTime)
    }
    DisposableEffect(Unit) {
        onDispose { settle(false) }
    }
    LaunchedEffect(setup) {
        var last = 0L
        var countdown = COUNTDOWN_S
        var lights = 1.35f
        val limit = (96.0 * finish / RaceDrive.FINISH_M).coerceIn(48.0, 170.0)
        while (isActive && !settled && hands.run.distance < finish && hands.run.time < limit) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.0 else ((now - last) / 1_000_000_000.0).coerceIn(0.0, 0.05)
                last = now
                if (!hands.clutchHeld) {
                    hands.clutch = RaceDrive.releaseClutch(hands.clutch, dt)
                    clutchShown = hands.clutch.toFloat()
                }
                // Five seconds of count before the tree wakes. Nothing on the strip runs and no clock starts.
                if (countdown > 0f) {
                    countdown -= dt.toFloat()
                    frame = snap(hands.run, lights, rivalTime, finish, done = false, won = false, countdown = countdown)
                    return@withFrameNanos
                }
                // The pedal sticks, so it can be set while the tree is lit. The car waits for green.
                if (lights > 0.0) {
                    lights -= dt.toFloat()
                    frame = snap(hands.run, lights, rivalTime, finish, done = false, won = false)
                    return@withFrameNanos
                }
                val shift = hands.shift && hands.clutch > 0.62
                hands.shift = false
                RaceDrive.step(setup, hands.run, hands.throttle, shift, dt, hands.clutch)
                frame = snap(hands.run, 0f, rivalTime, finish, done = false, won = false)
                if (hear) {
                    val voiceRpm = if (hands.run.limiter) RevLimiter.rpm(setup.redline, hands.run.time) else hands.run.rpm
                    crank.publish(params, voiceRpm.toFloat(), hands.throttle.toFloat(), true, hands.run.limiter)
                }
            }
        }
        if (!settled && isActive) {
            val won = hands.run.distance >= finish && hands.run.time <= rivalTime
            frame = snap(hands.run, 0f, rivalTime, finish, done = true, won = won)
            delay(900)
            settle(won)
        }
    }
    val banner = when {
        frame.done && frame.won -> "CAUGHT THEM"
        frame.done -> "SHORT"
        frame.countdown > 0f -> "STAGE"
        frame.lights > 0.7f -> "READY"
        frame.lights > 0f -> "SET"
        frame.shiftLeft > 0.0 -> "SHIFT"
        frame.spinning -> "SPIN"
        frame.limiter -> "LIMITER"
        else -> "GO"
    }
    val bannerColor = when {
        frame.countdown > 0f || frame.lights > 0f -> Amber
        frame.done && frame.won -> Lime
        frame.done || frame.spinning || frame.limiter -> Red
        frame.shiftLeft > 0.0 -> Amber
        else -> Lime
    }
    val purse = when {
        field.stake > 0.0 && field.purse <= 0.0 -> "The stake is not on the table."
        field.stake > 0.0 -> "Win pays ${formatDollars(field.purse)}. Lose pays nothing."
        else -> "Win pays ${formatDollars(field.purse)}."
    }
    Column(Modifier.fillMaxSize().background(Black).padding(12.dp)) {
        val length = Racing.Length.entries.firstOrNull { kotlin.math.abs(it.meters - finish) < 1.0 }?.title ?: "strip"
        Text("${field.title.uppercase(Locale.US)}  $length", color = White, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
        Text(field.detail, color = White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Text(purse, color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Text(banner, color = bannerColor, fontFamily = FontFamily.Monospace, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
        Canvas(Modifier.fillMaxWidth().height(40.dp).padding(top = 4.dp)) {
            val stroke = 2f
            val lamp = size.minDimension * 0.28f
            val y = size.height * 0.55f
            val xs = listOf(0.32f, 0.44f, 0.56f, 0.68f)
            val colors = listOf(Amber, Amber, Amber, if (frame.done && !frame.won) Red else Lime)
            val lit = listOf(
                frame.lights < 1.2f,
                frame.lights < 0.8f,
                frame.lights < 0.4f,
                frame.lights <= 0f,
            )
            glowLine(Olive, Offset(size.width * 0.22f, y), Offset(size.width * 0.78f, y), stroke, 0.35f)
            if (frame.countdown > 0f) {
                countdownDigit(ceil(frame.countdown).toInt().coerceIn(1, 5), Offset(size.width * 0.5f, y), size.height * 0.34f, stroke)
                return@Canvas
            }
            xs.forEachIndexed { index, at ->
                stageLamp(colors[index], Offset(size.width * at, y), lamp, stroke, lit[index])
            }
        }
        Canvas(Modifier.fillMaxWidth().height(280.dp).padding(top = 4.dp)) {
            val stroke = (size.minDimension / 80f).coerceIn(1.5f, 2.6f)
            val left = 18f
            val right = size.width - 18f
            val span = right - left
            val yourGas = if (frame.lights > 0f) 0.15f else (frame.rpm / setup.redline).toFloat().coerceIn(0.2f, 1f)
            val theirGas = if (frame.lights > 0f) 0.05f else 0.55f
            lane(Red, size.height * 0.46f, left, right, span, frame.rival / finish.toFloat(), frame.rival / 0.33f, false, stroke, rivalId, false, frame.time.toFloat(), theirGas)
            lane(Lime, size.height * 0.92f, left, right, span, (frame.distance / finish).toFloat(), (frame.distance / 0.33).toFloat(), frame.spinning, stroke, yourId, yourMid, frame.time.toFloat(), yourGas)
            gantry(Olive, left, 6f, size.height - 4f, stroke, 0.55f)
            val finishColor = if (frame.done && frame.won) Lime else if (frame.done) Red else White
            gantry(finishColor, right, 6f, size.height - 4f, stroke, 1f)
        }
        val gapM = frame.distance - frame.rival
        Text(
            if (frame.countdown > 0f) "COUNTDOWN" else if (frame.lights > 0f) "LIGHTS" else if (gapM >= 0) "AHEAD ${gapM.toInt()} m" else "BEHIND ${(-gapM).toInt()} m",
            color = if (frame.lights > 0f || frame.countdown > 0f) Olive else if (gapM >= 0) Lime else Red,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Row(Modifier.fillMaxWidth().height(128.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val stroke = (size.minDimension / 70f).coerceIn(1.6f, 3f)
                val c = Offset(size.width * 0.5f, size.height * 0.78f)
                val r = size.minDimension * 0.46f
                val start = 150f
                val sweep = 240f
                val arc = Offset(c.x - r, c.y - r)
                val box = Size(r * 2, r * 2)
                glowArc(White, start, sweep * 0.82f, arc, box, stroke, 0.7f)
                glowArc(Red, start + sweep * 0.82f, sweep * 0.18f, arc, box, stroke * 1.3f, 1.1f)
                for (tick in 0..4) {
                    val frac = tick / 4f * 0.82f
                    val ang = Math.toRadians((start + sweep * frac).toDouble())
                    glowLine(
                        Olive,
                        Offset(c.x + (cos(ang) * r * 0.88).toFloat(), c.y + (sin(ang) * r * 0.88).toFloat()),
                        Offset(c.x + (cos(ang) * r * 1.0).toFloat(), c.y + (sin(ang) * r * 1.0).toFloat()),
                        stroke,
                        0.45f,
                    )
                }
                val shiftAng = Math.toRadians((start + sweep * 0.91f).toDouble())
                glowLine(
                    Lime,
                    Offset(c.x + (cos(shiftAng) * r * 0.84).toFloat(), c.y + (sin(shiftAng) * r * 0.84).toFloat()),
                    Offset(c.x + (cos(shiftAng) * r * 1.04).toFloat(), c.y + (sin(shiftAng) * r * 1.04).toFloat()),
                    stroke * 1.2f,
                    1f,
                )
                val shownRpm = if (frame.limiter) RevLimiter.rpm(setup.redline, frame.time) else frame.rpm
                val frac = (shownRpm / setup.redline).toFloat().coerceIn(0f, 1.08f)
                val ang = Math.toRadians((start + sweep * frac).toDouble())
                val tail = Offset(c.x - (cos(ang) * r * 0.12).toFloat(), c.y - (sin(ang) * r * 0.12).toFloat())
                val tip = Offset(c.x + (cos(ang) * r * 0.78).toFloat(), c.y + (sin(ang) * r * 0.78).toFloat())
                val hot = frame.limiter || frame.rpm > setup.redline * 0.9
                glowLine(if (hot) Red else Lime, tail, tip, stroke * 1.25f, 1.15f)
                glowCircle(if (frame.spinning) Red else Lime, stroke * 2.2f, c, stroke, filled = true)
            }
            Column(Modifier.width(68.dp).fillMaxHeight()) {
                Text("CLUTCH", color = White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                ClutchPedal(
                    clutchShown.toDouble(),
                    onDepression = {
                        hands.clutch = it
                        clutchShown = it.toFloat()
                    },
                    onHeld = { hands.clutchHeld = it },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 2.dp),
                )
            }
            Column(Modifier.width(68.dp).fillMaxHeight()) {
                Text("THROTTLE", color = White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                ThrottlePedal(
                    throttle.toDouble(),
                    enabled = !frame.done,
                    onThrottle = {
                        throttle = it.toFloat()
                        hands.throttle = it
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 2.dp),
                )
            }
        }
        Text(
            "GEAR ${frame.gear}/${setup.ratios.size}   ${frame.rpm.toInt()} rpm   ${(frame.speed * 2.237).toInt()} mph   ${String.format(Locale.US, "%.2f", frame.time)}s",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        val ratio = setup.ratios.getOrElse(frame.gear - 1) { setup.ratios.last() }
        Text(
            "This gear ${String.format(Locale.US, "%.2f", ratio)} × ${String.format(Locale.US, "%.2f", setup.finalDrive)}",
            color = Cyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            hookAdvice(setup) + " Shift at the lime mark. Clutch in, then let it spring back.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        val canShift = frame.countdown <= 0f && frame.lights <= 0f && frame.shiftLeft <= 0.0 && frame.gear < setup.ratios.size && !frame.done && clutchShown > 0.62f
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineButton(
                if (frame.gear >= setup.ratios.size) "TOP" else "SHIFT",
                canShift,
                { hands.shift = true },
                Modifier.weight(1f),
            )
            LineButton("BAIL", !frame.done, { settle(false) }, Modifier.weight(1f))
        }
    }
}

private const val COUNTDOWN_S = 5f

// Seven-segment strokes (top, top-right, bottom-right, bottom, bottom-left, top-left, middle) for 1..5.
private val digitSegments = mapOf(
    1 to "0110000", 2 to "1101101", 3 to "1111001", 4 to "0110011", 5 to "1011011",
)

private fun DrawScope.countdownDigit(digit: Int, center: Offset, half: Float, stroke: Float) {
    val on = digitSegments.getValue(digit)
    val w = half * 0.55f
    val l = center.x - w
    val r = center.x + w
    val t = center.y - half
    val b = center.y + half
    val ends = listOf(
        Offset(l, t) to Offset(r, t), Offset(r, t) to Offset(r, center.y), Offset(r, center.y) to Offset(r, b),
        Offset(l, b) to Offset(r, b), Offset(l, center.y) to Offset(l, b), Offset(l, t) to Offset(l, center.y),
        Offset(l, center.y) to Offset(r, center.y),
    )
    ends.forEachIndexed { i, (from, to) -> if (on[i] == '1') glowLine(Amber, from, to, stroke * 1.4f, 1.15f) }
}

private class Hands(val run: RaceDrive.Run) {
    var throttle: Double = 0.0
    var clutch: Double = 0.0
    var clutchHeld: Boolean = false
    var shift: Boolean = false
}

private data class Frame(
    val lights: Float = 1.35f,
    val countdown: Float = COUNTDOWN_S,
    val gear: Int = 1,
    val rpm: Double = 900.0,
    val speed: Double = 0.0,
    val distance: Double = 0.0,
    val time: Double = 0.0,
    val shiftLeft: Double = 0.0,
    val spinning: Boolean = false,
    val limiter: Boolean = false,
    val rival: Float = 0f,
    val done: Boolean = false,
    val won: Boolean = false,
)

private fun snap(
    run: RaceDrive.Run,
    lights: Float,
    rivalTime: Double,
    finish: Double,
    done: Boolean,
    won: Boolean,
    countdown: Float = 0f,
): Frame = Frame(
    lights = lights.coerceAtLeast(0f),
    countdown = countdown.coerceAtLeast(0f),
    gear = run.gear,
    rpm = run.rpm,
    speed = run.speed,
    distance = run.distance,
    time = run.time,
    shiftLeft = run.shiftLeft,
    spinning = run.spinning,
    limiter = run.limiter,
    rival = (finish * (run.time / rivalTime)).toFloat().coerceAtMost(finish.toFloat()),
    done = done,
    won = won,
)

private fun hookAdvice(setup: RaceDrive.Setup): String {
    val hook = RaceDrive.launchThrottle(setup)
    return if (hook >= 0.99) {
        "Full throttle holds."
    } else {
        "Under ${(hook * 100).toInt()}% the tires hold. Past that they spin."
    }
}

private fun DrawScope.stageLamp(color: Color, center: Offset, radius: Float, stroke: Float, lit: Boolean) {
    if (lit) glowCircle(color, radius, center, stroke, filled = true, gain = 1.15f)
    else glowCircle(Olive, radius, center, stroke, filled = false, gain = 0.4f)
}

private fun DrawScope.gantry(color: Color, x: Float, top: Float, bottom: Float, stroke: Float, gain: Float) {
    glowLine(color, Offset(x, top), Offset(x, bottom), stroke, gain)
    glowLine(color, Offset(x - 7f, top), Offset(x + 7f, top), stroke, gain)
}

private fun DrawScope.lane(
    color: Color,
    ground: Float,
    left: Float,
    right: Float,
    span: Float,
    progress: Float,
    roll: Float,
    spinning: Boolean,
    stroke: Float,
    chassisId: String,
    mid: Boolean,
    time: Float,
    gas: Float,
) {
    glowLine(White, Offset(left, ground), Offset(right, ground), stroke * 0.8f, 0.28f)
    var mark = 0.25f
    while (mark < 1f) {
        val tick = left + span * mark
        glowLine(Olive, Offset(tick, ground - 5f), Offset(tick, ground + 5f), stroke, 0.4f)
        mark += 0.25f
    }
    // Tall enough to read the cabin, short enough that the car is not the whole track.
    val scale = (size.height * 0.13f).coerceIn(48f, 84f)
    val half = Wireframe.length(chassisId) * scale * 0.5f
    val pad = (scale * 0.22f).coerceAtLeast(10f)
    val along = progress.coerceIn(0f, 1f)
    val room = (span - half * 2f - pad * 2f).coerceAtLeast(8f)
    val x = left + half + pad + along * room
    with(Wireframe) {
        draw(chassisId, mid, color, x, ground, scale, roll, spinning, stroke, time, gas)
    }
}
