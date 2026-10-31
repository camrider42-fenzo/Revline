package com.revline.idle.game

import com.revline.idle.model.Block
import kotlin.math.sqrt

/**
 * The cars you line up beside on the strip.
 *
 * A drag rival has no stored time. It is a build (a [Racing.Spec] with the block's own redline and idle, and a
 * final drive) and a driver ([Skills]). It goes down the strip through [RaceDrive.setupFor], [AutoRace] and
 * [RaceDrive.step], the code the player's car runs, with the same traction check on the launch, the same torque
 * curve, gearing, drag, mass and shift time. What makes a rival slow is its build and its hands: a late reaction, a
 * launch that is off the best throttle, a shift above or below the right rpm. What makes one quick is a good build
 * and good hands. Nothing here can give it a launch or an elapsed time the car does not earn.
 */
object DragRivals {
    /** Sixty feet in metres. */
    const val SIXTY_FT = 18.288

    /** A car as built, and who is driving it. */
    data class Entrant(
        val name: String,
        val spec: Racing.Spec,
        val redline: Double,
        val idle: Double,
        val finalDrive: Double,
        val skills: Skills,
    )

    /** One trip down the strip. Times read from the green light, the same clock the player's result is on. */
    data class Pass(
        val time: Double,
        /** Elapsed time when the car reached sixty feet. */
        val sixtyFt: Double,
        /** The moment the car first rolled. The reaction and the clutch together. */
        val rolled: Double,
        val trapMps: Double,
        val reached: Boolean,
        val jumped: Boolean,
        /** Red lights thrown and re-staged before this clean pass. */
        val restages: Int = 0,
        /** The car's distance down the strip against the race clock, one sample a step, for drawing it. */
        val trace: FloatArray = FloatArray(0),
    ) {
        /** Metres down the strip at race time [t]. Stands at the line once it has crossed. */
        fun distanceAt(t: Double, meters: Double): Double {
            if (trace.isEmpty() || t <= 0.0) return 0.0
            val k = t / DT
            val i = k.toInt()
            if (i >= trace.size - 1) return if (reached) meters else trace.last().toDouble()
            val f = k - i
            return trace[i] + (trace[i + 1] - trace[i]) * f
        }
    }

    /** The block's engine on a given build. */
    fun entrant(name: String, spec: Racing.Spec, block: Block, skill: Double, finalId: String = "road"): Entrant =
        Entrant(name, spec, block.redlineRpm, block.idleRpm, Racing.finalRatio(finalId), Skills(skill, skill, skill, skill))

    /** The strip setup for this build, the same builder as the player's. */
    fun setup(entrant: Entrant, meters: Double): RaceDrive.Setup {
        val spec = entrant.spec
        return RaceDrive.setupFor(
            body = Racing.assess(spec),
            peakHp = spec.hp,
            peakTqLbFt = spec.tq,
            redline = entrant.redline,
            idle = entrant.idle,
            finalDrive = entrant.finalDrive,
            distanceM = meters,
            displacementCc = spec.displacementCc,
        )
    }

    /** One run with [skills] on [setup], timed through sixty feet and the line. Stepped at a fixed 60 Hz. */
    fun pass(setup: RaceDrive.Setup, skills: Skills, seed: Long): Pass {
        val race = AutoRace(setup, skills, seed)
        val run = race.run
        var sixty = Double.NaN
        var rolled = Double.NaN
        var guard = 0
        val samples = arrayListOf(0f)
        while (!race.over && guard < 40_000) {
            guard++
            val before = run.distance
            val t0 = run.time
            race.advance(DT)
            if (race.staging) continue
            samples.add(run.distance.toFloat())
            if (rolled.isNaN() && run.distance >= ROLL_M) rolled = run.time
            if (sixty.isNaN() && run.distance >= SIXTY_FT) {
                val share = if (run.distance > before) (SIXTY_FT - before) / (run.distance - before) else 1.0
                sixty = t0 + (run.time - t0) * share
            }
        }
        val reached = race.reachedLine
        return Pass(
            time = if (reached) run.time else cap(setup),
            sixtyFt = if (sixty.isNaN()) cap(setup) else sixty,
            rolled = if (rolled.isNaN()) cap(setup) else rolled,
            trapMps = run.speed,
            reached = reached,
            jumped = run.jumped,
            trace = samples.toFloatArray(),
        )
    }

    /** The rival's pass. A red light means the starter calls them back to stage again, up to [RESTAGES] times. */
    fun run(entrant: Entrant, meters: Double, seed: Long): Pass {
        val setup = setup(entrant, meters)
        var last = pass(setup, entrant.skills, seed)
        var tries = 0
        while (last.jumped && tries < RESTAGES) {
            tries++
            last = pass(setup, entrant.skills, seed + tries * 977L)
        }
        return last.copy(restages = tries)
    }

    /** The rival's elapsed time on this seed. */
    fun seconds(entrant: Entrant, meters: Double, seed: Long): Double = run(entrant, meters, seed).time

    /** The same build with a flawless driver: the quickest the physics lets this car go in a clean pass. */
    fun flawless(entrant: Entrant, meters: Double, seed: Long = 7_001L): Pass =
        pass(setup(entrant, meters), Skills(1.0, 1.0, 1.0, 1.0), seed)

    /**
     * A time the car cannot beat, from first principles and nothing else of the sim: the car's peak power through the
     * wheels, no drag and no traction loss, over [meters] from rest. The line is no sooner than (3d/2)^(2/3) (m/2P)^(1/3).
     */
    fun powerFloor(setup: RaceDrive.Setup): Double {
        val watts = setup.peakHp * 745.7 * 1.05
        return Math.pow(1.5 * setup.distanceM, 2.0 / 3.0) * Math.pow(setup.massKg / (2.0 * watts), 1.0 / 3.0)
    }

    /** No sooner than this to sixty feet: the most force the engine can put through the first gear, from rest. */
    fun sixtyFloor(setup: RaceDrive.Setup): Double {
        var tq = 0.0
        var rpm = setup.idle
        while (rpm <= setup.redline) {
            tq = maxOf(tq, RaceDrive.torqueNm(setup, rpm))
            rpm += 50.0
        }
        val force = tq * setup.ratios[0] * setup.finalDrive / RaceDrive.WHEEL_R
        val accel = force / setup.massKg
        return sqrt(2.0 * SIXTY_FT / accel)
    }

    private fun cap(setup: RaceDrive.Setup): Double = (96.0 * setup.distanceM / RaceDrive.FINISH_M).coerceIn(48.0, 170.0)

    private const val DT = 1.0 / 60.0
    private const val ROLL_M = 0.15
    const val RESTAGES = 4
}
