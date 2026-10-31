package com.revline.idle.economy

import org.junit.Test
import java.io.File

class CircuitSoakProbe {
    @Test fun soak() {
        val t0 = System.currentTimeMillis()
        val o = CircuitSoakRun.run(listOf(2, 3, 4, 5, 6), listOf(0, 1, 2, 3), 40)
        val sb = StringBuilder()
        sb.append("races ${o.total.races} bad ${o.total.bad} in ${(System.currentTimeMillis() - t0) / 1000}s\n")
        o.total.problems.forEach { sb.append("  $it\n") }
        sb.append("BY P/TIER: mean pos, dnf%, engine%, win%, net entries/race\n")
        for (k in o.byTier.keys.sorted()) o.byTier.getValue(k).let { sb.append("  %-8s %.2f %5.1f %5.1f %5.1f %+.2f\n".format(k, it.mean(), it.dnfRate() * 100, it.engRate() * 100, it.winRate() * 100, it.netMean())) }
        sb.append("BY BUILD/TIER\n")
        for (k in o.byBuild.keys.sorted()) o.byBuild.getValue(k).let { sb.append("  %-14s %.2f %5.1f %5.1f %5.1f %+.2f\n".format(k, it.mean(), it.dnfRate() * 100, it.engRate() * 100, it.winRate() * 100, it.netMean())) }
        sb.append("BY DRIVER\n")
        for (k in o.byDriver.keys.sorted()) o.byDriver.getValue(k).let { sb.append("  %-14s %.2f %5.1f %5.1f %5.1f %+.2f\n".format(k, it.mean(), it.dnfRate() * 100, it.engRate() * 100, it.winRate() * 100, it.netMean())) }
        sb.append("\nSINGLE PARTS ON THE STREET BUILD (p=6, all drivers, 8 circuits, 100 seeds): mean pos, dnf%, engine%, net, lap % vs none\n")
        val base = DriverBuilds.orientations(6).first { it.name == "street" }.state
        fun probe(label: String, st: com.revline.idle.data.GameState) {
            val car = com.revline.idle.game.Circuits.carOf(st)
            val t = CircuitSoak.Tally()
            var lapSum = 0.0; var lapN = 0
            for (c in com.revline.idle.game.Circuits.all) {
                val par = com.revline.idle.game.Circuits.parPlan(6, c)
                for (d in com.revline.idle.game.Drivers.roster) {
                    val plan = com.revline.idle.game.Circuits.plan(car, c, d)
                    lapSum += plan.mean / par.mean; lapN++
                    for (sd in 0 until 100) {
                        val r = com.revline.idle.game.Circuits.race(plan, d, par, c.id.hashCode() * 31L + d.id.hashCode() * 7L + sd)
                        val net = (com.revline.idle.game.Circuits.prize(st, r.position, r.retired != null) - com.revline.idle.game.Circuits.entry(st) - com.revline.idle.game.Circuits.fee(st, d) - (if (r.engineFailed) com.revline.idle.game.Circuits.repair(st) else 0.0)) / com.revline.idle.game.Circuits.entry(st)
                        t.add(r.position, r.retired != null, r.engineFailed, net)
                    }
                }
            }
            sb.append("  %-28s pos %.2f dnf %.1f eng %.1f net %+.2f lap %.4f\n".format(label, t.mean(), t.dnfRate() * 100, t.engRate() * 100, t.netMean(), lapSum / lapN))
        }
        probe("nothing", base)
        for (ct in com.revline.idle.game.Racing.circuitTires) probe("ctire ${ct.id}", base.copy(circuitTire = ct.id))
        for (line in com.revline.idle.game.Parts.lines) for (lv in 1..line.levels.size) probe("${line.id} L$lv", base.copy(kit = mapOf(line.id to lv)))
        probe("all L1", base.copy(kit = CircuitSoak.kitAt(6, 1)))
        probe("all L2", base.copy(kit = CircuitSoak.kitAt(6, 2)))
        probe("all L3", base.copy(kit = CircuitSoak.kitAt(6, 3)))
        probe("all L3 + racing tires", base.copy(kit = CircuitSoak.kitAt(6, 3), circuitTire = "racing"))
        File("build/circuit-soak.txt").writeText(sb.toString())
    }
}
