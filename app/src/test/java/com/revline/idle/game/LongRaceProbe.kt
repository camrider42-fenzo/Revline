package com.revline.idle.game

import com.revline.idle.economy.DriverBuilds
import com.revline.idle.game.Tires.Compound
import org.junit.Test
import java.io.File
import java.util.Locale

/** Writes the numbers behind the race-length change to app/build/longrace-probe.txt: wear, best plans, one stop against two, speed. */
class LongRaceProbe {
    private val d = Drivers.byId("colette")!!
    private fun car(tire: String, p: Int = 3) = Circuits.carOf(DriverBuilds.make(p, Racing.Build.MEDIUM, "street", "close", "lsd", 0.8, ctire = tire))
    private fun f(x: Double) = String.format(Locale.US, "%.3f", x)
    private fun pc(x: Double?) = if (x == null) "  - " else String.format(Locale.US, "%3.0f%%", x * 100)

    private fun plan(a: Circuits.Advice, laps: Int): String {
        val o = a.orders
        val stops = o.fit.stops
        val comps = if (stops.size == 2) "${o.start.name[0]}>${o.middle.name[0]}>${o.finish.name[0]}" else "${o.start.name[0]}>${o.finish.name[0]}"
        return "%-5s stops L%-8s %s  grip@stop %s %s flag %s".format(comps, stops.joinToString("+"), o.pace.name.take(4), pc(a.gripAtStop), pc(a.gripAtStop2), pc(a.gripAtFlag))
    }

    @Test fun probe() {
        val sb = StringBuilder()
        sb.append("== laps per circuit (was 3-6): lap km, F1 laps, laps, stretch\n")
        for (c in Circuits.all) sb.append("%-12s %.3f km  F1 %2d  laps %2d (was %d)  stretch %.2f  km %.0f\n".format(c.id, c.lapKm, c.f1Laps, c.laps, c.baseLaps, c.stretch, c.laps * c.lapKm))
        sb.append("\n== grip at the flag if one compound ran the whole race (steady), street/semi/racing tires\n")
        for (t in listOf("street", "semi", "racing")) for (c in Circuits.all) {
            val g = Circuits.noStopGrip(car(t), c, d, Circuits.Pace.STEADY)
            sb.append("%-7s %-12s S %s  M %s  H %s\n".format(t, c.id, pc(g[Compound.SOFT]), pc(g[Compound.MEDIUM]), pc(g[Compound.HARD])))
        }
        sb.append("\n== best plans per circuit (semi tires, dry): best overall, best 1-stop, best 2-stop, and the gap\n")
        for (w in listOf(Circuits.Weather.DRY, Circuits.Weather.DAMP)) {
            sb.append("-- $w\n")
            for (c in Circuits.all) {
                val adv = Circuits.advise(car("semi"), c, d, w)
                val best = adv.first()
                val one = adv.first { it.orders.stopCount == 1 }
                val two = adv.firstOrNull { it.orders.stopCount == 2 }
                sb.append("%-12s laps %2d BEST %s\n".format(c.id, c.laps, plan(best, c.laps)))
                sb.append("%-12s            1-stop %s\n".format("", plan(one, c.laps)))
                if (two != null) sb.append("%-12s            2-stop %s  vs 1-stop %+.2f%% of race\n".format("", plan(two, c.laps), (two.raceTime / one.raceTime - 1) * 100))
            }
        }
        sb.append("\n== best 1-stop stop lap by compound pair (semi, steady, dry)\n")
        for (c in Circuits.all) {
            val car = car("semi")
            val line = StringBuilder("%-12s laps %2d ".format(c.id, c.laps))
            for (a in listOf(Compound.SOFT, Compound.MEDIUM, Compound.HARD)) for (b in listOf(Compound.SOFT, Compound.MEDIUM, Compound.HARD)) {
                if (a == b) continue
                val best = (1 until c.laps).minByOrNull { Circuits.forecast(car, c, d, Circuits.Orders(a, it, b)).raceTime }!!
                line.append(" ${a.name[0]}>${b.name[0]}:L$best")
            }
            sb.append(line).append('\n')
        }
        sb.append("\n== 1-stop vs 2-stop by tire level and pace (best of each, dry, % of race time; negative = two stops quicker)\n")
        for (t in listOf("street", "racing")) for (c in Circuits.all) {
            val adv = Circuits.advise(car(t), c, d)
            val line = StringBuilder("%-7s %-12s".format(t, c.id))
            for (pace in Circuits.Pace.entries) {
                val one = adv.filter { it.orders.stopCount == 1 && it.orders.pace == pace }.minOf { it.raceTime }
                val two = adv.filter { it.orders.stopCount == 2 && it.orders.pace == pace }.minOf { it.raceTime }
                line.append("  ${pace.name.take(4)} %+.2f%%".format((two / one - 1) * 100))
            }
            sb.append(line).append('\n')
        }
        sb.append("\n== which plan wins overall (best pace) by tire level and weather: stops of the best plan, and its edge over the best of the other stop count (%% of race)\n")
        for (w in listOf(Circuits.Weather.DRY, Circuits.Weather.DAMP)) for (c in Circuits.all) {
            val line = StringBuilder("%-5s %-12s".format(w.name, c.id))
            for (t in listOf("street", "sport", "semi", "racing")) {
                val adv = Circuits.advise(car(t), c, d, w)
                val one = adv.first { it.orders.stopCount == 1 }
                val two = adv.first { it.orders.stopCount == 2 }
                line.append("  %s %s %+.2f%%".format(t.take(4), if (two.raceTime < one.raceTime) "2-stop" else "1-stop", (two.raceTime / one.raceTime - 1) * 100))
            }
            sb.append(line).append('\n')
        }
        sb.append("\n== how far the paper model is from the exact plan (top picks: exact quickest vs exhaustive on 1-stop, semi steady)\n")
        for (c in Circuits.all) {
            val car = car("semi")
            val adv = Circuits.advise(car, c, d).filter { it.orders.pace == Circuits.Pace.STEADY && it.orders.stopCount == 1 }.first()
            val ex = Tires.options(c.laps, Circuits.Weather.DRY).filter { it.stops.size == 1 }.minOf { Circuits.forecast(car, c, d, Circuits.Orders(it.start, it.stop, it.finish)).raceTime }
            sb.append("%-12s advise %.3f  exhaustive %.3f  gap %.4f%%\n".format(c.id, adv.raceTime, ex, (adv.raceTime / ex - 1) * 100))
        }
        sb.append("\n== speed (ms): plan, race, advise\n")
        for (c in Circuits.all) {
            val car = car("street")
            repeat(5) { Circuits.plan(car, c, d) }
            val t0 = System.nanoTime(); repeat(40) { Circuits.plan(car, c, d) }; val pl = (System.nanoTime() - t0) / 40 / 1e6
            val par = Circuits.parPlan(3, c); val p = Circuits.plan(car, c, d)
            repeat(50) { Circuits.race(p, d, par, it.toLong()) }
            val t1 = System.nanoTime(); repeat(500) { Circuits.race(p, d, par, it.toLong()) }; val rr = (System.nanoTime() - t1) / 500 / 1e6
            Circuits.advise(car, c, d)
            val t2 = System.nanoTime(); val adv = Circuits.advise(car, c, d); val ad = (System.nanoTime() - t2) / 1e6
            sb.append("%-12s laps %2d plan %.2f race %.3f advise %.0f (%d plans)\n".format(c.id, c.laps, pl, rr, ad, adv.size))
        }
        File("build").mkdirs()
        File("build/longrace-probe.txt").writeText(sb.toString())
    }
}
