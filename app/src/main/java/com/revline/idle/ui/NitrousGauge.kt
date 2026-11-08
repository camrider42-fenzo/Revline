package com.revline.idle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revline.idle.game.RaceDrive
import java.util.Locale

/**
 * Drag-strip nitrous lamp and bottle. Grey with no kit. Amber while armed,
 * lime while the solenoid is open, red when the bottle is dry.
 */
@Composable
fun NitrousGauge(read: RaceDrive.Bottle, modifier: Modifier = Modifier) {
    val color = when (read.phase) {
        RaceDrive.BottlePhase.OFF -> Olive
        RaceDrive.BottlePhase.ARMED -> Amber
        RaceDrive.BottlePhase.ACTIVE -> Lime
        RaceDrive.BottlePhase.EMPTY -> Red
    }
    val word = when (read.phase) {
        RaceDrive.BottlePhase.OFF -> "NO KIT"
        RaceDrive.BottlePhase.ARMED -> "ARMED"
        RaceDrive.BottlePhase.ACTIVE -> "ACTIVE"
        RaceDrive.BottlePhase.EMPTY -> "EMPTY"
    }
    val left = if (read.phase == RaceDrive.BottlePhase.OFF) "" else String.format(Locale.US, "%.1fs", read.secondsLeft)
    Column(
        modifier.semantics {
            contentDescription = "nitrous ${read.phase.name} ${String.format(Locale.US, "%.2f", read.fill)}"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(word, color = color, fontFamily = FontFamily.Monospace, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 1.dp)) {
            drawNitrousBottle(color, read.fill.toFloat(), read.phase == RaceDrive.BottlePhase.ACTIVE)
        }
        Text(left, color = color, fontFamily = FontFamily.Monospace, fontSize = 9.sp, maxLines = 1)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNitrousBottle(color: Color, fill: Float, flowing: Boolean) {
    val w = size.width.coerceAtMost(size.height * 0.55f)
    val left = (size.width - w) / 2f
    val neckW = w * 0.28f
    val neckH = size.height * 0.12f
    val neckLeft = (size.width - neckW) / 2f
    val bodyTop = neckH + 1f
    val body = Size(w, (size.height - bodyTop).coerceAtLeast(1f))
    val ink = if (flowing) color else color.copy(alpha = 0.85f)
    drawRoundRect(ink.copy(alpha = 0.35f), Offset(neckLeft, 0f), Size(neckW, neckH), CornerRadius(2f, 2f))
    drawRoundRect(ink.copy(alpha = 0.22f), Offset(left, bodyTop), body, CornerRadius(w * 0.18f, w * 0.18f))
    val level = fill.coerceIn(0f, 1f)
    if (level > 0.01f) {
        val fh = body.height * level
        drawRoundRect(
            ink.copy(alpha = if (flowing) 0.95f else 0.72f),
            Offset(left + 1.5f, bodyTop + body.height - fh),
            Size((body.width - 3f).coerceAtLeast(1f), fh),
            CornerRadius(w * 0.12f, w * 0.12f),
        )
    }
    drawRoundRect(
        ink,
        Offset(left, bodyTop),
        body,
        CornerRadius(w * 0.18f, w * 0.18f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f),
    )
}
