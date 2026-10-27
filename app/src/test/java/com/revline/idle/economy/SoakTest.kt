package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.model.EngineSim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoakTest {
    @Test
    fun theRateIsDegreesPerTenSecondsAndFollowsRpm() {
        val params = Logic.paramsFrom(GameState())
        val idle = EngineSim.soakPer10s(params, params.idleRpm, 0.12)
        val mid = EngineSim.soakPer10s(params, params.redlineRpm * 0.55, 0.65)
        val wide = EngineSim.soakPer10s(params, params.redlineRpm, 1.0)
        assertTrue("idle $idle should cool", idle < 0.0)
        assertTrue("wide open $wide should climb", wide > 1.5)
        assertTrue("higher rpm climbs faster ($mid vs $wide)", wide > mid)
        val gained = Logic.stepSoak(0.0, wide, 30.0)
        assertEquals(wide * 3.0, gained, 0.05)
    }

    @Test
    fun droppedThrottleOrACoolingSystemStopsTheClimb() {
        val hot = Logic.stepSoak(40.0, -4.0, 20.0)
        assertTrue("40°C eased to $hot", hot < 34.0)
        assertEquals(0.0, Logic.stepSoak(2.0, -8.0, 30.0), 0.0)
        assertEquals(80.0, Logic.stepSoak(78.0, 6.0, 20.0), 0.0)
        val stock = Logic.paramsFrom(GameState())
        val cooled = Logic.paramsFrom(
            GameState(
                upgrades = mapOf(
                    "radiator" to 4,
                    "duct" to 2,
                    "oil_cooler" to 3,
                    "cold_air" to 3,
                    "water_pump" to 2,
                ),
            ),
        )
        val rpm = stock.redlineRpm * 0.7
        val stockRate = EngineSim.soakPer10s(stock, rpm, 0.7)
        val cooledRate = EngineSim.soakPer10s(cooled, rpm, 0.7)
        assertTrue("stock $stockRate cooled $cooledRate", cooledRate < stockRate)
        assertTrue("a built cooling system should hold this stop ($cooledRate)", cooledRate <= 0.2)
        val stats = EngineSim.evaluate(stock)
        val rpmHeld = Logic.targetRpm(stock.redlineRpm, 0.7, stock.idleRpm)
        val fresh = Logic.incomeAt(stock, rpmHeld, stats.reliability, 1.0, 0.7, stats, 0.0)
        val soaked = Logic.incomeAt(stock, rpmHeld, stats.reliability, 1.0, 0.7, stats, 40.0)
        assertTrue("soaked $soaked vs fresh $fresh", soaked < fresh)
        val cool = EngineSim.point(stock, rpmHeld, Logic.loadFor(0.7), 0.0)
        val head = EngineSim.point(stock, rpmHeld, Logic.loadFor(0.7), 40.0)
        assertTrue("coolant ${cool.coolantTempC} -> ${head.coolantTempC}", head.coolantTempC > cool.coolantTempC + 20.0)
        assertTrue("oil ${cool.oilTempC} -> ${head.oilTempC}", head.oilTempC > cool.oilTempC + 25.0)
        assertTrue("egt ${cool.egtC} -> ${head.egtC}", head.egtC > cool.egtC + 15.0)
        assertTrue("charge ${cool.chargeTempC} -> ${head.chargeTempC}", head.chargeTempC > cool.chargeTempC + 25.0)
    }

    @Test
    fun aRebuildForgetsTheSoak() {
        val hot = GameState(
            soakC = 36.0,
            researched = setOf("exhaust_fab", "perf_cam", "wasted_spark", "tbi", "ic_node", "premium"),
            upgrades = com.revline.idle.model.BlockPaceTest.sixty,
            fuel = com.revline.idle.model.FuelKind.PREMIUM_93,
        )
        if (Logic.canPrestige(hot)) {
            assertEquals(0.0, Logic.prestige(hot).soakC, 0.0)
        }
    }
}
