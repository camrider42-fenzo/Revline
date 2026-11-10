package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SAVE_VERSION
import com.revline.idle.data.SaveCodec
import com.revline.idle.model.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A bench engine that will not fit the picked car races the shop mule in every mode. The pick is kept. */
class EngineFitTest {
    @Test fun aCarThatDoesNotFitRacesTheMuleInEveryMode() {
        val picked = v8().copy(chassis = "p50")
        assertFalse(Racing.fits(Racing.picked(picked), displacement(picked)))
        assertEquals("mule", Racing.raced(picked).id)
        assertEquals("Shop mule (engine doesn't fit Peel P50)", Racing.raceLabel(picked))
        val muleStrip = Racing.car(v8())
        val muleLap = Racing.car(v8(), circuit = true)
        // Quick, ladder, and stake all line up RaceDrive.setup. A circuit lines up Circuits.carOf. Both are the mule.
        val strip = RaceDrive.setup(picked)
        val circuit = Circuits.carOf(picked)
        assertEquals("mule", Racing.car(picked).spec.chassis.id)
        assertEquals("mule", muleLap.spec.chassis.id)
        assertEquals(muleStrip.massKg, strip.massKg, 0.05)
        assertEquals(muleLap.massKg, circuit.setup.massKg, 0.05)
        assertTrue(Racing.car(picked).fits)
        assertTrue(Racing.forecast(picked, Racing.chassisById("p50")).let { !it.fits && it.spec.chassis.id == "p50" })
    }

    @Test fun aCarThatFitsStaysTheCarYouPicked() {
        val gt = v8().copy(chassis = "gt40")
        assertEquals("gt40", Racing.raced(gt).id)
        assertEquals("Ford GT40", Racing.raceLabel(gt))
        assertEquals("gt40", Racing.car(gt).spec.chassis.id)
        assertEquals("gt40", Racing.car(gt, circuit = true).spec.chassis.id)
        assertEquals(Racing.car(gt).massKg, RaceDrive.setup(gt).massKg, 0.05)
        assertEquals(Racing.car(gt, circuit = true).massKg, Circuits.carOf(gt).setup.massKg, 0.05)
        val back = v8().copy(chassis = "p50").copy(prestiges = Block.MOPED.ordinal, blockId = Block.MOPED.name)
        assertEquals("p50", back.chassis)
        assertEquals("p50", Racing.raced(back).id)
        assertEquals("Peel P50", Racing.raceLabel(back))
        assertEquals("p50", Racing.car(back).spec.chassis.id)
        assertEquals("p50", Racing.car(back, circuit = true).spec.chassis.id)
    }

    @Test fun theShopMuleTakesEveryEngine() {
        assertTrue(Racing.fits(Racing.mule, 1_000_000.0))
        val huge = v8().copy(chassis = "mule")
        assertEquals("mule", Racing.raced(huge).id)
        assertTrue(Racing.car(huge).fits)
        assertEquals("Shop mule", Racing.raceLabel(huge))
    }

    @Test fun anOldSaveKeepsACarTheEngineWillNotFit() {
        val raw = """{"version":3,"prestiges":6,"blockId":"V8","chassis":"p50","money":12.5,"chassisLevel":{"p50":1}}"""
        val back = SaveCodec.decode(raw)
        assertEquals(SAVE_VERSION, back.version)
        assertEquals("p50", back.chassis)
        assertEquals(1, back.chassisLevel["p50"])
        assertEquals(12.5, back.money, 0.0)
        assertEquals("mule", Racing.raced(back).id)
        assertEquals("Shop mule (engine doesn't fit Peel P50)", Racing.raceLabel(back))
        val again = SaveCodec.decode(SaveCodec.encode(back))
        assertEquals("p50", again.chassis)
        assertEquals(12.5, again.money, 0.0)
    }

    private fun v8() = GameState(prestiges = Block.V8.ordinal, blockId = Block.V8.name)

    private fun displacement(state: GameState) =
        com.revline.idle.model.EngineSim.displacementCc(com.revline.idle.game.Logic.sheet(state).params)
}
