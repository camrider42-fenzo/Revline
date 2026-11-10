package com.revline.idle.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Head, manifold, and exhaust colors follow the live temperatures and come back down with them. */
class HeatGlowTest {
    @Test fun aCoolBayStaysOnTheInstrumentColors() {
        assertEquals(0f, HeatGlow.intensity(80f, 95f, 450f, 0f), 0.001f)
        assertEquals(Lime, HeatGlow.head(80f, 95f, 0f))
        assertEquals(Amber, HeatGlow.exhaust(450f, 0f))
        assertEquals(0.55f, HeatGlow.gain(420f, 0f), 0.001f)
    }

    @Test fun heatShiftsTowardRedAndCoolingPullsItBack() {
        val warm = HeatGlow.intensity(110f, 140f, 800f, 16f)
        val hot = HeatGlow.intensity(125f, 160f, 960f, 30f)
        val cooled = HeatGlow.intensity(90f, 100f, 500f, 2f)
        assertTrue(hot > warm)
        assertTrue(warm > cooled)
        assertTrue(cooled < 0.05f)
        val head = HeatGlow.head(125f, 160f, 30f)
        assertTrue(head.red > Lime.red)
        assertTrue(head.green < Lime.green)
        val pipe = HeatGlow.exhaust(960f, 30f)
        val rested = HeatGlow.exhaust(450f, 0f)
        assertTrue(pipe.red >= rested.red)
        assertTrue(pipe.green < rested.green)
        assertTrue(HeatGlow.gain(960f, 30f) > HeatGlow.gain(450f, 0f))
    }

    @Test fun oilAloneLightsTheHeadAndExhaustAloneLightsThePipe() {
        val oily = HeatGlow.head(80f, 160f, 0f)
        val coolHead = HeatGlow.head(80f, 95f, 0f)
        assertTrue(oily.red > coolHead.red)
        val gas = HeatGlow.exhaust(940f, 0f)
        assertTrue(gas.red > Amber.red - 0.02f)
        assertEquals(Amber, HeatGlow.exhaust(450f, 0f))
    }
}
