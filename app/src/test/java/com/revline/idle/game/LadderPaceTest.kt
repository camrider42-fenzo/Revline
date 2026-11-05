package com.revline.idle.game

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Ladder opponent pace must rise smoothly — no Peel-class spike on rung 2. */
class LadderPaceTest {
    @Test fun ladderPaceIsMonotonicAndSmooth() {
        for (length in Racing.Length.entries) {
            val paces = Racing.ladderRungs(length).map { it.pace }
            assertTrue("$length size", paces.size >= 4)
            for (i in 1 until paces.size) {
                assertTrue(
                    "$length L${i + 1} ${paces[i]} <= L$i ${paces[i - 1]}",
                    paces[i] >= paces[i - 1] * Racing.LADDER_PACE_STEP * 0.999,
                )
            }
            // Second differences stay mild: no single step jumps more than 2.2× the median step.
            val steps = paces.zipWithNext { a, b -> b / a }
            val median = steps.sorted()[steps.size / 2]
            for ((i, s) in steps.withIndex()) {
                assertTrue(
                    "$length step L${i + 1}->L${i + 2} ratio $s vs median $median",
                    s <= median * 2.2 + 1e-9,
                )
            }
            // Rung 2 sits between 1 and 3 on the curve (the old Peel national bug put it ~3–4× above both).
            assertTrue(
                "$length L2 ${paces[1]} not between L1 ${paces[0]} and a smooth lift past L3 ${paces[2]}",
                paces[1] > paces[0] && paces[1] < paces[2] * 1.15,
            )
        }
    }

    @Test fun nationalLadderDoesNotUseToyLightTubs() {
        val rungs = Racing.ladderRungs()
        // Rung 2 is moped national — must not be the 59 kg Peel.
        assertTrue(rungs[1].title.contains("national"))
        assertTrue("rung2 chassis ${rungs[1].chassisId}", rungs[1].chassisId != "p50")
        val medium = Racing.groupChassis(0, Racing.Build.MEDIUM)
        assertTrue(rungs[1].chassisId == medium.id)
    }
}
