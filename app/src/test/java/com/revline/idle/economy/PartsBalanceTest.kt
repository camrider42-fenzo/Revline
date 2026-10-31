package com.revline.idle.economy

import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import com.revline.idle.game.Parts
import com.revline.idle.game.RaceDrive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The circuit parts: each one is worth a lot on a lap and only a little on a strip. Measured on the five reference
 * builds at the last rebuild, over all eight circuits, with the neutral driver so the numbers are the car's.
 */
class PartsBalanceTest {
    private val p = 6
    private val builds = DriverBuilds.orientations(p)

    private fun lap(b: DriverBuilds.Build, kit: Map<String, Int>, only: Set<String>? = null): Double {
        val s = b.state.copy(kit = kit)
        val car = Circuits.carOf(s)
        return Circuits.all.filter { only == null || it.id in only }
            .map { Circuits.plan(car, it, Drivers.you).mean / Circuits.parPlan(p, it).mean }.average()
    }

    private fun et(b: DriverBuilds.Build, kit: Map<String, Int>): Double =
        RaceDrive.driveWell(RaceDrive.setup(b.state.copy(kit = kit)).copy(distanceM = 402.3)).time

    /** % quicker on a lap, averaged over the builds. */
    private fun lapGain(kit: Map<String, Int>, only: Set<String>? = null) = builds.map { (1 - lap(it, kit, only) / lap(it, emptyMap(), only)) * 100 }.average()

    /** % quicker over the quarter mile, averaged over the builds. Negative means the part costs ET. */
    private fun etGain(kit: Map<String, Int>) = builds.map { (1 - et(it, kit) / et(it, emptyMap())) * 100 }.average()

    private val corners = setOf("monaco", "hungaroring", "suzuka", "singapore")
    private val report = StringBuilder()

    @Test fun everyTopLevelPartIsABigLapAndASmallStrip() {
        report.appendLine("line  level  lap%  etGain%  cornerLap%")
        for (line in Parts.lines) {
            val n = line.levels.size
            val kit = mapOf(line.id to n)
            val lapG = lapGain(kit); val etG = etGain(kit)
            val cornerG = lapGain(kit, corners)
            report.appendLine("%-11s L%d %+.2f %+.2f %+.2f".format(line.id, n, lapG, etG, cornerG))
            // Slight on the strip, whatever it does.
            assertTrue("${line.id} drag $etG", etG in -2.5..2.5)
            when (line.id) {
                // Cooling is for the engine: it keeps the car running, and barely changes the lap.
                "cooling" -> assertTrue("cooling lap $lapG", lapG in -0.3..1.0)
                // The wing pays at the tight circuits and costs top end: it is the one that slightly slows the strip.
                "aero" -> { assertTrue("aero corners $cornerG", cornerG > 1.5); assertTrue("aero strip $etG", etG < 0.0) }
                else -> assertTrue("${line.id} lap $lapG", lapG > 1.4)
            }
            if (line.id != "aero" && line.id != "cooling") {
                assertTrue("${line.id} lap $lapG vs strip $etG", lapG >= 1.4 * Math.abs(etG))
            }
        }
        File("build/parts-balance.txt").apply { parentFile.mkdirs() }.writeText(report.toString())
    }

    @Test fun weightHelpsTheStripASmallAmountAndTheLapMore() {
        val kit = mapOf("weight" to 3)
        assertTrue(etGain(kit) in 0.8..2.5)
        assertTrue(lapGain(kit) > etGain(kit))
    }

    @Test fun suspensionHelpsTheLaunchSlightlyAndNeverHurtsIt() {
        val g = etGain(mapOf("suspension" to 3))
        assertTrue("suspension strip $g", g in -0.05..1.0)
    }

    @Test fun theMoreYouFitTheQuickerTheLapUpTheLevels() {
        for (line in Parts.lines.filter { it.id != "cooling" }) {
            var last = 0.0
            for (lv in 1..line.levels.size) {
                val g = lapGain(mapOf(line.id to lv))
                assertTrue("${line.id} L$lv $g after $last", g > last + 0.2)
                last = g
            }
        }
    }

    @Test fun noPartDominatesAndNoneIsUseless() {
        val singles = Parts.lines.filter { it.id != "cooling" }.associate { it.id to lapGain(mapOf(it.id to it.levels.size)) }
        val all = lapGain(Parts.lines.associate { it.id to it.levels.size })
        assertTrue("all $all", all in 8.0..25.0)
        for ((id, g) in singles) {
            assertTrue("$id $g of $all", g < 0.3 * all)
            assertTrue("$id $g of $all", g > 0.05 * all)
        }
        // Together they beat the sum of nothing: the parts stack, a little short of a straight sum.
        assertTrue(all > 0.7 * singles.values.sum())
    }

    @Test fun coolingKeepsTheEngineAliveAndThatIsWhatItIsFor() {
        val d = Drivers.roster.first()
        fun engine(level: Int): Double = builds.map { b ->
            val bb = DriverBuilds.Build("c$level", b.state.copy(kit = if (level == 0) emptyMap() else mapOf("cooling" to level)))
            Circuits.all.map { CircuitBalanceSim.cell(p, bb, it, d, 150).engineDnf }.average()
        }.average()
        val none = engine(0); val l1 = engine(1); val l2 = engine(2); val l3 = engine(3)
        assertTrue("engine DNF $none $l1 $l2 $l3", l3 < 0.7 * none && l2 < none && l1 <= none * 1.05)
    }

    @Test fun drivingStillMattersWithAFullKit() {
        val kit = Parts.lines.associate { it.id to minOf(2, it.levels.size) }
        val b = DriverBuilds.Build("kit", builds.first { it.name == "balanced" }.state.copy(kit = kit))
        val best = Drivers.roster.maxBy { it.overall }; val worst = Drivers.roster.minBy { it.overall }
        val cells = Circuits.all.associate { c -> c.id to (CircuitBalanceSim.cell(p, b, c, best, 150) to CircuitBalanceSim.cell(p, b, c, worst, 150)) }
        val better = cells.values.count { it.first.position < it.second.position }
        assertTrue("best driver ahead at $better of 8: " + cells.map { (k, v) -> "$k %.2f/%.2f".format(v.first.position, v.second.position) }, better >= 7)
    }
}
