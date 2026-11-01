package com.revline.idle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import com.revline.idle.game.CircuitPace
import kotlin.math.roundToInt

/** Width over height of the dash. */
internal const val DASH_ASPECT = 2.5f

/** The player's car on the race screen: tachometer, speedometer in mph (the unit of the drag screen), gear and a brake lamp. [dash] is null before the start. */
@Composable
internal fun CircuitDash(dash: CircuitPace.Dash?, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().aspectRatio(DASH_ASPECT)) { drawCircuitDash(dash) }
}

/** The dash of [CircuitDash], drawn into whatever canvas it is handed. */
internal fun DrawScope.drawCircuitDash(dash: CircuitPace.Dash?) {
    drawRect(Color.Black)
    val h = size.height
    val redline = dash?.redline ?: 8000.0
    val rpm = dash?.rpm ?: dash?.idle ?: 0.0
    val hot = dash != null && dash.rpm >= redline * 0.975
    // The dial takes the left square, the readouts the rest.
    inset(0f, 0f, size.width - h, 0f) { drawTach(rpm.toFloat(), redline, (redline * 0.9).toFloat(), hot) }
    val x0 = h * 1.02f
    val w = size.width - x0
    // The speed: big, right aligned, with its unit.
    val mph = dash?.mph?.roundToInt() ?: 0
    textLabel("SPEED", x0 + w * 0.05f, h * 0.20f, h * 0.10f, Olive, 0)
    textLabel(mph.toString(), x0 + w * 0.62f, h * 0.60f, h * 0.44f, if (hot) Red else Lime, 2)
    textLabel("MPH", x0 + w * 0.64f, h * 0.60f, h * 0.12f, Olive, 0)
    // The gear and the brake lamp, along the bottom.
    val gearText = when {
        dash == null || dash.speed < 0.5 -> "N"
        else -> dash.gear.toString()
    }
    val boxTop = h * 0.70f
    val boxH = h * 0.24f
    val boxW = w * 0.20f
    val gx = x0 + w * 0.05f
    drawRect(Lime.copy(alpha = 0.10f), Offset(gx, boxTop), Size(boxW, boxH))
    drawRect(Lime, Offset(gx, boxTop), Size(boxW, boxH), style = Stroke(width = (h / 90f).coerceAtLeast(1.5f)))
    textLabel(gearText, gx + boxW / 2f, boxTop + boxH * 0.78f, boxH * 0.80f, Lime, 1)
    textLabel("GEAR", gx + boxW + w * 0.03f, boxTop + boxH * 0.72f, h * 0.10f, Olive, 0)
    val braking = dash?.braking == true
    val lx = x0 + w * 0.60f
    drawRect(if (braking) Red else Red.copy(alpha = 0.12f), Offset(lx, boxTop), Size(w * 0.34f, boxH))
    textLabel("BRAKE", lx + w * 0.17f, boxTop + boxH * 0.68f, boxH * 0.52f, if (braking) Color.Black else Red.copy(alpha = 0.5f), 1)
}
