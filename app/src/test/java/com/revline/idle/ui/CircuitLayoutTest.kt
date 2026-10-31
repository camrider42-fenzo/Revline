package com.revline.idle.ui

import com.revline.idle.economy.DriverBuilds
import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import com.revline.idle.game.Racing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class CircuitLayoutTest {
    private fun race(id: String, seed: Long): Circuits.Result {
        val c = Circuits.all.first { it.id == id }
        val d = Drivers.byId("colette")!!
        val car = Circuits.carOf(DriverBuilds.make(3, Racing.Build.MEDIUM, "race", "close", "lsd", 0.8))
        return Circuits.race(Circuits.plan(car, c, d), d, Circuits.parPlan(3, c), seed)
    }

    @Test fun everyCircuitHasALayoutWithItsOwnCornerCount() {
        for (c in Circuits.all) {
            assertTrue("${c.id} has no layout", CircuitLayouts.has(c.id))
            assertEquals("${c.id} corners", c.corners, CircuitLayouts.of(c.id).corners)
        }
    }

    @Test fun layoutsAreClosedLoopsInsideTheBox() {
        for (c in Circuits.all) {
            val l = CircuitLayouts.of(c.id)
            val gap = hypot(l.xs[0] - l.xs[l.xs.size - 1], l.ys[0] - l.ys[l.ys.size - 1])
            assertTrue("${c.id} loop gap $gap", gap < l.step * 1.5f)
            assertTrue(l.minX >= 0f && l.minY >= 0f && l.maxX <= CircuitLayouts.BOX_W && l.maxY <= CircuitLayouts.BOX_H)
            assertTrue("${c.id} too short", l.length > 150f)
        }
    }

    @Test fun onlySuzukaCrossesItself() {
        for (c in Circuits.all) {
            val bridge = CircuitLayouts.of(c.id).bridgeArc >= 0f
            assertEquals(c.id, c.id == "suzuka", bridge)
        }
    }

    @Test fun roadStaysClearOfItselfSoTwoCarsFit() {
        val geo = { id: String -> CircuitGeo(936f, 580f, id) }
        for (c in Circuits.all) {
            val g = geo(c.id)
            val clear = g.line.minClearance()
            assertTrue("${c.id}: clearance $clear vs road ${g.roadUnits}", clear >= g.roadUnits * 1.35f)
        }
    }

    @Test fun twoCarsFitInTheirLanesWithRoomBetween() {
        for (c in Circuits.all) {
            val g = CircuitGeo(936f, 580f, c.id)
            for (id in DragLooks.ids) {
                val plan = TopPlan.of(id, false)
                assertTrue("$id plan", plan.length > 0f && plan.width > 0f)
                val widthPx = g.carLen * plan.width / plan.length
                // Car plus its wheels, in each lane, and still a gap between them and to the edge line.
                val across = widthPx * 1.25f
                assertTrue("$id is $across px wide, lane ${g.laneOffset}", across < g.laneOffset * 2f * 1.05f)
                assertTrue(g.laneOffset + across / 2f < g.roadW / 2f)
            }
        }
    }

    @Test fun startLineHasGridBehindItOnAStraight() {
        for (c in Circuits.all) {
            val g = CircuitGeo(936f, 580f, c.id)
            assertTrue("${c.id} straight ${g.line.startStraight}", g.line.startStraight.start < -g.carUnits * 2f)
        }
    }

    @Test fun replayProgressRunsThroughEveryLapAndStopsWhenOut() {
        val finisher = ReplayCar("a", "t", false, doubleArrayOf(0.0, 90.0, 180.0, 270.0), false)
        assertEquals(0.0, finisher.progress(-5.0), 1e-9)
        assertEquals(0.5, finisher.progress(45.0), 1e-9)
        assertEquals(2.25, finisher.progress(202.5), 1e-9)
        assertEquals(3.0, finisher.progress(270.0), 1e-9)
        assertEquals(3.5, finisher.progress(315.0), 1e-9)
        val dead = ReplayCar("b", "t", false, doubleArrayOf(0.0, 90.0, 180.0), true)
        assertEquals(2.4, dead.progress(10_000.0), 1e-9)
    }

    @Test fun replayCarsComeFromTheLogAndAgreeWithTheResult() {
        for (id in listOf("monza", "spa", "monaco")) for (seed in 1L..12L) {
            val r = race(id, seed)
            val rp = Replay.build(r)
            assertNotNull("$id $seed", rp)
            rp!!
            assertTrue(rp.you.you && !rp.rival.you)
            assertEquals(r.lapTimes.sum(), rp.you.cum.last(), 1e-6)
            // At the end of each lap the rival's time is the player's plus the gap the log gives between them.
            for (row in r.log.rows) {
                val me = row.order.first { it.you }
                val him = row.order.firstOrNull { it.name == rp.rival.name } ?: continue
                if (me.gap == null || him.gap == null || row.lap > rp.rival.completed || row.lap >= rp.you.cum.size) continue
                assertEquals(him.gap!! - me.gap!!, rp.rival.cum[row.lap] - rp.you.cum[row.lap], 1e-6)
            }
            assertTrue(rp.duration > rp.you.cum.last())
            assertTrue(rp.speed >= 1.0)
        }
    }

    @Test fun theRivalIsAlwaysCloseAndNeverMissing() {
        var closest = 0
        for (seed in 1L..20L) {
            val r = race("silverstone", seed)
            val rp = Replay.build(r)!!
            val n = minOf(rp.you.completed, rp.rival.completed)
            var mean = 0.0
            for (lap in 1..n) mean += abs(rp.rival.cum[lap] - rp.you.cum[lap])
            mean /= n
            if (mean < 20.0) closest++
        }
        assertTrue("rivals were close in $closest of 20", closest >= 15)
    }

    @Test fun carsStartSideBySideOnTheFrontRow() {
        val r = race("monza", 3L)
        val rp = Replay.build(r)!!
        val g = CircuitGeo(936f, 580f, "monza")
        val cars = ReplayLook.cars(g, rp, 0.0, "p911", false)
        assertEquals(2, cars.size)
        assertEquals(cars[0].s.toDouble(), cars[1].s.toDouble(), g.carUnits * 0.4)
        assertTrue(cars[0].lat * cars[1].lat < 0f)
    }

    @Test fun carsNeverOverlapWhenAbreast() {
        val g = CircuitGeo(936f, 580f, "monza")
        val r = race("monza", 3L)
        val rp = Replay.build(r)!!
        var t = 0.0
        while (t < rp.duration) {
            val cars = ReplayLook.cars(g, rp, t, "p911", false)
            val a = g.point(cars[0].s, cars[0].lat)
            val b = g.point(cars[1].s, cars[1].lat)
            val d = hypot(a.x - b.x, a.y - b.y)
            // Same lap position within a car's width of each other: the lanes must keep them a body apart across the road.
            if (abs(cars[0].s - cars[1].s) < g.carUnits * 0.5f) assertTrue("t=$t d=$d", d > g.laneOffset * 1.5f)
            t += 1.0
        }
    }
}
