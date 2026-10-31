package com.revline.idle.economy

import com.revline.idle.game.AutoRace
import com.revline.idle.game.DragRivals
import com.revline.idle.game.Drivers
import com.revline.idle.game.RaceDrive
import com.revline.idle.game.Racing
import com.revline.idle.game.Skills
import java.io.File
import java.util.Locale

/**
 * The balance harness. Every driver (and a few reference humans) runs every representative build against every
 * kind of field with the real physics, many seeds each. A race is won the way the race screen scores it: through
 * the lights without a red light, and at or under the rival's elapsed time.
 *
 * Money is measured in purses so the table does not depend on the block: a win pays 1 purse, a fee is its rate
 * times the purse, and a stake is paid up front (half of the purse it wins back).
 */
object DriverBalanceSim {
    class Entry(val name: String, val skillsFor: (RaceDrive.Setup) -> Skills, val rate: Double, val tier: Int)

    class Cell(val winRate: Double, val jumps: Int, val seconds: Double, val net: Double)

    class Field(val label: String, val rival: Double, val stake: Boolean, val weight: Double)

    fun fieldsFor(build: DriverBuilds.Build, stakeSeeds: Int = 5): List<Field> {
        val state = build.state
        val setup = build.setup
        val out = ArrayList<Field>()
        val realCount = Racing.Tier.values().size + 1 + stakeSeeds
        fun add(label: String, f: Racing.Field, stake: Boolean = false) =
            out.add(Field(label, rivalTime(f, setup), stake, REAL_SHARE / realCount))
        for (tier in Racing.Tier.values()) add(tier.name.lowercase(Locale.US), Racing.fieldForQuick(state, tier))
        val rungs = Racing.ladderRungs(Racing.lengthOf(state.raceLength)).size
        add("ladder", Racing.fieldForLadder(state.copy(raceLadder = (state.prestiges * (rungs / 8) + rungs / 16).coerceAtMost(rungs - 1))))
        for (s in 1..stakeSeeds) add("stake", Racing.openStake(state, 900L + s * 17L)!!.second, stake = true)
        // Tight races: the rival is a stated share slower than a flawless pilot, so the last few percent of skill decide it.
        val flawless = (1..3).map { AutoRace(setup, Skills(1.0, 1.0, 1.0, 1.0), 31L * it).finish().run.time }.average()
        for (q in TIGHT) out.add(Field("tight", flawless * q, false, (1.0 - REAL_SHARE) / TIGHT.size))
        return out
    }

    /** The rival's elapsed time on this strip: its own build and hands through the sim, averaged over a few fixed seeds. */
    fun rivalTime(f: Racing.Field, setup: RaceDrive.Setup): Double =
        (1..3).map { DragRivals.seconds(f.rival, setup.distanceM, 5_000L + it * 211L) }.average()

    /** Half the weight rides on the game's own fields. The other half is on races a flawless pilot only just wins. */
    const val REAL_SHARE = 0.5
    val TIGHT = listOf(1.02, 1.05, 1.08, 1.12, 1.17)

    fun humans(): List<Entry> = listOf(35, 55, 80).map { lv -> Entry("you@$lv", { Drivers.reference(lv) }, 0.0, 0) }

    fun drivers(): List<Entry> = Drivers.roster.map { d -> Entry(d.id, { Drivers.skills(d, it) }, d.rate, d.tier) }

    /** One driver on one build: [seeds] runs, each scored against every field. */
    fun run(build: DriverBuilds.Build, entry: Entry, fields: List<Field>, seeds: Int): Cell {
        val setup = build.setup
        var winShare = 0.0; var jumps = 0; var time = 0.0; var counted = 0; var net = 0.0
        for (seed in 1..seeds) {
            val race = AutoRace(setup, entry.skillsFor(setup), 40_000L + seed * 7919L).finish()
            val jumped = race.run.jumped
            if (jumped) jumps++ else if (race.reachedLine) { time += race.run.time; counted++ }
            for (f in fields) {
                val won = race.reachedLine && race.run.time <= f.rival
                if (won) winShare += f.weight
                net += f.weight * ((if (won) 1.0 else 0.0) - (if (f.stake) 0.5 else 0.0) - entry.rate)
            }
        }
        return Cell(winShare / seeds, jumps, if (counted == 0) 0.0 else time / counted, net / seeds)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println(report(seeds = 60))
    }

    /** What the last [report] found, for the balance tests to read. */
    class Summary(val bestByBuild: Map<String, String>, val win: Map<String, Double>, val net: Map<String, Double>, val gap: Map<String, Double>, val jump: Map<String, Double>, val bestPaidNet: List<Double>)

    var last: Summary? = null

    fun report(seeds: Int, blocks: IntRange = 0..7): String {
        val entries = humans() + drivers()
        val out = StringBuilder()
        val sumWin = HashMap<String, Double>(); val sumNet = HashMap<String, Double>()
        val sumGap = HashMap<String, Double>(); val sumJump = HashMap<String, Double>()
        val bestByBuild = ArrayList<String>()
        val bestNames = LinkedHashMap<String, String>()
        val bestNets = ArrayList<Double>()
        val perTierBuild = HashMap<String, MutableList<Double>>()
        var cells = 0
        out.append("block build              ").append(entries.joinToString("") { it.name.take(7).padStart(8) }).append("   (win % / ET gap % / net per purse)\n")
        for (p in blocks) for (build in DriverBuilds.forBlock(p)) {
            val fields = fieldsFor(build)
            val results = entries.map { it to run(build, it, fields, seeds) }
            val topTime = results.map { it.second.seconds }.filter { it > 0 }.minOrNull() ?: 1.0
            out.append("p$p ${build.name.padEnd(16)}")
            for ((e, c) in results) {
                out.append(String.format(Locale.US, "%4.0f/%-3.0f", c.winRate * 100, if (c.seconds > 0) (c.seconds / topTime - 1) * 100 else 99.0))
                sumWin.merge(e.name, c.winRate, Double::plus); sumNet.merge(e.name, c.net, Double::plus)
                sumGap.merge(e.name, if (c.seconds > 0) c.seconds / topTime - 1 else 0.5, Double::plus)
                sumJump.merge(e.name, c.jumps.toDouble() / seeds, Double::plus)
            }
            out.append("\n")
            val bestDriver = results.filter { it.first.rate > 0 }.maxByOrNull { it.second.net }!!
            bestByBuild.add("p$p ${build.name}: ${bestDriver.first.name} (net ${"%.2f".format(bestDriver.second.net)})")
            bestNames["p$p ${build.name}"] = bestDriver.first.name
            bestNets.add(bestDriver.second.net)
            cells++
        }
        out.append("\nPer driver, averaged over $cells builds (blocks ${blocks.first}..${blocks.last}):\n")
        out.append("name      tier rate  win%  gapET%  jump%  net/purse\n")
        for (e in entries) out.append(String.format(Locale.US, "%-9s %3d  %4.2f %5.1f %6.1f %6.1f %8.3f\n", e.name, e.tier, e.rate,
            sumWin[e.name]!! / cells * 100, sumGap[e.name]!! / cells * 100, sumJump[e.name]!! / cells * 100, sumNet[e.name]!! / cells))
        last = Summary(bestNames, sumWin.mapValues { it.value / cells }, sumNet.mapValues { it.value / cells }, sumGap.mapValues { it.value / cells }, sumJump.mapValues { it.value / cells }, bestNets)
        out.append("\nBest paid driver per build by net:\n").append(bestByBuild.joinToString("\n")).append("\n")
        return out.toString()
    }
}

class DriverBalanceRun {
    @org.junit.Test
    fun write() {
        // A probe, not a check: only runs when DRIVER_REPORT names the file to write. DriversTest holds the invariants.
        val out = System.getenv("DRIVER_REPORT") ?: return
        val seeds = (System.getenv("DRIVER_SEEDS") ?: "24").toInt()
        File(out).writeText(DriverBalanceSim.report(seeds))
    }
}
