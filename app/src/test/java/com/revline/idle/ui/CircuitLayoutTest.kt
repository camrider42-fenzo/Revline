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
            assertTrue(l.minX >= -1f && l.minY >= -1f && l.maxX <= CircuitLayouts.BOX_W + 1f && l.maxY <= l.boxH + 1f)
            assertTrue("${c.id} too short", l.length > 150f)
        }
    }

    @Test fun onlySuzukaCrossesItself() {
        for (c in Circuits.all) {
            val bridge = CircuitLayouts.of(c.id).bridgeArc >= 0f
            assertEquals(c.id, c.id == "suzuka", bridge)
        }
    }

    @Test fun roadRarelyRunsIntoItself() {
        // The drawn road is wider than the real one, so where two parts of a real track really are close it can touch. That is a few percent of the lap at most.
        for (c in Circuits.all) {
            val g = CircuitGeo(936f, 936f / CircuitLayouts.aspect(c.id), c.id)
            val share = g.line.tightShare(g.roadUnits * 1.05f)
            assertTrue("${c.id}: ${(share * 100).toInt()}% of the lap is closer than a road width", share < (if (c.id == "suzuka") 0.12f else 0.06f))
        }
    }

    @Test fun twoCarsFitInTheirLanesWithRoomBetween() {
        for (c in Circuits.all) {
            val g = CircuitGeo(936f, 936f / CircuitLayouts.aspect(c.id), c.id)
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

    @Test fun gridHasASlotForEveryCarAndFollowsTheRoadBack() {
        assertEquals(5, GRID_ROWS)
        assertTrue(GRID_ROWS * 2 >= Circuits.FIELD + 1)
        for (c in Circuits.all) {
            val g = CircuitGeo(936f, 936f / CircuitLayouts.aspect(c.id), c.id)
            val back = -gridArc(g, GRID_ROWS - 1, -1) + g.carUnits * 0.6f
            // The last slot is still well inside the lap and clear of the first corner ahead of the line.
            assertTrue("${c.id}: grid is ${back} units long", back < g.line.length * 0.2f)
            val last = g.line.cornerArc.last()
            assertTrue("${c.id}: grid runs into the corner before the line", g.line.length - last > 0f)
        }
    }

    @Test fun silverstoneStartLineSitsWellAlongTheHamiltonStraightAndTheGridRunsBackThroughClub() {
        val l = CircuitLayouts.of("silverstone")
        val g = CircuitGeo(936f, 936f / l.aspect, "silverstone")
        val clubToLineM = (l.length - l.cornerArc.last()) * l.metersPerUnit
        assertTrue("Club to the line is $clubToLineM m", clubToLineM in 180f..300f)
        val toAbbeyM = l.cornerArc[0] * l.metersPerUnit
        assertTrue("line to Abbey is $toAbbeyM m", toAbbeyM in 250f..450f)
        assertEquals(150f, l.shiftUnits * l.metersPerUnit, 10f)
        // The last row stands behind Club's apex, on the bend, not on the straight.
        val lastRowM = -gridArc(g, GRID_ROWS - 1, 1) * l.metersPerUnit
        assertTrue("grid is $lastRowM m deep", lastRowM > clubToLineM)
        // Boxes turn with the road: the front and back row headings differ by the bend they stand on.
        val turn = abs(CircuitLine.angleDiff(g.heading(gridArc(g, 0, 1)), g.heading(gridArc(g, GRID_ROWS - 1, 1))))
        assertTrue("grid bends only $turn rad", turn > 0.3f)
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
            assertEquals(1.0, rp.speed, 0.0)
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

    @Test fun theWholeFieldIsOnTheTrackEachCarItsOwn() {
        for (id in listOf("monza", "spa", "silverstone")) for (seed in 1L..6L) {
            val r = race(id, seed)
            val rp = Replay.build(r)!!
            val names = r.log.rows.first().order.filter { !it.you }.map { it.name }.toSet()
            assertEquals("$id $seed", names, rp.rivals.map { it.name }.toSet())
            assertEquals(names.size + 1, rp.all.size)
            assertTrue(rp.rivals.contains(rp.rival))
            assertTrue(rp.all.last().you)
            // Each rival has its own traces, lap times from the log and a livery nobody else has.
            for (c in rp.rivals) {
                assertNotNull("${c.name} traces", c.runs)
                for (row in r.log.rows) {
                    val me = row.order.first { it.you }
                    val him = row.order.firstOrNull { it.name == c.name } ?: continue
                    if (me.gap == null || him.gap == null || row.lap > c.completed || row.lap >= rp.you.cum.size) continue
                    assertEquals(him.gap!! - me.gap!!, c.cum[row.lap] - rp.you.cum[row.lap], 1e-6)
                }
            }
            val colours = rp.rivals.map { ReplayLook.livery(it.name) }.toSet() + ReplayLook.lime
            assertEquals(rp.all.size, colours.size)
            assertEquals(rp.all.size, rp.all.map { it.slot }.toSet().size)
            // Everyone with a lap has a best-lap trace at the flag.
            assertEquals(rp.all.count { it.completed > 0 }, rp.bestLaps().size)
            assertEquals(1, rp.bestLaps().count { it.you })
        }
    }

    @Test fun everyCarStartsInItsOwnGridSlotAndTheMapDrawsThemAll() {
        val r = race("monza", 3L)
        val rp = Replay.build(r)!!
        val g = CircuitGeo(936f, 936f / CircuitLayouts.aspect("monza"), "monza")
        val cars = ReplayLook.cars(g, rp, 0.0, "p911", false)
        assertEquals(rp.all.size, cars.size)
        assertEquals(rp.all.size, cars.map { it.color }.toSet().size)
        assertEquals(ReplayLook.lime, cars.last().color)
        // Nobody shares a spot on the grid, and each row has a car in each lane.
        assertEquals(cars.size, cars.map { it.s to it.lat }.toSet().size)
        for (c in cars) assertTrue(abs(c.lat) > g.laneOffset * 0.9f)
        assertTrue(cars.any { it.lat > 0f } && cars.any { it.lat < 0f })
    }

    @Test fun carsNeverOverlapWhenAbreast() {
        val g = CircuitGeo(936f, 936f / CircuitLayouts.aspect("monza"), "monza")
        val r = race("monza", 3L)
        val rp = Replay.build(r)!!
        val keeper = LaneKeeper(rp)
        var t = 0.0
        while (t < rp.duration) {
            val cars = keeper.cars(g, t, "p911", false)
            for (i in cars.indices) for (j in i + 1 until cars.size) {
                val a = g.point(cars[i].s, cars[i].lat)
                val b = g.point(cars[j].s, cars[j].lat)
                val d = hypot(a.x - b.x, a.y - b.y)
                val third = cars.indices.any { k -> k != i && k != j && (abs(cars[k].s - cars[i].s) < g.carUnits * 3 || abs(cars[k].s - cars[j].s) < g.carUnits * 3) }
                // Level with each other and nobody else near: the lanes keep them a body apart across the road.
                if (!third && abs(cars[i].s - cars[j].s) < g.carUnits * 0.5f) assertTrue("t=$t d=$d", d > g.laneOffset * 1.2f)
            }
            t += 0.25
        }
    }

    private fun linear(name: String, you: Boolean, lap: Double, ahead: Double, slot: Int) =
        ReplayCar(name, "", you, DoubleArray(5) { it * lap - ahead * lap }.also { it[0] = 0.0 }, false, null, false, slot)

    @Test fun carsRunningLevelKeepTheirLanesAndMoveSmoothly() {
        val g = CircuitGeo(936f, 936f / CircuitLayouts.aspect("monza"), "monza")
        // Level for the whole stretch, and a rival who creeps past: neither pair may ever change sides.
        for (pace in listOf(1.0, 0.9985, 0.97)) {
            val you = linear("you", true, 100.0, 0.0, 1)
            val him = linear("Him", false, 100.0 * pace, 0.0, 2)
            val rp = Replay(you, him, 4, 400.0, false)
            val keeper = LaneKeeper(rp)
            var prev: FloatArray? = null
            var t = 0.0
            var level = 0
            while (t < 380.0) {
                val cars = keeper.cars(g, t, "p911", false)
                val lat = floatArrayOf(cars[0].lat, cars[1].lat)
                prev?.let { p ->
                    // A glide: nobody moves a third of a lane in a tenth of a second, so there is no flip.
                    for (k in 0..1) assertTrue("t=$t k=$k ${p[k]} -> ${lat[k]}", abs(lat[k] - p[k]) < g.laneOffset * 0.3f)
                }
                if (t > 15.0 && abs(cars[0].s - cars[1].s) < g.carUnits * 3f) {
                    level++
                    assertTrue("t=$t same side", lat[0] * lat[1] < 0f)
                    assertTrue("t=$t you swapped", cars[1].lat > 0f)
                    assertTrue("t=$t he swapped", cars[0].lat < 0f)
                }
                prev = lat
                t += 0.1
            }
            if (pace > 0.99) assertTrue("level for $level steps at pace $pace", level > 100)
        }
    }

    private val official = mapOf(
        "monza" to 5.793, "monaco" to 3.337, "silverstone" to 5.891, "spa" to 7.004,
        "hungaroring" to 4.381, "suzuka" to 5.807, "montreal" to 4.361, "singapore" to 4.940,
    )

    @Test fun trueLengthMatchesTheOfficialLapWithinTwoPercent() {
        for ((id, km) in official) {
            val l = CircuitLayouts.of(id)
            val err = l.lengthM / (km * 1000.0) - 1.0
            assertTrue("$id is ${"%.0f".format(l.lengthM)} m against ${km * 1000} m (${"%.2f".format(err * 100)}%)", abs(err) <= 0.02)
            assertEquals(km, Circuits.all.first { it.id == id }.lapKm, 1e-9)
            assertEquals(km, l.officialKm, 1e-9)
            // The layout units and the metres agree.
            assertEquals(l.lengthM, l.length.toDouble() * l.metersPerUnit, 1.0)
        }
    }

    @Test fun firstTurnIsOnTheRightSideAndAfterTheStartLine() {
        val first = mapOf("monza" to 1, "monaco" to 1, "silverstone" to 1, "spa" to 1, "hungaroring" to 1, "suzuka" to 1, "montreal" to -1, "singapore" to -1)
        for ((id, side) in first) {
            val l = CircuitLayouts.of(id)
            assertEquals("$id turn 1 side", side, l.cornerSide[0])
            val m = l.cornerArc[0] * l.metersPerUnit
            assertTrue("$id turn 1 is $m m after the line", m in 150f..1000f)
            for (k in 1 until l.corners) assertTrue("$id corner order", l.cornerArc[k] > l.cornerArc[k - 1])
        }
    }

    @Test fun cornerSidesMatchTheBendsOfTheLine() {
        for (c in Circuits.all) {
            val l = CircuitLayouts.of(c.id)
            var agree = 0
            val a = FloatArray(3)
            val b = FloatArray(3)
            for (k in 0 until l.corners) {
                val s = l.cornerArc[k]
                // The turn is the heading change over the 40 m around the marked point.
                val half = 20f / l.metersPerUnit
                l.at(s - half, a); l.at(s + half, b)
                if (CircuitLine.angleDiff(a[2], b[2]) * l.cornerSide[k] > 0f) agree++
            }
            assertTrue("${c.id}: only $agree of ${l.corners} marked corners bend the way they are listed", agree >= l.corners * 0.85)
        }
    }

    @Test fun carsDriveTheRightWayRound() {
        // Total turning over a lap, in turns: clockwise on the page is positive. Suzuka's figure eight cancels out.
        val expect = mapOf("monza" to 1, "monaco" to 1, "silverstone" to 1, "spa" to 1, "hungaroring" to 1, "suzuka" to 0, "montreal" to 1, "singapore" to -1)
        for ((id, dir) in expect) {
            val l = CircuitLayouts.of(id)
            var total = 0f
            val a = FloatArray(3)
            val b = FloatArray(3)
            var s = 0f
            while (s < l.length) {
                l.at(s, a); l.at(s + 1f, b)
                total += CircuitLine.angleDiff(a[2], b[2])
                s += 1f
            }
            val turns = total / (2f * Math.PI.toFloat())
            if (dir == 0) assertTrue("$id turns $turns", abs(turns) < 0.3f) else assertTrue("$id turns $turns", turns * dir > 0.7f)
        }
    }

    @Test fun roadIsDrawnWiderThanLifeAndSaysSo() {
        // About 12 m is a real road; the drawing is several times that so two cars fit side by side.
        for (c in Circuits.all) {
            val g = CircuitGeo(936f, 936f / CircuitLayouts.aspect(c.id), c.id)
            val drawnM = g.roadUnits * g.line.metersPerUnit
            assertTrue("${c.id} road ${drawnM} m", drawnM in 15f..90f)
        }
    }

    @Test fun playbackProgressScalesWithTheChosenSpeed() {
        val replay = Replay.build(race("silverstone", 7L))!!
        val ticks = 600 // ten real seconds at 60 frames a second
        fun after(index: Int, playing: Boolean = true): Float {
            var clock = 0f
            repeat(ticks) { clock = ReplaySpeed.advance(clock, 1f / 60f, playing, ReplaySpeed.multiplier(index, replay)) }
            return clock
        }
        val base = after(ReplaySpeed.DEFAULT)
        assertEquals(10f, base, 1e-2f)
        assertEquals(base * 0.5f, after(0), 1e-2f)
        assertEquals(base * 2f, after(2), 1e-2f)
        assertEquals(base * 4f, after(3), 1e-2f)
        assertEquals(base * 8f, after(4), 1e-2f)
        // Paused films do not move.
        assertEquals(0f, after(4, playing = false), 0f)
        // Race progress at 8x is eight times as far along as at 1x, until the end.
        val at1 = ReplaySpeed.progress(base, replay)
        val at8 = ReplaySpeed.progress(after(4), replay)
        assertTrue(at1 > 0f)
        assertEquals(minOf(1f, at1 * 8f), at8, 1e-3f)
        // Time left shrinks in proportion, and 1x is the race at the pace it was run: a lap takes its lap time in real seconds.
        val end = ReplaySpeed.endClock(replay)
        assertEquals(ReplaySpeed.secondsLeft(0f, replay, 1.0) / 4.0, ReplaySpeed.secondsLeft(0f, replay, 4.0), 1e-6)
        assertEquals(replay.duration, ReplaySpeed.secondsLeft(0f, replay, ReplaySpeed.multiplier(ReplaySpeed.DEFAULT, replay)), 1e-3)
        assertEquals(replay.duration, end * replay.speed, 1e-3)
        assertEquals(1.0, replay.speed, 0.0)
        val lap = replay.you.cum[2] - replay.you.cum[1]
        assertEquals(lap, (replay.you.cum[2] - replay.you.cum[1]) / replay.speed / ReplaySpeed.multiplier(ReplaySpeed.DEFAULT, replay), 1e-9)
        val b = replay.you.bestLap()!!
        assertEquals((1..replay.you.completed).minOf { replay.you.cum[it] - replay.you.cum[it - 1] }, b.second.span, 1e-6)
        assertEquals(ReplaySpeed.labels.size, 5)
    }
}
