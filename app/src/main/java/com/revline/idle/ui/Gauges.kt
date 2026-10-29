package com.revline.idle.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revline.idle.model.OperatingPoint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun Tachometer(rpm: Double, redline: Double, targetRpm: Double, modifier: Modifier = Modifier) {
    val settle: AnimationSpec<Float> = if (LocalBatterySaver.current) snap() else tween(durationMillis = 180)
    val shown by animateFloatAsState(rpm.toFloat(), settle, label = "tach")
    val mark by animateFloatAsState(targetRpm.toFloat(), settle, label = "mark")
    Canvas(modifier) {
        val stroke = (size.minDimension / 80f).coerceIn(1.7f, 3.2f)
        val c = Offset(size.width / 2f, size.height * 0.62f)
        val r = min(size.width, size.height) * 0.42f
        val start = 150.0
        val sweep = 240.0
        val scale = (kotlin.math.ceil(redline / 1000.0) * 1000.0).coerceAtLeast(redline + 200.0)
        val arcSize = Size(r * 2, r * 2)
        val arcTop = Offset(c.x - r, c.y - r)
        glowArc(White, start.toFloat(), sweep.toFloat(), arcTop, arcSize, stroke, 0.75f)
        val redFrac = (redline / scale).toFloat().coerceIn(0f, 1f)
        glowArc(
            Red,
            (start + sweep * redFrac).toFloat(),
            (sweep * (1.0 - redFrac)).toFloat(),
            arcTop,
            arcSize,
            stroke * 1.35f,
            1.15f,
        )
        val ticks = (scale / 1000.0).toInt().coerceAtLeast(1)
        for (i in 0..ticks) {
            val f = i / ticks.toFloat()
            val a = Math.toRadians(start + sweep * f)
            val inner = r * 0.82f
            val color = if (i * 1000.0 >= redline) Red else White
            glowLine(
                color,
                Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
                Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()),
                stroke,
                0.8f,
            )
        }
        val markFrac = (mark / scale).coerceIn(0.0, 1.0)
        val ma = Math.toRadians(start + sweep * markFrac)
        glowCircle(
            Cyan,
            stroke * 1.35f,
            Offset(c.x + (cos(ma) * r * 0.90f).toFloat(), c.y + (sin(ma) * r * 0.90f).toFloat()),
            stroke,
            filled = true,
            gain = 1.1f,
        )
        val frac = (shown / scale).coerceIn(0.0, 1.0)
        val na = Math.toRadians(start + sweep * frac)
        val tip = Offset(c.x + (cos(na) * r * 0.78f).toFloat(), c.y + (sin(na) * r * 0.78f).toFloat())
        val hot = shown >= redline.toFloat()
        glowLine(if (hot) Red else Lime, c, tip, stroke * 1.35f, 1.2f)
        glowCircle(if (hot) Red else Lime, stroke * 2f, c, stroke, filled = true)
        val label = shown.toInt().toString()
        drawContext.canvas.nativeCanvas.drawText(
            label,
            c.x,
            c.y + r * 0.42f,
            Paint().apply {
                color = (if (hot) Red else Lime).toArgb()
                textAlign = Paint.Align.CENTER
                textSize = r * 0.20f
                typeface = Typeface.MONOSPACE
                isAntiAlias = true
            },
        )
    }
}

@Composable
fun DynoChart(curve: List<OperatingPoint>, rpm: Double, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(
        rpm.toFloat(),
        if (LocalBatterySaver.current) snap() else tween(durationMillis = 180),
        label = "cursor",
    )
    Column(modifier) {
        val peakHp = curve.maxOfOrNull { it.horsepower } ?: 0.0
        val peakTq = curve.maxOfOrNull { it.torqueLbFt } ?: 0.0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("HP ${formatPower(peakHp)}", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text("TQ ${formatPower(peakTq)}", color = Amber, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text("CURSOR", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(150.dp),
        ) {
            if (curve.size < 2) return@Canvas
            val stroke = (size.minDimension / 120f).coerceIn(1.5f, 2.8f)
            val left = 8f
            val right = size.width - 8f
            val top = 8f
            val bottom = size.height - 8f
            glowLine(White, Offset(left, top), Offset(left, bottom), stroke, 0.45f)
            glowLine(White, Offset(left, bottom), Offset(right, bottom), stroke, 0.45f)
            val minRpm = curve.first().rpm
            val maxRpm = curve.last().rpm
            val maxHp = curve.maxOf { it.horsepower }.coerceAtLeast(1.0)
            val maxTq = curve.maxOf { it.torqueLbFt }.coerceAtLeast(1.0)
            fun x(r: Double) = left + ((r - minRpm) / (maxRpm - minRpm)).toFloat() * (right - left)
            fun yHp(v: Double) = bottom - (v / maxHp).toFloat() * (bottom - top)
            fun yTq(v: Double) = bottom - (v / maxTq).toFloat() * (bottom - top)
            for (i in 1 until curve.size) {
                val a = curve[i - 1]
                val b = curve[i]
                glowLine(Lime, Offset(x(a.rpm), yHp(a.horsepower)), Offset(x(b.rpm), yHp(b.horsepower)), stroke, 1.05f)
                glowLine(Amber, Offset(x(a.rpm), yTq(a.torqueLbFt)), Offset(x(b.rpm), yTq(b.torqueLbFt)), stroke, 1.05f)
            }
            val cx = x(shown.toDouble().coerceIn(minRpm, maxRpm))
            glowLine(Cyan, Offset(cx, top), Offset(cx, bottom), stroke, 1.15f)
        }
    }
}

@Composable
fun ThrottlePedal(
    throttle: Double,
    enabled: Boolean,
    onThrottle: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown by animateFloatAsState(
        targetValue = throttle.toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 80),
        label = "pedal",
    )
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun apply(y: Float) {
                            onThrottle((y / size.height).toDouble().coerceIn(0.0, 1.0))
                        }
                        apply(down.position.y)
                        drag(down.id) { change ->
                            apply(change.position.y)
                            change.consume()
                        }
                    }
                },
        ) {
            val color = when {
                !enabled -> Olive
                shown > 0.92f -> Red
                shown > 0.55f -> Amber
                else -> Lime
            }
            pedalPlate(shown, color)
        }
        Text(
            when {
                !enabled -> "ROLL"
                shown < 0.015f -> "STALL"
                else -> "${(shown * 100).toInt()}%"
            },
            color = if (enabled) White else Olive,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
        )
    }
}

/**
 * Same plate as the throttle. Travel sticks while the foot is down.
 * Letting go springs it back to engaged, which is the top of the slot.
 */
@Composable
fun ClutchPedal(
    depression: Double,
    onDepression: (Double) -> Unit,
    onHeld: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown by animateFloatAsState(
        targetValue = depression.toFloat().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 80),
        label = "clutch",
    )
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onHeld(true)
                        try {
                            fun apply(y: Float) {
                                onDepression((y / size.height).toDouble().coerceIn(0.0, 1.0))
                            }
                            apply(down.position.y)
                            drag(down.id) { change ->
                                apply(change.position.y)
                                change.consume()
                            }
                        } finally {
                            onHeld(false)
                        }
                    }
                },
        ) {
            val color = when {
                shown < 0.08f -> Lime
                shown < 0.7f -> Amber
                else -> Red
            }
            pedalPlate(shown, color)
        }
        Text(
            when {
                shown < 0.04f -> "IN"
                shown > 0.85f -> "OUT"
                else -> "${(shown * 100).toInt()}%"
            },
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.pedalPlate(shown: Float, color: androidx.compose.ui.graphics.Color) {
    val w = size.width
    val h = size.height
    val stroke = (w / 26f).coerceIn(1.6f, 3.2f)
    glowLine(White, Offset(w * 0.22f, h * 0.06f), Offset(w * 0.22f, h * 0.94f), stroke, 0.45f)
    glowLine(White, Offset(w * 0.78f, h * 0.06f), Offset(w * 0.78f, h * 0.94f), stroke, 0.45f)
    val plate = h * 0.10f + shown * (h * 0.68f)
    glowLine(color, Offset(w * 0.10f, plate), Offset(w * 0.90f, plate + h * 0.07f), stroke * 1.6f, 0.55f + shown)
    glowLine(color, Offset(w * 0.90f, plate + h * 0.07f), Offset(w * 0.78f, plate + h * 0.16f), stroke * 1.3f, 0.7f + shown * 0.4f)
    glowLine(color, Offset(w * 0.78f, plate + h * 0.16f), Offset(w * 0.22f, plate + h * 0.16f), stroke * 1.3f, 0.7f + shown * 0.4f)
    glowLine(color, Offset(w * 0.22f, plate + h * 0.16f), Offset(w * 0.10f, plate), stroke * 1.3f, 0.7f + shown * 0.4f)
    glowCircle(White, stroke * 1.5f, Offset(w * 0.5f, h * 0.93f), stroke, filled = true, gain = 0.7f)
}

fun signed(value: Double, digits: Int = 1): String {
    val step = 0.5 * Math.pow(10.0, -digits.toDouble())
    if (kotlin.math.abs(value) < step) return "0"
    val body = String.format(Locale.US, "%.${digits}f", kotlin.math.abs(value))
    return if (value > 0.0) "+$body" else "-$body"
}

/** Horsepower and torque. Small engines need the fraction; a V8 does not. */
fun formatPower(value: Double): String = when {
    kotlin.math.abs(value) < 10.0 -> String.format(Locale.US, "%.2f", value)
    kotlin.math.abs(value) < 100.0 -> String.format(Locale.US, "%.1f", value)
    else -> String.format(Locale.US, "%.0f", value)
}

fun powerDigits(value: Double): Int = when {
    kotlin.math.abs(value) < 10.0 -> 2
    kotlin.math.abs(value) < 100.0 -> 1
    else -> 0
}

/** Digits that keep a small step visible next to a large number. */
fun stepDigits(now: Double, delta: Double): Int = when {
    kotlin.math.abs(delta) >= 10.0 -> 0
    kotlin.math.abs(delta) >= 1.0 && kotlin.math.abs(now) >= 20.0 -> 1
    kotlin.math.abs(now) >= 100.0 && kotlin.math.abs(delta) >= 1.0 -> 0
    else -> 2
}
