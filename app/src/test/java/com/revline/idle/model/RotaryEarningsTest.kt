package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.Racing
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 1.93.3: the single-rotor's pay scale matches the thumper it shares a step with,
 * and the 26B's sheet goals match the power it actually makes so club Quick is winnable.
 */
class RotaryEarningsTest {
    @Test
    fun singleRotorStockBestPedalPaySitsWithTheThumper() {
        val rotor = bestPedalPay(Block.ROTARY_1R)
        val thumper = bestPedalPay(Block.THUMPER)
        assertTrue("single-rotor \$${"%.2f".format(rotor)}/s", rotor in 1.45..1.85)
        assertTrue(
            "single-rotor \$${"%.2f".format(rotor)}/s vs thumper \$${"%.2f".format(thumper)}/s",
            abs(rotor - thumper) / thumper < 0.15,
        )
    }

    @Test
    fun stock26BWinsClubQuick() {
        val state = arrived(Block.ROTARY_26B)
        val body = Racing.quickest(state)
        assertTrue("26B fits a chassis", body != null)
        val fitted = state.copy(chassis = body!!.id)
        val you = Racing.car(fitted)
        val club = Racing.fieldForQuick(fitted, Racing.Tier.CLUB)
        assertTrue(you.fits)
        assertTrue(
            "26B pace ${you.pace.toInt()} vs club ${club.pace.toInt()} (goals ${Block.ROTARY_26B.hpGoal.toInt()}/${Block.ROTARY_26B.torqueGoal.toInt()})",
            you.pace > club.pace,
        )
        assertTrue(Block.ROTARY_26B.hpGoal in 440.0..480.0)
        assertTrue(Block.ROTARY_26B.torqueGoal in 280.0..320.0)
    }

    /** Fresh long-block on its ladder step, with the knowledge a rebuild to that step already has. */
    private fun arrived(block: Block): GameState =
        GameState(
            prestiges = block.ladderStep,
            blockId = block.name,
            knowledge = block.ladderStep,
            money = 1_000_000.0,
        )

    private fun bestPedalPay(block: Block): Double {
        val state = arrived(block)
        return Logic.sheet(state.copy(throttle = Logic.bestThrottle(state))).incomePerSec
    }
}
