package com.revline.idle.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverStatsTest {
    private fun d(id: String) = Drivers.byId(id)!!

    @Test fun everyStatIsOnAHundredScale() {
        (Drivers.roster + Drivers.you).forEach {
            listOf(it.throttle, it.clutch, it.shifting, it.dyno, it.cornering, it.braking, it.acceleration, it.mistakes, it.care, it.overall)
                .forEach { v -> assertTrue("${it.name} $v", v in 1..100) }
        }
    }

    @Test fun idsAreUnchanged() {
        assertEquals(
            listOf("dusty", "mina", "rusty", "pavel", "ines", "tanaka", "gus", "colette", "viktor", "ace").toSet(),
            Drivers.roster.map { it.id }.toSet(),
        )
    }

    @Test fun overallIsTheDocumentedWeightedMean() {
        val w = Drivers.OVERALL_WEIGHTS
        assertEquals(1.0, w.values.sum(), 1e-9)
        Drivers.roster.forEach {
            val expect = 0.18 * it.cornering + 0.15 * (100 - it.mistakes) + 0.13 * it.braking + 0.12 * it.acceleration +
                0.10 * it.throttle + 0.10 * it.care + 0.09 * it.shifting + 0.07 * it.dyno + 0.06 * it.clutch
            assertEquals(it.name, Math.round(expect).toInt(), it.overall)
        }
    }

    @Test fun lowerMistakesRaisesOverall() {
        val a = d("ace")
        assertTrue(a.copy(mistakes = a.mistakes - 20).overall > a.overall)
        assertTrue(a.copy(care = a.care + 10).overall > a.overall)
        assertTrue(a.copy(cornering = a.cornering + 10).overall > a.overall)
    }

    @Test fun overallRisesWithTierAndFee() {
        val byTier = Drivers.roster.groupBy { it.tier }.mapValues { (_, v) -> v.map { it.overall }.average() }
        assertTrue(byTier.getValue(1) < byTier.getValue(2))
        assertTrue(byTier.getValue(2) < byTier.getValue(3))
        // Fee and rating move together across the whole roster.
        val ds = Drivers.roster
        val mean = ds.map { it.overall.toDouble() }.average(); val rm = ds.map { it.rate }.average()
        val cov = ds.sumOf { (it.overall - mean) * (it.rate - rm) }
        val corr = cov / Math.sqrt(ds.sumOf { (it.overall - mean) * (it.overall - mean) } * ds.sumOf { (it.rate - rm) * (it.rate - rm) })
        assertTrue("corr $corr", corr > 0.8)
    }

    @Test fun theNamedCharactersAreWhoTheyShouldBe() {
        val prost = d("colette"); val senna = d("viktor"); val hunt = d("tanaka"); val lauda = d("pavel"); val gilles = d("ines")
        val care = Drivers.roster.sortedByDescending { it.care }
        assertEquals(prost, care.first())
        assertTrue(prost.mistakes <= Drivers.roster.minOf { it.mistakes })
        assertTrue(lauda.care >= 70 && lauda.mistakes <= 30)
        assertEquals(senna, Drivers.roster.maxByOrNull { it.cornering })
        assertEquals(senna, Drivers.roster.maxByOrNull { it.acceleration })
        val mean = Drivers.roster.map { it.mistakes }.average()
        assertTrue(hunt.mistakes > mean + 10)
        assertTrue(gilles.mistakes > mean + 10)
        assertTrue(gilles.cornering > 65 && gilles.acceleration > 60)
    }

    @Test fun youAreMidField() {
        val you = Drivers.you
        listOf(you.cornering, you.braking, you.acceleration, you.mistakes, you.care, you.throttle, you.shifting).forEach { assertEquals(50, it) }
        assertEquals(0.0, you.rate, 0.0)
    }
}
