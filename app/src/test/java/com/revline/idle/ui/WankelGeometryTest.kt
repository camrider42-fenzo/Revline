package com.revline.idle.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WankelGeometryTest {
    @Test
    fun apexSealsTouchTheEpitrochoidAtAllShaftAngles() {
        val e = 10.0
        val R = e * WankelGeometry.DEFAULT_R_OVER_E
        val gap = WankelGeometry.maxApexHousingGap(R, e, steps = 360)
        assertTrue("apex/housing gap $gap", gap < 1e-9)
    }

    @Test
    fun housingIsTwoLobePeanutWithMazdaClassRatio() {
        val e = 1.0
        val R = WankelGeometry.DEFAULT_R_OVER_E
        val pts = WankelGeometry.sampleHousing(R, e, 360)
        val xs = pts.map { it.x }
        val ys = pts.map { it.y }
        val spanX = xs.max() - xs.min()
        val spanY = ys.max() - ys.min()
        // Two-lobe epitrochoid: major/minor span ratio clearly elongated, not a circle.
        assertTrue("spanX $spanX spanY $spanY", abs(spanX - spanY) / maxOf(spanX, spanY) > 0.08)
        assertEquals(7.0, R / e, 1e-9)
    }

    @Test
    fun rotorTurnsAtOneThirdShaftSpeed() {
        assertEquals(30f, EngineMotion.rotorAngle(90f, 0, 1), 1e-4f)
        assertEquals(60f, EngineMotion.rotorAngle(180f, 0, 1), 1e-4f)
    }
}
