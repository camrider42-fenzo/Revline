package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.Block
import com.revline.idle.model.EngineFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EngineWeightTest {
    @Test
    fun familyTierWeightsMatchTheBrief() {
        assertEquals("light", EngineWeight.familyTierLabel(Block.ROTARY))
        assertEquals("mid", EngineWeight.familyTierLabel(Block.INLINE_FOUR))
        assertEquals("mid", EngineWeight.familyTierLabel(Block.V6))
        assertEquals("heavy", EngineWeight.familyTierLabel(Block.V8))
        assertEquals("heavy", EngineWeight.familyTierLabel(Block.V12))
        assertTrue(Block.ROTARY.baseEngineKg < Block.INLINE_FOUR.baseEngineKg)
        assertTrue(Block.INLINE_FOUR.baseEngineKg < Block.V8.baseEngineKg)
        assertTrue(Block.V8.baseEngineKg < Block.V12.baseEngineKg)
    }

    @Test
    fun pistonStockBasesStayNearLegacySoSavesKeepMass() {
        val deltas = Block.entries.filter { it.family == EngineFamily.PISTON }
            .associateWith { EngineWeight.stockDeltaVsLegacy(it) }
        println(deltas.entries.joinToString("\n") { "${it.key.title}: ${"%.2f".format(it.value)} kg vs legacy" })
        for ((block, d) in deltas) {
            assertTrue("${block.title} delta $d", abs(d) < 0.6)
        }
        val rotaryDelta = EngineWeight.stockDeltaVsLegacy(Block.ROTARY)
        println("ROTARY: ${"%.2f".format(rotaryDelta)} kg vs legacy (intentional light)")
        assertTrue(rotaryDelta < -20.0)
    }

    @Test
    fun partsAdjustEngineWeight() {
        val stock = GameState(prestiges = Block.INLINE_FOUR.ordinal)
        val turbo = stock.copy(upgrades = mapOf("turbo" to 1, "intercooler" to 1))
        val light = GameState(prestiges = Block.ROTARY.ladderStep, blockId = Block.ROTARY.name, upgrades = mapOf("light_rotor" to 2))
        assertTrue(EngineWeight.kg(turbo) > EngineWeight.kg(stock) + 0.4)
        assertTrue(EngineWeight.partDeltaKg(light) < -4.0)
        assertTrue(
            EngineWeight.kg(GameState(prestiges = Block.ROTARY.ladderStep, blockId = Block.ROTARY.name, upgrades = mapOf("rotor4" to 1, "rotor3" to 1))) >
                EngineWeight.kg(GameState(prestiges = Block.ROTARY.ladderStep, blockId = Block.ROTARY.name)) + 14.0,
        )
    }

    @Test
    fun engineWeightFeedsCarMassAndFrontBias() {
        val light = GameState(
            prestiges = Block.ROTARY.ladderStep,
            blockId = Block.ROTARY.name,
            chassis = "jensen",
            tire = "slick",
            transmission = "close",
            driveline = "lsd",
        )
        val heavy = GameState(
            prestiges = Block.V12.ordinal,
            chassis = "jensen",
            tire = "slick",
            transmission = "close",
            driveline = "lsd",
        )
        val lightCar = Racing.car(light)
        val heavyCar = Racing.car(heavy)
        assertTrue(heavyCar.massKg > lightCar.massKg + 150.0)
        assertTrue(heavyCar.engineKg > lightCar.engineKg + 150.0)
        val lightShare = DragTune.rearShare(lightCar)
        val heavyShare = DragTune.rearShare(heavyCar)
        assertTrue("light rear $lightShare heavy rear $heavyShare", heavyShare < lightShare)
    }

    @Test
    fun lighterEngineShortensEtAndLapTime() {
        val baseState = GameState(
            prestiges = Block.ROTARY.ladderStep,
            blockId = Block.ROTARY.name,
            chassis = "rx7",
            chassisLevel = mapOf("rx7" to 3),
            coolingSlots = 3,
            transmission = "close",
            driveline = "lsd",
            tire = "slick",
            circuitTire = "racing",
            racing = true,
        )
        val base = Racing.specOf(baseState)
        val lightCar = Racing.assess(base.copy(engineKg = 55.0))
        val heavyCar = Racing.assess(base.copy(engineKg = 280.0))
        assertTrue(lightCar.pace > heavyCar.pace)
        assertTrue(lightCar.massKg < heavyCar.massKg)

        val lightSetup = RaceDrive.setup(baseState).copy(massKg = lightCar.massKg)
        val heavySetup = RaceDrive.setup(baseState).copy(massKg = heavyCar.massKg)
        val lightEt = RaceDrive.clean(lightSetup).time
        val heavyEt = RaceDrive.clean(heavySetup).time
        assertTrue("light ET $lightEt heavy ET $heavyEt", lightEt < heavyEt)

        val lightCircuit = Circuits.carOf(baseState).let { it.copy(setup = it.setup.copy(massKg = lightCar.massKg)) }
        val heavyCircuit = Circuits.carOf(baseState).let { it.copy(setup = it.setup.copy(massKg = heavyCar.massKg)) }
        val circuit = Circuits.byId("suzuka") ?: Circuits.all.first()
        val hands = Circuits.hands(Drivers.you, lightCircuit.setup)
        val lightLap = Circuits.lap(lightCircuit, circuit, hands).time
        val heavyLap = Circuits.lap(heavyCircuit, circuit, hands).time
        println("ET $lightEt/$heavyEt lap $lightLap/$heavyLap mass ${lightCar.massKg}/${heavyCar.massKg}")
        assertTrue("light lap $lightLap heavy lap $heavyLap", lightLap < heavyLap)
    }
}
