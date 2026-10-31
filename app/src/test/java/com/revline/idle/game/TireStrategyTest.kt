package com.revline.idle.game

import com.revline.idle.economy.DriverBuilds
import com.revline.idle.game.Tires.Compound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TireStrategyTest {
    private val P = 3
    private fun car(tire: String, share: Double = 0.8) = Circuits.carOf(DriverBuilds.make(P, Racing.Build.MEDIUM, "street", "close", "lsd", share, ctire = tire))
    private fun circuit(id: String) = Circuits.all.first { it.id == id }
    private val d = Drivers.byId("colette")!!

    @Test fun aStopCostsTheLossAndGivesNewTires() {
        val c = circuit("monaco")
        val noWear = car("street").copy(wear = 0.0)
        val o = Circuits.Orders(Compound.MEDIUM, 3, Compound.MEDIUM)
        // Same compound is not legal in the dry: two plans with equal grip and no wear differ only in the compound and the stop.
        val a = Circuits.plan(noWear, c, d, Circuits.Orders(Compound.HARD, 3, Compound.MEDIUM))
        val b = Circuits.plan(noWear, c, d, Circuits.Orders(Compound.HARD, 5, Compound.MEDIUM))
        assertEquals(setOf(3), a.pits)
        assertTrue(a.pits.first() == 3 && b.pits.first() == 5 && o.stop == 3)
        // With wear the grip comes back after the stop.
        val worn = Circuits.plan(car("racing"), c, d, Circuits.Orders(Compound.SOFT, 3, Compound.MEDIUM))
        assertTrue(worn.grip[3] > worn.grip[2])
        assertEquals(1.0 - 0.0, Tires.condition(0.0), 1e-12)
        assertTrue(worn.grip.take(3).zipWithNext().all { (x, y) -> y < x } && worn.grip.drop(3).zipWithNext().all { (x, y) -> y < x })
    }

    @Test fun aStopCostsOneLossOfALap() {
        val c = circuit("hungaroring"); val k = car("street").copy(wear = 0.0)
        val o = Circuits.Orders(Compound.MEDIUM, 2, Compound.MEDIUM.let { Compound.HARD })
        val p = Circuits.plan(k, c, d, o)
        val lap = p.laps[1]
        assertEquals(Circuits.PIT_LOSS * (lap.time / (1.0 + Circuits.PIT_LOSS)), lap.time - p.raw[1], 1e-6 * lap.time)
    }

    @Test fun pushingBuysLapTimeAndPaysInTiresEngineAndMistakes() {
        val c = circuit("singapore"); val k = car("semi")
        val o = { pace: Circuits.Pace -> Circuits.Orders(Compound.MEDIUM, 2, Compound.HARD, pace) }
        val steady = Circuits.plan(k, c, d, o(Circuits.Pace.STEADY)); val push = Circuits.plan(k, c, d, o(Circuits.Pace.PUSH)); val save = Circuits.plan(k, c, d, o(Circuits.Pace.SAVE))
        assertTrue(push.laps[0].time < steady.laps[0].time && steady.laps[0].time < save.laps[0].time)
        assertTrue(push.gripAtFlag < steady.gripAtFlag && steady.gripAtFlag < save.gripAtFlag)
        assertTrue(push.gripAtStop!! < steady.gripAtStop!! && steady.gripAtStop!! < save.gripAtStop!!)
        assertTrue(push.failure.sum() > steady.failure.sum() && steady.failure.sum() > save.failure.sum())
        assertTrue(push.mistakes > steady.mistakes && steady.mistakes > save.mistakes)
        assertTrue(push.raceTime < steady.raceTime + 5.0)
    }

    @Test fun everyPaceIsTheBestOnAtLeastOneCircuitOrCloseToIt() {
        // Pace is a real choice: the three paces' best plans are within about a percent of each other.
        for (c in Circuits.all) {
            val adv = Circuits.advise(car("street"), c, d)
            val bests = Circuits.Pace.entries.map { p -> adv.first { it.orders.pace == p }.raceTime }
            assertTrue("${c.id} $bests", bests.max() / bests.min() < 1.012)
        }
    }

    @Test fun aCarefulDriverIsKinderToTheTiresAndPushesBetter() {
        val c = circuit("hungaroring"); val k = car("semi")
        val o = Circuits.Orders(Compound.SOFT, 2, Compound.MEDIUM)
        val careful = Circuits.plan(k, c, Drivers.you.copy(care = 95, cornering = 80), o)
        val rough = Circuits.plan(k, c, Drivers.you.copy(care = 10, cornering = 30), o)
        assertTrue(careful.gripAtStop!! > rough.gripAtStop!! && careful.gripAtFlag > rough.gripAtFlag)
        val po = o.copy(pace = Circuits.Pace.PUSH)
        val pc = Circuits.plan(k, c, Drivers.you.copy(care = 95), po)
        val pr = Circuits.plan(k, c, Drivers.you.copy(care = 10), po)
        assertTrue(pc.failure.sum() < pr.failure.sum())
        assertTrue(pc.gripAtFlag > pr.gripAtFlag)
    }

    @Test fun aStopDoesNotMoveQualifying() {
        val c = circuit("suzuka"); val k = car("racing")
        val a = Circuits.plan(k, c, d, Circuits.Orders(Compound.SOFT, 1, Compound.MEDIUM))
        val b = Circuits.plan(k, c, d, Circuits.Orders(Compound.SOFT, 3, Compound.HARD))
        assertEquals(b.fresh, a.fresh, 1e-9)
        assertTrue(Circuits.plan(k, c, d, Circuits.Orders(Compound.HARD, 2, Compound.MEDIUM)).fresh > a.fresh)
    }

    @Test fun aStrategistRivalPitsEveryRaceAndTheRaceStaysEven() {
        val c = circuit("silverstone")
        var pits = 0
        for (seed in 1L..120L) {
            val r = Circuits.race(Circuits.plan(car("street"), c, d), d, Circuits.parPlan(P, c), seed)
            pits += r.log.events.count { it.text.startsWith("Emerson Fittipaldi pits: ") }
        }
        assertTrue(pits > 100)
    }

    @Test fun ordersReachTheRace() {
        val s = DriverBuilds.make(P, Racing.Build.MEDIUM, "slick", "close", "lsd", 0.8).copy(money = 1e9, hiredDrivers = setOf("colette"))
        val paid = Circuits.open(s, "colette") as Circuits.Entry.Paid
        val o = Circuits.Orders(Compound.SOFT, 2, Compound.HARD, Circuits.Pace.PUSH)
        val out = Circuits.finish(s, Circuits.carOf(s), circuit("monaco"), paid.driver, paid.fee, paid.entry, 5L, o)
        assertEquals(o, out.result.orders)
    }

    @Test fun everyTireTierIsStillDrivableAndBestPlansExistForAll() {
        for (t in listOf("street", "sport", "semi", "racing")) for (c in Circuits.all) {
            val adv = Circuits.advise(car(t), c, d)
            assertTrue(adv.isNotEmpty() && adv.all { it.raceTime > 0.0 })
        }
    }
}
