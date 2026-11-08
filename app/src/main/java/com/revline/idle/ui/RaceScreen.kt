package com.revline.idle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.revline.idle.game.Drivers
import com.revline.idle.game.Circuits
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.revline.idle.game.RaceDrive
import com.revline.idle.game.RaceLog
import com.revline.idle.game.Rivals
import com.revline.idle.game.Season
import com.revline.idle.game.Tires
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revline.idle.UiModel
import com.revline.idle.data.GameState
import com.revline.idle.economy.formatDollars
import com.revline.idle.game.Parts
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
    onLength: (String) -> Unit,
    onQuick: (Racing.Tier) -> Unit,
    onLadder: () -> Unit,
    onStake: () -> Unit,
    onHire: (String) -> Unit = {},
    onPick: (String) -> Unit = {},
    onCircuit: (String, String, Circuits.Orders) -> Unit = { _, _, _ -> },
    onRevise: (Circuits.Orders, Int) -> Unit = { _, _ -> },
) {
    val state = ui.game
    val car = Racing.car(state)
    val body = car.spec.chassis
    val level = state.chassisLevel[body.id] ?: 0
    val slots = car.spec.slots
    val slotMax = Racing.slotMax(body, level)
    var page by rememberSaveable { mutableStateOf("DRAG") }
    var circuitId by rememberSaveable { mutableStateOf(Season.next(state).id) }
    val handled = remember { arrayOf(ui.circuitOutcome) }
    LaunchedEffect(ui.circuitOutcome) {
        val outcome = ui.circuitOutcome
        if (outcome != null && outcome !== handled[0]) circuitId = Season.next(ui.game).id
        handled[0] = outcome
    }
    var armed by rememberSaveable { mutableStateOf(false) }
    var watching by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ui.circuitOutcome, page, armed) {
        if (page == "CIRCUIT" && armed && ui.circuitOutcome?.result?.log?.rows?.isNotEmpty() == true) {
            watching = true
            armed = false
        }
    }
    val dragScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val circuitScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val pages = rememberSaveableStateHolder()
    val setup = RaceDrive.setup(state, ui.sheet)
    val forecast by produceState<Map<String, Drivers.Forecast>>(emptyMap(), setup) {
        value = withContext(Dispatchers.Default) { Drivers.forecast(setup).associateBy { it.driver.id } }
    }
    pages.SaveableStateProvider(page) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(if (page == "DRAG") dragScroll else circuitScroll)
            .padding(12.dp),
    ) {
        Text("RACE", color = White, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("DRAG", "CIRCUIT").forEach { tab ->
                LineButton(tab, page != tab, { page = tab })
            }
        }
        Text(
            "${body.name}  ${car.massKg.toInt()} kg  dyno ${car.wheelHp.toInt()} hp  pace ${car.pace.toInt()}",
            color = if (car.fits) Lime else Amber,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "${car.spec.place.name.lowercase(Locale.US)} ${car.spec.drive.name}  ducts $slots/$slotMax  fade ${(car.fade * 100).toInt()}%" +
                if (car.fits) "" else "  ENGINE DOES NOT FIT",
            color = if (!car.fits || car.fade > 0.2) Amber else White,
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
        if (page == "DRAG") {
        val bottle = RaceDrive.bottle(setup, RaceDrive.fresh(setup), racing = false)
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            NitrousGauge(bottle, Modifier.width(72.dp).fillMaxHeight())
            Text(
                if (bottle.phase == RaceDrive.BottlePhase.OFF) {
                    "No drag nitrous kit. The bottle stays dark until one is fitted."
                } else {
                    "Bottle ${String.format(Locale.US, "%.0f", setup.bottleS)}s. A new pass fills it. It drains while the shot is open."
                },
                color = if (bottle.phase == RaceDrive.BottlePhase.OFF) Olive else White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
        }
        Text("DISTANCE", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
        Text(
            "An eighth is the launch. A quarter is the gears. A half mile is the top end. The pace number follows that distance.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        ChipRow(Racing.Length.entries.map { it.id to it.title }, state.raceLength, onLength)
        DriverToggle(state, forecast, onPick)
        Text("QUICK", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Club is a heavy car. National is a medium one. International is a light car with a milder engine. You drive the distance above.",
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
        val rungs = Racing.ladderRungs(Racing.lengthOf(state.raceLength))
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
        Text("DRAG TIRES", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        Text(
            "For the strip. Grip off the line, and nothing else. Circuit races use the circuit tires on the Circuit page, whatever is fitted here. A better tire is a study, then the price.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        ChipRow(Racing.tires.map { it.id to Racing.gate(state, "tire", it.id, it.name).label }, state.tire) { id ->
            val tire = Racing.tires.first { it.id == id }
            val gate = Racing.gate(state, "tire", id, tire.name)
            if (gate.study != null) onStudy(gate.study) else onPart("tire", id)
        }
        Text("DRAG PARTS", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        Text(
            "Launch control, a transbrake, wheelie bars, a light flywheel, a short shifter, a line lock and a nitrous kit. They work on the strip only, at full price. Several open a knob on the Tune page's Drag tab.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Parts.strip.forEach { line -> KitLine(state, line, onStudy, onPart) }
        } else {
        CircuitSection(ui, car, circuitId, { circuitId = it }, watching, { watching = it }, { armed = true }, onCircuit, onRevise)
        Text("CIRCUIT TIRES", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        Text(
            "For corners and brakes. A softer tire grips more and wears faster, most on a hot circuit. Strips use the drag tires on the Drag page. Costs a study, then the price.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        ChipRow(Racing.circuitTires.map { it.id to Racing.gate(state, "ctire", it.id, it.name).label }, state.circuitTire) { id ->
            val tire = Racing.circuitTires.first { it.id == id }
            val gate = Racing.gate(state, "ctire", id, tire.name)
            if (gate.study != null) onStudy(gate.study) else onPart("ctire", id)
        }
        Racing.circuitTires.firstOrNull { it.id == state.circuitTire }?.let { t ->
            Text(
                String.format(Locale.US, "Grip %.2f, loses %.1f%% a lap on a mild track. For circuits only.", t.mu, t.wear * 100.0),
                color = Cyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text("CIRCUIT PARTS", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        Text(
            "Brakes, suspension, aero and the rest. Each moves a lap a lot and a drag run a little. A study, then the price. One level of a line is fitted at a time.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Parts.lines.forEach { line -> KitLine(state, line, onStudy, onPart) }
        }

        Text("CHASSIS", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 16.dp))
        Text(
            "A light car is quickest on the strip and worst on the dyno. The bay is small and the radiator is small. A medium car is the middle, and a few of them sat the engine in more than one place. A heavy car takes a much larger engine, reads higher on the dyno, and is slower on the track.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        val quickestId = Racing.quickest(state)?.id
        ChassisRow("Shop mule", "640 kg  front  fits the engine you have  1 duct", state.chassis == "mule", true, "Use the mule") { onChassis("mule") }
        com.revline.idle.model.Block.primarySteps().forEach { block ->
            val group = Racing.chassis.filter { it.group == block.ladderStep }
            if (group.isEmpty()) return@forEach
            if (block.ladderStep > state.prestiges) {
                Text(
                    "${block.title}  ·  three cars, with a later engine",
                    color = Olive,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                return@forEach
            }
            Text(block.title.uppercase(Locale.US), color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            group.forEach { item ->
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
                val places = item.places.joinToString("/") { it.name.lowercase(Locale.US) }
                val bay = if (item.maxCc < 800) "${item.maxCc.toInt()} cc" else String.format(Locale.US, "%.1f L", item.maxCc / 1000.0)
                val look = Racing.forecast(state, item)
                val outlook = if (look.fits) {
                    "dyno ${look.wheelHp.toInt()}  pace ${look.pace.toInt()}  fade ${(look.fade * 100).toInt()}%"
                } else {
                    "this engine will not fit"
                }
                val star = if (item.id == quickestId) "  ·  quickest" else ""
                ChassisRow(
                    "${item.build.name.lowercase(Locale.US)}  ${item.name}$star",
                    "$outlook  ·  ${item.kg.toInt()} kg  $places  bay $bay  ·  ${item.source}",
                    state.chassis == item.id,
                    open,
                    label,
                ) {
                    if (!known) onStudy(study) else onChassis(item.id)
                }
            }
        }

        Text("GEARBOX", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        Text(
            "The mule starts on the 4-speed. Another box is a study, then the price.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        ChipRow(Racing.gearboxes.map { it.id to Racing.gate(state, "box", it.id, it.name).label }, state.transmission) { id ->
            val gate = Racing.gate(state, "box", id, Racing.gearboxes.first { it.id == id }.name)
            if (gate.study != null) onStudy(gate.study) else onPart("box", id)
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
            "Short leaves harder and runs out of rpm. Tall is lazy off the line and carries the top. The road gear is already in the mule. The other two are a study, then the price. The pace number does not see the gear. The strip does.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        ChipRow(
            Racing.finals.map { it.id to Racing.gate(state, "final", it.id, it.name).label },
            state.finalDrive,
        ) { id ->
            val gear = Racing.finals.first { it.id == id }
            val gate = Racing.gate(state, "final", id, gear.name)
            if (gate.study != null) onStudy(gate.study) else onFinal(id)
        }
        val gearKey = listOf(
            state.finalDrive,
            state.raceLength,
            state.chassis,
            state.transmission,
            state.driveline,
            state.tire,
            state.circuitTire,
            state.kit,
            state.enginePlace,
            ui.sheet.stats.peakHorsepower.toInt(),
            ui.sheet.stats.peakTorqueLbFt.toInt(),
        )
        val gearLine = remember(gearKey) {
            val times = Racing.gearTimes(state)
            val best = times.minBy { it.value }
            val now = times[state.finalDrive] ?: best.value
            val name = when (best.key) {
                "short" -> "Short"
                "tall" -> "Tall"
                else -> "Road"
            }
            if (best.key == state.finalDrive) {
                "$name is the quick gear here, ${fmtSeconds(now)}."
            } else {
                "$name is ${fmtSeconds(now - best.value)} quicker on this distance."
            }
        }
        Text(
            gearLine,
            color = Cyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text("DRIVELINE", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        ChipRow(Racing.axles.map { it.id to Racing.gate(state, "axle", it.id, it.name).label }, state.driveline) { id ->
            val gate = Racing.gate(state, "axle", id, Racing.axles.first { it.id == id }.name)
            if (gate.study != null) onStudy(gate.study) else onPart("axle", id)
        }
        Text("LAYOUT", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 14.dp))
        if (body.places.size == 1) {
            Text(
                "${body.places.first().name.lowercase(Locale.US)} engine, ${car.spec.drive.name}. This car was not built the other ways.",
                color = White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        } else {
            Text(
                "Front is the road car, and it pulls the front wheels. Mid is the Turbo, and it drives the rears.",
                color = White,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            ChipRow(
                body.places.map { spot ->
                    val drive = Racing.driveFor(body, spot)
                    spot.name to "${spot.name.lowercase(Locale.US)} ${drive.name}"
                },
                car.spec.place.name,
            ) { onPart("place", it) }
        }
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
        DriverRoster(state, forecast, onHire)
    }
    }
}

private fun fmtSeconds(seconds: Double): String =
    String.format(Locale.US, "%.2fs", kotlin.math.abs(seconds))

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
private fun KitLine(state: GameState, line: Parts.Line, onStudy: (String) -> Unit, onPart: (String, String) -> Unit) {
    val now = Parts.level(state, line.id)
    Text(line.title, color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    Text(line.blurb, color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    ChipRow(
        listOf("0" to "Stock") + line.levels.mapIndexed { i, lv -> "${i + 1}" to Racing.gate(state, line.id, "${i + 1}", lv.name).label },
        now.toString(),
    ) { id ->
        val gate = Racing.gate(state, line.id, id, line.level(id.toInt())?.name ?: "Stock")
        if (gate.study != null) onStudy(gate.study) else onPart(line.id, id)
    }
    Text(
        if (now == 0) "Stock." else "Fitted: ${line.level(now)?.name}. ${Parts.hint(line, now)}",
        color = Cyan,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
    line.level(now + 1)?.let { next ->
        Text(
            "Next, ${next.name}: ${Parts.hint(line, now + 1)}",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
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

/** Fee on the next ladder rung, the race a driver is most often hired for. */
private fun rungFee(state: com.revline.idle.data.GameState, driver: Drivers.Driver): Double =
    Drivers.fee(state, driver, Racing.fieldForLadder(state))

@Composable
private fun DriverToggle(state: com.revline.idle.data.GameState, forecast: Map<String, Drivers.Forecast>, onPick: (String) -> Unit) {
    Text("DRIVER", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
    val hired = Drivers.hired(state)
    val picked = Drivers.selected(state)
    if (hired.isEmpty()) {
        Text(
            "You drive. Hire someone below and he can take any race for a share of its purse.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    } else {
        Text(
            if (picked == null) "DRIVE MYSELF. No fee." else "${picked.name} drives for ${(picked.rate * 100).let { String.format(Locale.US, "%.1f", it) }}% of the purse, paid when the lights come up.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
    val options = listOf("" to "DRIVE MYSELF") + hired.map { it.id to it.name.uppercase(Locale.US) }
    ChipRow(options, picked?.id ?: "", onPick)
    if (picked != null) {
        val fit = forecast[picked.id]
        Text(
            "Rung fee ${formatDollars(rungFee(state, picked))}" + if (fit != null) "  ${fit.fit.label} for this car" else "",
            color = Cyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DriverRoster(state: com.revline.idle.data.GameState, forecast: Map<String, Drivers.Forecast>, onHire: (String) -> Unit) {
    Text("DRIVERS", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
    Text(
        "Hiring is free. A driver costs a share of each purse, paid when he drives. Each runs the same car you do, with his own hands. A specialist beats a better all-rounder on the car he suits, so read the fit.",
        color = White,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
    )
    Drivers.roster.forEach { driver ->
        val open = Drivers.unlocked(state, driver)
        val hired = driver.id in state.hiredDrivers
        val fit = forecast[driver.id]
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(
                "${driver.name}  tier ${driver.tier}  ${driver.specialty.title}",
                color = if (open) Cyan else Olive,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            if (!open) {
                Text(
                    "Signs after ${Drivers.tierUnlock(driver.tier)} rebuilds.",
                    color = Olive,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                return@Column
            }
            Text(driver.bio, color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Drivers.Stat.entries.forEach { stat -> StatBar(stat.title, driver.stat(stat), Drivers.statOf(driver.specialty) == stat) }
            Text("CIRCUIT  overall ${driver.overall}/100", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            StatBar("cornering", driver.cornering, false)
            StatBar("braking", driver.braking, false)
            StatBar("accel", driver.acceleration, false)
            StatBar("mistakes", driver.mistakes, false, lowerIsBetter = true)
            StatBar("engine care", driver.care, false)
            Text(
                "Fee ${String.format(Locale.US, "%.1f", driver.rate * 100)}% of the purse  (rung ${formatDollars(rungFee(state, driver))})" +
                    if (fit != null) "   ${fit.fit.label}  ${String.format(Locale.US, "%.0f", fit.jumpRate * 100)}% red lights" else "",
                color = when (fit?.fit) {
                    Drivers.Fit.BEST -> Lime
                    Drivers.Fit.GOOD -> Cyan
                    Drivers.Fit.FAIR -> Amber
                    Drivers.Fit.POOR -> Red
                    null -> White
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (hired) Text("HIRED", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            else LineButton("Hire ${driver.name.substringBefore(' ')}", true, { onHire(driver.id) }, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StatBar(label: String, value: Int, spec: Boolean, lowerIsBetter: Boolean = false) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
        Text(label, color = if (spec) Lime else White, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.width(72.dp))
        Row(Modifier.width(140.dp).height(8.dp).border(1.dp, Olive)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value / 100f).background(if (spec) Lime else if (lowerIsBetter) Amber else White))
        }
        Text("  $value" + if (lowerIsBetter) "  lower is better" else "", color = White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

private fun aspectLabel(a: Circuits.Aspect) = when (a) {
    Circuits.Aspect.ACCELERATION -> "accel"
    Circuits.Aspect.TOP_SPEED -> "top speed"
    Circuits.Aspect.WEIGHT -> "weight"
    Circuits.Aspect.CORNERING -> "cornering"
    Circuits.Aspect.BRAKING -> "braking"
}

@Composable
private fun CircuitSection(
    ui: UiModel,
    car: Racing.Car,
    circuitId: String,
    onCircuitId: (String) -> Unit,
    watching: Boolean,
    onWatching: (Boolean) -> Unit,
    onArm: () -> Unit,
    onRun: (String, String, Circuits.Orders) -> Unit,
    onRevise: (Circuits.Orders, Int) -> Unit = { _, _ -> },
) {
    val state = ui.game
    val circuit = Circuits.all.firstOrNull { it.id == circuitId } ?: Circuits.all.first()
    // Circuit races are for hired drivers only. This pick is its own, so the drag-race toggle is left alone.
    var driverPick by rememberSaveable { mutableStateOf(Drivers.selected(state)?.id ?: "") }
    val hired = Drivers.hired(state)
    val picked = Circuits.entrant(state, driverPick)
    Text("CIRCUIT", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 12.dp))
    Text(
        "A few laps round a real circuit against seven rivals, simulated with your car. The race then plays back on a map of the circuit. Tires, axle, gearbox, final drive, weight, cooling and power all change the lap. The driver's cornering, braking, acceleration, mistakes and engine care change it too.",
        color = White,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
    )
    val upNext = Season.next(state)
    ChipRow(Circuits.all.map { it.id to (if (it.id == upNext.id) "> " else "") + it.name.substringBefore('-').uppercase(Locale.US) }, circuit.id) { onCircuitId(it) }
    Text(
        if (circuit.id == upNext.id) "CHAMPIONSHIP ROUND ${state.seasonRound + 1} of ${Season.ROUNDS}: this race scores points."
        else "Free race. The championship's next round is ${upNext.name} (marked >).",
        color = if (circuit.id == upNext.id) Lime else White,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        "${circuit.name}, ${circuit.place}  ${String.format(Locale.US, "%.3f", circuit.lapKm)} km  ${circuit.corners} corners  ${circuit.laps} laps  full throttle ${circuit.fullThrottlePct}%",
        color = Cyan,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
    Text(circuit.blurb, color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    CircuitMap(circuit, state.chassis, state.chassis == "r5" && state.enginePlace == "MID")
    val favors = remember(circuit.id) { Circuits.favors(circuit) }
    val emphasis = remember(circuit.id) { Circuits.emphasis(circuit) }
    Text(
        "FAVORS ${favors.joinToString(" and ") { it.title }}",
        color = Lime,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
    Circuits.Aspect.entries.forEach { a -> StatBar(aspectLabel(a), (emphasis.getValue(a) * 50).toInt().coerceIn(0, 100), a in favors) }
    val fit by produceState<Double?>(null, car.fade, car.massKg, car.pace, picked?.id, circuit.id, state.prestiges, state.circuitTire, state.kit) {
        value = withContext(Dispatchers.Default) { Circuits.fitPercent(Circuits.carOf(state, ui.sheet), circuit, state.prestiges, picked ?: Drivers.you) }
    }
    fit?.let {
        Text(
            Circuits.fitText(it),
            color = if (it >= 2.0) Lime else if (it >= -1.0) White else Amber,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Text("DRIVER", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    if (picked == null) {
        Text(
            Circuits.HIRE_NOTE,
            color = Amber,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            "Hire one from the DRIVERS list below. Circuit races are driven by your hired drivers, not by you.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    } else {
        ChipRow(hired.map { it.id to it.name.uppercase(Locale.US) }, picked.id) { driverPick = it }
        Text(
            "${picked.name}  overall ${picked.overall}  corner ${picked.cornering}  brake ${picked.braking}  accel ${picked.acceleration}  react ${picked.reaction}  mistakes ${picked.mistakes} (lower is better)  care ${picked.care}",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    val entry = Circuits.entry(state)
    val fee = if (picked == null) 0.0 else Circuits.fee(state, picked)
    Text(
        "Entry ${formatDollars(entry)}" + (if (fee > 0.0) "  driver ${formatDollars(fee)}" else "") +
            "  engine rebuild ${formatDollars(Circuits.repair(state))}",
        color = Cyan,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        "Prizes  " + Circuits.podium(state).withIndex().joinToString("  ") { (i, d) -> "${i + 1}${listOf("st", "nd", "rd", "th", "th")[i]} ${formatDollars(d)}" } +
            ". Sixth and below pay nothing.",
        color = Cyan,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
    var showTable by rememberSaveable { mutableStateOf(false) }
    Text(
        "CHAMPIONSHIP  season ${state.seasonNo}, round ${state.seasonRound} of ${Season.ROUNDS} done, you are P${Season.position(state)}" +
            if (state.seasonHistory.isNotEmpty()) ", ${Season.titles(state)} title${if (Season.titles(state) == 1) "" else "s"} won" else "",
        color = Lime,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 8.dp).clickable { showTable = !showTable },
    )
    Text(
        "Points 25 18 15 12 10 8 6 4, nothing for a retirement. Win the title for ${Season.REWARDS.joinToString(", ") { String.format(Locale.US, "%.1f", it) }} entries (top five). Tap to ${if (showTable) "hide" else "show"} the table.",
        color = White,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
    )
    if (showTable) {
        Season.standings(state).forEach { row ->
            Text(
                "${row.position}. ${if (row.you) "YOU" else row.name}  ${row.team}  ${row.points} pts" + if (row.wins > 0) "  ${row.wins}W" else "",
                color = if (row.you) Lime else White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        state.seasonHistory.takeLast(5).forEach { rec ->
            Text("Season ${rec.season}: you P${rec.position} on ${rec.points} pts, champion ${rec.champion}", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
    val weather = Circuits.weatherFor(state.circuitRaces, circuit.id)
    Text(
        "FORECAST  ${weather.title.uppercase(Locale.US)}. ${weather.blurb}",
        color = if (weather == Circuits.Weather.DRY) Lime else Amber,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
    val laps = circuit.laps
    val wet = weather == Circuits.Weather.WET
    val pool = if (weather == Circuits.Weather.DRY) Tires.dry else Tires.Compound.entries
    var start by rememberSaveable(circuit.id, weather) { mutableStateOf(if (wet) Tires.Compound.WET else Tires.Compound.MEDIUM) }
    var finish by rememberSaveable(circuit.id, weather) { mutableStateOf(if (wet) Tires.Compound.WET else Tires.Compound.HARD) }
    var stops by rememberSaveable(circuit.id, weather) { mutableStateOf(if (wet) 0 else 1) }
    var stop by rememberSaveable(circuit.id, weather) { mutableStateOf(if (wet) 0 else (laps + 1) / 2) }
    var stop2 by rememberSaveable(circuit.id, weather) { mutableStateOf(laps * 2 / 3) }
    var middle by rememberSaveable(circuit.id, weather) { mutableStateOf(if (wet) Tires.Compound.WET else Tires.Compound.MEDIUM) }
    var pace by rememberSaveable { mutableStateOf(Circuits.Pace.STEADY) }
    Text("PIT WALL", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    Text(
        "${circuit.corners} corners a lap: ${Tires.wearWord(circuit)}. " + Tires.rule(weather),
        color = White, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
    )
    Text("START ON", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    ChipRow(pool.map { it.name to it.title.uppercase(Locale.US) }, start.name) { start = Tires.Compound.valueOf(it); if (stops == 0) finish = start }
    Text(start.blurb, color = White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    Text("STOPS", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    ChipRow((if (wet) listOf("0" to "NO STOP") else emptyList()) + listOf("1" to "1 STOP", "2" to "2 STOPS"), stops.toString()) {
        stops = it.toInt()
        when (stops) {
            0 -> { stop = 0; stop2 = 0; finish = start }
            1 -> { if (stop == 0) stop = (laps + 1) / 2 }
            else -> { if (stop == 0 || stop > laps - 2) stop = laps / 3; if (stop2 <= stop) stop2 = (stop + 1 + (laps - stop) / 2).coerceAtMost(laps - 1) }
        }
    }
    if (stops > 0) {
        Text(if (stops == 2) "FIRST STOP AT THE END OF LAP" else "STOP AT THE END OF LAP", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        ChipRow((1 until laps).map { it.toString() to it.toString() }, stop.toString()) {
            stop = it.toInt()
            if (stops == 2 && stop2 <= stop) stop2 = (stop + 1).coerceAtMost(laps - 1)
        }
    }
    if (stops == 2) {
        Text("SECOND STOP AT THE END OF LAP", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        ChipRow((1 until laps).map { it.toString() to it.toString() }, stop2.toString()) {
            stop2 = it.toInt()
            if (stop2 <= stop) stop = (stop2 - 1).coerceAtLeast(1)
        }
        Text("MIDDLE STINT ON", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        ChipRow(pool.map { it.name to it.title.uppercase(Locale.US) }, middle.name) { middle = Tires.Compound.valueOf(it) }
    }
    Text("FINISH ON", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    ChipRow(pool.map { it.name to it.title.uppercase(Locale.US) }, finish.name) { finish = Tires.Compound.valueOf(it) }
    Text("PACE", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    ChipRow(Circuits.Pace.entries.map { it.name to it.title.uppercase(Locale.US) }, pace.name) { pace = Circuits.Pace.valueOf(it) }
    Text(pace.blurb, color = White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    val orders = Circuits.Orders(start, if (stops == 0) 0 else stop, finish, pace, if (stops == 2) stop2 else 0, middle)
    val breach = Tires.breach(orders.fit, laps, weather)
    if (breach != null) Text("ILLEGAL: $breach", color = Red, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    val mine by produceState<Circuits.Advice?>(null, car.fade, car.massKg, car.pace, picked?.id, circuit.id, state.circuitTire, state.kit, start, stops, stop, stop2, middle, finish, pace) {
        value = withContext(Dispatchers.Default) {
            if (breach != null) null else Circuits.forecast(Circuits.carOf(state, ui.sheet), circuit, picked ?: Drivers.you, orders, weather)
        }
    }
    mine?.let { m ->
        fun pct(x: Double) = "${(x * 100).toInt()}%"
        val cs = orders.fit.used
        val stopText = when (orders.stopCount) {
            0 -> "No stop. "
            1 -> "At the stop (end of lap $stop) the ${start.title.lowercase(Locale.US)}s have ${pct(m.gripAtStop ?: 1.0)} grip. "
            else -> "At the first stop (end of lap $stop) the ${start.title.lowercase(Locale.US)}s have ${pct(m.gripAtStop ?: 1.0)} grip, at the second (end of lap $stop2) the ${middle.title.lowercase(Locale.US)}s have ${pct(m.gripAtStop2 ?: 1.0)}. "
        }
        Text(
            "${stopText}At the flag the ${cs.last().title.lowercase(Locale.US)}s have ${pct(m.gripAtFlag)} grip. Planned race ${Circuits.clock(m.raceTime)}.",
            color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
    val wearTable by produceState<Map<Tires.Compound, Double>?>(null, car.massKg, picked?.id, circuit.id, state.circuitTire, state.kit, pace) {
        value = withContext(Dispatchers.Default) { Circuits.noStopGrip(Circuits.carOf(state, ui.sheet), circuit, picked ?: Drivers.you, pace, weather) }
    }
    wearTable?.let { t ->
        Text(
            "Grip at the flag with no stop: " + pool.joinToString("  ") { "${it.letter} ${((t[it] ?: 1.0) * 100).toInt()}%" } + "  (a stop is required)",
            color = White, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
        )
    }
    val advice by produceState<List<Circuits.Advice>?>(null, car.fade, car.massKg, car.pace, picked?.id, circuit.id, state.circuitTire, state.kit) {
        value = withContext(Dispatchers.Default) { Circuits.advise(Circuits.carOf(state, ui.sheet), circuit, picked ?: Drivers.you, weather) }
    }
    advice?.let { list ->
        val best = list.first().orders
        Text(
            "Best plan on paper: ${Circuits.planText(best)}, ${best.pace.title.lowercase(Locale.US)}, ${Circuits.clock(list.first().raceTime)}.",
            color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
        )
        LineButton("Use best plan", true, { start = best.start; stops = best.stopCount; stop = best.stop; stop2 = if (best.stopCount == 2) best.stop2 else laps * 2 / 3; middle = if (best.stopCount == 2) best.middle else middle; finish = best.finish; pace = best.pace }, Modifier.padding(top = 2.dp))
    }
    Text("THE FIELD", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    Rivals.all.forEach { r ->
        Text("${r.name}, ${r.team}: ${r.style.title}, aggression ${(r.style.aggression * 100).toInt()}. ${r.style.blurb}", color = White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
    val can = picked != null && breach == null && !ui.circuitBusy && (state.devMode || state.money >= entry + fee)
    // Running a race opens the race screen: the map, the dash and the replay live there and nowhere else.
    LineButton(if (ui.circuitBusy) "Racing..." else "Run race", can, { onArm(); onRun(circuit.id, picked?.id ?: "", orders) }, Modifier.padding(top = 6.dp))
    ui.circuitOutcome?.let { o ->
        val r = o.result
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, if (r.retired != null) Red else Lime).padding(8.dp)) {
            Text(
                if (r.retired != null) "${r.circuit.name}: DNF" else "${r.circuit.name}: P${r.position} of ${r.field}",
                color = if (r.retired != null) Red else Lime,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
            Text("Driver ${r.driver}  ${Circuits.planText(r.orders)}, ${r.orders.pace.title.lowercase(Locale.US)}, ${r.weather.title.lowercase(Locale.US)}", color = White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            if (r.retired != null) Text(r.retired, color = Red, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            else Text("Total ${Circuits.clock(r.totalTime)}  best lap ${Circuits.clock(r.bestLap)}", color = White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            if (r.log.rows.isNotEmpty()) {
                LineButton("Watch the race", true, { onWatching(true) }, Modifier.padding(top = 4.dp))
                remember(r) { Replay.build(r) }?.let { BestLapTraces(it, Modifier.padding(vertical = 4.dp)) }
                if (watching) CircuitRaceScreen(
                    r, state.chassis, state.chassis == "r5" && state.enginePlace == "MID",
                    session = o.run?.seed, onRevise = onRevise,
                    ear = ui.sheet.params, voice = LocalEngineSpeaker.current,
                ) { onWatching(false) }
            }
            Text(
                "Laps " + r.lapTimes.joinToString("  ") { Circuits.clock(it) },
                color = White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Text(
                if (r.incidents.isEmpty()) "Incidents: none" else "Incidents: " + r.incidents.joinToString("; "),
                color = if (r.incidents.isEmpty()) White else Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Text(
                "Entry ${formatDollars(o.entry)}" + (if (o.fee > 0.0) "  driver ${formatDollars(o.fee)}" else "") +
                    "  prize ${formatDollars(o.prize)}" + (if (o.repair > 0.0) "  engine rebuild ${formatDollars(o.repair)}" else ""),
                color = Cyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            if (o.season.isNotEmpty()) Text(o.season, color = Lime, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            if (r.qualifying.rows.isNotEmpty()) {
                Text("QUALIFYING AND GRID", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Circuits.gridLines(r.qualifying).forEachIndexed { i, line ->
                    Text(line, color = if (r.qualifying.rows[i].you) Lime else White, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
            if (r.log.rows.isNotEmpty()) {
                var showOrder by rememberSaveable { mutableStateOf(false) }
                Text("LAP BY LAP", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                var allLaps by rememberSaveable { mutableStateOf(false) }
                val items = remember(r, allLaps) { RaceLog.items(r.log, allLaps) }
                items.forEach { item ->
                    when (item) {
                        is RaceLog.Item.Skipped -> Text(
                            "    ${if (item.from == item.to) "lap ${item.from}" else "laps ${item.from} to ${item.to}"} not shown",
                            color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        )
                        is RaceLog.Item.Lap -> {
                            val row = item.row
                            Text(item.line, color = if (row.yourOut) Red else White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            if (showOrder) Text("    " + RaceLog.orderLine(row), color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            row.events.filter { allLaps || RaceLog.mine(it) }.forEach { e ->
                                Text("    ${e.text}", color = Amber, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }
                LineButton(if (allLaps) "Show the key laps only" else "Show all ${r.log.laps} laps", true, { allLaps = !allLaps }, Modifier.padding(top = 4.dp))
                LineButton(if (showOrder) "Hide order each lap" else "Show order each lap", true, { showOrder = !showOrder }, Modifier.padding(top = 4.dp))
                Text("FINAL ORDER", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            r.grid.forEachIndexed { i, f ->
                Text(
                    "${i + 1}. ${f.name}${if (f.you) "" else "  ${f.team}"}  " + if (f.out) "DNF" else Circuits.clock(f.time),
                    color = if (f.you) Lime else White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** The circuit seen from above, with your car and a rival on the front row. */
@Composable
private fun CircuitMap(circuit: Circuits.Circuit, chassis: String, mid: Boolean) {
    val scenes = remember { CircuitSceneHolder() }
    val title = circuit.name.substringBefore('-').uppercase(Locale.US)
    val sub = String.format(Locale.US, "%.3f km  %d corners", circuit.lapKm, circuit.corners)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().aspectRatio(CircuitLayouts.aspect(circuit.id)).padding(top = 4.dp)) {
        val scene = scenes.get(this, circuit.id, title, sub)
        val geo = scene.geo
        drawCircuitScene(
            scene,
            listOf(
                CircuitCar(ReplayLook.rivalId(circuit.id), false, ReplayLook.red, Color.White, gridArc(geo, 0, -1), -geo.laneOffset),
                CircuitCar(chassis, mid, ReplayLook.lime, ReplayLook.ink, gridArc(geo, 0, 1), geo.laneOffset),
            ),
        )
    }
}
