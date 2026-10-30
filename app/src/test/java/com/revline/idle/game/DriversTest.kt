package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.economy.DriverBalanceSim
import com.revline.idle.economy.DriverBuilds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriversTest {
    private val field = Racing.Field("Club", "A tired hatch", pace = 100.0, purse = 1000.0, stake = 0.0, rivalId = "mule")
    private fun state(money: Double = 5_000.0, prestiges: Int = 4) =
        GameState(money = money, prestiges = prestiges, hiredDrivers = setOf("dusty", "gus", "ace"), driverId = "gus")

    // ---- roster ----

    @Test fun rosterShape() {
        val roster = Drivers.roster
        assertTrue(roster.size in 6..10)
        assertEquals(roster.size, roster.map { it.id }.toSet().size)
        assertEquals(setOf(1, 2, 3), roster.map { it.tier }.toSet())
        roster.forEach { d -> listOf(d.throttle, d.clutch, d.shifting, d.dyno).forEach { assertTrue(it in 1..100) } }
        // Every specialty has someone who owns it.
        Drivers.Specialty.entries.forEach { sp -> assertTrue(sp.name, roster.any { it.specialty == sp }) }
    }

    @Test fun feesRiseWithTier() {
        val byTier = Drivers.roster.groupBy { it.tier }
        assertTrue(byTier.getValue(1).maxOf { it.rate } < byTier.getValue(2).minOf { it.rate })
        assertTrue(byTier.getValue(2).maxOf { it.rate } < byTier.getValue(3).minOf { it.rate })
        assertTrue(Drivers.roster.all { it.rate in 0.01..0.40 })
    }

    @Test fun tiersUnlockWithRebuilds() {
        val ace = Drivers.byId("ace")!!
        val dusty = Drivers.byId("dusty")!!
        assertTrue(Drivers.unlocked(GameState(prestiges = 0), dusty))
        assertFalse(Drivers.unlocked(GameState(prestiges = 0), ace))
        assertTrue(Drivers.unlocked(GameState(prestiges = Drivers.tierUnlock(3)), ace))
        // Locked drivers cannot be hired.
        assertEquals(GameState(), Drivers.hire(GameState(), "ace"))
        assertEquals(setOf("dusty"), Drivers.hire(GameState(), "dusty").hiredDrivers)
    }

    // ---- fees ----

    @Test fun feeIsAShareOfThePurse() {
        val gus = Drivers.byId("gus")!!
        assertEquals(gus.rate * 1000.0, Drivers.fee(state(), gus, field), 1e-9)
    }

    @Test fun feeChargedWhenTheDriverDrives() {
        val hand = Drivers.charge(state(), field)!!
        assertEquals("gus", hand.driver!!.id)
        assertEquals(Drivers.byId("gus")!!.rate * 1000.0, hand.fee, 1e-9)
        assertEquals(5_000.0 - hand.fee, hand.state.money, 1e-9)
    }

    @Test fun drivingYourselfIsFree() {
        val me = state().copy(driverId = "")
        val hand = Drivers.charge(me, field)!!
        assertNull(hand.driver)
        assertEquals(0.0, hand.fee, 0.0)
        assertEquals(me, hand.state)
    }

    @Test fun anUnaffordableDriverCannotTakeTheSeat() {
        val fee = Drivers.byId("gus")!!.rate * 1000.0
        assertNull(Drivers.charge(state(money = fee - 1.0), field))
        assertNotNull(Drivers.charge(state(money = fee), field))
        // A stake is checked together with the fee.
        assertNull(Drivers.charge(state(money = fee + 50.0), field, alsoPaying = 100.0))
        assertNotNull(Drivers.charge(state(money = fee + 100.0), field, alsoPaying = 100.0))
    }

    @Test fun devModeAndEmptyPursesAreFree() {
        assertEquals(0.0, Drivers.charge(state().copy(devMode = true, money = 0.0), field)!!.fee, 0.0)
        assertEquals(0.0, Drivers.charge(state(), field.copy(purse = 0.0))!!.fee, 0.0)
    }

    @Test fun selectingNeedsAHiredDriver() {
        val s = GameState(prestiges = 4, hiredDrivers = setOf("dusty"))
        assertEquals("", Drivers.select(s, "ace").driverId)
        assertEquals("dusty", Drivers.select(s, "dusty").driverId)
        assertEquals("", Drivers.select(Drivers.select(s, "dusty"), "").driverId)
        assertEquals("", Drivers.select(s, "nobody").driverId)
    }

    @Test fun resultNoteNamesTheDriverAndFee() {
        val s = GameState(prestiges = 1)
        val note = Racing.stripNote(s, field, true, 11.0, 12.0, false, false, "Gus Harlan", 85.0)
        assertTrue(note, note.contains("Gus Harlan") && note.contains("fee"))
        val plain = Racing.stripNote(s, field, true, 11.0, 12.0, false, false)
        assertFalse(plain, plain.contains("Driver"))
    }

    // ---- save migration ----

    @Test fun oldSavesLoadWithNoDrivers() {
        val full = Json.parseToJsonElement(SaveCodec.encode(GameState(money = 77.0, prestiges = 2))).jsonObject
        val old = JsonObject(full.filterKeys { it != "hiredDrivers" && it != "driverId" })
        val loaded = SaveCodec.decode(old.toString())
        assertEquals(77.0, loaded.money, 0.0)
        assertTrue(loaded.hiredDrivers.isEmpty())
        assertEquals("", loaded.driverId)
        assertNull(Drivers.selected(loaded))
    }

    @Test fun hiredDriversSurviveASaveRoundTrip() {
        val s = Drivers.select(Drivers.hire(Drivers.hire(GameState(prestiges = 3), "dusty"), "pavel"), "pavel")
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(setOf("dusty", "pavel"), back.hiredDrivers)
        assertEquals("pavel", Drivers.selected(back)!!.id)
    }

    @Test fun aStaleDriverIdDrivesYourself() {
        assertNull(Drivers.selected(GameState(driverId = "retired", hiredDrivers = setOf("retired"))))
        assertNull(Drivers.selected(GameState(driverId = "ace", hiredDrivers = emptySet())))
    }

    // ---- the AI hands ----

    private val builds = (0..7 step 3).flatMap { DriverBuilds.forBlock(it) }

    @Test fun aSkilledClutchHandNeverJumpsAndAlwaysShifts() {
        val flawless = Skills(1.0, 0.95, 0.95, 0.95)
        for (b in builds) for (seed in 1..12) {
            val race = AutoRace(b.setup, flawless, 100L + seed).finish()
            assertFalse("${b.name} seed $seed jumped", race.run.jumped)
            assertTrue("${b.name} seed $seed did not finish", race.reachedLine)
            if (b.setup.ratios.size > 1) assertTrue("${b.name} never shifted", race.run.gear > 1)
        }
    }

    @Test fun aSloppyClutchHandJumpsSometimes() {
        val sloppy = Skills(0.4, 0.05, 0.4, 0.4)
        val jumps = builds.sumOf { b -> (1..20).count { AutoRace(b.setup, sloppy, 500L + it).finish().run.jumped } }
        assertTrue("jumps=$jumps", jumps > 0)
    }

    @Test fun sameSeedSameRace() {
        val b = builds.first()
        val a = AutoRace(b.setup, Skills(0.6, 0.6, 0.6, 0.6), 42L).finish().run.time
        val c = AutoRace(b.setup, Skills(0.6, 0.6, 0.6, 0.6), 42L).finish().run.time
        assertEquals(a, c, 0.0)
    }

    @Test fun betterHandsAreFaster() {
        var better = 0
        for (b in builds) {
            fun avg(level: Double) = (1..10).map { AutoRace(b.setup, Skills(level, level, level, level), 900L + it).finish() }
                .filter { it.reachedLine }.map { it.run.time }.average()
            if (avg(0.9) < avg(0.3)) better++
        }
        assertTrue("better on $better of ${builds.size}", better >= builds.size - 1)
    }

    @Test fun theForecastReadsTheSetup() {
        val b = builds.first()
        val f = Drivers.forecast(b.setup)
        assertEquals(Drivers.roster.size, f.size)
        assertTrue(f.any { it.fit == Drivers.Fit.BEST })
        assertTrue(f.all { it.seconds > 0.0 })
    }

    // ---- balance ----

    @Test fun balanceInvariants() {
        DriverBalanceSim.report(seeds = 24)
        val sum = DriverBalanceSim.last!!
        val builds = sum.bestByBuild.size
        val wins = sum.bestByBuild.values.groupingBy { it }.eachCount()
        // No driver owns the game, and everyone is the right hire somewhere.
        assertTrue("share: $wins", wins.values.max() <= builds * 0.40)
        Drivers.roster.forEach { assertTrue("${it.id} is never the pick: $wins", (wins[it.id] ?: 0) >= 1) }
        // Hiring someone beats driving as an average human on most builds.
        val human35 = sum.net.getValue("you@35")
        assertTrue(sum.bestPaidNet.count { it > human35 } >= builds * 0.8)
        // A skilled human (80) out-drives the mid tier on the clock.
        Drivers.roster.filter { it.tier == 2 }.forEach { assertTrue("${it.id}: ${sum.gap}", sum.gap.getValue("you@80") < sum.gap.getValue(it.id)) }
        // Time lost falls with tier, and so does the red-light rate.
        fun tierGap(t: Int) = Drivers.roster.filter { it.tier == t }.map { sum.gap.getValue(it.id) }.average()
        assertTrue(tierGap(1) > tierGap(2) && tierGap(2) > tierGap(3))
        fun tierJump(t: Int) = Drivers.roster.filter { it.tier == t }.map { sum.jump.getValue(it.id) }.average()
        assertTrue(tierJump(1) > tierJump(3))
        // High clutch control does not jump.
        assertTrue(sum.jump.getValue("colette") < 0.01)
        // A specialist beats an all-rounder of the same tier on its own builds, and loses to him elsewhere.
        assertTrue(sum.bestByBuild.values.toSet().size >= 6)
    }
}
