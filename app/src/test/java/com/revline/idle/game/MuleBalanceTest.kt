package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shop mule is a lighter stopgap, and every car sticker is 35% off, nearest $10. */
class MuleBalanceTest {
    /** Stickers before the 1.94.0 cut. The mule was free and still is. */
    private val was = mapOf(
        "p50" to 140.0, "isetta" to 380.0, "cv2" to 720.0,
        "kr200" to 460.0, "fiat500" to 820.0, "saab96" to 1500.0,
        "sprite" to 980.0, "mini" to 900.0, "amazon" to 2100.0,
        "elan" to 1800.0, "gta" to 2400.0, "b2002" to 3200.0,
        "seven" to 480.0, "r5" to 2600.0, "etype" to 4200.0,
        "stratos" to 6400.0, "p911" to 8200.0, "vette" to 9800.0,
        "gt40" to 15000.0, "cobra" to 6400.0, "sel63" to 18000.0,
        "p917" to 24000.0, "daytona" to 20000.0, "jensen" to 22000.0,
        "rx7" to 24000.0, "cosmo" to 22000.0, "luxrotary" to 26000.0,
    )

    @Test fun theMuleIsTwentyKilosLighterAndStillNotTheBestCar() {
        assertEquals(620.0, Racing.mule.kg, 0.0)
        val four = stock(Block.INLINE_FOUR)
        val eight = stock(Block.V8)
        val muleFour = pass(four, "mule")
        val seven = pass(four, "seven")
        val muleEight = pass(eight, "mule")
        val gt40 = pass(eight, "gt40")
        assertEquals(846.9, muleFour.mass, 0.2)
        assertEquals(16.12, muleFour.et, 0.03)
        assertEquals(173.76, muleFour.monza, 0.08)
        assertEquals(973.0, muleEight.mass, 0.2)
        assertEquals(12.58, muleEight.et, 0.03)
        assertEquals(156.93, muleEight.monza, 0.08)
        assertTrue("seven still owns the inline-four quarter ${seven.et} vs ${muleFour.et}", seven.et < muleFour.et - 1.0)
        assertTrue("seven still owns Monza ${seven.monza} vs ${muleFour.monza}", seven.monza < muleFour.monza - 5.0)
        assertTrue("GT40 still matches the V8 quarter ${gt40.et} vs ${muleEight.et}", muleEight.et >= gt40.et)
        assertTrue("GT40 still owns the V8 Monza ${gt40.monza} vs ${muleEight.monza}", gt40.monza < muleEight.monza)
    }

    @Test fun everyCarStickerIsThirtyFivePercentOffToTheNearestTen() {
        assertEquals(0.0, Racing.mule.price, 0.0)
        assertEquals(was.keys, Racing.chassis.map { it.id }.toSet())
        Racing.chassis.forEach { car ->
            val old = was.getValue(car.id)
            val nearestTen = (old * 0.65 / 10.0).let { kotlin.math.round(it) } * 10.0
            assertEquals(car.name, nearestTen, car.price, 0.0)
            val cut = 1.0 - car.price / old
            assertTrue("$car.name cut $cut", cut in 0.34..0.36)
        }
    }

    @Test fun refittingACarYouAlreadyOwnDoesNotChargeOrRefund() {
        val owned = stock(Block.INLINE_FOUR).copy(
            money = 40.0,
            raceStudies = setOf("chassis_seven"),
            chassisLevel = mapOf("seven" to 1),
        )
        val back = Racing.fitChassis(owned, "seven")
        assertEquals("seven", back.chassis)
        assertEquals(40.0, back.money, 0.0)
        assertEquals(1, back.chassisLevel["seven"])
    }

    private data class Pass(val mass: Double, val et: Double, val monza: Double)

    private fun stock(block: Block) = GameState(prestiges = block.ordinal, blockId = block.name)

    private fun pass(base: GameState, id: String): Pass {
        val state = base.copy(chassis = id)
        val car = Racing.car(state)
        val et = RaceDrive.clean(RaceDrive.setup(state)).time
        val lap = Circuits.plan(Circuits.carOf(state), Circuits.all.first { it.id == "monza" }, Drivers.you).mean
        return Pass(car.massKg, et, lap)
    }
}
