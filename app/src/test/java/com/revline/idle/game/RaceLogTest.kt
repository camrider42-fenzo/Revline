package com.revline.idle.game

import com.revline.idle.economy.DriverBuilds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceLogTest {
    private val P = 3
    private fun race(id: String, seed: Long, driver: String = "colette"): Circuits.Result {
        val c = Circuits.all.first { it.id == id }
        val d = Drivers.byId(driver)!!
        val car = Circuits.carOf(DriverBuilds.make(P, Racing.Build.MEDIUM, "race", "close", "lsd", 0.8))
        return Circuits.race(Circuits.plan(car, c, d), d, Circuits.parPlan(P, c), seed)
    }

    @Test fun everyLapIsLogged() {
        for (seed in 1L..60L) for (id in listOf("monza", "monaco", "spa")) {
            val r = race(id, seed)
            assertEquals(r.circuit.laps, r.log.rows.size)
            r.log.rows.forEach { row ->
                assertEquals(Circuits.FIELD, row.order.size)
                assertEquals((1..Circuits.FIELD).toList(), row.order.map { it.position })
                assertEquals(1, row.order.count { it.you })
                assertEquals(row.yourPosition, row.order.first { it.you }.position)
            }
        }
    }

    @Test fun lastLapOrderIsTheResult() {
        for (seed in 1L..80L) {
            val r = race("suzuka", seed)
            val last = r.log.rows.last()
            if (r.retired == null) assertEquals(r.position, last.yourPosition)
            val finishers = r.grid.filter { !it.out }.map { it.name }
            assertEquals(finishers, last.order.filter { !it.out }.map { it.name })
        }
    }

    @Test fun gapsAddUp() {
        for (seed in 1L..40L) {
            val r = race("monza", seed)
            r.log.rows.filter { !it.yourOut }.forEach { row ->
                assertEquals(0.0, row.order.first { !it.out }.gap!!, 1e-9)
                val me = row.order.first { it.you }
                assertEquals(me.gap!!, row.gapLeader!!, 1e-9)
                if (row.yourPosition == 1) assertEquals(0.0, row.gapAhead!!, 1e-9)
                else {
                    val ahead = row.order[row.yourPosition - 2]
                    assertEquals(me.gap!! - ahead.gap!!, row.gapAhead!!, 1e-9)
                    assertTrue(row.gapAhead!! >= 0.0)
                }
                var prev = -1.0
                row.order.filter { !it.out }.forEach { assertTrue(it.gap!! >= prev); prev = it.gap!! }
            }
        }
    }

    @Test fun yourLapsAreTheResultsLaps() {
        val r = race("spa", 11L)
        if (r.retired == null) {
            assertEquals(r.lapTimes, r.log.rows.map { it.yourTime })
            assertEquals(r.totalTime, r.lapTimes.sum(), 1e-6)
        }
    }

    @Test fun incidentsAndRetirementsAreEvents() {
        var sawOut = false; var sawIncident = false
        for (seed in 1L..400L) {
            val r = race("monaco", seed, "dusty")
            val text = r.log.events.map { it.text }
            if (r.retired != null) { sawOut = true; assertTrue(text.any { it.startsWith("You are out") }) }
            if (r.incidents.isNotEmpty()) { sawIncident = true; assertEquals(r.incidents.size, text.count { it.startsWith("You run wide") || it.startsWith("You spin") || it.startsWith("You hit the wall") }) }
            assertTrue(r.log.events.all { it.lap in 1..r.circuit.laps })
        }
        assertTrue(sawOut && sawIncident)
    }

    @Test fun aRivalWhoStopsIsOutOfTheOrder() {
        var seen = false
        for (seed in 1L..800L) {
            val r = race("hungaroring", seed)
            val out = r.grid.filter { it.out && !it.you }
            if (out.isNotEmpty()) {
                seen = true
                val last = r.log.rows.last()
                out.forEach { f -> assertTrue(last.order.first { it.name == f.name }.out) }
                assertTrue(r.log.events.any { it.text.contains(" is out.") })
                break
            }
        }
        assertTrue(seen)
    }

    @Test fun fastestLapIsOnTheLog() {
        val r = race("silverstone", 9L)
        assertTrue(r.log.fastestLap > 0.0)
        assertTrue(r.log.events.any { it.text.startsWith("Fastest lap") })
        assertTrue(r.log.fastestLap <= r.bestLap + 1e-9 || r.retired != null)
    }

    @Test fun linesReadOnePerLap() {
        val r = race("monza", 3L)
        val lines = RaceLog.lines(r.log)
        assertEquals(r.circuit.laps, lines.size)
        assertTrue(lines.first().startsWith("L1"))
        lines.forEach { assertFalse(it.contains("NaN")) }
        assertEquals(0, RaceLog.lines(RaceLog.Log.EMPTY).size)
    }
}
