package com.revline.idle.game

import com.revline.idle.economy.DriverBuilds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualifyingTest {
    private val P = 3
    private fun build(share: Double, tire: String) = Circuits.carOf(DriverBuilds.make(P, Racing.Build.MEDIUM, tire, "close", "lsd", share))
    private fun race(id: String, seed: Long, car: Circuits.Car = build(0.8, "race"), driver: String = "colette"): Circuits.Result {
        val c = Circuits.all.first { it.id == id }
        val d = Drivers.byId(driver)!!
        return Circuits.race(Circuits.plan(car, c, d), d, Circuits.parPlan(P, c), seed)
    }

    @Test fun theGridIsEveryCarOnceInTimeOrder() {
        for (seed in 1L..50L) {
            val q = race("monza", seed).qualifying
            assertEquals(Circuits.FIELD, q.rows.size)
            assertEquals((1..Circuits.FIELD).toList(), q.rows.map { it.slot })
            assertEquals(q.rows.map { it.time }.sorted(), q.rows.map { it.time })
            assertEquals(1, q.rows.count { it.you })
            assertEquals(Circuits.FIELD, q.rows.map { it.name }.toSet().size)
        }
    }

    @Test fun sameSeedSameGrid() {
        val a = race("suzuka", 5L).qualifying; val b = race("suzuka", 5L).qualifying
        assertEquals(a, b)
        assertTrue((1L..30L).map { race("suzuka", it).qualifying.you!!.time }.toSet().size > 25)
    }

    @Test fun gridOffsetsAverageOutAndCostMoreWhereMistakesAreWalled() {
        for (c in Circuits.all) {
            assertEquals(0.0, (1..Circuits.FIELD).sumOf { Circuits.gridOffset(it, c) }, 1e-9)
            assertTrue(Circuits.gridOffset(1, c) < 0.0 && Circuits.gridOffset(Circuits.FIELD, c) > 0.0)
        }
        val monaco = Circuits.all.first { it.id == "monaco" }; val spa = Circuits.all.first { it.id == "spa" }
        assertTrue(Circuits.gridOffset(8, monaco) > Circuits.gridOffset(8, spa))
    }

    @Test fun aQuickerCarQualifiesHigher() {
        val fast = build(1.0, "racing"); val slow = build(0.2, "street")
        val a = (1L..120L).map { race("spa", it, fast).qualifying.you!!.slot }.average()
        val b = (1L..120L).map { race("spa", it, slow).qualifying.you!!.slot }.average()
        assertTrue("fast $a slow $b", a + 1.0 < b)
    }

    @Test fun theGridMovesTheStartAndTheLog() {
        val r = race("monaco", 8L)
        val me = r.qualifying.you!!
        assertTrue(r.log.rows.first().events.any { it.text.startsWith(if (me.slot == 1) "You start from pole" else "You start P${me.slot}") })
        assertEquals(Circuits.FIELD, Circuits.gridLines(r.qualifying).size)
        assertTrue(Circuits.gridLines(r.qualifying).first().endsWith("pole"))
        assertTrue(r.qualifying.gapToPole >= 0.0)
    }

    @Test fun poleIsWorthSomethingButNotTheRace() {
        // On a tight circuit the front of the grid is a real edge, and a slow car on pole is still caught by a fast one.
        val c = Circuits.all.first { it.id == "monaco" }
        val mid = build(0.6, "street")
        val sample = (1L..800L).map { race("monaco", it, mid) }.filter { it.retired == null }
        val pole = sample.filter { it.qualifying.you!!.slot <= 3 }.map { it.position }.average()
        val back = sample.filter { it.qualifying.you!!.slot >= 5 }.map { it.position }.average()
        assertTrue("pole $pole back $back", pole < back)
        assertTrue(c.punish > 1.0)
    }
}
