package com.revline.idle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

fun DrawScope.glowLine(color: Color, start: Offset, end: Offset, width: Float, gain: Float = 1f) {
    val g = gain.coerceIn(0f, 1.8f)
    if (g <= 0.02f) return
    drawLine(color.copy(alpha = 0.13f * g), start, end, width * 7f, StrokeCap.Round)
    drawLine(color.copy(alpha = 0.38f * g), start, end, width * 2.5f, StrokeCap.Round)
    drawLine(color, start, end, width, StrokeCap.Round)
}

fun DrawScope.glowCircle(
    color: Color,
    radius: Float,
    center: Offset,
    width: Float,
    filled: Boolean = false,
    gain: Float = 1f,
) {
    val g = gain.coerceIn(0f, 1.8f)
    if (g <= 0.02f || radius <= 0.4f) return
    if (filled) {
        drawCircle(color.copy(alpha = 0.14f * g), radius * 2.6f, center)
        drawCircle(color.copy(alpha = 0.42f * g), radius * 1.4f, center)
        drawCircle(color, radius, center)
    } else {
        drawCircle(color.copy(alpha = 0.14f * g), radius, center, style = Stroke(width * 6f))
        drawCircle(color.copy(alpha = 0.40f * g), radius, center, style = Stroke(width * 2.3f))
        drawCircle(color, radius, center, style = Stroke(width))
    }
}

fun DrawScope.glowArc(
    color: Color,
    startAngle: Float,
    sweep: Float,
    topLeft: Offset,
    size: Size,
    width: Float,
    gain: Float = 1f,
) {
    val g = gain.coerceIn(0f, 1.8f)
    if (g <= 0.02f || sweep <= 0.4f) return
    drawArc(color.copy(alpha = 0.16f * g), startAngle, sweep, false, topLeft, size, style = Stroke(width * 6.5f, cap = StrokeCap.Round))
    drawArc(color.copy(alpha = 0.42f * g), startAngle, sweep, false, topLeft, size, style = Stroke(width * 2.5f, cap = StrokeCap.Round))
    drawArc(color, startAngle, sweep, false, topLeft, size, style = Stroke(width, cap = StrokeCap.Round))
}

fun DrawScope.glowPath(
    color: Color,
    path: Path,
    width: Float,
    gain: Float = 1f,
    dashed: Boolean = false,
) {
    val g = gain.coerceIn(0f, 1.8f)
    if (g <= 0.02f) return
    val dash = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 9f), 0f) else null
    fun stroke(w: Float) = Stroke(width = w, cap = StrokeCap.Round, pathEffect = dash)
    drawPath(path, color.copy(alpha = 0.13f * g), style = stroke(width * 5.5f))
    drawPath(path, color.copy(alpha = 0.36f * g), style = stroke(width * 2.3f))
    drawPath(path, color, style = stroke(width))
}
