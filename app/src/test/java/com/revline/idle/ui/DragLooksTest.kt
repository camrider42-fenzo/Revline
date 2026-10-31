package com.revline.idle.ui

import com.revline.idle.game.Racing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drag screen draws every chassis with its own silhouette, and the anchors it lays out by stay sane. */
class DragLooksTest {
    private val ids = listOf("mule") + Racing.chassis.map { it.id }

    @Test
    fun everyChassisHasItsOwnDragLook() {
        val looks = ids.map { DragLooks.of(it) }
        looks.forEach { look ->
            assertTrue(look.body.size >= 4 * 3)
            assertEquals(0, look.body.size % 3)
            assertTrue(look.glass.isNotEmpty())
        }
        assertEquals(ids.size, looks.map { it.body.toList() }.toSet().size)
        assertTrue(DragLooks.of("r5", mid = true) !== DragLooks.of("r5"))
    }

    @Test
    fun anchorsPutTheFrontAxleBetweenTailAndNose() {
        ids.forEach { id ->
            val a = dragAnchor(id, false)
            assertTrue("$id tail", a.tailBehindFront > 0.4f)
            assertTrue("$id nose", a.noseAheadOfFront in 0.1f..0.6f)
            // The rear axle must sit behind the front one, so the rubber lands behind the car.
            assertTrue("$id rear", dragRearFromMid(id, false) < a.frontFromMid)
        }
    }
}
