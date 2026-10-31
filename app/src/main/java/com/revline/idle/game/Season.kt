package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.data.SeasonRecord
import com.revline.idle.economy.formatDollars

/**
 * The championship: eight rounds, one on each circuit in order. The round that is up counts for points when the player
 * enters it; any other circuit is a free race for prize money only. Points go to the whole field by finishing order,
 * a retirement scores nothing, and after round eight the table pays a modest reward in entry fees and a new season starts.
 */
object Season {
    /** Points by finishing place, first to eighth. */
    val POINTS = listOf(25, 18, 15, 12, 10, 8, 6, 4)

    /** The player's key in the points table. The rivals are keyed by name. */
    const val YOU = "You"

    /**
     * What the final table pays, in entry fees: champion, second, third, fourth, fifth. About a third of the champion's
     * prize money for the season at most, so a title is a bonus and not the income.
     */
    val REWARDS = listOf(3.0, 2.0, 1.2, 0.8, 0.4)

    val ROUNDS: Int get() = Circuits.all.size

    /** The circuit the next championship round is on. */
    fun next(state: GameState): Circuits.Circuit = Circuits.all[state.seasonRound.coerceIn(0, ROUNDS - 1)]

    data class Row(val position: Int, val name: String, val team: String, val points: Int, val wins: Int, val you: Boolean)

    /** The table, leader first. Level on points: more wins, then the player behind (the rivals had the head start). */
    fun standings(state: GameState): List<Row> {
        val names = Rivals.all.map { it.name to it.team } + (YOU to Rivals.YOUR_TEAM)
        val sorted = names.sortedWith(
            compareByDescending<Pair<String, String>> { state.seasonPoints[it.first] ?: 0 }
                .thenByDescending { state.seasonWins[it.first] ?: 0 }
                .thenBy { it.first == YOU },
        )
        return sorted.mapIndexed { i, (n, t) -> Row(i + 1, n, t, state.seasonPoints[n] ?: 0, state.seasonWins[n] ?: 0, n == YOU) }
    }

    fun position(state: GameState): Int = standings(state).first { it.you }.position

    /** What finishing [place] in the final table pays, in entry fees. */
    fun reward(place: Int): Double = REWARDS.getOrElse(place - 1) { 0.0 }

    fun lines(state: GameState): List<String> = standings(state).map {
        "${it.position}. ${if (it.you) "YOU" else it.name}  ${it.team}  ${it.points} pts" + if (it.wins > 0) "  ${it.wins} win${if (it.wins > 1) "s" else ""}" else ""
    }

    /** The result of settling a race: the new state and what to tell the player about the championship. */
    data class Update(val state: GameState, val note: String)

    /**
     * Pays the prize and the engine rebuild, and when the race was the round that was up, scores it for the whole field.
     * Pure: the state in, a state out. [raceCircuit] is the circuit that was raced.
     */
    fun settle(state: GameState, raceCircuit: Circuits.Circuit, outcome: Circuits.Outcome): Update {
        val paid = state.copy(money = (state.money + outcome.prize - outcome.repair).coerceAtLeast(0.0))
        if (raceCircuit.id != next(state).id) {
            return Update(paid, "Free race, no championship points. Round ${state.seasonRound + 1} of $ROUNDS is ${next(state).name}.")
        }
        val points = state.seasonPoints.toMutableMap()
        val wins = state.seasonWins.toMutableMap()
        val finish = outcome.result.grid
        finish.forEachIndexed { i, f ->
            val key = if (f.you) YOU else f.name
            val earned = if (f.out) 0 else POINTS.getOrElse(i) { 0 }
            points[key] = (points[key] ?: 0) + earned
            if (i == 0 && !f.out) wins[key] = (wins[key] ?: 0) + 1
        }
        val mine = outcome.result.grid.indexOfFirst { it.you }.let { if (it < 0 || outcome.result.grid[it].out) 0 else POINTS.getOrElse(it) { 0 } }
        val round = state.seasonRound + 1
        val running = paid.copy(seasonRound = round, seasonPoints = points, seasonWins = wins)
        val head = "Round $round of $ROUNDS: +$mine points."
        if (round < ROUNDS) {
            val pos = position(running)
            return Update(running, "$head You are P$pos in the championship on ${running.seasonPoints[YOU] ?: 0}. Next: ${next(running).name}.")
        }
        val place = position(running)
        val pts = points[YOU] ?: 0
        val bonus = reward(place) * Circuits.entry(state)
        val record = SeasonRecord(state.seasonNo, place, pts, standings(running).first().name.let { if (it == YOU) "You" else it })
        val closed = running.copy(
            money = running.money + bonus,
            seasonNo = state.seasonNo + 1,
            seasonRound = 0,
            seasonPoints = emptyMap(),
            seasonWins = emptyMap(),
            seasonHistory = (state.seasonHistory + record).takeLast(HISTORY),
        )
        val tail = when (place) {
            1 -> "You are the season ${state.seasonNo} champion on $pts points."
            else -> "Season ${state.seasonNo} ends: you finish P$place on $pts points, ${record.champion} the champion."
        }
        return Update(closed, "$head $tail" + if (bonus > 0.0) " Season reward ${formatDollars(bonus)}." else " No season reward.")
    }

    private const val HISTORY = 12

    /** Titles won, from the history. */
    fun titles(state: GameState): Int = state.seasonHistory.count { it.position == 1 }
}
