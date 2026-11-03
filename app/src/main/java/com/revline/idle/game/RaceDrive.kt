package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
import kotlin.math.exp
import kotlin.math.pow

/**
 * A quarter-mile you have to drive. The launch spins if the throttle asks for
 * more than the tires can hold. The clutch cuts that ask: pedal travel 0 is up
 * and engaged, 1 is on the floor. The clutch only grabs inside a bite band
 * ([BITE_START] down to [BITE_FULL]): below the band the engine is free of the
 * road, above it the engine has the road. The box you fitted sets the ratios and how long a
 * shift takes. Sitting on the limiter does not count as a shift.
 */
object RaceDrive {
    const val FINISH_M = 402.3
    const val WHEEL_R = 0.33
    const val SLIP_EDGE = 0.3
    private const val SLIP_FADE_S = 0.45

    /** Pedal travel where the clutch first grabs. Further up (smaller) it takes more of the engine. Further down it is open. */
    const val BITE_START = 0.72

    /** Pedal travel where the clutch has all of the engine. Above this on the slot it is fully engaged. */
    const val BITE_FULL = 0.28

    /** The clutch is out far enough to row the gearbox. Just inside the open end of the band. */
    const val SHIFT_PEDAL = 0.66

    /** A tiny share of grab is still open for practical purposes: the engine revs free of the road. */
    private const val OPEN_BELOW = 0.004

    /** The car has rolled this far on the tree. That is a jump start. */
    const val JUMP_M = 0.02

    private const val DECAY_SHARE = 0.55

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
        val distanceM: Double = FINISH_M,
        /** Sets how fast the engine can spin up. Big blocks have more to turn. */
        val displacementCc: Double = 1000.0,
        // Drag strip terms below. Each is neutral at its default, so a car without drag parts or a tune is unchanged.
        /** Launch control: share of a spinning launch's lost push given back, 0..1. */
        val spinTrim: Double = 0.0,
        /** Transbrake: the rpm the engine is held at on the line. 0 means the throttle sets it. */
        val launchRpm: Double = 0.0,
        /** The push, in g, a car takes in first and second before the nose comes up. Default is never. */
        val liftG: Double = 99.0,
        /** Flywheel inertia taken off the driveline, kg m². */
        val flywheelKgm2: Double = 0.0,
        /** Rolling drag from the tires at any speed, in newtons. Negative is a lower pressure's extra. */
        val rollN: Double = 0.0,
        /** Redline share trimmed off (or added to) the shift point, in rpm. */
        val shiftTrimRpm: Double = 0.0,
        /** How unforgiving the short-shifter is of a sloppy hand, 0..1. */
        val notchy: Double = 0.0,
        /** Nitrous: share of torque while it flows, where it opens (m), and seconds of flow in the bottle. */
        val shotFrac: Double = 0.0,
        val shotAtM: Double = 60.0,
        val bottleS: Double = 0.0,
    )

    data class Run(
        var gear: Int = 1,
        var rpm: Double = 900.0,
        var speed: Double = 0.0,
        var distance: Double = 0.0,
        var shiftLeft: Double = 0.0,
        var time: Double = 0.0,
        var spinning: Boolean = false,
        /** 0..1 smoke and dash-lamp strength: set by how far the launch overshoots grip, eased out when the tires hook. */
        var slip: Double = 0.0,
        var limiter: Boolean = false,
        var hooked: Boolean = false,
        /** Engine seconds: ticks on the tree too, where [time] (the race clock) does not. Drives the limiter's cut. */
        var clock: Double = 0.0,
        /** Rolled before green. The race is lost. */
        var jumped: Boolean = false,
        /** Seconds of nitrous used. */
        var nitrousS: Double = 0.0,
    )

    fun setup(state: GameState): Setup = setup(state, Logic.sheet(state))

    /** [circuit] fits the circuit tire and leaves out the launch grip a stiffer car earns on the strip. */
    fun setup(state: GameState, sheet: Sheet, circuit: Boolean = false): Setup {
        val rig = if (circuit) DragTune.Rig() else DragTune.rig(state)
        return setupFor(
            body = Racing.car(state, circuit),
            peakHp = sheet.stats.peakHorsepower,
            peakTqLbFt = sheet.stats.peakTorqueLbFt,
            redline = sheet.params.redlineRpm,
            idle = sheet.params.idleRpm,
            finalDrive = Racing.finalRatio(state.finalDrive) + rig.finalTrim,
            distanceM = Racing.lengthOf(state.raceLength).meters,
            displacementCc = Block.forPrestige(state.prestiges).let {
                EngineSim.displacementCc(it.boreMm, it.strokeMm, it.cylinders)
            },
            circuit = circuit,
            rig = rig,
        )
    }

    /**
     * The one place a car becomes a strip [Setup]: weight, drivetrain losses, the tire's grip on this axle and balance,
     * drag, the box's ratios and shift time. The player's car and every drag rival (see [DragRivals]) go through it,
     * so nobody gets a launch or a time the build itself does not earn.
     */
    fun setupFor(
        body: Racing.Car,
        peakHp: Double,
        peakTqLbFt: Double,
        redline: Double,
        idle: Double,
        finalDrive: Double,
        distanceM: Double,
        displacementCc: Double,
        circuit: Boolean = false,
        rig: DragTune.Rig = DragTune.Rig(),
    ): Setup {
        val box = body.spec.gearbox
        val axle = body.spec.axle
        var eff = box.efficiency * axle.efficiency
        val native = Racing.driveFor(body.spec.chassis, body.spec.place)
        if (body.spec.drive == Racing.Drive.AWD && native != Racing.Drive.AWD) eff *= 0.94
        if (body.spec.drive != native) eff *= 0.97
        if (body.spec.place !in body.spec.chassis.places) eff *= 0.98
        val balance = when (body.spec.place) {
            Racing.Place.FRONT -> 0.96
            Racing.Place.MID -> 1.08
            Racing.Place.REAR -> 1.01
        } * when (body.spec.drive) {
            Racing.Drive.FWD -> 0.88
            Racing.Drive.RWD -> 1.0
            Racing.Drive.AWD -> 1.08
        }
        val kit = body.spec.kit
        // The strip's own terms. The circuit uses none of them: its Setup stays what it was.
        val warmth = if (circuit) 0.0 else DragTune.warmth(body.spec.tire.id)
        val pressure = rig.psi + (if (warmth > 0.0) kit.burnout else 0.0)
        val warm = kit.burnout * warmth
        val stripGrip = if (circuit) 1.0 else {
            (1.0 - 0.018 * pressure) * (1.0 + 0.05 * warm) * (1.0 - 0.012 * rig.front) *
                (1.0 - 0.010 * rig.rear) * (1.0 + 0.0008 * rig.heightMm)
        }
        val stripDrag = if (circuit) 1.0 else 1.0 + 0.0012 * rig.heightMm
        val squat = if (circuit) 1.0 else 1.0 - 0.003 * (-rig.rear).coerceAtLeast(0.0)
        val grip = body.spec.tire.mu * axle.grip * balance * body.massKg * 9.81 * (if (circuit) 1.0 else kit.launch) * stripGrip
        val drag = body.spec.chassis.cd * body.spec.chassis.area * (1.0 + 0.018 * body.spec.slots) * body.spec.tire.drag * kit.dragMult * stripDrag
        val hp = peakHp.coerceAtLeast(8.0)
        val lift = if (circuit) 99.0 else DragTune.baseLift(body) + kit.liftG + 0.04 * rig.front - 0.004 * rig.heightMm
        val red = redline.coerceAtLeast(3000.0)
        val base = Setup(
            peakHp = hp,
            peakTqLbFt = peakTqLbFt.coerceAtLeast(8.0),
            redline = red,
            idle = idle.coerceIn(600.0, 1800.0),
            massKg = body.massKg,
            ratios = box.ratios,
            finalDrive = finalDrive,
            efficiency = eff * squat,
            gripN = grip,
            drag = drag,
            shiftSeconds = box.shift * kit.shiftMult,
            distanceM = distanceM,
            displacementCc = displacementCc,
            spinTrim = kit.spinTrim,
            liftG = lift,
            flywheelKgm2 = kit.flywheel,
            rollN = if (circuit) 0.0 else body.massKg * 9.81 * 0.014 * (-0.30 * pressure),
            shiftTrimRpm = if (circuit) 0.0 else rig.shiftTrimRpm,
            notchy = (1.0 - kit.shiftMult).coerceAtLeast(0.0),
            shotFrac = if (circuit) 0.0 else kit.shot,
            shotAtM = rig.nosAtM,
            bottleS = kit.bottleS,
        )
        if (kit.preload <= 0.0 || circuit) return base
        // A transbrake holds the engine at a launch rpm. The tune moves it off the rpm that just fits the tires.
        val held = (holdRpm(base) + rig.launchRpmOffset).coerceIn(0.45 * red, 0.99 * red)
        return base.copy(launchRpm = held)
    }

    /** The rpm a transbrake can hold that pushes hardest without asking more of the tires than they hold. */
    fun holdRpm(setup: Setup): Double {
        val overall = setup.ratios[0] * setup.finalDrive
        val shot = shotAtLaunch(setup, 1.0)
        var best = setup.redline * 0.45
        var bestForce = 0.0
        var rpm = setup.redline * 0.45
        while (rpm <= setup.redline * 0.99) {
            val force = launchForce(setup.copy(launchRpm = rpm), 1.0, 1.0, overall, shot)
            if (force <= setup.gripN * LAUNCH_CAP && force > bestForce) {
                bestForce = force
                best = rpm
            }
            rpm += setup.redline * 0.01
        }
        return best
    }

    private const val LAUNCH_CAP = 0.96

    /** The rpm the engine is at for the launch: held with a transbrake, otherwise wound up by the throttle. */
    private fun launchRpmFor(setup: Setup, th: Double): Double =
        if (setup.launchRpm > 0.0) setup.launchRpm else setup.idle + th * (setup.redline * 0.74 - setup.idle)

    /**
     * The push at the wheels at the bite, in newtons: what the tires are asked for on a launch. A transbrake hits
     * harder the higher its rpm (the engine is already spun up). A light flywheel bogs a little. The shot adds torque.
     */
    fun launchForce(setup: Setup, th: Double, engagement: Double, overall: Double, shotOn: Boolean): Double {
        val rpm = launchRpmFor(setup, th)
        val hit = if (setup.launchRpm > 0.0) (rpm / (setup.redline * 0.74)).pow(1.5) else 1.0
        val bog = (1.0 - 1.5 * setup.flywheelKgm2).coerceAtLeast(0.5)
        val shot = if (shotOn) 1.0 + setup.shotFrac else 1.0
        return torqueNm(setup, rpm) * setup.efficiency * th * engagement * overall / WHEEL_R * hit * bog * shot
    }

    /** The shot is open on the strip: past its point, with flow left in the bottle, and the throttle most of the way down. */
    fun shotActive(setup: Setup, run: Run, th: Double): Boolean =
        setup.shotFrac > 0.0 && th > 0.5 && run.distance >= setup.shotAtM && run.nitrousS < setup.bottleS

    internal fun shotAtLaunch(setup: Setup, th: Double): Boolean =
        setup.shotFrac > 0.0 && th > 0.5 && setup.shotAtM <= 0.5

    /**
     * The shot's push, on top of [dry]. In first and second the tires can only take so much more than they hold,
     * so a shot opened there on a car that already has more push than grip adds nothing. It pays in the gears
     * the tires can take it.
     */
    private fun withShot(setup: Setup, run: Run, dry: Double): Double {
        val full = dry * (1.0 + setup.shotFrac)
        if (run.gear > 2) return full
        return maxOf(dry, minOf(full, setup.gripN * SHOT_TRACTION))
    }

    private const val SHOT_TRACTION = 1.3

    /** Mass the push has to move: the car, less the flywheel's inertia as seen at the wheels (it matters most in the low gears). */
    private fun effMass(setup: Setup, overall: Double): Double =
        (setup.massKg - setup.flywheelKgm2 * (overall / WHEEL_R).pow(2.0)).coerceAtLeast(setup.massKg * 0.85)

    /**
     * The nose comes up past [Setup.liftG]. Most of the push over the limit is wasted. Only the low gears are
     * short enough to do it.
     */
    private fun lifted(setup: Setup, run: Run, accel: Double): Double {
        val limit = setup.liftG * 9.81
        return if (run.gear <= 2 && accel > limit) limit + (accel - limit) * 0.25 else accel
    }

    /** Where the pilot shifts: just under the redline, moved by the tune. */
    fun shiftPoint(setup: Setup): Double =
        (setup.redline * 0.91 + setup.shiftTrimRpm).coerceIn(setup.redline * 0.55, setup.redline * 0.985)

    /**
     * Share of the gap to the throttle's rpm the engine closes each second with the clutch open.
     * A 50cc single winds up almost at once. A 6.5L V12 has a lot more crank and flywheel to move.
     */
    fun revRate(setup: Setup): Double =
        (4.6 * (1000.0 / setup.displacementCc.coerceAtLeast(40.0)).pow(0.22) * (1.0 + 5.0 * setup.flywheelKgm2)).coerceIn(2.4, 12.0)

    /** How much of the engine the clutch passes at this pedal travel. 0 open, 1 locked. Smooth through the bite band. */
    fun engagement(pedal: Double): Double {
        val t = ((BITE_START - pedal.coerceIn(0.0, 1.0)) / (BITE_START - BITE_FULL)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    /** The pedal travel that passes this share of the engine. The inverse of [engagement]. */
    fun pedalFor(engagement: Double): Double {
        val want = engagement.coerceIn(0.0, 1.0)
        var lo = BITE_FULL
        var hi = BITE_START
        repeat(40) {
            val mid = (lo + hi) / 2.0
            if (engagement(mid) > want) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }

    /**
     * The tree. The engine runs for real: revs follow the throttle, the limiter cuts, the needle falls when you
     * lift. The race clock and the road do not. If the clutch grabs enough to roll the car it is a jump start.
     * Returns true once the car has rolled.
     */
    fun stage(setup: Setup, run: Run, throttle: Double, dt: Double, clutch: Double): Boolean {
        if (run.jumped) return true
        val clock = run.time
        val bottle = run.nitrousS
        drive(setup, run, throttle, false, dt, clutch)
        run.time = clock
        run.nitrousS = bottle
        if (run.distance > JUMP_M) run.jumped = true
        return run.jumped
    }

    fun fresh(setup: Setup): Run = Run(rpm = setup.idle)

    /**
     * One slice of the strip. [shift] is a rising edge: the next gear.
     * [clutch] is pedal travel, 0 engaged and 1 on the floor. Callers that
     * omit it leave the clutch engaged, which is how the card driver shifts.
     */
    fun step(setup: Setup, run: Run, throttle: Double, shift: Boolean, dt: Double, clutch: Double = 0.0) {
        drive(setup, run, throttle, shift, dt, clutch)
        if (!run.spinning) run.slip = (run.slip - dt.coerceIn(0.0, 0.05) / SLIP_FADE_S).coerceAtLeast(0.0)
    }

    /** How hard the tires are breaking loose: 0 while the grip holds, 0.3 at the edge, 1 at half again over the limit. */
    fun slipAmount(requestedN: Double, gripN: Double): Double {
        if (gripN <= 0.0 || requestedN <= gripN) return 0.0
        return (SLIP_EDGE + (1.0 - SLIP_EDGE) * (requestedN / gripN - 1.0) / 0.5).coerceIn(SLIP_EDGE, 1.0)
    }

    private fun drive(setup: Setup, run: Run, throttle: Double, shift: Boolean, dt: Double, clutch: Double) {
        if (run.distance >= setup.distanceM) return
        val stepDt = dt.coerceIn(0.0, 0.05)
        val th = throttle.coerceIn(0.0, 1.0)
        val depression = clutch.coerceIn(0.0, 1.0)
        val engagement = engagement(depression)
        if (shift && run.shiftLeft <= 0.0 && run.gear < setup.ratios.size) {
            run.gear += 1
            run.shiftLeft = setup.shiftSeconds
            run.hooked = false
        }
        if (run.shiftLeft > 0.0) {
            run.shiftLeft -= stepDt
            val dragA = dragForce(setup, run.speed) / setup.massKg
            run.speed = (run.speed - dragA * stepDt).coerceAtLeast(0.0)
            val free = if (depression > 0.5 && th > 0.15) {
                setup.idle + th * (setup.redline - setup.idle)
            } else {
                setup.idle * 1.15
            }
            run.rpm += (free - run.rpm) * stepDt * 2.2
            run.rpm = run.rpm.coerceAtLeast(setup.idle * 0.75)
            run.spinning = false
            run.limiter = false
            advance(run, stepDt)
            return
        }
        if (engagement < OPEN_BELOW) {
            if (run.gear == 1 && run.speed < 14.0) run.hooked = false
            freeRev(setup, run, th, stepDt)
            run.spinning = false
            val dragA = dragForce(setup, run.speed) / setup.massKg
            run.speed = (run.speed - dragA * stepDt).coerceAtLeast(0.0)
            advance(run, stepDt)
            return
        }
        val overall = setup.ratios[run.gear - 1] * setup.finalDrive
        val roadRpm = roadRpm(run.speed, overall)
        val launching = run.gear == 1 && run.speed < 14.0 && !run.hooked
        if (launching) {
            val shotOn = shotActive(setup, run, th)
            if (shotOn) run.nitrousS += stepDt
            val biteRpm = launchRpmFor(setup, th)
            val requested = launchForce(setup, th, engagement, overall, shotOn)
            if (requested > setup.gripN && th > 0.05) {
                run.spinning = true
                val trim = setup.spinTrim.coerceIn(0.0, 1.0)
                val target = slipAmount(requested, setup.gripN) * (1.0 - 0.85 * trim)
                run.slip += (target - run.slip) * (stepDt * 12.0).coerceAtMost(1.0)
                val flare = setup.idle + th * (setup.redline - setup.idle)
                run.rpm += (flare - run.rpm) * stepDt * 4.5
                val floor = setup.gripN * 0.22
                val held = minOf(requested, setup.gripN * 1.03)
                val force = floor + trim * (held - floor)
                val accel = lifted(setup, run, (force - dragForce(setup, run.speed)) / effMass(setup, overall))
                run.speed = (run.speed + accel * stepDt).coerceAtLeast(0.0)
            } else {
                run.spinning = false
                val locked = biteRpm.coerceAtLeast(roadRpm)
                val flare = setup.idle + th * (setup.redline - setup.idle)
                val target = if (engagement >= 0.92) locked else roadRpm * engagement + flare * (1.0 - engagement)
                run.rpm += (target - run.rpm) * stepDt * 4.0
                val accel = lifted(setup, run, (requested - dragForce(setup, run.speed)) / effMass(setup, overall))
                run.speed = (run.speed + accel * stepDt).coerceAtLeast(0.0)
                if (run.speed > 4.0 && roadRpm > setup.idle * 1.2) run.hooked = true
            }
            run.rpm = run.rpm.coerceIn(setup.idle * 0.8, setup.redline * 1.05)
            run.limiter = run.rpm >= setup.redline * 0.98
        } else {
            run.hooked = true
            run.spinning = false
            val over = roadRpm > setup.redline && engagement >= 0.45 && th > 0.15
            if (over) {
                run.limiter = true
                run.rpm = RevLimiter.rpm(setup.redline, run.time)
                val dragA = dragForce(setup, run.speed) / setup.massKg
                run.speed = (run.speed - dragA * stepDt).coerceAtLeast(0.0)
            } else {
                run.limiter = false
                val shotOn = shotActive(setup, run, th)
                if (shotOn) run.nitrousS += stepDt
                val tq = torqueNm(setup, roadRpm.coerceAtMost(setup.redline)) * setup.efficiency
                val dry = tq * engagement * overall / WHEEL_R
                val force = if (shotOn) withShot(setup, run, dry) else dry
                val accel = lifted(setup, run, (force - dragForce(setup, run.speed)) / effMass(setup, overall))
                run.speed = (run.speed + accel * stepDt).coerceAtLeast(0.0)
                val road = roadRpm(run.speed, overall).coerceAtLeast(setup.idle * 0.8)
                run.rpm = if (engagement >= 0.92) {
                    road
                } else {
                    val flare = setup.idle + th * (setup.redline - setup.idle)
                    (road * engagement + flare * (1.0 - engagement)).coerceAtLeast(setup.idle * 0.8)
                }
            }
        }
        advance(run, stepDt)
    }

    /** Pedal travel (inside the bite band) that lets full throttle leave without breaking the tires. 0 means the tires already hold it. */
    fun clutchForLaunch(setup: Setup): Double {
        val full = launchForce(setup, 1.0, 1.0, setup.ratios[0] * setup.finalDrive, shotAtLaunch(setup, 1.0))
        if (setup.spinTrim >= 0.5 || setup.launchRpm > 0.0) return 0.0
        if (full <= setup.gripN * 0.96) return 0.0
        val engagement = (setup.gripN * 0.96 / full).coerceIn(0.2, 0.92)
        return pedalFor(engagement)
    }

    /** Spring the pedal back to engaged. A foot that is still on it should not call this. */
    fun releaseClutch(depression: Double, dt: Double): Double {
        val back = depression.coerceIn(0.0, 1.0) * exp(-dt.coerceIn(0.0, 0.05) / 0.07)
        return if (back < 0.02) 0.0 else back
    }

    /** A driver who feathers the launch and shifts just under the limiter. */
    fun driveWell(setup: Setup, mark: ((Run) -> Unit)? = null): Run {
        val run = fresh(setup)
        var guard = 0
        val cap = (90.0 * setup.distanceM / FINISH_M).coerceIn(40.0, 160.0)
        while (run.distance < setup.distanceM && run.time < cap && guard < 20_000) {
            guard++
            val launch = if (run.gear == 1 && run.speed < 14.0 && !run.hooked) launchThrottle(setup) else 1.0
            val up = run.shiftLeft <= 0.0 && run.gear < setup.ratios.size && run.rpm > shiftPoint(setup) && run.hooked
            step(setup, run, launch, up, 1.0 / 60.0)
            if (mark != null) mark(run)
        }
        return run
    }

    /** One clean pass: 60 ft, the time, and the trap speed. What the Drag tune page shows and the tests compare. */
    data class Clean(val sixtyFt: Double, val time: Double, val trapMps: Double, val reached: Boolean)

    fun clean(setup: Setup): Clean {
        var sixty = 0.0
        var seen = false
        var lastD = 0.0
        var lastT = 0.0
        val run = driveWell(setup) { r ->
            if (!seen && r.distance >= 18.288) {
                seen = true
                val span = (r.distance - lastD).coerceAtLeast(1e-9)
                sixty = lastT + (r.time - lastT) * ((18.288 - lastD) / span)
            }
            lastD = r.distance
            lastT = r.time
        }
        return Clean(sixty, run.time, run.speed, run.distance >= setup.distanceM)
    }

    /** What a launch module lets the throttle ask of the tires: a share more than the tires hold without it. */
    fun launchCap(setup: Setup): Double = setup.gripN * (LAUNCH_CAP + 0.035 * setup.spinTrim.coerceIn(0.0, 1.0))

    /** A transbrake is let go on the button: no feathering, the launch rpm is the tune. */
    fun launchThrottle(setup: Setup): Double {
        if (setup.launchRpm > 0.0) return 1.0
        var best = 0.25
        var bestForce = 0.0
        var probe = 0.12
        val cap = launchCap(setup)
        while (probe <= 1.0) {
            val requested = launchForce(setup, probe, 1.0, setup.ratios[0] * setup.finalDrive, shotAtLaunch(setup, probe))
            if (requested <= cap && requested > bestForce) {
                bestForce = requested
                best = probe
            }
            probe += 0.02
        }
        return best
    }

    /** Clutch open: the engine is only fighting its own inertia. It climbs fast, falls slower, and bounces off the limiter. */
    private fun freeRev(setup: Setup, run: Run, th: Double, dt: Double) {
        val reach = setup.redline * 0.98
        val target = if (th < 0.02) setup.idle else setup.idle + th * (setup.redline * 1.06 - setup.idle)
        val k = revRate(setup) * (if (target >= run.rpm) 1.0 else DECAY_SHARE)
        if (!(run.limiter && target >= reach)) {
            run.rpm += (target - run.rpm) * (dt * k).coerceAtMost(1.0)
            run.rpm = run.rpm.coerceIn(setup.idle * 0.8, setup.redline * 1.05)
            run.limiter = run.rpm >= reach && target >= reach
        }
        if (run.limiter) run.rpm = RevLimiter.rpm(setup.redline, run.clock)
    }

    private fun advance(run: Run, dt: Double) {
        run.distance += run.speed * dt
        run.time += dt
        run.clock += dt
    }

    private fun roadRpm(speed: Double, overall: Double): Double =
        speed / WHEEL_R * overall * 9.5493

    internal fun dragForce(setup: Setup, speed: Double): Double =
        (0.5 * 1.22 * setup.drag * speed * speed + setup.rollN).coerceAtLeast(0.0)

    /** lb-ft curve that peaks mid-range and will not make more horsepower than the engine has. */
    fun torqueNm(setup: Setup, rpm: Double): Double {
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
