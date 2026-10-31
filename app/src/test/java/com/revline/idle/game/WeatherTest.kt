package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SAVE_VERSION
import com.revline.idle.data.SaveCodec
import com.revline.idle.economy.DriverBuilds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    private val P = 3
    private fun car(tire: String) = Circuits.carOf(DriverBuilds.make(P, Racing.Build.MEDIUM, "street", "close", "lsd", 0.8, ctire = tire))
    private fun circuit(id: String) = Circuits.all.first { it.id == id }
    private val W = Circuits.Weather.WET
    private val D = Circuits.Weather.DAMP

    @Test fun theForecastIsTheWeatherAndItChangesRaceToRace() {
        for (c in Circuits.all) {
            assertEquals(Circuits.weatherFor(7, c.id), Circuits.weatherFor(7, c.id))
        }
        val seen = (0 until 400).map { Circuits.weatherFor(it, "monza") }.toSet()
        assertEquals(Circuits.Weather.entries.toSet(), seen)
    }

    @Test fun mostRacesAreDryAndTheWetCircuitsAreWetter() {
        fun share(id: String, w: Circuits.Weather) = (0 until 4000).count { Circuits.weatherFor(it, id) == w } / 4000.0
        for (c in Circuits.all) assertTrue(c.id, share(c.id, Circuits.Weather.DRY) in 0.4..0.8)
        assertTrue(share("spa", Circuits.Weather.DRY) < share("monza", Circuits.Weather.DRY) - 0.1)
        assertTrue(share("spa", W) > share("monza", W))
    }

    @Test fun rainSlowsEveryCarAndTheWetMoreThanTheDamp() {
        val d = Drivers.byId("colette")!!
        for (t in listOf("street", "racing")) for (c in Circuits.all) {
            val dry = Circuits.plan(car(t), c, d).mean
            val damp = Circuits.plan(car(t), c, d, weather = D).mean
            val wet = Circuits.plan(car(t), c, d, orders = if (W == Circuits.Weather.WET) WETS else Circuits.Orders(), weather = W).mean
            assertTrue("$t ${c.id}", dry < damp && damp < wet)
            assertTrue("$t ${c.id} wet is a few percent, not a different race: ${wet / dry}", wet / dry in 1.02..1.35)
        }
    }

    @Test fun aSlickLosesMoreInTheWetThanAStreetTire() {
        val d = Drivers.byId("colette")!!
        for (c in Circuits.all) {
            val street = Circuits.plan(car("street"), c, d, orders = if (W == Circuits.Weather.WET) WETS else Circuits.Orders(), weather = W).mean / Circuits.plan(car("street"), c, d).mean
            val racing = Circuits.plan(car("racing"), c, d, orders = if (W == Circuits.Weather.WET) WETS else Circuits.Orders(), weather = W).mean / Circuits.plan(car("racing"), c, d).mean
            assertTrue("${c.id} street $street racing $racing", racing > street)
        }
    }

    private val WETS = Circuits.Orders(Tires.Compound.WET, 0, Tires.Compound.WET)

    @Test fun aSlickStillLeadsInTheDampButNotByMuchInTheWet() {
        val d = Drivers.byId("colette")!!
        var dryGain = 0.0; var wetGain = 0.0
        for (c in Circuits.all) {
            dryGain += Circuits.plan(car("street"), c, d).mean / Circuits.plan(car("racing"), c, d).mean - 1.0
            wetGain += Circuits.plan(car("street"), c, d, orders = if (W == Circuits.Weather.WET) WETS else Circuits.Orders(), weather = W).mean / Circuits.plan(car("racing"), c, d, orders = if (W == Circuits.Weather.WET) WETS else Circuits.Orders(), weather = W).mean - 1.0
        }
        assertTrue("dry gain $dryGain, wet gain $wetGain", wetGain < dryGain * 0.6)
    }

    @Test fun aWetHandIsWorthMoreInTheRain() {
        val c = circuit("spa"); val k = car("sport")
        val smooth = Drivers.you.copy(care = 90, cornering = 80, mistakes = 20)
        val rough = Drivers.you.copy(care = 15, cornering = 30, mistakes = 80)
        val dry = Circuits.plan(k, c, rough).mean / Circuits.plan(k, c, smooth).mean
        val wet = Circuits.plan(k, c, rough, orders = if (W == Circuits.Weather.WET) WETS else Circuits.Orders(), weather = W).mean / Circuits.plan(k, c, smooth, orders = if (W == Circuits.Weather.WET) WETS else Circuits.Orders(), weather = W).mean
        assertTrue("dry gap $dry, wet gap $wet", wet > dry)
        assertTrue(Circuits.plan(k, c, rough, orders = if (W == Circuits.Weather.WET) WETS else Circuits.Orders(), weather = W).mistakes > Circuits.plan(k, c, smooth, orders = if (W == Circuits.Weather.WET) WETS else Circuits.Orders(), weather = W).mistakes * 1.3)
    }

    @Test fun theWetCoolsSlicksAndACookedWetIsWorn() {
        val c = circuit("hungaroring"); val d = Drivers.byId("colette")!!
        val k = car("racing")
        for (cp in Tires.dry) assertTrue(Tires.wearIn(cp, 1.0) < Tires.wearIn(cp, 0.0))
        assertTrue(Tires.wearIn(Tires.Compound.WET, 0.0) > 2.0 * Tires.wearIn(Tires.Compound.WET, 1.0))
        // The same rubber used all race ends less worn in the rain than in the dry; a wet tire in the dry is cooked.
        val dry = Circuits.noStopGrip(k, c, d, Circuits.Pace.STEADY, Circuits.Weather.DRY)
        val wet = Circuits.noStopGrip(k, c, d, Circuits.Pace.STEADY, W)
        assertTrue(wet[Tires.Compound.MEDIUM]!! > dry[Tires.Compound.MEDIUM]!!)
        assertTrue(dry[Tires.Compound.WET]!! < dry[Tires.Compound.MEDIUM]!!)
    }

    @Test fun theRainSpecialistGainsOnTheFieldInTheWet() {
        val c = circuit("monza"); val d = Drivers.byId("colette")!!
        fun meanPos(w: Circuits.Weather, name: String): Double {
            var sum = 0.0
            for (seed in 1L..300L) {
                val r = Circuits.race(Circuits.plan(car("street"), c, d, orders = if (w == Circuits.Weather.WET) WETS else Circuits.Orders(), weather = w), d, Circuits.parPlan(P, c, w), seed)
                sum += r.grid.indexOfFirst { it.name == name } + 1
            }
            return sum / 300
        }
        val dry = meanPos(Circuits.Weather.DRY, "Nelson Piquet")
        val wet = meanPos(W, "Nelson Piquet")
        assertTrue("dry $dry, wet $wet", wet < dry - 0.4)
    }

    @Test fun theLogSaysWhatTheWeatherWas() {
        val c = circuit("spa"); val d = Drivers.byId("colette")!!
        val r = Circuits.race(Circuits.plan(car("street"), c, d, orders = if (W == Circuits.Weather.WET) WETS else Circuits.Orders(), weather = W), d, Circuits.parPlan(P, c, W), 3L)
        assertEquals(W, r.weather)
        assertTrue(r.log.events.any { it.text == "Conditions: wet." })
        val dry = Circuits.race(Circuits.plan(car("street"), c, d), d, Circuits.parPlan(P, c), 3L)
        assertTrue(dry.log.events.none { it.text.startsWith("Conditions") })
    }

    @Test fun enteringCountsTheRaceAndAFreshSaveStartsAtZero() {
        val s = DriverBuilds.make(P, Racing.Build.MEDIUM, "slick", "close", "lsd", 0.8).copy(hiredDrivers = setOf("colette"))
        assertEquals(0, GameState().circuitRaces)
        val a = Circuits.open(s, "colette") as Circuits.Entry.Paid
        assertEquals(s.circuitRaces + 1, a.state.circuitRaces)
        val refused = Circuits.open(s.copy(money = 0.0), "colette")
        assertTrue(refused is Circuits.Entry.Refused)
    }

    @Test fun anOlderSaveLoadsAndKeepsEverything() {
        val s = DriverBuilds.make(P, Racing.Build.MEDIUM, "slick", "close", "lsd", 0.8).copy(circuitRaces = 5)
        val v2 = SaveCodec.encode(s).replace("\"version\": $SAVE_VERSION", "\"version\": 2").replace(Regex("\"circuitRaces\":\\s*5,?"), "")
        val back = SaveCodec.decode(v2)
        assertEquals(SAVE_VERSION, back.version)
        assertEquals(0, back.circuitRaces)
        assertEquals(s.copy(circuitRaces = 0), back)
        assertEquals(s, SaveCodec.decode(SaveCodec.encode(s)))
    }
}
