package com.revline.idle.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revline.idle.DynoPhase
import com.revline.idle.UiModel
import com.revline.idle.economy.formatDollars
import com.revline.idle.game.Effect
import com.revline.idle.game.Logic
import com.revline.idle.game.StatDelta
import com.revline.idle.game.Upgrade
import com.revline.idle.game.UpgradeMap
import com.revline.idle.game.Upgrades
import com.revline.idle.model.EngineSim
import com.revline.idle.model.FuelKind
import java.util.Locale
import kotlin.math.hypot

private fun effectColor(effect: Effect): Color = when (effect) {
    Effect.POWER -> Lime
    Effect.ECONOMY -> Amber
    Effect.BOOST -> Cyan
    Effect.COOLING -> White
    Effect.DURABILITY -> Red
}

@Composable
fun PartsScreen(ui: UiModel, onBuy: (String) -> Unit, onFuel: (FuelKind) -> Unit) {
    val selected = remember { mutableStateOf<String?>(null) }
    val layout = remember { UpgradeMap.bubbles() }
    val pedalBucket = (ui.game.throttle * 20.0).toInt()
    val previews = remember(
        ui.game.upgrades,
        ui.game.researched,
        ui.game.fuel,
        ui.game.perks,
        ui.game.knowledge,
        ui.game.prestiges,
        pedalBucket,
    ) {
        Upgrades.all.associate { it.id to Logic.preview(ui.game, it.id) }
    }
    val pulse by rememberInfiniteTransition(label = "afford").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "affordPulse",
    )
    val bucket = (ui.rpm / 50.0).toInt() * 50
    val liveLoad = if (ui.phase == DynoPhase.IDLE) Logic.loadFor(ui.game.throttle) else 1.0
    val live = remember(ui.sheet.params, bucket, liveLoad) {
        EngineSim.point(ui.sheet.params, bucket.toDouble(), liveLoad)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        EngineStrip(ui, live)
        FuelRow(ui, onFuel)
        BubbleMap(
            layout = layout,
            ui = ui,
            previews = previews,
            selected = selected.value,
            pulse = pulse,
            onSelect = { selected.value = it },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        selected.value?.let { id ->
            PartDetail(ui, id, previews[id], onBuy)
        }
        EngineStrip(ui, live)
    }
}

@Composable
private fun BubbleMap(
    layout: UpgradeMap.BubbleMap,
    ui: UiModel,
    previews: Map<String, StatDelta?>,
    selected: String?,
    pulse: Float,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    var scale by remember { mutableFloatStateOf(0.9f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var framed by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier) {
        val viewW = with(LocalDensity.current) { maxWidth.toPx() }
        val viewH = with(LocalDensity.current) { maxHeight.toPx() }
        androidx.compose.runtime.LaunchedEffect(viewW, viewH) {
            if (framed || viewW < 1f || viewH < 1f) return@LaunchedEffect
            val focus = layout.bubbles.first { it.id == Effect.BOOST.name }
            val fit = (viewW / (560f * density)).coerceIn(0.72f, 1.15f)
            scale = fit
            offset = Offset(
                viewW / 2f - focus.cx * density * fit,
                viewH / 2f - focus.cy * density * fit,
            )
            framed = true
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(layout, viewW, viewH) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = down.position
                        var total = 0f
                        var lastCentroid = down.position
                        var lastSpan = 0f
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                                val span = hypot(a.x - b.x, a.y - b.y).coerceAtLeast(1f)
                                if (lastSpan > 1f) {
                                    val next = (scale * (span / lastSpan)).coerceIn(0.38f, 2.4f)
                                    val ratio = next / scale
                                    offset = centroid - (centroid - offset) * ratio
                                    scale = next
                                }
                                lastSpan = span
                                lastCentroid = centroid
                                total = 1000f
                                pressed.forEach { it.consume() }
                            } else if (pressed.size == 1) {
                                val change = pressed[0]
                                val delta = change.position - lastCentroid
                                lastCentroid = change.position
                                lastSpan = 0f
                                total += hypot(delta.x, delta.y)
                                if (total > viewConfiguration.touchSlop) {
                                    offset += delta
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        val mapW = layout.width * density * scale
                        val mapH = layout.height * density * scale
                        offset = Offset(
                            offset.x.coerceIn(viewW - mapW - 48f, 48f),
                            offset.y.coerceIn(viewH - mapH - 48f, 48f),
                        )
                        if (total <= viewConfiguration.touchSlop) {
                            val mapX = (start.x - offset.x) / scale / density
                            val mapY = (start.y - offset.y) / scale / density
                            val hit = layout.bubbles
                                .filter { !it.hub }
                                .filter { bubble ->
                                    hypot(bubble.cx - mapX, bubble.cy - mapY) <= bubble.r
                                }
                                .minByOrNull { it.r }
                            if (hit != null) onSelect(hit.id)
                        }
                    }
                },
        ) {
            Box(
                Modifier.graphicsLayer {
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            ) {
                Canvas(Modifier.size(layout.width.dp, layout.height.dp)) {
                    val index = layout.bubbles.associateBy { it.id }
                    val d = density
                    for (edge in layout.edges) {
                        val from = index[edge.from] ?: continue
                        val to = index[edge.to] ?: continue
                        glowLine(
                            effectColor(edge.effect),
                            Offset(from.cx * d, from.cy * d),
                            Offset(to.cx * d, to.cy * d),
                            2.4f,
                            0.7f,
                        )
                    }
                    for (bubble in layout.bubbles) {
                        val level = if (bubble.hub) 0 else ui.game.upgrades[bubble.id] ?: 0
                        val affordable = !bubble.hub && Logic.canBuyUpgrade(ui.game, bubble.id)
                        val gain = when {
                            bubble.id == selected -> 1.15f
                            affordable -> pulse
                            bubble.hub || level > 0 -> 0.8f
                            else -> 0.32f
                        }
                        glowCircle(
                            effectColor(bubble.effect),
                            bubble.r * d,
                            Offset(bubble.cx * d, bubble.cy * d),
                            2.4f,
                            gain = gain,
                        )
                    }
                }
                layout.bubbles.forEach { bubble ->
                    if (bubble.hub) {
                        HubBubble(bubble)
                    } else {
                        UpgradeBubble(
                            bubble = bubble,
                            upgrade = Upgrades.byId(bubble.id),
                            ui = ui,
                            selected = selected == bubble.id,
                            delta = previews[bubble.id],
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubBubble(bubble: UpgradeMap.Bubble) {
    val color = effectColor(bubble.effect)
    Box(
        Modifier
            .offset((bubble.cx - bubble.r).dp, (bubble.cy - bubble.r).dp)
            .size((bubble.r * 2f).dp)
            .border(1.5.dp, color, CircleShape)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            bubble.effect.title.uppercase(Locale.US),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UpgradeBubble(
    bubble: UpgradeMap.Bubble,
    upgrade: Upgrade,
    ui: UiModel,
    selected: Boolean,
    delta: StatDelta?,
) {
    val level = ui.game.upgrades[upgrade.id] ?: 0
    val locked = Logic.lockLabel(ui.game, upgrade.id)
    val affordable = Logic.canBuyUpgrade(ui.game, upgrade.id)
    val color = when {
        selected -> White
        locked != null -> Olive
        affordable || level > 0 -> effectColor(UpgradeMap.effectOf(upgrade.id))
        else -> effectColor(UpgradeMap.effectOf(upgrade.id))
    }
    val price = when {
        level >= upgrade.maxLevel -> "Full"
        locked != null -> locked
        else -> formatDollars(Logic.upgradeCost(ui.game, upgrade.id))
    }
    Box(
        Modifier
            .offset((bubble.cx - bubble.r).dp, (bubble.cy - bubble.r).dp)
            .size((bubble.r * 2f).dp)
            .border(if (selected) 2.dp else 1.dp, color, CircleShape)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                upgrade.name,
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$level/${upgrade.maxLevel}",
                color = if (level > 0) Lime else color,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (delta != null) {
                Text(
                    forecastLabel(delta),
                    color = Lime,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Text(
                price,
                color = if (locked != null) Amber else color,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun forecastLabel(delta: StatDelta): String {
    val options = listOf(
        delta.peakHp to "${signed(delta.peakHp, 2)} hp",
        delta.peakTorque to "${signed(delta.peakTorque, 2)} tq",
        delta.cruiseMpg to "${signed(delta.cruiseMpg, 1)} mpg",
        delta.payPerSec to "${signed(delta.payPerSec, 3)}/s",
    )
    val best = options.maxBy { kotlin.math.abs(it.first) }
    return if (kotlin.math.abs(best.first) < 0.005) "no change" else best.second
}

@Composable
private fun Forecast(ui: UiModel, delta: StatDelta) {
    val stats = ui.sheet.stats
    ForecastRow("Peak hp", stats.peakHorsepower, stats.peakHorsepower + delta.peakHp, "hp")
    ForecastRow("Torque", stats.peakTorqueLbFt, stats.peakTorqueLbFt + delta.peakTorque, "lb-ft")
    ForecastRow("Cruise", stats.cruiseMpg, stats.cruiseMpg + delta.cruiseMpg, "mpg")
    Text(
        "Contract  ${signed(delta.payPerSec, if (kotlin.math.abs(delta.payPerSec) < 0.1) 3 else 2)}/s at this pedal",
        color = if (delta.payPerSec > 0.0005) Lime else if (delta.payPerSec < -0.0005) Red else White,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun ForecastRow(label: String, now: Double, next: Double, unit: String) {
    val digits = stepDigits(now, next - now)
    val pattern = "%.${digits}f"
    val changed = kotlin.math.abs(next - now) >= 0.5 * Math.pow(10.0, -digits.toDouble())
    Text(
        "$label  ${String.format(java.util.Locale.US, pattern, now)} → ${String.format(java.util.Locale.US, pattern, next)} $unit",
        color = if (changed) Lime else White,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun PartDetail(
    ui: UiModel,
    id: String,
    delta: com.revline.idle.game.StatDelta?,
    onBuy: (String) -> Unit,
) {
    val upgrade = Upgrades.byId(id)
    val level = ui.game.upgrades[id] ?: 0
    val lock = Logic.lockLabel(ui.game, id)
    val cost = if (level < upgrade.maxLevel) Logic.upgradeCost(ui.game, id) else 0.0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, effectColor(UpgradeMap.effectOf(upgrade.id)))
            .padding(8.dp),
    ) {
        Text(upgrade.name, color = White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text(
            upgrade.blurb,
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (delta != null) {
            Forecast(ui, delta)
        }
        val label = when {
            level >= upgrade.maxLevel -> "Installed"
            lock != null -> lock
            else -> formatDollars(cost)
        }
        LineButton(
            label,
            Logic.canBuyUpgrade(ui.game, id),
            { onBuy(id) },
            Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun FuelRow(ui: UiModel, onFuel: (FuelKind) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FuelKind.entries.forEach { fuel ->
            val open = Logic.fuelUnlocked(ui.game, fuel)
            val on = ui.game.fuel == fuel
            Text(
                "${fuel.name} $${String.format(Locale.US, "%.2f", fuel.dollarsPerGallon)}",
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
