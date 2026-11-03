package com.revline.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.revline.idle.game.Circuits

/**
 * The race on its own full screen: the track map, the tachometer and speedometer, the speed control, and at the flag each car's
 * best-lap speed trace. The map lives only here; the race screen under it never draws one.
 */
@Composable
internal fun CircuitRaceScreen(
    result: Circuits.Result, yourId: String, yourMid: Boolean, session: Any? = null, onRevise: ((Circuits.Orders, Int) -> Unit)? = null, onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CircuitRaceBody(result, yourId, yourMid, session, onRevise, onClose)
    }
}

/** The screen's contents, filling whatever it is given. */
@Composable
internal fun CircuitRaceBody(
    result: Circuits.Result, yourId: String, yourMid: Boolean, session: Any? = null, onRevise: ((Circuits.Orders, Int) -> Unit)? = null, onClose: () -> Unit,
) {
    run {
        // Wide or near-square (a landscape phone, the unfolded Fold) puts the map beside the dials; a tall narrow screen stacks them.
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(horizontal = 12.dp, vertical = 16.dp)) {
            val wide = maxWidth >= maxHeight * 0.75f
            CircuitReplayView(result, yourId, yourMid, Modifier.fillMaxSize(), wide, header = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(result.circuit.name.uppercase(java.util.Locale.US), color = Lime, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    LineButton("Close", true, onClose)
                }
            }, endContent = { m ->
                val replay = remember(result) { Replay.build(result) }
                if (replay != null) BestLapTraces(replay, m, fit = true)
            }, session = session, onRevise = onRevise)
        }
    }
}
