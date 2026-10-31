package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import com.revline.idle.game.Parts

/**
 * A big batch of circuit races over circuits x drivers x builds x kit tiers x seeds, checking every result for sanity
 * and keeping the numbers the balance report needs. Deterministic: the seed comes from the indices.
 */
object CircuitSoak {
    class Tally {
        var races = 0L
        var bad = 0L
        val problems = ArrayList<String>()
        var pos = 0.0; var dnf = 0L; var eng = 0L; var net = 0.0; var win = 0L
        fun add(position: Int, retired: Boolean, engine: Boolean, netEntries: Double) {
            races++; pos += position; if (retired) dnf++; if (engine) eng++; net += netEntries; if (position == 1) win++
        }
        fun mean() = pos / races
        fun dnfRate() = dnf.toDouble() / races
        fun engRate() = eng.toDouble() / races
        fun netMean() = net / races
        fun winRate() = win.toDouble() / races
    }

    /** The kit for a tier: tier 1, 2 or 3 is that level of each line, or the best the rebuild allows. */
    fun kitAt(p: Int, tier: Int): Map<String, Int> {
        if (tier == 0) return emptyMap()
        return Parts.lines.associate { line ->
            val allowed = line.levels.indexOfLast { it.minPrestige <= p } + 1
            line.id to minOf(tier, allowed)
        }
    }

    fun check(t: Tally, label: String, r: Circuits.Result, st: GameState) {
        val fieldSize = r.field
        fun bad(why: String) { t.bad++; if (t.problems.size < 20) t.problems += "$label: $why" }
        if (r.position !in 1..fieldSize) bad("position ${r.position}")
        if (!r.totalTime.isFinite() || r.totalTime <= 0.0) bad("total ${r.totalTime}")
        if (r.lapTimes.any { !it.isFinite() || it <= 0.0 }) bad("lap ${r.lapTimes}")
        if (!r.bestLap.isFinite() && r.retired == null) bad("best ${r.bestLap}")
        if (r.grid.size != fieldSize) bad("grid ${r.grid.size}")
        if (r.grid.any { it.time.isNaN() }) bad("grid time")
        val prize = Circuits.prize(st, r.position, r.retired != null)
        if (!prize.isFinite() || prize < 0.0) bad("prize $prize")
    }
}

/** Runs the grid and returns tallies keyed by "p|build|tier", "driver:id" and so on. */
object CircuitSoakRun {
    class Out(val total: CircuitSoak.Tally, val byTier: Map<String, CircuitSoak.Tally>, val byDriver: Map<String, CircuitSoak.Tally>, val byBuild: Map<String, CircuitSoak.Tally>)

    fun run(ps: List<Int>, tiers: List<Int>, seeds: Int, drivers: List<Drivers.Driver> = Drivers.roster): Out {
        val total = CircuitSoak.Tally()
        val byTier = HashMap<String, CircuitSoak.Tally>(); val byDriver = HashMap<String, CircuitSoak.Tally>(); val byBuild = HashMap<String, CircuitSoak.Tally>()
        for (p in ps) for (b in DriverBuilds.orientations(p)) for (tier in tiers) {
            val st = b.state.copy(kit = CircuitSoak.kitAt(p, tier))
            val car = Circuits.carOf(st)
            val entry = Circuits.entry(st)
            for (c in Circuits.all) {
                val par = Circuits.parPlan(p, c)
                for (d in drivers) {
                    val plan = Circuits.plan(car, c, d)
                    val fee = Circuits.fee(st, d)
                    val repair = Circuits.repair(st)
                    for (s in 0 until seeds) {
                        val seed = ((p * 31L + b.name.hashCode()) * 31L + tier) * 1000003L + c.id.hashCode() * 7919L + d.id.hashCode() * 104729L + s
                        val r = Circuits.race(plan, d, par, seed)
                        val label = "p$p ${b.name} t$tier ${c.id} ${d.id} s$s"
                        CircuitSoak.check(total, label, r, st)
                        val net = (Circuits.prize(st, r.position, r.retired != null) - entry - fee - (if (r.engineFailed) repair else 0.0)) / entry
                        val keys = listOf(byTier to "p$p t$tier", byDriver to d.id, byBuild to "${b.name} t$tier")
                        total.add(r.position, r.retired != null, r.engineFailed, net)
                        for ((m, k) in keys) m.getOrPut(k) { CircuitSoak.Tally() }.add(r.position, r.retired != null, r.engineFailed, net)
                    }
                }
            }
        }
        return Out(total, byTier, byDriver, byBuild)
    }
}
