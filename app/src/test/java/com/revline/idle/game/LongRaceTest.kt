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

    @Test fun theWearOfAWholeRaceIsTheOldRacesWearSoTheTargetsHold() {
        for (c in Circuits.all) {
            val oldLaps = c.baseLaps
            for (comp in listOf(S, M, H)) {
                val perLap = Tires.lapUse(c, comp, 0.0, 1.0, 1.0)
                val old = perLap * c.stretch
                assertEquals("${c.id} $comp", oldLaps * old, c.laps * perLap, 1e-9)
            }
        }
        // No-stop softs are badly worn on the corner-heavy tracks and the compounds stay in order everywhere.
        for (tire in listOf("street", "sport", "semi", "racing")) for (c in Circuits.all) {
            val g = Circuits.noStopGrip(car(tire), c, d, Circuits.Pace.STEADY)
            assertTrue("$tire ${c.id} $g", g.getValue(S) <= g.getValue(M) && g.getValue(M) <= g.getValue(H) && g.getValue(S) < g.getValue(H))
            assertTrue("$tire ${c.id} soft ${g.getValue(S)}", g.getValue(S) < 0.85)
            if (c.corners >= 18) assertTrue("$tire ${c.id} soft ${g.getValue(S)}", g.getValue(S) < 0.7)
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

    @Test fun twoStopsBeatOneOnTheTyreEatersAndLoseToOneOnTheKindTracks() {
        fun best(c: Circuits.Circuit, stops: Int) = Circuits.advise(car("street"), c, d).first { it.orders.pace == Circuits.Pace.STEADY && it.orders.stopCount == stops }
        for (id in listOf("monaco", "hungaroring")) {
            val c = circuit(id)
            assertTrue("$id two ${best(c, 2).raceTime} one ${best(c, 1).raceTime}", best(c, 2).raceTime < best(c, 1).raceTime)
        }
        for (id in listOf("monza", "spa")) {
            val c = circuit(id)
            assertTrue("$id two ${best(c, 2).raceTime} one ${best(c, 1).raceTime}", best(c, 1).raceTime < best(c, 2).raceTime)
        }
        // Either way the choice is worth a fraction of a percent, not a different race.
        for (c in Circuits.all) assertTrue(c.id, abs(best(c, 1).raceTime / best(c, 2).raceTime - 1.0) < 0.01)
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
