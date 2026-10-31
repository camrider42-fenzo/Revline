package com.revline.idle.game

import com.revline.idle.data.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the strip the way a player would, and the way a player shouldn't. */
class RaceDriveTest {
    @Test
    fun flooringTheLaunchSpinsAndAFeatherHooksUp() {
        val car = sample()
        val floored = runFor(car, 1.6) { _, _ -> 1.0 to false }
        val hold = RaceDrive.launchThrottle(car)
        val feathered = runFor(car, 1.6) { setup, run ->
            val t = if (run.gear == 1 && run.speed < 14.0 && !run.hooked) hold else 1.0
            val up = run.hooked && run.rpm > setup.redline * 0.9 && run.gear < setup.ratios.size
            t to up
        }
        assertTrue("floor spun the whole way", floored.spinning || floored.distance < feathered.distance)
        assertTrue(
            "feather ${"%.1f".format(feathered.distance)} m vs floor ${"%.1f".format(floored.distance)} m",
            feathered.distance > floored.distance * 1.25,
        )
    }

    @Test
    fun slipIsZeroUnderGripThenJumpsToTheEdgeAndSaturates() {
        assertEquals(0.0, RaceDrive.slipAmount(900.0, 1000.0), 0.0)
        assertEquals(0.0, RaceDrive.slipAmount(1000.0, 1000.0), 0.0)
        assertEquals(RaceDrive.SLIP_EDGE, RaceDrive.slipAmount(1000.1, 1000.0), 0.01)
        assertTrue(RaceDrive.slipAmount(1250.0, 1000.0) in 0.5..0.7)
        assertEquals(1.0, RaceDrive.slipAmount(2000.0, 1000.0), 0.0)
    }

    @Test
    fun smokeBuildsWhileSpinningAndFadesOnceTheTiresHook() {
        val car = sample()
        val run = RaceDrive.fresh(car)
        var peak = 0.0
        var t = 0
        while (t < 60) {
            t++
            RaceDrive.step(car, run, 1.0, false, 1.0 / 60.0)
            if (run.spinning) peak = maxOf(peak, run.slip)
            assertTrue("slip only while spinning or fading", run.spinning || run.slip < peak + 1e-9)
        }
        assertTrue("floored launch never spun", peak >= RaceDrive.SLIP_EDGE * 0.8)
        // Lift: the tires hold again and the smoke goes out within half a second.
        repeat(40) { RaceDrive.step(car, run, 0.0, false, 1.0 / 60.0) }
        assertTrue("still smoking ${run.slip}", !run.spinning && run.slip == 0.0)
    }

    @Test
    fun sittingOnTheLimiterStopsThePull() {
        val car = sample()
        val run = RaceDrive.fresh(car)
        var guard = 0
        var marked = false
        var speedAt = 0.0
        var timeAt = 0.0
        var speedLater = 0.0
        while (run.time < 20.0 && run.distance < RaceDrive.FINISH_M && guard < 8_000) {
            guard++
            val shift = !marked && run.gear == 1 && run.rpm > car.redline * 0.55
            RaceDrive.step(car, run, 1.0, shift, 1.0 / 60.0)
            if (!marked && run.limiter) {
                marked = true
                speedAt = run.speed
                timeAt = run.time
            }
            if (marked && run.time > timeAt + 0.55) {
                speedLater = run.speed
                break
            }
        }
        assertTrue("never reached the limiter", marked)
        assertTrue("kept pulling $speedAt to $speedLater", speedLater <= speedAt * 1.03)
        assertTrue("rpm ${run.rpm}", run.rpm < car.redline)
        assertTrue(RevLimiter.rpm(7000.0, 0.0) < 6700.0)
        assertTrue(RevLimiter.rpm(7000.0, 0.05) > 6900.0)
    }

    @Test
    fun aShortGearWinsTheEighthAndGivesGroundBackOnTheHalf() {
        fun pair(meters: Double): Pair<Double, Double> {
            val short = RaceDrive.driveWell(sample().copy(distanceM = meters, finalDrive = 4.56)).time
            val tall = RaceDrive.driveWell(sample().copy(distanceM = meters, finalDrive = 3.07)).time
            return short to tall
        }
        val (shortEighth, tallEighth) = pair(Racing.Length.EIGHTH.meters)
        val (shortHalf, tallHalf) = pair(Racing.Length.HALF.meters)
        val eighthShare = (tallEighth - shortEighth) / shortEighth
        val halfShare = (tallHalf - shortHalf) / shortHalf
        assertTrue("eighth short $shortEighth tall $tallEighth", tallEighth > shortEighth + 0.05)
        assertTrue("half share $halfShare eighth share $eighthShare", halfShare < eighthShare)
    }

    @Test
    fun aShortGearLeavesHarderAndATallGearCarriesTheTop() {
        val short = sample().copy(finalDrive = Racing.finalRatio("short"))
        val tall = sample().copy(finalDrive = Racing.finalRatio("tall"))
        val shortRun = runFor(short, 1.4, driver = ::clean)
        val tallRun = runFor(tall, 1.4, driver = ::clean)
        assertTrue(
            "short ${"%.1f".format(shortRun.distance)} vs tall ${"%.1f".format(tallRun.distance)}",
            shortRun.distance > tallRun.distance,
        )
        val shortDone = RaceDrive.driveWell(short)
        val tallDone = RaceDrive.driveWell(tall)
        assertTrue("tall top ${tallDone.speed} short ${shortDone.speed}", tallDone.speed > shortDone.speed * 0.9)
        assertTrue(shortDone.distance >= RaceDrive.FINISH_M - 1.0 || shortDone.time > 1.0)
    }

    @Test
    fun youHaveToShiftAndADogBoxWastesLessTimeDoingIt() {
        val held = runFor(sample(), 8.0) { _, run ->
            val t = if (run.gear == 1 && !run.hooked) 0.32 else 1.0
            t to false
        }
        val shifted = RaceDrive.driveWell(sample())
        assertTrue("held ${held.distance} shifted ${shifted.distance}", shifted.distance > held.distance * 1.3)
        val dog = sample().copy(shiftSeconds = 0.13, ratios = listOf(2.64, 1.88, 1.48, 1.22, 1.00))
        val crash = sample().copy(shiftSeconds = 0.58, ratios = listOf(2.87, 1.55, 1.00))
        val dogTime = RaceDrive.driveWell(dog).time
        val crashTime = RaceDrive.driveWell(crash).time
        assertTrue("dog $dogTime crash $crashTime", dogTime < crashTime)
    }

    @Test
    fun aCleanDriveBeatsASpunLaunchOnTheSameCar() {
        val car = sample()
        val clean = RaceDrive.driveWell(car)
        assertTrue("clean ${clean.time} dist ${clean.distance}", clean.distance >= RaceDrive.FINISH_M - 1.0)
        val spun = runFor(car, 40.0) { setup, run ->
            val up = run.rpm > setup.redline * 0.91 && run.gear < setup.ratios.size
            1.0 to up
        }
        assertTrue(
            "spun ${spun.time} clean ${clean.time} dist ${spun.distance}",
            spun.time > clean.time + 0.05 || spun.distance < RaceDrive.FINISH_M,
        )
    }

    @Test
    fun theFinalDriveChangesTheStripAndNotThePaceCard() {
        val fresh = GameState(money = 20_000.0, researchPoints = 500.0)
        assertEquals("road", Racing.setFinal(fresh, "short").finalDrive)
        val studied = Racing.study(fresh, "final_short")
        assertTrue(studied.researchPoints < fresh.researchPoints)
        val short = Racing.setFinal(studied, "short")
        assertEquals("short", short.finalDrive)
        assertTrue(short.money < studied.money)
        assertEquals(Racing.car(studied).pace, Racing.car(short).pace, 0.001)
        assertEquals(3.73, RaceDrive.setup(studied).finalDrive, 0.001)
        assertEquals(4.56, RaceDrive.setup(short).finalDrive, 0.001)
        val back = Racing.setFinal(short, "road")
        assertEquals("road", back.finalDrive)
        assertEquals(short.money, back.money, 0.01)
        val again = Racing.setFinal(back, "short")
        assertEquals("short", again.finalDrive)
        assertEquals(back.money, again.money, 0.01)
    }

    @Test
    fun aGearboxAndATireTakeResearchThenMoneyOnce() {
        val fresh = GameState(money = 50_000.0, researchPoints = 5_000.0)
        assertEquals("four", Racing.fitPart(fresh, "box", "crash").transmission)
        assertEquals("bias", Racing.fitPart(fresh, "tire", "radial").tire)
        val studied = Racing.study(Racing.study(fresh, "box_crash"), "tire_radial")
        assertEquals("four", Racing.fitPart(studied.copy(money = 0.0), "box", "crash").transmission)
        val boxed = Racing.fitPart(studied, "box", "crash")
        assertEquals("crash", boxed.transmission)
        assertTrue(boxed.money < studied.money)
        val four = Racing.fitPart(boxed, "box", "four")
        assertEquals("four", four.transmission)
        assertEquals(boxed.money, four.money, 0.01)
        val refit = Racing.fitPart(four, "box", "crash")
        assertEquals("crash", refit.transmission)
        assertEquals(four.money, refit.money, 0.01)
        val shod = Racing.fitPart(studied, "tire", "radial")
        assertEquals("radial", shod.tire)
        assertTrue(shod.money < studied.money)
        val bare = Racing.fitPart(shod, "tire", "bias")
        assertEquals(shod.money, Racing.fitPart(bare, "tire", "radial").money, 0.01)
    }

    @Test
    fun theStakeIsTakenUpFrontAndOnlyAWinPaysItBack() {
        val broke = Racing.openStake(GameState(money = 10.0), 1L)
        assertNull(broke)
        val (paid, field) = Racing.openStake(GameState(money = 5_000.0), 7L)!!
        val stake = Racing.stakeCost(0)
        assertEquals(200.0, stake, 0.0)
        assertEquals(5_000.0 - stake, paid.money, 0.01)
        assertEquals(2.0 * stake, field.purse, 0.01)
        assertEquals(5_000.0 + stake, Racing.award(paid, field, won = true, ladder = false).money, 0.01)
        assertEquals(5_000.0 - stake, Racing.award(paid, field, won = false, ladder = false).money, 0.01)
        val dev = GameState(money = 0.0, prestiges = 2, devMode = true)
        val (free, practice) = Racing.openStake(dev, 3L)!!
        assertEquals(0.0, free.money, 0.01)
        assertEquals(0.0, practice.purse, 0.01)
        assertEquals(0.0, Racing.award(free, practice, won = true, ladder = false).money, 0.01)
    }

    @Test
    fun aLadderWinMovesTheRungAndALossLeavesIt() {
        val start = GameState()
        val field = Racing.fieldForLadder(start)
        val won = Racing.award(start, field, won = true, ladder = true)
        assertEquals(1, won.raceLadder)
        assertTrue(won.money > start.money)
        val lost = Racing.award(start, field, won = false, ladder = true)
        assertEquals(0, lost.raceLadder)
        assertEquals(start.money, lost.money, 0.01)
        assertTrue(Racing.stripNote(start, field, won = false, time = 20.0, rival = 14.0, ladder = true).contains("Short"))
    }

    @Test
    fun slippingTheClutchHooksAFullThrottleLaunch() {
        val car = sample()
        val slip = RaceDrive.clutchForLaunch(car)
        assertTrue("slip $slip", slip > 0.08)
        val dumped = runFor(car, 1.6) { _, _ -> 1.0 to false }
        val slipped = runFor(car, 1.6, clutch = { _, _ -> slip }) { _, _ -> 1.0 to false }
        assertTrue(
            "slipped ${"%.1f".format(slipped.distance)} m vs dumped ${"%.1f".format(dumped.distance)} m",
            slipped.distance > dumped.distance * 1.15,
        )
        val feather = { setup: RaceDrive.Setup, run: RaceDrive.Run ->
            val t = if (run.gear == 1 && run.speed < 14.0 && !run.hooked) RaceDrive.launchThrottle(setup) else 1.0
            t to false
        }
        val heldOpen = runFor(car, 1.2, clutch = { _, _ -> 1.0 }, driver = feather)
        val engaged = runFor(car, 1.2, driver = feather)
        assertTrue(
            "open ${heldOpen.distance} engaged ${engaged.distance}",
            engaged.distance > heldOpen.distance * 3.0,
        )
    }

    @Test
    fun theClutchSpringsClosedWhenYouLetGo() {
        assertEquals(0.85, RaceDrive.releaseClutch(0.85, 0.0), 0.001)
        var pedal = 0.85
        var t = 0.0
        while (t < 0.45) {
            pedal = RaceDrive.releaseClutch(pedal, 1.0 / 60.0)
            t += 1.0 / 60.0
        }
        assertEquals(0.0, pedal, 0.001)
    }

    @Test
    fun aStockMopedCanFinishTheQuarter() {
        val run = RaceDrive.driveWell(RaceDrive.setup(GameState()))
        assertTrue("moped ${run.time}s ${run.distance}m gear ${run.gear}", run.distance >= RaceDrive.FINISH_M - 1.0)
    }

    private fun sample() = RaceDrive.Setup(
        peakHp = 180.0,
        peakTqLbFt = 190.0,
        redline = 7000.0,
        idle = 900.0,
        massKg = 980.0,
        ratios = listOf(2.52, 1.74, 1.27, 1.00),
        finalDrive = 3.73,
        efficiency = 0.88,
        gripN = 980.0 * 9.81 * 0.55,
        drag = 0.72,
        shiftSeconds = 0.36,
    )

    private fun clean(setup: RaceDrive.Setup, run: RaceDrive.Run): Pair<Double, Boolean> {
        val t = if (run.gear == 1 && run.speed < 14.0 && !run.hooked) RaceDrive.launchThrottle(setup) else 1.0
        val up = run.hooked && run.rpm > setup.redline * 0.91 && run.gear < setup.ratios.size
        return t to up
    }

    private fun runFor(
        setup: RaceDrive.Setup,
        seconds: Double,
        clutch: (RaceDrive.Setup, RaceDrive.Run) -> Double = { _, _ -> 0.0 },
        driver: (RaceDrive.Setup, RaceDrive.Run) -> Pair<Double, Boolean>,
    ): RaceDrive.Run {
        val run = RaceDrive.fresh(setup)
        var guard = 0
        while (run.time < seconds && run.distance < RaceDrive.FINISH_M && guard < 8_000) {
            guard++
            val (throttle, shift) = driver(setup, run)
            RaceDrive.step(setup, run, throttle, shift, 1.0 / 60.0, clutch(setup, run))
        }
        return run
    }
}
