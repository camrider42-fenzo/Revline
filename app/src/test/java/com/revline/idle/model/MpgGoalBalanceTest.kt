package com.revline.idle.model

import com.revline.idle.game.Logic
import com.revline.idle.game.MpgGoals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Every mpg goal is the horsepower/torque fraction of one mid power build,
 * and that build still clears it.
 */
class MpgGoalBalanceTest {
    @Test
    fun everyGoalMatchesTheOtherGoalsAndTheReferenceBuildClearsIt() {
        for (block in Block.entries) {
            val parts = MpgGoals.reference(block)
            assertTrue("${block.title} keeps a turbo", (parts["turbo"] ?: 0) > 0)
            assertTrue("${block.title} keeps an intercooler", (parts["intercooler"] ?: 0) > 0)
            assertTrue("${block.title} keeps headers", (parts["headers"] ?: 0) > 0)
            assertTrue("${block.title} keeps an intake", (parts["filter"] ?: 0) > 0)
            val sheet = Logic.sheet(MpgGoals.state(block))
            val hp = MpgGoals.difficulty(block.hpGoal, sheet.stats.peakHorsepower)
            val tq = MpgGoals.difficulty(block.torqueGoal, sheet.stats.peakTorqueLbFt)
            val med = (hp + tq) / 2.0
            val ceiling = if (block == Block.ROTARY_1R) {
                minOf(sheet.profit.mpg, MpgGoals.SINGLE_ROTOR_CEILING)
            } else {
                sheet.profit.mpg
            }
            val matched = minOf(med * sheet.profit.mpg, ceiling)
            assertTrue(
                "${block.title} ${sheet.profit.mpg} vs ${block.mpgGoal}",
                sheet.profit.mpg + 1e-6 >= block.mpgGoal,
            )
            assertTrue(
                "${block.title} goal ${block.mpgGoal} vs matched $matched",
                abs(block.mpgGoal - kotlin.math.floor(matched * 10.0 + 1e-9) / 10.0) < 1e-9,
            )
            val mpg = MpgGoals.difficulty(block.mpgGoal, sheet.profit.mpg)
            val lo = minOf(hp, tq)
            val hi = maxOf(hp, tq)
            val capped = med * sheet.profit.mpg > ceiling + 1e-6
            val inBand = mpg in (lo - 0.05)..(hi + 0.05)
            assertTrue(
                "${block.title} mpg difficulty $mpg vs hp $hp torque $tq",
                inBand || (capped && mpg <= hi + 0.05),
            )
            if (block == Block.ROTARY_1R) {
                assertTrue("single rotor stays at or under 57", block.mpgGoal <= MpgGoals.SINGLE_ROTOR_CEILING)
            }
            assertTrue("${block.title} under the cap", block.mpgGoal < block.mpgCap())
        }
    }
}
