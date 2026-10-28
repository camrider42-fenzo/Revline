package com.revline.idle.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
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
import com.revline.idle.game.Aim
import com.revline.idle.game.AimGoal
import com.revline.idle.game.Bottleneck
import com.revline.idle.game.Effect
import com.revline.idle.game.Logic
import com.revline.idle.game.SheetAim
import com.revline.idle.game.StatDelta
import com.revline.idle.game.Upgrade
import com.revline.idle.game.UpgradeMap
import com.revline.idle.game.Upgrades
import com.revline.idle.model.EngineSim
import com.revline.idle.model.FuelKind
import java.util.Locale
import kotlin.math.hypot
import kotlinx.coroutines.delay

/**
 * The map may move for a new focus request, and once when it first has a size.
 * A later size change, such as the detail panel growing after a purchase, is not a request.
 */
internal fun cameraShouldMove(appliedTick: Int, requestTick: Int, width: Float, height: Float): Boolean {
    if (width < 1f || height < 1f) return false
    return appliedTick != requestTick
}

private fun effectColor(effect: Effect): Color = when (effect) {
    Effect.POWER -> Lime
    Effect.ECONOMY -> Amber
    Effect.BOOST -> Cyan
    Effect.COOLING -> White
    Effect.DURABILITY -> Red
}

@Composable
fun PartsScreen(
    ui: UiModel,
    onBuy: (String) -> Unit,
    onRemove: (String) -> Unit,
    onFuel: (FuelKind) -> Unit,
    onGoal: (AimGoal) -> Unit,
) {
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
    val saver = LocalBatterySaver.current
    var pulse by remember { mutableFloatStateOf(0.7f) }
    LaunchedEffect(saver) {
        if (saver) {
            pulse = 0.55f
            return@LaunchedEffect
        }
        var bright = false
        while (true) {
            bright = !bright
            pulse = if (bright) 1f else 0.45f
            delay(700)
        }
    }
    val bucket = (ui.rpm / 50.0).toInt() * 50
    val liveLoad = if (ui.phase == DynoPhase.IDLE) Logic.loadFor(ui.game.throttle) else 1.0
    val soakBucket = (ui.game.soakC * 2.0).toInt()
    val live = remember(ui.sheet.params, bucket, liveLoad, soakBucket) {
        EngineSim.point(ui.sheet.params, bucket.toDouble(), liveLoad, ui.game.soakC)
    }
    val opening = if (ui.phase == DynoPhase.IDLE) ui.game.throttle else 1.0
    val soakRate = EngineSim.soakPer10s(ui.sheet.params, ui.rpm, opening)
    val suggestion = remember(ui.sheet.stats, live.heatSoakC, live.fuelLimited, soakRate, ui.game.upgrades, ui.game.prestiges) {
        Bottleneck.suggest(ui.game, ui.sheet.stats, live, soakRate)
    }
    val aimGoal = AimGoal.from(ui.game.aimGoal)
    val aim = remember(
        ui.game.upgrades,
        ui.game.researched,
        ui.game.prestiges,
        ui.game.fuel,
        ui.game.tune,
        ui.game.perks,
        ui.game.aimGoal,
        ui.game.throttle,
    ) {
        SheetAim.recommend(ui.game, aimGoal)
    }
    var focusTick by remember { mutableIntStateOf(0) }
    var pinned by remember { mutableStateOf<Effect?>(null) }
    var focusPart by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Black).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().zIndex(2f).background(Black)) {
            BottleneckBar(suggestion) {
                pinned = suggestion.effect
                focusPart = null
                focusTick++
            }
            AimBar(aim, aimGoal, onGoal) {
                val id = aim.upgradeId ?: return@AimBar
                selected.value = id
                pinned = UpgradeMap.effectOf(id)
                focusPart = id
                focusTick++
            }
            PartsStatus(ui, live)
            FuelRow(ui, onFuel)
        }
        BubbleMap(
            layout = layout,
            ui = ui,
            selected = selected.value,
            pulse = pulse,
            focus = pinned ?: suggestion.effect,
            focusPart = focusPart,
            focusTick = focusTick,
            onSelect = { selected.value = it },
            modifier = Modifier.weight(1f).fillMaxWidth().zIndex(0f),
        )
        Column(Modifier.fillMaxWidth().zIndex(2f).background(Black)) {
            if (selected.value == null) {
                PartsStatus(ui, live)
            } else {
                PartDetail(ui, selected.value!!, previews[selected.value], onBuy, onRemove)
            }
        }
    }
}

@Composable
private fun BubbleMap(
    layout: UpgradeMap.BubbleMap,
    ui: UiModel,
    selected: String?,
    pulse: Float,
    focus: Effect,
    focusPart: String?,
    focusTick: Int,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    var scale by remember { mutableFloatStateOf(0.9f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var appliedTick by remember { mutableIntStateOf(-1) }
    BoxWithConstraints(modifier) {
        val viewW = with(LocalDensity.current) { maxWidth.toPx() }
        val viewH = with(LocalDensity.current) { maxHeight.toPx() }
        androidx.compose.runtime.LaunchedEffect(viewW, viewH, focusTick) {
            // Buying a part changes the detail height, which restarts this effect.
            // Only a new request (or the first layout) may move the camera.
            if (!cameraShouldMove(appliedTick, focusTick, viewW, viewH)) return@LaunchedEffect
            val part = focusPart?.let { id -> layout.bubbles.firstOrNull { it.id == id } }
            if (part != null) {
                val fit = (minOf(viewW, viewH) * 0.22f / (part.r * density)).coerceIn(0.55f, 1.7f)
                scale = fit
                offset = Offset(
                    viewW / 2f - part.cx * density * fit,
                    viewH / 2f - part.cy * density * fit,
                )
            } else {
                val hub = layout.bubbles.first { it.id == focus.name }
                val reach = layout.bubbles
                    .filter { it.effect == focus }
                    .maxOf { hypot((it.cx - hub.cx).toDouble(), (it.cy - hub.cy).toDouble()).toFloat() + it.r }
                val fit = (minOf(viewW, viewH) / (2f * reach * density * 1.12f)).coerceIn(0.42f, 1.35f)
                scale = fit
                offset = Offset(
                    viewW / 2f - hub.cx * density * fit,
                    viewH / 2f - hub.cy * density * fit,
                )
            }
            appliedTick = focusTick
        }
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
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
                        val dx = to.cx - from.cx
                        val dy = to.cy - from.cy
                        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                        val ux = dx / len
                        val uy = dy / len
                        glowLine(
                            effectColor(edge.effect),
                            Offset((from.cx + ux * (from.r + 2f)) * d, (from.cy + uy * (from.r + 2f)) * d),
                            Offset((to.cx - ux * (to.r + 2f)) * d, (to.cy - uy * (to.r + 2f)) * d),
                            1.6f,
                            0.28f,
                        )
                    }
                    for (bubble in layout.bubbles) {
                        val level = if (bubble.hub) 0 else ui.game.upgrades[bubble.id] ?: 0
                        val affordable = !bubble.hub && Logic.canBuyUpgrade(ui.game, bubble.id)
                        val gain = when {
                            bubble.id == selected -> 0.85f
                            affordable -> 0.28f + pulse * 0.22f
                            bubble.hub || level > 0 -> 0.42f
                            else -> 0.16f
                        }
                        glowCircle(
                            effectColor(bubble.effect),
                            bubble.r * d,
                            Offset(bubble.cx * d, bubble.cy * d),
                            1.6f,
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
            .background(Black, CircleShape)
            .border(1.5.dp, color, CircleShape)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            bubble.effect.title.uppercase(Locale.US),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
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
) {
    val level = ui.game.upgrades[upgrade.id] ?: 0
    val locked = Logic.lockLabel(ui.game, upgrade.id)
    val color = when {
        selected -> White
        locked != null -> Olive
        else -> effectColor(UpgradeMap.effectOf(upgrade.id))
    }
    val price = when {
        level >= upgrade.maxLevel -> "$level/${upgrade.maxLevel}"
        locked != null -> locked
        ui.game.devMode -> "$level/${upgrade.maxLevel}  free"
        else -> "$level/${upgrade.maxLevel}  ${formatDollars(Logic.upgradeCost(ui.game, upgrade.id))}"
    }
    Box(
        Modifier
            .offset((bubble.cx - bubble.r).dp, (bubble.cy - bubble.r).dp)
            .size((bubble.r * 2f).dp)
            .background(Black, CircleShape)
            .border(if (selected) 2.dp else 1.dp, color, CircleShape)
            .padding(6.dp),
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                price,
                color = if (locked != null) Amber else if (level > 0) Lime else color,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun BottleneckBar(suggestion: com.revline.idle.game.Suggestion, onShow: () -> Unit) {
    val color = effectColor(suggestion.effect)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .border(1.dp, color)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onShow,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text("BOTTLENECK  ·  ${suggestion.headline.uppercase(Locale.US)}", color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(
            suggestion.detail,
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text("Show this bay", color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun AimBar(aim: Aim, goal: AimGoal, onGoal: (AimGoal) -> Unit, onShow: () -> Unit) {
    val color = aim.upgradeId?.let { effectColor(UpgradeMap.effectOf(it)) } ?: Amber
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .border(1.dp, color)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AimGoal.entries.forEach { item ->
                val on = item == goal
                Text(
                    item.short,
                    color = if (on) Black else color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(if (on) color else Black)
                        .border(1.dp, color)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onGoal(item) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clickable(
                    enabled = aim.upgradeId != null,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onShow,
                ),
        ) {
        Text("NEXT  ·  ${aim.headline.uppercase(Locale.US)}", color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            aim.detail,
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (aim.upgradeId != null) {
            Text("Show this part", color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
        }
    }
}

private fun soakRate(ui: UiModel): String {
    val opening = if (ui.phase == DynoPhase.IDLE) ui.game.throttle else 1.0
    val per10s = EngineSim.soakPer10s(ui.sheet.params, ui.rpm, opening)
    val sign = if (per10s >= 0.0) "+" else "−"
    return sign + String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(per10s)) + "°/10s"
}

@Composable
private fun PartsStatus(ui: UiModel, live: com.revline.idle.model.OperatingPoint) {
    val stats = ui.sheet.stats
    Text(
        "${formatDollars(ui.game.money)}   ${formatDollars(ui.sheet.incomePerSec)}/s    " +
            "sheet ${formatPower(stats.peakHorsepower)} hp  ${formatPower(stats.peakTorqueLbFt)} lb-ft  ${String.format(Locale.US, "%.1f", stats.cruiseMpg)} mpg",
        color = Lime,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        "${live.rpm.toInt()} rpm   load ${(live.load * 100).toInt()}%   " +
            "soak ${ui.game.soakC.toInt()}° ${soakRate(ui)}   cool ${live.coolantTempC.toInt()}°   oil ${live.oilTempC.toInt()}°   egt ${live.egtC.toInt()}°   " +
            "afr ${String.format(Locale.US, "%.1f", live.afr)}",
        color = White,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
    )
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
    onRemove: (String) -> Unit,
) {
    val upgrade = Upgrades.byId(id)
    val level = ui.game.upgrades[id] ?: 0
    val lock = Logic.lockLabel(ui.game, id)
    val cost = if (level < upgrade.maxLevel) Logic.upgradeCost(ui.game, id) else 0.0
    Column(
        Modifier
            .fillMaxWidth()
            .background(Black)
            .padding(top = 8.dp)
            .border(1.dp, effectColor(UpgradeMap.effectOf(upgrade.id)))
            .background(Black)
            .padding(8.dp),
    ) {
        Text(upgrade.name, color = White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text(
            upgrade.blurb,
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (delta != null) {
            Forecast(ui, delta)
        }
        val label = when {
            level >= upgrade.maxLevel -> "Installed"
            lock != null -> lock
            ui.game.devMode -> "Fit it · free (dev)"
            else -> formatDollars(cost)
        }
        val refund = if (level > 0) Logic.upgradeRefund(ui.game, id) else 0.0
        val pull = if (ui.game.devMode) "Pull one" else "Pull one · ${formatDollars(refund)}"
        LineButton(label, Logic.canBuyUpgrade(ui.game, id), { onBuy(id) }, Modifier.padding(top = 6.dp))
        if (level > 0) {
            LineButton(pull, true, { onRemove(id) }, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun FuelRow(ui: UiModel, onFuel: (FuelKind) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
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
