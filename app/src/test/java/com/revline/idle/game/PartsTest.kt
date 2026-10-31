package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.economy.CircuitSoak
import com.revline.idle.economy.CircuitSoakRun
import com.revline.idle.economy.DriverBuilds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The race components and the two kinds of tire: how they are gated and bought, what they say, how they save. */
class PartsTest {
    private fun rich(p: Int) = GameState(money = 1e15, researchPoints = 1e9, prestiges = p)

    @Test fun everyLevelIsAStudyThenAPurchaseChainedByLevelAndRebuild() {
        for (line in Parts.lines) {
            assertTrue(line.levels.size in 2..4)
            line.levels.forEachIndexed { i, lv ->
                val study = Racing.studies.first { it.id == line.studyId(i + 1) }
                assertEquals(lv.studyRp, study.cost, 0.0)
                assertEquals(lv.minPrestige, study.minPrestige)
                assertEquals(if (i == 0) null else line.studyId(i), study.requires)
                if (i > 0) {
                    assertTrue("${line.id} study cost rises", lv.studyRp > line.levels[i - 1].studyRp)
                    assertTrue("${line.id} price rises", lv.price > line.levels[i - 1].price)
                    assertTrue(lv.minPrestige >= line.levels[i - 1].minPrestige)
                }
            }
        }
        assertTrue(Parts.lines.size in 5..7)
    }

    @Test fun aPartWalksThroughTheSameGateAsAnyOther() {
        val line = Parts.lines.first { it.id == "suspension" }
        var s = GameState(money = 1e6, researchPoints = 1e6, prestiges = 0)
        assertEquals("rebuild 1", Racing.gate(s, "suspension", "1", "Stiff springs").label.substringAfter("· "))
        assertNotNull(Racing.partBlocked(s, "suspension", "1"))
        s = s.copy(prestiges = 1)
        assertEquals("80 RP".replace("80", "60"), Racing.gate(s, "suspension", "1", "Stiff springs").label.substringAfter("· "))
        s = Racing.study(s, line.studyId(1))
        assertTrue(s.researchPoints < 1e6)
        assertTrue(Racing.gate(s, "suspension", "1", "Stiff springs").label.contains("$"))
        // The second level waits on the first study and on its rebuild.
        assertTrue(Racing.gate(s, "suspension", "2", "Dampers").label.contains("rebuild 3"))
        val before = s.money
        s = Racing.fitPart(s, "suspension", "1")
        assertEquals(1, Parts.level(s, "suspension"))
        assertTrue(s.money < before)
        // Taking it off and putting it back on is free.
        val off = Racing.fitPart(s, "suspension", "0")
        assertEquals(0, Parts.level(off, "suspension"))
        assertEquals(s.money, off.money, 0.0)
        val on = Racing.fitPart(off, "suspension", "1")
        assertEquals(1, Parts.level(on, "suspension"))
        assertEquals(s.money, on.money, 0.0)
    }

    @Test fun aPartNeedsItsStudyAndItsMoney() {
        val noStudy = rich(6).copy(researchPoints = 0.0)
        assertEquals(noStudy, Racing.fitPart(noStudy, "brakes", "1"))
        val poor = Racing.study(rich(6), "kit_brakes_1").copy(money = 1.0)
        assertEquals(poor, Racing.fitPart(poor, "brakes", "1"))
        assertTrue(Racing.partBlocked(poor, "brakes", "1")!!.startsWith("Needs"))
    }

    @Test fun badIdsAreRefusedAndNeverFree() {
        val s = rich(6)
        for ((kind, id) in listOf("brakes" to "9", "brakes" to "-1", "brakes" to "x", "ctire" to "nope", "tire" to "nope", "weight" to "4", "nonsense" to "1")) {
            assertEquals("$kind:$id", s, Racing.fitPart(s, kind, id))
        }
    }

    @Test fun theKitAddsUpAcrossLinesAndStockAddsNothing() {
        assertEquals(Parts.NONE, Parts.kit(GameState()))
        val s = GameState(kit = mapOf("brakes" to 2, "weight" to 1))
        val k = Parts.kit(s)
        assertEquals(1.27 * 1.04, k.brake, 1e-9)
        assertEquals(0.015, k.shave, 1e-9)
        assertEquals(Parts.lines.first { it.id == "brakes" }.level(2)!!.fx.addKg, k.addKg, 1e-9)
    }

    @Test fun theHintsAreWrittenFromTheNumbers() {
        for (line in Parts.lines) for (lv in line.levels) {
            assertTrue("${lv.name} circuit hint", lv.circuitHint.isNotBlank())
            assertTrue("${lv.name} drag hint", lv.dragHint.isNotBlank())
            if (lv.fx.brake > 1.0) assertTrue(lv.circuitHint.contains("brak", ignoreCase = true))
            if (lv.fx.lateral > 1.0) assertTrue(lv.circuitHint.contains("corner", ignoreCase = true))
            if (lv.fx.downforce > 0.0) assertTrue(lv.circuitHint.contains("downforce", ignoreCase = true))
            if (lv.fx.dragMult > 1.0) assertTrue(lv.dragHint.contains("drag +"))
            if (lv.fx.shave > 0.0) assertTrue(lv.dragHint.contains("weight -"))
        }
    }

    // ---- tires ----

    @Test fun dragTiresAndCircuitTiresAreSeparateSets() {
        val base = DriverBuilds.make(4, Racing.Build.MEDIUM, "bias", "close", "lsd", 0.6, ctire = "street")
        val lap = { s: GameState -> Circuits.all.map { Circuits.plan(Circuits.carOf(s), it, Drivers.you).mean } }
        val et = { s: GameState -> RaceDrive.driveWell(RaceDrive.setup(s).copy(distanceM = 402.3)).time }
        // A drag slick does not change a lap.
        assertEquals(lap(base), lap(base.copy(tire = "slick")))
        // A circuit slick does not change the strip.
        assertEquals(et(base), et(base.copy(circuitTire = "racing")), 0.0)
        // And each one does its own job.
        assertTrue(et(base.copy(tire = "slick")) < et(base) - 0.3)
        val quicker = lap(base.copy(circuitTire = "racing")).zip(lap(base)).all { (a, b) -> a < b * 0.97 }
        assertTrue(quicker)
    }

    @Test fun circuitTiresRunInOrderAndWearFasterAsTheyGetSofter() {
        val ts = Racing.circuitTires
        assertTrue(ts.zipWithNext().all { (a, b) -> b.mu > a.mu && b.wear > a.wear && b.price > a.price })
        assertTrue(Racing.circuitTires.all { it.circuit } && Racing.tires.none { it.circuit })
        assertEquals(listOf("bias", "radial", "race", "slick"), Racing.tires.map { it.id })
        val s = DriverBuilds.make(4, Racing.Build.MEDIUM, "radial", "close", "lsd", 0.6, ctire = "street")
        val c = Circuits.all.first { it.id == "monza" }
        val soft = Circuits.plan(Circuits.carOf(s.copy(circuitTire = "racing")), c, Drivers.you)
        val hard = Circuits.plan(Circuits.carOf(s.copy(circuitTire = "street")), c, Drivers.you)
        assertTrue(soft.mean < hard.mean)
        // The racing slick is fastest on lap one and loses more of its edge over the race.
        val lossSoft = soft.laps.last().time / soft.laps.first().time
        val lossHard = hard.laps.last().time / hard.laps.first().time
        assertTrue("$lossSoft vs $lossHard", lossSoft > lossHard)
    }

    @Test fun circuitTiresAreGatedByStudyRebuildAndPrice() {
        var s = rich(0)
        assertTrue(Racing.partBlocked(s, "ctire", "sport")!!.contains("rebuild"))
        s = rich(5)
        assertTrue(Racing.partBlocked(s, "ctire", "racing")!!.startsWith("Study Semi-slicks before"))
        for (id in listOf("sport", "semi", "racing")) { s = Racing.study(s, "ctire_$id"); s = Racing.fitPart(s, "ctire", id) }
        assertEquals("racing", s.circuitTire)
        assertEquals("slick".let { "bias" }, s.tire)
        assertTrue(s.money < 1e15 - 900.0)
    }

    // ---- saves ----

    @Test fun anOldSaveWithOneTireGetsTheNearestCircuitTiresAndKeepsItsDragTire() {
        val old = GameState(
            prestiges = 4, tire = "slick",
            raceStudies = setOf("tire_radial", "tire_race", "tire_slick"),
            raceOwned = setOf("tire:radial", "tire:race", "tire:slick"),
            version = 1,
        )
        val json = kotlinx.serialization.json.Json.parseToJsonElement(SaveCodec.encode(old)).let { t ->
            kotlinx.serialization.json.JsonObject((t as kotlinx.serialization.json.JsonObject) - "version").toString()
        }
        assertFalse(json.contains("\"version\""))
        val s = SaveCodec.decode(json)
        assertEquals("slick", s.tire)
        assertEquals("semi", s.circuitTire)
        assertTrue("ctire_sport" in s.raceStudies && "ctire_semi" in s.raceStudies)
        assertTrue("ctire:sport" in s.raceOwned && "ctire:semi" in s.raceOwned)
        assertFalse("ctire_racing" in s.raceStudies)
        assertEquals(old.money, s.money, 0.0)
        assertEquals(SAVE_VERSION_FOR_TEST, s.version)
        // Loading it again changes nothing.
        assertEquals(s, SaveCodec.decode(SaveCodec.encode(s)))
    }

    @Test fun anOldSaveOnStreetTiresGetsNothingForFree() {
        val s = SaveCodec.decode(SaveCodec.encode(GameState(prestiges = 2, tire = "radial", version = 1)))
        assertEquals("street", s.circuitTire)
        assertTrue(s.raceStudies.none { it.startsWith("ctire") })
        assertTrue(s.raceOwned.none { it.startsWith("ctire") })
    }

    @Test fun aNewSaveRoundTripsItsKitAndCircuitTire() {
        val s = GameState(kit = mapOf("brakes" to 2, "aero" to 1), circuitTire = "semi", raceOwned = setOf("brakes:2"))
        assertEquals(s, SaveCodec.decode(SaveCodec.encode(s)))
    }

    @Test fun aSaveFromBeforeVersionsDecodesAsVersionOne() {
        val s = SaveCodec.decode("""{"prestiges":3,"tire":"race","raceOwned":["tire:race"]}""")
        assertEquals("sport", s.circuitTire)
        assertEquals("race", s.tire)
    }

    // ---- the circuit screen ----

    @Test fun aHopelessBuildReadsAsFarOffThePaceNotAHugeNumber() {
        assertEquals("Your build here: +3.2% a lap against the field's par car", Circuits.fitText(3.2))
        assertEquals("Your build here: -4.0% a lap against the field's par car", Circuits.fitText(-4.0))
        for (bad in listOf(-277.5, -25.1, Double.NaN, Double.NEGATIVE_INFINITY)) assertTrue(Circuits.fitText(bad).contains("far off the pace"))
        val tiny = GameState(prestiges = 6)
        val fit = Circuits.fitPercent(Circuits.carOf(tiny), Circuits.all.first(), 6, Drivers.you)
        assertTrue(Circuits.fitText(fit).startsWith("Your build here"))
    }

    // ---- a big batch ----

    @Test fun aBigBatchOfCircuitRacesWithEveryKitTierHasNoBadResult() {
        val out = CircuitSoakRun.run(listOf(2, 4, 6), listOf(0, 1, 2, 3), 12)
        assertTrue("races ${out.total.races}", out.total.races > 50_000)
        assertEquals(out.total.problems.toString(), 0L, out.total.bad)
        assertTrue(out.total.net.isFinite())
        // Money: a middling build does not get rich on the entry, and a strong one is not ruined.
        assertTrue("street t0 ${out.byBuild.getValue("street t0").netMean()}", out.byBuild.getValue("street t0").netMean() in -0.6..0.2)
        assertTrue("balanced t3 ${out.byBuild.getValue("balanced t3").netMean()}", out.byBuild.getValue("balanced t3").netMean() in 0.0..2.5)
        assertTrue("street t3", out.byBuild.getValue("street t3").netMean() > out.byBuild.getValue("street t0").netMean())
        for ((k, t) in out.byBuild) assertTrue("$k dnf ${t.dnfRate()}", t.dnfRate() in 0.0..0.35)
    }

    private companion object { const val SAVE_VERSION_FOR_TEST = 2 }
}
