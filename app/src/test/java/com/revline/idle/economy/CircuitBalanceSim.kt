package com.revline.idle.economy

import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers

/**
 * Monte Carlo over builds x circuits x drivers x seeds. Everything is deterministic: the seed is derived from the
 * indices, so a failure reproduces. Used by [CircuitBalanceTest] and the probe.
 */
object CircuitBalanceSim {
    data class Cell(val position: Double, val dnf: Double, val engineDnf: Double, val crashDnf: Double, val incidents: Double, val win: Double, val net: Double)

    const val SEEDS = 160

    /** Net money per race in units of the entry fee: prize - entry - driver fee - rebuild. */
    fun cell(p: Int, build: DriverBuilds.Build, circuit: Circuits.Circuit, driver: Drivers.Driver, seeds: Int = SEEDS): Cell {
        val car = Circuits.carOf(build.state)
        val plan = Circuits.plan(car, circuit, driver)
        val par = Circuits.parPlan(p, circuit)
        val state = build.state
        val entry = Circuits.entry(state)
        val fee = Circuits.fee(state, driver)
        var pos = 0.0; var dnf = 0; var eng = 0; var crash = 0; var inc = 0; var win = 0; var net = 0.0
        for (s in 0 until seeds) {
            val seed = (circuit.id.hashCode() * 31L + build.name.hashCode()) * 1000003L + driver.id.hashCode() * 7919L + s
            val r = Circuits.race(plan, driver, par, seed)
            pos += r.position
            if (r.retired != null) { dnf++; if (r.engineFailed) eng++ else crash++ }
            inc += r.incidents.size
            if (r.position == 1) win++
            val prize = Circuits.prize(state, r.position, r.retired != null)
            net += prize - entry - fee - (if (r.engineFailed) Circuits.repair(state) else 0.0)
        }
        val n = seeds.toDouble()
        return Cell(pos / n, dnf / n, eng / n, crash / n, inc / n, win / n, net / entry / n)
    }

    fun correlation(xs: List<Double>, ys: List<Double>): Double {
        val mx = xs.average(); val my = ys.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in xs.indices) { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) * (xs[i] - mx); syy += (ys[i] - my) * (ys[i] - my) }
        return sxy / Math.sqrt(sxx * syy)
    }
}
