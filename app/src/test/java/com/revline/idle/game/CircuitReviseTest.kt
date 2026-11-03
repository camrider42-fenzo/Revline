package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.economy.DriverBuilds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pit calls mid-race: the race is run again from a lap, with the same seed, and everything before that lap stands. */
class CircuitReviseTest {
    private val circuit = Circuits.all.first { it.id == "spa" }
    private val driver = Drivers.byId("colette")!!
    private val car = Circuits.carOf(DriverBuilds.make(3, Racing.Build.MEDIUM, "race", "close", "lsd", 0.8))
    private val laps = circuit.laps

    private fun setup(weather: Circuits.Weather, seed: Long, orders: Circuits.Orders = Circuits.Orders()): Pair<Circuits.Run, Circuits.Result> {
        val run = Circuits.Run(car, driver, weather, GameState(prestiges = 3), seed)
        val result = Circuits.race(Circuits.plan(car, circuit, driver, orders, weather), driver, Circuits.parPlan(3, circuit, weather), seed)
        return run to result
    }

    private fun same(a: Circuits.Result, b: Circuits.Result) {
        assertEquals(a.lapTimes, b.lapTimes)
        assertEquals(a.totalTime, b.totalTime, 0.0)
        assertEquals(a.bestLap, b.bestLap, 0.0)
        assertEquals(a.position, b.position)
        assertEquals(a.retired, b.retired)
        assertEquals(a.engineFailed, b.engineFailed)
        assertEquals(a.incidents, b.incidents)
        assertEquals(a.grid, b.grid)
        assertEquals(a.log, b.log)
        assertEquals(a.qualifying, b.qualifying)
        assertEquals(a.orders, b.orders)
    }

    @Test fun anUnchangedPlanFromAnyLapReproducesTheRace() {
        for (weather in Circuits.Weather.entries) for (seed in 1L..5L) {
            val (run, base) = setup(weather, seed)
            for (n in listOf(1, 2, laps / 3, laps / 2, laps - 2)) {
                val again = Circuits.revise(run, base, base.orders, n)
                assertNotNull("$weather seed $seed lap $n", again)
                same(base, again!!)
            }
        }
    }

    @Test fun aChangedPlanChangesOnlyTheLapsFromTheCallOn() {
        for (seed in 1L..4L) {
            val (run, base) = setup(Circuits.Weather.DRY, seed)
            val stop = base.orders.stop
            val call = base.orders.copy(stop = stop + 4)
            val n = stop - 3
            val next = Circuits.revise(run, base, call, n)
            assertNotNull(next)
            next!!
            // Before the call: the same laps, the same order of the field, the same rivals, the same grid.
            assertEquals(base.lapTimes.take(n - 1), next.lapTimes.take(n - 1))
            for (i in 0 until n - 1) {
                assertEquals(base.log.rows[i].yourTime, next.log.rows[i].yourTime, 0.0)
                assertEquals(base.log.rows[i].order, next.log.rows[i].order)
            }
            assertEquals(base.qualifying, next.qualifying)
            assertEquals(base.grid.filter { !it.you }, next.grid.filter { !it.you })
            // From the call on: the old stop lap loses its pit time and the new one gains it.
            assertTrue(next.lapTimes[stop - 1] < base.lapTimes[stop - 1])
            assertTrue(next.lapTimes[stop + 3] > base.lapTimes[stop + 3])
            assertEquals(call, next.orders)
            assertNotEquals(base.totalTime, next.totalTime, 0.0)
            // And it goes on being a race: a second call from an earlier lap keeps the first one's rivals.
            val back = Circuits.revise(run, next, base.orders, n - 1)
            assertNotNull(back)
            same(base, back!!)
        }
    }

    @Test fun boxThisLapStopsOnThisLap() {
        val (run, base) = setup(Circuits.Weather.DRY, 3L)
        val n = base.orders.stop - 6
        val box = Circuits.boxNow(base.orders, n, laps, base.weather)!!
        assertEquals(n, box.stop)
        assertNull(Circuits.reviseCheck(base, box, n))
        val next = Circuits.revise(run, base, box, n)!!
        assertEquals(base.lapTimes.take(n - 1), next.lapTimes.take(n - 1))
        assertTrue(next.lapTimes[n - 1] > base.lapTimes[n - 1])
        assertTrue(next.log.rows[n - 1].pitted)
    }

    @Test fun boxThisLapAddsAStopWhenNoneIsLeft() {
        val (_, base) = setup(Circuits.Weather.DRY, 3L)
        val late = base.orders.stop + 2
        val box = Circuits.boxNow(base.orders, late, laps, base.weather)!!
        assertEquals(base.orders.stop, box.stop)
        assertEquals(late, box.stop2)
        assertNull(Circuits.reviseCheck(base, box, late))
        assertNull(Circuits.boxNow(box, late + 2, laps, base.weather))
        assertNull(Circuits.boxNow(base.orders, laps, laps, base.weather))
    }

    @Test fun theRulesRefuseIllegalOrLateCalls() {
        val (run, base) = setup(Circuits.Weather.DRY, 3L)
        val o = base.orders
        val n = o.stop - 4
        // After the flag, and on the last lap.
        assertEquals("The flag is down. Nothing can change.", Circuits.reviseCheck(base, o.copy(stop = o.stop - 1), laps + 1))
        assertNotNull(Circuits.reviseCheck(base, o.copy(stop = o.stop - 1), laps))
        assertNull(Circuits.revise(run, base, o.copy(stop = o.stop + 1), laps + 1))
        // The compound rule: one stop on the same dry compound, a wet tire on a dry day.
        assertNotNull(Circuits.reviseCheck(base, o.copy(finish = o.start), n))
        assertNotNull(Circuits.reviseCheck(base, o.copy(finish = Tires.Compound.WET), n))
        assertNotNull(Circuits.reviseCheck(base, o.copy(stop = 0), n))
        // The start tire is on; the pace was set at the start.
        assertNotNull(Circuits.reviseCheck(base, o.copy(start = Tires.Compound.SOFT), n))
        assertNotNull(Circuits.reviseCheck(base, o.copy(pace = Circuits.Pace.PUSH), n))
        // A stop already made cannot be moved or undone; a new one cannot be called in the past.
        assertNotNull(Circuits.reviseCheck(base, o.copy(stop = o.stop + 3), o.stop + 2))
        assertNotNull(Circuits.reviseCheck(base, o.copy(stop = o.stop - 6, stop2 = o.stop), o.stop + 2))
        assertNotNull(Circuits.reviseCheck(base, o.copy(stop = n - 1), n))
        assertNull(Circuits.revise(run, base, o.copy(stop = n - 1), n))
        // A legal change before the stop is fine.
        assertNull(Circuits.reviseCheck(base, o.copy(stop = o.stop + 2, finish = Tires.Compound.SOFT.takeIf { o.start != it } ?: Tires.Compound.HARD), n))
    }

    @Test fun conditionsFollowThePlanLapByLap() {
        val (_, base) = setup(Circuits.Weather.DRY, 3L)
        val cs = base.conditions
        assertEquals(laps, cs.size)
        assertEquals(1, cs[0].age)
        assertEquals(1.0, cs[0].gripStart, 0.0)
        val stop = base.orders.stop
        assertEquals(stop, cs[stop - 1].age)
        assertEquals(1, cs[stop].age)
        assertEquals(1.0, cs[stop].gripStart, 0.0)
        assertEquals(base.orders.finish, cs[stop].tire)
        assertTrue(cs[stop - 1].gripEnd < cs[0].gripEnd)
        assertTrue(cs.all { it.power in 0.0..1.0 && it.risk in 0.0..1.0 })
    }

    @Test fun theEconomyFollowsTheRevisedOutcome() {
        val start = GameState(prestiges = 3, money = 5_000.0)
        val (run, base) = setup(Circuits.Weather.DRY, 3L)
        val first = Circuits.Outcome(base, Circuits.prize(start, base.position, base.retired != null), 0.0, 10.0, 50.0, "", run = Circuits.Run(car, driver, Circuits.Weather.DRY, start, 3L))
        val n = base.orders.stop - 3
        val next = Circuits.revise(first.run!!, base, base.orders.copy(stop = base.orders.stop + 4), n)!!
        val second = Circuits.reprice(first, next)
        assertEquals(Circuits.prize(start, next.position, next.retired != null), second.prize, 0.0)
        val settled = Season.settle(start, circuit, first)
        // The till has moved on since the first settling: the difference is applied on top of what is there now.
        val current = settled.state.copy(money = settled.state.money + 700.0)
        val again = Season.resettle(current, start, circuit, first, second)
        assertEquals(current.money + second.prize - first.prize, again.state.money, 1e-6)
        // The same outcome settled in place of itself changes nothing.
        val none = Season.resettle(current, start, circuit, first, first)
        assertEquals(current.money, none.state.money, 1e-6)
        assertEquals(current.seasonPoints, none.state.seasonPoints)
    }
}
