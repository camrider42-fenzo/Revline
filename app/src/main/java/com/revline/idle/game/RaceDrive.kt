package com.revline.idle.game

import com.revline.idle.data.GameState
import kotlin.math.exp
import kotlin.math.pow

/**
 * A quarter-mile you have to drive. The launch spins if the throttle asks for
 * more than the tires can hold. The box you fitted sets the ratios and how long
 * a shift takes. Sitting on the limiter does not count as a shift.
 */
object RaceDrive {
    const val FINISH_M = 402.3
    const val WHEEL_R = 0.33

    data class Setup(
        val peakHp: Double,
        val peakTqLbFt: Double,
        val redline: Double,
        val idle: Double,
        val massKg: Double,
        val ratios: List<Double>,
        val finalDrive: Double,
        val efficiency: Double,
        val gripN: Double,
        val drag: Double,
        val shiftSeconds: Double,
    )

    data class Run(
        var gear: Int = 1,
        var rpm: Double = 900.0,
        var speed: Double = 0.0,
        var distance: Double = 0.0,
        var shiftLeft: Double = 0.0,
        var time: Double = 0.0,
        var spinning: Boolean = false,
        var limiter: Boolean = false,
        var hooked: Boolean = false,
    )

    fun setup(state: GameState): Setup {
        val sheet = Logic.sheet(state)
        val body = Racing.car(state)
        val box = body.spec.gearbox
        val axle = body.spec.axle
        var eff = box.efficiency * axle.efficiency
        if (body.spec.drive == Racing.Drive.AWD) eff *= 0.94
        if (body.spec.drive != body.spec.chassis.drive) eff *= 0.97
        if (body.spec.place != body.spec.chassis.place) eff *= 0.98
        val balance = when (body.spec.place) {
            Racing.Place.FRONT -> 0.96
            Racing.Place.MID -> 1.08
            Racing.Place.REAR -> 1.01
        } * when (body.spec.drive) {
            Racing.Drive.FWD -> 0.88
            Racing.Drive.RWD -> 1.0
            Racing.Drive.AWD -> 1.08
        }
        val grip = body.spec.tire.mu * axle.grip * balance * body.massKg * 9.81
        val drag = body.spec.chassis.cd * body.spec.chassis.area * (1.0 + 0.018 * body.spec.slots) * body.spec.tire.drag
        return Setup(
            peakHp = sheet.stats.peakHorsepower.coerceAtLeast(8.0),
            peakTqLbFt = sheet.stats.peakTorqueLbFt.coerceAtLeast(8.0),
            redline = sheet.params.redlineRpm.coerceAtLeast(3000.0),
            idle = sheet.params.idleRpm.coerceIn(600.0, 1800.0),
            massKg = body.massKg,
            ratios = box.ratios,
            finalDrive = Racing.finalRatio(state.finalDrive),
            efficiency = eff,
            gripN = grip,
            drag = drag,
            shiftSeconds = box.shift,
        )
    }

    fun fresh(setup: Setup): Run = Run(rpm = setup.idle)

    /** One slice of the strip. [shift] is a rising edge: the player asked for the next gear. */
    fun step(setup: Setup, run: Run, throttle: Double, shift: Boolean, dt: Double) {
        if (run.distance >= FINISH_M) return
        val stepDt = dt.coerceIn(0.0, 0.05)
        val th = throttle.coerceIn(0.0, 1.0)
        if (shift && run.shiftLeft <= 0.0 && run.gear < setup.ratios.size) {
            run.gear += 1
            run.shiftLeft = setup.shiftSeconds
            run.hooked = false
        }
        if (run.shiftLeft > 0.0) {
            run.shiftLeft -= stepDt
            val dragA = dragForce(setup, run.speed) / setup.massKg
            run.speed = (run.speed - dragA * stepDt).coerceAtLeast(0.0)
            run.rpm += (setup.idle * 1.15 - run.rpm) * stepDt * 2.2
            run.rpm = run.rpm.coerceAtLeast(setup.idle * 0.75)
            run.spinning = false
            run.limiter = false
            advance(run, stepDt)
            return
        }
        val overall = setup.ratios[run.gear - 1] * setup.finalDrive
        val roadRpm = roadRpm(run.speed, overall)
        val launching = run.gear == 1 && run.speed < 14.0 && !run.hooked
        if (launching) {
            val biteRpm = setup.idle + th * (setup.redline * 0.74 - setup.idle)
            val requested = torqueNm(setup, biteRpm) * setup.efficiency * th * overall / WHEEL_R
            if (requested > setup.gripN && th > 0.05) {
                run.spinning = true
                val flare = setup.idle + th * (setup.redline - setup.idle)
                run.rpm += (flare - run.rpm) * stepDt * 4.5
                val accel = (setup.gripN * 0.22 - dragForce(setup, run.speed)) / setup.massKg
                run.speed = (run.speed + accel * stepDt).coerceAtLeast(0.0)
            } else {
                run.spinning = false
                run.rpm += (biteRpm.coerceAtLeast(roadRpm) - run.rpm) * stepDt * 4.0
                val accel = (requested - dragForce(setup, run.speed)) / setup.massKg
                run.speed = (run.speed + accel * stepDt).coerceAtLeast(0.0)
                if (run.speed > 4.0 && roadRpm > setup.idle * 1.2) run.hooked = true
            }
            run.rpm = run.rpm.coerceIn(setup.idle * 0.8, setup.redline * 1.05)
            run.limiter = run.rpm >= setup.redline * 0.98
        } else {
            run.hooked = true
            run.spinning = false
            var tq = torqueNm(setup, roadRpm.coerceAtMost(setup.redline)) * setup.efficiency
            run.limiter = roadRpm > setup.redline
            if (run.limiter) tq *= 0.07
            val force = tq * overall / WHEEL_R
            val accel = (force - dragForce(setup, run.speed)) / setup.massKg
            run.speed = (run.speed + accel * stepDt).coerceAtLeast(0.0)
            run.rpm = roadRpm(run.speed, overall).coerceAtLeast(setup.idle * 0.8)
        }
        advance(run, stepDt)
    }

    /** A driver who feathers the launch and shifts just under the limiter. */
    fun driveWell(setup: Setup): Run {
        val run = fresh(setup)
        var guard = 0
        while (run.distance < FINISH_M && run.time < 90.0 && guard < 12_000) {
            guard++
            val launch = if (run.gear == 1 && run.speed < 14.0 && !run.hooked) launchThrottle(setup) else 1.0
            val up = run.shiftLeft <= 0.0 && run.gear < setup.ratios.size && run.rpm > setup.redline * 0.91 && run.hooked
            step(setup, run, launch, up, 1.0 / 60.0)
        }
        return run
    }

    /**
     * The other car's elapsed time. An equal pace is your own clean run, so a
     * perfect drive ties and a spun launch loses. A faster car gives you time
     * in hand. A faster ghost is ahead of any clean run you can make.
     */
    fun rivalSeconds(setup: Setup, yourPace: Double, theirPace: Double): Double {
        val clean = driveWell(setup)
        val perfect = if (clean.distance >= FINISH_M - 1.0) clean.time else 90.0
        val ratio = (yourPace / theirPace.coerceAtLeast(1.0)).coerceIn(0.45, 2.2)
        return (perfect * ratio).coerceIn(6.0, 90.0)
    }

    fun launchThrottle(setup: Setup): Double {
        var best = 0.25
        var bestForce = 0.0
        var probe = 0.12
        while (probe <= 1.0) {
            val bite = setup.idle + probe * (setup.redline * 0.74 - setup.idle)
            val requested = torqueNm(setup, bite) * setup.efficiency * probe * setup.ratios[0] * setup.finalDrive / WHEEL_R
            if (requested <= setup.gripN * 0.96 && requested > bestForce) {
                bestForce = requested
                best = probe
            }
            probe += 0.02
        }
        return best
    }

    private fun advance(run: Run, dt: Double) {
        run.distance += run.speed * dt
        run.time += dt
    }

    private fun roadRpm(speed: Double, overall: Double): Double =
        speed / WHEEL_R * overall * 9.5493

    private fun dragForce(setup: Setup, speed: Double): Double =
        0.5 * 1.22 * setup.drag * speed * speed

    /** lb-ft curve that peaks mid-range and will not make more horsepower than the engine has. */
    private fun torqueNm(setup: Setup, rpm: Double): Double {
        val span = (setup.redline - setup.idle).coerceAtLeast(500.0)
        val x = ((rpm - setup.idle) / span).coerceIn(0.0, 1.2)
        val shape = exp(-((x - 0.72).pow(2.0)) / 0.05)
        var lbFt = setup.peakTqLbFt * (0.62 + 0.38 * shape)
        val hp = lbFt * rpm / 5252.0
        val cap = setup.peakHp * 1.05
        if (hp > cap && hp > 1.0) lbFt *= cap / hp
        if (rpm < setup.idle * 0.85) lbFt *= 0.35
        return lbFt * 1.3558
    }
}
