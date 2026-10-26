package com.revline.idle.game

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
}
