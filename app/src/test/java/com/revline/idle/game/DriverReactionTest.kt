package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.economy.DriverBuilds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reaction stat: the delay from the green light to the launch, on the strip and on the grid. */
class DriverReactionTest {
    private val setup = DriverBuilds.forBlock(3).first().setup

    /** Seconds from green until the car has rolled a metre, for one run. */
    private fun off(reaction: Double, seed: Long): Double {
        val race = AutoRace(setup, Skills(0.6, 0.6, 0.6, 0.6, reaction), seed)
        var guard = 0
        while (!race.over && guard++ < 40_000) {
            race.advance(1.0 / 120.0)
            if (!race.staging && race.run.distance >= 1.0) return race.run.time
        }
        return Double.NaN
    }

    @Test fun aQuickerReactionIsOffTheLightsSoonerAndEveryRunIsADifferentDelay() {
        val seeds = 1L..24L
        val slow = seeds.map { off(0.1, it) }
        val quick = seeds.map { off(0.95, it) }
        assertTrue("$slow $quick", (slow + quick).none { it.isNaN() })
        assertTrue("slow ${slow.average()} quick ${quick.average()}", slow.average() > quick.average() + 0.12)
        assertTrue(slow.toSet().size > 12 && quick.toSet().size > 12)
        // Same seed, same delay.
        assertEquals(off(0.4, 5L), off(0.4, 5L), 0.0)
    }

    @Test fun theStandingStartDelayIsZeroForAnAverageDriverAndGrowsWithSlowness() {
        assertEquals(0.0, Circuits.startDelay(0.5, 0.0), 1e-12)
        assertTrue(Circuits.startDelay(0.2, 0.0) > Circuits.startDelay(0.5, 0.0))
        assertTrue(Circuits.startDelay(0.9, 0.0) < 0.0)
        assertTrue(Circuits.startDelay(0.1, 1.0) > Circuits.startDelay(0.1, -1.0))
        // Noise matters less to a sharp driver.
        assertTrue(Circuits.startDelay(0.9, 1.0) - Circuits.startDelay(0.9, -1.0) < Circuits.startDelay(0.1, 1.0) - Circuits.startDelay(0.1, -1.0))
        val setup = DriverBuilds.forBlock(3).first().setup
        assertEquals(0.58, Circuits.hands(Drivers.byId("ines")!!.copy(reaction = 58), setup).reaction, 1e-12)
    }

    @Test fun everyDriverHasAReactionAndTheDefaultIsAverage() {
        Drivers.roster.forEach { assertTrue(it.id, it.reaction in 30..95) }
        assertEquals(50, Drivers.you.reaction)
        assertEquals(50, Drivers.Driver("x", "X", 1, 1, 1, 1, 1, Drivers.Specialty.ALL_ROUNDER, 0.0, "").reaction)
        assertEquals(0.3, Skills(0.5, 0.3, 0.5, 0.5).reaction, 0.0)
        assertEquals(Drivers.byId("viktor")!!.reaction / 100.0, Drivers.skills(Drivers.byId("viktor")!!, setup).reaction, 0.0)
        assertTrue(Drivers.Stat.entries.any { it == Drivers.Stat.REACTION })
    }

    @Test fun anOldSaveWithDriversLoadsAndTheirReactionComesFromTheRoster() {
        val full = Json.parseToJsonElement(SaveCodec.encode(GameState(money = 5.0, prestiges = 4, hiredDrivers = setOf("ines"), driverId = "ines"))).jsonObject
        val old = JsonObject(full.filterKeys { it != "reaction" })
        val loaded = SaveCodec.decode(old.toString())
        val d = Drivers.selected(loaded)!!
        assertEquals("ines", d.id)
        assertEquals(58, d.reaction)
        assertEquals(0.5, Drivers.skills(Drivers.you, setup).reaction, 1e-12)
    }
}
