package com.revline.idle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** Meters of road per unit of car, so a wheel turns by what the odometer says it should. */
internal const val METERS_PER_UNIT = 2.0625f

private const val TAU = (Math.PI * 2.0).toFloat()
private val rubber = Color(0xFF0A0A0C)
private val sidewall = Color(0xFF1E2024)
private val alloy = Color(0xFFC9D1D9)
private val alloyShade = Color(0xFF5B646E)
private val rotor = Color(0xFF2B3036)

private fun blurOf(omega: Float): Float = ((omega - 16f) / 44f).coerceIn(0f, 1f)

/**
 * One wheel in car units (y up), centered on [c]. [angle] is the roll in radians and [omega] its rate, which
 * decides how much of the spokes you can still see. Slicks (the rear) are smooth and wrinkle on launch; the
 * skinny fronts carry a groove and a brake caliper in the team color.
 */
internal fun DrawScope.drawDragWheel(
    c: Offset,
    r: Float,
    angle: Float,
    omega: Float,
    slick: Boolean,
    accent: Color,
    wrinkle: Float,
    px: Float,
) {
    val blur = blurOf(omega)
    // Tire: dark body that lifts a little toward the sidewall so it reads as round.
    drawCircle(
        Brush.radialGradient(0f to sidewall, 0.72f to rubber, 1f to Color.Black, center = c, radius = r),
        r, c,
    )
    // Road face: a soft sheen on the upper-left, and tread hints that turn with the wheel.
    drawCircle(Color.White.copy(alpha = 0.16f), r * 0.965f, c, style = Stroke(px * 1.1f))
    if (slick) {
        drawArc(
            Color.White.copy(alpha = 0.2f), 195f, 60f, false,
            Offset(c.x - r * 0.9f, c.y - r * 0.9f), Size(r * 1.8f, r * 1.8f), style = Stroke(px * 1.6f, cap = StrokeCap.Round),
        )
        // Wrinkles: the carcass twists on a hard launch, short arcs that rotate with the tire.
        if (wrinkle > 0.05f) {
            for (i in 0 until 5) {
                val a = angle + i * TAU / 5f
                val from = Offset(c.x + cos(a) * r * 0.9f, c.y + sin(a) * r * 0.9f)
                val to = Offset(c.x + cos(a + 0.22f) * r * 0.7f, c.y + sin(a + 0.22f) * r * 0.7f)
                drawLine(Color.White.copy(alpha = 0.22f * wrinkle * (1f - blur * 0.6f)), from, to, px * 1.2f, StrokeCap.Round)
            }
        }
    } else {
        drawCircle(Color.Black, r * 0.9f, c, style = Stroke(px * 1.3f))
        val ticks = 22
        for (i in 0 until ticks) {
            val a = angle + i * TAU / ticks
            drawLine(
                Color.White.copy(alpha = 0.3f * (1f - blur)),
                Offset(c.x + cos(a) * r * 0.93f, c.y + sin(a) * r * 0.93f),
                Offset(c.x + cos(a) * r * 0.985f, c.y + sin(a) * r * 0.985f), px, StrokeCap.Butt,
            )
        }
    }
    // Sidewall lettering: a dashed ring that spins with the wheel.
    val letters = 26
    for (i in 0 until letters step 2) {
        val a = angle * 1f + i * TAU / letters
        val a2 = a + TAU / letters * 0.7f
        drawLine(
            Color.White.copy(alpha = 0.34f * (1f - blur)),
            Offset(c.x + cos(a) * r * 0.78f, c.y + sin(a) * r * 0.78f),
            Offset(c.x + cos(a2) * r * 0.78f, c.y + sin(a2) * r * 0.78f), px * 1.3f, StrokeCap.Butt,
        )
    }
    val rim = r * if (slick) 0.56f else 0.6f
    // Brake disc seen through the spokes, with the caliper held still at the top rear.
    drawCircle(rotor, rim * 0.94f, c)
    drawCircle(Color.Black.copy(alpha = 0.5f), rim * 0.94f, c, style = Stroke(px))
    if (!slick) {
        drawArc(
            accent, 40f, 46f, false,
            Offset(c.x - rim * 0.72f, c.y - rim * 0.72f), Size(rim * 1.44f, rim * 1.44f), style = Stroke(rim * 0.2f, cap = StrokeCap.Butt),
        )
    }
    // Spokes.
    val spokeAlpha = 1f - 0.9f * blur
    if (spokeAlpha > 0.03f) {
        val spokes = if (slick) 5 else 6
        for (i in 0 until spokes) {
            val a = angle + i * TAU / spokes
            val dx = cos(a)
            val dy = sin(a)
            val from = Offset(c.x + dx * rim * 0.16f, c.y + dy * rim * 0.16f)
            val to = Offset(c.x + dx * rim * 0.93f, c.y + dy * rim * 0.93f)
            drawLine(alloyShade.copy(alpha = spokeAlpha), from, to, rim * 0.2f, StrokeCap.Round)
            drawLine(alloy.copy(alpha = spokeAlpha), from, to, rim * 0.13f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.7f * spokeAlpha), from, to, rim * 0.04f, StrokeCap.Round)
        }
    }
    if (blur > 0.02f) {
        // Spinning fast: the spokes smear into a bright disc with a few streaks still turning.
        drawCircle(
            Brush.radialGradient(0f to alloy.copy(alpha = 0.55f * blur), 1f to alloyShade.copy(alpha = 0.3f * blur), center = c, radius = rim),
            rim * 0.93f, c,
        )
        for (i in 0 until 3) {
            val a = angle * 0.4f + i * TAU / 3f
            drawArc(
                Color.White.copy(alpha = 0.3f * blur), Math.toDegrees(a.toDouble()).toFloat(), 55f, false,
                Offset(c.x - rim * 0.6f, c.y - rim * 0.6f), Size(rim * 1.2f, rim * 1.2f), style = Stroke(rim * 0.12f, cap = StrokeCap.Round),
            )
        }
    }
    // Rim lip, hub and lug nuts.
    drawCircle(alloy, rim, c, style = Stroke(rim * 0.09f))
    drawCircle(Color.Black.copy(alpha = 0.55f), rim * 0.93f, c, style = Stroke(px))
    drawCircle(Brush.radialGradient(0f to Color.White, 1f to alloyShade, center = c, radius = rim * 0.2f), rim * 0.2f, c)
    if (blur < 0.6f) {
        for (i in 0 until 5) {
            val a = angle + i * TAU / 5f
            drawCircle(Color.Black.copy(alpha = 0.7f), rim * 0.028f, Offset(c.x + cos(a) * rim * 0.12f, c.y + sin(a) * rim * 0.12f))
        }
    }
}
