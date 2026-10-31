package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SAVE_VERSION
import com.revline.idle.data.SaveCodec
import com.revline.idle.economy.DriverBuilds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonTest {
    private val P = 3
    private val base = DriverBuilds.make(P, Racing.Build.MEDIUM, "street", "close", "lsd", 0.8).copy(money = 1000.0)

    /** A race outcome with the player finishing [place] (1-based, 0 = retired) and the rivals in their usual order behind. */
    private fun outcome(circuit: Circuits.Circuit, place: Int, prize: Double = 0.0, repair: Double = 0.0): Circuits.Outcome {
        val rivals = Rivals.all.map { Circuits.Finisher(it.name, 100.0, false, false, it.team) }
        val grid = if (place == 0) rivals + Circuits.Finisher("Colette", 999.0, true, true, Rivals.YOUR_TEAM)
        else rivals.take(place - 1) + Circuits.Finisher("Colette", 99.0, false, true, Rivals.YOUR_TEAM) + rivals.drop(place - 1)
        val d = Drivers.byId("colette")!!
        val real = Circuits.race(Circuits.plan(Circuits.carOf(base), circuit, d), d, Circuits.parPlan(P, circuit), 1L)
        return Circuits.Outcome(real.copy(grid = grid, position = if (place == 0) 8 else place, retired = if (place == 0) "out" else null), prize, repair, 0.0, 0.0, "")
    }

    @Test fun aNewSeasonStartsLevelOnTheFirstCircuit() {
        val t = Season.standings(base)
        assertEquals(8, t.size)
        assertTrue(t.all { it.points == 0 })
        assertEquals(Circuits.all.first().id, Season.next(base).id)
        assertTrue(t.last().you)
    }

    @Test fun theRoundThatIsUpScoresTheWholeField() {
        val c = Season.next(base)
        val u = Season.settle(base, c, outcome(c, 3, prize = 10.0, repair = 2.0))
        assertEquals(1, u.state.seasonRound)
        assertEquals(15, u.state.seasonPoints[Season.YOU])
        assertEquals(25, u.state.seasonPoints[Rivals.all[0].name])
        assertEquals(18, u.state.seasonPoints[Rivals.all[1].name])
        assertEquals(12, u.state.seasonPoints[Rivals.all[2].name])
        assertEquals(base.money + 8.0, u.state.money, 1e-9)
        assertEquals(Circuits.all[1].id, Season.next(u.state).id)
        assertTrue(u.note.contains("Round 1 of 8"))
        assertEquals(1, u.state.seasonWins[Rivals.all[0].name])
    }

    @Test fun aRetirementScoresNothingAndANonRoundCircuitIsFree() {
        val c = Season.next(base)
        val dnf = Season.settle(base, c, outcome(c, 0))
        assertEquals(0, dnf.state.seasonPoints[Season.YOU] ?: 0)
        assertEquals(1, dnf.state.seasonRound)
        val other = Circuits.all[5]
        val free = Season.settle(base, other, outcome(other, 1, prize = 5.0))
        assertEquals(0, free.state.seasonRound)
        assertTrue(free.state.seasonPoints.isEmpty())
        assertEquals(base.money + 5.0, free.state.money, 1e-9)
        assertTrue(free.note.startsWith("Free race"))
    }

    private fun runSeason(start: GameState, place: Int): GameState {
        var s = start
        repeat(Season.ROUNDS) { s = Season.settle(s, Season.next(s), outcome(Season.next(s), place)).state }
        return s
    }

    @Test fun theTitleIsWonAndPaidAndTheNextSeasonStartsClean() {
        val s = runSeason(base, 1)
        assertEquals(2, s.seasonNo)
        assertEquals(0, s.seasonRound)
        assertTrue(s.seasonPoints.isEmpty())
        assertEquals(1, s.seasonHistory.size)
        assertEquals(1, s.seasonHistory[0].position)
        assertEquals(200, s.seasonHistory[0].points)
        assertEquals(1, Season.titles(s))
        assertEquals(base.money + 3.0 * Circuits.entry(base), s.money, 1e-6)
    }

    @Test fun aMidTableSeasonPaysLessOrNothing() {
        val third = runSeason(base, 3)
        assertEquals(3, third.seasonHistory[0].position)
        assertEquals(base.money + Season.reward(3) * Circuits.entry(base), third.money, 1e-6)
        val last = runSeason(base, 8)
        assertEquals(base.money, last.money, 1e-9)
        assertEquals(0, Season.titles(last))
        assertEquals(Rivals.all[0].name, last.seasonHistory[0].champion)
        assertTrue(Season.reward(1) > Season.reward(2) && Season.reward(2) > Season.reward(5) && Season.reward(6) == 0.0)
    }

    @Test fun theRewardIsAModestShareOfASeasonsPrizes() {
        // A title is worth less than the entries it costs to run a season, and about a quarter of what a winning season's prize money is.
        val prizes = Circuits.prizes.first()
        assertTrue(Season.REWARDS.first() < prizes)
        assertTrue(Season.REWARDS.first() < Season.ROUNDS * Circuits.prizes.average() * 0.25)
    }

    @Test fun devModeSeasonPaysNothingExtra() {
        val dev = base.copy(devMode = true)
        assertEquals(dev.money, runSeason(dev, 1).money, 1e-9)
    }

    @Test fun theHistoryIsCapped() {
        var s = base
        repeat(15) { s = runSeason(s, 1) }
        assertEquals(12, s.seasonHistory.size)
        assertEquals(16, s.seasonNo)
    }

    @Test fun tiesGoToWinsAndThenTheRivals() {
        val s = base.copy(seasonPoints = mapOf(Season.YOU to 50, Rivals.all[0].name to 50), seasonWins = mapOf(Season.YOU to 1))
        assertEquals(1, Season.standings(s).first { it.you }.position)
        val t = base.copy(seasonPoints = mapOf(Season.YOU to 50, Rivals.all[0].name to 50))
        assertEquals(2, Season.standings(t).first { it.you }.position)
    }

    @Test fun anOlderSaveLoadsIntoSeasonOne() {
        val s = base.copy(seasonNo = 4, seasonRound = 3, seasonPoints = mapOf("You" to 40), circuitRaces = 9)
        val json = SaveCodec.encode(s)
        assertEquals(s, SaveCodec.decode(json))
        val v2 = json.replace("\"version\": $SAVE_VERSION", "\"version\": 2")
            .replace(Regex("\"(seasonNo|seasonRound|circuitRaces)\":\\s*\\d+,?"), "")
            .replace(Regex("\"seasonPoints\":\\s*\\{[^}]*\\},?"), "")
        val back = SaveCodec.decode(v2)
        assertEquals(1, back.seasonNo)
        assertEquals(0, back.seasonRound)
        assertTrue(back.seasonPoints.isEmpty())
        assertEquals(SAVE_VERSION, back.version)
        assertEquals(base.money, back.money, 1e-9)
        assertFalse(back.seasonHistory.isNotEmpty())
    }
}
