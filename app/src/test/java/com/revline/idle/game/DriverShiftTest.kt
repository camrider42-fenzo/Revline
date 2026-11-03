package com.revline.idle.game

import com.revline.idle.data.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A driver changes up through every gear on the strip, whatever the shift rpm and the launch are tuned to. */
class DriverShiftTest {
    private fun strip(hp: Double, kit: Map<String, Int>, tune: Map<String, Double>): RaceDrive.Setup {
        val s = GameState(prestiges = 6, chassis = "seven", transmission = "close", driveline = "lsd", tire = "slick", kit = kit, dragTune = tune)
        val rig = DragTune.rig(s)
        return RaceDrive.setupFor(Racing.car(s), hp, hp * 0.9, 7500.0, 900.0, Racing.finalRatio("road") + rig.finalTrim, 402.3, 4000.0, false, rig)
    }

    /**
     * The gears the pass went through, in order, whether it got to the line, and the time the needle spent at the shift point in the gear
     * it finished in. A pilot who reaches the shift point under 0.6 s before the flag (a late shift on a slow car, a slow hand) has not failed to shift.
     */
    private class Pass(val gears: List<Int>, val line: Boolean, val secondsAtThePoint: Double)

    private fun pass(setup: RaceDrive.Setup, level: Int, seed: Long): Pass {
        val race = AutoRace(setup, Drivers.reference(level), seed)
        val seen = arrayListOf(1)
        val point = setup.redline - RevLimiter.HOLD_BELOW - 5.0
        var atPoint = 0.0
        var guard = 0
        while (!race.over && guard++ < 40_000) {
            race.advance(1.0 / 60.0)
            if (race.staging) continue
            if (race.run.gear != seen.last()) { seen.add(race.run.gear); atPoint = 0.0 }
            if (race.run.rpm >= point - 1.0) atPoint += 1.0 / 60.0
        }
        return Pass(seen, race.reachedLine, atPoint)
    }

    @Test fun theDriverUpshiftsThroughEveryGearAcrossTheWholeShiftAndLaunchRange() {
        val shifter = mapOf("shifter" to 1, "suspension" to 1)
        val brake = shifter + mapOf("tbrake" to 1)
        val launchControl = brake + mapOf("launch" to 3)
        val bad = ArrayList<String>()
        for (hp in listOf(300.0, 900.0)) for ((name, kit) in listOf("shifter" to shifter, "brake" to brake, "control" to launchControl)) {
            val launches = if (kit.containsKey("tbrake")) listOf(-1800.0, 0.0, 800.0) else listOf(0.0)
            for (launch in launches) for (trim in -1500..600 step 100) for (level in listOf(20, 100)) {
                val tune = mapOf("shift" to trim.toDouble(), "launch" to launch)
                val setup = strip(hp, kit, tune)
                val p = pass(setup, level, 7L)
                val all = p.gears == (1..setup.ratios.size).toList()
                val shortOfThePoint = p.gears == (1 until setup.ratios.size).toList() && p.secondsAtThePoint < 0.6
                if (!p.line || !(all || shortOfThePoint)) bad.add("hp$hp $name launch$launch shift$trim skill$level gears=${p.gears} atPoint=${p.secondsAtThePoint} line=${p.line}")
            }
        }
        assertTrue("${bad.size} passes did not shift through the gears: ${bad.take(8)}", bad.isEmpty())
    }
}
