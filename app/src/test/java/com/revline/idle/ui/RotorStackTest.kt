package com.revline.idle.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Extra rotors step up and to the left. One rotor stays where the bay already drew it. */
class RotorStackTest {
    @Test fun oneRotorStaysOnTheOldCenter() {
        val only = RotorStack.centers(1, 1000f, 480f)
        assertEquals(1, only.size)
        assertEquals(500f, only[0].x, 0.01f)
        assertEquals(480f * 0.54f, only[0].y, 0.01f)
        assertEquals(1f, RotorStack.gain(0, 1), 0f)
    }

    @Test fun twoThreeAndFourStepUpAndBack() {
        for (count in listOf(2, 3, 4)) {
            val centers = RotorStack.centers(count, 1000f, 480f)
            assertEquals(count, centers.size)
            val pace = RotorStack.step(480f)
            assertTrue("step is up-left $pace", pace.x < 0f && pace.y < 0f)
            for (i in 1 until count) {
                val moved = centers[i] - centers[i - 1]
                assertEquals("x step $count/$i", pace.x, moved.x, 0.05f)
                assertEquals("y step $count/$i", pace.y, moved.y, 0.05f)
                assertTrue("behind is left and up", centers[i].x < centers[i - 1].x && centers[i].y < centers[i - 1].y)
            }
            val span = hypot((centers.last().x - centers.first().x).toDouble(), (centers.last().y - centers.first().y).toDouble())
            assertTrue("the stack opens up ($count -> $span)", span > 40.0 * (count - 1))
        }
    }

    @Test fun theNearHousingIsBrightest() {
        for (count in listOf(2, 3, 4)) {
            assertEquals(1f, RotorStack.gain(0, count), 0.001f)
            var previous = 2f
            for (i in 0 until count) {
                val gain = RotorStack.gain(i, count)
                assertTrue("$count/$i fades ($gain after $previous)", gain < previous)
                previous = gain
            }
            assertTrue(RotorStack.gain(count - 1, count) > 0.4f)
        }
        assertEquals(RotorStack.gain(0, 2), RotorStack.gain(0, 4), 0f)
        assertTrue(abs(RotorStack.gain(1, 2) - (1f - 0.52f)) < 0.001f)
    }
}
