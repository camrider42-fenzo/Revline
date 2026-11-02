package com.revline.idle.ui

import com.revline.idle.economy.DriverBuilds
import com.revline.idle.game.CircuitPace
import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import com.revline.idle.game.Racing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircuitPaceTest {
    private fun car(prestige: Int = 3): Circuits.Car =
        Circuits.carOf(DriverBuilds.make(prestige, Racing.Build.MEDIUM, "race", "close", "lsd", 0.8))

    private val driver = Drivers.byId("colette")!!
    private fun circuit(id: String) = Circuits.all.first { it.id == id }
    private fun hands(c: Circuits.Car, id: String) = Circuits.plan(c, circuit(id), driver).hands
    private fun radii(id: String) = CircuitRadii.of(CircuitLayouts.of(id))

    private fun trace(c: Circuits.Car, id: String, power: Double = 1.0): CircuitPace.Trace {
        val r = radii(id)
        return CircuitPace.trace(c, hands(c, id), power, r.radius, r.ds)
    }

    private fun withGrip(c: Circuits.Car, k: Double) = c.copy(setup = c.setup.copy(gripN = c.setup.gripN * k))
    private fun withPower(c: Circuits.Car, k: Double) = c.copy(setup = c.setup.copy(peakHp = c.setup.peakHp * k, peakTqLbFt = c.setup.peakTqLbFt * k))

    private fun race(id: String, seed: Long): Circuits.Result {
        val c = circuit(id)
        val car = car()
        return Circuits.race(Circuits.plan(car, c, driver), driver, Circuits.parPlan(3, c), seed)
    }

    @Test fun betterBrakesBrakeLaterForTheHardestCorner() {
        for (id in listOf("monza", "silverstone", "spa")) {
            val base = car()
            val stock = trace(base, id)
            val better = trace(base.copy(brake = base.brake * 1.3), id)
            val r = radii(id)
            // The corner with the biggest drop from the speed that comes into it.
            val apex = r.apex.maxBy { a -> stock.v[(a - 12).coerceAtLeast(0)] - stock.v[a] }
            val before = stock.brakeDistance(apex)
            val after = better.brakeDistance(apex)
            assertTrue("$id: brake point $before m vs $after m", after < before)
            for (a in r.apex) assertTrue("$id corner at $a", better.brakeDistance(a) <= stock.brakeDistance(a) + 1e-9)
        }
    }

    @Test fun betterTiresCarryMoreSpeedThroughTheApex() {
        for (id in listOf("monza", "monaco", "silverstone")) {
            val base = car()
            val stock = trace(base, id)
            val better = trace(withGrip(base, 1.15), id)
            val r = radii(id)
            var gained = 0
            for (a in r.apex) {
                assertTrue("$id corner at $a", better.v[a] >= stock.v[a] - 1e-9)
                if (better.v[a] > stock.v[a] + 0.3) gained++
            }
            assertTrue("$id: only $gained of ${r.apex.size} corners gained speed", gained * 2 > r.apex.size)
            assertTrue("$id lap", better.lapTime < stock.lapTime)
        }
    }

    @Test fun moreEnginePowerReachesAHigherTopSpeedAndTrapsFaster() {
        val base = car()
        val stock = trace(base, "monza")
        val strong = trace(withPower(base, 1.4), "monza")
        assertTrue("top ${stock.topSpeed} vs ${strong.topSpeed}", strong.topSpeed > stock.topSpeed + 0.5)
        // The trap speed: the car's mean over its fastest stretch. The speed where the braking begins is the brakes' to set, not the engine's.
        fun trap(t: CircuitPace.Trace) = t.v.sortedDescending().take(t.n / 20).average()
        assertTrue("trap ${trap(strong)} vs ${trap(stock)}", trap(strong) > trap(stock) + 0.5)
        assertTrue("lap ${strong.lapTime} vs ${stock.lapTime}", strong.lapTime < stock.lapTime)
    }

    @Test fun aFresherEngineAcceleratesHarderOutOfACorner() {
        val base = car()
        val r = radii("monza")
        val weak = CircuitPace.trace(base, hands(base, "monza"), 0.9, r.radius, r.ds)
        val fresh = CircuitPace.trace(base, hands(base, "monza"), 1.0, r.radius, r.ds)
        assertTrue(fresh.lapTime < weak.lapTime)
    }

    @Test fun theSpeedTraceIsCloseToTheSimOnEveryTrack() {
        for (c in Circuits.all) {
            val car = car()
            val h = hands(car, c.id)
            val sim = Circuits.lap(car, c, h, 1.0).time
            val tr = trace(car, c.id)
            val ratio = sim / tr.lapTime
            assertTrue("${c.id}: sim ${sim}s, trace ${tr.lapTime}s", ratio in 0.85..1.2)
            assertEquals(c.lapKm * 1000.0, tr.lengthM, c.lapKm * 1000.0 * 0.02)
        }
    }

    @Test fun calibratedRunsLastExactlyAsLongAsTheSimLap() {
        for (seed in listOf(3L, 7L)) {
            val r = race("silverstone", seed)
            val replay = Replay.build(r)
            assertNotNull(replay)
            for (c in replay!!.all) {
                val runs = c.runs
                assertNotNull(runs)
                assertTrue(runs!!.size >= c.completed)
                for (k in 0 until c.completed) {
                    val span = c.cum[k + 1] - c.cum[k]
                    assertEquals("${c.name} lap ${k + 1}", span, runs[k].span, 1e-6)
                    assertEquals(1.0, runs[k].fracAt(span), 1e-9)
                    assertEquals(0.0, runs[k].fracAt(0.0), 1e-9)
                }
            }
        }
    }

    @Test fun carsNeverGoBackwardsAndMeetEachLapLineOnTheClock() {
        val replay = Replay.build(race("monza", 11L))!!
        for (c in replay.all) {
            var last = 0.0
            var t = 0.0
            while (t < replay.duration + 5.0) {
                val p = c.progress(t)
                assertTrue("${c.name} at $t: $p after $last", p >= last - 1e-9)
                last = p
                t += 0.25
            }
            for (k in 1..c.completed) assertEquals(k.toDouble(), c.progress(c.cum[k]), 1e-6)
        }
    }

    @Test fun cornersAreWhereTheCarSlowsDown() {
        // The solver's speed through each official corner is below the speed on the way in, and the cars lift for the corner.
        val tr = trace(car(), "silverstone")
        val r = radii("silverstone")
        var slower = 0
        for (a in r.apex) if (tr.v[a] < tr.v[(a - 25).coerceAtLeast(0)] - 1.0) slower++
        assertTrue(slower >= r.apex.size / 2)
    }

    @Test fun theRevsStayBetweenIdleAndTheRedlineAtEverySpeedOnEveryTrack() {
        for (prestige in listOf(1, 3, 6)) for (c in Circuits.all) {
            val car = car(prestige)
            val tr = trace(car, c.id)
            for (tau in listOf(0.8, 1.0, 1.25)) {
                val run = CircuitPace.run(tr, tr.lapTime * tau)
                var tin = 0.0
                while (tin <= run.span) {
                    val d = run.dash(tin)
                    val msg = "${c.id} p$prestige tau $tau at $tin: ${d.rpm} rpm gear ${d.gear}"
                    assertTrue(msg, d.rpm <= car.setup.redline + 1e-6 && d.rpm >= car.setup.idle - 1e-6)
                    assertTrue(msg, d.gear in 1..car.setup.ratios.size)
                    assertTrue(msg, d.speed >= 0.0 && d.mph >= 0.0)
                    tin += run.span / 400.0
                }
            }
        }
    }

    @Test fun theRevsRiseThroughAGearAndDropAtTheShift() {
        val car = car()
        val tr = trace(car, "monza")
        val run = CircuitPace.run(tr, tr.lapTime)
        var drops = 0
        var prev = run.dash(0.0)
        var tin = 0.0
        var top = 0.0
        while (tin < run.span) {
            val d = run.dash(tin)
            if (d.gear > prev.gear) { drops++; assertTrue("upshift drops the revs", d.rpm < prev.rpm) }
            top = maxOf(top, d.rpm)
            prev = d
            tin += run.span / 4000.0
        }
        assertTrue("shifted $drops times", drops >= 5)
        assertTrue("revs reach the top end: $top of ${car.setup.redline}", top > car.setup.redline * 0.85)
    }

    @Test fun theDashReadsMilesPerHourOfTheCarsOwnSpeed() {
        val replay = Replay.build(race("silverstone", 7L))!!
        val d = replay.you.dash(replay.duration * 0.4)
        assertNotNull(d)
        assertEquals(d!!.speed * 2.23694, d.mph, 1e-9)
        assertTrue("${d.mph} mph", d.mph in 20.0..230.0)
        assertTrue(replay.you.dash(0.0)?.speed ?: 0.0 < 5.0)
    }

    @Test fun theLiveTraceFollowsTheLapAndResetsAtTheLine() {
        val replay = Replay.build(race("silverstone", 3L))!!
        val you = replay.you
        assertTrue(liveCeiling(replay) >= 100.0)
        val cross = you.cum[1]
        val before = you.lapTrace(cross - 0.5)!!
        val after = you.lapTrace(cross + 0.5)!!
        assertTrue("near the end of lap 1: ${before.second}", before.second > 0.95)
        assertTrue("start of lap 2: ${after.second}", after.second < 0.05)
        assertEquals(1, you.lapNumber(cross - 0.5))
        assertEquals(2, you.lapNumber(cross + 0.5))
        // The line only ever grows within a lap.
        var last = 0.0
        var t = cross + 0.1
        while (t < you.cum[2]) { val f = you.lapTrace(t)!!.second; assertTrue(f >= last); last = f; t += 0.5 }
    }
}
