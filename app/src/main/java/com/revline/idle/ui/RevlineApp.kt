package com.revline.idle.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.revline.idle.game.Category
import com.revline.idle.game.Logic
import com.revline.idle.game.Perks
import com.revline.idle.game.ResearchStatus
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import com.revline.idle.model.EngineSim
import com.revline.idle.model.FuelKind
import java.util.Locale

private enum class Tab(val label: String) { BENCH("BENCH"), PARTS("PARTS"), STUDY("STUDY"), REBUILD("REBUILD") }

@Composable
fun RevlineApp(vm: GameViewModel) {
    val ui = vm.ui
    val tab = remember { androidx.compose.runtime.mutableStateOf(Tab.BENCH) }
    RevlineTheme {
        Scaffold(
            containerColor = Black,
            bottomBar = {
                Column(Modifier.background(Black)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(White))
                    Row(Modifier.fillMaxWidth()) {
                        Tab.entries.forEach { item ->
                            val on = tab.value == item
                            Text(
                                item.label,
                                color = if (on) Lime else White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { tab.value = item }
                                    .padding(vertical = 14.dp),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(Black)) {
                when (tab.value) {
                    Tab.BENCH -> BenchScreen(ui, vm::startDyno)
                    Tab.PARTS -> PartsScreen(ui, vm::buy, vm::fuel)
                    Tab.STUDY -> StudyScreen(ui, vm::research)
                    Tab.REBUILD -> RebuildScreen(ui, vm::perk, vm::prestige)
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

@Composable
private fun BenchScreen(ui: UiModel, onDyno: () -> Unit) {
    val params = ui.sheet.params
    val stats = ui.sheet.stats
    val bucket = (ui.rpm / 50.0).toInt() * 50
    val live = remember(params, bucket) { EngineSim.point(params, bucket.toDouble()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1.1f)) {
                    Hud(ui)
                    Text(Logic.engineTitle(params), color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    EngineArt(params, ui.rpm.toFloat(), live.coolantTempC.toFloat(), live.egtC.toFloat(), Modifier.fillMaxWidth().height(280.dp))
                    Tachometer(ui.rpm, params.redlineRpm, Modifier.fillMaxWidth().height(160.dp))
                }
                Column(Modifier.weight(0.9f).verticalScroll(rememberScrollState())) {
                    StatGrid(ui, live)
                    EnergyBlock(live)
                    DynoChart(stats.curve, ui.rpm, Modifier.fillMaxWidth().padding(top = 8.dp))
                    DynoButton(ui, onDyno)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Hud(ui)
                Text(Logic.engineTitle(params), color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                EngineArt(params, ui.rpm.toFloat(), live.coolantTempC.toFloat(), live.egtC.toFloat(), Modifier.fillMaxWidth().height(230.dp))
                Tachometer(ui.rpm, params.redlineRpm, Modifier.fillMaxWidth().height(150.dp))
                StatGrid(ui, live)
                EnergyBlock(live)
                DynoChart(stats.curve, ui.rpm, Modifier.fillMaxWidth())
                DynoButton(ui, onDyno)
            }
        }
    }
}

@Composable
private fun Hud(ui: UiModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("REVLINE", color = White, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
            Text(formatDollars(ui.game.money), color = Lime, fontFamily = FontFamily.Monospace, fontSize = 22.sp)
            Text("${formatDollars(ui.sheet.incomePerSec)}/s", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("x${String.format(Locale.US, "%.2f", ui.sheet.multiplier)}", color = Cyan, fontFamily = FontFamily.Monospace)
            Text("${formatCompact(ui.game.researchPoints)} RP", color = Cyan, fontFamily = FontFamily.Monospace)
            Text("${formatCompact(ui.sheet.rpPerSec)}/s", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatGrid(ui: UiModel, live: com.revline.idle.model.OperatingPoint) {
    val s = ui.sheet.stats
    val notes = if (s.reliabilityNotes.isEmpty()) "Inside limits" else s.reliabilityNotes.joinToString(" · ")
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat("HP", String.format(Locale.US, "%.0f", s.peakHorsepower), Lime, Modifier.weight(1f))
            Stat("TQ", String.format(Locale.US, "%.0f", s.peakTorqueLbFt), Amber, Modifier.weight(1f))
            Stat("MPG", String.format(Locale.US, "%.1f", s.cruiseMpg), White, Modifier.weight(1f))
            Stat("REL", String.format(Locale.US, "%.0f", s.reliability), if (s.reliability < 70) Red else White, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat("COOLANT", "${live.coolantTempC.toInt()}°C", if (live.coolantTempC > 110) Red else Cyan, Modifier.weight(1f))
            Stat("OIL", "${live.oilTempC.toInt()}°C", if (live.oilTempC > 140) Red else Amber, Modifier.weight(1f))
            Stat("EGT", "${live.egtC.toInt()}°C", if (live.egtC > 900) Red else Amber, Modifier.weight(1f))
            Stat("BOOST", String.format(Locale.US, "%.1f", live.boostPsi), Cyan, Modifier.weight(1f))
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
private fun Stat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.border(1.dp, White).padding(6.dp)) {
        Text(label, color = Lime, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text(value, color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
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
        Box(Modifier.fillMaxWidth().height(2.dp).background(Black).border(0.dp, Black)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat())
                    .height(2.dp)
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
        else -> "Dyno pull  +${formatCompact(Logic.dynoReward(ui.sheet.stats))} RP"
    }
    LineButton(label, !busy && left == 0L, onDyno, Modifier.fillMaxWidth().padding(top = 10.dp))
}

@Composable
private fun PartsScreen(ui: UiModel, onBuy: (String) -> Unit, onFuel: (FuelKind) -> Unit) {
    val category = remember { androidx.compose.runtime.mutableStateOf(Category.INTAKE) }
    val previews = remember(ui.game.upgrades, ui.game.researched, ui.game.fuel, ui.game.perks, ui.game.knowledge) {
        Upgrades.all.associate { it.id to Logic.preview(ui.game, it.id) }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Hud(ui)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Category.entries.forEach { cat ->
                Text(
                    cat.title.uppercase(Locale.US),
                    color = if (category.value == cat) Lime else White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(end = 14.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { category.value = cat },
                )
            }
        }
        if (category.value == Category.FUEL) {
            FuelRow(ui, onFuel)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Upgrades.all.filter { it.category == category.value }, key = { it.id }) { upgrade ->
                val level = ui.game.upgrades[upgrade.id] ?: 0
                val lock = Logic.lockReason(ui.game, upgrade.id)
                val delta = previews[upgrade.id]
                val cost = if (level < upgrade.maxLevel) Logic.upgradeCost(ui.game, upgrade.id) else 0.0
                Column(Modifier.fillMaxWidth().border(1.dp, White).padding(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(upgrade.name, color = White, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("$level/${upgrade.maxLevel}", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    Text(upgrade.blurb, color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    if (delta != null) {
                        Text(
                            "${signed(delta.hp, 0)} hp   ${signed(delta.torque, 0)} lb-ft   ${signed(delta.mpg)} mpg   " +
                                "rel ${signed(delta.reliability, 0)}   coolant ${signed(delta.coolantC, 0)}°   oil ${signed(delta.oilC, 0)}°   egt ${signed(delta.egtC, 0)}°",
                            color = Cyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    val label = when {
                        level >= upgrade.maxLevel -> "Installed"
                        lock != null -> "Needs $lock"
                        else -> formatDollars(cost)
                    }
                    LineButton(
                        label,
                        Logic.canBuyUpgrade(ui.game, upgrade.id),
                        { onBuy(upgrade.id) },
                        Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FuelRow(ui: UiModel, onFuel: (FuelKind) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FuelKind.entries.forEach { fuel ->
            val open = Logic.fuelUnlocked(ui.game, fuel)
            val on = ui.game.fuel == fuel
            Text(
                fuel.name,
                color = if (on) Black else if (open) Lime else Olive,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(if (on) Lime else Black)
                    .border(1.dp, if (open) Lime else Olive)
                    .clickable(
                        enabled = open,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onFuel(fuel) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
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
                        ResearchStatus.LOCKED -> "Needs ${node.requires.joinToString { ResearchTree.byId(it).name }}"
                        ResearchStatus.OPEN -> "${formatCompact(node.cost)} RP"
                    }
                    LineButton(label, Logic.canBuyResearch(ui.game, node.id), { onBuy(node.id) }, Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun RebuildScreen(ui: UiModel, onPerk: (String) -> Unit, onPrestige: () -> Unit) {
    val gain = Prestige.knowledgeGain(ui.game.runEarnings)
    val free = Logic.knowledgeFree(ui.game)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Hud(ui)
        Text("NEW LONG-BLOCK", color = White, fontFamily = FontFamily.Monospace, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Money and parts go back to a stock 2.0. Tech you already studied stays. Engineering Knowledge stays and multiplies the contract.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "This run has earned ${formatDollars(ui.game.runEarnings)}. The notebook pays out at ${formatDollars(Prestige.MIN_EARNINGS)}.",
            color = Lime,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            if (gain > 0) "Rebuild pays $gain Engineering Knowledge." else "Not enough hours on this block yet.",
            color = if (gain > 0) Cyan else White,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Knowledge ${ui.game.knowledge} · free $free · rebuilds ${ui.game.prestiges} · multiplier x${String.format(Locale.US, "%.2f", ui.sheet.multiplier)}",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LineButton(if (gain > 0) "Rebuild the long-block" else "Keep the hours", gain > 0, onPrestige, Modifier.fillMaxWidth())
        Text("PERKS", color = Lime, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
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
    }
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
