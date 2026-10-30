package com.revline.idle.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revline.idle.UiModel
import com.revline.idle.economy.formatDollars
import com.revline.idle.game.Tuners
import com.revline.idle.model.EngineSim
import java.util.Locale

@Composable
fun TuneScreen(ui: UiModel, onNudge: (String, Int) -> Unit) {
    val stats = ui.sheet.stats
    val params = ui.sheet.params
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("TUNE", color = White, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
        Text(
            "Screws stay locked until the part is on the engine. More of that part, and the parts that refine it, make the clicks smaller.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Text(
            "Sheet ${formatPower(stats.peakHorsepower)} hp   ${formatPower(stats.peakTorqueLbFt)} lb-ft   " +
                "${String.format(Locale.US, "%.1f", ui.sheet.profit.mpg)} mpg@\$   ${formatDollars(ui.sheet.incomePerSec)}/s",
            color = Lime,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Text(
            "WOT AFR ${String.format(Locale.US, "%.2f", EngineSim.wotAfr(params))}   " +
                "cruise AFR ${String.format(Locale.US, "%.2f", EngineSim.cruiseAfr(params))}",
            color = Lime,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Tuners.all.forEach { screw ->
            ScrewRow(ui, screw.id, onNudge)
        }
    }
}

@Composable
private fun ScrewRow(ui: UiModel, id: String, onNudge: (String, Int) -> Unit) {
    val screw = Tuners.byId(id)
    val open = Tuners.unlocked(ui.game, id)
    Column(Modifier.fillMaxWidth().border(1.dp, if (open) Lime else Olive).padding(8.dp)) {
        Text(screw.name, color = White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text(
            screw.detail,
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!open) {
            Text(
                screw.lock,
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            return
        }
        val step = Tuners.step(ui.game, id)
        Text(
            "Step ${formatStep(step)} ${screw.unit}   ·   ${screw.parts.joinToString(" · ") { partLevel(ui, it) }}",
            color = Cyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineButton("−", true, { onNudge(id, -1) })
            Text(
                Tuners.format(ui.game, id),
                color = Lime,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
            )
            LineButton("+", true, { onNudge(id, 1) })
        }
    }
}

private fun partLevel(ui: UiModel, id: String): String {
    val name = com.revline.idle.game.Upgrades.byId(id).name
    val level = ui.game.upgrades[id] ?: 0
    return "$name $level"
}

private fun formatStep(step: Double): String = when {
    step >= 10.0 -> String.format(Locale.US, "%.0f", step)
    step >= 0.1 -> String.format(Locale.US, "%.1f", step)
    else -> String.format(Locale.US, "%.2f", step)
}
