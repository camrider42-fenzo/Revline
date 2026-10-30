package com.revline.idle.game

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** What a driver can do, each 0..1. Setup-adjusted by [Drivers.skills]. */
data class Skills(
    val throttle: Double,
    val clutch: Double,
    val shifting: Double,
    val dyno: Double,
)

/**
 * A driver's hands and feet. It writes the same four controls the player has (throttle pedal,
 * clutch pedal, shift edge) and then the real [RaceDrive] decides what happens. Nothing here rolls
 * for the outcome. A driver's skill only changes how well the pedals are placed and how late they move.
 *
 * - Throttle control: how steadily the foot holds the launch throttle, how much margin it leaves
 *   under the tires' limit, and how fast it lifts when the tires go.
 * - Clutch control: how close it sits to the bite point on the tree (the jump-start risk lives in that
 *   margin), how soon it reacts to green, and how smoothly it feeds the pedal out. A grabby box makes
 *   all of that harder.
 * - Shifting speed: how soon it moves once the shift point is reached, and how fast the dip, the
 *   gear change hand-off and the release go.
 * - Dyno knowledge: how well it knows the torque curve. That sets its read on the launch throttle
 *   the tires can take and where each upshift pays.
 */
class Pilot(private val setup: RaceDrive.Setup, val skills: Skills, seed: Long) {
    private val rng = Random(seed)

    private fun gauss(): Double {
        val u = rng.nextDouble().coerceAtLeast(1e-9)
        return sqrt(-2.0 * ln(u)) * cos(2.0 * Math.PI * rng.nextDouble())
    }

    private val grab = Drivers.grabbiness(setup)
    private val loseT = 1.0 - skills.throttle
    private val loseC = 1.0 - skills.clutch
    private val loseS = 1.0 - skills.shifting
    private val loseD = 1.0 - skills.dyno

    // Launch throttle: the true limit, as this driver reads it, then backed off by his own foot noise.
    private val plan = Drivers.launchPlan(setup)
    private val footNoise = 0.20 * loseT
    private val read = (plan.throttle * (1.0 + gauss() * 0.22 * loseD)).coerceIn(0.12, 1.0)
    private val aim = if (plan.slip || read >= 0.99) 1.0 else (read * (1.0 - 1.4 * footNoise)).coerceIn(0.1, 1.0)

    // A launch the tires cannot take on throttle alone is fed on the clutch instead. How much slip, as this driver reads it.
    private var hold = if (plan.slip) {
        (plan.pedal + gauss() * (0.012 + 0.07 * loseD)).coerceIn(RaceDrive.BITE_FULL, RaceDrive.BITE_START)
    } else {
        0.0
    }
    private val spinReact = 0.04 + 0.30 * loseT

    // Tree: where the pedal sits. The margin over the bite point is sized to the driver's own wobble.
    private val clutchNoise = (0.008 + 0.075 * loseC * (1.0 + 0.5 * grab))
    private val sitPedal = (RaceDrive.BITE_START + 0.03 + clutchNoise + gauss() * clutchNoise).coerceIn(0.3, 1.0)
    private val reaction = (0.09 + 0.22 * loseC + gauss() * (0.015 + 0.05 * loseC)).coerceAtLeast(0.07)
    private val launchRelease = 0.05 + 0.40 * Math.pow(loseC, 1.2) * (1.0 + 0.6 * grab)

    // Shifts.
    private val shiftRpm = DoubleArray(setup.ratios.size) { gear ->
        val best = Drivers.optimalShiftRpm(setup, gear + 1)
        val feel = 1.0 + gauss() * 0.06 * loseD - 0.02 * loseD
        (best * feel).coerceIn(setup.redline * 0.5, setup.redline * 1.02)
    }
    private val latency get() = (0.02 + 0.30 * loseS) * (1.0 + 0.3 * gauss()).coerceIn(0.5, 1.8)
    private val dipSeconds = 0.03 + 0.28 * loseS
    private val settleSeconds = 0.01 + 0.16 * loseS
    private val releaseSeconds = 0.04 + 0.26 * Math.pow(loseC, 1.1) * (1.0 + 0.7 * grab) + 0.20 * loseS

    var throttle = 0.0
        private set

    /** The pedal the sim sees: travel 0 engaged, 1 on the floor. */
    var pedal = 1.0
        private set
    var shift = false
        private set

    private var wobble = 0.0
    private var pedalWobble = 0.0
    private var spinFor = 0.0
    private var sinceGreen = 0.0
    private var base = 1.0
    private var released = false

    private enum class Phase { DRIVE, WAIT, DIP, SHIFT_HOLD, SETTLE, RELEASE }
    private var phase = Phase.DRIVE
    private var timer = 0.0
    private var pending = 0.0
    private var shiftGear = 0

    /** On the tree: hold the staging revs, creep to the bite point once the lights start. */
    fun stage(run: RaceDrive.Run, countdown: Float, dt: Double) {
        shift = false
        throttle = aim
        val creep = if (countdown > 0f) 1.0 else sitPedal
        // The foot comes up at 1.2 per second, so the pedal moves like a person's.
        base = if (base > creep) (base - 1.2 * dt).coerceAtLeast(creep) else creep
        if (run.jumped) base = 1.0
        pedal = base
    }

    /** On the strip, one slice. Call after green. */
    fun drive(run: RaceDrive.Run, dt: Double) {
        shift = false
        sinceGreen += dt
        wobble += (-wobble * dt / 0.15) + footNoise * sqrt(2.0 * dt / 0.15) * gauss()
        pedalWobble += (-pedalWobble * dt / 0.08) + clutchNoise * 0.5 * sqrt(2.0 * dt / 0.08) * gauss()
        val launching = run.gear == 1 && run.speed < 14.0 && !run.hooked
        if (launching) {
            if (run.spinning) spinFor += dt else spinFor = 0.0
            val target = if (spinFor > spinReact) aim * 0.55 else aim
            throttle = (target + (if (aim < 0.99) wobble else 0.0)).coerceIn(0.0, 1.0)
        } else {
            throttle = (throttle + 8.0 * dt).coerceAtMost(1.0)
        }
        if (!released) {
            if (sinceGreen >= reaction) {
                if (launching && plan.slip && run.spinning && spinFor > spinReact) {
                    hold = (hold + 0.6 * dt).coerceAtMost(RaceDrive.BITE_START)
                }
                // Through the bite band over the driver's own release time, and quickly through the dead travel.
                val inBand = base < RaceDrive.BITE_START
                val rate = if (inBand) (RaceDrive.BITE_START - RaceDrive.BITE_FULL) / launchRelease else 8.0
                val floor = if (launching && plan.slip) hold else 0.0
                base = if (base > floor) (base - rate * dt).coerceAtLeast(floor) else (base + 0.9 * dt).coerceAtMost(floor)
                if (base <= RaceDrive.BITE_FULL) {
                    base = 0.0
                    released = true
                }
            }
            pedal = if (base in 0.001..RaceDrive.BITE_START) (base + pedalWobble).coerceIn(0.0, 1.0) else base
            return
        }
        shiftMachine(run, dt)
        pedal = base
    }

    private fun shiftMachine(run: RaceDrive.Run, dt: Double) {
        val last = run.gear >= setup.ratios.size
        when (phase) {
            Phase.DRIVE -> {
                if (!last && run.hooked && run.shiftLeft <= 0.0 && run.rpm >= shiftRpm[run.gear - 1]) {
                    phase = Phase.WAIT
                    timer = 0.0
                    pending = latency
                    shiftGear = run.gear
                }
            }
            Phase.WAIT -> {
                timer += dt
                if (timer >= pending) {
                    phase = Phase.DIP
                    timer = 0.0
                }
            }
            Phase.DIP -> {
                base = (base + dt / dipSeconds).coerceAtMost(1.0)
                if (base > RaceDrive.SHIFT_PEDAL + 0.02) {
                    shift = true
                    phase = Phase.SHIFT_HOLD
                    timer = 0.0
                }
            }
            Phase.SHIFT_HOLD -> {
                if (run.gear > shiftGear && run.shiftLeft <= 0.0) {
                    phase = Phase.SETTLE
                    timer = 0.0
                } else if (run.gear == shiftGear && run.shiftLeft <= 0.0 && timer > 0.2) {
                    // The edge did not take (base not far enough). Dip again.
                    shift = true
                    timer = 0.0
                } else {
                    timer += dt
                }
            }
            Phase.SETTLE -> {
                timer += dt
                if (timer >= settleSeconds) {
                    phase = Phase.RELEASE
                    timer = 0.0
                }
            }
            Phase.RELEASE -> {
                // Quick through the dead part of the pedal, the driver's own pace through the bite band.
                val inBand = base < RaceDrive.BITE_START
                val rate = if (inBand) (RaceDrive.BITE_START - RaceDrive.BITE_FULL) / releaseSeconds else 9.0
                base = (base - rate * dt).coerceAtLeast(0.0)
                if (base <= RaceDrive.BITE_FULL) {
                    base = 0.0
                    phase = Phase.DRIVE
                }
            }
        }
    }
}

/**
 * One automatic run from the tree to the line, stepped exactly the way the race screen steps a human:
 * a countdown and a light tree on [RaceDrive.stage], then [RaceDrive.step]. The screen drives it frame by frame
 * and the balance harness drives it at a fixed step. Both get the same physics and the same pilot.
 */
class AutoRace(val setup: RaceDrive.Setup, skills: Skills, seed: Long) {
    val run: RaceDrive.Run = RaceDrive.fresh(setup)
    private val pilot = Pilot(setup, skills, seed)
    var countdown = COUNTDOWN_S
        private set
    var lights = LIGHTS_S
        private set
    val limit: Double = (96.0 * setup.distanceM / RaceDrive.FINISH_M).coerceIn(48.0, 170.0)
    val throttle: Double get() = pilot.throttle
    val clutch: Double get() = pilot.pedal
    val staging: Boolean get() = countdown > 0f || lights > 0f
    val over: Boolean get() = run.jumped || run.distance >= setup.distanceM || run.time >= limit

    fun advance(dt: Double) {
        if (staging) {
            if (countdown > 0f) countdown -= dt.toFloat() else lights -= dt.toFloat()
            pilot.stage(run, countdown, dt)
            RaceDrive.stage(setup, run, pilot.throttle, dt, pilot.pedal)
            return
        }
        pilot.drive(run, dt)
        val shift = pilot.shift && pilot.pedal > RaceDrive.SHIFT_PEDAL
        RaceDrive.step(setup, run, pilot.throttle, shift, dt, pilot.pedal)
    }

    /** Runs to the line (or the limit, or a red light) at a fixed step. */
    fun finish(dt: Double = 1.0 / 60.0): AutoRace {
        var guard = 0
        while (!over && guard < 40_000) {
            advance(dt)
            guard++
        }
        return this
    }

    val reachedLine: Boolean get() = run.distance >= setup.distanceM && !run.jumped

    companion object {
        const val COUNTDOWN_S = 5f
        const val LIGHTS_S = 1.35f
    }
}
