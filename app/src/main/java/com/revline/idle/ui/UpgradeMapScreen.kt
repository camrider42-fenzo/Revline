package com.revline.idle.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.revline.idle.UiModel
import com.revline.idle.economy.formatDollars
import com.revline.idle.game.Category
import com.revline.idle.game.Logic
import com.revline.idle.game.Upgrade
import com.revline.idle.game.UpgradeMap
import com.revline.idle.game.Upgrades
import com.revline.idle.model.FuelKind
import java.util.Locale

private const val NODE_W = 156f
private const val NODE_H = 74f
private const val CAT_W = 112f
private const val GAP_X = 48f
private const val GAP_Y = 16f
private const val CAT_GAP = 28f

private data class NodeSpot(val id: String, val x: Float, val y: Float, val category: Category, val categoryNode: Boolean)
private data class EdgeSpot(val from: String, val to: String, val category: Category, val solid: Boolean)

private data class MapLayout(
    val nodes: List<NodeSpot>,
    val edges: List<EdgeSpot>,
    val width: Float,
    val height: Float,
)

private fun layoutMap(): MapLayout {
    val byParent = mutableMapOf<String, MutableList<Upgrade>>()
    val roots = mutableMapOf<Category, MutableList<Upgrade>>()
    for (upgrade in Upgrades.all) {
        val parent = UpgradeMap.primaryParent(upgrade.id)
        if (parent == null) roots.getOrPut(upgrade.category) { mutableListOf() }.add(upgrade)
        else byParent.getOrPut(parent) { mutableListOf() }.add(upgrade)
    }
    byParent.values.forEach { it.sortBy { node -> node.id } }
    roots.values.forEach { it.sortBy { node -> node.id } }
    val spots = mutableListOf<NodeSpot>()
    var y = 10f
    var maxX = CAT_W
    fun xOf(col: Int) = CAT_W + 24f + (col - 1) * (NODE_W + GAP_X)
    fun place(upgrade: Upgrade, col: Int): Float {
        val kids = byParent[upgrade.id].orEmpty()
        val x = xOf(col)
        maxX = maxOf(maxX, x + NODE_W)
        if (kids.isEmpty()) {
            spots += NodeSpot(upgrade.id, x, y, upgrade.category, false)
            val center = y + NODE_H / 2f
            y += NODE_H + GAP_Y
            return center
        }
        val centers = kids.map { place(it, col + 1) }
        val mid = (centers.first() + centers.last()) / 2f
        spots += NodeSpot(upgrade.id, x, mid - NODE_H / 2f, upgrade.category, false)
        return mid
    }
    for (category in Category.entries) {
        val top = y
        val list = roots[category].orEmpty()
        if (list.isEmpty()) y += NODE_H + GAP_Y
        else list.forEach { place(it, 1) }
        val bottom = y - GAP_Y
        val catY = (top + bottom) / 2f - NODE_H / 2f
        spots += NodeSpot(category.name, 0f, catY, category, true)
        y += CAT_GAP
    }
    val index = spots.associateBy { it.id }
    val edges = mutableListOf<EdgeSpot>()
    for (upgrade in Upgrades.all) {
        val parents = UpgradeMap.parents(upgrade)
        val primary = UpgradeMap.primaryParent(upgrade.id)
        if (primary == null) {
            edges += EdgeSpot(upgrade.category.name, upgrade.id, upgrade.category, true)
        }
        for (parent in parents) {
            if (index[parent] == null) continue
            edges += EdgeSpot(parent, upgrade.id, upgrade.category, parent == primary)
        }
    }
    return MapLayout(spots, edges, maxX + 18f, y + 8f)
}

private fun categoryColor(category: Category): Color = when (category) {
    Category.INTAKE -> Lime
    Category.EXHAUST -> Red
    Category.FUEL -> Amber
    Category.IGNITION -> White
    Category.VALVETRAIN -> Cyan
    Category.INDUCTION -> Lime
    Category.INTERNALS -> Amber
    Category.COOLING -> Cyan
    Category.ECU -> Red
}

@Composable
fun PartsScreen(ui: UiModel, onBuy: (String) -> Unit, onFuel: (FuelKind) -> Unit) {
    val selected = remember { mutableStateOf<String?>(null) }
    val layout = remember { layoutMap() }
    val previews = remember(ui.game.upgrades, ui.game.researched, ui.game.fuel, ui.game.perks, ui.game.knowledge) {
        Upgrades.all.associate { it.id to Logic.preview(ui.game, it.id) }
    }
    val pulse by rememberInfiniteTransition(label = "afford").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "affordPulse",
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        HudLine(ui)
        Text(
            "PARTS MAP",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "Solid lines are the next part in that bay. Dashed lines reach a part in another bay that this one still waits on.",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        FuelRow(ui, onFuel)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                MapBody(layout, ui, selected.value, pulse) { selected.value = it }
            }
        }
        selected.value?.let { id ->
            PartDetail(ui, id, previews[id], onBuy)
        }
    }
}

@Composable
private fun MapBody(
    layout: MapLayout,
    ui: UiModel,
    selected: String?,
    pulse: Float,
    onSelect: (String) -> Unit,
) {
    val density = LocalDensity.current
    Box(Modifier.size(layout.width.dp, layout.height.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val index = layout.nodes.associateBy { it.id }
            for (spot in layout.nodes) {
                if (spot.categoryNode) continue
                val level = ui.game.upgrades[spot.id] ?: 0
                val affordable = Logic.canBuyUpgrade(ui.game, spot.id)
                val gain = when {
                    affordable -> pulse
                    level > 0 -> 0.75f
                    else -> 0.28f
                }
                val left = with(density) { spot.x.dp.toPx() }
                val top = with(density) { spot.y.dp.toPx() }
                val right = with(density) { (spot.x + NODE_W).dp.toPx() }
                val bottom = with(density) { (spot.y + NODE_H).dp.toPx() }
                val color = categoryColor(spot.category)
                glowLine(color, Offset(left, top), Offset(right, top), 2.2f, gain)
                glowLine(color, Offset(right, top), Offset(right, bottom), 2.2f, gain)
                glowLine(color, Offset(right, bottom), Offset(left, bottom), 2.2f, gain)
                glowLine(color, Offset(left, bottom), Offset(left, top), 2.2f, gain)
            }
            for (edge in layout.edges) {
                val from = index[edge.from] ?: continue
                val to = index[edge.to] ?: continue
                val fromW = if (from.categoryNode) CAT_W else NODE_W
                val x1 = with(density) { (from.x + fromW).dp.toPx() }
                val y1 = with(density) { (from.y + NODE_H / 2f).dp.toPx() }
                val x2 = with(density) { to.x.dp.toPx() }
                val y2 = with(density) { (to.y + NODE_H / 2f).dp.toPx() }
                val path = Path().apply {
                    moveTo(x1, y1)
                    cubicTo(x1 + (x2 - x1) * 0.5f, y1, x2 - (x2 - x1) * 0.45f, y2, x2, y2)
                }
                val owned = (ui.game.upgrades[edge.to] ?: 0) > 0
                glowPath(
                    categoryColor(edge.category),
                    path,
                    if (edge.solid) 2.2f else 1.6f,
                    gain = if (owned) 1f else 0.4f,
                    dashed = !edge.solid,
                )
            }
        }
        layout.nodes.forEach { spot ->
            if (spot.categoryNode) {
                CategoryNode(spot)
            } else {
                val upgrade = Upgrades.byId(spot.id)
                UpgradeNode(
                    upgrade = upgrade,
                    level = ui.game.upgrades[upgrade.id] ?: 0,
                    locked = Logic.lockReason(ui.game, upgrade.id) != null,
                    affordable = Logic.canBuyUpgrade(ui.game, upgrade.id),
                    selected = selected == upgrade.id,
                    pulse = pulse,
                    modifier = Modifier.offset(spot.x.dp, spot.y.dp).size(NODE_W.dp, NODE_H.dp),
                    onClick = { onSelect(upgrade.id) },
                )
            }
        }
    }
}

@Composable
private fun CategoryNode(spot: NodeSpot) {
    val color = categoryColor(spot.category)
    Box(
        Modifier
            .offset(spot.x.dp, spot.y.dp)
            .size(CAT_W.dp, NODE_H.dp)
            .border(1.dp, color)
            .padding(8.dp),
    ) {
        Text(
            spot.category.title.uppercase(Locale.US),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun UpgradeNode(
    upgrade: Upgrade,
    level: Int,
    locked: Boolean,
    affordable: Boolean,
    selected: Boolean,
    pulse: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val base = categoryColor(upgrade.category)
    val color = when {
        selected -> White
        level >= upgrade.maxLevel -> base
        affordable -> base
        locked -> Olive
        level > 0 -> base
        else -> base.copy(alpha = 0.75f)
    }
    val gain = if (affordable) pulse else if (level > 0) 0.85f else 0.35f
    Box(
        modifier
            .border((if (selected) 2 else 1).dp, color.copy(alpha = (0.35f + gain).coerceAtMost(1f)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(6.dp),
    ) {
        Column {
            Text(
                upgrade.name,
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$level/${upgrade.maxLevel}",
                color = if (level > 0) Lime else color,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
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
    val lock = Logic.lockReason(ui.game, id)
    val cost = if (level < upgrade.maxLevel) Logic.upgradeCost(ui.game, id) else 0.0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, categoryColor(upgrade.category))
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
private fun HudLine(ui: UiModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDollars(ui.game.money), color = Lime, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
        Text(
            "${formatDollars(ui.sheet.incomePerSec)}/s",
            color = Lime,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
    }
}
