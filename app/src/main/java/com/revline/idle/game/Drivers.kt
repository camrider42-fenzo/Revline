package com.revline.idle.game

import com.revline.idle.data.GameState

/**
 * Hired drivers. A driver is four numbers and a habit. The numbers (0..100) are throttle control, clutch control,
 * shifting speed and dyno knowledge. [Pilot] turns them into pedal work that the real [RaceDrive] then judges.
 * The habit is a [Specialty]: on a setup that suits it, the matching stat is worth [Specialty.BOOST] more.
 * That is the strategy. A launch artist wastes his hands on a car that already hooks.
 *
 * The fee is a share of the purse of the race he drives (the stake race counts the full 2x take), taken at the start
 * of that race and not refunded. Everything scales with the engine block through the purse, so one rate means
 * the same thing on the moped and on the V12. A driver is worth hiring on a build when he lifts your win rate by
 * more than his rate.
 */
object Drivers {
    enum class Specialty(val title: String, val blurb: String) {
        ALL_ROUNDER("All-rounder", "No habit, no weakness. Same hands on every build."),
        GRIP("Grip whisperer", "Big power on small tires. Feathers a launch the tires can barely hold."),
        REVS("Rev hound", "Peaky, high-revving engines. Shifts where the curve pays."),
        GEARS("Box rower", "Four or more gears. Every extra shift is a place to save time."),
        CLUTCH("Clutch kicker", "Close-ratio and dog boxes with a race clutch. Lives at the bite point."),
        ;

        companion object {
            /** Stat points a matching setup is worth. */
            const val BOOST = 14
        }
    }

    enum class Stat(val title: String) { THROTTLE("THROTTLE"), CLUTCH("CLUTCH"), SHIFTING("SHIFTING"), DYNO("DYNO") }

    data class Driver(
        val id: String,
        val name: String,
        val tier: Int,
        val throttle: Int,
        val clutch: Int,
        val shifting: Int,
        val dyno: Int,
        val specialty: Specialty,
        /** Share of the purse he charges for each race he drives. */
        val rate: Double,
        val bio: String,
    ) {
        fun stat(stat: Stat): Int = when (stat) {
            Stat.THROTTLE -> throttle
            Stat.CLUTCH -> clutch
            Stat.SHIFTING -> shifting
            Stat.DYNO -> dyno
        }
    }

    /** Engine blocks (rebuilds) it takes to meet each tier. */
    fun tierUnlock(tier: Int): Int = when (tier) {
        1 -> 0
        2 -> 2
        else -> 4
    }

    val roster: List<Driver> = listOf(
        Driver("dusty", "Graham Hill", 1, 42, 40, 44, 38, Specialty.ALL_ROUNDER, 0.015, "Tidy and unflappable. Keeps it pointed straight."),
        Driver("mina", "Stirling Moss", 1, 66, 40, 34, 40, Specialty.GRIP, 0.035, "Smooth as silk. Soft right foot."),
        Driver("rusty", "Jack Brabham", 1, 36, 60, 38, 34, Specialty.CLUTCH, 0.045, "Mechanic first. Feels the clutch through his boots."),
        Driver("pavel", "Niki Lauda", 2, 48, 52, 74, 50, Specialty.GEARS, 0.07, "Exact and unhurried. Rows a box faster than you can say the gear."),
        Driver("ines", "Gilles Villeneuve", 2, 50, 48, 52, 76, Specialty.REVS, 0.07, "Flat out and fearless. Knows where the power lives."),
        Driver("tanaka", "James Hunt", 2, 76, 52, 46, 54, Specialty.GRIP, 0.075, "Charming and quick off the line."),
        Driver("gus", "Jackie Stewart", 2, 62, 60, 60, 60, Specialty.ALL_ROUNDER, 0.085, "Calm, careful and clever. Wins by being tidy."),
        Driver("colette", "Alain Prost", 3, 58, 84, 72, 62, Specialty.CLUTCH, 0.15, "The Professor. Lives on the bite point."),
        Driver("viktor", "Ayrton Senna", 3, 64, 60, 62, 90, Specialty.REVS, 0.13, "Counts the curve in his head. Shifts exactly there."),
        Driver("ace", "Juan Manuel Fangio", 3, 78, 76, 80, 76, Specialty.ALL_ROUNDER, 0.22, "The old master. Sharp everywhere, and he bills for it."),
    )

    fun byId(id: String): Driver? = roster.firstOrNull { it.id == id }

    fun unlocked(state: GameState, driver: Driver): Boolean = state.prestiges >= tierUnlock(driver.tier)

    fun hired(state: GameState): List<Driver> = roster.filter { it.id in state.hiredDrivers }

    /** The driver the toggle has selected. Null drives it yourself, also when the id is stale or he is not hired. */
    fun selected(state: GameState): Driver? =
        byId(state.driverId)?.takeIf { it.id in state.hiredDrivers }

    fun canHire(state: GameState, driver: Driver): Boolean = unlocked(state, driver) && driver.id !in state.hiredDrivers

    fun hire(state: GameState, id: String): GameState {
        val driver = byId(id) ?: return state
        if (!canHire(state, driver)) return state
        return state.copy(hiredDrivers = state.hiredDrivers + id)
    }

    /** Cost to run [field] with [driver]. A dev-mode table is free, and so is a race with no purse. */
    fun fee(state: GameState, driver: Driver, field: Racing.Field): Double =
        if (state.devMode || field.purse <= 0.0) 0.0 else driver.rate * field.purse

    fun affordable(state: GameState, driver: Driver, field: Racing.Field, alsoPaying: Double = 0.0): Boolean =
        state.devMode || state.money >= fee(state, driver, field) + alsoPaying

    /** A race opened under the toggle: the till after the fee, who drives, and what he was paid. */
    data class Charge(val state: GameState, val driver: Driver?, val fee: Double)

    /**
     * Opens [field] under the toggle. Driving yourself is free. A hired driver is paid here, at the start, and
     * only when he actually drives. Null when the till cannot cover him, so he cannot be put in the seat.
     * [alsoPaying] is money the race takes besides his fee (the stake), checked together.
     */
    fun charge(state: GameState, field: Racing.Field, alsoPaying: Double = 0.0): Charge? {
        val driver = selected(state) ?: return Charge(state, null, 0.0)
        if (!affordable(state, driver, field, alsoPaying)) return null
        val fee = fee(state, driver, field)
        return Charge(state.copy(money = state.money - fee), driver, fee)
    }

    /** Picks [id] (blank to drive yourself). Only a hired driver can be picked. */
    fun select(state: GameState, id: String): GameState {
        if (id.isEmpty()) return state.copy(driverId = "")
        val driver = byId(id) ?: return state
        if (driver.id !in state.hiredDrivers) return state
        return state.copy(driverId = driver.id)
    }

    /**
     * How the launch is best taken. On throttle alone when the tires can hold a useful push. Fed on the clutch with the
     * throttle pinned when they cannot (the best throttle that does not spin is then too little to leave on).
     */
    data class LaunchPlan(val throttle: Double, val slip: Boolean, val pedal: Double)

    fun launchPlan(setup: RaceDrive.Setup): LaunchPlan {
        val l = RaceDrive.launchThrottle(setup)
        val bite = setup.idle + l * (setup.redline * 0.74 - setup.idle)
        val force = RaceDrive.torqueNm(setup, bite) * setup.efficiency * l * setup.ratios[0] * setup.finalDrive / RaceDrive.WHEEL_R
        val cap = setup.gripN * 0.96
        val feed = RaceDrive.clutchForLaunch(setup)
        return if (feed > 0.0 && (force < cap * 0.85 || force > cap * 1.0001)) LaunchPlan(1.0, true, feed) else LaunchPlan(l, false, 0.0)
    }

    /** How grabby the box and clutch are, 0..1. A dog box has no give. */
    fun grabbiness(setup: RaceDrive.Setup): Double = when {
        setup.shiftSeconds <= 0.16 -> 1.0
        setup.shiftSeconds <= 0.25 -> 0.5
        else -> 0.0
    }

    fun matches(specialty: Specialty, setup: RaceDrive.Setup): Boolean = when (specialty) {
        Specialty.ALL_ROUNDER -> false
        Specialty.GRIP -> RaceDrive.launchThrottle(setup) < 0.6
        Specialty.REVS -> peaky(setup)
        Specialty.GEARS -> setup.ratios.size >= 4
        Specialty.CLUTCH -> grabbiness(setup) >= 0.5
    }

    /** An engine whose power sits near the top of the tach. */
    fun peaky(setup: RaceDrive.Setup): Boolean {
        val crossRpm = setup.peakHp * 5252.0 / setup.peakTqLbFt.coerceAtLeast(1.0)
        return crossRpm / setup.redline >= PEAKY_AT
    }

    const val PEAKY_AT = 0.88

    fun skills(driver: Driver, setup: RaceDrive.Setup): Skills {
        val on = matches(driver.specialty, setup)
        fun v(raw: Int, stat: Stat): Double {
            val boost = if (on && stat == statOf(driver.specialty)) Specialty.BOOST else 0
            return ((raw + boost).coerceAtMost(100)) / 100.0
        }
        return Skills(
            v(driver.throttle, Stat.THROTTLE),
            v(driver.clutch, Stat.CLUTCH),
            v(driver.shifting, Stat.SHIFTING),
            v(driver.dyno, Stat.DYNO),
        )
    }

    fun statOf(specialty: Specialty): Stat? = when (specialty) {
        Specialty.ALL_ROUNDER -> null
        Specialty.GRIP -> Stat.THROTTLE
        Specialty.REVS -> Stat.DYNO
        Specialty.GEARS -> Stat.SHIFTING
        Specialty.CLUTCH -> Stat.CLUTCH
    }

    /** Flat reference hands for the balance harness: what a player manages on the real pedals. */
    fun reference(level: Int): Skills = Skills(level / 100.0, level / 100.0, level / 100.0, level / 100.0)

    /**
     * Rpm where the next gear starts pulling harder than this one: engine torque times ratio, before and after the shift.
     * If it never does, the top of the tach just under the limiter.
     */
    fun optimalShiftRpm(setup: RaceDrive.Setup, gear: Int): Double {
        if (gear >= setup.ratios.size) return setup.redline
        val from = setup.ratios[gear - 1]
        val to = setup.ratios[gear]
        var rpm = setup.redline * 0.55
        while (rpm < setup.redline * 0.985) {
            val next = rpm * to / from
            val now = RaceDrive.torqueNm(setup, rpm) * from
            val then = RaceDrive.torqueNm(setup, next) * to
            if (then >= now) return rpm
            rpm += setup.redline * 0.005
        }
        return setup.redline * 0.975
    }

    /** [loss] is the extra time over a flawless pilot as a share, with red lights priced in. [score] adds the fee. */
    data class Forecast(val driver: Driver, val seconds: Double, val jumpRate: Double, val loss: Double, val score: Double, val fit: Fit)

    enum class Fit(val label: String) { BEST("BEST FIT"), GOOD("GOOD FIT"), FAIR("FAIR FIT"), POOR("POOR FIT") }

    /** One percent of elapsed time is worth about this much of a purse in win rate, so a fee rate converts to elapsed time at its inverse. */
    const val WIN_PER_PERCENT = 0.045

    /**
     * What this driver does on this setup, from a handful of real simulated runs on fixed seeds (the same seeds
     * every time, so the hint does not flicker). A jump start counts as a run that did not finish. The fit
     * ranks drivers by lost time plus what their fee is worth in time, so a cheap driver can out-fit an ace.
     */
    fun forecast(setup: RaceDrive.Setup, drivers: List<Driver> = roster, runs: Int = 8): List<Forecast> {
        val flawless = AutoRace(setup, Skills(1.0, 1.0, 1.0, 1.0), 7_001L).finish()
        val perfect = if (flawless.reachedLine) flawless.run.time else return drivers.map { Forecast(it, 0.0, 0.0, 0.0, 0.0, Fit.POOR) }
        val rows = drivers.map { driver ->
            var total = 0.0
            var jumps = 0
            var counted = 0
            for (seed in 1..runs) {
                val race = AutoRace(setup, skills(driver, setup), 7_000L + seed * 31L).finish()
                if (race.run.jumped) jumps++ else if (race.reachedLine) {
                    total += race.run.time
                    counted++
                }
            }
            val seconds = if (counted == 0) perfect * 2.0 else total / counted
            val jumpRate = jumps / runs.toDouble()
            val loss = (seconds / perfect - 1.0) + jumpRate * 0.5
            Triple(driver, seconds, jumpRate) to loss
        }
        val scored = rows.map { (core, loss) -> core to (loss to loss + core.first.rate / (WIN_PER_PERCENT * 100.0)) }
        val best = scored.minOf { it.second.second }
        return scored.map { (core, pair) ->
            val score = pair.second
            val fit = when {
                score <= best + 0.006 -> Fit.BEST
                score <= best + 0.02 -> Fit.GOOD
                score <= best + 0.045 -> Fit.FAIR
                else -> Fit.POOR
            }
            Forecast(core.first, core.second, core.third, pair.first, score, fit)
        }
    }
}
