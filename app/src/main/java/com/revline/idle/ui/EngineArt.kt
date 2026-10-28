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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.revline.idle.model.EngineParams
import com.revline.idle.model.Induction
import com.revline.idle.model.Mixture
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun EngineArt(
    params: EngineParams,
    rpm: Float,
    coolantC: Float,
    egtC: Float,
    parts: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val frozen = LocalBatterySaver.current
    val rpmState = rememberUpdatedState(rpm)
    var angle by remember { mutableFloatStateOf(20f) }
    var clock by remember { mutableFloatStateOf(0f) }
    var spinRpm by remember { mutableFloatStateOf(rpm) }
    LaunchedEffect(frozen) {
        if (frozen) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    val follow = 1f - exp(-dt * 7f)
                    spinRpm += (rpmState.value - spinRpm) * follow
                    clock += dt
                    if (rpmState.value > 1f) {
                        angle = EngineMotion.advanceCrank(angle, spinRpm, dt)
                    }
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
            contentDescription = "${params.blockTitle}, ${parts.count { it.value > 0 }} parts fitted, ${rpm.toInt()} rpm"
        },
    ) {
        val stroke = (size.width / 170f).coerceIn(1.7f, 3.4f)
        val calm = (1f - spinRpm / params.redlineRpm.toFloat()).coerceIn(0.15f, 1f)
        val lope = 2.2f + spinRpm / 900f
        translate(
            left = stroke * 0.35f * calm * sin(clock * lope),
            top = stroke * 0.45f * calm * sin(clock * lope * 1.7f + 0.6f),
        ) {
            drawEngine(params, parts, stroke, angle, clock, spinRpm, hose, exhaust, exhaustGain)
        }
    }
}

private fun DrawScope.drawEngine(
    params: EngineParams,
    parts: Map<String, Int>,
    stroke: Float,
    angle: Float,
    clock: Float,
    spinRpm: Float,
    hose: Color,
    exhaust: Color,
    exhaustGain: Float,
) {
    run {
        val w = size.width
        val h = size.height
        val n = params.cylinders.coerceIn(1, 12)
        val perBank = if (params.banks >= 2) (n / 2).coerceAtLeast(1) else n
        val span = when {
            perBank <= 1 -> w * 0.28f
            perBank == 2 -> w * 0.40f
            perBank == 3 -> w * 0.50f
            else -> w * 0.54f
        }
        val left = if (params.banks >= 2) w * 0.28f else w * 0.36f - span * 0.15f
        val right = left + span
        val gap = (right - left) / perBank
        val crankY = h * 0.73f
        val crankThrow = h * if (n == 1) 0.07f else 0.055f
        val deck = h * 0.40f
        val rod = crankY - crankThrow - deck - h * 0.03f
        val offsets = EngineMotion.firingOffsets(perBank)
        val pulse = 0.82f + 0.18f * sin(clock * (2.2f + spinRpm / 1800f))
        fun part(id: String) = parts[id] ?: 0
        val filterLvl = part("filter")
        val coldLvl = part("cold_air")
        val bootLvl = part("boot")
        val headerLvl = part("headers")
        val longLvl = part("long_tube")
        val tipLvl = part("tip")
        val catLvl = part("catback")
        val ceramicOn = part("ceramic") > 0
        val springLvl = part("springs")
        val camLvl = part("cam")
        val vvtLvl = part("vvt")
        val boreLvl = part("bore")
        val compLvl = part("compression")
        val forged = part("pistons") > 0
        val finLvl = part("fins")
        val ductLvl = part("duct")
        val capLvl = part("cap")
        val oilLvl = part("oil_cooler")
        val pumpLvl = part("fuel_pump")
        val needleLvl = part("needle")
        val coilLvl = part("coils")
        val plugLvl = part("plugs") + part("gap")
        val turboLvl = part("turbo")
        val scLvl = part("supercharger")
        val icLvl = part("intercooler")
        val controllerLvl = part("boost_controller")
        val methLvl = part("meth")
        val studLvl = part("studs")

        val radRows = when {
            params.airCooled -> 0
            params.radiatorUa > 1600.0 -> 4
            params.radiatorUa > 1200.0 -> 3
            params.radiatorUa > 950.0 -> 2
            else -> 1
        }
        val radW = w * (0.055f + 0.012f * radRows.coerceAtLeast(1))
        val radL = w * 0.05f
        val radR = radL + radW
        val radT = h * 0.22f
        val radB = h * 0.62f
        if (!params.airCooled) {
        glowLine(White, Offset(radL, radT), Offset(radR, radT), stroke, 0.7f)
        glowLine(White, Offset(radR, radT), Offset(radR, radB), stroke, 0.7f)
        glowLine(White, Offset(radR, radB), Offset(radL, radB), stroke, 0.7f)
        glowLine(White, Offset(radL, radB), Offset(radL, radT), stroke, 0.7f)
        for (i in 1 until radRows + 2) {
            val x = radL + radW * i / (radRows + 2f)
            val shimmer = 0.45f + 0.55f * ((sin(clock * 3.4f + i) + 1f) / 2f)
            glowLine(Cyan, Offset(x, radT + 4f), Offset(x, radB - 4f), stroke * 0.7f, shimmer)
        }
        if (capLvl > 0) {
            val nub = 4f + capLvl * 3f
            glowLine(White, Offset((radL + radR) / 2f - 4f, radT), Offset((radL + radR) / 2f - 4f, radT - nub), stroke * 0.7f)
            glowLine(White, Offset((radL + radR) / 2f + 4f, radT), Offset((radL + radR) / 2f + 4f, radT - nub), stroke * 0.7f)
            glowLine(White, Offset((radL + radR) / 2f - 4f, radT - nub), Offset((radL + radR) / 2f + 4f, radT - nub), stroke * 0.7f)
        }
        if (ductLvl > 0) {
            val mouth = radL - 6f - ductLvl * 5f
            glowLine(Cyan, Offset(mouth, radT - 4f), Offset(radL, radT + 8f), stroke * 0.8f, 0.9f)
            glowLine(Cyan, Offset(mouth, radB + 4f), Offset(radL, radB - 8f), stroke * 0.8f, 0.9f)
        }
        }
        if ((params.intercoolerEff > 0.05 || icLvl > 0) && !params.airCooled) {
            val depth = h * (0.10f + 0.025f * icLvl.coerceAtLeast(1))
            val icL = w * 0.012f
            val icR = radL - 4f
            val icTop = radT + 8f
            val icB = icTop + depth
            glowLine(Cyan, Offset(icL, icTop), Offset(icR, icTop), stroke, 0.9f)
            glowLine(Cyan, Offset(icR, icTop), Offset(icR, icB), stroke, 0.9f)
            glowLine(Cyan, Offset(icR, icB), Offset(icL, icB), stroke, 0.9f)
            glowLine(Cyan, Offset(icL, icB), Offset(icL, icTop), stroke, 0.9f)
            val bars = icLvl.coerceAtLeast(if (params.intercoolerEff > 0.05) 1 else 0)
            for (bar in 1..bars) {
                val y = icTop + depth * bar / (bars + 1f)
                glowLine(Cyan, Offset(icL + 3f, y), Offset(icR - 3f, y), stroke * 0.55f, 0.7f)
            }
        }
        if (params.oilCoolerUa > 10.0 || oilLvl > 0) {
            val y = radB + h * 0.05f
            val coolerH = h * (0.05f + 0.018f * oilLvl.coerceAtLeast(1))
            glowLine(Amber, Offset(radL, y), Offset(radR, y), stroke)
            glowLine(Amber, Offset(radR, y), Offset(radR, y + coolerH), stroke)
            glowLine(Amber, Offset(radR, y + coolerH), Offset(radL, y + coolerH), stroke)
            glowLine(Amber, Offset(radL, y + coolerH), Offset(radL, y), stroke)
            for (row in 1..oilLvl.coerceAtLeast(1)) {
                val yy = y + coolerH * row / (oilLvl.coerceAtLeast(1) + 1f)
                glowLine(Amber, Offset(radL + 3f, yy), Offset(radR - 3f, yy), stroke * 0.5f, 0.6f)
            }
        }

        val exhY = h * 0.37f
        val collectorX = right + w * (0.03f + 0.018f * headerLvl + 0.02f * longLvl)
        val tailX = collectorX + w * (0.03f + 0.012f * catLvl)
        val tailDrop = h * 0.012f * tipLvl
        if (params.banks < 2) {
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
        if (params.methFraction > 0.0 || methLvl > 0) {
            val nozzle = stroke * (1.4f + 0.45f * methLvl.coerceAtLeast(1))
            glowLine(Cyan, Offset(left + gap, h * 0.16f), Offset(left + gap, h * 0.24f), stroke, 1.2f)
            glowCircle(Cyan, nozzle, Offset(left + gap, h * 0.15f), stroke, gain = 1.2f)
        }
        if (filterLvl > 0 || coldLvl > 0 || bootLvl > 0 || needleLvl > 0) {
            val mouth = (left + right) / 2f
            val boxW = 8f + filterLvl * 4.5f + if (bootLvl > 0) 4f else 0f
            val boxH = 7f + filterLvl * 2.2f
            val top = h * 0.125f
            glowLine(Lime, Offset(mouth - boxW, top), Offset(mouth + boxW, top), stroke * 0.8f, 1f)
            glowLine(Lime, Offset(mouth + boxW, top), Offset(mouth + boxW, top + boxH), stroke * 0.8f, 1f)
            glowLine(Lime, Offset(mouth + boxW, top + boxH), Offset(mouth - boxW, top + boxH), stroke * 0.8f, 1f)
            glowLine(Lime, Offset(mouth - boxW, top + boxH), Offset(mouth - boxW, top), stroke * 0.8f, 1f)
            for (pleat in 1..filterLvl.coerceAtMost(5)) {
                val x = mouth - boxW + boxW * 2f * pleat / (filterLvl.coerceAtLeast(1) + 1f)
                glowLine(Cyan, Offset(x, top + 2f), Offset(x, top + boxH - 2f), stroke * 0.45f, 0.75f)
            }
            if (coldLvl > 0 || bootLvl > 0) {
                val reach = w * (0.03f * bootLvl.coerceAtLeast(0) + 0.045f * coldLvl)
                val snorkelY = top - h * (0.015f * coldLvl)
                glowLine(Cyan, Offset(mouth - boxW, top + boxH * 0.4f), Offset(mouth - boxW - reach, snorkelY), stroke, 1f)
                glowLine(Cyan, Offset(mouth - boxW - reach, snorkelY), Offset(mouth - boxW - reach - 8f, snorkelY - 6f), stroke, 1f)
            }
            if (needleLvl > 0) {
                for (mark in 0 until needleLvl.coerceAtMost(4)) {
                    val y = top + 3f + mark * 3.5f
                    glowLine(White, Offset(mouth + boxW, y), Offset(mouth + boxW + 6f + needleLvl, y), stroke * 0.45f, 0.8f)
                }
            }
        }

        if (headerLvl > 0) {
            for (i in 0 until n) {
                val cx = left + gap * (i + 0.5f)
                val spread = (i - (n - 1) / 2f) * (1.5f + headerLvl)
                glowLine(exhaust, Offset(cx, h * 0.40f), Offset(collectorX, exhY + spread), stroke * (0.65f + 0.06f * headerLvl), exhaustGain)
                if (ceramicOn) {
                    glowLine(White, Offset(cx, h * 0.40f + 3f), Offset(collectorX, exhY + spread + 3f), stroke * 0.4f, 0.65f)
                }
            }
        } else {
            glowLine(exhaust, Offset(left, exhY), Offset(right, exhY), stroke, exhaustGain)
        }
        if (headerLvl > 0 || catLvl > 0 || tipLvl > 0) {
            glowLine(exhaust, Offset(if (headerLvl > 0) collectorX else right, exhY), Offset(tailX, exhY + tailDrop), stroke * (0.85f + 0.08f * catLvl), exhaustGain)
        }
        if (tipLvl > 0) {
            glowLine(exhaust, Offset(tailX, exhY + tailDrop), Offset(tailX - 6f, exhY + tailDrop + 6f + tipLvl * 4f), stroke, exhaustGain)
        }
        if (pumpLvl > 0) {
            val pumpW = 8f + pumpLvl * 3f
            val py = h * 0.86f
            glowLine(Amber, Offset(left, py), Offset(left + pumpW, py), stroke * 0.7f)
            glowLine(Amber, Offset(left + pumpW, py), Offset(left + pumpW, py + 10f), stroke * 0.7f)
            glowLine(Amber, Offset(left + pumpW, py + 10f), Offset(left, py + 10f), stroke * 0.7f)
            glowLine(Amber, Offset(left, py + 10f), Offset(left, py), stroke * 0.7f)
        }
        if (camLvl > 0) {
            val camY = h * 0.295f
            val lobe = 3.5f + camLvl * 1.3f
            glowLine(White, Offset(left - 4f, camY), Offset(right - 8f, camY), stroke * 0.65f, 0.9f)
            for (i in 0 until n) {
                val cx = left + gap * (i + 0.5f)
                val cyc = EngineMotion.cycle(angle + offsets[i])
                for ((x, peak) in listOf(cx - gap * 0.15f to 465f, cx + gap * 0.15f to 255f)) {
                    val nose = Math.toRadians(((cyc - peak) / 2f).toDouble())
                    glowCircle(Lime, lobe * 0.7f, Offset(x, camY), stroke * 0.55f, gain = 0.8f)
                    glowLine(
                        Lime,
                        Offset(x, camY),
                        Offset(x + (sin(nose) * lobe * 1.25f).toFloat(), camY + (cos(nose) * lobe * 1.25f).toFloat()),
                        stroke * 0.7f,
                        1.05f,
                    )
                }
            }
            if (vvtLvl > 0) {
                glowCircle(Cyan, 5f + vvtLvl * 2.5f, Offset(left - 10f, camY), stroke, gain = 1.05f)
            }
        }
        if (plugLvl > 0 || coilLvl > 0) {
            val packs = (1 + coilLvl).coerceAtMost(n)
            val railY = h * 0.275f
            glowLine(White, Offset(left, railY), Offset(left + gap * packs, railY), stroke * 0.55f, 0.8f)
            for (i in 0 until packs) {
                val cx = left + gap * (i + 0.5f)
                val pack = 3f + coilLvl * 1.2f
                glowLine(White, Offset(cx - pack, railY - pack), Offset(cx + pack, railY - pack), stroke * 0.5f)
                glowLine(White, Offset(cx + pack, railY - pack), Offset(cx + pack, railY + pack), stroke * 0.5f)
                glowLine(White, Offset(cx + pack, railY + pack), Offset(cx - pack, railY + pack), stroke * 0.5f)
                glowLine(White, Offset(cx - pack, railY + pack), Offset(cx - pack, railY - pack), stroke * 0.5f)
                glowLine(Amber, Offset(cx, railY + pack), Offset(cx, h * 0.36f), stroke * 0.4f, 0.7f + 0.05f * plugLvl)
            }
        }
        }

        if (params.induction == Induction.TURBO || params.induction == Induction.TWIN_TURBO) {
            val count = if (params.induction == Induction.TWIN_TURBO) 2 else 1
            for (t in 0 until count) {
                val cx = right + w * 0.065f
                val cy = h * (0.34f + t * 0.13f)
                val grown = 1f + 0.16f * (turboLvl - 1).coerceAtLeast(0)
                val rad = h * (if (count == 2) 0.048f else 0.064f) * grown
                glowCircle(Cyan, rad, Offset(cx, cy), stroke, gain = 1.15f)
                if (part("overboost") > 0) {
                    glowCircle(Red, rad * (1.28f + 0.06f * part("overboost")), Offset(cx, cy), stroke * 0.65f, gain = 0.85f)
                }
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
                if (controllerLvl > 0 && t == 0) {
                    val box = 4f + controllerLvl * 2.5f
                    glowLine(Lime, Offset(cx + rad, cy - box), Offset(cx + rad + box, cy - box), stroke * 0.6f)
                    glowLine(Lime, Offset(cx + rad + box, cy - box), Offset(cx + rad + box, cy + box), stroke * 0.6f)
                    glowLine(Lime, Offset(cx + rad + box, cy + box), Offset(cx + rad, cy + box), stroke * 0.6f)
                }
            }
        }
        if (params.induction == Induction.SUPERCHARGER && params.banks < 2) {
            val grow = 1f + 0.16f * (scLvl - 1).coerceAtLeast(0)
            val scR = left + w * 0.08f + w * 0.20f * grow
            glowLine(Lime, Offset(left + w * 0.08f, h * 0.28f), Offset(scR, h * 0.28f), stroke, 1.1f)
            glowLine(Lime, Offset(scR, h * 0.28f), Offset(scR, h * 0.36f), stroke, 1.1f)
            glowLine(Lime, Offset(scR, h * 0.36f), Offset(left + w * 0.08f, h * 0.36f), stroke, 1.1f)
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

        if (!params.airCooled) {
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
        }

        abuseMarks(parts, w, h, stroke)
        if (params.banks >= 2) {
            drawVee(params, parts, stroke, angle, exhaust, exhaustGain, perBank)
            return@run
        }
        val headerOut = headerLvl > 0
        val pipeEnd = if (headerLvl > 0 || catLvl > 0 || tipLvl > 0) Offset(tailX, exhY + tailDrop) else Offset(right, exhY)
        val blur = ((spinRpm - 2200f) / 4000f).coerceIn(0f, 1f)
        for (i in 0 until perBank) {
            val cx = left + gap * (i + 0.5f)
            val cyc = EngineMotion.cycle(angle + offsets[i])
            val th = Math.toRadians(cyc.toDouble())
            val pinX = cx + (sin(th) * crankThrow).toFloat()
            val pinY = crankY - (cos(th) * crankThrow).toFloat()
            val counterX = cx - (sin(th) * crankThrow * 0.72f).toFloat()
            val counterY = crankY + (cos(th) * crankThrow * 0.72f).toFloat()
            val crown = crankY - EngineMotion.travel(cyc, crankThrow, rod)
            val inX = cx - gap * 0.15f
            val exX = cx + gap * 0.15f
            val valveR = gap * 0.075f
            val liftMax = h * 0.022f
            val inLift = EngineMotion.intakeLift(cyc) * liftMax
            val exLift = EngineMotion.exhaustLift(cyc) * liftMax
            for ((x, lift, color) in listOf(Triple(inX, inLift, Lime), Triple(exX, exLift, exhaust))) {
                glowLine(White, Offset(x, h * 0.315f + lift), Offset(x, deck + lift), stroke * 0.5f, 0.6f)
                glowLine(color, Offset(x - valveR, deck + lift), Offset(x + valveR, deck + lift), stroke * 0.8f, 0.75f + lift / liftMax * 0.4f)
                if (springLvl > 0) {
                    val coils = 2 + springLvl
                    val top = h * 0.322f + lift
                    val seat = h * 0.372f
                    for (coil in 0 until coils) {
                        val y0 = top + (seat - top) * coil / coils
                        val y1 = top + (seat - top) * (coil + 1) / coils
                        val zig = if (coil % 2 == 0) -3f else 3f
                        glowLine(White, Offset(x + zig, y0), Offset(x - zig, y1), stroke * 0.4f, 0.6f)
                    }
                }
            }
            EngineMotion.charge(cyc)?.let { q ->
                val y = h * 0.24f + (deck - h * 0.24f) * q
                glowCircle(Lime, stroke * 0.75f, Offset(inX, y), stroke, filled = true, gain = sin(Math.PI * q).toFloat() * 0.85f)
            }
            EngineMotion.puff(cyc)?.let { p ->
                val port = Offset(cx, deck)
                val bend = if (headerOut) {
                    Offset(collectorX, exhY + (i - (n - 1) / 2f) * (1.5f + headerLvl))
                } else {
                    Offset(cx + gap * 0.3f, exhY)
                }
                val spot = alongPath(listOf(port, bend, pipeEnd), p)
                val fade = (1f - p).pow(0.7f)
                glowCircle(exhaust, stroke * (0.6f + 0.9f * fade), spot, stroke, filled = true, gain = fade * exhaustGain * 0.9f)
            }
            glowLine(White, Offset(cx - gap * 0.30f, h * 0.40f), Offset(cx - gap * 0.30f, crankY + h * 0.05f), stroke * 0.75f, 0.55f)
            glowLine(White, Offset(cx + gap * 0.30f, h * 0.40f), Offset(cx + gap * 0.30f, crankY + h * 0.05f), stroke * 0.75f, 0.55f)
            if (params.airCooled) {
                val fins = 4 + finLvl
                val stick = gap * (0.34f + 0.035f * finLvl)
                for (fin in 0 until fins) {
                    val fy = h * 0.42f + fin * (h * 0.22f / fins)
                    glowLine(Cyan, Offset(cx - stick, fy), Offset(cx + stick, fy), stroke * 0.55f, 0.7f)
                }
            }
            val skirt = gap * (0.22f + 0.028f * boreLvl)
            val crownDrop = h * (0.04f + 0.007f * compLvl)
            val crownColor = if (forged) Lime else Cyan
            if (studLvl > 0) {
                glowCircle(White, stroke * (0.7f + 0.15f * studLvl), Offset(cx - skirt, h * 0.40f), stroke, filled = true, gain = 0.6f)
                glowCircle(White, stroke * (0.7f + 0.15f * studLvl), Offset(cx + skirt, h * 0.40f), stroke, filled = true, gain = 0.6f)
            }
            val burn = EngineMotion.burn(cyc)
            if (burn > 0.02f) {
                val room = (crown - deck).coerceAtLeast(2f)
                val reach = skirt * (0.35f + 0.6f * (1f - burn))
                glowLine(White, Offset(cx - reach * 0.55f, deck + room * 0.3f), Offset(cx + reach * 0.55f, deck + room * 0.3f), stroke, burn * 1.3f)
                glowLine(Amber, Offset(cx - reach, deck + room * 0.55f), Offset(cx + reach, deck + room * 0.55f), stroke, burn * 1.1f)
                glowLine(Red, Offset(cx - reach * 0.8f, deck + room * 0.8f), Offset(cx + reach * 0.8f, deck + room * 0.8f), stroke * 0.8f, burn * 0.8f)
            }
            val spark = EngineMotion.spark(cyc)
            if (spark > 0.03f) {
                glowCircle(White, stroke * (0.9f + spark * 2.6f), Offset(cx, deck + 2f), stroke, filled = true, gain = spark * 1.2f)
                glowCircle(Cyan, stroke * (0.5f + spark * 1.2f), Offset(cx, deck + 2f), stroke, filled = true, gain = spark)
            }
            glowLine(crownColor, Offset(cx - skirt, crown), Offset(cx + skirt, crown), stroke, 1.15f)
            glowLine(White, Offset(cx - skirt, crown + crownDrop), Offset(cx + skirt, crown + crownDrop), stroke)
            glowLine(White, Offset(cx - skirt, crown), Offset(cx - skirt, crown + crownDrop), stroke)
            glowLine(White, Offset(cx + skirt, crown), Offset(cx + skirt, crown + crownDrop), stroke)
            if (blur > 0.02f) {
                for (ghost in 1..2) {
                    val g = Math.toRadians((cyc - ghost * 16f).toDouble())
                    val ghostPin = Offset(cx + (sin(g) * crankThrow).toFloat(), crankY - (cos(g) * crankThrow).toFloat())
                    glowLine(White, Offset(cx, crankY), ghostPin, stroke * 0.8f, blur * 0.32f / ghost)
                    glowLine(Amber, Offset(cx, crown + crownDrop), ghostPin, stroke * 0.6f, blur * 0.22f / ghost)
                }
            }
            glowLine(Amber, Offset(cx, crown + crownDrop), Offset(pinX, pinY), stroke * 0.9f, 0.95f)
            glowLine(White, Offset(cx, crankY), Offset(pinX, pinY), stroke, 0.9f)
            glowLine(White, Offset(cx, crankY), Offset(counterX, counterY), stroke * 1.15f, 0.7f)
            glowCircle(White, stroke * 1.05f, Offset(pinX, pinY), stroke, filled = true, gain = 0.8f)
        }
        val flyR = h * 0.065f
        val fly = Offset(right + flyR * 0.5f, crankY)
        glowLine(White, Offset(left, crankY), fly, stroke * 0.75f, 0.85f)
        glowCircle(Lime, stroke * 1.7f, Offset(left, crankY), stroke, filled = true, gain = 0.55f + pulse * 0.3f)
        val turn = Math.toRadians(angle.toDouble())
        glowCircle(White, flyR, fly, stroke * 0.8f, gain = 0.7f)
        for (spoke in 0 until 3) {
            val a = turn + spoke * 2.0 * Math.PI / 3.0
            glowLine(White, fly, Offset(fly.x + (sin(a) * flyR * 0.82f).toFloat(), fly.y - (cos(a) * flyR * 0.82f).toFloat()), stroke * 0.6f, 0.55f)
        }
        glowCircle(Lime, stroke * 0.9f, Offset(fly.x + (sin(turn) * flyR).toFloat(), fly.y - (cos(turn) * flyR).toFloat()), stroke, filled = true, gain = 1.1f)
    }
}

/** Nitrous bottle and the red arc of a limiter that is no longer a limiter. */
private fun DrawScope.abuseMarks(parts: Map<String, Int>, w: Float, h: Float, stroke: Float) {
    val nitrous = parts["nitrous"] ?: 0
    if (nitrous > 0) {
        val bw = 8f + nitrous * 2.5f
        val bh = 16f + nitrous * 6f
        val x = w * 0.03f
        val y = h * 0.62f
        glowLine(Cyan, Offset(x, y), Offset(x + bw, y), stroke * 0.7f, 1.05f)
        glowLine(Cyan, Offset(x + bw, y), Offset(x + bw, y + bh), stroke * 0.7f, 1.05f)
        glowLine(Cyan, Offset(x + bw, y + bh), Offset(x, y + bh), stroke * 0.7f, 1.05f)
        glowLine(Cyan, Offset(x, y + bh), Offset(x, y), stroke * 0.7f, 1.05f)
        glowLine(White, Offset(x + bw * 0.5f, y), Offset(x + bw * 0.5f, y - 7f - nitrous), stroke * 0.6f)
        glowLine(White, Offset(x + bw * 0.5f, y - 7f - nitrous), Offset(x + bw * 0.5f + 8f, y - 7f - nitrous), stroke * 0.6f)
        val fill = (0.35f + 0.2f * nitrous).coerceAtMost(0.9f)
        glowLine(Cyan, Offset(x + 2f, y + bh * (1f - fill)), Offset(x + bw - 2f, y + bh * (1f - fill)), stroke * 0.45f, 0.7f)
    }
    val limiter = parts["limiter"] ?: 0
    if (limiter > 0) {
        val arc = 54f + limiter * 18f
        glowArc(Red, 210f, arc, Offset(w * 0.78f, h * 0.58f), Size(h * 0.22f, h * 0.22f), stroke * 0.8f, gain = 0.75f + 0.1f * limiter)
    }
    val lean = parts["dyno_fuel"] ?: 0
    if (lean > 0) {
        val x = w * 0.90f
        val y = h * 0.50f
        repeat(lean) { mark ->
            val yy = y + mark * 5f
            glowLine(Red, Offset(x, yy), Offset(x + 10f + lean * 2f, yy - 3f), stroke * 0.45f, 0.8f)
        }
    }
}

private fun alongPath(points: List<Offset>, u: Float): Offset {
    val lengths = points.zipWithNext { a, b -> (b - a).getDistance() }
    val total = lengths.sum().coerceAtLeast(1e-3f)
    var left = u.coerceIn(0f, 1f) * total
    for (i in lengths.indices) {
        if (left <= lengths[i] || i == lengths.lastIndex) {
            val t = (left / lengths[i].coerceAtLeast(1e-3f)).coerceIn(0f, 1f)
            return points[i] + (points[i + 1] - points[i]) * t
        }
        left -= lengths[i]
    }
    return points.last()
}

private fun DrawScope.drawVee(
    params: EngineParams,
    parts: Map<String, Int>,
    stroke: Float,
    angle: Float,
    exhaust: Color,
    exhaustGain: Float,
    perBank: Int,
) {
    val w = size.width
    val h = size.height
    fun part(id: String) = parts[id] ?: 0
    val rows = perBank.coerceAtLeast(1)
    val half = if (params.cylinders == 8) 45f else 30f
    val throwLen = h * 0.062f
    val rodLen = h * 0.24f
    val pistonH = h * (0.048f + 0.006f * part("compression"))
    val deck = rodLen + throwLen + pistonH + h * 0.024f
    val cover = h * 0.055f
    val skirt = h * 0.062f * (1f + 0.025f * part("bore"))
    val depth = Offset(w * 0.020f, -h * 0.016f)
    val front = Offset(w * 0.47f - depth.x * (rows - 1) / 2f, h * 0.74f - depth.y * (rows - 1) / 2f)
    val crownColor = if (part("pistons") > 0) Lime else Cyan
    val headerLvl = part("headers") + part("long_tube")
    val springLvl = part("springs")
    val camLvl = part("cam")
    val coilLvl = part("coils")

    for (row in rows - 1 downTo 0) {
        val c = front + depth * row.toFloat()
        val g = 1f - 0.55f * row / (rows - 1).coerceAtLeast(1)
        val crankDeg = angle + row * 360f / rows
        val turn = Math.toRadians(crankDeg.toDouble())
        val pin = Offset(c.x + (sin(turn) * throwLen).toFloat(), c.y - (cos(turn) * throwLen).toFloat())
        for (bank in 0..1) {
            val beta = if (bank == 0) -half else half
            val b = Math.toRadians(beta.toDouble())
            val d = Offset(sin(b).toFloat(), -cos(b).toFloat())
            val across = Offset(cos(b).toFloat(), sin(b).toFloat())
            val out = if (bank == 0) -1f else 1f
            fun at(along: Float, side: Float) = c + d * along + across * side
            val cyc = EngineMotion.cycle(crankDeg - beta + 360f * ((row + bank) % 2))
            val s = EngineMotion.travel(cyc, throwLen, rodLen)
            val crown = s + pistonH

            glowLine(White, at(rodLen - throwLen, -skirt - 2f), at(deck, -skirt - 2f), stroke * 0.75f, 0.55f * g)
            glowLine(White, at(rodLen - throwLen, skirt + 2f), at(deck, skirt + 2f), stroke * 0.75f, 0.55f * g)
            glowLine(White, at(deck, -skirt - 6f), at(deck, skirt + 6f), stroke, 0.8f * g)
            glowLine(White, at(deck, -skirt - 6f), at(deck + cover, -skirt + 2f), stroke * 0.85f, 0.75f * g)
            glowLine(White, at(deck + cover, -skirt + 2f), at(deck + cover, skirt - 2f), stroke * 0.85f, 0.75f * g)
            glowLine(White, at(deck + cover, skirt - 2f), at(deck, skirt + 6f), stroke * 0.85f, 0.75f * g)

            val burn = EngineMotion.burn(cyc)
            if (burn > 0.02f) {
                val mid = (crown + deck) / 2f
                val reach = skirt * (0.35f + 0.6f * (1f - burn))
                glowLine(White, at(mid + (deck - crown) * 0.2f, -reach * 0.55f), at(mid + (deck - crown) * 0.2f, reach * 0.55f), stroke, burn * 1.3f * g)
                glowLine(Amber, at(mid, -reach), at(mid, reach), stroke, burn * 1.1f * g)
                glowLine(Red, at(mid - (deck - crown) * 0.25f, -reach * 0.8f), at(mid - (deck - crown) * 0.25f, reach * 0.8f), stroke * 0.8f, burn * 0.8f * g)
            }
            val spark = EngineMotion.spark(cyc)
            if (spark > 0.03f) {
                glowCircle(White, stroke * (0.9f + spark * 2.4f), at(deck - 2f, 0f), stroke, filled = true, gain = spark * 1.2f * g)
                glowCircle(Cyan, stroke * (0.5f + spark * 1.1f), at(deck - 2f, 0f), stroke, filled = true, gain = spark * g)
            }

            if (row == 0) {
                val liftMax = h * 0.02f
                val valves = listOf(
                    Triple(-out * skirt * 0.45f, EngineMotion.intakeLift(cyc) * liftMax, Lime),
                    Triple(out * skirt * 0.45f, EngineMotion.exhaustLift(cyc) * liftMax, exhaust),
                )
                for ((side, lift, color) in valves) {
                    glowLine(White, at(deck + cover * 0.8f - lift, side), at(deck - lift, side), stroke * 0.5f, 0.6f)
                    glowLine(color, at(deck - lift, side - skirt * 0.28f), at(deck - lift, side + skirt * 0.28f), stroke * 0.8f, 0.75f + lift / liftMax * 0.4f)
                    if (springLvl > 0) {
                        val coils = 2 + springLvl
                        val top = deck + cover * 0.7f - lift
                        val seat = deck + cover * 0.15f
                        for (coil in 0 until coils) {
                            val a0 = top + (seat - top) * coil / coils
                            val a1 = top + (seat - top) * (coil + 1) / coils
                            val zig = if (coil % 2 == 0) -3f else 3f
                            glowLine(White, at(a0, side + zig), at(a1, side - zig), stroke * 0.4f, 0.6f)
                        }
                    }
                }
                if (camLvl > 0) {
                    val lobe = 3f + camLvl * 1.2f
                    val hub = at(deck + cover * 0.82f, 0f)
                    val nose = Math.toRadians((cyc / 2f).toDouble()) + b
                    glowCircle(Lime, lobe * 0.7f, hub, stroke * 0.55f, gain = 0.8f)
                    glowLine(Lime, hub, Offset(hub.x + (sin(nose) * lobe * 1.25f).toFloat(), hub.y - (cos(nose) * lobe * 1.25f).toFloat()), stroke * 0.7f, 1.05f)
                }
                if (coilLvl > 0) {
                    val pack = 3f + coilLvl * 1.2f
                    val base = at(deck + cover + pack, 0f)
                    glowLine(White, base - Offset(pack, pack), base + Offset(pack, -pack), stroke * 0.5f)
                    glowLine(White, base + Offset(pack, -pack), base + Offset(pack, pack), stroke * 0.5f)
                    glowLine(White, base + Offset(pack, pack), base + Offset(-pack, pack), stroke * 0.5f)
                    glowLine(White, base + Offset(-pack, pack), base - Offset(pack, pack), stroke * 0.5f)
                }
            }

            val port = at(deck - h * 0.03f, out * (skirt + 4f))
            val bend = port + across * (out * (h * 0.05f + headerLvl * h * 0.012f))
            val drop = Offset(bend.x, c.y + h * 0.04f)
            if (row == 0 || headerLvl > 0) {
                glowLine(exhaust, port, bend, stroke * (0.65f + 0.06f * headerLvl), exhaustGain * g)
            }
            if (row == 0) {
                glowLine(exhaust, bend, drop, stroke * 0.9f, exhaustGain)
            }
            EngineMotion.puff(cyc)?.let { p ->
                val fade = (1f - p).pow(0.7f)
                glowCircle(exhaust, stroke * (0.6f + 0.9f * fade), alongPath(listOf(port, bend, drop), p), stroke, filled = true, gain = fade * exhaustGain * 0.9f * g)
            }

            glowLine(crownColor, at(crown, -skirt), at(crown, skirt), stroke, 1.15f * g)
            glowLine(White, at(s, -skirt), at(s, skirt), stroke * 0.8f, 0.8f * g)
            glowLine(White, at(s, -skirt), at(crown, -skirt), stroke * 0.8f, 0.8f * g)
            glowLine(White, at(s, skirt), at(crown, skirt), stroke * 0.8f, 0.8f * g)
            glowLine(Amber, at(s, 0f), pin, stroke * 0.85f, 0.95f * g)
        }
        glowLine(White, c, Offset(c.x - (sin(turn) * throwLen * 1.3f).toFloat(), c.y + (cos(turn) * throwLen * 1.3f).toFloat()), stroke * 1.3f, 0.7f * g)
        glowLine(White, c, pin, stroke, 0.9f * g)
        glowCircle(White, stroke * 1.05f, pin, stroke, filled = true, gain = 0.85f * g)
    }

    val c = front
    val turn = Math.toRadians(angle.toDouble())
    val pulley = throwLen * 1.55f
    glowCircle(White, pulley, c, stroke * 0.7f, gain = 0.5f)
    for (spoke in 0 until 3) {
        val a = turn + spoke * 2.0 * Math.PI / 3.0
        glowLine(White, c, Offset(c.x + (sin(a) * pulley * 0.82f).toFloat(), c.y - (cos(a) * pulley * 0.82f).toFloat()), stroke * 0.6f, 0.55f)
    }
    glowCircle(Lime, stroke * 0.9f, Offset(c.x + (sin(turn) * pulley).toFloat(), c.y - (cos(turn) * pulley).toFloat()), stroke, filled = true, gain = 1.1f)
    val sumpTop = c.y + throwLen * 0.4f
    val sumpBottom = c.y + h * 0.13f
    glowLine(White, Offset(c.x - h * 0.21f, sumpTop), Offset(c.x - h * 0.16f, sumpBottom), stroke * 0.85f, 0.65f)
    glowLine(White, Offset(c.x - h * 0.16f, sumpBottom), Offset(c.x + h * 0.16f, sumpBottom), stroke * 0.85f, 0.65f)
    glowLine(White, Offset(c.x + h * 0.16f, sumpBottom), Offset(c.x + h * 0.21f, sumpTop), stroke * 0.85f, 0.65f)

    val valley = Offset(c.x, c.y - (deck + cover) * cos(Math.toRadians(half.toDouble())).toFloat())
    val plenumW = h * 0.12f
    val plenumH = h * 0.045f
    val plenumTop = valley.y - plenumH - h * 0.02f
    val blower = params.induction == Induction.SUPERCHARGER
    val boxW = if (blower) plenumW * (1.15f + 0.12f * (part("supercharger") - 1).coerceAtLeast(0)) else plenumW
    val boxTop = if (blower) plenumTop - h * 0.04f else plenumTop
    glowLine(Lime, Offset(valley.x - boxW, boxTop), Offset(valley.x + boxW, boxTop), stroke, 1.05f)
    glowLine(Lime, Offset(valley.x + boxW, boxTop), Offset(valley.x + boxW, valley.y - h * 0.02f), stroke, 1.05f)
    glowLine(Lime, Offset(valley.x + boxW, valley.y - h * 0.02f), Offset(valley.x - boxW, valley.y - h * 0.02f), stroke, 1.05f)
    glowLine(Lime, Offset(valley.x - boxW, valley.y - h * 0.02f), Offset(valley.x - boxW, boxTop), stroke, 1.05f)
    if (blower) {
        val rotor = Offset(valley.x, (boxTop + valley.y - h * 0.02f) / 2f)
        val spin = Math.toRadians((angle * 5f).toDouble())
        for (k in 0 until 3) {
            val a = spin + k * 2.0 * Math.PI / 3.0
            glowLine(White, rotor, Offset(rotor.x + (cos(a) * boxW * 0.5f).toFloat(), rotor.y + (sin(a) * h * 0.025f).toFloat()), stroke * 0.7f)
        }
        glowLine(White, Offset(c.x, c.y), Offset(valley.x - boxW, rotor.y), stroke * 0.6f, 0.5f)
    }
    val filterLvl = part("filter")
    if (filterLvl > 0) {
        val fw = h * (0.04f + 0.012f * filterLvl)
        val fh = h * (0.03f + 0.006f * filterLvl)
        val ft = boxTop - fh - h * 0.012f
        glowLine(Lime, Offset(valley.x - fw, ft), Offset(valley.x + fw, ft), stroke * 0.8f)
        glowLine(Lime, Offset(valley.x + fw, ft), Offset(valley.x + fw, ft + fh), stroke * 0.8f)
        glowLine(Lime, Offset(valley.x + fw, ft + fh), Offset(valley.x - fw, ft + fh), stroke * 0.8f)
        glowLine(Lime, Offset(valley.x - fw, ft + fh), Offset(valley.x - fw, ft), stroke * 0.8f)
        for (pleat in 1..filterLvl.coerceAtMost(5)) {
            val x = valley.x - fw + 2f * fw * pleat / (filterLvl + 1f)
            glowLine(Cyan, Offset(x, ft + 2f), Offset(x, ft + fh - 2f), stroke * 0.45f, 0.75f)
        }
    }
    if (params.airCooled) {
        glowLine(Cyan, Offset(valley.x - 20f, plenumTop - h * 0.05f), Offset(valley.x + 20f, plenumTop - h * 0.05f), stroke * 0.6f, 0.6f)
    }
}
