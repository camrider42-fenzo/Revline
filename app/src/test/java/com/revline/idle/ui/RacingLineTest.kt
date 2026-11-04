package com.revline.idle.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Cars drive the racing line, step aside only while alongside, and the close-up follows only you. */
class RacingLineTest {
    private val g = CircuitGeo(936f, 936f / CircuitLayouts.aspect("monaco"), "monaco")

    private fun linear(name: String, you: Boolean, lap: Double, ahead: Double, slot: Int) =
        ReplayCar(name, "", you, DoubleArray(5) { it * lap - ahead * lap }.also { it[0] = 0.0 }, false, null, false, slot)

    @Test fun aLoneCarDrivesTheRacingLine() {
        val rp = Replay(linear("you", true, 100.0, 0.0, 1), linear("Him", false, 100.0, -0.5, 2), 4, 400.0, false)
        val keeper = LaneKeeper(rp)
        var t = 30.0
        while (t < 300.0) {
            val you = keeper.cars(g, t, "p911", false).last()
            assertEquals("t=$t", g.racingLat(you.s), you.lat, 1e-3f)
            t += 0.1
        }
    }

    @Test fun theRacingLineTakesTheInsideOfMarkedCorners() {
        val l = g.line
        val inside = (0 until l.corners).count { g.racingLat(l.cornerArc[it]) * l.cornerSide[it] > 0f }
        assertTrue("$inside of ${l.corners}", inside >= l.corners * 0.75)
    }

    @Test fun overlappingCarsSeparateAndReturnToTheLineAfterThePass() {
        val rp = Replay(linear("you", true, 100.0, 0.0, 1), linear("Him", false, 97.0, -0.03, 2), 4, 400.0, false)
        val keeper = LaneKeeper(rp)
        var t = 0.0
        var level = 0
        var back = 0
        val prev = FloatArray(2) { Float.NaN }
        while (t < 380.0) {
            val cars = keeper.cars(g, t, "p911", false)
            val gap = abs(cars[0].s - cars[1].s) % g.line.length
            for (k in 0..1) {
                if (!prev[k].isNaN()) assertTrue("t=$t smooth", abs(cars[k].lat - prev[k]) < g.laneOffset * 0.3f)
                prev[k] = cars[k].lat
            }
            if (t > 20.0 && gap < g.carUnits * 0.5f) {
                level++
                assertTrue("t=$t apart", abs(cars[0].lat - cars[1].lat) > g.laneOffset * 1.2f)
            }
            if (t > 20.0 && minOf(gap, g.line.length - gap) > g.carUnits * 12f) {
                back++
                for (c in cars) assertEquals("t=$t back on line", g.racingLat(c.s), c.lat, g.laneOffset * 0.05f)
            }
            t += 0.1
        }
        assertTrue("level $level", level > 0)
        assertTrue("back $back", back > 100)
    }

    @Test fun lateralPlaceIsSmoothFrameToFrameInARealRace() {
        val c = com.revline.idle.game.Circuits.all.first { it.id == "monaco" }
        val d = com.revline.idle.game.Drivers.byId("colette")!!
        val car = com.revline.idle.game.Circuits.carOf(com.revline.idle.economy.DriverBuilds.make(3, com.revline.idle.game.Racing.Build.MEDIUM, "race", "close", "lsd", 0.8))
        val rp = Replay.build(com.revline.idle.game.Circuits.race(com.revline.idle.game.Circuits.plan(car, c, d), d, com.revline.idle.game.Circuits.parPlan(3, c), 5L))!!
        val keeper = LaneKeeper(rp)
        var prev: List<CircuitCar>? = null
        var t = 0.0
        while (t < rp.duration * 0.5) {
            val cars = keeper.cars(g, t, "p911", false)
            prev?.let { p -> for (k in cars.indices) assertTrue("t=$t k=$k ${p[k].s} ${cars[k].s} ${p[k].lat} ${cars[k].lat} ${g.laneOffset} ${cars.map { it.s }}", abs(cars[k].lat - p[k].lat) < g.laneOffset * 0.12f) }
            prev = cars
            t += 1.0 / 60.0
        }
    }

    @Test fun theCloseUpCentersOnYouAlone() {
        val rp = Replay(linear("you", true, 100.0, 0.0, 1), linear("Him", false, 100.0, 0.01, 2), 4, 400.0, false)
        val keeper = LaneKeeper(rp)
        var t = 5.0
        while (t < 200.0) {
            val cars = keeper.cars(g, t, "p911", false)
            val you = cars.single { it.look == "p911" && it.color == ReplayLook.lime }
            val f = CircuitView.focus(g, cars)
            assertEquals(g.point(you.s, you.lat), f)
            assertEquals(f.x - 300f, CircuitView.window(f, 600f, 400f).x, 1e-3f)
            t += 0.5
        }
    }

    @Test fun speedChipsReachThirtyTwo() {
        assertEquals(listOf("0.5x", "1x", "2x", "4x", "8x", "16x", "32x"), ReplaySpeed.labels)
    }
}
