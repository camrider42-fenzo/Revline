package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Rotary long-blocks sit on the piston step whose stock power is closest. */
class RotaryAlternativesTest {
    private val alts = listOf(
        Block.ROTARY_1R to 2,
        Block.ROTARY_RENESIS to 4,
        Block.ROTARY to 4,
        Block.ROTARY_20B to 5,
        Block.ROTARY_26B to 5,
    )

    private fun stockHp(block: Block): Double {
        val state = GameState(
            prestiges = block.ladderStep,
            blockId = if (block == Block.forPrestige(block.ladderStep)) "" else block.name,
        )
        return Logic.sheet(state.copy(throttle = Logic.bestThrottle(state))).stats.peakHorsepower
    }

    @Test fun eachRotarySitsOnItsLadderStep() {
        for ((block, step) in alts) {
            assertEquals(block.title, step, block.ladderStep)
            assertEquals(EngineFamily.ROTARY, block.family)
            val state = GameState(prestiges = step, blockId = block.name)
            val fitted = Block.of(state.blockId, state.prestiges)
            assertEquals(block, fitted)
            val sheet = Logic.sheet(state.copy(throttle = Logic.bestThrottle(state)))
            assertEquals(block.cylinders, sheet.params.cylinders)
            assertEquals(EngineFamily.ROTARY, sheet.params.family)
            assertTrue(block.title, sheet.stats.peakHorsepower > 5.0)
        }
    }

    @Test fun rotaryPowerMatchesStepBandAndOrderIsMonotonic() {
        val pistonHp = (0..Block.maxLadderStep).associateWith { step ->
            val b = Block.forPrestige(step)
            assertEquals(EngineFamily.PISTON, b.family)
            stockHp(b)
        }
        val rotaries = Block.entries.filter { it.family == EngineFamily.ROTARY }
            .sortedBy { stockHp(it) }
        var prevHp = 0.0
        var prevStep = -1
        for (block in rotaries) {
            val hp = stockHp(block)
            assertTrue("${block.title} hp $hp <= prev $prevHp", hp >= prevHp)
            assertTrue("${block.title} step ${block.ladderStep} < prev $prevStep", block.ladderStep >= prevStep)
            val closest = pistonHp.minBy { (_, php) -> abs(php - hp) }.key
            assertEquals("${block.title} hp $hp closest piston step", closest, block.ladderStep)
            // Within band: closer to own step than to any other primary.
            val own = pistonHp.getValue(block.ladderStep)
            for ((step, php) in pistonHp) {
                if (step == block.ladderStep) continue
                assertTrue(
                    "${block.title} hp $hp closer to step $step ($php) than own $own",
                    abs(hp - own) <= abs(hp - php) + 1e-6,
                )
            }
            prevHp = hp
            prevStep = block.ladderStep
        }
    }

    @Test fun primaryLadderPowerIsMonotonicAcrossPistonSteps() {
        var previous = 0.0
        for (step in 0..Block.maxLadderStep) {
            val block = Block.forPrestige(step)
            assertEquals(EngineFamily.PISTON, block.family)
            val state = GameState(prestiges = step)
            val hp = Logic.sheet(state.copy(throttle = Logic.bestThrottle(state))).stats.peakHorsepower
            assertTrue("${block.title} hp $hp <= $previous", hp > previous * 1.15)
            previous = hp
        }
    }

    @Test fun animRotorCountFollowsBlockCylindersFromOneToFour() {
        assertEquals(1, Block.ROTARY_1R.cylinders)
        assertEquals(2, Block.ROTARY_RENESIS.cylinders)
        assertEquals(3, Block.ROTARY_20B.cylinders)
        assertEquals(4, Block.ROTARY_26B.cylinders)
        assertEquals(2, Block.ROTARY.cylinders)
        for (n in 1..4) {
            assertEquals(n, n.coerceIn(1, 4))
        }
    }

    @Test fun fitBlockSwitchesAltAndClearsOnWrongStep() {
        val atFour = GameState(prestiges = 4, blockId = Block.ROTARY_RENESIS.name)
        assertEquals(Block.ROTARY_RENESIS, Block.of(atFour.blockId, atFour.prestiges))
        val wrong = GameState(prestiges = 4, blockId = Block.ROTARY_20B.name)
        assertEquals(Block.INLINE_FOUR, Block.of(wrong.blockId, wrong.prestiges))
        val fitted = Logic.fitBlock(GameState(prestiges = 2), Block.ROTARY_1R.name)
        assertEquals(Block.ROTARY_1R.name, fitted.blockId)
        val back = Logic.fitBlock(fitted, Block.TWIN.name)
        assertEquals("", back.blockId)
    }
}
