package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.UpgradeMap
import com.revline.idle.game.Upgrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EngineSimTest {
    @Test
    fun displacementMatchesATwoLiterBoreAndStroke() {
        val cc = EngineSim.displacementCc(86.0, 86.0, 4)
        assertEquals(1998.0, cc, 8.0)
    }

    @Test
    fun pistonSpeedAt6500IsAboutEighteenMetersPerSecond() {
        assertEquals(18.63, EngineSim.meanPistonSpeedMps(86.0, 6500.0), 0.05)
    }

    @Test
    fun boilingPointFollowsCapPressure() {
        assertEquals(100.0, EngineSim.boilingPointC(0.0, 0.0), 0.2)
        assertEquals(121.0, EngineSim.boilingPointC(15.0, 0.0), 0.3)
        assertEquals(128.0, EngineSim.boilingPointC(15.0, 0.5), 0.4)
        assertTrue(EngineSim.coolantBoilingPointC(EngineParams(coolant = Coolant.WATERLESS)) > 180.0)
    }

    @Test
    fun ottoEfficiencyRisesWithCompression() {
        val low = EngineSim.ottoEfficiency(9.5)
        val high = EngineSim.ottoEfficiency(12.0)
        assertTrue(low in 0.40..0.55)
        assertTrue(high > low)
    }

    @Test
    fun economyPartsMoveCruiseMpg() {
        val stock = Logic.sheet(GameState(prestiges = Block.INLINE_FOUR.ordinal))
        val misses = mutableListOf<String>()
        for (upgrade in Upgrades.all.filter { UpgradeMap.effectOf(it.id) == com.revline.idle.game.Effect.ECONOMY }) {
            val one = Logic.sheet(
                GameState(prestiges = Block.INLINE_FOUR.ordinal, upgrades = mapOf(upgrade.id to 1)),
            )
            val maxed = Logic.sheet(
                GameState(prestiges = Block.INLINE_FOUR.ordinal, upgrades = mapOf(upgrade.id to upgrade.maxLevel)),
            )
            val step = one.stats.cruiseMpg - stock.stats.cruiseMpg
            if (step < 0.2) misses += "${upgrade.id} +1 only ${"%.2f".format(step)} mpg"
            if (maxed.stats.cruiseMpg < one.stats.cruiseMpg + 0.2 && upgrade.maxLevel > 1) {
                misses += "${upgrade.id} max ${"%.2f".format(maxed.stats.cruiseMpg)} vs +1 ${"%.2f".format(one.stats.cruiseMpg)}"
            }
            if (one.stats.cruiseMpg <= stock.stats.cruiseMpg) {
                misses += "${upgrade.id} did not raise cruise"
            }
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun stockLongBlockIsInAStreetBand() {
        val stats = EngineSim.evaluate(EngineParams())
        assertTrue("hp ${stats.peakHorsepower}", stats.peakHorsepower in 110.0..180.0)
        assertTrue("tq ${stats.peakTorqueLbFt}", stats.peakTorqueLbFt in 100.0..190.0)
        assertTrue("mpg ${stats.cruiseMpg}", stats.cruiseMpg in 20.0..42.0)
        assertTrue("rel ${stats.reliability}", stats.reliability > 80.0)
        assertTrue("coolant ${stats.peakCoolantC}", stats.peakCoolantC in 80.0..105.0)
        assertTrue("oil ${stats.peakOilC}", stats.peakOilC in 90.0..155.0)
        assertTrue("egt ${stats.peakEgtC}", stats.peakEgtC in 600.0..1050.0)
        assertTrue("bte ${stats.peakBrakeEfficiency}", stats.peakBrakeEfficiency in 0.24..0.40)
    }

    @Test
    fun energyBalanceCloses() {
        val params = listOf(EngineParams(), turboBuild())
        for (paramsSet in params) {
            val curve = EngineSim.evaluate(paramsSet).curve
            for (point in listOf(curve.first(), curve[curve.size / 2], curve.last())) {
                val sum = point.brakeKw + point.frictionKw + point.wallKw + point.exhaustKw
                val err = abs(sum - point.fuelReleasedKw) / point.fuelReleasedKw
                assertTrue("balance $err at ${point.rpm}", err < 0.02)
            }
        }
    }

    @Test
    fun bigCamHurtsLowEndAndHelpsPeakPower() {
        val stock = EngineParams()
        val cam = stock.copy(peakVe = 1.05, vePeakFraction = 0.82, veWidthRpm = 1600.0, redlineRpm = 7600.0)
        val stockLow = EngineSim.point(stock, 1800.0).torqueLbFt
        val camLow = EngineSim.point(cam, 1800.0).torqueLbFt
        assertTrue("low $stockLow vs $camLow", camLow < stockLow)
        assertTrue(EngineSim.evaluate(cam).peakHorsepower > EngineSim.evaluate(stock).peakHorsepower)
    }

    @Test
    fun radiatorConductanceCutsCoolantTemperature() {
        val hot = turboBuild().copy(radiatorUa = 280.0)
        val cool = hot.copy(radiatorUa = 1800.0)
        val hotStats = EngineSim.evaluate(hot)
        val coolStats = EngineSim.evaluate(cool)
        assertTrue(
            "coolant ${hotStats.peakCoolantC} vs ${coolStats.peakCoolantC}",
            coolStats.peakCoolantC < hotStats.peakCoolantC - 8.0,
        )
    }

    @Test
    fun intercoolerDropsChargeTemperature() {
        val bare = turboBuild().copy(intercoolerEff = 0.0)
        val cooled = bare.copy(intercoolerEff = 0.7)
        val a = EngineSim.point(bare, 5500.0)
        val b = EngineSim.point(cooled, 5500.0)
        assertTrue("${a.chargeTempC} vs ${b.chargeTempC}", b.chargeTempC < a.chargeTempC - 8.0)
        assertTrue(b.knockMargin > a.knockMargin)
    }

    @Test
    fun retardedSparkRaisesEgt() {
        val base = EngineParams(timingDeg = 22.0, mixture = Mixture.PORT)
        val late = base.copy(timingDeg = 4.0)
        val a = EngineSim.point(base, 5000.0)
        val b = EngineSim.point(late, 5000.0)
        assertTrue("egt ${a.egtC} vs ${b.egtC}", b.egtC > a.egtC + 30.0)
        assertTrue(b.horsepower < a.horsepower)
    }

    @Test
    fun fuelSystemLimitsPowerWhenThePumpIsSmall() {
        val starved = turboBuild().copy(fuelFlowFactor = 0.7)
        val fed = turboBuild().copy(fuelFlowFactor = 6.0)
        val a = EngineSim.point(starved, 6000.0)
        val b = EngineSim.point(fed, 6000.0)
        assertTrue(a.fuelLimited)
        assertTrue(b.horsepower > a.horsepower + 20.0)
    }

    @Test
    fun liveMpgFallsAsThePullUsesMoreFuel() {
        val low = EngineSim.point(EngineParams(), 1800.0).mpg
        val high = EngineSim.point(EngineParams(), 6000.0).mpg
        assertTrue("mpg $low vs $high", high < low * 0.7)
        val e85 = EngineSim.point(EngineParams(fuel = FuelKind.E85), 4000.0).mpg
        val pump = EngineSim.point(EngineParams(), 4000.0).mpg
        assertTrue("e85 $e85 vs pump $pump", e85 < pump * 0.85)
    }

    @Test
    fun highCompressionOn87KnocksHarderThanRaceFuel() {
        val pump = EngineParams(
            compressionRatio = 12.2,
            timingDeg = 30.0,
            mixture = Mixture.PORT,
            fuel = FuelKind.PUMP_87,
            ignitionQuality = 0.98,
        )
        val race = pump.copy(fuel = FuelKind.RACE_100)
        val a = EngineSim.evaluate(pump)
        val b = EngineSim.evaluate(race)
        assertTrue("rel ${a.reliability} vs ${b.reliability}", a.reliability < b.reliability - 5.0)
    }

    private fun turboBuild() = EngineParams(
        induction = Induction.TURBO,
        maxBoostPsi = 14.0,
        fuelFlowFactor = 4.5,
        fuel = FuelKind.PREMIUM_93,
        intercoolerEff = 0.55,
        mixture = Mixture.PORT,
        ignitionQuality = 0.98,
        afrTrim = 0.45,
        knockControl = 0.8,
        internalsStrength = 1.6,
        radiatorUa = 1400.0,
    )
}
