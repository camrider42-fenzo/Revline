package com.revline.idle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.revline.idle.model.EngineParams
import com.revline.idle.model.Induction
import com.revline.idle.model.Mixture
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun EngineArt(
    params: EngineParams,
    rpm: Float,
    coolantC: Float,
    egtC: Float,
    modifier: Modifier = Modifier,
) {
    val rpmState = rememberUpdatedState(rpm)
    var angle by remember { mutableFloatStateOf(20f) }
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    clock += dt
                    angle = (angle + rpmState.value * 0.12f * dt) % 360f
                }
                last = now
            }
        }
    }
    val hose = when {
        coolantC >= 120f -> Red
        coolantC >= 105f -> Amber
        else -> Cyan
    }
    val exhaust = if (egtC >= 900f) Red else Amber
    Canvas(
        modifier.semantics {
            contentDescription = "Line-art inline-four, coolant ${coolantC.toInt()} degrees, ${rpm.toInt()} rpm"
        },
    ) {
        val w = size.width
        val h = size.height
        val stroke = (w / 180f).coerceIn(1.6f, 3.2f)
        val pen = Stroke(width = stroke, cap = StrokeCap.Round)
        val n = 4
        val left = w * 0.30f
        val right = w * 0.86f
        val gap = (right - left) / n
        val crankY = h * 0.73f
        val crankThrow = h * 0.055f
        val rod = h * 0.155f
        val phases = floatArrayOf(0f, 180f, 180f, 0f)

        val radRows = when {
            params.radiatorUa > 1600.0 -> 4
            params.radiatorUa > 1200.0 -> 3
            params.radiatorUa > 950.0 -> 2
            else -> 1
        }
        val radW = w * (0.055f + 0.012f * radRows)
        val radL = w * 0.05f
        val radR = radL + radW
        val radT = h * 0.22f
        val radB = h * 0.62f
        drawLine(White, Offset(radL, radT), Offset(radR, radT), stroke)
        drawLine(White, Offset(radR, radT), Offset(radR, radB), stroke)
        drawLine(White, Offset(radR, radB), Offset(radL, radB), stroke)
        drawLine(White, Offset(radL, radB), Offset(radL, radT), stroke)
        for (i in 1 until radRows + 2) {
            val x = radL + radW * i / (radRows + 2f)
            drawLine(Cyan, Offset(x, radT + 4f), Offset(x, radB - 4f), stroke * 0.7f)
        }
        if (params.intercoolerEff > 0.05) {
            val icL = w * 0.012f
            val icR = radL - 4f
            drawLine(Cyan, Offset(icL, radT + 8f), Offset(icR, radT + 8f), stroke)
            drawLine(Cyan, Offset(icR, radT + 8f), Offset(icR, radT + h * 0.16f), stroke)
            drawLine(Cyan, Offset(icR, radT + h * 0.16f), Offset(icL, radT + h * 0.16f), stroke)
            drawLine(Cyan, Offset(icL, radT + h * 0.16f), Offset(icL, radT + 8f), stroke)
        }
        if (params.oilCoolerUa > 10.0) {
            val y = radB + h * 0.05f
            drawLine(Amber, Offset(radL, y), Offset(radR, y), stroke)
            drawLine(Amber, Offset(radR, y), Offset(radR, y + h * 0.08f), stroke)
            drawLine(Amber, Offset(radR, y + h * 0.08f), Offset(radL, y + h * 0.08f), stroke)
            drawLine(Amber, Offset(radL, y + h * 0.08f), Offset(radL, y), stroke)
        }

        drawLine(White, Offset(left - 6f, h * 0.40f), Offset(right, h * 0.40f), stroke)
        drawLine(White, Offset(left - 6f, h * 0.33f), Offset(right - 8f, h * 0.33f), stroke)
        drawLine(White, Offset(left - 6f, h * 0.33f), Offset(left - 6f, h * 0.40f), stroke)
        drawLine(White, Offset(left, crankY + h * 0.06f), Offset(right, crankY + h * 0.06f), stroke)
        drawLine(White, Offset(left, h * 0.40f), Offset(left, crankY + h * 0.06f), stroke)

        val headY = h * 0.36f
        when {
            params.individualThrottles -> {
                for (i in 0 until n) {
                    val cx = left + gap * (i + 0.5f)
                    drawLine(Lime, Offset(cx, h * 0.18f), Offset(cx, headY), stroke)
                    drawLine(Lime, Offset(cx - 6f, h * 0.18f), Offset(cx + 6f, h * 0.18f), stroke)
                }
            }
            params.mixture == Mixture.CARB -> {
                drawLine(Lime, Offset(left, headY), Offset(right - 20f, headY), stroke * 0.8f)
                val cx = (left + right) / 2f
                drawLine(Lime, Offset(cx, headY), Offset(cx, h * 0.20f), stroke)
                drawLine(Lime, Offset(cx - 16f, h * 0.20f), Offset(cx + 16f, h * 0.20f), stroke)
                drawLine(Lime, Offset(cx - 16f, h * 0.20f), Offset(cx - 16f, h * 0.14f), stroke)
                drawLine(Lime, Offset(cx + 16f, h * 0.20f), Offset(cx + 16f, h * 0.14f), stroke)
                drawLine(Lime, Offset(cx - 16f, h * 0.14f), Offset(cx + 16f, h * 0.14f), stroke)
            }
            else -> {
                drawLine(Lime, Offset(left + 8f, h * 0.24f), Offset(right - 16f, h * 0.24f), stroke)
                for (i in 0 until n) {
                    val cx = left + gap * (i + 0.5f)
                    drawLine(Lime, Offset(cx, h * 0.24f), Offset(cx, headY), stroke * 0.8f)
                }
                if (params.mixture == Mixture.DIRECT) {
                    for (i in 0 until n) {
                        val cx = left + gap * (i + 0.5f)
                        drawLine(Lime, Offset(cx, h * 0.33f), Offset(cx, h * 0.40f), stroke * 0.7f)
                    }
                }
            }
        }
        if (params.methFraction > 0.0) {
            drawLine(Cyan, Offset(left + gap, h * 0.16f), Offset(left + gap, h * 0.24f), stroke)
            drawCircle(Cyan, stroke * 1.4f, Offset(left + gap, h * 0.15f), style = Stroke(stroke))
        }

        val exhY = h * 0.37f
        if (params.backpressure < 0.78f) {
            for (i in 0 until n) {
                val cx = left + gap * (i + 0.5f)
                drawLine(exhaust, Offset(cx, h * 0.40f), Offset(right + w * 0.02f, exhY), stroke * 0.75f)
            }
        } else {
            drawLine(exhaust, Offset(left, exhY), Offset(right, exhY), stroke)
        }

        if (params.induction == Induction.TURBO || params.induction == Induction.TWIN_TURBO) {
            val count = if (params.induction == Induction.TWIN_TURBO) 2 else 1
            for (t in 0 until count) {
                val cx = right + w * 0.06f
                val cy = h * (0.34f + t * 0.12f)
                val rad = h * if (count == 2) 0.045f else 0.06f
                drawCircle(Cyan, rad, Offset(cx, cy), style = Stroke(stroke))
                val spin = Math.toRadians((-(angle * 4f) + t * 40f).toDouble())
                for (k in 0 until 4) {
                    val a = spin + k * Math.PI / 2.0
                    drawLine(
                        White,
                        Offset(cx, cy),
                        Offset(cx + (cos(a) * rad * 0.75f).toFloat(), cy + (sin(a) * rad * 0.75f).toFloat()),
                        stroke * 0.7f,
                    )
                }
            }
        }
        if (params.induction == Induction.SUPERCHARGER) {
            drawLine(Lime, Offset(left + w * 0.08f, h * 0.28f), Offset(left + w * 0.28f, h * 0.28f), stroke)
            drawLine(Lime, Offset(left + w * 0.28f, h * 0.28f), Offset(left + w * 0.28f, h * 0.36f), stroke)
            drawLine(Lime, Offset(left + w * 0.28f, h * 0.36f), Offset(left + w * 0.08f, h * 0.36f), stroke)
            drawLine(Lime, Offset(left + w * 0.08f, h * 0.36f), Offset(left + w * 0.08f, h * 0.28f), stroke)
            drawLine(White, Offset(left - 4f, crankY), Offset(left + w * 0.08f, h * 0.32f), stroke * 0.7f)
        }

        drawLine(hose, Offset(radR, radT + 6f), Offset(left, h * 0.34f), stroke * 0.85f)
        val pumpY = crankY
        drawLine(hose, Offset(radR, radB - 6f), Offset(left, pumpY), stroke * 0.85f)
        if (!params.electricPump) {
            drawCircle(White, stroke * 2.2f, Offset(left - 2f, pumpY), style = Stroke(stroke))
        } else {
            drawLine(Cyan, Offset(left - 10f, pumpY - 8f), Offset(left + 6f, pumpY - 8f), stroke)
            drawLine(Cyan, Offset(left + 6f, pumpY - 8f), Offset(left + 6f, pumpY + 8f), stroke)
            drawLine(Cyan, Offset(left + 6f, pumpY + 8f), Offset(left - 10f, pumpY + 8f), stroke)
        }
        val flow = (clock * (0.35f + if (params.electricPump) 0.25f else 0.1f)) % 1f
        for (i in 0 until 4) {
            val u = (flow + i / 4f) % 1f
            val a = Offset(radR, radT + 6f)
            val b = Offset(left, h * 0.34f)
            drawCircle(hose, stroke, Offset(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u))
        }

        for (i in 0 until n) {
            val cx = left + gap * (i + 0.5f)
            val th = Math.toRadians((angle + phases[i]).toDouble())
            val pinX = cx + (sin(th) * crankThrow).toFloat()
            val pinY = crankY - (cos(th) * crankThrow).toFloat()
            val horiz = pinX - cx
            val rodY = sqrt((rod * rod - horiz * horiz).coerceAtLeast(4f))
            val crown = pinY - rodY
            val half = gap * 0.22f
            drawLine(White, Offset(cx - gap * 0.30f, h * 0.40f), Offset(cx - gap * 0.30f, crankY + h * 0.05f), stroke * 0.8f)
            drawLine(White, Offset(cx + gap * 0.30f, h * 0.40f), Offset(cx + gap * 0.30f, crankY + h * 0.05f), stroke * 0.8f)
            drawLine(White, Offset(cx - half, crown), Offset(cx + half, crown), stroke)
            drawLine(White, Offset(cx - half, crown + h * 0.04f), Offset(cx + half, crown + h * 0.04f), stroke)
            drawLine(White, Offset(cx - half, crown), Offset(cx - half, crown + h * 0.04f), stroke)
            drawLine(White, Offset(cx + half, crown), Offset(cx + half, crown + h * 0.04f), stroke)
            drawLine(White, Offset(cx, crown + h * 0.04f), Offset(pinX, pinY), stroke * 0.85f)
            drawLine(White, Offset(cx, crankY), Offset(pinX, pinY), stroke)
            drawCircle(White, stroke * 0.9f, Offset(pinX, pinY))
            val deg = ((angle + phases[i]) % 360f + 360f) % 360f
            if (deg < 16f || deg > 344f) {
                drawCircle(Red, stroke * 1.5f, Offset(cx, h * 0.385f), style = Stroke(stroke))
            }
        }
        drawLine(White, Offset(left, crankY), Offset(right - 6f, crankY), stroke * 0.7f)
    }
}
