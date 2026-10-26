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
    parts: Map<String, Int> = emptyMap(),
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
            contentDescription = "${params.blockTitle}, ${parts.count { it.value > 0 }} parts fitted, ${rpm.toInt()} rpm"
        },
    ) {
        val w = size.width
        val h = size.height
        val stroke = (w / 170f).coerceIn(1.7f, 3.4f)
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
        val rod = h * if (n == 1) 0.20f else 0.155f
        val phases = firingPhases(perBank)
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

        val collectorX = right + w * (0.03f + 0.018f * headerLvl + 0.02f * longLvl)
        val tailX = collectorX + w * (0.03f + 0.012f * catLvl)
        val tailDrop = h * 0.012f * tipLvl
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
            glowLine(White, Offset(left - 4f, camY), Offset(right - 8f, camY), stroke * 0.65f, 0.9f)
            for (i in 0 until n) {
                val cx = left + gap * (i + 0.5f)
                glowCircle(Lime, 3.5f + camLvl * 1.3f, Offset(cx, camY), stroke * 0.55f, gain = 0.95f)
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
        if (params.induction == Induction.SUPERCHARGER) {
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

        if (params.banks >= 2) {
            drawVee(params, parts, w, h, stroke, angle, exhaust, exhaustGain, perBank, gap)
        } else for (i in 0 until perBank) {
            val cx = left + gap * (i + 0.5f)
            val th = Math.toRadians((angle + phases[i]).toDouble())
            val pinX = cx + (sin(th) * crankThrow).toFloat()
            val pinY = crankY - (cos(th) * crankThrow).toFloat()
            val counterX = cx - (sin(th) * crankThrow * 0.72f).toFloat()
            val counterY = crankY + (cos(th) * crankThrow * 0.72f).toFloat()
            val horiz = pinX - cx
            val rodY = sqrt((rod * rod - horiz * horiz).coerceAtLeast(4f))
            val crown = pinY - rodY
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
            if (springLvl > 0) {
                val coils = 2 + springLvl
                for (coil in 0 until coils) {
                    val sy = h * 0.31f - coil * h * 0.012f
                    val zig = if (coil % 2 == 0) -3f else 3f
                    glowLine(White, Offset(cx + zig, sy), Offset(cx - zig, sy + h * 0.01f), stroke * 0.4f, 0.65f)
                }
            }
            if (studLvl > 0) {
                glowCircle(White, stroke * (0.7f + 0.15f * studLvl), Offset(cx - skirt, h * 0.40f), stroke, filled = true, gain = 0.6f)
                glowCircle(White, stroke * (0.7f + 0.15f * studLvl), Offset(cx + skirt, h * 0.40f), stroke, filled = true, gain = 0.6f)
            }
            glowLine(crownColor, Offset(cx - skirt, crown), Offset(cx + skirt, crown), stroke, 1.15f)
            glowLine(White, Offset(cx - skirt, crown + crownDrop), Offset(cx + skirt, crown + crownDrop), stroke)
            glowLine(White, Offset(cx - skirt, crown), Offset(cx - skirt, crown + crownDrop), stroke)
            glowLine(White, Offset(cx + skirt, crown), Offset(cx + skirt, crown + crownDrop), stroke)
            glowLine(Amber, Offset(cx, crown + crownDrop), Offset(pinX, pinY), stroke * 0.9f, 0.95f)
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

private fun firingPhases(count: Int): FloatArray = when (count) {
    1 -> floatArrayOf(0f)
    2 -> floatArrayOf(0f, 180f)
    3 -> floatArrayOf(0f, 240f, 120f)
    4 -> floatArrayOf(0f, 180f, 180f, 0f)
    else -> FloatArray(count) { index -> (index * (360f / count)) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVee(
    params: EngineParams,
    parts: Map<String, Int>,
    w: Float,
    h: Float,
    stroke: Float,
    angle: Float,
    exhaust: androidx.compose.ui.graphics.Color,
    exhaustGain: Float,
    perBank: Int,
    gap: Float,
) {
    val crankY = h * 0.64f
    val mid = w * 0.50f
    val span = gap * perBank
    val left = mid - span / 2f
    glowLine(androidx.compose.ui.graphics.Color.White, Offset(left, crankY), Offset(left + span, crankY), stroke, 0.9f)
    val crankThrow = h * 0.035f
    for (bank in 0..1) {
        val sign = if (bank == 0) -1f else 1f
        val tilt = 0.72f
        for (i in 0 until perBank) {
            val along = left + gap * (i + 0.5f)
            val phase = angle + bank * 90f + i * (360f / perBank)
            val th = Math.toRadians(phase.toDouble())
            val lift = (cos(th) * crankThrow).toFloat()
            val pinX = along + sign * tilt * h * 0.02f
            val pinY = crankY - lift
            val topX = along + sign * h * 0.20f
            val topY = crankY - h * 0.28f
            val crownX = topX - sign * tilt * lift
            val crownY = topY - lift
            glowLine(androidx.compose.ui.graphics.Color.White, Offset(along, crankY - h * 0.02f), Offset(topX, topY), stroke * 0.75f, 0.8f)
            val skirt = 7f + (parts["bore"] ?: 0) * 1.6f
            val crownColor = if ((parts["pistons"] ?: 0) > 0) Lime else Cyan
            glowLine(crownColor, Offset(crownX - skirt, crownY), Offset(crownX + skirt, crownY), stroke, 1.05f)
            if ((parts["headers"] ?: 0) > 0) {
                glowLine(exhaust, Offset(topX, topY), Offset(w * (0.84f + 0.015f * (parts["headers"] ?: 0)), h * 0.28f), stroke * 0.55f, exhaustGain)
            }
            glowLine(Amber, Offset(crownX, crownY), Offset(pinX, pinY), stroke * 0.8f, 0.9f)
            glowCircle(androidx.compose.ui.graphics.Color.White, stroke, Offset(pinX, pinY), stroke, filled = true, gain = 0.7f)
            val fromTdc = min(kotlin.math.abs(phase % 360f), 360f - kotlin.math.abs(phase % 360f))
            if (fromTdc < 28f) {
                val spark = 1f - fromTdc / 28f
                glowCircle(androidx.compose.ui.graphics.Color.White, stroke * (1f + spark * 3f), Offset(topX, topY + 6f), stroke, filled = true, gain = spark)
                glowLine(exhaust, Offset(topX, topY), Offset(w * 0.92f, h * 0.30f), stroke * 0.45f, spark * exhaustGain)
            }
        }
    }
    if (params.airCooled) {
        glowLine(Cyan, Offset(mid - 20f, h * 0.22f), Offset(mid + 20f, h * 0.22f), stroke * 0.6f, 0.6f)
    }
}
