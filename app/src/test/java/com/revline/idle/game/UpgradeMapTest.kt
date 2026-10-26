package com.revline.idle.game

import com.revline.idle.data.GameState
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeMapTest {
    @Test
    fun branchesFollowResearchTheChildStillNeeds() {
        assertTrue(UpgradeMap.parents(Upgrades.byId("filter")).isEmpty())
        assertTrue("turbo" in UpgradeMap.parents(Upgrades.byId("twin")))
        assertTrue("tbi_unit" in UpgradeMap.parents(Upgrades.byId("injectors")))
        assertTrue("manifold" in UpgradeMap.parents(Upgrades.byId("itb")))
        assertTrue("injectors" in UpgradeMap.parents(Upgrades.byId("hpfp")))
        assertFalse("tbi_unit" in UpgradeMap.parents(Upgrades.byId("hpfp")))
        assertTrue("vvt" in UpgradeMap.parents(Upgrades.byId("vvl")))
        assertTrue("afr_map" in UpgradeMap.parents(Upgrades.byId("torque_model")))
    }

    @Test
    fun everyPartIsOnTheMapAndTheTreeDoesNotCycle() {
        val ids = Upgrades.all.map { it.id }.toSet()
        for (upgrade in Upgrades.all) {
            val parents = UpgradeMap.parents(upgrade)
            assertEquals(parents, parents.distinct())
            parents.forEach { parent ->
                assertTrue(upgrade.id, parent in ids)
                assertFalse(parent == upgrade.id)
                val childNeeds = UpgradeMap.closure(upgrade)
                UpgradeMap.requiredResearch(Upgrades.byId(parent)).forEach { research ->
                    assertTrue("${upgrade.id} <- $parent missing $research", research in childNeeds)
                }
            }
        }
        val seen = mutableSetOf<String>()
        fun walk(id: String) {
            assertTrue("cycle at $id", seen.add(id))
            UpgradeMap.primaryParent(id)?.let { walk(it) }
            seen.remove(id)
        }
        ids.forEach { walk(it) }
    }

    @Test
    fun bubblesStayApartAndLinesDoNotCrossBays() {
        val map = UpgradeMap.bubbles()
        assertEquals(Upgrades.all.size + Category.entries.size, map.bubbles.size)
        val bubbles = map.bubbles
        for (i in bubbles.indices) {
            for (j in i + 1 until bubbles.size) {
                val a = bubbles[i]
                val b = bubbles[j]
                val gap = hypot((a.cx - b.cx).toDouble(), (a.cy - b.cy).toDouble())
                assertTrue("${a.id} overlaps ${b.id}", gap + 1.0 >= a.r + b.r)
            }
        }
        for (edge in map.edges) {
            val from = map.bubbles.first { it.id == edge.from }
            val to = map.bubbles.first { it.id == edge.to }
            assertEquals(edge.to, from.category, to.category)
            assertFalse(from.categoryNode && to.categoryNode)
        }
        val open = Logic.lockLabel(GameState(), "filter")
        assertEquals(null, open)
        val headers = Logic.lockLabel(GameState(), "headers")
        assertTrue(headers!!.contains("Header fabrication"))
        val twin = Logic.lockLabel(GameState(), "twin")
        assertTrue(twin!!.contains("Turbocharger"))
    }
}
