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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.revline.idle.model.EngineParams
import com.revline.idle.model.Induction
import com.revline.idle.model.Mixture
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
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
    var spinRpm by remember { mutableFloatStateOf(rpm) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    val follow = 1f - exp(-dt * 7f)
                    spinRpm += (rpmState.value - spinRpm) * follow
                    clock += dt
                    angle = (angle + spinRpm * 0.20f * dt) % 360f
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
    val exhaust = if (egtC >= 880f) Red else Amber
    val exhaustGain = ((egtC - 420f) / 480f).coerceIn(0.55f, 1.45f)
    Canvas(
        modifier.semantics {
            contentDescription = "Line-art inline-four, coolant ${coolantC.toInt()} degrees, ${rpm.toInt()} rpm"
        },
    ) {
        val w = size.width
        val h = size.height
        val stroke = (w / 170f).coerceIn(1.7f, 3.4f)
        val n = 4
        val left = w * 0.30f
        val right = w * 0.84f
        val gap = (right - left) / n
        val crankY = h * 0.73f
        val crankThrow = h * 0.055f
        val rod = h * 0.155f
        val phases = floatArrayOf(0f, 180f, 180f, 0f)
        val pulse = 0.82f + 0.18f * sin(clock * (2.2f + spinRpm / 1800f))

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
        glowLine(White, Offset(radL, radT), Offset(radR, radT), stroke, 0.7f)
        glowLine(White, Offset(radR, radT), Offset(radR, radB), stroke, 0.7f)
        glowLine(White, Offset(radR, radB), Offset(radL, radB), stroke, 0.7f)
        glowLine(White, Offset(radL, radB), Offset(radL, radT), stroke, 0.7f)
        for (i in 1 until radRows + 2) {
            val x = radL + radW * i / (radRows + 2f)
            val shimmer = 0.45f + 0.55f * ((sin(clock * 3.4f + i) + 1f) / 2f)
            glowLine(Cyan, Offset(x, radT + 4f), Offset(x, radB - 4f), stroke * 0.7f, shimmer)
        }
        if (params.intercoolerEff > 0.05) {
            val icL = w * 0.012f
            val icR = radL - 4f
            val icB = radT + h * 0.16f
            glowLine(Cyan, Offset(icL, radT + 8f), Offset(icR, radT + 8f), stroke, 0.9f)
            glowLine(Cyan, Offset(icR, radT + 8f), Offset(icR, icB), stroke, 0.9f)
            glowLine(Cyan, Offset(icR, icB), Offset(icL, icB), stroke, 0.9f)
            glowLine(Cyan, Offset(icL, icB), Offset(icL, radT + 8f), stroke, 0.9f)
        }
        if (params.oilCoolerUa > 10.0) {
            val y = radB + h * 0.05f
            glowLine(Amber, Offset(radL, y), Offset(radR, y), stroke)
            glowLine(Amber, Offset(radR, y), Offset(radR, y + h * 0.08f), stroke)
            glowLine(Amber, Offset(radR, y + h * 0.08f), Offset(radL, y + h * 0.08f), stroke)
            glowLine(Amber, Offset(radL, y + h * 0.08f), Offset(radL, y), stroke)
        }

        glowLine(White, Offset(left - 6f, h * 0.40f), Offset(right, h * 0.40f), stroke, pulse)
        glowLine(White, Offset(left - 6f, h * 0.33f), Offset(right - 8f, h * 0.33f), stroke, 0.85f)
        glowLine(White, Offset(left - 6f, h * 0.33f), Offset(left - 6f, h * 0.40f), stroke, 0.85f)
        glowLine(White, Offset(left, crankY + h * 0.06f), Offset(right, crankY + h * 0.06f), stroke, 0.8f)
        glowLine(White, Offset(left, h * 0.40f), Offset(left, crankY + h * 0.06f), stroke, 0.8f)

        val headY = h * 0.36f
        when {
            params.individualThrottles -> {
                for (i in 0 until n) {
                    val cx = left + gap * (i + 0.5f)
                    glowLine(Lime, Offset(cx, h * 0.18f), Offset(cx, headY), stroke, 1.1f)
                    glowLine(Lime, Offset(cx - 6f, h * 0.18f), Offset(cx + 6f, h * 0.18f), stroke, 1.1f)
                }
            }
            params.mixture == Mixture.CARB -> {
                glowLine(Lime, Offset(left, headY), Offset(right - 20f, headY), stroke * 0.8f, 0.9f)
                val cx = (left + right) / 2f
                glowLine(Lime, Offset(cx, headY), Offset(cx, h * 0.20f), stroke)
                glowLine(Lime, Offset(cx - 16f, h * 0.20f), Offset(cx + 16f, h * 0.20f), stroke)
                glowLine(Lime, Offset(cx - 16f, h * 0.20f), Offset(cx - 16f, h * 0.14f), stroke)
                glowLine(Lime, Offset(cx + 16f, h * 0.20f), Offset(cx + 16f, h * 0.14f), stroke)
                glowLine(Lime, Offset(cx - 16f, h * 0.14f), Offset(cx + 16f, h * 0.14f), stroke)
            }
            else -> {
                glowLine(Lime, Offset(left + 8f, h * 0.24f), Offset(right - 16f, h * 0.24f), stroke, 1.05f)
                for (i in 0 until n) {
                    val cx = left + gap * (i + 0.5f)
                    glowLine(Lime, Offset(cx, h * 0.24f), Offset(cx, headY), stroke * 0.8f)
                }
                if (params.mixture == Mixture.DIRECT) {
                    for (i in 0 until n) {
                        val cx = left + gap * (i + 0.5f)
                        glowLine(Lime, Offset(cx, h * 0.33f), Offset(cx, h * 0.40f), stroke * 0.75f, 1.15f)
                    }
                }
            }
        }
        if (params.methFraction > 0.0) {
            glowLine(Cyan, Offset(left + gap, h * 0.16f), Offset(left + gap, h * 0.24f), stroke, 1.2f)
            glowCircle(Cyan, stroke * 1.6f, Offset(left + gap, h * 0.15f), stroke, gain = 1.2f)
        }

        val exhY = h * 0.37f
        if (params.backpressure < 0.78f) {
            for (i in 0 until n) {
                val cx = left + gap * (i + 0.5f)
                glowLine(exhaust, Offset(cx, h * 0.40f), Offset(right + w * 0.02f, exhY), stroke * 0.8f, exhaustGain)
            }
        } else {
            glowLine(exhaust, Offset(left, exhY), Offset(right, exhY), stroke, exhaustGain)
        }

        if (params.induction == Induction.TURBO || params.induction == Induction.TWIN_TURBO) {
            val count = if (params.induction == Induction.TWIN_TURBO) 2 else 1
            for (t in 0 until count) {
                val cx = right + w * 0.065f
                val cy = h * (0.34f + t * 0.13f)
                val rad = h * if (count == 2) 0.048f else 0.064f
                glowCircle(Cyan, rad, Offset(cx, cy), stroke, gain = 1.15f)
                for (trail in 2 downTo 0) {
                    val spin = Math.toRadians((-(angle * 7f) - trail * 14f + t * 40f).toDouble())
                    val gain = if (trail == 0) 1.15f else 0.28f
                    val blade = if (trail == 0) White else Cyan
                    for (k in 0 until 4) {
                        val a = spin + k * Math.PI / 2.0
                        glowLine(
                            blade,
                            Offset(cx, cy),
                            Offset(cx + (cos(a) * rad * 0.78f).toFloat(), cy + (sin(a) * rad * 0.78f).toFloat()),
                            stroke * 0.7f,
                            gain,
                        )
                    }
                }
            }
        }
        if (params.induction == Induction.SUPERCHARGER) {
            glowLine(Lime, Offset(left + w * 0.08f, h * 0.28f), Offset(left + w * 0.28f, h * 0.28f), stroke, 1.1f)
            glowLine(Lime, Offset(left + w * 0.28f, h * 0.28f), Offset(left + w * 0.28f, h * 0.36f), stroke, 1.1f)
            glowLine(Lime, Offset(left + w * 0.28f, h * 0.36f), Offset(left + w * 0.08f, h * 0.36f), stroke, 1.1f)
            glowLine(Lime, Offset(left + w * 0.08f, h * 0.36f), Offset(left + w * 0.08f, h * 0.28f), stroke, 1.1f)
            val spin = Math.toRadians((angle * 5f).toDouble())
            val sx = left + w * 0.18f
            val sy = h * 0.32f
            for (k in 0 until 3) {
                val a = spin + k * Math.PI / 1.5
                glowLine(
                    White,
                    Offset(sx, sy),
                    Offset(sx + (cos(a) * h * 0.03f).toFloat(), sy + (sin(a) * h * 0.025f).toFloat()),
                    stroke * 0.7f,
                )
            }
            glowLine(White, Offset(left - 4f, crankY), Offset(left + w * 0.08f, h * 0.32f), stroke * 0.7f, 0.8f)
        }

        glowLine(hose, Offset(radR, radT + 6f), Offset(left, h * 0.34f), stroke * 0.9f, 1.05f)
        val pumpY = crankY
        glowLine(hose, Offset(radR, radB - 6f), Offset(left, pumpY), stroke * 0.9f, 1.05f)
        if (!params.electricPump) {
            glowCircle(White, stroke * 2.4f, Offset(left - 2f, pumpY), stroke)
        } else {
            glowLine(Cyan, Offset(left - 10f, pumpY - 8f), Offset(left + 6f, pumpY - 8f), stroke)
            glowLine(Cyan, Offset(left + 6f, pumpY - 8f), Offset(left + 6f, pumpY + 8f), stroke)
            glowLine(Cyan, Offset(left + 6f, pumpY + 8f), Offset(left - 10f, pumpY + 8f), stroke)
        }
        val flow = (clock * (0.55f + if (params.electricPump) 0.35f else 0.12f)) % 1f
        val upperA = Offset(radR, radT + 6f)
        val upperB = Offset(left, h * 0.34f)
        val lowerA = Offset(left, pumpY)
        val lowerB = Offset(radR, radB - 6f)
        for (i in 0 until 4) {
            val u = (flow + i / 4f) % 1f
            glowCircle(hose, stroke * 0.85f, Offset(upperA.x + (upperB.x - upperA.x) * u, upperA.y + (upperB.y - upperA.y) * u), stroke, filled = true, gain = 0.9f)
            glowCircle(hose, stroke * 0.7f, Offset(lowerA.x + (lowerB.x - lowerA.x) * u, lowerA.y + (lowerB.y - lowerA.y) * u), stroke, filled = true, gain = 0.75f)
        }

        for (i in 0 until n) {
            val cx = left + gap * (i + 0.5f)
            val th = Math.toRadians((angle + phases[i]).toDouble())
            val pinX = cx + (sin(th) * crankThrow).toFloat()
            val pinY = crankY - (cos(th) * crankThrow).toFloat()
            val counterX = cx - (sin(th) * crankThrow * 0.72f).toFloat()
            val counterY = crankY + (cos(th) * crankThrow * 0.72f).toFloat()
            val horiz = pinX - cx
            val rodY = sqrt((rod * rod - horiz * horiz).coerceAtLeast(4f))
            val crown = pinY - rodY
            val half = gap * 0.22f
            glowLine(White, Offset(cx - gap * 0.30f, h * 0.40f), Offset(cx - gap * 0.30f, crankY + h * 0.05f), stroke * 0.75f, 0.55f)
            glowLine(White, Offset(cx + gap * 0.30f, h * 0.40f), Offset(cx + gap * 0.30f, crankY + h * 0.05f), stroke * 0.75f, 0.55f)
            glowLine(Cyan, Offset(cx - half, crown), Offset(cx + half, crown), stroke, 1.15f)
            glowLine(White, Offset(cx - half, crown + h * 0.04f), Offset(cx + half, crown + h * 0.04f), stroke)
            glowLine(White, Offset(cx - half, crown), Offset(cx - half, crown + h * 0.04f), stroke)
            glowLine(White, Offset(cx + half, crown), Offset(cx + half, crown + h * 0.04f), stroke)
            glowLine(Amber, Offset(cx, crown + h * 0.04f), Offset(pinX, pinY), stroke * 0.9f, 0.95f)
            glowLine(White, Offset(cx, crankY), Offset(pinX, pinY), stroke, 0.9f)
            glowLine(White, Offset(cx, crankY), Offset(counterX, counterY), stroke * 1.15f, 0.7f)
            glowCircle(White, stroke * 1.05f, Offset(pinX, pinY), stroke, filled = true, gain = 0.8f)
            val deg = ((angle + phases[i]) % 360f + 360f) % 360f
            val fromTdc = min(deg, 360f - deg)
            val spark = if (fromTdc < 34f) (1f - fromTdc / 34f).pow(1.35f) else 0f
            if (spark > 0.03f) {
                glowCircle(White, stroke * (1.1f + spark * 4.2f), Offset(cx, h * 0.372f), stroke, filled = true, gain = spark)
                glowCircle(Red, stroke * (0.7f + spark * 2.2f), Offset(cx, h * 0.372f), stroke, filled = true, gain = spark * 0.9f)
                glowLine(exhaust, Offset(cx, h * 0.40f), Offset(right + w * 0.03f, exhY), stroke * 0.45f, spark * exhaustGain)
            }
        }
        glowLine(White, Offset(left, crankY), Offset(right - 6f, crankY), stroke * 0.75f, 0.85f)
        glowCircle(Lime, stroke * 1.7f, Offset(left, crankY), stroke, filled = true, gain = 0.55f + pulse * 0.3f)
    }
}
