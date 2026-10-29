package com.revline.idle.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revline.idle.DynoPhase
import com.revline.idle.GameViewModel
import com.revline.idle.UiModel
import com.revline.idle.economy.Prestige
import com.revline.idle.economy.formatCompact
import com.revline.idle.economy.formatDollars
import com.revline.idle.model.Block
import com.revline.idle.game.Logic
import com.revline.idle.game.Perks
import com.revline.idle.game.ResearchStatus
import com.revline.idle.game.ResearchTree
import com.revline.idle.model.EngineSim
import java.util.Locale

private enum class Tab(val label: String) {
    BENCH("BENCH"), PARTS("PARTS"), TUNE("TUNE"), STUDY("STUDY"), RACE("RACE"), REBUILD("REBUILD"),
}

@Composable
fun RevlineApp(vm: GameViewModel) {
    val ui = vm.ui
    val crank = rememberEngineCrank(vm)
    EngineSoundtrack(crank, ui.game.batterySaver)
    val tab = remember { androidx.compose.runtime.mutableStateOf(Tab.BENCH) }
    CompositionLocalProvider(LocalBatterySaver provides ui.game.batterySaver) {
    RevlineTheme {
        Scaffold(
            containerColor = Black,
            contentWindowInsets = WindowInsets.systemBars.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
            ),
            bottomBar = {
                Column(
                    Modifier
                        .background(Black)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp),
                ) {
                    Text(
                        if (ui.game.batterySaver) "BATTERY SAVER ON  ·  DRAWING FROZEN" else "BATTERY SAVER",
                        color = if (ui.game.batterySaver) Amber else Olive,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Black)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = vm::toggleBatterySaver,
                            )
                            .padding(vertical = 8.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(White))
                    Row(Modifier.fillMaxWidth()) {
                        Tab.entries.forEach { item ->
                            val on = tab.value == item
                            Text(
                                item.label,
                                color = if (on) Lime else White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { if (ui.strip == null) tab.value = item }
                                    .padding(vertical = 14.dp),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(Black)) {
                val strip = ui.strip
                if (strip != null) {
                    RaceMeet(strip.setup, strip.field, strip.yourPace, vm::finishMeet)
                } else {
                    when (tab.value) {
                        Tab.BENCH -> BenchScreen(ui, vm::startDyno, vm::setThrottle)
                        Tab.PARTS -> PartsScreen(ui, vm::buy, vm::remove, vm::fuel, vm::setAimGoal)
                        Tab.TUNE -> TuneScreen(ui, vm::nudgeTune)
                        Tab.STUDY -> StudyScreen(ui, vm::research)
                        Tab.RACE -> RaceScreen(
                            ui,
                            vm::studyRace,
                            vm::fitChassis,
                            vm::fitRace,
                            vm::setCooling,
                            vm::setFinal,
                            vm::openQuick,
                            vm::openLadder,
                            vm::openStake,
                        )
                        Tab.REBUILD -> RebuildScreen(ui, vm::perk, vm::prestige, vm::toggleDevMode)
                    }
                }
            }
        }
        if (ui.offline != null) {
            AlertDialog(
                onDismissRequest = vm::dismissOffline,
                containerColor = Black,
                titleContentColor = White,
                textContentColor = White,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                modifier = Modifier.border(1.dp, Lime),
                title = { Text("While the lights were off", fontFamily = FontFamily.Monospace) },
                text = { Text(ui.offline, fontFamily = FontFamily.Monospace) },
                confirmButton = {
                    LineButton("Bank it", true, vm::dismissOffline)
                },
            )
        }
    }
    }
}

@Composable
private fun rememberEngineCrank(vm: GameViewModel): EngineCrank {
    val crank = remember { EngineCrank() }
    val params = vm.ui.sheet.params
    val rpm = vm.ui.rpm.toFloat()
    val throttle = if (vm.ui.phase == DynoPhase.IDLE) vm.ui.game.throttle.toFloat() else 1f
    val live = !vm.ui.game.batterySaver &&
        !(Logic.stalled(vm.ui.game.throttle) && vm.ui.phase == DynoPhase.IDLE)
    LaunchedEffect(params, rpm, throttle, live) {
        crank.publish(params, rpm, throttle, live)
    }
    return crank
}

@Composable
private fun EngineSoundtrack(crank: EngineCrank, saver: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, crank, saver) {
        if (saver) return@DisposableEffect onDispose { }
        val audio = EngineAudio(crank)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> audio.start()
                Lifecycle.Event.ON_STOP -> audio.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) audio.start()
        onDispose {
            lifecycle.removeObserver(observer)
            audio.stop()
        }
    }
}

@Composable
private fun BenchScreen(ui: UiModel, onDyno: () -> Unit, onThrottle: (Double) -> Unit) {
    val params = ui.sheet.params
    val stats = ui.sheet.stats
    val bucket = (ui.rpm / 50.0).toInt() * 50
    val liveLoad = if (ui.phase == DynoPhase.IDLE) Logic.loadFor(ui.game.throttle) else 1.0
    val soakBucket = (ui.game.soakC * 2.0).toInt()
    val live = remember(params, bucket, liveLoad, soakBucket) {
        val point = EngineSim.point(params, bucket.toDouble(), liveLoad, ui.game.soakC)
        if (Logic.stalled(ui.game.throttle) && ui.phase == DynoPhase.IDLE) {
            point.copy(horsepower = 0.0, torqueLbFt = 0.0, mpg = 0.0)
        } else {
            point
        }
    }
    val target = Logic.targetRpm(params.redlineRpm, ui.game.throttle, params.idleRpm)
    val pedalEnabled = ui.phase == DynoPhase.IDLE
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1.1f)) {
                    Hud(ui)
                    Text(Logic.engineTitle(params), color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    FailureLine(ui)
                    EngineArt(params, ui.rpm.toFloat(), live.coolantTempC.toFloat(), live.egtC.toFloat(), ui.game.upgrades, Modifier.fillMaxWidth().height(280.dp))
                    Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tachometer(ui.rpm, params.redlineRpm, target, Modifier.weight(1f).height(170.dp))
                        ThrottlePedal(ui.game.throttle, pedalEnabled, onThrottle, Modifier.width(84.dp).height(170.dp))
                    }
                    Text(
                        "The pedal pays for how much of the engine's output you are using, and the mpg at that stop. Heat still taxes the floor.",
                        color = White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Column(Modifier.weight(0.9f).verticalScroll(rememberScrollState())) {
                    StatGrid(ui, live)
                    EnergyBlock(live)
                    DynoChart(stats.curve, ui.rpm, Modifier.fillMaxWidth().padding(top = 8.dp))
                    DynoButton(ui, onDyno)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Hud(ui)
                Text(Logic.engineTitle(params), color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                FailureLine(ui)
                EngineArt(params, ui.rpm.toFloat(), live.coolantTempC.toFloat(), live.egtC.toFloat(), ui.game.upgrades, Modifier.fillMaxWidth().height(200.dp))
                Row(Modifier.fillMaxWidth().height(148.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tachometer(ui.rpm, params.redlineRpm, target, Modifier.weight(1f).height(148.dp))
                    ThrottlePedal(ui.game.throttle, pedalEnabled, onThrottle, Modifier.width(76.dp).height(148.dp))
                }
                Text(
                    "Pay follows output used and mpg at the cyan mark. A closed pedal leaves power on the table. Heat taxes the floor.",
                    color = White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    StatGrid(ui, live)
                    EnergyBlock(live)
                    DynoChart(stats.curve, ui.rpm, Modifier.fillMaxWidth())
                    DynoButton(ui, onDyno)
                }
            }
        }
    }
}

@Composable
private fun Hud(ui: UiModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("REVLINE", color = White, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
                if (ui.game.devMode) {
                    Text(
                        "DEV",
                        color = Red,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp).border(1.dp, Red).padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Text(formatDollars(ui.game.money), color = Lime, fontFamily = FontFamily.Monospace, fontSize = 22.sp)
            Text(
                if (Logic.stalled(ui.game.throttle)) {
                    "stalled"
                } else {
                    "${formatDollars(ui.sheet.incomePerSec)}/s contract"
                },
                color = if (Logic.stalled(ui.game.throttle)) Red else Lime,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
            if (ui.game.breakdowns > 0) {
                Text(
                    "Breakdowns ${ui.game.breakdowns}",
                    color = Red,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("x${String.format(Locale.US, "%.2f", ui.sheet.multiplier)}", color = Cyan, fontFamily = FontFamily.Monospace)
            Text("${formatCompact(ui.game.researchPoints)} RP", color = Cyan, fontFamily = FontFamily.Monospace)
            Text("${formatCompact(ui.sheet.rpPerSec)}/s", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FailureLine(ui: UiModel) {
    val left = ((ui.game.downUntilEpochMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
    if (left <= 0L) return
    Text(
        ui.game.lastFailure.ifEmpty { "The engine let go." },
        color = Red,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
    Text(
        "Down ${left}s. The contract pays nothing until it fires again.",
        color = Red,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
    )
}

private fun soakReadout(soakC: Double, per10s: Double): String {
    val sign = if (per10s >= 0.0) "+" else "−"
    val rate = String.format(Locale.US, "%.1f", kotlin.math.abs(per10s))
    return "soak ${soakC.toInt()}°C $sign$rate°/10s"
}

@Composable
private fun StatGrid(ui: UiModel, live: com.revline.idle.model.OperatingPoint) {
    val s = ui.sheet.stats
    val notes = if (s.reliabilityNotes.isEmpty()) "Inside limits" else s.reliabilityNotes.joinToString(" · ")
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat("HP", formatPower(s.peakHorsepower), Lime, Modifier.weight(1f))
            Stat("TQ", formatPower(s.peakTorqueLbFt), Amber, Modifier.weight(1f))
            Stat("MPG", String.format(Locale.US, "%.1f", s.cruiseMpg), if (s.cruiseMpg < 12.0) Amber else White, Modifier.weight(1f))
            Stat("REL", String.format(Locale.US, "%.0f", s.reliability), if (s.reliability < 70) Red else White, Modifier.weight(1f))
        }
        Text(
            "Sheet peak. Now ${formatPower(live.horsepower)} hp, ${formatPower(live.torqueLbFt)} lb-ft, " +
                "${String.format(Locale.US, "%.1f", live.mpg)} mpg at this pedal.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "At ${live.rpm.toInt()} rpm · load ${(live.load * 100).toInt()}% · ${soakReadout(ui.game.soakC, EngineSim.soakPer10s(ui.sheet.params, live.rpm, if (ui.phase == DynoPhase.IDLE) ui.game.throttle else 1.0))} · " +
                (if (ui.sheet.params.induction != com.revline.idle.model.Induction.NA && live.boostPsi < 0.4) "below spool · " else "") +
                "${String.format(Locale.US, "%.1f", 60.0 / live.mpg.coerceAtLeast(0.8))} gal/h · $${String.format(Locale.US, "%.2f", ui.sheet.params.fuel.dollarsPerGallon)}/gal",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat("COOLANT", "${live.coolantTempC.toInt()}°C", if (live.coolantTempC > 110) Red else Cyan, Modifier.weight(1f))
            Stat("OIL", "${live.oilTempC.toInt()}°C", if (live.oilTempC > 140) Red else Amber, Modifier.weight(1f))
            Stat("EGT", "${live.egtC.toInt()}°C", if (live.egtC > 900) Red else Amber, Modifier.weight(1f))
            Stat(
                "BOOST",
                if (ui.sheet.params.induction == com.revline.idle.model.Induction.NA) {
                    "0.0"
                } else {
                    String.format(Locale.US, "%.1f", live.boostPsi)
                },
                if (ui.sheet.params.induction != com.revline.idle.model.Induction.NA && live.boostPsi < 0.4) Amber else Cyan,
                Modifier.weight(1f),
            )
        }
        Text(notes, color = if (s.reliabilityNotes.isEmpty()) Lime else Red, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Text(
            "AFR ${String.format(Locale.US, "%.2f", live.afr)}  spark ${live.timingDeg.toInt()}°  " +
                "octane ${live.octane.toInt()}/${live.octaneDemand.toInt()}  " +
                "charge ${live.chargeTempC.toInt()}°C  BTE ${String.format(Locale.US, "%.1f", live.brakeEfficiency * 100)}%",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun EngineStrip(ui: UiModel, live: com.revline.idle.model.OperatingPoint) {
    val s = ui.sheet.stats
    val boost = if (ui.sheet.params.induction == com.revline.idle.model.Induction.NA) {
        "0.0"
    } else {
        String.format(Locale.US, "%.1f", live.boostPsi)
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDollars(ui.game.money), color = Lime, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
            Text("${formatDollars(ui.sheet.incomePerSec)}/s", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
        }
        Text(
            "Sheet ${formatPower(s.peakHorsepower)} hp   ${formatPower(s.peakTorqueLbFt)} lb-ft   ${String.format(Locale.US, "%.1f", s.cruiseMpg)} mpg",
            color = Lime,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Stat("HP", formatPower(live.horsepower), Lime, Modifier.weight(1f))
            Stat("TQ", formatPower(live.torqueLbFt), Amber, Modifier.weight(1f))
            Stat("MPG", String.format(Locale.US, "%.1f", live.mpg), if (live.mpg < 12.0) Amber else White, Modifier.weight(1f))
            Stat("BOOST", boost, if (ui.sheet.params.induction != com.revline.idle.model.Induction.NA && live.boostPsi < 0.4) Amber else Cyan, Modifier.weight(1f))
            Stat("REL", String.format(Locale.US, "%.0f", s.reliability), if (s.reliability < 70) Red else White, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Stat("RPM", live.rpm.toInt().toString(), White, Modifier.weight(1f))
            Stat("LOAD", "${(live.load * 100).toInt()}%", White, Modifier.weight(1f))
            Stat("SOAK", "${ui.game.soakC.toInt()}°", if (ui.game.soakC > 18.0) Red else Cyan, Modifier.weight(1f))
            Stat("COOL", "${live.coolantTempC.toInt()}°", if (live.coolantTempC > 110) Red else Cyan, Modifier.weight(1f))
            Stat("OIL", "${live.oilTempC.toInt()}°", if (live.oilTempC > 140) Red else Amber, Modifier.weight(1f))
            Stat("EGT", "${live.egtC.toInt()}°", if (live.egtC > 900) Red else Amber, Modifier.weight(1f))
        }
        Text(
            "AFR ${String.format(Locale.US, "%.2f", live.afr)}  spark ${live.timingDeg.toInt()}°  " +
                "octane ${live.octane.toInt()}/${live.octaneDemand.toInt()}  " +
                "charge ${live.chargeTempC.toInt()}°C  " +
                (if (ui.sheet.params.induction != com.revline.idle.model.Induction.NA && live.boostPsi < 0.4) "below spool  " else "") +
                "${String.format(Locale.US, "%.1f", 60.0 / live.mpg.coerceAtLeast(0.8))} gal/h",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.border(1.dp, White).padding(horizontal = 4.dp, vertical = 3.dp)) {
        Text(label, color = Lime, fontFamily = FontFamily.Monospace, fontSize = 9.sp, maxLines = 1)
        Text(value, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun EnergyBlock(live: com.revline.idle.model.OperatingPoint) {
    val released = live.fuelReleasedKw.coerceAtLeast(0.1)
    Column(Modifier.padding(top = 8.dp)) {
        Text("ENERGY AT ${live.rpm.toInt()} RPM", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        EnergyLine("Fuel burned", live.fuelReleasedKw, 1.0, White)
        EnergyLine("Brake", live.brakeKw, live.brakeKw / released, Lime)
        EnergyLine("Radiator", live.coolantKw, live.coolantKw / released, Cyan)
        EnergyLine("Oil to air", live.oilToAirKw, live.oilToAirKw / released, Amber)
        EnergyLine("Exhaust", live.exhaustKw, live.exhaustKw / released, if (live.egtC > 900) Red else Amber)
    }
}

@Composable
private fun EnergyLine(label: String, kw: Double, fraction: Double, color: Color) {
    Column(Modifier.padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Text(String.format(Locale.US, "%.1f kW", kw), color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat())
                    .height(8.dp)
                    .background(color.copy(alpha = 0.22f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat())
                    .height(2.dp)
                    .align(Alignment.CenterStart)
                    .background(color),
            )
        }
    }
}

@Composable
private fun DynoButton(ui: UiModel, onDyno: () -> Unit) {
    val now = System.currentTimeMillis()
    val left = ((ui.game.dynoReadyAtEpochMs - now) / 1000L).coerceAtLeast(0L)
    val busy = ui.phase != DynoPhase.IDLE
    val label = when {
        busy -> "On the rollers"
        left > 0 -> "Rollers free in ${left}s"
        else -> "Dyno pull  +${formatCompact(Logic.dynoReward(ui.sheet.rpPerSec, ui.sheet.params.dynoFactor))} RP"
    }
    LineButton(label, !busy && left == 0L, onDyno, Modifier.fillMaxWidth().padding(top = 10.dp))
}

@Composable
private fun StudyScreen(ui: UiModel, onBuy: (String) -> Unit) {
    val branches = remember { ResearchTree.all.map { it.branch }.distinct() }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Hud(ui) }
        branches.forEach { branch ->
            item { Text(branch.uppercase(Locale.US), color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp)) }
            items(ResearchTree.all.filter { it.branch == branch }, key = { it.id }) { node ->
                val status = Logic.researchStatus(ui.game, node.id)
                Column(Modifier.fillMaxWidth().border(1.dp, White).padding(8.dp)) {
                    Text(node.name, color = White, fontFamily = FontFamily.Monospace)
                    Text(node.detail, color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    val label = when (status) {
                        ResearchStatus.OWNED -> "In the notebook"
                        ResearchStatus.LOCKED -> if (node.minPrestige > ui.game.prestiges) {
                            "Needs a ${Block.forPrestige(node.minPrestige).title}"
                        } else {
                            "Needs ${node.requires.joinToString { ResearchTree.byId(it).name }}"
                        }
                        ResearchStatus.OPEN -> if (ui.game.devMode) "Study · free (dev)" else "${formatCompact(node.cost)} RP"
                    }
                    LineButton(label, Logic.canBuyResearch(ui.game, node.id), { onBuy(node.id) }, Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun RebuildScreen(ui: UiModel, onPerk: (String) -> Unit, onPrestige: (Boolean) -> Unit, onDevMode: () -> Unit) {
    val block = Block.forPrestige(ui.game.prestiges)
    val next = Block.forPrestige(ui.game.prestiges + 1)
    val racing = ui.game.racing
    val stats = ui.sheet.stats
    val gain = Prestige.knowledgeGain(stats.peakHorsepower, stats.peakTorqueLbFt, stats.cruiseMpg, block, racing)
    val nextMultiplier = Prestige.multiplier(ui.game.knowledge + gain)
    val free = Logic.knowledgeFree(ui.game)
    val onLast = ui.game.prestiges >= Block.entries.lastIndex
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Hud(ui)
        Text(block.sheetTitle(racing).uppercase(Locale.US), color = if (racing) Red else White, fontFamily = FontFamily.Monospace, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
        Text(
            if (onLast) {
                "Another rebuild stays on the ${block.title}. Street keeps this sheet. Race raises the power targets and cuts the mpg target, and the long-block itself revs higher. Parts come off. The bench starts with ${formatDollars(Logic.startingMoney(ui.game.prestiges + 1))}, enough for the four cheapest tools. Studies stay."
            } else {
                "The next long-block is a ${next.title}. Street keeps the road sheet. Race asks for more power and less mpg, and comes out of the crate with more compression and a higher tach. Parts come off. The bench starts with ${formatDollars(Logic.startingMoney(ui.game.prestiges + 1))}, enough for the four cheapest tools. Studies stay."
            },
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "The sheet has to clear power, torque, and cruise economy together.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        SheetTarget("Power", stats.peakHorsepower, block.sheetHp(racing), "hp")
        SheetTarget("Torque", stats.peakTorqueLbFt, block.sheetTorque(racing), "lb-ft")
        SheetTarget("Cruise", stats.cruiseMpg, block.sheetMpg(racing), "mpg")
        Text(
            "Street next  ${fmtGoal(next.sheetHp(false))} hp  ${fmtGoal(next.sheetTorque(false))} lb-ft  ${fmtGoal(next.sheetMpg(false))} mpg",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Race next  ${fmtGoal(next.sheetHp(true))} hp  ${fmtGoal(next.sheetTorque(true))} lb-ft  ${fmtGoal(next.sheetMpg(true))} mpg",
            color = Red,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Text(
            if (gain > 0) {
                "Rebuild pays $gain Engineering Knowledge. The multiplier goes to x${String.format(Locale.US, "%.2f", nextMultiplier)}."
            } else {
                "One of those three numbers is still short."
            },
            color = if (gain > 0) Cyan else Amber,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (gain == 1) {
            Text(
                "This is the sheet minimum. Power, torque, or cruise past the target raises the payout, up to 16.",
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            "Knowledge ${ui.game.knowledge} · free $free · rebuilds ${ui.game.prestiges} · multiplier x${String.format(Locale.US, "%.2f", ui.sheet.multiplier)}",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LineButton(
            when {
                gain <= 0 -> "Keep building"
                onLast -> "Street rebuild"
                else -> "Street rebuild into a ${next.title}"
            },
            gain > 0,
            { onPrestige(false) },
            Modifier.fillMaxWidth(),
        )
        if (gain > 0) {
            LineButton(
                if (onLast) "Race rebuild" else "Race rebuild into a ${next.title}",
                true,
                { onPrestige(true) },
                Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        Text("PERKS", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Text(
            "These stay with the shop. Each rank spends Engineering Knowledge and applies to every long-block after it.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Perks.all.forEach { perk ->
            val rank = ui.game.perks[perk.id] ?: 0
            val next = perk.costs.getOrNull(rank)
            Column(Modifier.fillMaxWidth().border(1.dp, White).padding(8.dp).padding(bottom = 8.dp)) {
                Text("${perk.name}  $rank/${perk.costs.size}", color = White, fontFamily = FontFamily.Monospace)
                Text(perk.blurb, color = White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                LineButton(
                    if (next == null) "Maxed" else "Study · $next EK",
                    Logic.canBuyPerk(ui.game, perk.id),
                    { onPerk(perk.id) },
                    Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Text("DEV MODE", color = Red, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Text(
            if (ui.game.devMode) {
                "On. Every part level and every study costs nothing. Study gates still apply, so open them in the STUDY tab first. Money and research keep counting."
            } else {
                "Off. Turning it on makes every part level and every study free, for testing builds. It stays on across rebuilds until you turn it off."
            },
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 16.dp)
                .border(1.dp, if (ui.game.devMode) Red else Olive)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDevMode,
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Free upgrades", color = White, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            DevSwitch(ui.game.devMode)
        }
    }
}

@Composable
private fun DevSwitch(on: Boolean) {
    val color = if (on) Red else Olive
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(48.dp).height(22.dp).border(1.dp, color, androidx.compose.foundation.shape.RoundedCornerShape(11.dp))) {
            Box(
                Modifier
                    .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(3.dp)
                    .width(16.dp)
                    .height(16.dp)
                    .background(color, androidx.compose.foundation.shape.CircleShape),
            )
        }
        Text(if (on) " ON" else " OFF", color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

private fun fmtGoal(value: Double): String = String.format(Locale.US, "%.0f", value)

@Composable
private fun SheetTarget(name: String, now: Double, goal: Double, unit: String) {
    val met = now >= goal
    val ratio = if (goal > 0.0) now / goal else 0.0
    val over = if (met && ratio >= 1.05) "  ${String.format(Locale.US, "%.2f", ratio)}×" else ""
    Text(
        "$name  ${String.format(Locale.US, "%.1f", now)} / ${String.format(Locale.US, "%.1f", goal)} $unit$over",
        color = if (met) Lime else Amber,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
fun LineButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (enabled) Lime else Olive
    Text(
        text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        modifier = modifier
            .border(BorderStroke(1.dp, color))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
