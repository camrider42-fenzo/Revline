package com.revline.idle.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineMotionTest {
    @Test
    fun theSpeakerFiresAtTheTach() {
        val sixThousand = 6000f * EngineMotion.SOUND_CRANK / 720f
        assertEquals(50f, sixThousand, 0.01f)
        assertEquals(200f, sixThousand * 4f, 0.01f)
        val drawn = 6000f * EngineMotion.VISUAL_CRANK / 720f
        assertTrue("the bench stays slow ($drawn Hz)", drawn < 3f)
    }

    @Test
    fun everyCylinderFiresOncePerTwoTurnsAtItsOwnAngle() {
        for (n in 1..12) {
            val offsets = EngineMotion.firingOffsets(n)
            assertEquals(n, offsets.size)
            val fires = offsets.map { EngineMotion.cycle(-it) }.sorted()
            assertEquals("cylinders $n share a firing angle", n, fires.distinct().size)
            fires.forEach { assertTrue(it in 0f..720f) }
        }
    }

    @Test
    fun inlineFourIsAFlatPlaneFiringOneThreeFourTwo() {
        val offsets = EngineMotion.firingOffsets(4)
        assertEquals(listOf(0f, 180f, 180f, 0f), offsets.map { it % 360f })
        val order = offsets.indices.sortedBy { EngineMotion.cycle(-offsets[it]) }.map { it + 1 }
        assertEquals(listOf(1, 3, 4, 2), order)
    }

    @Test
    fun pistonTravelIsRodPlusThrowAtTopAndRodMinusThrowAtBottom() {
        assertEquals(13f, EngineMotion.travel(0f, 3f, 10f), 1e-4f)
        assertEquals(7f, EngineMotion.travel(180f, 3f, 10f), 1e-4f)
        assertTrue(EngineMotion.travel(90f, 3f, 10f) < 10f)
    }

    @Test
    fun valvesOpenOnTheirOwnStrokes() {
        assertEquals(0f, EngineMotion.intakeLift(90f), 0f)
        assertEquals(0f, EngineMotion.exhaustLift(450f), 0f)
        assertTrue(EngineMotion.intakeLift(465f) > 0.98f)
        assertTrue(EngineMotion.exhaustLift(255f) > 0.98f)
        assertTrue("overlap at TDC", EngineMotion.intakeLift(360f) > 0f && EngineMotion.exhaustLift(360f) > 0f)
    }

    @Test
    fun theFlameLightsAtTdcAndTheSparkComesFirst() {
        assertEquals(1f, EngineMotion.burn(0f), 1e-6f)
        assertEquals(0f, EngineMotion.burn(200f), 0f)
        assertEquals(0f, EngineMotion.burn(360f), 0f)
        assertTrue(EngineMotion.spark(710f) > 0.5f)
        assertEquals(0f, EngineMotion.spark(360f), 0f)
        assertNull(EngineMotion.puff(60f))
        assertEquals(0.5f, EngineMotion.puff(330f)!!, 1e-6f)
        assertNull(EngineMotion.charge(100f))
    }
}
