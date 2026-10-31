package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import com.revline.idle.game.Parts
import com.revline.idle.game.Racing
import com.revline.idle.model.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dollar numbers: the stake, the circuit money that hangs off it, and the circuit part prices. */
class CircuitMoneyTest {
    private fun scale(p: Int) = Block.forPrestige(p).upgradePriceScale(p)

    @Test fun theFirstStakeIsTwoHundredAndEachRebuildFollowsTheShopPriceScale() {
        assertEquals(200.0, Racing.stakeCost(0), 0.0)
        for (p in 1..7) {
            assertTrue("stake rises at $p", Racing.stakeCost(p) > Racing.stakeCost(p - 1))
            val ratio = Racing.stakeCost(p) / (200.0 * scale(p) / scale(0))
            assertEquals("stake $p follows the price scale", 1.0, ratio, 0.03)
        }
    }

    @Test fun aStakeWinPaysDoubleAndTheDriverFeeIsHisShareOfThatPurse() {
        for (p in listOf(0, 3, 7)) {
            val s = GameState(prestiges = p, money = 1.0e9)
            val (paid, field) = Racing.openStake(s, 5L)!!
            assertEquals(Racing.stakeCost(p), s.money - paid.money, 1e-6)
            assertEquals(2.0 * Racing.stakeCost(p), field.purse, 1e-6)
            val gus = Drivers.byId("gus")!!
            assertEquals(gus.rate * 2.0 * Racing.stakeCost(p), Drivers.fee(s, gus, field), 1e-6)
        }
    }

    @Test fun circuitMoneyIsHalfTheStakeNotTheStakeCutTwice() {
        for (p in 0..7) {
            val s = GameState(prestiges = p)
            assertEquals(0.5 * Racing.stakeCost(p), Circuits.entry(s), 1e-6)
            assertEquals(1.75 * Racing.stakeCost(p), Circuits.winnerPurse(s), 1e-6)
            listOf(1.75, 1.2, 0.9, 0.7, 0.5).zip(Circuits.podium(s)).forEach { (m, d) -> assertEquals(m * Racing.stakeCost(p), d, 1e-6) }
        }
        // At the first rebuild: $100 in; $350, $240, $180, $140 and $100 for the top five.
        assertEquals(100.0, Circuits.entry(GameState()), 0.0)
        assertEquals(listOf(350.0, 240.0, 180.0, 140.0, 100.0), Circuits.podium(GameState()).map { Math.round(it).toDouble() })
    }

    @Test fun circuitPartsAndTiresArePricedBelowTheirListAndKeepTheirOrder() {
        val s = GameState(prestiges = 7, money = 1.0e9, raceStudies = Racing.studies.map { it.id }.toSet())
        for (line in Parts.lines) {
            val prices = line.levels.indices.map { i -> 1.0e9 - Racing.fitPart(s, line.id, "${i + 1}").money }
            assertTrue("${line.id} $prices", prices.zipWithNext().all { (a, b) -> b > a })
            line.levels.forEachIndexed { i, lv -> assertEquals(lv.price * Racing.CIRCUIT_PRICE_FACTOR * scale(7), prices[i], 1e-6 * prices[i]) }
        }
        // A circuit tire costs about a fifth of its list price, a drag tire the full price.
        val sport = 1.0e9 - Racing.fitPart(s, "ctire", "sport").money
        val radial = 1.0e9 - Racing.fitPart(s, "tire", "race").money
        assertEquals(900.0 * 0.2 * scale(7), sport, 1e-6 * sport)
        assertEquals(1800.0 * scale(7), radial, 1e-6 * radial)
    }
}
