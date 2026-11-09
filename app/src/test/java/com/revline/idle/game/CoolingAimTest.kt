package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
import com.revline.idle.ui.tempEffectText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cooling is degrees under the tightest limit on the wide-open sweep.
 * On a V8 that limit is the oil. The four spend modes rank headroom under it.
 * The card prints the same sweep's change in coolant, oil, and exhaust.
 */
class CoolingAimTest {
    @Test fun anOldSaveKeepsItsGoalAndCoolingRoundTrips() {
        val old = GameState(aimGoal = "POWER", aimSpend = "CHEAPEST")
        val encoded = SaveCodec.encode(old)
        assertTrue(encoded.contains("\"aimGoal\": \"POWER\"") || encoded.contains("\"aimGoal\":\"POWER\""))
        assertEquals("POWER", SaveCodec.decodeOrNull(encoded)!!.aimGoal)
        assertEquals(AimGoal.POWER, AimGoal.from("POWER"))
        assertEquals(AimGoal.POWER, AimGoal.from("not-a-goal"))
        val cooling = old.copy(aimGoal = AimGoal.COOLING.name)
        assertEquals(AimGoal.COOLING.name, SaveCodec.decodeOrNull(SaveCodec.encode(cooling))!!.aimGoal)
    }

    @Test fun aCoolEngineIsAlreadyClear() {
        val state = GameState(
            prestiges = Block.INLINE_FOUR.ladderStep,
            blockId = Block.INLINE_FOUR.name,
            aimGoal = AimGoal.COOLING.name,
        )
        assertTrue("stock inline-four margin ${margin(state)}", margin(state) >= 0.0)
        val aim = SheetAim.recommend(state, AimGoal.COOLING, AimSpend.BEST)
        assertEquals("Clear", aim.headline)
        assertNull(aim.upgradeId)
        assertTrue(aim.detail.contains("inside their limits"))
    }

    @Test fun eachSpendModeRanksHeadroomUnderTheLimit() {
        val hot = v8(money = 1_000_000.0)
        val before = margin(hot)
        assertTrue("stock V8 should be over a limit, margin $before", before < -0.05)
        val open = offers(hot)
        assertTrue("an oil cooler should buy headroom", open.any { it.id == "oil_cooler" && it.gain > 0.5 })
        assertTrue("synthetic oil should buy headroom by moving the oil limit", open.any { it.id == "synthetic" && it.gain > 0.5 })
        // Almost affordable excludes anything the cash already covers, so a full till names nothing.
        for (mode in listOf(AimSpend.BEST, AimSpend.CHEAPEST, AimSpend.AFFORDABLE)) {
            val aim = SheetAim.recommend(hot, AimGoal.COOLING, mode)
            val expected = AimSpends.choose(open, hot.money, mode, 0.0).offer
            assertEquals(mode.name, expected?.id, aim.upgradeId)
            val picked = open.first { it.id == aim.upgradeId }
            assertTrue("${aim.upgradeId} should cool, gain ${picked.gain}", picked.gain > 0.0)
            assertTrue(aim.detail.contains("°"))
        }
        val best = open.first { it.id == SheetAim.recommend(hot, AimGoal.COOLING, AimSpend.BEST).upgradeId }
        assertTrue(best.gain + 1e-6 >= open.maxOf { it.gain })
        val clearing = open.filter { it.after >= -1e-9 }
        val pool = if (clearing.isNotEmpty()) clearing else open
        val cheapest = open.first { it.id == SheetAim.recommend(hot, AimGoal.COOLING, AimSpend.CHEAPEST).upgradeId }
        assertTrue("${cheapest.id} costs ${cheapest.cost}", cheapest.cost <= pool.minOf { it.cost } + 1e-6)
    }

    @Test fun affordableAndAlmostStayInsideTheirPriceBands() {
        val cooler = Logic.upgradeCost(v8(money = 0.0), "oil_cooler")
        val almostCash = cooler * 0.9
        val hot = v8(money = almostCash)
        val open = offers(hot)
        val affordable = SheetAim.recommend(hot, AimGoal.COOLING, AimSpend.AFFORDABLE)
        assertNotNull(affordable.upgradeId)
        val bought = open.first { it.id == affordable.upgradeId }
        assertTrue(AimSpends.covers(hot.money, bought.cost))
        val almost = SheetAim.recommend(hot, AimGoal.COOLING, AimSpend.ALMOST)
        assertEquals("oil_cooler", almost.upgradeId)
        assertTrue(AimSpends.almost(hot.money, open.first { it.id == "oil_cooler" }.cost))
        val broke = hot.copy(money = 0.0)
        val none = SheetAim.recommend(broke, AimGoal.COOLING, AimSpend.AFFORDABLE)
        assertNull(none.upgradeId)
        assertTrue(none.detail.contains("affordable"))
    }

    @Test fun theCardTemperatureIsTheWideOpenSweep() {
        val state = v8(money = 100_000.0)
        val cooler = Logic.preview(state, "oil_cooler")!!
        val coolerPeak = peakDelta(state, "oil_cooler")
        assertEquals(coolerPeak.coolant, cooler.peakCoolantC, 1e-6)
        assertEquals(coolerPeak.oil, cooler.peakOilC, 1e-6)
        assertEquals(coolerPeak.egt, cooler.peakEgtC, 1e-6)
        assertTrue("oil cooler ${cooler.peakOilC}", cooler.peakOilC < -0.5)
        assertEquals("${signed(cooler.peakOilC)}°C", tempEffectText(cooler.peakOilC))

        val radiator = Logic.preview(state, "radiator")!!
        assertEquals(peakDelta(state, "radiator").coolant, radiator.peakCoolantC, 1e-6)
        assertTrue("coolant is already on the thermostat, radiator ${radiator.peakCoolantC}", kotlin.math.abs(radiator.peakCoolantC) < 0.5)
        assertEquals("0°C", tempEffectText(radiator.peakCoolantC))

        val nitrous = Logic.preview(state, "nitrous")!!
        assertEquals(peakDelta(state, "nitrous").egt, nitrous.peakEgtC, 1e-6)
        assertEquals(peakDelta(state, "nitrous").coolant, nitrous.peakCoolantC, 1e-6)
        assertTrue("nitrous egt ${nitrous.peakEgtC} coolant ${nitrous.peakCoolantC}", nitrous.peakEgtC > 0.5 || nitrous.peakCoolantC > 0.5)
        val turbo = Logic.preview(state, "turbo")!!
        val blower = Logic.preview(state, "supercharger")!!
        assertEquals(peakDelta(state, "turbo").oil, turbo.peakOilC, 1e-6)
        assertEquals(peakDelta(state, "supercharger").oil, blower.peakOilC, 1e-6)
        assertEquals(tempEffectText(turbo.peakEgtC), "${signed(turbo.peakEgtC)}°C")
        assertEquals(tempEffectText(blower.peakCoolantC), "${signed(blower.peakCoolantC)}°C")

        assertEquals("0°C", tempEffectText(0.4))
        assertEquals("0°C", tempEffectText(-0.49))
        assertEquals("-5°C", tempEffectText(-5.4))
        assertEquals("+3°C", tempEffectText(3.2))
    }

    private fun v8(money: Double): GameState = GameState(
        prestiges = Block.V8.ladderStep,
        blockId = Block.V8.name,
        money = money,
        researched = setOf("oil_science", "turbo_node", "nitrous_node"),
        aimGoal = AimGoal.COOLING.name,
    )

    private fun margin(state: GameState): Double {
        val params = Logic.paramsFrom(state)
        return EngineSim.heatMarginC(params, EngineSim.evaluate(params))
    }

    private fun offers(state: GameState): List<AimOffer> {
        val before = margin(state)
        val out = ArrayList<AimOffer>()
        for (upgrade in Upgrades.all) {
            val level = state.upgrades[upgrade.id] ?: 0
            if (level >= upgrade.maxLevel) continue
            if (Logic.lockReason(state, upgrade.id) != null) continue
            val next = state.copy(upgrades = state.upgrades + (upgrade.id to level + 1))
            val gain = margin(next) - before
            if (gain < 0.05) continue
            out += AimOffer(upgrade.id, Logic.upgradeCost(state, upgrade.id), gain, margin(next))
        }
        return out
    }

    private data class Peaks(val coolant: Double, val oil: Double, val egt: Double)

    private fun peakDelta(state: GameState, id: String): Peaks {
        val level = state.upgrades[id] ?: 0
        val now = EngineSim.evaluate(Logic.paramsFrom(state))
        val next = EngineSim.evaluate(Logic.paramsFrom(state.copy(upgrades = state.upgrades + (id to level + 1))))
        return Peaks(
            next.peakCoolantC - now.peakCoolantC,
            next.peakOilC - now.peakOilC,
            next.peakEgtC - now.peakEgtC,
        )
    }

    private fun signed(value: Double): String = com.revline.idle.ui.signed(value, 0)
}
