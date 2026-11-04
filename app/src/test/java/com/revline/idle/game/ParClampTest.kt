package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.economy.DriverBuilds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The field's par car stays within 10% of the player's car, measured on the mean planned lap. */
class ParClampTest {
    @Test fun scaleHoldsTheParSpeedWithinTenPercent() {
        assertEquals(1.0, Circuits.parScale(100.0, 100.0), 1e-9)
        assertEquals(1.0, Circuits.parScale(105.0, 100.0), 1e-9)
        assertEquals(1.0, Circuits.parScale(95.0, 100.0), 1e-9)
        // A far stronger car: the par car is brought up to 10% slower than it, so a quicker field.
        assertEquals(100.0 / 0.9, 200.0 * Circuits.parScale(200.0, 100.0), 1e-9)
        // A far weaker car: the par car comes down to 10% quicker than it, so an easier field.
        assertEquals(200.0 / 1.1, 100.0 * Circuits.parScale(100.0, 200.0), 1e-9)
    }

    private fun speedRatio(car: Circuits.Car, p: Int, c: Circuits.Circuit): Double {
        val mine = Circuits.plan(car, c, Drivers.you, Circuits.Orders(), Circuits.Weather.DRY, measure = true).mean
        return mine / Circuits.fieldPar(car, p, c).mean
    }

    @Test fun everyCarOnEveryCircuitRacesAFieldWithinTenPercent() {
        val weak = Circuits.carOf(GameState(prestiges = 0))
        val strong = Circuits.carOf(DriverBuilds.orientations(6).first().state)
        for (c in Circuits.all) for (car in listOf(weak, strong)) for (p in listOf(0, 3, 6)) {
            val r = speedRatio(car, p, c)
            assertTrue("${c.id} p$p $r", r in 0.9 - 1e-6..1.1 + 1e-6)
        }
    }

    @Test fun aWeakCarGetsAnEasierFieldAndAStrongOneAHarderField() {
        val weak = Circuits.carOf(GameState(prestiges = 0))
        val strong = Circuits.carOf(DriverBuilds.orientations(6).first().state)
        var easier = 0; var harder = 0
        for (c in Circuits.all) {
            val w = Circuits.fieldPar(weak, 6, c).mean - Circuits.parPlan(6, c).mean
            val s = Circuits.fieldPar(strong, 0, c).mean - Circuits.parPlan(0, c).mean
            assertTrue("${c.id} weak $w", w >= 0.0)
            assertTrue("${c.id} strong $s", s <= 0.0)
            if (w > 0.0) easier++
            if (s < 0.0) harder++
        }
        assertTrue("easier on $easier circuits", easier >= 4)
        assertTrue("harder on $harder circuits", harder >= 4)
    }

    @Test fun aCarAtParIsLeftAlone() {
        for (c in Circuits.all) assertEquals(Circuits.parPlan(4, c).mean, Circuits.fieldPar(Circuits.parCar(4), 4, c).mean, 1e-9)
    }
}
