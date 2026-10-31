package com.revline.idle.economy

import com.revline.idle.economy.CircuitBalanceSim.correlation
import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The circuit harness: many sims across builds, circuits and drivers. It asserts the balance rules and writes a
 * summary to build/circuit-balance.txt (and stderr) so the numbers can be read without rerunning anything.
 */
class CircuitBalanceTest {
    private val report = StringBuilder()
    private fun say(s: String = "") { report.appendLine(s) }

    private fun gainOf(p: Int, build: DriverBuilds.Build, c: Circuits.Circuit): Double {
        val par = Circuits.parPlan(p, c).mean
        return (par - Circuits.plan(Circuits.carOf(build.state), c, Drivers.you).mean) / par * 100.0
    }

    @Test fun partsAndCircuitsFavourDifferentBuilds() {
        for (p in listOf(2, 4)) {
            val builds = DriverBuilds.orientations(p).associateBy { it.name }
            val gain = builds.mapValues { (_, b) -> Circuits.all.associate { c -> c.id to gainOf(p, b, c) } }
            say("== lap time vs the field's par car, % quicker, p=$p (builds differ in what they are good at)")
            say("%-12s %s".format("", builds.keys.joinToString(" ") { "%9s".format(it) }))
            for (c in Circuits.all) say("%-12s %s".format(c.id, builds.keys.joinToString(" ") { "%+8.1f%%".format(gain.getValue(it).getValue(c.id)) }))
            // Every real build beats the par street car; power wins outright.
            for (c in Circuits.all) {
                assertTrue("${c.id} power", gain.getValue("power").getValue(c.id) > 10.0)
                assertTrue(gain.getValue("street").getValue(c.id) in -1.0..1.0)
            }
            // The light and the balanced build swap places across the circuits (at p=2, where both are in range).
            if (p == 2) {
                val lightWins = Circuits.all.count { gain.getValue("light").getValue(it.id) > gain.getValue("balanced").getValue(it.id) }
                assertTrue("p=$p light beats balanced at $lightWins circuits", lightWins in 1..7)
                assertTrue(gain.getValue("light").getValue("hungaroring") > gain.getValue("balanced").getValue("hungaroring"))
                assertTrue(gain.getValue("balanced").getValue("monza") > gain.getValue("light").getValue("monza"))
            }
            // How far the light build is ahead of the grip build swings by circuit: Monza wants power, Monaco wants grip.
            val swing = Circuits.all.map { gain.getValue("light").getValue(it.id) - gain.getValue("grip").getValue(it.id) }
            assertTrue("p=$p swing ${swing.max() - swing.min()}", swing.max() - swing.min() > 3.0)
            // A grip-only build gains most where the corners are and fades where the straights are.
            val grip = gain.getValue("grip")
            assertTrue(grip.getValue("hungaroring") > grip.getValue("monza") + 5.0)
            assertTrue(grip.getValue("monaco") > grip.getValue("spa") + 5.0)
            // So the best build for you depends on where you race: the best non-power build is not the same everywhere.
            val best = Circuits.all.associate { c -> c.id to listOf("grip", "light", "balanced").maxBy { gain.getValue(it).getValue(c.id) } }
            say("best of grip/light/balanced: $best")
            if (p == 2) assertTrue("p=$p $best", best.values.toSet().size >= 2)
        }
    }

    @Test fun theDriverRatingPredictsTheResult() {
        val p = 3
        val b = DriverBuilds.orientations(p)[4]
        val all = Drivers.roster // hired drivers only: the player cannot enter a circuit race
        val pos = HashMap<String, MutableList<Double>>(); val net = HashMap<String, MutableList<Double>>(); val dnf = HashMap<String, MutableList<Double>>()
        val wins = HashMap<String, Int>()
        say("== drivers on the par street car, p=$p, ${Circuits.all.size} circuits x 300 seeds: mean position / DNF% / net (entries per race)")
        say("%-12s %s".format("", all.joinToString(" ") { "%-12s".format(it.name.substringAfterLast(' ').take(10)) }))
        for (c in Circuits.all) {
            val cells = all.associateWith { CircuitBalanceSim.cell(p, b, c, it, 300) }
            for ((d, x) in cells) {
                pos.getOrPut(d.id) { ArrayList() }.add(x.position); net.getOrPut(d.id) { ArrayList() }.add(x.net); dnf.getOrPut(d.id) { ArrayList() }.add(x.dnf)
            }
            val best = cells.minBy { it.value.position }.key
            wins[best.id] = (wins[best.id] ?: 0) + 1
            say("%-12s %s".format(c.id, all.joinToString(" ") { d -> val x = cells.getValue(d); "%4.1f/%2d/%+.2f".format(x.position, (x.dnf * 100).toInt(), x.net).padEnd(12) }))
        }
        val meanPos = all.map { pos.getValue(it.id).average() }
        val overall = all.map { it.overall.toDouble() }
        val r = correlation(overall, meanPos)
        say("correlation of overall with mean position: %.2f".format(r))
        say("best driver per circuit (by mean position): " + wins.entries.sortedByDescending { it.value }.joinToString { "${Drivers.roster.firstOrNull { d -> d.id == it.key }?.name} x${it.value}" })
        say("overall / mean pos / mean DNF% / mean net: " + all.joinToString("; ") { "${it.name.substringAfterLast(' ')} ${it.overall}/%.2f/%.0f/%+.2f".format(pos.getValue(it.id).average(), dnf.getValue(it.id).average() * 100, net.getValue(it.id).average()) })
        assertTrue("overall vs position $r", r < -0.85)
        // Nobody is best everywhere.
        assertTrue("wins $wins", wins.values.max() <= 5)
        assertTrue("wins $wins", wins.size >= 3)
        // The ten hired drivers all finish in the field's middle or better on this car, the worst is not a ringer.
        val fastest = meanPos.min(); val slowest = meanPos.max()
        assertTrue(slowest - fastest in 0.8..3.0)
        // Fast drivers retire less than the sloppy fast ones: care and mistakes are in the DNF rate.
        val dnfOf = { id: String -> dnf.getValue(id).average() }
        assertTrue(dnfOf("colette") < dnfOf("ines") && dnfOf("pavel") < dnfOf("tanaka"))
        // The driver is worth his fee on a par car for the top half, never absurdly so for the bottom.
        val netOf = { id: String -> net.getValue(id).average() }
        assertTrue(netOf("colette") > netOf("dusty"))
        assertTrue(netOf("ace") > -0.9 && netOf("dusty") > -1.1)
    }

    @Test fun theParCarRunsAboutFourthToFifthAndRetiresNowAndThen() {
        for (p in listOf(2, 3, 4)) {
            val b = DriverBuilds.orientations(p)[4]
            val cells = Circuits.all.map { CircuitBalanceSim.cell(p, b, it, Drivers.you) }
            val pos = cells.map { it.position }.average(); val dnf = cells.map { it.dnf }.average()
            say("par street car, neutral baseline driver (all 50s, NOT selectable in the game), p=$p: mean position %.2f, DNF %.1f%%, mean net %+.2f entries".format(pos, dnf * 100, cells.map { it.net }.average()))
            assertTrue("p=$p position $pos", pos in 4.0..5.8)
            assertTrue("p=$p dnf $dnf", dnf in 0.03..0.25)
            assertTrue(cells.all { it.position in 3.5..6.5 })
        }
    }

    @Test fun engineCareAndMistakesShowInTheRetirements() {
        val p = 3
        val b = DriverBuilds.orientations(p)[3]
        val c = Circuits.all.first { it.id == "monaco" }
        val careful = Drivers.you.copy(care = 90, mistakes = 15); val reckless = Drivers.you.copy(care = 15, mistakes = 85)
        val a = CircuitBalanceSim.cell(p, b, c, careful, 400); val z = CircuitBalanceSim.cell(p, b, c, reckless, 400)
        say("monaco p=3 balanced build: careful DNF %.1f%% (engine %.1f%%, crash %.1f%%), reckless DNF %.1f%% (engine %.1f%%, crash %.1f%%), incidents %.2f vs %.2f".format(
            a.dnf * 100, a.engineDnf * 100, a.crashDnf * 100, z.dnf * 100, z.engineDnf * 100, z.crashDnf * 100, a.incidents, z.incidents))
        assertTrue(z.engineDnf > a.engineDnf)
        assertTrue(z.crashDnf > a.crashDnf)
        assertTrue(z.incidents > 2 * a.incidents)
        assertTrue(z.position > a.position + 0.5)
    }

    @Test fun feesTrackTheOverallRating() {
        val s = com.revline.idle.data.GameState(prestiges = 3)
        val fees = Drivers.roster.map { Circuits.fee(s.copy(hiredDrivers = setOf(it.id), driverId = it.id), it) }
        val overall = Drivers.roster.map { it.overall.toDouble() }
        val r = correlation(overall, fees)
        say("correlation of overall with the circuit fee: %.2f".format(r))
        assertTrue(r > 0.8)
        assertEquals(Drivers.roster.size, fees.size)
        // The dearest driver costs less than the winner's purse: the fee is a share of it.
        assertTrue(fees.max() < Circuits.winnerPurse(s))
    }

    @org.junit.After fun write() {
        val f = File("build/circuit-balance.txt")
        f.parentFile?.mkdirs()
        f.appendText(report.toString() + "\n")
        System.err.println(report)
    }
}
