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

    const val UPGRADE_R = 62f
    const val CATEGORY_R = 74f

    data class Bubble(
        val id: String,
        val cx: Float,
        val cy: Float,
        val r: Float,
        val category: Category,
        val categoryNode: Boolean,
    )

    data class BubbleEdge(val from: String, val to: String, val category: Category)

    data class BubbleMap(
        val bubbles: List<Bubble>,
        val edges: List<BubbleEdge>,
        val width: Float,
        val height: Float,
    )

    /** Category bubbles with the parts in that bay on rings around them. No cross-bay lines. */
    fun bubbles(): BubbleMap {
        val children = mutableMapOf<String, MutableList<Upgrade>>()
        val roots = mutableMapOf<Category, MutableList<Upgrade>>()
        for (upgrade in Upgrades.all) {
            val parent = primaryParent(upgrade.id)
            if (parent == null) roots.getOrPut(upgrade.category) { mutableListOf() }.add(upgrade)
            else children.getOrPut(parent) { mutableListOf() }.add(upgrade)
        }
        children.values.forEach { it.sortBy { node -> node.id } }
        roots.values.forEach { it.sortBy { node -> node.id } }

        val cell = 1500f
        val margin = 90f
        val cols = 3
        val bubbles = mutableListOf<Bubble>()
        val edges = mutableListOf<BubbleEdge>()
        Category.entries.forEachIndexed { index, category ->
            val col = index % cols
            val row = index / cols
            val cx = margin + cell * col + cell / 2f
            val cy = margin + cell * row + cell / 2f
            bubbles += Bubble(category.name, cx, cy, CATEGORY_R, category, true)
            val layers = mutableListOf<List<Upgrade>>()
            var frontier = roots[category].orEmpty()
            while (frontier.isNotEmpty()) {
                layers += frontier
                frontier = frontier.flatMap { children[it.id].orEmpty() }
            }
            layers.forEachIndexed { layerIndex, layer ->
                val hop = 210f + layerIndex * 155f
                val count = layer.size.coerceAtLeast(1)
                layer.forEachIndexed { i, upgrade ->
                    val stagger = if (layerIndex % 2 == 1) Math.PI / count else 0.0
                    val angle = -Math.PI / 2.0 + stagger + i * (Math.PI * 2.0 / count)
                    bubbles += Bubble(
                        upgrade.id,
                        cx + (cos(angle) * hop).toFloat(),
                        cy + (sin(angle) * hop).toFloat(),
                        UPGRADE_R,
                        category,
                        false,
                    )
                    val from = primaryParent(upgrade.id) ?: category.name
                    edges += BubbleEdge(from, upgrade.id, category)
                }
            }
        }
        val rows = (Category.entries.size + cols - 1) / cols
        return BubbleMap(
            bubbles,
            edges,
            margin * 2f + cell * cols,
            margin * 2f + cell * rows,
        )
    }
}
