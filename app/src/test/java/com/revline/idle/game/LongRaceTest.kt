package com.revline.idle.game

import com.revline.idle.economy.CircuitBalanceSim
import com.revline.idle.economy.DriverBuilds
import com.revline.idle.game.Tires.Compound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/** Races are about 38% of an F1 distance now: 15 to 30 laps, with the same wear, mistakes and cost per race as before. */
class LongRaceTest {
    private val P = 3
    private val d = Drivers.byId("colette")!!
    private fun car(tire: String) = Circuits.carOf(DriverBuilds.make(P, Racing.Build.MEDIUM, "street", "close", "lsd", 0.8, ctire = tire))
    private fun circuit(id: String) = Circuits.all.first { it.id == id }
    private val S = Compound.SOFT
    private val M = Compound.MEDIUM
    private val H = Compound.HARD

    @Test fun aRaceIsAShareOfTheRealDistanceAndTheLapCountsAreWhatTheyAre() {
        val expected = mapOf("monza" to 20, "monaco" to 30, "silverstone" to 20, "spa" to 17, "hungaroring" to 27, "suzuka" to 20, "montreal" to 27, "singapore" to 24)
        for (c in Circuits.all) {
            assertEquals(c.id, expected.getValue(c.id), c.laps)
            assertEquals(c.id, (Circuits.RACE_SHARE * c.f1Laps).roundToInt(), c.laps)
            // The F1 distance is about 305 km (Monaco 78 laps, Singapore 62, as in F1), so laps follow the lap length.
            assertTrue(c.id, c.laps in 15..30)
            val km = c.laps * c.lapKm
            assertTrue("${c.id} $km km", km in 95.0..125.0)
        }
        // The shortest laps run the most laps, as in F1.
        val byLap = Circuits.all.sortedBy { it.lapKm }
        assertTrue(byLap.zipWithNext().all { (a, b) -> a.laps >= b.laps })
    }

    /** Reference car and hands: a street tire, steady pace, a driver of middling care, dry. */
    private fun softLife(c: Circuits.Circuit, n: Int) = Tires.lapUse(c, S, 0.0, 1.0, 1.0) * n

    @Test fun aSoftTireIsAtTheCliffWithinFiveToTenLapsOnEveryCircuit() {
        val cliff = Circuits.all.associate { c ->
            val lap = (1..60).first { Tires.condition(softLife(c, it)) <= 0.60 }
            assertEquals(c.id, lap, Tires.softCliffLap(c))
            c.id to lap
        }
        for ((id, lap) in cliff) assertTrue("$id soft cliff lap $lap", lap in 5..10)
        // The kindest circuit is about ten laps and the roughest about five, in order of corners and heat.
        assertEquals(10, cliff.getValue("monza"))
        assertTrue(cliff.getValue("singapore") <= 5 && cliff.getValue("monaco") <= 6)
        for (a in Circuits.all) for (b in Circuits.all) if (Tires.severity(b) > Tires.severity(a) + 0.01) assertTrue("${a.id} ${b.id}", cliff.getValue(b.id) <= cliff.getValue(a.id))
        println("soft cliff laps: " + cliff)
    }

    @Test fun aSoftTireIsWellDegradedAfterTenLapsAndAtTheFloorOverTheRace() {
        for (c in Circuits.all) {
            assertTrue("${c.id} grip after 10 laps ${Tires.condition(softLife(c, 10))}", Tires.condition(softLife(c, 10)) <= 0.61)
            // On an average-corner track (14 corners) it is well below 60%.
            if (c.corners == 14) assertTrue("${c.id} ${Tires.condition(softLife(c, 10))}", Tires.condition(softLife(c, 10)) < 0.50)
            // A soft run the whole race, on any owned tire at any pace, ends at the floor.
            for (tire in listOf("street", "sport", "semi", "racing")) for (pace in listOf(Circuits.Pace.STEADY, Circuits.Pace.PUSH)) {
                val end = Circuits.noStopGrip(car(tire), c, d, pace).getValue(S)
                assertTrue("$tire ${c.id} $pace soft ${end}", end <= Tires.FLOOR + 0.01)
            }
        }
    }

    @Test fun mediumsLastOneAndAHalfToTwiceSoftsAndHardsTwoAndAHalfToThree() {
        for (c in Circuits.all) {
            val s = Tires.lapUse(c, S, 0.0, 1.0, 1.0)
            val m = s / Tires.lapUse(c, M, 0.0, 1.0, 1.0)
            val h = s / Tires.lapUse(c, H, 0.0, 1.0, 1.0)
            assertTrue("${c.id} medium ${m}x", m in 1.5..2.0)
            assertTrue("${c.id} hard ${h}x", h in 2.5 - 1e-9..3.0)
        }
        // A full race is more than a soft's life and a hard's life is a long stint, not the race, on the rough circuits.
        for (id in listOf("monaco", "singapore", "hungaroring")) {
            val c = circuit(id)
            assertTrue(id, Tires.condition(Tires.lapUse(c, H, 0.0, 1.0, 1.0) * c.laps) <= 0.40)
        }
    }

    @Test fun stopLapIsAChoiceNotJustLapOneOrTwo() {
        // On every circuit the quickest stop lap of a Medium to Hard one-stop is mid-race, and stopping on lap 1 or at the end of the race costs time.
        for (c in Circuits.all) {
            val k = car("semi")
            val times = (1 until c.laps).associateWith { Circuits.plan(k, c, d, Circuits.Orders(M, it, H)).raceTime }
            val best = times.minBy { it.value }
            assertTrue("${c.id} best stop ${best.key} of ${c.laps}", best.key in c.laps / 4..c.laps * 3 / 4)
            assertTrue("${c.id} lap 1 costs ${times.getValue(1) / best.value}", times.getValue(1) > best.value * 1.0005)
            assertTrue("${c.id} last lap costs", times.getValue(c.laps - 1) > best.value * 1.0001)
        }
    }

    @Test fun twoStopsBeatOneOnTheTyreEatersAndTheEdgeShrinksOnTheKindTracks() {
        fun best(c: Circuits.Circuit, stops: Int) = Circuits.advise(car("street"), c, d).first { it.orders.pace == Circuits.Pace.STEADY && it.orders.stopCount == stops }
        fun edge(c: Circuits.Circuit) = best(c, 1).raceTime / best(c, 2).raceTime - 1.0
        for (id in listOf("monaco", "hungaroring", "singapore")) assertTrue("$id edge ${edge(circuit(id))}", edge(circuit(id)) > 0.0)
        // The rougher the circuit on rubber, the more two stops are worth; and either way it is a few percent at most.
        assertTrue(edge(circuit("monaco")) > edge(circuit("monza")))
        for (c in Circuits.all) assertTrue("${c.id} ${edge(c)}", abs(edge(c)) < 0.06)
    }

    @Test fun theAdvisorFindsTheBestOneStopTheExhaustiveSearchFinds() {
        for (id in listOf("monza", "monaco", "spa")) {
            val c = circuit(id); val k = car("semi")
            val exhaustive = Tires.options(c.laps, Circuits.Weather.DRY).filter { it.stops.size == 1 }
                .minOf { Circuits.plan(k, c, d, Circuits.Orders(it.start, it.stop, it.finish)).raceTime }
            val adv = Circuits.advise(k, c, d).filter { it.orders.pace == Circuits.Pace.STEADY && it.orders.stopCount == 1 }.minOf { it.raceTime }
            assertTrue("$id advisor $adv exhaustive $exhaustive", adv <= exhaustive * 1.0005)
        }
    }

    @Test fun theAdvicesAreExactPlansAndSortedAndHaveTheGripAtEveryStop() {
        for (c in Circuits.all) {
            val adv = Circuits.advise(car("sport"), c, d)
            assertTrue(adv.zipWithNext().all { (a, b) -> a.raceTime <= b.raceTime })
            assertTrue(adv.any { it.orders.stopCount == 1 } && adv.any { it.orders.stopCount == 2 })
            for (a in adv) {
                assertEquals(a.raceTime, Circuits.plan(car("sport"), c, d, a.orders).raceTime, 1e-9)
                assertTrue(Tires.breach(a.orders.fit, c.laps, Circuits.Weather.DRY) == null)
                if (a.orders.stopCount == 2) assertTrue(a.gripAtStop2 != null && a.gripAtStop != null) else assertTrue(a.gripAtStop2 == null)
            }
        }
    }

    @Test fun aLongerRaceStillCostsAndKillsAsOftenAsTheOldOne() {
        // Mistakes and engine failures are per race, not per lap: the retirement rate is what it was (5.5 to 8.7% by build in 1.74.0).
        var dnf = 0; var n = 0
        for (c in Circuits.all) for (build in DriverBuilds.orientations(P)) {
            val cell = CircuitBalanceSim.cell(P, build, c, d, 120)
            dnf += (cell.dnf * 120).roundToInt(); n += 120
        }
        val rate = dnf / n.toDouble()
        assertTrue("retirements $rate", rate in 0.04..0.11)
    }

    @Test fun theRaceSimIsFast() {
        val k = car("sport")
        val t0 = System.nanoTime()
        var n = 0
        for (c in Circuits.all) {
            val par = Circuits.parPlan(P, c)
            val plan = Circuits.plan(k, c, d, Circuits.Orders(M, c.laps / 2, H))
            for (seed in 1L..150L) { Circuits.race(plan, d, par, seed); n++ }
        }
        val ms = (System.nanoTime() - t0) / 1e6
        assertTrue("$n races took $ms ms", ms < 8000.0)
        val t1 = System.nanoTime()
        for (c in Circuits.all) Circuits.advise(k, c, d)
        val adviseMs = (System.nanoTime() - t1) / 1e6 / Circuits.all.size
        assertTrue("advise took $adviseMs ms", adviseMs < 800.0)
    }
}
