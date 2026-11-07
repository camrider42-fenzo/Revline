package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sheet is read at the profit pedal. On the single rotor that pedal stays
 * in boost, so a turbo with the intake, exhaust, intercooler and the other
 * power parts still fitted reads less than the stock engine. The goal sits
 * just under that build. The same parts clear every other engine's goal.
 */
class SingleRotorMpgGoalTest {
    private val research = ResearchTree.all.map { it.id }.toSet()

    /** Power hardware a built engine keeps, on top of every economy part that fits. */
    private val power = mapOf(
        "turbo" to 5,
        "intercooler" to 4,
        "filter" to 5,
        "manifold" to 2,
        "headers" to 4,
        "ceramic" to 1,
        "fuel_pump" to 4,
        "compression" to 3,
        "plugs" to 3,
        "base_timing" to 3,
        "coils" to 2,
        "gap" to 3,
        "port_street" to 2,
        "apex_seals" to 2,
        "cam" to 2,
    )

    @Test
    fun theGoalIsReachableWithThePowerPartsKept() {
        val block = Block.ROTARY_1R
        val kept = sheet(block)
        assertTrue("turbo stayed on", keptUpgrades(block).getValue("turbo") > 0)
        assertTrue("intercooler stayed on", keptUpgrades(block).getValue("intercooler") > 0)
        assertTrue("headers stayed on", keptUpgrades(block).getValue("headers") > 0)
        assertTrue("intake stayed on", keptUpgrades(block).getValue("filter") > 0 && keptUpgrades(block).getValue("manifold") > 0)
        assertTrue(
            "kept ${kept.profit.mpg} @ ${(kept.profit.throttle * 100).toInt()}% vs ${block.mpgGoal}",
            kept.profit.mpg >= block.mpgGoal,
        )
        assertTrue("hp ${kept.stats.peakHorsepower} vs ${block.hpGoal}", kept.stats.peakHorsepower >= block.hpGoal)
        assertTrue("tq ${kept.stats.peakTorqueLbFt} vs ${block.torqueGoal}", kept.stats.peakTorqueLbFt >= block.torqueGoal)
        assertTrue(block.mpgGoal < block.mpgCap())
        val stock = Logic.sheet(state(block, emptyMap()))
        assertTrue("stock hp ${stock.stats.peakHorsepower}", stock.stats.peakHorsepower < block.hpGoal)
    }

    @Test
    fun theSameBuildClearsEveryOtherEnginesGoal() {
        for (block in Block.entries) {
            if (block == Block.ROTARY_1R) continue
            val kept = sheet(block)
            assertTrue(
                "${block.title} ${kept.profit.mpg} vs ${block.mpgGoal}",
                kept.profit.mpg >= block.mpgGoal,
            )
        }
    }

    private fun sheet(block: Block) = Logic.sheet(state(block, keptUpgrades(block)))

    private fun keptUpgrades(block: Block): Map<String, Int> {
        val economy = (Logic.ECONOMY_IDS + Logic.NEW_ECONOMY_IDS).associateWith { Upgrades.byId(it).maxLevel }
        return (economy + power)
            .filterKeys { block.family.allows(it) }
            .mapValues { (id, level) -> level.coerceAtMost(Upgrades.byId(id).maxLevel) }
    }

    private fun state(block: Block, upgrades: Map<String, Int>) = GameState(
        prestiges = block.ladderStep,
        blockId = block.name,
        researched = research,
        upgrades = upgrades,
        money = 1e12,
    )
}
