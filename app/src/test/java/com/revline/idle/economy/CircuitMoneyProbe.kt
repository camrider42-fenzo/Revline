package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import com.revline.idle.game.Logic
import com.revline.idle.game.Parts
import com.revline.idle.game.Racing
import com.revline.idle.model.Block
import org.junit.Test
import java.io.File

/** Writes what a player sees in dollars at each rebuild, drag against circuit. Not a gate. */
class CircuitMoneyProbe {
    @Test fun write() {
        val out = StringBuilder()
        for (p in 0..7) {
            val scale = Block.forPrestige(p).upgradePriceScale(p)
            val lo = DriverBuilds.make(p, Racing.Build.MEDIUM, "radial", "close", "lsd", 0.3)
            val mid = DriverBuilds.make(p, Racing.Build.MEDIUM, "radial", "close", "lsd", 0.6)
            val hi = DriverBuilds.make(p, Racing.Build.MEDIUM, "slick", "dog", "irs", 1.0)
            val inc = listOf(lo, mid, hi).map { Logic.sheet(it).incomePerSec }
            val s = mid.copy(money = 0.0)
            val stake = Racing.stakeCost(p)
            fun f(x: Double) = "%.4g".format(x)
            out.appendLine("p=$p scale=${f(scale)} income/s lo=${f(inc[0])} mid=${f(inc[1])} hi=${f(inc[2])}")
            out.appendLine("  drag: stake=${f(stake)} winpays=${f(stake * 2)} club=${f(inc[1] * 50)} national=${f(inc[1] * 160)} intl=${f(inc[1] * 520)}")
            val entry = Circuits.entry(s)
            out.appendLine("  circuit: entry=${f(entry)} winner=${f(Circuits.winnerPurse(s))} podium=${Circuits.podium(s).joinToString("/") { f(it) }} p4=${f(Circuits.prize(s, 4, false))} repair=${f(Circuits.repair(s))}")
            out.appendLine("  fee: " + Drivers.roster.joinToString(" ") { "${it.id}=${f(Circuits.fee(s, it))}" })
            out.appendLine("  entry/income-sec: lo=${f(entry / inc[0])} mid=${f(entry / inc[1])} hi=${f(entry / inc[2])}")
            out.appendLine("  circuit tires: " + Racing.circuitTires.joinToString(" ") { "${it.id}=${f(it.price * scale * Racing.CIRCUIT_PRICE_FACTOR)}" })
            out.appendLine("  drag tires: " + Racing.tires.filter { !it.circuit }.joinToString(" ") { "${it.id}=${f(it.price * scale)}" })
            out.appendLine("  parts: " + Parts.lines.joinToString(" | ") { l -> l.id + ":" + l.levels.joinToString(",") { f(it.price * scale * Racing.CIRCUIT_PRICE_FACTOR) } })
            out.appendLine("  boxes: " + Racing.gearboxes.joinToString(" ") { "${it.id}=${f(it.price * scale)}" })
            out.appendLine("  axles: " + Racing.axles.joinToString(" ") { "${it.id}=${f(it.price * scale)}" })
        }
        File("build/circuit-money.txt").apply { parentFile.mkdirs() }.writeText(out.toString())
    }
}
