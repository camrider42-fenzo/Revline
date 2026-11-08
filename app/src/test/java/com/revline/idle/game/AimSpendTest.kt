package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The next-part line ranked by what it costs, not only by how far the stat moves. */
class AimSpendTest {
    private val offers = listOf(
        AimOffer("air", 40.0, 2.0, 12.0),
        AimOffer("jets", 80.0, 6.0, 16.0),
        AimOffer("cam", 100.0, 9.0, 19.0),
        AimOffer("header", 100.0, 11.0, 21.0),
        AimOffer("turbo", 500.0, 30.0, 40.0),
    )

    @Test fun bestIsTheBiggestStepAndATieGoesToTheCheaperPart() {
        val choice = AimSpends.choose(offers, money = 10.0, AimSpend.BEST, target = 50.0)
        assertEquals("turbo", choice.offer?.id)
        val tied = listOf(
            AimOffer("zeta", 40.0, 5.0, 15.0),
            AimOffer("alpha", 20.0, 5.0, 15.0),
            AimOffer("mid", 20.0, 5.0, 15.0),
        )
        assertEquals("alpha", AimSpends.choose(tied, 0.0, AimSpend.BEST, null).offer?.id)
    }

    @Test fun cheapestIsTheLowestPriceThatClearsTheTarget() {
        val choice = AimSpends.choose(offers, money = 0.0, AimSpend.CHEAPEST, target = 20.0)
        assertEquals("header", choice.offer?.id)
        assertTrue(choice.reaches)
        val samePrice = listOf(
            AimOffer("b", 50.0, 4.0, 20.0),
            AimOffer("a", 50.0, 6.0, 22.0),
        )
        assertEquals("a", AimSpends.choose(samePrice, 0.0, AimSpend.CHEAPEST, target = 20.0).offer?.id)
    }

    @Test fun cheapestStepsWhenNothingClearsTheTarget() {
        val choice = AimSpends.choose(offers, money = 1_000.0, AimSpend.CHEAPEST, target = 80.0)
        assertEquals("air", choice.offer?.id)
        assertTrue(choice.short)
        assertFalse(choice.reaches)
    }

    @Test fun affordableTakesTheBestResultTheCashCanBuy() {
        val choice = AimSpends.choose(offers, money = 100.0, AimSpend.AFFORDABLE, target = 50.0)
        assertEquals("header", choice.offer?.id)
        assertTrue(choice.short)
        val clearing = AimSpends.choose(offers, money = 500.0, AimSpend.AFFORDABLE, target = 40.0)
        assertEquals("turbo", clearing.offer?.id)
        assertTrue(clearing.reaches)
    }

    @Test fun affordableWithNoPurchaseSaysSo() {
        val choice = AimSpends.choose(offers, money = 10.0, AimSpend.AFFORDABLE, target = 20.0)
        assertNull(choice.offer)
    }

    @Test fun aMetTargetIsAlreadyDone() {
        assertTrue(AimSpends.met(40.0, 40.0))
        assertTrue(AimSpends.met(39.96, 40.0))
        assertFalse(AimSpends.met(39.94, 40.0))
        assertFalse(AimSpends.met(1.0, null))
    }

    @Test fun almostKeepsTheBandAndDropsWhatTheCashAlreadyCovers() {
        assertTrue("exactly 80% is in", AimSpends.almost(80.0, 100.0))
        assertFalse("a hair under 80% is out", AimSpends.almost(79.999, 100.0))
        assertFalse("already affordable is out", AimSpends.almost(100.0, 100.0))
        assertFalse("well under is out", AimSpends.almost(79.0, 100.0))
        assertTrue("the far edge of the band is in", AimSpends.almost(100.0, 125.0))
        assertFalse("past 20% over is out", AimSpends.almost(100.0, 125.1))
        assertFalse("a free part is affordable, not almost", AimSpends.almost(50.0, 0.0))

        val band = AimSpends.choose(offers, money = 80.0, AimSpend.ALMOST, target = 50.0)
        assertEquals("header", band.offer?.id)
        assertTrue(band.short)
        val onlyAffordable = AimSpends.choose(
            listOf(AimOffer("air", 40.0, 9.0, 19.0)),
            money = 80.0,
            AimSpend.ALMOST,
            target = 30.0,
        )
        assertNull(onlyAffordable.offer)
    }

    @Test fun bestOnAFreshBlockMatchesTheOldBiggestStep() {
        val state = GameState(prestiges = Block.INLINE_FOUR.ordinal, throttle = 0.55, money = 15.0)
        val plain = SheetAim.recommend(state, AimGoal.POWER)
        val best = SheetAim.recommend(state, AimGoal.POWER, AimSpend.BEST)
        assertEquals(plain.upgradeId, best.upgradeId)
        assertEquals(plain.detail, best.detail)
        val id = best.upgradeId
        assertTrue(id != null)
        val picked = step(state, id!!)
        for (upgrade in Upgrades.all) {
            if (upgrade.id == id) continue
            if (Logic.lockReason(state, upgrade.id) != null) continue
            assertTrue(step(state, upgrade.id) <= picked + 1e-6)
        }
    }

    @Test fun affordableOnAnEmptyTillNamesNoPart() {
        val state = GameState(prestiges = Block.INLINE_FOUR.ordinal, money = 0.0)
        val aim = SheetAim.recommend(state, AimGoal.POWER, AimSpend.AFFORDABLE)
        assertNull(aim.upgradeId)
        assertTrue(aim.detail.contains("affordable"))
    }

    @Test fun anOldSaveOpensOnTheBiggestStep() {
        val loaded = SaveCodec.decode("""{"money":12.0,"prestiges":4}""")
        assertEquals("BEST", loaded.aimSpend)
        assertEquals(AimSpend.BEST, AimSpend.from(loaded.aimSpend))
        val round = SaveCodec.decode(SaveCodec.encode(loaded.copy(aimSpend = "ALMOST")))
        assertEquals("ALMOST", round.aimSpend)
        assertEquals(AimSpend.BEST, AimSpend.from("no-such-mode"))
    }

    private fun step(state: GameState, id: String): Double {
        val before = EngineSim.evaluate(Logic.paramsFrom(state)).peakHorsepower
        val next = state.copy(upgrades = state.upgrades + (id to ((state.upgrades[id] ?: 0) + 1)))
        val after = EngineSim.evaluate(Logic.paramsFrom(next)).peakHorsepower
        return after - before
    }
}
