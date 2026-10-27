package com.revline.idle.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFrameTest {
    @Test
    fun theCameraMovesOncePerRequestAndIgnoresAResize() {
        assertFalse(cameraShouldMove(appliedTick = -1, requestTick = 0, width = 0f, height = 400f))
        assertTrue(cameraShouldMove(appliedTick = -1, requestTick = 0, width = 800f, height = 400f))
        assertFalse(cameraShouldMove(appliedTick = 0, requestTick = 0, width = 800f, height = 280f))
        assertTrue(cameraShouldMove(appliedTick = 0, requestTick = 1, width = 800f, height = 280f))
        assertFalse(cameraShouldMove(appliedTick = 1, requestTick = 1, width = 800f, height = 220f))
    }
}
