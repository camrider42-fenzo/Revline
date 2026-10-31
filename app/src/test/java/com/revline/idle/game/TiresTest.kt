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
            // A soft run the whole race is at the floor, on every tire and every circuit.
            assertTrue("$t ${c.id} softs ${g[S]}", g[S]!! <= Tires.FLOOR + 0.01)
            assertTrue("$t ${c.id} softs <= mediums <= hards: $g", g[S]!! <= g[M]!! && g[M]!! <= g[H]!!)
        }
        // On a street tire a soft is done on every circuit, and even a hard is well down.
        val street = car("street")
        assertTrue(Circuits.all.all { Circuits.noStopGrip(street, it, d, Circuits.Pace.STEADY)[S]!! < 0.40 })
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
        // A lap of a 19-corner hot circuit wears a soft about twice as fast as a lap of an 11-corner one.
        val ratio = Tires.softLaps(monza) / Tires.softLaps(singapore)
        assertTrue("ratio $ratio", ratio in 1.8..2.2)
        assertTrue(Tires.severity(monaco) > 1.5 * Tires.severity(monza) * 0.9)
    }

    @Test fun theCompoundTheDriverThePaceAndTheSkyAllChangeTheWear() {
        val k = car("street"); val c = circuit("monza")
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
        val c = circuit("monza")
        val ends = listOf("street", "sport", "semi", "racing").map { Circuits.noStopGrip(car(it), c, d, Circuits.Pace.STEADY)[H]!! }
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

    private val RACE_LAPS = listOf(15, 17, 20, 24, 27, 30)

    @Test fun inTheDryNoStopAndNoSameCompoundAndNoWetsAreAllowed() {
        val dry = Circuits.Weather.DRY
        for (laps in RACE_LAPS) {
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

    @Test fun aSecondStopNeedsTwoDifferentDryCompoundsAmongTheThreeAndComesAfterTheFirst() {
        val dry = Circuits.Weather.DRY
        for (laps in RACE_LAPS) {
            val a = laps / 3; val b = 2 * laps / 3
            assertNull(Tires.breach(Tires.Fit(S, a, H, b, M), laps, dry))
            assertNull(Tires.breach(Tires.Fit(S, a, S, b, M), laps, dry))
            assertNull(Tires.breach(Tires.Fit(M, a, M, b, H), laps, dry))
            assertNull(Tires.breach(Tires.Fit(S, a, M, b, S), laps, dry))
            // The same compound all the way, or a wet in the dry, breaks the rule whatever the stops.
            assertNotNull(Tires.breach(Tires.Fit(M, a, M, b, M), laps, dry))
            assertNotNull(Tires.breach(Tires.Fit(S, a, H, b, W), laps, dry))
            // The second stop is after the first and before the flag, and needs a first.
            assertNotNull(Tires.breach(Tires.Fit(S, b, H, a, M), laps, dry))
            assertNotNull(Tires.breach(Tires.Fit(S, a, H, a, M), laps, dry))
            assertNotNull(Tires.breach(Tires.Fit(S, a, H, laps, M), laps, dry))
            assertNotNull(Tires.breach(Tires.Fit(S, 0, H, b, M), laps, dry))
        }
    }

    @Test fun inTheWetTheChangeIsWaivedAndOnADampTrackItIsRequired() {
        val wet = Circuits.Weather.WET; val damp = Circuits.Weather.DAMP
        for (laps in RACE_LAPS) {
            assertNull(Tires.breach(Tires.Fit(W, 0, W), laps, wet))
            assertNull(Tires.breach(Tires.Fit(W, 5, W, 10, W), laps, wet))
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
        for (w in Circuits.Weather.entries) for (laps in listOf(3, 16, 30)) {
            val marks = listOf(-1, 0, 1, 2, laps / 2, laps - 1, laps, laps + 1)
            for (a in Compound.entries) for (m in Compound.entries) for (b in Compound.entries) for (stop in marks) for (stop2 in marks) {
                val o = Tires.Fit(a, stop, b, stop2, m)
                val fit = Tires.legalise(o, laps, w)
                assertNull("$w $laps $o -> $fit", Tires.breach(fit, laps, w))
                assertTrue(fit.stops.size <= 2)
                // A legal order is left as it is.
                if (Tires.breach(o, laps, w) == null && (o.stop2 > 0 || o.middle == o.finish)) assertEquals(o, fit)
            }
        }
    }

    @Test fun everyOptionIsLegalAndTheDryOnesAreOneStopOrTwo() {
        for (w in Circuits.Weather.entries) for (laps in RACE_LAPS) {
            val opts = Tires.options(laps, w)
            assertTrue(opts.isNotEmpty())
            for (o in opts) {
                assertNull("$w $laps $o", Tires.breach(o, laps, w))
                // No stint shorter than the minimum.
                val cuts = listOf(0) + o.stops + listOf(laps)
                assertTrue("$o", cuts.zipWithNext().all { (x, y) -> y - x >= Tires.MIN_STINT || o.stops.isEmpty() })
            }
            if (w == Circuits.Weather.DRY) {
                assertTrue(opts.all { it.stops.size in 1..2 && it.used.all { c -> c.dry } && it.used.toSet().size >= 2 })
                val one = opts.filter { it.stops.size == 1 }
                val two = opts.filter { it.stops.size == 2 }
                assertEquals(3 * 2 * (laps - 5), one.size)
                val n = laps - 8
                assertEquals(24 * n * (n + 1) / 2, two.size)
                assertEquals(opts.size, one.size + two.size)
            }
            if (w == Circuits.Weather.WET) assertTrue(opts.none { it.stops.size == 2 })
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

    @Test fun aTwoStopPlanRunsThreeStintsAndPaysTwoStops() {
        for (c in Circuits.all) {
            val a = c.laps / 3; val b = 2 * c.laps / 3
            val k = car("street").copy(wear = 0.0)
            val p = Circuits.plan(k, c, d, Circuits.Orders(S, a, H, Circuits.Pace.STEADY, b, M))
            assertEquals(setOf(a, b), p.pits)
            assertEquals(List(a) { S } + List(b - a) { M } + List(c.laps - b) { H }, p.compounds)
            assertEquals(2, p.orders.stopCount)
            for (stop in listOf(a, b)) assertEquals(Circuits.pitLoss(c) * p.raw[stop - 1], p.laps[stop - 1].time - p.raw[stop - 1], 1e-6)
            assertEquals(0.0, p.laps[0].time - p.raw[0], 1e-9)
        }
    }

    @Test fun theChangeRuleStillHoldsOnTwoStops() {
        for (c in Circuits.all) {
            val a = c.laps / 3; val b = 2 * c.laps / 3
            for (o in listOf(Circuits.Orders(M, a, M, Circuits.Pace.STEADY, b, M), Circuits.Orders(S, a, S, Circuits.Pace.STEADY, b, S), Circuits.Orders(W, a, H, Circuits.Pace.STEADY, b, W))) {
                val p = Circuits.plan(car("street"), c, d, o)
                assertTrue("${c.id} $o ${p.compounds.toSet()}", p.compounds.toSet().size >= 2 && p.compounds.all { it.dry })
                assertTrue(p.pits.size in 1..2)
            }
        }
    }

    /** The compounds a car ran, in order, one per stint, from the log. */
    private fun stints(r: Circuits.Result, name: String): List<Compound> {
        val tires = r.log.rows.mapNotNull { row -> row.order.firstOrNull { it.name == name && !it.out }?.tire }
        return tires.fold(ArrayList<Compound>()) { acc, t -> if (acc.lastOrNull() != t) acc.add(t); acc }
    }

    private fun laps(r: Circuits.Result, name: String): List<Compound> =
        r.log.rows.mapNotNull { row -> row.order.firstOrNull { it.name == name && !it.out }?.tire }

    @Test fun everyRivalUsesTwoDifferentDryCompoundsInEveryDryRace() {
        for (c in Circuits.all) {
            val par = Circuits.parPlan(P, c, Circuits.Weather.DRY)
            for (seed in 1L..16L) {
                val r = Circuits.race(Circuits.plan(car("street"), c, d), d, par, seed)
                for (f in r.grid.filter { !it.you && !it.out }) {
                    val used = laps(r, f.name)
                    assertEquals(c.laps, used.size)
                    assertTrue("${c.id} seed $seed ${f.name} ${used.toSet()}", used.toSet().size >= 2 && used.none { it == W })
                    val pits = r.log.events.count { it.text.startsWith("${f.name} pits") }
                    assertTrue("${f.name} stopped $pits times", pits in 1..2)
                }
            }
        }
    }

    @Test fun rivalsMeetTheRuleOnADampTrackToo() {
        val damp = Circuits.Weather.DAMP
        for (c in Circuits.all) {
            val par = Circuits.parPlan(P, c, damp)
            for (seed in 1L..8L) {
                val r = Circuits.race(Circuits.plan(car("street"), c, d, weather = damp), d, par, seed)
                for (f in r.grid.filter { !it.you && !it.out }) {
                    assertTrue("${c.id} ${f.name}", laps(r, f.name).toSet().size >= 2)
                    assertTrue(r.log.events.any { it.text.startsWith("${f.name} pits") })
                }
            }
        }
    }

    @Test fun inTheWetEveryRivalIsOnWetsAndStopsOnlyForNewWets() {
        val wet = Circuits.Weather.WET
        for (c in Circuits.all) {
            val par = Circuits.parPlan(P, c, wet)
            val r = Circuits.race(Circuits.plan(car("street"), c, d, Circuits.Orders(W, 0, W), wet), d, par, 4L)
            // Every rival is on wets, and a stop, if he makes one, is for new wets.
            val pits = r.log.events.filter { it.text.contains(" pits: ") }
            assertTrue(pits.joinToString { it.text }, pits.all { it.text.endsWith("Wet to Wet.") })
            assertEquals(W, r.orders.start)
            assertEquals(0, r.orders.stop)
        }
    }

    @Test fun rivalsPickCompoundsByStyle() {
        val seen = HashMap<Rivals.Style, MutableSet<List<Compound>>>()
        for (c in Circuits.all) {
            val par = Circuits.parPlan(P, c, Circuits.Weather.DRY)
            val r = Circuits.race(Circuits.plan(car("street"), c, d), d, par, 9L)
            for (rv in Rivals.all) {
                if (r.grid.first { it.name == rv.name }.out) continue
                seen.getOrPut(rv.style) { HashSet() }.add(stints(r, rv.name))
            }
        }
        assertTrue(seen.getValue(Rivals.Style.QUALIFIER).all { it.first() == S && it.size == 2 })
        assertTrue(seen.getValue(Rivals.Style.AGGRESSIVE).all { it.first() == S })
        assertTrue(seen.getValue(Rivals.Style.CLOSER).all { it.last() == S })
        assertTrue(seen.getValue(Rivals.Style.STEADY).all { it == listOf(M, H) })
        assertTrue(seen.getValue(Rivals.Style.SURVIVOR).all { it == listOf(H, M) })
        assertTrue(seen.values.flatten().toSet().size >= 5)
    }

    @Test fun someRivalsTwoStopByStyleAndOthersStopOnce() {
        var two = 0; var one = 0
        val byStyle = HashMap<Rivals.Style, Int>()
        for (c in Circuits.all) for (seed in 1L..6L) {
            val par = Circuits.parPlan(P, c, Circuits.Weather.DRY)
            val r = Circuits.race(Circuits.plan(car("street"), c, d), d, par, seed)
            for (rv in Rivals.all) {
                if (r.grid.first { it.name == rv.name }.out) continue
                val n = r.log.events.count { it.text.startsWith("${rv.name} pits") }
                if (n == 2) { two++; byStyle.merge(rv.style, 1, Int::plus) } else one++
            }
        }
        assertTrue("two $two one $one", two > 20 && one > 20)
        // The one-stoppers by style never stop twice.
        for (st in listOf(Rivals.Style.QUALIFIER, Rivals.Style.STEADY, Rivals.Style.SURVIVOR)) assertEquals(st.name, 0, byStyle[st] ?: 0)
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

    @Test fun theForecastOfATwoStopIsThePlanWithTheGripAtBothStopsAndTheFlag() {
        for (c in Circuits.all) for (pace in Circuits.Pace.entries) {
            val a = c.laps / 3; val b = 2 * c.laps / 3
            val o = Circuits.Orders(S, a, H, pace, b, M)
            val k = car("sport")
            val f = Circuits.forecast(k, c, d, o)
            val p = Circuits.plan(k, c, d, o)
            assertEquals(p.raceTime, f.raceTime, 1e-9)
            assertEquals(p.gripAtStop!!, f.gripAtStop!!, 1e-12)
            assertEquals(p.gripAtStops[1], f.gripAtStop2!!, 1e-12)
            assertEquals(p.gripAtFlag, f.gripAtFlag, 1e-12)
            assertEquals(o, f.orders)
            val dw = Circuits.driverWear(Circuits.hands(d, k.setup), pace)
            fun cond(x: Compound, n: Int) = Tires.condition(Tires.lapUse(c, x, 0.0, k.wear, dw) * n)
            assertEquals(cond(S, a), f.gripAtStop!!, 1e-9)
            assertEquals(cond(M, b - a), f.gripAtStop2!!, 1e-9)
            assertEquals(cond(H, c.laps - b), f.gripAtFlag, 1e-9)
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
        val bests = Circuits.all.associateWith { Circuits.advise(k, it, d).first { a -> a.orders.pace == Circuits.Pace.STEADY } }
        assertTrue("distinct fits ${bests.values.map { it.orders.fit }}", bests.values.map { it.orders.start to it.orders.finish }.toSet().size >= 2 || bests.values.map { it.orders.stop }.toSet().size >= 2)
        // The stop is not on lap 1 or 2 any more: the best first stop of every circuit is well inside the race, and they differ.
        for ((c, a) in bests) assertTrue("${c.id} stop ${a.orders.stop} of ${c.laps}", a.orders.stop >= c.laps / 5 && a.orders.stop <= c.laps * 3 / 4)
        assertTrue(bests.values.map { it.orders.stop }.toSet().size >= 4)
        val monza = bests.getValue(circuit("monza"))
        assertTrue(monza.gripAtFlag > 0.5)
    }

    @Test fun theLogShowsTheCompoundsAndTheStop() {
        val c = circuit("monaco"); val k = car("racing")
        val o = Circuits.Orders(S, 12, M)
        val plan = Circuits.plan(k, c, d, o)
        val r = (1L..60L).map { Circuits.race(plan, d, Circuits.parPlan(P, c), it) }.first { it.retired == null }
        assertTrue(r.log.events.any { it.lap == 12 && it.text == "You pit for fresh tires: Soft to Medium." })
        val lines = RaceLog.lines(r.log)
        assertEquals(30, lines.size)
        assertTrue(lines[0].contains(" S P"))
        assertTrue(lines[11].endsWith("PIT") && lines[11].contains(" S P"))
        assertTrue(lines[12].contains(" M P"))
        assertEquals(List(12) { S } + List(18) { M }, r.log.rows.map { it.yourTire })
    }

    @Test fun theLogShowsBothStopsOfATwoStop() {
        val c = circuit("monaco"); val k = car("racing")
        val o = Circuits.Orders(S, 10, H, Circuits.Pace.STEADY, 20, M)
        val plan = Circuits.plan(k, c, d, o)
        val r = (1L..60L).map { Circuits.race(plan, d, Circuits.parPlan(P, c), it) }.first { it.retired == null }
        assertTrue(r.log.events.any { it.lap == 10 && it.text == "You pit for fresh tires: Soft to Medium." })
        assertTrue(r.log.events.any { it.lap == 20 && it.text == "You pit for fresh tires: Medium to Hard." })
        assertEquals(List(10) { S } + List(10) { M } + List(10) { H }, r.log.rows.map { it.yourTire })
        assertEquals(o.copy(), r.orders)
        assertEquals(listOf(10, 20), r.log.rows.filter { it.pitted }.map { it.lap })
    }

    @Test fun anOrderForNoStopInTheDryReachesTheRaceAsAStop() {
        val s = DriverBuilds.make(P, Racing.Build.MEDIUM, "slick", "close", "lsd", 0.8).copy(money = 1e9, hiredDrivers = setOf("colette"))
        val paid = Circuits.open(s, "colette") as Circuits.Entry.Paid
        val c = circuit("monza")
        val out = Circuits.finish(s, Circuits.carOf(s), c, paid.driver, paid.fee, paid.entry, 5L, Circuits.Orders(H, 0, H, Circuits.Pace.PUSH))
        assertEquals(Circuits.Pace.PUSH, out.result.orders.pace)
        assertTrue(out.result.orders.stop in 1 until c.laps)
        assertTrue(out.result.orders.start != out.result.orders.finish)
    }
}
