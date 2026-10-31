package com.revline.idle.economy

import com.revline.idle.game.DragRivals
import com.revline.idle.game.Drivers
import com.revline.idle.game.RaceDrive
import com.revline.idle.game.Racing
import java.io.File
import java.util.Locale

/**
 * Drag rival probe. For every block and a par player car it reports the rival ET and sixty-foot time the old way
 * (the player's own clean ET scaled by a pace ratio, no sixty-foot at all) and the new way (the rival's own build
 * and hands through the player's sim), with the quickest the physics lets the same build go, and the win rate of a
 * par human (flat 55 hands) before and after. A probe, not a check: it only writes when DRAG_PROBE names a file.
 */
object DragRivalProbe {
    private fun oldSeconds(setup: RaceDrive.Setup, yourPace: Double, theirPace: Double): Double {
        val clean = RaceDrive.driveWell(setup)
        val cap = (90.0 * setup.distanceM / RaceDrive.FINISH_M).coerceIn(40.0, 160.0)
        val perfect = if (clean.distance >= setup.distanceM - 1.0) clean.time else cap
        val ratio = (yourPace / theirPace.coerceAtLeast(1.0)).coerceIn(0.45, 2.2)
        return (perfect * ratio).coerceIn(6.0, cap)
    }

    private fun median(v: List<Double>): Double = v.sorted()[v.size / 2]

    class Row(val kind: String, val p: Int, val build: String, val oldEt: Double, val newEt: Double, val newLo: Double, val newHi: Double,
              val newSixty: Double, val floorEt: Double, val floorSixty: Double, val oldWin: Double, val newWin: Double, val parEt: Double, val parSixty: Double)

    fun rows(seeds: Int = 16, hands: Int = 55, builds: List<String> = listOf("street", "balanced")): List<Row> {
        val out = ArrayList<Row>()
        for (p in 0..7) for (name in builds) {
            val build = DriverBuilds.orientations(p).first { it.name == name }
            val setup = build.setup
            val pace = Racing.car(build.state).pace
            val player = (1..seeds).map { DragRivals.pass(setup, Drivers.reference(hands), 900L + it * 13L) }
            val parEt = median(player.map { it.time }); val parSixty = median(player.map { it.sixtyFt })
            val fields = ArrayList<Pair<String, Racing.Field>>()
            for (tier in Racing.Tier.values()) fields.add(tier.name.lowercase(Locale.US) to Racing.fieldForQuick(build.state, tier))
            val rungs = Racing.ladderRungs(Racing.lengthOf(build.state.raceLength)).size
            fields.add("ladder" to Racing.fieldForLadder(build.state.copy(raceLadder = (p * (rungs / 8) + rungs / 16).coerceAtMost(rungs - 1))))
            for (s in 1..8) fields.add("stake" to Racing.openStake(build.state, 900L + s * 17L)!!.second)
            for ((kind, f) in fields) {
                val old = oldSeconds(setup, pace, f.pace)
                val rival = (1..seeds).map { DragRivals.run(f.rival, setup.distanceM, 100L + it * 7919L) }
                val times = rival.map { it.time }
                val flawless = DragRivals.flawless(f.rival, setup.distanceM)
                var oldWon = 0; var newWon = 0; var n = 0
                for (a in player) for (r in rival) {
                    n++
                    if (a.reached && !a.jumped && a.time <= old) oldWon++
                    if (a.reached && !a.jumped && a.time <= r.time) newWon++
                }
                out.add(Row(kind, p, name, old, median(times), times.min(), times.max(), median(rival.map { it.sixtyFt }),
                    flawless.time, flawless.sixtyFt, oldWon / n.toDouble(), newWon / n.toDouble(), parEt, parSixty))
            }
        }
        return out
    }

    fun report(seeds: Int = 16): String {
        val rows = rows(seeds)
        val sb = StringBuilder()
        sb.append("Drag rival probe: par player = flat 55 hands. ET and 60ft read from the green light (race clock). 'old' is the scripted ET (no 60ft existed).\n")
        sb.append("floor = same rival build with a flawless pilot (what the physics allows); old<floor flags a scripted time the build could not run.\n\n")
        sb.append("blk build    field    | par ET/60  | old ET  old<floor | new ET (lo..hi)   new 60ft  floor ET/60 | par win old->new\n")
        for (r in rows) sb.append(String.format(Locale.US, "p%d  %-8s %-8s | %5.2f/%4.2f | %6.2f  %-9s | %6.2f (%5.2f..%5.2f) %5.2f   %6.2f/%4.2f | %3.0f%% -> %3.0f%%\n",
            r.p, r.build, r.kind, r.parEt, r.parSixty, r.oldEt, if (r.oldEt < r.floorEt - 0.01) "BEATS" else "", r.newEt, r.newLo, r.newHi, r.newSixty, r.floorEt, r.floorSixty, r.oldWin * 100, r.newWin * 100))
        sb.append("\nPer field kind, averaged over blocks and both par builds:\n")
        sb.append("kind      old ET  new ET  new 60ft  floor ET  old<floor  par win old  par win new\n")
        for (kind in listOf("club", "national", "international", "ladder", "stake")) {
            val k = rows.filter { it.kind == kind }
            fun avg(f: (Row) -> Double) = k.map(f).average()
            sb.append(String.format(Locale.US, "%-9s %6.2f  %6.2f  %7.2f   %7.2f   %5.0f%%    %8.0f%%    %8.0f%%\n", kind, avg { it.oldEt }, avg { it.newEt }, avg { it.newSixty }, avg { it.floorEt },
                100.0 * k.count { it.oldEt < it.floorEt - 0.01 } / k.size, avg { it.oldWin } * 100, avg { it.newWin } * 100))
        }
        sb.append(String.format(Locale.US, "\nAll fields: old<floor %.0f%% of rows, par win old %.0f%% new %.0f%%\n",
            100.0 * rows.count { it.oldEt < it.floorEt - 0.01 } / rows.size, rows.map { it.oldWin }.average() * 100, rows.map { it.newWin }.average() * 100))
        return sb.toString()
    }
}

class DragRivalProbeRun {
    @org.junit.Test
    fun write() {
        val out = System.getenv("DRAG_PROBE") ?: return
        File(out).writeText(DragRivalProbe.report((System.getenv("DRAG_SEEDS") ?: "16").toInt()))
    }
}
