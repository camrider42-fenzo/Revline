package com.revline.idle.game

import java.util.Locale

/**
 * The lap-by-lap record of a circuit race: who ran where at the end of every lap, how far the player was from the
 * car ahead and the leader, and what happened. It is design data as much as a results screen: everything a picture
 * of the race would need (order, gaps, lap times, incidents, pit stops) is in here and nothing is drawn.
 */
object RaceLog {
    /** One car's race as a list of lap times. [outOnLap] is 0 for a car that took the flag, else the lap it stopped on ([times] then ends with the part-lap it did). */
    class CarRun(
        val name: String,
        val team: String,
        val you: Boolean,
        val times: List<Double>,
        val outOnLap: Int = 0,
        /** Laps the car came in at the end of. */
        val pits: Set<Int> = emptySet(),
        /** The compound on each lap. Empty on a run built by hand. */
        val tires: List<Tires.Compound> = emptyList(),
    ) {
        val out: Boolean get() = outOnLap > 0
        /** Whole laps the car completed. */
        val completed: Int get() = if (out) outOnLap - 1 else times.size
        fun total(): Double = times.sum()
        fun cumulative(lap: Int): Double = times.take(lap).sum()
    }

    /** One line of the order after a lap. [gap] is to the leader, 0 for the leader, null for a car that is out. */
    data class Slot(val position: Int, val name: String, val team: String, val you: Boolean, val gap: Double?, val out: Boolean, val pitted: Boolean, val tire: Tires.Compound? = null)

    data class Event(val lap: Int, val text: String)

    /** The race at the end of [lap]. The "your" fields are the player's. */
    data class Row(
        val lap: Int,
        val order: List<Slot>,
        val yourPosition: Int,
        val yourTime: Double,
        val yourOut: Boolean,
        /** Seconds to the car in front, 0 if leading, null if out. */
        val gapAhead: Double?,
        /** Seconds the car behind is back, null if last or out. */
        val gapBehind: Double?,
        val gapLeader: Double?,
        val fastest: Boolean,
        val pitted: Boolean,
        /** The compound the player ran this lap, null on a log built by hand. */
        val yourTire: Tires.Compound?,
        val events: List<Event>,
    )

    data class Log(val rows: List<Row>, val events: List<Event>, val fastestLap: Double, val fastestBy: String) {
        companion object {
            val EMPTY = Log(emptyList(), emptyList(), 0.0, "")
        }
        val laps: Int get() = rows.size
        /** The player's place at the end of each lap. */
        val positions: List<Int> get() = rows.map { it.yourPosition }
    }

    /** Builds the log from every car's laps. [laps] is the race distance. [notes] are extra events (incidents) keyed by lap. */
    fun build(runs: List<CarRun>, laps: Int, notes: List<Event> = emptyList()): Log {
        val you = runs.first { it.you }
        // The fastest lap of the race, not counting the standing-start lap, among laps that were whole.
        var fastest = Double.MAX_VALUE
        var fastestBy = ""
        var fastestLapNo = 0
        for (run in runs) for ((i, t) in run.times.withIndex()) {
            val lapNo = i + 1
            if (lapNo == 1 || (run.out && lapNo == run.outOnLap)) continue
            if (t < fastest) { fastest = t; fastestBy = run.name; fastestLapNo = lapNo }
        }
        if (fastest == Double.MAX_VALUE) { fastest = 0.0 }
        val events = ArrayList<Event>()
        val rows = ArrayList<Row>()
        var lastPlace = 0
        for (lap in 1..laps) {
            val running = runs.filter { it.completed >= lap }
                .sortedWith(compareBy<CarRun> { it.cumulative(lap) }.thenBy { it.name })
            val retired = runs.filter { it.completed < lap }
                .sortedWith(compareByDescending<CarRun> { it.completed }.thenByDescending { it.cumulative(it.completed) })
            val lead = running.firstOrNull()?.cumulative(lap) ?: 0.0
            val slots = ArrayList<Slot>()
            running.forEachIndexed { i, run ->
                slots.add(Slot(i + 1, run.name, run.team, run.you, run.cumulative(lap) - lead, false, lap in run.pits, run.tires.getOrNull(lap - 1)))
            }
            retired.forEachIndexed { i, run -> slots.add(Slot(running.size + i + 1, run.name, run.team, run.you, null, true, false)) }
            val me = slots.first { it.you }
            val myIndex = slots.indexOf(me)
            val ahead = slots.getOrNull(myIndex - 1)?.takeIf { !it.out }
            val behind = slots.getOrNull(myIndex + 1)?.takeIf { !it.out }
            val out = me.out
            val rowEvents = ArrayList<Event>()
            for (run in runs) {
                if (run.out && run.outOnLap == lap) {
                    rowEvents.add(Event(lap, if (run.you) "You are out." else "${run.name} (${run.team}) is out."))
                }
                if (lap in run.pits) {
                    val to = run.tires.getOrNull(lap)
                    val from = run.tires.getOrNull(lap - 1)
                    val change = if (to != null && from != null) ": ${from.title} to ${to.title}" else ""
                    rowEvents.add(Event(lap, if (run.you) "You pit for fresh tires$change." else "${run.name} pits$change."))
                }
            }
            if (lap == fastestLapNo && fastest > 0.0) rowEvents.add(Event(lap, "Fastest lap of the race: ${fastestBy}, ${Circuits.clock(fastest)}."))
            if (!out && lastPlace != 0 && me.position != lastPlace) {
                rowEvents.add(Event(lap, if (me.position < lastPlace) "You move up to P${me.position}." else "You drop to P${me.position}."))
            }
            if (!out) lastPlace = me.position
            rowEvents.addAll(notes.filter { it.lap == lap })
            events.addAll(rowEvents)
            rows.add(
                Row(
                    lap, slots, me.position, you.times.getOrElse(lap - 1) { 0.0 }, out,
                    if (out) null else if (ahead == null) 0.0 else me.gap!! - ahead.gap!!,
                    if (out || behind == null) null else behind.gap!! - me.gap!!,
                    me.gap, lap == fastestLapNo && fastestBy == you.name, lap in you.pits, you.tires.getOrNull(lap - 1),
                    rowEvents.sortedBy { it.text },
                ),
            )
        }
        return Log(rows, events.sortedBy { it.lap }, if (fastest > 0.0) fastest else 0.0, fastestBy)
    }

    private fun sec(x: Double) = String.format(Locale.US, "%.2f", x)

    /** One readable line per lap for the results screen. */
    fun lines(log: Log): List<String> = log.rows.map { r ->
        val lap = "L${r.lap}".padEnd(3)
        if (r.yourOut) "$lap OUT"
        else {
            val gap = when {
                r.gapAhead == null -> ""
                r.yourPosition == 1 -> "leading" + (r.gapBehind?.let { ", +${sec(it)} clear" } ?: "")
                else -> "+${sec(r.gapAhead)} to P${r.yourPosition - 1}, +${sec(r.gapLeader ?: 0.0)} to leader"
            }
            "$lap ${r.yourTire?.letter ?: " "} P${r.yourPosition}  ${Circuits.clock(r.yourTime)}  $gap" + if (r.pitted) "  PIT" else ""
        }
    }

    /** The running order after a lap, surnames only: "Clark, Surtees, YOU, ...". Cars that are out are left off. */
    fun orderLine(row: Row): String =
        row.order.filter { !it.out }.joinToString(", ") { if (it.you) "YOU" else it.name.substringAfterLast(' ') }
}
