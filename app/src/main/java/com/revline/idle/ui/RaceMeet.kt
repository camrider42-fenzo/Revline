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
import java.util.Locale
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
) {
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
        var lights = 1.35f
        var jumped = false
        while (isActive && !settled && hands.run.distance < RaceDrive.FINISH_M && hands.run.time < 96.0) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.0 else ((now - last) / 1_000_000_000.0).coerceIn(0.0, 0.05)
                last = now
                if (!hands.clutchHeld) {
                    hands.clutch = RaceDrive.releaseClutch(hands.clutch, dt)
                    clutchShown = hands.clutch.toFloat()
                }
                if (lights > 0.0) {
                    lights -= dt.toFloat()
                    if (hands.throttle > 0.18) jumped = true
                    frame = snap(hands.run, lights, jumped, rivalTime, done = false, won = false)
                    return@withFrameNanos
                }
                val asked = if (jumped && hands.run.time < 0.7) 1.0 else hands.throttle
                val shift = hands.shift && hands.clutch > 0.62
                hands.shift = false
                RaceDrive.step(setup, hands.run, asked, shift, dt, hands.clutch)
                frame = snap(hands.run, 0f, jumped, rivalTime, done = false, won = false)
            }
        }
        if (!settled && isActive) {
            val won = hands.run.distance >= RaceDrive.FINISH_M && hands.run.time <= rivalTime
            frame = snap(hands.run, 0f, jumped, rivalTime, done = true, won = won)
            delay(900)
            settle(won)
        }
    }
    val banner = when {
        frame.done && frame.won -> "CAUGHT THEM"
        frame.done -> "SHORT"
        frame.lights > 0.7f -> "READY"
        frame.lights > 0f -> "SET"
        frame.shiftLeft > 0.0 -> "SHIFT"
        frame.jumped && frame.time < 0.7 -> "JUMP"
        frame.spinning -> "SPIN"
        frame.limiter -> "LIMITER"
        else -> "GO"
    }
    val bannerColor = when {
        frame.lights > 0f -> Amber
        frame.done && frame.won -> Lime
        frame.done || frame.spinning || frame.limiter || (frame.jumped && frame.time < 0.7) -> Red
        frame.shiftLeft > 0.0 -> Amber
        else -> Lime
    }
    val purse = when {
        field.stake > 0.0 && field.purse <= 0.0 -> "The stake is not on the table."
        field.stake > 0.0 -> "Win pays ${formatDollars(field.purse)}. Lose pays nothing."
        else -> "Win pays ${formatDollars(field.purse)}."
    }
    Column(Modifier.fillMaxSize().background(Black).padding(12.dp)) {
        Text(field.title.uppercase(Locale.US), color = White, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
        Text(field.detail, color = White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Text(purse, color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Text(banner, color = bannerColor, fontFamily = FontFamily.Monospace, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().height(156.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val stroke = (size.minDimension / 90f).coerceIn(1.6f, 3f)
            val lampY = size.height * 0.08f
            val lamp = size.minDimension * 0.045f
            stageLamp(Olive, Offset(size.width * 0.38f, lampY), lamp, stroke, frame.lights > 0.7f)
            stageLamp(Amber, Offset(size.width * 0.50f, lampY), lamp, stroke, frame.lights > 0f)
            stageLamp(Lime, Offset(size.width * 0.62f, lampY), lamp, stroke, frame.lights <= 0f && !frame.done)
            val c = Offset(size.width * 0.5f, size.height * 0.74f)
            val r = size.minDimension * 0.40f
            val start = 150f
            val sweep = 240f
            glowArc(White, start, sweep * 0.82f, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), stroke, 0.7f)
            glowArc(Red, start + sweep * 0.82f, sweep * 0.18f, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), stroke * 1.3f, 1.1f)
            val frac = (frame.rpm / setup.redline).toFloat().coerceIn(0f, 1.08f)
            val ang = Math.toRadians((start + sweep * frac).toDouble())
            val tip = Offset(c.x + (cos(ang) * r * 0.78).toFloat(), c.y + (sin(ang) * r * 0.78).toFloat())
            val hot = frame.limiter || frame.rpm > setup.redline * 0.9
            glowLine(if (hot) Red else Lime, c, tip, stroke * 1.3f, 1.15f)
            glowCircle(if (frame.spinning) Red else Lime, stroke * 2f, c, stroke, filled = true)
        }
            Column(Modifier.width(72.dp).fillMaxHeight()) {
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
            Column(Modifier.width(72.dp).fillMaxHeight()) {
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
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val y = size.height * 0.62f
            val stroke = 2f
            val left = 12f
            val width = size.width - 24f
            glowLine(White, Offset(left, y), Offset(left + width, y), stroke, 0.4f)
            glowLine(Olive, Offset(left, y - 14f), Offset(left, y + 8f), stroke, 0.7f)
            glowLine(Olive, Offset(left + width, y - 18f), Offset(left + width, y + 8f), stroke, 0.9f)
            val youX = left + (frame.distance / RaceDrive.FINISH_M).toFloat().coerceIn(0f, 1f) * width
            val themX = left + (frame.rival / RaceDrive.FINISH_M.toFloat()).coerceIn(0f, 1f) * width
            carMark(Lime, youX, y, stroke)
            carMark(Red, themX, y - 18f, stroke)
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
            "Throttle stays where you leave it. Clutch springs back in. Press it to shift.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        val canShift = frame.lights <= 0f && frame.shiftLeft <= 0.0 && frame.gear < setup.ratios.size && !frame.done && clutchShown > 0.62f
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

private class Hands(val run: RaceDrive.Run) {
    var throttle: Double = 0.0
    var clutch: Double = 0.0
    var clutchHeld: Boolean = false
    var shift: Boolean = false
}

private data class Frame(
    val lights: Float = 1.35f,
    val gear: Int = 1,
    val rpm: Double = 900.0,
    val speed: Double = 0.0,
    val distance: Double = 0.0,
    val time: Double = 0.0,
    val shiftLeft: Double = 0.0,
    val spinning: Boolean = false,
    val limiter: Boolean = false,
    val jumped: Boolean = false,
    val rival: Float = 0f,
    val done: Boolean = false,
    val won: Boolean = false,
)

private fun snap(
    run: RaceDrive.Run,
    lights: Float,
    jumped: Boolean,
    rivalTime: Double,
    done: Boolean,
    won: Boolean,
): Frame = Frame(
    lights = lights.coerceAtLeast(0f),
    gear = run.gear,
    rpm = run.rpm,
    speed = run.speed,
    distance = run.distance,
    time = run.time,
    shiftLeft = run.shiftLeft,
    spinning = run.spinning,
    limiter = run.limiter,
    jumped = jumped,
    rival = (RaceDrive.FINISH_M * (run.time / rivalTime)).toFloat().coerceAtMost(RaceDrive.FINISH_M.toFloat()),
    done = done,
    won = won,
)

private fun DrawScope.stageLamp(color: Color, center: Offset, radius: Float, stroke: Float, lit: Boolean) {
    if (lit) glowCircle(color, radius, center, stroke, filled = true, gain = 1.1f)
    else glowCircle(Olive, radius, center, stroke, filled = false, gain = 0.45f)
}

private fun DrawScope.carMark(color: Color, x: Float, y: Float, stroke: Float) {
    glowLine(color, Offset(x - 14f, y), Offset(x + 16f, y), stroke, 0.9f)
    glowLine(color, Offset(x - 6f, y), Offset(x - 2f, y - 10f), stroke, 0.9f)
    glowLine(color, Offset(x - 2f, y - 10f), Offset(x + 10f, y - 10f), stroke, 0.9f)
    glowLine(color, Offset(x + 10f, y - 10f), Offset(x + 16f, y), stroke, 0.9f)
    glowCircle(color, 4f, Offset(x - 8f, y + 2f), stroke, filled = true, gain = 0.8f)
    glowCircle(color, 4f, Offset(x + 10f, y + 2f), stroke, filled = true, gain = 0.8f)
}
