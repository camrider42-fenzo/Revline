package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chips on the race screen: what they say, and that a tap either works or says why not. */
class RacePartsTapTest {
    /** What a tap does on the screen: buy the study if there is one, else fit the part. */
    private fun tap(s: GameState, kind: String, id: String): GameState {
        val name = when (kind) {
            "box" -> Racing.gearboxes.first { it.id == id }.name
            "axle" -> Racing.axles.first { it.id == id }.name
            "tire" -> Racing.tires.first { it.id == id }.name
            else -> Racing.finals.first { it.id == id }.name
        }
        val gate = Racing.gate(s, kind, id, name)
        return when {
            gate.study != null -> Racing.study(s, gate.study)
            kind == "final" -> Racing.setFinal(s, id)
            else -> Racing.fitPart(s, kind, id)
        }
    }

    private fun fittedId(s: GameState, kind: String) = when (kind) {
        "box" -> s.transmission
        "axle" -> s.driveline
        "tire" -> s.tire
        else -> s.finalDrive
    }

    @Test
    fun drivelineChipShowsItsCostLikeTheOtherParts() {
        val price = 900.0 * Block.forPrestige(0).upgradePriceScale(0)
        var s = GameState(money = 5000.0, researchPoints = 100.0)
        assertEquals("Limited-slip · 50 RP", Racing.gate(s, "axle", "lsd", "Limited-slip").label)
        s = tap(s, "axle", "lsd")
        assertEquals("Limited-slip · ${com.revline.idle.economy.formatDollars(price)}", Racing.gate(s, "axle", "lsd", "Limited-slip").label)
        s = tap(s, "axle", "lsd")
        assertEquals("lsd", s.driveline)
        assertEquals(5000.0 - price, s.money, 0.001)
        assertEquals(50.0, s.researchPoints, 0.001)
        assertEquals("Limited-slip", Racing.gate(s, "axle", "lsd", "Limited-slip").label)
    }

    @Test
    fun aLockedPartSaysWhatItWaitsOnInsteadOfAPrice() {
        val s = GameState(money = 1e6, researchPoints = 1e6, prestiges = 2)
        assertEquals("Independent, limited-slip · after Limited-slip", Racing.gate(s, "axle", "irs", "Independent, limited-slip").label)
        assertEquals("Close-ratio 4-speed · rebuild 1", Racing.gate(GameState(), "box", "close", "Close-ratio 4-speed").label)
    }

    @Test
    fun aTapThatCannotWorkExplainsWhy() {
        val s = GameState(money = 1e6, researchPoints = 1e6, prestiges = 2)
        assertTrue(Racing.partBlocked(s, "axle", "irs")!!.startsWith("Study Limited-slip before"))
        assertTrue(Racing.partBlocked(GameState(researchPoints = 10.0), "tire", "radial")!!.contains("30 RP"))
        val studied = GameState(money = 100.0, raceStudies = setOf("axle_lsd"))
        assertTrue(Racing.partBlocked(studied, "axle", "lsd")!!.startsWith("Needs"))
        assertEquals(studied, Racing.fitPart(studied, "axle", "lsd"))
        assertTrue(Racing.partBlocked(GameState(), "box", "close")!!.contains("rebuild 1"))
    }

    @Test
    fun aTapThatCanWorkHasNoBlock() {
        val s = GameState(money = 1000.0, researchPoints = 100.0)
        assertNull(Racing.partBlocked(s, "tire", "radial"))
        assertNull(Racing.partBlocked(s.copy(raceStudies = setOf("tire_radial")), "tire", "radial"))
        assertNull(Racing.partBlocked(s, "axle", "open"))
    }

    @Test
    fun everyPartCanBeReachedWithEnoughResourcesByTapping() {
        val order = listOf("box" to Racing.gearboxes.map { it.id }, "axle" to Racing.axles.map { it.id }, "tire" to Racing.tires.map { it.id }, "final" to Racing.finals.map { it.id })
        var s = GameState(money = 1e12, researchPoints = 1e12, prestiges = 8)
        for ((kind, ids) in order) {
            for (id in ids) {
                repeat(3) { if (fittedId(s, kind) != id) s = tap(s, kind, id) }
                assertEquals("$kind $id", id, fittedId(s, kind))
            }
        }
    }

    @Test
    fun aPartYouOwnPutsBackOnForFree() {
        var s = GameState(money = 5000.0, researchPoints = 100.0)
        repeat(2) { s = tap(s, "axle", "lsd") }
        val paid = s.money
        s = tap(s, "axle", "open")
        assertEquals("open", s.driveline)
        s = tap(s, "axle", "lsd")
        assertEquals("lsd", s.driveline)
        assertEquals(paid, s.money, 0.001)
        assertNotNull(s.raceOwned.find { it == "axle:lsd" })
    }
}
