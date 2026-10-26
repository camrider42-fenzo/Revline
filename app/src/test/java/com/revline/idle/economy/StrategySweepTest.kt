package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.model.EngineSim
import com.revline.idle.model.FuelKind
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Steady-pedal contracts for a handful of builds. The assertions are the shape
 * of the shop: the floor is not free, cooling and octane move the stop, and a
 * hotter fuel grade is allowed to earn its price.
 */
class StrategySweepTest {
    private val stops = doubleArrayOf(0.20, 0.35, 0.50, 0.65, 0.80, 0.90, 1.00)

    @Test
    fun pedalAndFuelStrategies() {
        val stock = GameState(prestiges = com.revline.idle.model.Block.INLINE_FOUR.ordinal)
        val cooled = stock.copy(
            upgrades = mapOf(
                "cold_air" to 4,
                "radiator" to 4,
                "duct" to 2,
                "oil_cooler" to 3,
                "cap" to 2,
                "water_pump" to 3,
                "thermostat" to 1,
            ),
        )
        val cam = stock.copy(upgrades = mapOf("cam" to 4, "springs" to 5))
        val research = setOf("premium", "tbi", "port", "race", "e85", "turbo_node", "exhaust_fab")
        val boosted = mapOf(
            "turbo" to 4,
            "intercooler" to 2,
            "fuel_pump" to 5,
            "injectors" to 4,
            "radiator" to 2,
            "compression" to 4,
            "base_timing" to 3,
        )
        val hot87 = stock.copy(upgrades = boosted, researched = research, fuel = FuelKind.PUMP_87)
        val hot93 = hot87.copy(fuel = FuelKind.PREMIUM_93)
        val hotRace = hot87.copy(fuel = FuelKind.RACE_100)
        val hotE85 = hot87.copy(fuel = FuelKind.E85)
        val mild93 = stock.copy(researched = setOf("premium"), fuel = FuelKind.PREMIUM_93)
        val sorted = hot93.copy(
            upgrades = boosted + mapOf(
                "radiator" to 4,
                "duct" to 2,
                "cold_air" to 3,
                "intercooler" to 4,
                "oil_cooler" to 2,
                "meth" to 1,
                "compression" to 1,
            ),
        )

        val builds = listOf(
            "stock 87" to stock,
            "cooled 87" to cooled,
            "cam 87" to cam,
            "mild 93" to mild93,
            "boost 87" to hot87,
            "boost 93" to hot93,
            "boost race" to hotRace,
            "boost e85" to hotE85,
            "sorted 93" to sorted,
        )
        val report = StringBuilder()
        val bestOf = mutableMapOf<String, Double>()
        val payOf = mutableMapOf<String, Double>()
        for ((name, state) in builds) {
            val best = Logic.bestThrottle(state)
            bestOf[name] = best
            payOf[name] = pay(state, best)
            report.append(line(name, state, best))
        }
        val stockBest = bestOf.getValue("stock 87")
        val cooledBest = bestOf.getValue("cooled 87")
        val camBest = bestOf.getValue("cam 87")
        val boostBest = bestOf.getValue("boost 87")
        val text = report.toString()
        println(text)

        assertTrue("stock stop $stockBest\n$text", stockBest in 0.30..0.75)
        assertTrue("floor should lose on a stock block\n$text", pay(stock, stockBest) > pay(stock, 1.0) * 1.05)
        assertTrue("a closed pedal should lose\n$text", pay(stock, stockBest) > pay(stock, 0.15) * 1.4)

        assertTrue(
            "cooling should hold more pedal ($cooledBest vs $stockBest)\n$text",
            cooledBest >= stockBest - 0.001,
        )
        assertTrue(
            "cooling should be happier near the floor than a hot stock pull\n$text",
            ratio(cooled, 0.90) > ratio(stock, 0.90),
        )

        val camRpm = Logic.targetRpm(Logic.paramsFrom(cam).redlineRpm, camBest)
        val stockRpm = Logic.targetRpm(Logic.paramsFrom(stock).redlineRpm, stockBest)
        assertTrue("cam rpm $camRpm vs stock $stockRpm\n$text", camRpm > stockRpm - 50.0)

        assertTrue(
            "a knocking boosted 87 should come off the floor ($boostBest)\n$text",
            boostBest < 0.92 && pay(hot87, boostBest) > pay(hot87, 1.0) * 1.03,
        )
        assertTrue(
            "octane should let the same boost hold more pedal\n$text",
            bestOf.getValue("boost 93") >= boostBest - 0.001 &&
                bestOf.getValue("boost e85") > boostBest,
        )
        assertTrue(
            "a cooled intercooled 93 should hold more pedal than stock and pay more\n$text",
            bestOf.getValue("sorted 93") >= stockBest - 0.001 &&
                payOf.getValue("sorted 93") > payOf.getValue("stock 87"),
        )

        val mild87 = payOf.getValue("stock 87")
        val mildPremium = payOf.getValue("mild 93")
        assertTrue(
            "mild 93 pays $mildPremium vs 87 $mild87\n$text",
            mildPremium > mild87 * 0.90,
        )
        assertTrue(
            "boosted 93 should beat boosted 87 (${payOf["boost 93"]} vs ${payOf["boost 87"]})\n$text",
            payOf.getValue("boost 93") > payOf.getValue("boost 87"),
        )
        assertTrue(
            "race fuel on a hot build should beat 87 (${payOf["boost race"]} vs ${payOf["boost 87"]})\n$text",
            payOf.getValue("boost race") > payOf.getValue("boost 87") * 0.95,
        )

        val soaked = soak(stock, 1.0)
        val light = soak(stock, 0.35)
        val cooledSoak = soak(cooled, 1.0)
        assertTrue("wot soak $soaked vs light $light", soaked > light + 4.0)
        assertTrue("cooled wot soak $cooledSoak vs stock $soaked", cooledSoak < soaked - 3.0)

        val hunt = huntMoney(stock)
        val floor = floorMoney(stock)
        assertTrue("hunting the stop made $hunt, flooring made $floor", hunt > floor * 1.08)
    }

    private fun pay(state: GameState, throttle: Double): Double =
        Logic.sheet(state.copy(throttle = throttle)).incomePerSec

    private fun ratio(state: GameState, throttle: Double): Double {
        val best = pay(state, Logic.bestThrottle(state))
        return pay(state, throttle) / best.coerceAtLeast(0.01)
    }

    private fun soak(state: GameState, throttle: Double): Double {
        val params = Logic.paramsFrom(state)
        val rpm = Logic.targetRpm(params.redlineRpm, throttle)
        return EngineSim.point(params, rpm, Logic.loadFor(throttle)).heatSoakC
    }

    private fun line(name: String, state: GameState, best: Double): String {
        val params = Logic.paramsFrom(state)
        val cells = stops.joinToString("  ") { stop ->
            val rpm = Logic.targetRpm(params.redlineRpm, stop)
            val point = EngineSim.point(params, rpm, Logic.loadFor(stop))
            String.format(
                Locale.US,
                "%.2f:$%.1f/%dhp/%.0f°",
                stop,
                pay(state, stop),
                point.horsepower.toInt(),
                point.heatSoakC,
            )
        }
        return String.format(Locale.US, "%-12s best %.2f  $%.2f/s  %s%n", name, best, pay(state, best), cells)
    }

    /** Twenty minutes parked on the best stop, versus twenty minutes floored. */
    private fun huntMoney(state: GameState): Double = pay(state, Logic.bestThrottle(state)) * 20.0 * 60.0

    private fun floorMoney(state: GameState): Double = pay(state, 1.0) * 20.0 * 60.0
}
