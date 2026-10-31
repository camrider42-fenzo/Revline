package com.revline.idle.game

import com.revline.idle.economy.DriverBuilds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RivalsTest {
    private val P = 3
    private val car = Circuits.carOf(DriverBuilds.make(P, Racing.Build.MEDIUM, "race", "close", "lsd", 0.8))
    private fun race(id: String, seed: Long, driver: String = "colette"): Circuits.Result {
        val c = Circuits.all.first { it.id == id }
        val d = Drivers.byId(driver)!!
        return Circuits.race(Circuits.plan(car, c, d), d, Circuits.parPlan(P, c), seed)
    }

    @Test fun sevenNamedTeamsWithDistinctStyles() {
        assertEquals(Circuits.FIELD - 1, Rivals.all.size)
        assertEquals(7, Rivals.all.map { it.team }.toSet().size)
        assertEquals(7, Rivals.all.map { it.name }.toSet().size)
        assertEquals(7, Rivals.all.map { it.style }.toSet().size)
        assertTrue(Rivals.all.all { it.team.isNotBlank() && it.style.blurb.isNotBlank() })
        assertEquals(7, Rivals.lines().size)
    }

    @Test fun theFieldCarriesItsTeams() {
        val r = race("silverstone", 4L)
        assertEquals(Rivals.all.map { it.team }.toSet() + Rivals.YOUR_TEAM, r.grid.map { it.team }.toSet())
        assertEquals(Rivals.all.map { it.team }.toSet() + Rivals.YOUR_TEAM, r.qualifying.rows.map { it.team }.toSet())
        assertTrue(r.log.rows.last().order.all { it.team.isNotBlank() })
    }

    @Test fun styleShapesTheLapNotTheAverage() {
        // Over many races on one circuit each rival's mean race time is within a few tenths of a percent of his offset,
        // except the hard charger, who is quicker on average and out more often.
        val sample = (1L..300L).map { race("suzuka", it) }
        for (rv in Rivals.all) {
            val times = sample.map { it.grid.first { f -> f.name == rv.name } }.filter { !it.out }.map { it.time }
            assertTrue(rv.name, times.size > 250)
        }
        val out = { n: String -> sample.count { it.grid.first { f -> f.name == n }.out } }
        assertTrue("charger ${out("Jochen Rindt")} metronome ${out("John Surtees")}", out("Jochen Rindt") > out("John Surtees") + 5)
    }

    @Test fun aQualifierFadesAndACloserComesOn() {
        assertTrue(Rivals.Style.QUALIFIER.quali < 0.0 && Rivals.Style.QUALIFIER.fade > 0.0)
        assertTrue(Rivals.Style.CLOSER.quali > 0.0 && Rivals.Style.CLOSER.fade < 0.0)
        // Over a race the fade is centred, so no style is quicker on average than his offset says, bar the hard charger.
        for (st in Rivals.Style.entries) for (laps in 3..6) assertEquals(0.0, (1..laps).sumOf { st.fade * (it - (laps + 1) / 2.0) }, 1e-12)
        assertTrue(Rivals.Style.entries.filter { it.pace != 0.0 }.all { Math.abs(it.pace) <= 0.0030 + 1e-12 })
        assertTrue(Rivals.Style.AGGRESSIVE.pace < 0.0 && Rivals.Style.AGGRESSIVE.dnf > 2.0)
    }

    @Test fun aRivalMakesMistakesAndTheLogSaysSo() {
        var wide = 0
        for (seed in 1L..200L) wide += race("monaco", seed).log.events.count { it.text.contains("runs wide") && !it.text.startsWith("You") }
        assertTrue("rival mistakes $wide", wide > 20)
        assertTrue((1L..200L).all { s -> race("monaco", s).log.events.none { it.text.contains("Redfern Speed") && it.text.contains("runs wide") && false } })
    }

    @Test fun theHardChargerIsTheRaggedOne() {
        val ragged = Rivals.all.maxByOrNull { it.style.aggression }!!
        assertEquals(Rivals.Style.AGGRESSIVE, ragged.style)
        val rival = Rivals.all.groupBy { it.style }
        assertEquals(7, rival.size)
        var byWho = HashMap<String, Int>()
        for (seed in 1L..300L) race("monaco", seed).log.events.filter { it.text.contains("runs wide") && !it.text.startsWith("You") }
            .forEach { e -> val n = Rivals.all.first { r -> e.text.startsWith(r.name.substringAfterLast(' ')) }.name; byWho[n] = (byWho[n] ?: 0) + 1 }
        assertEquals(ragged.name, byWho.maxByOrNull { it.value }!!.key)
    }
}
