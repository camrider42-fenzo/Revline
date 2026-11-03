package com.revline.idle.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.revline.idle.game.Circuits
import com.revline.idle.game.Tires
import java.util.Locale
import kotlin.math.max

/** The pieces of the race screen that read and change the player's pit plan. Only what the sim models is shown: no fuel, no track temperature. */
internal object CircuitStrategy {
    /** The text lines of the conditions strip for [lap] (from 1) with [frac] of it run: the track and its forecast, the tires, the engine. */
    fun lines(result: Circuits.Result, lap: Int, frac: Double): List<Pair<String, Boolean>> {
        val laps = result.circuit.laps
        val sky = result.weather.title.lowercase(Locale.US)
        val track = "TRACK ${sky.uppercase(Locale.US)}  FORECAST " +
            if (lap >= laps) "last lap, $sky" else "L${lap + 1}-L$laps $sky, no change"
        val c = result.conditions.getOrNull(lap - 1) ?: return listOf(track to (result.weather == Circuits.Weather.DRY))
        val grip = (c.gripStart + (c.gripEnd - c.gripStart) * frac.coerceIn(0.0, 1.0)) * 100.0
        val next = result.orders.fit.stops.firstOrNull { it >= lap }
        val tires = String.format(Locale.US, "TIRES %s %s  grip %.0f%%  %d lap%s old", c.tire.letter, c.tire.title.uppercase(Locale.US), grip, c.age, if (c.age == 1) "" else "s") +
            (if (next != null) "  stop L$next" else "")
        val engine = String.format(Locale.US, "ENGINE %.0f%% power  failure risk %.1f%%/lap", c.power * 100.0, c.risk * 100.0)
        return listOf(track to (result.weather == Circuits.Weather.DRY), tires to true, engine to (c.risk < 0.01))
    }
}

/** The track, tires and engine as the sim has them on the lap the player is on, three short lines. */
@Composable
internal fun CircuitConditions(result: Circuits.Result, lap: Int, frac: Double, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        for ((text, calm) in CircuitStrategy.lines(result, lap, frac)) {
            Text(text, color = if (calm) Cyan else Amber, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 2)
        }
    }
}

@Composable
private fun PitChip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (!enabled) Olive.copy(alpha = 0.45f) else if (selected) Lime else Olive
    Text(
        text,
        color = if (selected && enabled) Color.Black else color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier
            .background(if (selected && enabled) Lime else Color.Transparent)
            .border(BorderStroke(1.dp, color))
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

/**
 * The pit wall, as a sheet over the race: change the stop laps and the tires from the lap in progress, or call "box this lap".
 * The race is run again from [lap] when the call is made; the laps before it stay as they were. Stops already made are locked.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun StrategySheet(result: Circuits.Result, lap: Int, onCall: (Circuits.Orders) -> Unit, onClose: () -> Unit) {
    val laps = result.circuit.laps
    val weather = result.weather
    val old = result.orders
    val fit = old.fit
    val stops = Tires.stopsOf(fit, laps)
    var s1 by remember { mutableStateOf(stops.getOrElse(0) { 0 }) }
    var s2 by remember { mutableStateOf(stops.getOrElse(1) { 0 }) }
    var mid by remember { mutableStateOf(fit.middle) }
    var fin by remember { mutableStateOf(fit.finish) }
    val pool = if (weather == Circuits.Weather.DRY) Tires.dry else Tires.Compound.entries
    val draft = Circuits.Orders(fit.start, s1, fin, old.pace, s2, if (s2 > 0) mid else fin)
    val problem = if (draft == old.copy(middle = old.fit.middle)) "No change to the plan." else Circuits.reviseCheck(result, draft, lap)
    val box = Circuits.boxNow(old, lap, laps, weather)
    val boxProblem = if (box == null) "No stop left to call." else Circuits.reviseCheck(result, box, lap)
    val made1 = s1 in 1 until lap
    val made2 = s2 in 1 until lap
    fun other(c: Tires.Compound): Tires.Compound = pool.firstOrNull { it != c && it != Tires.Compound.SOFT } ?: pool.first { it != c }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).background(Color.Black).border(1.dp, Lime).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("PIT STRATEGY  lap $lap of $laps", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                LineButton("Cancel", true, onClose)
            }
            Text("Now: ${Circuits.planText(old)}", color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            Text(Tires.rule(weather), color = Olive, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            @Composable
            fun stopRow(n: Int, lapAt: Int, locked: Boolean, floor: Int, ceil: Int, then: Tires.Compound, setLap: (Int) -> Unit, setThen: (Tires.Compound) -> Unit) {
                Column(Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Stop $n" + if (locked) "  done, lap $lapAt" else "  lap", color = White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        if (!locked) {
                            PitChip("-", false, lapAt > floor) { setLap(lapAt - 1) }
                            Text("$lapAt", color = Lime, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                            PitChip("+", false, lapAt < ceil) { setLap(lapAt + 1) }
                        }
                    }
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("onto", color = White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        for (c in pool) PitChip(c.letter, c == then, !locked) { setThen(c) }
                        Text(then.title, color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
            if (s1 > 0) {
                val ceil1 = if (s2 > 0) s2 - 1 else laps - 1
                stopRow(1, s1, made1, max(lap, 1), ceil1, if (s2 > 0) mid else fin, { s1 = it }) { if (s2 > 0) mid = it else fin = it }
            }
            if (s2 > 0) stopRow(2, s2, made2, max(lap, s1 + 1), laps - 1, fin, { s2 = it }) { fin = it }
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if ((s2 == 0 && !made1 && s1 > 0) || (s2 > 0 && !made2)) {
                    LineButton(if (s2 > 0) "Drop stop 2" else "Drop the stop", true, {
                        if (s2 > 0) { s2 = 0; fin = mid } else { s1 = 0 }
                    })
                }
                if (s1 == 0 && !made1) {
                    LineButton("Add a stop", lap < laps - 1, {
                        s1 = ((lap + laps) / 2).coerceIn(lap, laps - 1)
                        if (fin == fit.start) fin = other(fit.start)
                        mid = fin
                    })
                } else if (s2 == 0 && s1 > 0) {
                    LineButton("Add stop 2", lap < laps - 1, {
                        s2 = ((max(lap, s1) + laps) / 2).coerceIn(max(lap, s1 + 1), laps - 1)
                        mid = fin
                        fin = other(mid)
                    })
                }
            }
            Text(
                problem ?: "Laps $lap to $laps are run again from here. The laps before stay as they were.",
                color = if (problem == null) Lime else Red, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LineButton("Apply the plan", problem == null, { onCall(draft); onClose() })
                LineButton("Box this lap", boxProblem == null, { box?.let { onCall(it); onClose() } })
            }
            if (boxProblem != null) Text("Box this lap: $boxProblem", color = Olive, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
