package com.revline.idle.game

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
}
