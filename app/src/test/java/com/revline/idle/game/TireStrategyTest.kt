package com.revline.idle.game

import com.revline.idle.economy.DriverBuilds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TireStrategyTest {
    private val P = 3
    private fun car(tire: String, share: Double = 0.8) = Circuits.carOf(DriverBuilds.make(P, Racing.Build.MEDIUM, "street", "close", "lsd", share, ctire = tire))
    private fun circuit(id: String) = Circuits.all.first { it.id == id }
    private val d = Drivers.byId("colette")!!

    @Test fun aStopIsInsideTheRace() {
        for (c in Circuits.all) {
            assertEquals(0, Circuits.Strategy.STAY_OUT.stopLap(c.laps))
            for (st in listOf(Circuits.Strategy.EARLY, Circuits.Strategy.MID)) assertTrue(st.stopLap(c.laps) in 1 until c.laps)
            assertTrue(Circuits.Strategy.EARLY.stopLap(c.laps) < Circuits.Strategy.MID.stopLap(c.laps) || c.laps <= 3)
        }
    }

    @Test fun aStopCostsTheLossAndGivesNewTires() {
        val c = circuit("monaco")
        val noWear = car("street").copy(wear = 0.0)
        val stay = Circuits.plan(noWear, c, d)
        val stop = Circuits.plan(noWear, c, d, Circuits.Strategy.MID)
        assertEquals(setOf(Circuits.Strategy.MID.stopLap(c.laps)), stop.pits)
        // No wear to cure: the stop is pure loss, one PIT_LOSS of that lap.
        val lapNo = stop.pits.first() - 1
        assertEquals(Circuits.PIT_LOSS * stay.laps[lapNo].time, stop.raceTime - stay.raceTime, 1e-6)
        // With wear the grip comes back after the stop.
        val worn = Circuits.plan(car("racing"), c, d, Circuits.Strategy.MID)
        val after = worn.pits.first()
        assertTrue(worn.grip[after] > worn.grip[after - 1])
        assertEquals(worn.grip[0], worn.grip[after], 1e-9)
        assertTrue(Circuits.plan(car("racing"), c, d).grip.zipWithNext().all { (a, b) -> b < a })
    }

    @Test fun streetTiresNeverPayForAStopButSoftOnesCan() {
        for (c in Circuits.all) {
            val street = Circuits.advise(car("street"), c, d).filter { it.pace == Circuits.Pace.STEADY }
            assertEquals(c.id, Circuits.Strategy.STAY_OUT, street.first().strategy)
        }
        // A slick on a hot circuit pushed hard wants fresh rubber on at least one circuit.
        val wants = Circuits.all.count { c -> Circuits.advise(car("racing"), c, d).first { it.pace == Circuits.Pace.PUSH }.strategy != Circuits.Strategy.STAY_OUT }
        assertTrue("circuits where a stop wins with slicks pushed: $wants", wants in 1 until Circuits.all.size)
    }

    @Test fun pushingBuysLapTimeAndPaysInTiresEngineAndMistakes() {
        val c = circuit("singapore"); val k = car("semi")
        val steady = Circuits.plan(k, c, d); val push = Circuits.plan(k, c, d, pace = Circuits.Pace.PUSH); val save = Circuits.plan(k, c, d, pace = Circuits.Pace.SAVE)
        assertTrue(push.fresh < steady.fresh && steady.fresh < save.fresh || push.laps[0].time < steady.laps[0].time)
        assertTrue(push.grip.last() < steady.grip.last() && steady.grip.last() < save.grip.last())
        assertTrue(push.failure.sum() > steady.failure.sum() && steady.failure.sum() > save.failure.sum())
        assertTrue(push.mistakes > steady.mistakes && steady.mistakes > save.mistakes)
        assertTrue(push.raceTime < steady.raceTime + 5.0)
    }

    @Test fun aCarefulDriverIsKinderToTheTiresAndPushesBetter() {
        val c = circuit("hungaroring"); val k = car("semi")
        val careful = Circuits.plan(k, c, Drivers.you.copy(care = 95, cornering = 80))
        val rough = Circuits.plan(k, c, Drivers.you.copy(care = 10, cornering = 30))
        assertTrue(careful.grip.last() > rough.grip.last())
        val pc = Circuits.plan(k, c, Drivers.you.copy(care = 95), pace = Circuits.Pace.PUSH)
        val pr = Circuits.plan(k, c, Drivers.you.copy(care = 10), pace = Circuits.Pace.PUSH)
        assertTrue(pc.failure.sum() < pr.failure.sum())
        assertTrue(pc.grip.last() > pr.grip.last())
    }

    @Test fun theLogShowsTheStop() {
        val c = circuit("monaco"); val k = car("racing")
        val plan = Circuits.plan(k, c, d, Circuits.Strategy.EARLY)
        val stopLap = plan.pits.first()
        val r = Circuits.race(plan, d, Circuits.parPlan(P, c), 12L)
        assertEquals(Circuits.Strategy.EARLY, r.orders.strategy)
        if (r.retired == null) {
            assertTrue(r.log.rows[stopLap - 1].pitted)
            assertTrue(r.log.events.any { it.lap == stopLap && it.text.startsWith("You pit") })
            assertTrue(RaceLog.lines(r.log)[stopLap - 1].endsWith("PIT"))
        }
    }

    @Test fun aStopDoesNotMoveQualifying() {
        val c = circuit("suzuka"); val k = car("racing")
        val a = Circuits.plan(k, c, d, Circuits.Strategy.EARLY, Circuits.Pace.STEADY)
        val b = Circuits.plan(k, c, d)
        assertEquals(b.fresh, a.fresh, 1e-9)
    }

    @Test fun aStrategistRivalPitsAndTheRaceStaysEven() {
        val c = circuit("silverstone")
        var pits = 0
        for (seed in 1L..120L) {
            val r = Circuits.race(Circuits.plan(car("street"), c, d), d, Circuits.parPlan(P, c), seed)
            pits += r.log.events.count { it.text == "Emerson Fittipaldi pits." }
        }
        assertTrue(pits > 100)
    }

    @Test fun ordersReachTheRace() {
        val s = DriverBuilds.make(P, Racing.Build.MEDIUM, "slick", "close", "lsd", 0.8).copy(money = 1e9, hiredDrivers = setOf("colette"))
        val paid = Circuits.open(s, "colette") as Circuits.Entry.Paid
        val out = Circuits.finish(s, Circuits.carOf(s), circuit("monaco"), paid.driver, paid.fee, paid.entry, 5L, Circuits.Orders(Circuits.Strategy.MID, Circuits.Pace.PUSH))
        assertEquals(Circuits.Pace.PUSH, out.result.orders.pace)
        assertEquals(Circuits.Strategy.MID, out.result.orders.strategy)
    }
}
