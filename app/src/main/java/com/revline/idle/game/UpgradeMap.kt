package com.revline.idle.game

import kotlin.math.cos
import kotlin.math.sin

/**
 * Parts map. An upgrade hangs off earlier upgrades when those earlier parts sit on
 * the research it still needs. Same research depth stays as siblings. A deeper
 * prior hides the shallower ones, so the line drawn is the immediate branch.
 */
object UpgradeMap {
    fun requiredResearch(upgrade: Upgrade): Set<String> = buildSet {
        upgrade.requires?.let { add(it) }
        addAll(upgrade.levelRequires.values)
    }

    fun researchClosure(id: String): Set<String> {
        val out = LinkedHashSet<String>()
        val stack = ArrayDeque<String>()
        stack.add(id)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!out.add(node)) continue
            ResearchTree.byId(node).requires.forEach { stack.add(it) }
        }
        return out
    }

    fun closure(upgrade: Upgrade): Set<String> =
        requiredResearch(upgrade).flatMap { researchClosure(it) }.toSet()

    fun parents(upgrade: Upgrade): List<String> {
        val child = closure(upgrade)
        if (child.isEmpty()) return emptyList()
        val candidates = Upgrades.all.mapNotNull { other ->
            if (other.id == upgrade.id) return@mapNotNull null
            val theirs = closure(other)
            if (theirs.isEmpty() || theirs.size >= child.size || !theirs.all { it in child }) return@mapNotNull null
            other.id to theirs
        }
        return candidates
            .filter { (id, mine) ->
                candidates.none { (otherId, theirs) ->
                    otherId != id && mine.size < theirs.size && mine.all { it in theirs }
                }
            }
            .map { it.first }
            .sorted()
    }

    /** Same-bay parent used to lay the tree out. Cross-bay parents are still drawn. */
    fun primaryParent(id: String): String? {
        val upgrade = Upgrades.byId(id)
        val same = parents(upgrade)
            .map { Upgrades.byId(it) }
            .filter { it.category == upgrade.category }
        return same.maxWithOrNull(compareBy<Upgrade> { closure(it).size }.thenBy { it.id })?.id
    }

    const val UPGRADE_R = 44f
    const val HUB_R = 52f

    fun effectOf(id: String): Effect = when (id) {
        "filter", "manifold", "itb", "headers", "long_tube", "cam", "vvt", "vvl",
        "compression", "bore", "coils", "base_timing", "mapped_timing", "springs",
        "lap", "gap", "limiter", "dyno_fuel", "rev_kit",
        -> Effect.POWER
        "jets", "afr_map", "catback", "cold_air", "boot", "tip", "needle",
        -> Effect.ECONOMY
        "supercharger", "turbo", "twin", "intercooler", "boost_controller",
        "fuel_pump", "injectors", "hpfp", "tbi_unit", "meth", "nitrous", "overboost",
        -> Effect.BOOST
        "radiator", "duct", "thermostat", "cap", "water_pump", "electric_pump",
        "oil_cooler", "waterless", "synthetic", "ceramic", "oil_service", "fins",
        -> Effect.COOLING
        "studs", "pistons", "rods", "crank", "billet", "inconel", "knock_box", "plugs", "torque_model",
        "wrench",
        -> Effect.DURABILITY
        else -> error("No effect for $id")
    }

    /** Parent used on the map: the prior part with the same effect, if there is one. */
    fun layoutParent(id: String): String? {
        val effect = effectOf(id)
        val same = parents(Upgrades.byId(id))
            .map { Upgrades.byId(it) }
            .filter { effectOf(it.id) == effect }
        return same.maxWithOrNull(compareBy<Upgrade> { closure(it).size }.thenBy { it.id })?.id
    }

    data class Bubble(
        val id: String,
        val cx: Float,
        val cy: Float,
        val r: Float,
        val effect: Effect,
        val hub: Boolean,
    )

    data class BubbleEdge(val from: String, val to: String, val effect: Effect)

    data class BubbleMap(
        val bubbles: List<Bubble>,
        val edges: List<BubbleEdge>,
        val width: Float,
        val height: Float,
    )

    /** Effect hubs with their parts on tight rings. Groups sit close enough to read as one map. */
    fun bubbles(): BubbleMap {
        val children = mutableMapOf<String, MutableList<Upgrade>>()
        val roots = mutableMapOf<Effect, MutableList<Upgrade>>()
        for (upgrade in Upgrades.all) {
            val parent = layoutParent(upgrade.id)
            if (parent == null) roots.getOrPut(effectOf(upgrade.id)) { mutableListOf() }.add(upgrade)
            else children.getOrPut(parent) { mutableListOf() }.add(upgrade)
        }
        children.values.forEach { it.sortBy { node -> node.id } }
        roots.values.forEach { it.sortBy { node -> node.id } }

        val cell = 640f
        val margin = 16f
        val cols = 3
        val bubbles = mutableListOf<Bubble>()
        val edges = mutableListOf<BubbleEdge>()
        Effect.entries.forEachIndexed { index, effect ->
            val col = index % cols
            val row = index / cols
            val cx = margin + cell * col + cell / 2f
            val cy = margin + cell * row + cell / 2f
            bubbles += Bubble(effect.name, cx, cy, HUB_R, effect, true)
            val ordered = ArrayDeque<Upgrade>()
            val seen = HashSet<String>()
            val queue = ArrayDeque(roots[effect].orEmpty())
            while (queue.isNotEmpty()) {
                val upgrade = queue.removeFirst()
                if (!seen.add(upgrade.id)) continue
                ordered += upgrade
                children[upgrade.id].orEmpty().forEach { queue.add(it) }
            }
            val rings = listOf(6, 10, 14)
            var cursor = 0
            rings.forEachIndexed { ring, capacity ->
                if (cursor >= ordered.size) return@forEachIndexed
                val count = minOf(capacity, ordered.size - cursor)
                val hop = 120f + ring * 100f
                for (i in 0 until count) {
                    val upgrade = ordered[cursor + i]
                    val stagger = if (ring % 2 == 1) Math.PI / count else 0.0
                    val angle = -Math.PI / 2.0 + stagger + i * (Math.PI * 2.0 / count)
                    bubbles += Bubble(
                        upgrade.id,
                        cx + (cos(angle) * hop).toFloat(),
                        cy + (sin(angle) * hop).toFloat(),
                        UPGRADE_R,
                        effect,
                        false,
                    )
                    val from = layoutParent(upgrade.id) ?: effect.name
                    edges += BubbleEdge(from, upgrade.id, effect)
                }
                cursor += count
            }
        }
        val rows = (Effect.entries.size + cols - 1) / cols
        return BubbleMap(
            bubbles,
            edges,
            margin * 2f + cell * cols,
            margin * 2f + cell * rows,
        )
    }
}
