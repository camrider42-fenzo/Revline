package com.revline.idle.economy

import com.revline.idle.game.*
import org.junit.Test
import java.io.File

/** Slow (about four minutes); remove @Ignore to regenerate build/strategy-balance.txt. */
@org.junit.Ignore("slow balance probe")
class StrategyBalanceProbe {
    private fun net(st: com.revline.idle.data.GameState, c: Circuits.Circuit, d: Drivers.Driver, o: Circuits.Orders, w: Circuits.Weather, seeds: Int): Double {
        val car = Circuits.carOf(st)
        val p = Circuits.plan(car, c, d, o.strategy, o.pace, w)
        val par = Circuits.parPlan(st.prestiges, c, w)
        var s = 0.0
        for (sd in 0 until seeds) {
            val r = Circuits.race(p, d, par, c.id.hashCode() * 31L + d.id.hashCode() * 7L + sd + w.ordinal * 1000003L)
            s += (Circuits.prize(st, r.position, r.retired != null) - Circuits.entry(st) - Circuits.fee(st, d) - (if (r.engineFailed) Circuits.repair(st) else 0.0)) / Circuits.entry(st)
        }
        return s / seeds
    }

    @Test fun probe() {
        val sb = StringBuilder()
        val allOrders = Circuits.Strategy.entries.flatMap { s -> Circuits.Pace.entries.map { Circuits.Orders(s, it) } }
        val seeds = 30
        for (p in listOf(3, 6)) for (b in DriverBuilds.orientations(p)) for (tier in listOf(0, 3)) {
            sb.append("p$p ${b.name} t$tier\n")
            for (ct in Racing.circuitTires) {
                val st = b.state.copy(kit = CircuitSoak.kitAt(p, tier), circuitTire = ct.id)
                val line = StringBuilder("  %-7s".format(ct.id))
                for (w in Circuits.Weather.entries) {
                    val cells = allOrders.map { o -> o to Circuits.all.flatMap { c -> Drivers.roster.map { d -> net(st, c, d, o, w, seeds) } }.average() }
                    val def = cells.first { it.first == Circuits.Orders() }.second
                    val best = cells.maxByOrNull { it.second }!!
                    val worst = cells.minByOrNull { it.second }!!
                    line.append("  %s: def %+.2f best %+.2f (%s/%s) worst %+.2f |".format(w, def, best.second, best.first.strategy, best.first.pace, worst.second))
                }
                sb.append(line).append('\n')
            }
        }
        // per-circuit and per-driver spread of the default for the par street car and balanced tier 3 (weather in the actual calendar)
        sb.append("\nSEASON: reward vs income, the player on defaults and on advice, balanced p6 t3 street tires\n")
        val b = DriverBuilds.orientations(6).first { it.name == "balanced" }
        val st = b.state.copy(kit = CircuitSoak.kitAt(6, 3))
        val entry = Circuits.entry(st)
        for (d in Drivers.roster) {
            var income = 0.0; var titles = 0; var pos = 0.0; val seasons = 200
            for (se in 0 until seasons) {
                var g = st.copy(circuitRaces = se * 8)
                var gross = 0.0
                repeat(8) {
                    val c = Season.next(g)
                    val w = Circuits.weatherFor(g.circuitRaces, c.id)
                    val car = Circuits.carOf(g)
                    val o = Circuits.advise(car, c, d, w).first().let { Circuits.Orders(it.strategy, it.pace) }
                    val plan = Circuits.plan(car, c, d, o.strategy, o.pace, w)
                    val r = Circuits.race(plan, d, Circuits.parPlan(6, c, w), se * 977L + g.circuitRaces)
                    val pz = Circuits.prize(g, r.position, r.retired != null)
                    val cost = entry + Circuits.fee(g, d) + (if (r.engineFailed) Circuits.repair(g) else 0.0)
                    gross += pz - cost
                    val out = Circuits.Outcome(r, pz, 0.0, 0.0, 0.0, "")
                    g = Season.settle(g.copy(money = g.money + cost), c, out).state.copy(circuitRaces = g.circuitRaces + 1)
                }
                val rec = g.seasonHistory.last()
                if (rec.position == 1) titles++
                pos += rec.position
                income += gross
            }
            sb.append("  %-10s mean season net (no reward) %+.1f entries, mean final pos %.2f, titles %.0f%%\n".format(d.id, income / seasons / entry, pos / seasons, titles * 100.0 / seasons))
        }
        File("build/strategy-balance.txt").writeText(sb.toString())
    }
}
