package com.revline.idle.game

import com.revline.idle.data.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Light is the track car. Heavy is the dyno car. The engine only sits where that car sat it. */
class ChassisClassTest {
    @Test
    fun eachPowerGroupHasALightAMediumAndAHeavy() {
        for (group in 0..7) {
            val cars = Racing.chassis.filter { it.group == group }
            val light = cars.single { it.build == Racing.Build.LIGHT }
            val medium = cars.single { it.build == Racing.Build.MEDIUM }
            val heavy = cars.single { it.build == Racing.Build.HEAVY }
            assertTrue("$group mass", light.kg < medium.kg && medium.kg < heavy.kg)
            assertTrue("$group ducts", light.slots <= medium.slots && medium.slots <= heavy.slots)
            assertTrue("$group bay", light.maxCc < medium.maxCc && medium.maxCc < heavy.maxCc)
            assertTrue("$group dyno factor", light.dyno < medium.dyno && medium.dyno < heavy.dyno)
            assertEquals("$group places", 1, light.places.size)
            val cc = light.maxCc * 0.8
            val hp = 20.0 + group * 45.0
            val left = rate(light, cc, hp)
            val mid = rate(medium, cc, hp)
            val right = rate(heavy, cc, hp)
            assertTrue("$group dyno ${left.wheelHp.toInt()} ${mid.wheelHp.toInt()} ${right.wheelHp.toInt()}", right.wheelHp > mid.wheelHp && mid.wheelHp > left.wheelHp)
            assertTrue("$group pace ${left.pace.toInt()} ${mid.pace.toInt()} ${right.pace.toInt()}", left.pace > mid.pace && mid.pace > right.pace)
            assertTrue(left.fits && mid.fits && right.fits)
            val bigger = (light.maxCc + heavy.maxCc) / 2.0
            assertFalse(Racing.fits(light, bigger))
            assertTrue(Racing.fits(heavy, bigger))
            val choked = rate(light, bigger, hp)
            assertFalse(choked.fits)
            assertTrue("$group choked ${choked.wheelHp} vs ${left.wheelHp}", choked.wheelHp < left.wheelHp * 0.6)
        }
    }

    @Test
    fun theEngineOnlySitsWhereThatCarSatIt() {
        val seven = GameState(chassis = "seven", enginePlace = "MID")
        assertEquals(Racing.Place.FRONT, Racing.car(seven).spec.place)
        assertEquals("MID", Racing.fitPart(seven, "place", "REAR").enginePlace)
        val road = GameState(chassis = "r5")
        val mid = Racing.fitPart(road, "place", "MID")
        assertEquals("MID", mid.enginePlace)
        assertEquals(Racing.Place.MID, Racing.car(mid).spec.place)
        assertEquals("", Racing.fitPart(road, "place", "REAR").enginePlace)
        assertTrue(Racing.chassis.single { it.id == "r5" }.places.size >= 2)
    }

    @Test
    fun aLongerRaceTakesLongerAndPaysForTheLength() {
        val eighth = sample(Racing.Length.EIGHTH.meters)
        val quarter = sample(Racing.Length.QUARTER.meters)
        val half = sample(Racing.Length.HALF.meters)
        val short = RaceDrive.driveWell(eighth)
        val mid = RaceDrive.driveWell(quarter)
        val long = RaceDrive.driveWell(half)
        assertTrue("${short.time} ${mid.time} ${long.time}", short.time < mid.time && mid.time < long.time)
        assertTrue(short.distance >= eighth.distanceM - 1.0)
        assertTrue(long.distance >= half.distanceM - 1.0)
        assertTrue("half ${long.speed} quarter ${mid.speed}", long.speed >= mid.speed * 0.98)
        val base = GameState(money = 10_000.0)
        val quarterPurse = Racing.fieldForQuick(base, Racing.Tier.CLUB).purse
        val halfPurse = Racing.fieldForQuick(Racing.setLength(base, "half"), Racing.Tier.CLUB).purse
        assertTrue("$halfPurse vs $quarterPurse", halfPurse > quarterPurse * 1.4)
    }

    private fun rate(body: Racing.Chassis, cc: Double, hp: Double): Racing.Car {
        val box = Racing.gearboxes.first { it.id == "four" }
        val axle = Racing.axles.first()
        val tire = Racing.tires.first()
        return Racing.assess(
            Racing.Spec(hp, hp * 5252.0 / 3600.0, cc, body, 0, box, axle, tire, body.slots, body.drive, body.place),
        )
    }

    private fun sample(meters: Double) = RaceDrive.Setup(
        peakHp = 180.0,
        peakTqLbFt = 190.0,
        redline = 7000.0,
        idle = 900.0,
        massKg = 980.0,
        ratios = listOf(2.52, 1.74, 1.27, 1.00),
        finalDrive = 3.73,
        efficiency = 0.88,
        gripN = 980.0 * 9.81 * 0.55,
        drag = 0.72,
        shiftSeconds = 0.36,
        distanceM = meters,
    )
}
