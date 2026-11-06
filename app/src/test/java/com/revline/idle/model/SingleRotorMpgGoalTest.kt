package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.Upgrades
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The single-rotor sheet is read at the profit pedal. A maxed turbo keeps that
 * pedal in boost, so the goal has to sit where the economy parts can still clear
 * it, the way the thumper's goal sits under the same build.
 */
class SingleRotorMpgGoalTest {
    private val block = Block.ROTARY_1R
    private val research = setOf("exhaust_fab", "turbo_node", "piggyback", "standalone", "lean_burn")

    @Test
    fun stockMissesAndATurboWithTheEconomyPartsClearsIt() {
        val stock = Logic.sheet(state(emptyMap()))
        assertTrue("stock ${stock.profit.mpg} vs ${block.mpgGoal}", stock.profit.mpg < block.mpgGoal)

        val economy = (Logic.ECONOMY_IDS + "lean_spark").associateWith { Upgrades.byId(it).maxLevel }
        val plain = Logic.sheet(state(economy))
        val turbo = Logic.sheet(state(economy + ("turbo" to Upgrades.byId("turbo").maxLevel)))
        assertTrue("economy ${plain.profit.mpg} vs ${block.mpgGoal}", plain.profit.mpg >= block.mpgGoal)
        assertTrue(
            "turbo ${turbo.profit.mpg} @ ${(turbo.profit.throttle * 100).toInt()}% vs ${block.mpgGoal}",
            turbo.profit.mpg >= block.mpgGoal,
        )
        assertTrue(block.mpgGoal < block.mpgCap())
    }

    private fun state(upgrades: Map<String, Int>) = GameState(
        prestiges = block.ladderStep,
        blockId = block.name,
        researched = research,
        upgrades = upgrades,
        money = 1e12,
    )
}
