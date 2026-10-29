package com.revline.idle.ui

import com.revline.idle.game.Racing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each chassis on the strip is its own rough side view, not one shared blob. */
class WireframeTest {
    @Test
    fun everyChassisHasItsOwnWireframe() {
        val ids = listOf("mule") + Racing.chassis.map { it.id }
        val prints = ids.map { Wireframe.signature(it) }
        assertEquals(ids.toSet().size, prints.toSet().size)
        ids.forEach { id ->
            assertTrue(Wireframe.of(id).lines.size >= 4)
            assertEquals(2, Wireframe.of(id).axles.size)
        }
        assertTrue(Wireframe.length("etype") > Wireframe.length("p50") * 1.4f)
        assertTrue(Wireframe.height("cv2") > Wireframe.height("gt40") * 1.4f)
        assertTrue(Wireframe.height("sel63") > Wireframe.height("seven"))
        assertTrue(Wireframe.of("r5", mid = true).lines.size > Wireframe.of("r5").lines.size)
    }
}
