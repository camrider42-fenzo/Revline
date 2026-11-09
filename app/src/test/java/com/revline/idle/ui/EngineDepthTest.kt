package com.revline.idle.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The bay's depth stays a lip on the part: up and to the right, and never a jump. */
class EngineDepthTest {
    @Test fun theLipRisesToTheRightAndStaysOnThePart() {
        val lip = EngineDepth.extrusion(220f)
        assertTrue("runs right (${lip.x})", lip.x in 16f..22f)
        assertTrue("rises (${lip.y})", lip.y in -26f..-20f)
        val small = EngineDepth.extrusion(40f)
        assertTrue(small.x > 0f && small.y < 0f)
        assertTrue("a small part keeps a small lip (${small.x}, ${small.y})", small.x < 8f && -small.y < 10f)
        // Unfolded fold: the 280dp bay is about 735px tall and the crankcase
        // span is ~0.46 of that, past the point where 8% of the part exceeds
        // the 26px cap. That used to throw and close the app.
        for (span in listOf(338f, 400f, 735f, 1600f)) {
            val wide = EngineDepth.extrusion(span)
            assertTrue("wide lip $span runs right (${wide.x})", wide.x in 0f..22f)
            assertTrue("wide lip $span rises (${wide.y})", wide.y in -26f..-1f)
        }
    }

    @Test fun theTopFaceIsTheBrightPlane() {
        assertTrue(EngineDepth.faceAlpha(EngineDepth.Face.TOP) > EngineDepth.faceAlpha(EngineDepth.Face.SIDE))
        assertTrue(EngineDepth.faceAlpha(EngineDepth.Face.SIDE) > EngineDepth.faceAlpha(EngineDepth.Face.FRONT))
    }

    @Test fun theTopFaceSitsAboveTheFrontEdge() {
        val box = EngineDepth.slab(0f, 40f, 120f, 90f, EngineDepth.extrusion(80f))
        assertEquals(4, box.top.size)
        assertEquals(4, box.side.size)
        val frontTopRight = box.front[1]
        val backTopRight = box.top[2]
        assertTrue(backTopRight.x > frontTopRight.x)
        assertTrue(backTopRight.y < frontTopRight.y)
        assertTrue(box.side[1].x > box.front[1].x)
    }

    @Test fun parallaxStaysInsideAStroke() {
        var peak = 0f
        for (step in 0..40) {
            val drift = EngineDepth.parallax(3.4f, step / 5f)
            peak = maxOf(peak, abs(drift.x), abs(drift.y))
            assertTrue("drift $drift", abs(drift.x) <= 3.4f && abs(drift.y) <= 3.4f)
        }
        assertTrue("the hardware does move ($peak)", peak > 1f)
    }
}
