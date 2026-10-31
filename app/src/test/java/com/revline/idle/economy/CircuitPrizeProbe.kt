package com.revline.idle.economy

import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import org.junit.Test
import java.io.File

/** Position spread of the par street car and of strong builds, and the prize EV under the current multiples. */
class CircuitPrizeProbe {
    @Test fun write() {
        val out = StringBuilder()
        for (p in listOf(0, 3, 6)) {
            for (name in listOf("street", "balanced", "power")) {
                val b = DriverBuilds.orientations(p).first { it.name == name }
                val car = Circuits.carOf(b.state)
                for (label in listOf("roster", "you")) {
                    val drivers = if (label == "you") listOf(Drivers.you) else Drivers.roster
                    val count = DoubleArray(9) // 1..8, index 0 = DNF
                    var n = 0; var eng = 0
                    for (c in Circuits.all) for (d in drivers) {
                        val plan = Circuits.plan(car, c, d); val par = Circuits.parPlan(p, c)
                        for (sd in 0 until 200) {
                            val r = Circuits.race(plan, d, par, c.id.hashCode() * 31L + d.id.hashCode() * 7L + sd)
                            if (r.retired != null) count[0]++ else count[r.position]++
                            if (r.engineFailed) eng++
                            n++
                        }
                    }
                    val pr = count.map { it / n }
                    val old = listOf(3.2, 2.2, 1.6, 1.2, 0.8, 0.45, 0.2, 0.0)
                    val ev = (1..8).sumOf { pr[it] * old[it - 1] }
                    val evNew = (1..3).sumOf { pr[it] * Circuits.prizes[it - 1] }
                    out.appendLine("p=$p %-8s %-6s DNF %.3f eng %.3f | ".format(name, label, pr[0], eng.toDouble() / n) + (1..8).joinToString(" ") { "%.3f".format(pr[it]) } + " | EV old=%.3f new=%.3f  top3=%.3f".format(ev, evNew, pr[1] + pr[2] + pr[3]))
                }
            }
        }
        File("build/circuit-prize-ev.txt").apply { parentFile.mkdirs() }.writeText(out.toString())
    }
}
