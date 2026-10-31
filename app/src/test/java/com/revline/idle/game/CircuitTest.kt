package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.economy.DriverBuilds
import com.revline.idle.game.Circuits.Aspect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CircuitTest {
    private fun circuit(id: String) = Circuits.all.first { it.id == id }
    private val P = 3

    /** A medium-chassis car on the P=3 block, race-tuned. [edit] swaps parts. */
    private fun state(
        tire: String = "race", box: String = "close", line: String = "lsd", share: Double = 0.8,
        build: Racing.Build = Racing.Build.MEDIUM, edit: (GameState) -> GameState = { it },
    ) = edit(DriverBuilds.make(P, build, tire, box, line, share))

    private fun lap(s: GameState, id: String, driver: Drivers.Driver = Drivers.you): Double =
        Circuits.plan(Circuits.carOf(s), circuit(id), driver).mean

    private fun lapAt(s: GameState, id: String) = lap(s, id)

    // ---- circuits ----

    @Test fun eightRealCircuitsWithRealLengths() {
        assertEquals(8, Circuits.all.size)
        val real = mapOf(
            "monza" to 5.793, "monaco" to 3.337, "silverstone" to 5.891, "spa" to 7.004,
            "hungaroring" to 4.381, "suzuka" to 5.807, "montreal" to 4.361, "singapore" to 4.940,
        )
        assertEquals(real.keys, Circuits.all.map { it.id }.toSet())
        Circuits.all.forEach {
            assertEquals(it.id, real.getValue(it.id), it.lapKm, 1e-9)
            assertEquals(it.id, it.lapKm * 1000.0, it.lapMetres, 1.0)
            assertTrue(it.laps in 3..6)
            assertTrue(it.corners in 8..25)
        }
    }

    @Test fun emphasisFavorsTheRightThings() {
        val monza = Circuits.favors(circuit("monza"))
        assertEquals(Aspect.TOP_SPEED, monza.first())
        listOf("suzuka", "hungaroring").forEach { assertEquals(it, Aspect.CORNERING, Circuits.favors(circuit(it)).first()) }
        assertTrue(Aspect.CORNERING in Circuits.favors(circuit("silverstone")))
        listOf("monaco", "singapore").forEach {
            val f = Circuits.favors(circuit(it))
            assertTrue(it, Aspect.BRAKING in f || Aspect.ACCELERATION in f)
            assertFalse(it, Aspect.TOP_SPEED in f)
        }
        assertEquals(Aspect.TOP_SPEED, Circuits.favors(circuit("spa")).first())
        // Different circuits, different favourites.
        assertTrue(Circuits.all.map { Circuits.favors(it).first() }.toSet().size >= 3)
        // Top speed matters far less on the twisty circuits than at Monza.
        val e = { id: String, a: Aspect -> Circuits.emphasis(circuit(id)).getValue(a) }
        assertTrue(e("monza", Aspect.TOP_SPEED) > 3 * e("monaco", Aspect.TOP_SPEED))
        assertTrue(e("suzuka", Aspect.CORNERING) > 3 * e("montreal", Aspect.CORNERING))
    }

    @Test fun throttleShareFollowsTheRealCircuits() {
        val car = Circuits.carOf(state())
        val full = Circuits.all.associate { it.id to Circuits.plan(car, it, Drivers.you).fullThrottle * 100 }
        assertTrue(full.getValue("monza") > full.getValue("monaco") + 12)
        assertTrue(full.getValue("monza") > full.getValue("singapore") + 8)
        assertTrue(full.getValue("spa") > full.getValue("hungaroring"))
    }

    // ---- the sim ----

    @Test fun sameSeedSameRace() {
        val car = Circuits.carOf(state())
        val c = circuit("suzuka"); val driver = Drivers.byId("viktor")!!
        val a = Circuits.race(Circuits.plan(car, c, driver), driver, Circuits.parPlan(P, c), 99L)
        val b = Circuits.race(Circuits.plan(car, c, driver), driver, Circuits.parPlan(P, c), 99L)
        assertEquals(a.totalTime, b.totalTime, 0.0)
        assertEquals(a.position, b.position)
        assertEquals(a.incidents, b.incidents)
        val other = (1L..40L).map { Circuits.race(Circuits.plan(car, c, driver), driver, Circuits.parPlan(P, c), it).totalTime }.toSet()
        assertTrue(other.size > 30)
    }

    @Test fun aRaceReadsRight() {
        val car = Circuits.carOf(state())
        val c = circuit("monza"); val d = Drivers.byId("colette")!!
        val r = Circuits.race(Circuits.plan(car, c, d), d, Circuits.parPlan(P, c), 5L)
        assertEquals(Circuits.FIELD, r.field)
        assertEquals(Circuits.FIELD, r.grid.size)
        assertTrue(r.position in 1..Circuits.FIELD)
        if (r.retired == null) {
            assertEquals(c.laps, r.lapTimes.size)
            assertTrue(r.bestLap <= r.lapTimes.drop(1).minOrNull()!! + 1e-9 || r.lapTimes.size == 1)
            assertEquals(r.lapTimes.sum(), r.totalTime, 1e-6)
        }
        assertTrue(r.totalTime > 0)
        assertEquals(1, r.grid.count { it.you })
    }

    // ---- racing parts move the lap ----

    @Test fun tiresMoveTheLap() {
        listOf("monaco", "suzuka", "monza").forEach { id ->
            val t = listOf("slick", "race", "radial", "bias").map { lapAt(state(tire = it), id) }
            assertTrue("$id $t", t[0] < t[1] && t[1] < t[2] && t[2] < t[3])
        }
        // Grip is worth most where there are the most corners at the limit.
        val gain = { id: String -> 1 - lapAt(state(tire = "slick"), id) / lapAt(state(tire = "radial"), id) }
        assertTrue(gain("suzuka") > gain("monza"))
    }

    @Test fun axleMovesTheLap() {
        listOf("monaco", "singapore", "hungaroring").forEach { id ->
            val t = listOf("cv", "irs", "lsd", "open").map { lapAt(state(line = it), id) }
            assertTrue("$id $t", t[0] < t[1] && t[1] < t[2] && t[2] < t[3])
        }
    }

    @Test fun gearboxMovesTheLap() {
        // A power build, where the box is the limit. The dog box and the four-speed beat the gappy crash box everywhere.
        fun at(box: String) = DriverBuilds.make(P, Racing.Build.MEDIUM, "slick", box, "irs", 1.0, rev = true)
        val ids = listOf("monaco", "singapore", "monza")
        ids.forEach { id ->
            val dog = lapAt(at("dog"), id); val four = lapAt(at("four"), id); val crash = lapAt(at("crash"), id)
            assertTrue("$id dog $dog crash $crash", dog < crash)
            assertTrue("$id four $four crash $crash", four < crash)
            assertTrue("$id dog $dog four $four", dog < four)
        }
        // Gear changes cost most where there are the most of them.
        val gain = { id: String -> 1 - lapAt(at("dog"), id) / lapAt(at("crash"), id) }
        val g = ids.associateWith { gain(it) }
        assertTrue("gains $g", g.getValue("monaco") > g.getValue("monza"))
        assertTrue("gains $g", g.values.all { it > 0.004 })
    }

    @Test fun finalDriveTradesAccelerationForTopSpeed() {
        fun withFinal(f: String) = state(edit = { it.copy(finalDrive = f) })
        val shortMonaco = lapAt(withFinal("short"), "monaco"); val tallMonaco = lapAt(withFinal("tall"), "monaco")
        assertTrue("monaco short $shortMonaco tall $tallMonaco", shortMonaco < tallMonaco)
        val shortHung = lapAt(withFinal("short"), "hungaroring"); val tallHung = lapAt(withFinal("tall"), "hungaroring")
        assertTrue(shortHung < tallHung)
        // A car with the power to use a tall gear wants it on the straights, and Monza and Spa pay the most for it.
        fun power(f: String) = DriverBuilds.make(4, Racing.Build.MEDIUM, "slick", "dog", "irs", 1.0, rev = true).copy(finalDrive = f)
        val at = { f: String, id: String -> Circuits.plan(Circuits.carOf(power(f)), circuit(id), Drivers.you) }
        assertTrue(at("tall", "monza").top > at("short", "monza").top + 30)
        listOf("monza", "spa").forEach { id ->
            val tall = at("tall", id).mean; val road = at("road", id).mean; val short = at("short", id).mean
            assertTrue("$id tall $tall road $road short $short", tall < road && road < short)
        }
        val flip = { id: String -> (at("short", id).mean - at("tall", id).mean) / at("tall", id).mean }
        assertTrue(flip("monza") > 3 * flip("monaco"))
        assertTrue(flip("spa") > 3 * flip("hungaroring"))
    }

    @Test fun weightMovesTheLap() {
        val light = Racing.groupChassis(P, Racing.Build.LIGHT)
        val heavy = Racing.groupChassis(P, Racing.Build.HEAVY)
        val a = lapAt(DriverBuilds.make(P, Racing.Build.LIGHT, "race", "close", "lsd", 0.8), "monaco")
        val b = lapAt(DriverBuilds.make(P, Racing.Build.HEAVY, "race", "close", "lsd", 0.8), "monaco")
        assertTrue("light ${Racing.Build.LIGHT} $a heavy $b", a < b)
        assertTrue(light.id != heavy.id)
        // Weight matters at every circuit, most where there is the most braking and accelerating.
        val rel = { id: String ->
            lapAt(DriverBuilds.make(P, Racing.Build.HEAVY, "race", "close", "lsd", 0.8), id) /
                lapAt(DriverBuilds.make(P, Racing.Build.LIGHT, "race", "close", "lsd", 0.8), id)
        }
        Circuits.all.forEach { assertTrue(it.id, rel(it.id) > 1.0) }
    }

    @Test fun chassisLevelMovesTheLap() {
        val body = Racing.groupChassis(P, Racing.Build.MEDIUM)
        fun at(level: Int) = state(edit = { it.copy(chassisLevel = mapOf(body.id to level), coolingSlots = 0) })
        val laps = (0..3).map { lapAt(at(it), "monaco") }
        assertTrue("levels $laps", laps.zipWithNext().all { (x, y) -> y <= x + 1e-9 } && laps.last() < laps.first())
    }

    @Test fun coolingSlotsMoveTheLapOnAHotStuffedBuild() {
        val hot = DriverBuilds.make(P, Racing.Build.MEDIUM, "slick", "dog", "irs", 0.7)
        val body = Racing.groupChassis(P, Racing.Build.MEDIUM)
        val max = Racing.slotMax(body, hot.chassisLevel[body.id] ?: 0)
        assertTrue(max > 0)
        val bare = hot.copy(coolingSlots = 0); val cooled = hot.copy(coolingSlots = max)
        val fadeBare = Circuits.carOf(bare).fade; val fadeCooled = Circuits.carOf(cooled).fade
        assertTrue("fade bare $fadeBare cooled $fadeCooled", fadeBare > fadeCooled)
        listOf("singapore", "hungaroring", "monza").forEach { id ->
            assertTrue(id, lapAt(cooled, id) < lapAt(bare, id))
        }
        // Heat is worse at the hot circuits, so the same fade costs more time there.
        val cost = { id: String -> lapAt(bare, id) / lapAt(cooled, id) }
        assertTrue(cost("singapore") > cost("spa"))
    }

    @Test fun powerMovesTheLapMostWhereTheStraightsAre() {
        val weak = state(share = 0.4); val strong = state(share = 1.0)
        val gain = { id: String -> 1 - lapAt(strong, id) / lapAt(weak, id) }
        Circuits.all.forEach { assertTrue(it.id, gain(it.id) > 0.0) }
        assertTrue(gain("monza") > gain("monaco"))
        assertTrue(gain("spa") > gain("hungaroring"))
    }

    // ---- the driver ----

    @Test fun driverStatsMoveTheLap() {
        val s = state()
        val base = Drivers.you
        listOf("corner" to base.copy(cornering = 90), "brake" to base.copy(braking = 90), "accel" to base.copy(acceleration = 90)).forEach { (n, d) ->
            Circuits.all.forEach { c -> assertTrue("$n ${c.id}", lap(s, c.id, d) < lap(s, c.id, base)) }
        }
        // Cornering stat is worth the most at the cornering circuits, braking at the stop-go ones.
        val corner = { id: String -> 1 - lap(s, id, base.copy(cornering = 90)) / lap(s, id) }
        assertTrue(corner("suzuka") > corner("monza"))
        assertTrue(corner("hungaroring") > corner("montreal"))
    }

    @Test fun lowerMistakesMeanFewerIncidents() {
        val s = state(); val c = circuit("monaco"); val par = Circuits.parPlan(P, c)
        fun incidents(d: Drivers.Driver) = (1L..300L).sumOf { Circuits.race(Circuits.plan(Circuits.carOf(s), c, d), d, par, it).incidents.size }
        val clean = incidents(Drivers.you.copy(mistakes = 15)); val sloppy = incidents(Drivers.you.copy(mistakes = 85))
        assertTrue("clean $clean sloppy $sloppy", sloppy > 2 * clean)
    }

    @Test fun engineCareMeansFewerEngineFailures() {
        val s = state(share = 1.0, edit = { it.copy(upgrades = it.upgrades + mapOf("rev_kit" to 4, "cam" to 4)) })
        val c = circuit("singapore"); val par = Circuits.parPlan(P, c)
        fun fails(d: Drivers.Driver) = (1L..600L).count { Circuits.race(Circuits.plan(Circuits.carOf(s), c, d), d, par, it).engineFailed }
        val careful = fails(Drivers.you.copy(care = 95, mistakes = 0)); val rough = fails(Drivers.you.copy(care = 10, mistakes = 0))
        assertTrue("careful $careful rough $rough", rough > 1.8 * careful && rough > careful + 10)
        // Care also keeps the power: less wear, a faster late lap.
        val a = Circuits.plan(Circuits.carOf(s), c, Drivers.you.copy(care = 95)); val b = Circuits.plan(Circuits.carOf(s), c, Drivers.you.copy(care = 10))
        assertTrue(a.failure.sum() < b.failure.sum())
    }

    @Test fun aWeakEngineBlowsMoreOften() {
        val strong = state(share = 0.4); val frail = state(share = 1.0, edit = { it.copy(upgrades = it.upgrades + mapOf("rev_kit" to 4, "cam" to 4, "springs" to 4)) })
        val a = Circuits.carOf(strong); val b = Circuits.carOf(frail)
        assertTrue(b.reliability < a.reliability)
        val c = circuit("spa")
        assertTrue(Circuits.plan(b, c, Drivers.you).failure.sum() > Circuits.plan(a, c, Drivers.you).failure.sum())
    }

    // ---- money ----

    @Test fun entryFeePrizeAndRepair() {
        val s = GameState(prestiges = 3, money = 1.0e9, hiredDrivers = setOf("ace"), driverId = "ace")
        val stake = Racing.stakeCost(3)
        assertEquals(0.5 * stake, Circuits.entry(s), 1e-6)
        assertEquals(Circuits.FEE_SHARE * Drivers.byId("ace")!!.rate * 3.2 * 0.5 * stake, Circuits.fee(s, Drivers.byId("ace")!!), 1e-6)
        assertEquals(3.2 * 0.5 * stake, Circuits.prize(s, 1, false), 1e-6)
        assertEquals(0.0, Circuits.prize(s, 1, true), 0.0)
        assertEquals(0.0, Circuits.prize(s, Circuits.FIELD, false), 0.0)
        assertTrue(Circuits.prize(s, 4, false) > Circuits.entry(s))
        assertTrue(Circuits.prize(s, 5, false) < Circuits.entry(s))
        assertEquals(0.25 * Circuits.entry(s), Circuits.repair(s), 1e-9)
        assertTrue(Circuits.prizes.zipWithNext().all { (a, b) -> a > b })
    }

    @Test fun entryChargesUpFrontAndRefusesWhenShort() {
        val stake = Racing.stakeCost(3)
        val poor = GameState(prestiges = 3, money = 0.4 * stake, hiredDrivers = setOf("gus"), driverId = "gus")
        assertTrue(Circuits.open(poor) is Circuits.Entry.Refused)
        val ok = GameState(prestiges = 3, money = 10 * stake, hiredDrivers = setOf("gus"), driverId = "gus")
        val paid = Circuits.open(ok) as Circuits.Entry.Paid
        assertEquals(ok.money - paid.entry - paid.fee, paid.state.money, 1e-6)
        assertTrue(paid.fee > 0)
        // The driver's fee counts toward what you need.
        val gus = Drivers.byId("gus")!!
        val shortOfFee = GameState(prestiges = 3, money = Circuits.entry(ok) + 0.5 * Circuits.fee(ok, gus), hiredDrivers = setOf("gus"), driverId = "gus")
        assertTrue(Circuits.open(shortOfFee) is Circuits.Entry.Refused)
        // Dev mode is free.
        val dev = GameState(prestiges = 3, money = 0.0, devMode = true, hiredDrivers = setOf("gus"))
        val free = Circuits.open(dev) as Circuits.Entry.Paid
        assertEquals(0.0, free.entry, 0.0)
        assertEquals(0.0, Circuits.prize(dev, 1, false), 0.0)
    }

    @Test fun finishPaysAndNames() {
        val s = state().copy(money = 1.0e9, hiredDrivers = setOf("gus"), driverId = "gus")
        val paid = Circuits.open(s) as Circuits.Entry.Paid
        val out = Circuits.finish(s, Circuits.carOf(s), circuit("monza"), paid.driver, paid.fee, paid.entry, 7L)
        assertTrue(out.note.contains("Monza"))
        assertEquals(Circuits.prize(s, out.result.position, out.result.retired != null), out.prize, 1e-6)
        assertEquals(out.result.engineFailed, out.repair > 0.0)
        assertNotNull(out.result)
        assertNull(null)
    }

    // ---- Circuit races are for hired drivers only.

    @Test fun noHiredDriverMeansNoEntry() {
        val rich = GameState(prestiges = 3, money = 1.0e12)
        assertNull(Circuits.entrant(rich))
        val refused = Circuits.open(rich) as Circuits.Entry.Refused
        assertTrue(refused.note.contains("Hire a driver to enter circuit races"))
        // Rich, dev mode, a stale selection and a driver who is not hired: still nobody at the wheel.
        assertTrue(Circuits.open(rich.copy(devMode = true)) is Circuits.Entry.Refused)
        assertTrue(Circuits.open(rich.copy(driverId = "ace")) is Circuits.Entry.Refused)
        assertTrue(Circuits.open(rich, "ace") is Circuits.Entry.Refused)
        assertTrue(Circuits.open(rich, "") is Circuits.Entry.Refused)
        // Nothing is charged for a refusal.
        assertEquals(1.0e12, rich.money, 0.0)
    }

    @Test fun theEntrantIsAlwaysAHiredDriver() {
        val s = GameState(prestiges = 3, money = 1.0e12, hiredDrivers = setOf("gus", "ace"), driverId = "")
        val hired = Drivers.hired(s)
        // A blank pick (the old "You") falls back to the first hired driver, as does a stale or unhired id.
        assertEquals(hired.first().id, Circuits.entrant(s, "")!!.id)
        assertEquals(hired.first().id, Circuits.entrant(s, "nobody")!!.id)
        assertEquals(hired.first().id, Circuits.entrant(s, "viktor")!!.id)
        assertEquals("ace", Circuits.entrant(s, "ace")!!.id)
        assertEquals("gus", Circuits.entrant(s, "gus")!!.id)
        for (pick in listOf("", "nobody", "viktor", "gus", "ace")) {
            val paid = Circuits.open(s, pick) as Circuits.Entry.Paid
            assertTrue(paid.driver.id in s.hiredDrivers)
            assertTrue(paid.driver.id.isNotEmpty())
            assertTrue(paid.driver.name != "You")
        }
        // The drag-race toggle on "drive myself" does not stop a hired driver from entering.
        assertEquals("ace", (Circuits.open(s, "ace") as Circuits.Entry.Paid).driver.id)
    }

    @Test fun theChosenDriverPaysHisOwnFee() {
        val s = GameState(prestiges = 3, money = 1.0e12, hiredDrivers = setOf("gus", "ace"))
        val gus = Circuits.open(s, "gus") as Circuits.Entry.Paid
        val ace = Circuits.open(s, "ace") as Circuits.Entry.Paid
        assertEquals(Circuits.fee(s, Drivers.byId("gus")!!), gus.fee, 1e-9)
        assertEquals(Circuits.fee(s, Drivers.byId("ace")!!), ace.fee, 1e-9)
        assertTrue(ace.fee > gus.fee)
        assertTrue(gus.fee > 0.0)
    }

    @Test fun theRaceNeverNamesYou() {
        val s = GameState(prestiges = 3, money = 1.0e12, hiredDrivers = setOf("gus"))
        val paid = Circuits.open(s) as Circuits.Entry.Paid
        val out = Circuits.finish(s, Circuits.carOf(s), circuit("monza"), paid.driver, paid.fee, paid.entry, 3L)
        assertEquals("Jackie Stewart", out.result.driver)
        assertTrue(out.result.grid.any { it.you && it.name == "Jackie Stewart" })
    }
}
