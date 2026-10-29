package com.revline.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revline.idle.UiModel
import com.revline.idle.economy.formatDollars
import com.revline.idle.game.Racing
import java.util.Locale

@Composable
fun RaceScreen(
    ui: UiModel,
    onStudy: (String) -> Unit,
    onChassis: (String) -> Unit,
    onPart: (String, String) -> Unit,
    onSlots: (Int) -> Unit,
    onFinal: (String) -> Unit,
    onQuick: (Racing.Tier) -> Unit,
    onLadder: () -> Unit,
    onStake: () -> Unit,
) {
    val state = ui.game
    val car = Racing.car(state)
    val body = car.spec.chassis
    val level = state.chassisLevel[body.id] ?: 0
    val slots = car.spec.slots
    val slotMax = Racing.slotMax(body, level)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text("RACE", color = White, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
        Text(
            "${body.name}  ${car.massKg.toInt()} kg  ${car.wheelHp.toInt()} hp at the wheels  pace ${car.pace.toInt()}",
            color = Lime,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "${car.spec.place.name.lowercase(Locale.US)} ${car.spec.drive.name}  ducts $slots/$slotMax  fade ${(car.fade * 100).toInt()}%",
            color = if (car.fade > 0.2) Amber else White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        if (ui.raceNote != null) {
            Text(
                ui.raceNote,
                color = Cyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text("QUICK", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Three fields in this engine's class. You drive the quarter. Feather the launch, then shift.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Racing.Tier.entries.forEach { tier ->
                LineButton(tier.name.lowercase(Locale.US), true, { onQuick(tier) })
            }
        }
        Text("LADDER", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
        val rungs = Racing.ladderRungs()
        val rung = rungs[state.raceLadder.coerceIn(0, rungs.lastIndex)]
        Text(
            "Rung ${state.raceLadder + 1}/${rungs.size}: ${rung.title}, pace ${rung.pace.toInt()}. The field does not care what you drive.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        LineButton("Drive the rung", true, onLadder, Modifier.padding(top = 6.dp))
        Text("STAKE", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
        val stake = Racing.stakeCost(state.prestiges)
        Text(
            "A random car in this class. ${formatDollars(stake)} to enter. Win pays ${formatDollars(stake * 2)}. Lose pays nothing.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        LineButton("Drive for the stake", state.devMode || state.money >= stake, onStake, Modifier.padding(top = 6.dp))

        Text("CHASSIS", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Published weights. Lightening stops at a period race trim.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        ChassisRow("Shop mule", "640 kg  front RWD  1 duct", state.chassis == "mule", true, "Use the mule") { onChassis("mule") }
        Racing.chassis.forEach { item ->
            val study = "chassis_${item.id}"
            val owned = state.chassisLevel[item.id] ?: 0
            val open = item.minPrestige <= state.prestiges
            val known = study in state.raceStudies
            val label = when {
                !open -> "Needs a later engine"
                !known -> "Study · ${item.studyCost.toInt()} RP"
                state.chassis == item.id && owned < 3 -> "Lighten to ${owned + 1}/3 · ${formatDollars(Racing.chassisPrice(state, item, owned + 1))}"
                state.chassis == item.id -> "Fitted ${owned}/3"
                owned >= 1 -> "Fit it"
                else -> "Buy · ${formatDollars(Racing.chassisPrice(state, item, 1))}"
            }
            ChassisRow(
                item.name,
                "${item.kg.toInt()} kg  ${item.place.name.lowercase(Locale.US)} ${item.drive.name}  ${item.slots} ducts  ${item.source}",
                state.chassis == item.id,
                open,
                label,
            ) {
                if (!known) onStudy(study) else onChassis(item.id)
            }
        }

        Text("GEARBOX", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        ChipRow(Racing.gearboxes.map { it.id to it.name }, state.transmission) { id ->
            val box = Racing.gearboxes.first { it.id == id }
            if (box.study != null && box.study !in state.raceStudies) onStudy(box.study) else onPart("box", id)
        }
        val fitted = Racing.gearboxes.first { it.id == state.transmission }
        val ratioText = fitted.ratios.joinToString(" / ") { String.format(Locale.US, "%.2f", it) }
        Text(
            "Ratios $ratioText. A shift takes ${String.format(Locale.US, "%.2f", fitted.shift)}s.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text("FINAL DRIVE", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        Text(
            "Short leaves harder and runs out of rpm. Tall is lazy off the line and carries the top. The pace number stays put. The strip changes.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        ChipRow(
            listOf("short" to "Short 4.56", "road" to "Road 3.73", "tall" to "Tall 3.07"),
            state.finalDrive,
            onFinal,
        )
        Text("DRIVELINE", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        ChipRow(Racing.axles.map { it.id to it.name }, state.driveline) { id ->
            val axle = Racing.axles.first { it.id == id }
            if (axle.study != null && axle.study !in state.raceStudies) onStudy(axle.study) else onPart("axle", id)
        }
        Text("TIRES", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        ChipRow(Racing.tires.map { it.id to it.name }, state.tire) { id ->
            val tire = Racing.tires.first { it.id == id }
            if (tire.study != null && tire.study !in state.raceStudies) onStudy(tire.study) else onPart("tire", id)
        }
        Text("DRIVE", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        ChipRow(Racing.Drive.entries.map { it.name to it.name }, car.spec.drive.name) { onPart("drive", it) }
        Text("ENGINE", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        ChipRow(Racing.Place.entries.map { it.name to it.name }, car.spec.place.name) { onPart("place", it) }
        Text("DUCTS", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineButton("Blank one", slots > 0, { onSlots(slots - 1) })
            LineButton("Open one", slots < slotMax, { onSlots(slots + 1) })
        }
        Text(
            "A duct lets heat out and adds a little drag. A small engine does not need many. A big one fades without them.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun ChassisRow(title: String, detail: String, on: Boolean, enabled: Boolean, action: String, click: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .border(1.dp, if (on) Lime else if (enabled) White else Olive)
            .padding(8.dp),
    ) {
        Text(title, color = if (on) Lime else White, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        Text(detail, color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        LineButton(action, enabled && action != "Fitted ${3}/3", click, Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (id, label) ->
            val on = id == selected
            Text(
                label,
                color = if (on) Black else White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(if (on) Lime else Black)
                    .border(1.dp, if (on) Lime else White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPick(id) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
