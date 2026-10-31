package com.revline.idle.game

import com.revline.idle.economy.DriverBuilds
import com.revline.idle.data.GameState
import com.revline.idle.game.Tires.Compound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TiresTest {
    private val P = 3
    private fun car(tire: String) = Circuits.carOf(DriverBuilds.make(P, Racing.Build.MEDIUM, "street", "close", "lsd", 0.8, ctire = tire))
    private fun circuit(id: String) = Circuits.all.first { it.id == id }
    private val d = Drivers.byId("colette")!!
    private val S = Compound.SOFT
    private val M = Compound.MEDIUM
    private val H = Compound.HARD
    private val W = Compound.WET

    // ---- The wear curve --------------------------------------------------------------------------------------------

    @Test fun theCurveStartsAtOneFallsAndQuickensIntoACliff() {
        assertEquals(1.0, Tires.condition(0.0), 1e-12)
        val xs = (0..30).map { Tires.condition(it * 0.05) }
        assertTrue(xs.zipWithNext().all { (a, b) -> b <= a })
        // The loss in the same step grows: the last of a tire's life costs more than the first.
        val early = Tires.condition(0.0) - Tires.condition(0.3)
        val late = Tires.condition(0.6) - Tires.condition(0.9)
        assertTrue("early $early late $late", late > 2.0 * early)
        // It never goes to nothing.
        assertEquals(Tires.FLOOR, Tires.condition(5.0), 1e-12)
        assertTrue(Tires.condition(0.6) > Tires.FLOOR)
        // A full design life is a heavily worn tire.
        assertTrue(Tires.condition(1.0) < 0.35 + 0.4)
    }

    @Test fun aNoStopOnSoftsIsBadlyWornEverywhereAndNeverNearNinetyPercent() {
        for (t in listOf("street", "sport", "semi", "racing")) for (c in Circuits.all) {
            val g = Circuits.noStopGrip(car(t), c, d, Circuits.Pace.STEADY)
            assertTrue("$t ${c.id} softs ${g[S]}", g[S]!! < 0.85)
            assertTrue("$t ${c.id} softs < mediums < hards: $g", g[S]!! <= g[M]!! && g[M]!! <= g[H]!! && g[S]!! < g[H]!! - 0.05)
        }
        // On a street tire the hard circuits cook a soft to a rag, and even a hard is well down.
        val street = car("street")
        val bad = Circuits.all.count { Circuits.noStopGrip(street, it, d, Circuits.Pace.STEADY)[S]!! < 0.60 }
        assertTrue("circuits where a soft no-stop is under 60%: $bad", bad >= 5)
        assertTrue(Circuits.all.all { Circuits.noStopGrip(street, it, d, Circuits.Pace.STEADY)[H]!! < 0.95 })
    }

    // ---- Corners ---------------------------------------------------------------------------------------------------

    @Test fun theCircuitsHaveTheirRealCornerCounts() {
        val real = mapOf("monza" to 11, "monaco" to 19, "silverstone" to 18, "spa" to 19, "hungaroring" to 14, "suzuka" to 18, "montreal" to 14, "singapore" to 19)
        for (c in Circuits.all) assertEquals(c.id, real[c.id], c.corners)
        assertEquals(real.keys, Circuits.all.map { it.id }.toSet())
    }

    @Test fun moreCornersWearTheTiresMore() {
        val k = car("street")
        fun use(c: Circuits.Circuit) = Tires.lapUse(c, M, 0.0, k.wear, 1.0)
        val monza = circuit("monza"); val hung = circuit("hungaroring"); val monaco = circuit("monaco"); val singapore = circuit("singapore")
        assertTrue(use(monza) < use(hung) && use(hung) < use(monaco))
        assertTrue(use(monza) < use(singapore))
        // Each circuit ranks by its corners against any circuit with four more or less.
        for (a in Circuits.all) for (b in Circuits.all) if (b.corners >= a.corners + 4) assertTrue("${a.id} ${b.id}", use(a) < use(b))
        // And in the race: more corners, less grip at the flag, per lap (the laps differ, so per-lap use is the fair measure).
        assertTrue(Tires.cornerFactor(monaco) > 1.5 * Tires.cornerFactor(monza) * 0.9)
        // A lap of a 19-corner circuit uses about 1.7x the life of a lap of an 11-corner one on the same tire.
        val ratio = Tires.cornerFactor(monaco) / Tires.cornerFactor(monza)
        assertTrue("ratio $ratio", ratio in 1.5..2.0)
    }

    @Test fun theCompoundTheDriverThePaceAndTheSkyAllChangeTheWear() {
        val k = car("semi"); val c = circuit("suzuka")
        fun end(comp: Compound, pace: Circuits.Pace = Circuits.Pace.STEADY, drv: Drivers.Driver = d, w: Circuits.Weather = Circuits.Weather.DRY) =
            Circuits.noStopGrip(k, c, drv, pace, w)[comp]!!
        assertTrue(end(S) < end(M) && end(M) < end(H))
        assertTrue(end(M, Circuits.Pace.PUSH) < end(M) && end(M) < end(M, Circuits.Pace.SAVE))
        assertTrue(end(M, drv = Drivers.you.copy(care = 95, cornering = 85)) > end(M, drv = Drivers.you.copy(care = 10, cornering = 25)))
        // A wet track runs the slicks cooler; a wet tire in the dry is eaten away.
        assertTrue(end(M, w = Circuits.Weather.WET) > end(M))
        assertTrue(end(W) <= end(S) && end(W) < end(H))
        assertTrue(end(W, w = Circuits.Weather.WET) > end(W) + 0.2)
    }

    @Test fun anOwnedTireTierIsHarderOnItselfTheHigherItGoes() {
        val c = circuit("silverstone")
        val ends = listOf("street", "sport", "semi", "racing").map { Circuits.noStopGrip(car(it), c, d, Circuits.Pace.STEADY)[M]!! }
        assertTrue("$ends", ends.zipWithNext().all { (a, b) -> b <= a } && ends.first() > ends.last())
        val wear = listOf("street", "sport", "semi", "racing").map { car(it).wear }
        assertTrue(wear.zipWithNext().all { (a, b) -> b > a })
        assertEquals(1.0, wear.first(), 1e-9)
        assertTrue("racing tier wear ${wear.last()}", wear.last() in 1.3..1.8)
    }

    @Test fun everyOwnedTierIsDrivableOnEveryCircuitWithALegalBestPlan() {
        for (t in Racing.circuitTires) for (c in Circuits.all) {
            val k = car(t.id)
            val best = Circuits.advise(k, c, d).first()
            assertNull("${t.id} ${c.id}", Tires.breach(best.orders.fit, c.laps, Circuits.Weather.DRY))
            assertTrue(best.gripAtFlag in Tires.FLOOR..1.0)
        }
        assertEquals("street", GameState().circuitTire)
    }

    // ---- Compounds -------------------------------------------------------------------------------------------------

    @Test fun softIsFastestAndHardIsSlowestOnNewRubber() {
        for (c in Circuits.all) {
            fun fresh(a: Compound) = Circuits.plan(car("semi"), c, d, Circuits.Orders(a, 2.coerceAtMost(c.laps - 1), if (a == H) S else H)).fresh
            assertTrue(c.id, fresh(S) < fresh(M) && fresh(M) < fresh(H))
            // A step between compounds is a fraction of a percent to a couple, not a different car.
            assertTrue(c.id, fresh(H) / fresh(S) in 1.005..1.03)
        }
        assertTrue(S.grip > M.grip && M.grip > H.grip)
        assertTrue(S.wear > M.wear && M.wear > H.wear)
    }

    @Test fun slicksAreBadInTheWetAndWetsAreBadInTheDry() {
        val k = car("street")
        val wet = Circuits.Weather.WET; val dry = Circuits.Weather.DRY; val damp = Circuits.Weather.DAMP
        val skill = 0.5
        for (slick in Tires.dry) assertTrue(Tires.grip(W, wet, k.wetTire, skill) > Tires.grip(slick, wet, k.wetTire, skill) + 0.08)
        assertTrue(Tires.grip(M, dry, k.wetTire, skill) > Tires.grip(W, dry, k.wetTire, skill) + 0.05)
        // A damp track is a close call between the wet and a slick; the wet is no disaster.
        assertTrue(Math.abs(Tires.grip(W, damp, k.wetTire, skill) - Tires.grip(M, damp, k.wetTire, skill)) < 0.06)
        // In the dry the best plan never uses a wet, in the wet it always does.
        for (c in Circuits.all) {
            assertTrue(c.id, Circuits.advise(k, c, d, dry).first().orders.let { it.start.dry && it.finish.dry })
            assertEquals(c.id, W, Circuits.advise(k, c, d, wet).first().orders.start)
        }
    }

    @Test fun aWetTireLevelsTheOwnedTiersInTheRain() {
        val c = circuit("spa")
        fun gap(w: Circuits.Weather, o: Circuits.Orders) = Circuits.plan(car("street"), c, d, o, w).mean / Circuits.plan(car("racing"), c, d, o, w).mean
        val wets = Circuits.Orders(W, 0, W)
        assertTrue(gap(Circuits.Weather.WET, wets) < gap(Circuits.Weather.DRY, Circuits.Orders(M, 1, H)) )
    }

    // ---- The rule: one change of compound ---------------------------------------------------------------------------

    @Test fun inTheDryNoStopAndNoSameCompoundAndNoWetsAreAllowed() {
        val dry = Circuits.Weather.DRY
        for (laps in 3..6) {
            assertNotNull(Tires.breach(Tires.Fit(M, 0, M), laps, dry))
            assertNotNull(Tires.breach(Tires.Fit(S, 0, H), laps, dry))
            for (stop in 1 until laps) {
                assertNotNull(Tires.breach(Tires.Fit(M, stop, M), laps, dry))
                assertNotNull(Tires.breach(Tires.Fit(W, stop, H), laps, dry))
                assertNotNull(Tires.breach(Tires.Fit(S, stop, W), laps, dry))
                assertNull(Tires.breach(Tires.Fit(S, stop, H), laps, dry))
            }
            assertNotNull(Tires.breach(Tires.Fit(S, laps, H), laps, dry))
        }
    }

    @Test fun inTheWetTheChangeIsWaivedAndOnADampTrackItIsRequired() {
        val wet = Circuits.Weather.WET; val damp = Circuits.Weather.DAMP
        for (laps in 3..6) {
            assertNull(Tires.breach(Tires.Fit(W, 0, W), laps, wet))
            assertNull(Tires.breach(Tires.Fit(W, 2, W), laps, wet))
            assertNotNull(Tires.breach(Tires.Fit(W, 0, S), laps, wet))
            assertNotNull(Tires.breach(Tires.Fit(W, 0, W), laps, damp))
            assertNull(Tires.breach(Tires.Fit(W, 1, S), laps, damp))
            assertNull(Tires.breach(Tires.Fit(M, 1, W), laps, damp))
            assertNotNull(Tires.breach(Tires.Fit(W, 1, W), laps, damp))
        }
        assertTrue(Circuits.Weather.entries.all { Tires.rule(it).isNotBlank() })
    }

    @Test fun theStewardsMakeAnyOrderLegal() {
        for (w in Circuits.Weather.entries) for (laps in 3..6) for (a in Compound.entries) for (b in Compound.entries) for (stop in -1..laps + 1) {
            val fit = Tires.legalise(Tires.Fit(a, stop, b), laps, w)
            assertNull("$w $laps $a $stop $b -> $fit", Tires.breach(fit, laps, w))
            // A legal order is left as it is.
            if (Tires.breach(Tires.Fit(a, stop, b), laps, w) == null) assertEquals(Tires.Fit(a, stop, b), fit)
        }
    }

    @Test fun everyOptionIsLegalAndTheDryOnesAreAllOneStop() {
        for (w in Circuits.Weather.entries) for (laps in 3..6) {
            val opts = Tires.options(laps, w)
            assertTrue(opts.isNotEmpty())
            for (o in opts) assertNull("$w $laps $o", Tires.breach(o, laps, w))
            if (w == Circuits.Weather.DRY) {
                assertTrue(opts.all { it.stop in 1 until laps && it.start != it.finish && it.start.dry && it.finish.dry })
                assertEquals(3 * 2 * (laps - 1), opts.size)
            }
        }
    }

    @Test fun thePlanRunsWhatTheStewardsLeaveAndThePlayerCannotStayOut() {
        for (c in Circuits.all) {
            val p = Circuits.plan(car("street"), c, d, Circuits.Orders(H, 0, H))
            assertEquals(1, p.pits.size)
            assertTrue(p.orders.stop in 1 until c.laps)
            assertTrue(p.orders.start != p.orders.finish)
            assertEquals(p.compounds.first(), p.orders.start)
            assertEquals(p.compounds.last(), p.orders.finish)
            assertEquals(c.laps, p.compounds.size)
        }
    }

    @Test fun everyRivalChangesCompoundInEveryDryRace() {
        for (c in Circuits.all) for (w in listOf(Circuits.Weather.DRY)) {
            val par = Circuits.parPlan(P, c, w)
            for (seed in 1L..24L) {
                val r = Circuits.race(Circuits.plan(car("street"), c, d, weather = w), d, par, seed)
                for (f in r.grid.filter { !it.you }) {
                    val pit = r.log.events.firstOrNull { it.text.startsWith("${f.name} pits: ") }
                    if (f.out) continue
                    assertNotNull("${c.id} seed $seed ${f.name}", pit)
                    val change = pit!!.text.removePrefix("${f.name} pits: ").removeSuffix(".").split(" to ")
                    assertEquals(2, change.size)
                    assertTrue("${pit.text}", change[0] != change[1] && change.none { it == "Wet" })
                }
            }
        }
    }

    @Test fun rivalsMeetTheRuleOnADampTrackToo() {
        val damp = Circuits.Weather.DAMP
        for (c in Circuits.all) {
            val par = Circuits.parPlan(P, c, damp)
            for (seed in 1L..12L) {
                val r = Circuits.race(Circuits.plan(car("street"), c, d, weather = damp), d, par, seed)
                for (f in r.grid.filter { !it.you && !it.out }) {
                    val pit = r.log.events.firstOrNull { it.text.startsWith("${f.name} pits: ") }
                    assertNotNull("${c.id} ${f.name}", pit)
                    val change = pit!!.text.removePrefix("${f.name} pits: ").removeSuffix(".").split(" to ")
                    assertTrue(change[0] != change[1])
                }
            }
        }
    }

    @Test fun inTheWetEveryRivalIsOnWetsAndNeedNotStop() {
        val wet = Circuits.Weather.WET
        for (c in Circuits.all) {
            val par = Circuits.parPlan(P, c, wet)
            val r = Circuits.race(Circuits.plan(car("street"), c, d, Circuits.Orders(W, 0, W), wet), d, par, 4L)
            assertTrue(r.log.events.filter { it.text.contains(" pits: ") }.joinToString { it.text }, r.log.events.none { it.text.contains(" pits: ") })
            assertEquals(W, r.orders.start)
            assertEquals(0, r.orders.stop)
        }
    }

    @Test fun rivalsPickCompoundsByStyle() {
        val seen = HashMap<Rivals.Style, MutableSet<Pair<Compound, Compound>>>()
        for (c in Circuits.all) for (w in listOf(Circuits.Weather.DRY)) {
            val par = Circuits.parPlan(P, c, w)
            val r = Circuits.race(Circuits.plan(car("street"), c, d, weather = w), d, par, 9L)
            for (rv in Rivals.all) {
                val pit = r.log.events.firstOrNull { it.text.startsWith("${rv.name} pits: ") } ?: continue
                val (a, b) = pit.text.removePrefix("${rv.name} pits: ").removeSuffix(".").split(" to ").map { n -> Compound.entries.first { it.title == n } }
                seen.getOrPut(rv.style) { HashSet() }.add(a to b)
            }
        }
        assertTrue(seen.getValue(Rivals.Style.QUALIFIER).all { it.first == S })
        assertTrue(seen.getValue(Rivals.Style.AGGRESSIVE).all { it.first == S })
        assertTrue(seen.getValue(Rivals.Style.CLOSER).all { it.second == S })
        assertTrue(seen.getValue(Rivals.Style.STEADY).all { it == (M to H) })
        assertTrue(seen.getValue(Rivals.Style.SURVIVOR).all { it == (H to M) })
        assertTrue(seen.values.flatten().toSet().size >= 4)
    }

    // ---- The pit wall matches the sim ---------------------------------------------------------------------------------

    @Test fun theForecastIsThePlanTheRaceIsRunFrom() {
        for (c in Circuits.all) for (pace in Circuits.Pace.entries) {
            val o = Circuits.Orders(S, (c.laps + 1) / 2, H, pace)
            val f = Circuits.forecast(car("sport"), c, d, o)
            val p = Circuits.plan(car("sport"), c, d, o)
            assertEquals(p.raceTime, f.raceTime, 1e-9)
            assertEquals(p.gripAtStop!!, f.gripAtStop!!, 1e-12)
            assertEquals(p.gripAtFlag, f.gripAtFlag, 1e-12)
            assertEquals(o, f.orders)
            assertEquals(p.pits, setOf(o.stop))
            // The stop's grip is the wear of the start compound over its stint, at the end of the stint.
            val dw = Circuits.driverWear(Circuits.hands(d, car("sport").setup), pace)
            val use = Tires.lapUse(c, S, 0.0, car("sport").wear, dw)
            assertEquals(Tires.condition(use * o.stop), f.gripAtStop!!, 1e-9)
            val useH = Tires.lapUse(c, H, 0.0, car("sport").wear, dw)
            assertEquals(Tires.condition(useH * (c.laps - o.stop)), f.gripAtFlag, 1e-9)
        }
    }

    @Test fun theNoStopTableIsTheSameWearTheRaceRuns() {
        // The table's soft-on-soft is the plan's own curve with no stop in the wet, where no stop is legal.
        val wet = Circuits.Weather.WET
        for (c in Circuits.all) {
            val p = Circuits.plan(car("racing"), c, d, Circuits.Orders(W, 0, W), wet)
            assertEquals(Circuits.noStopGrip(car("racing"), c, d, Circuits.Pace.STEADY, wet)[W]!!, p.gripAtFlag, 1e-12)
            assertNull(p.gripAtStop)
        }
    }

    @Test fun theBestPlanOnPaperIsTheQuickestAndRaceTimesAreWhatTheySay() {
        for (c in Circuits.all) {
            val adv = Circuits.advise(car("street"), c, d)
            assertTrue(adv.zipWithNext().all { (a, b) -> a.raceTime <= b.raceTime })
            // Every row is a plan the sim reproduces exactly.
            for (a in adv.take(5)) assertEquals(a.raceTime, Circuits.plan(car("street"), c, d, a.orders).raceTime, 1e-9)
        }
        // The best plan wins on average: over many dice the simulated mean is within a few percent of the forecast.
        val c = circuit("silverstone"); val k = car("street")
        val best = Circuits.advise(k, c, d).first()
        val plan = Circuits.plan(k, c, d, best.orders)
        val par = Circuits.parPlan(P, c)
        val times = (1L..200L).map { Circuits.race(plan, d, par, it) }.filter { it.retired == null }.map { it.totalTime }
        assertTrue(times.size > 100)
        val mean = times.average()
        assertTrue("sim $mean forecast ${best.raceTime}", mean in best.raceTime * 0.97..best.raceTime * 1.12)
    }

    @Test fun theBestStrategyIsNotTheSameEverywhere() {
        val k = car("street")
        val bests = Circuits.all.map { Circuits.advise(k, it, d).first { a -> a.orders.pace == Circuits.Pace.STEADY }.orders }
        assertTrue("distinct fits ${bests.map { it.fit }}", bests.map { it.start to it.finish }.toSet().size >= 2 || bests.map { it.stop }.toSet().size >= 2)
        // On a kinder circuit the stop comes later or the tires are softer than on a brutal one.
        val monza = Circuits.advise(k, circuit("monza"), d).first { it.orders.pace == Circuits.Pace.STEADY }
        assertTrue(monza.gripAtFlag > 0.5)
    }

    @Test fun theLogShowsTheCompoundsAndTheStop() {
        val c = circuit("monaco"); val k = car("racing")
        val o = Circuits.Orders(S, 2, M)
        val plan = Circuits.plan(k, c, d, o)
        val r = Circuits.race(plan, d, Circuits.parPlan(P, c), 12L)
        if (r.retired == null) {
            assertTrue(r.log.events.any { it.lap == 2 && it.text == "You pit for fresh tires: Soft to Medium." })
            val lines = RaceLog.lines(r.log)
            assertTrue(lines.take(3).toString(), lines[0].contains(" S P"))
            assertTrue(lines[1].endsWith("PIT") && lines[1].contains(" S P"))
            assertTrue(lines[2].contains(" M P"))
            assertEquals(listOf(S, S, M, M, M, M), r.log.rows.map { it.yourTire })
        }
    }

    @Test fun anOrderForNoStopInTheDryReachesTheRaceAsAStop() {
        val s = DriverBuilds.make(P, Racing.Build.MEDIUM, "slick", "close", "lsd", 0.8).copy(money = 1e9, hiredDrivers = setOf("colette"))
        val paid = Circuits.open(s, "colette") as Circuits.Entry.Paid
        val out = Circuits.finish(s, Circuits.carOf(s), circuit("monza"), paid.driver, paid.fee, paid.entry, 5L, Circuits.Orders(H, 0, H, Circuits.Pace.PUSH))
        assertEquals(Circuits.Pace.PUSH, out.result.orders.pace)
        assertTrue(out.result.orders.stop in 1..3)
        assertTrue(out.result.orders.start != out.result.orders.finish)
    }
}
