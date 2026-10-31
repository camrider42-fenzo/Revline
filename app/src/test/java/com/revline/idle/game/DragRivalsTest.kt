package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.economy.DriverBuilds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DragRivalsTest {
    private val perfect = Skills(1.0, 1.0, 1.0, 1.0)

    /** Every kind of drag opponent the game can put on the strip, across every block. */
    private fun everyRival(): List<Pair<String, DragRivals.Entrant>> {
        val out = ArrayList<Pair<String, DragRivals.Entrant>>()
        for (p in 0..7) {
            val state = GameState(prestiges = p)
            for (tier in Racing.Tier.values()) out.add("p$p ${tier.name}" to Racing.fieldForQuick(state, tier).rival)
            for (s in 1..6) out.add("p$p stake$s" to Racing.openStake(state.copy(devMode = true), 900L + s * 17L)!!.second.rival)
        }
        Racing.ladderRungs().forEach { out.add("rung ${it.title}" to it.entrant) }
        return out
    }

    @Test
    fun everyFieldCarriesABuildAndADriverNotATime() {
        val state = GameState(prestiges = 3, devMode = true)
        assertTrue(Racing.fieldForQuick(state, Racing.Tier.CLUB).rival.spec.hp > 0.0)
        assertTrue(Racing.fieldForLadder(state).rival.spec.hp > 0.0)
        assertTrue(Racing.openStake(state, 5L)!!.second.rival.spec.hp > 0.0)
        // A stronger tier at the same block is a stronger build or a better driver, never a different clock.
        val club = Racing.fieldForQuick(state, Racing.Tier.CLUB).rival
        val intl = Racing.fieldForQuick(state, Racing.Tier.INTERNATIONAL).rival
        assertTrue(intl.skills.shifting > club.skills.shifting)
        assertTrue(intl.spec.tire.mu > club.spec.tire.mu)
    }

    @Test
    fun theSameBuildAndPerfectHandsGiveTheSameResultAsThePlayerSim() {
        for (p in listOf(0, 3, 7)) for (build in DriverBuilds.orientations(p)) {
            val state = build.state
            val sheet = Logic.sheet(state)
            val spec = Racing.specOf(state)
            val entrant = DragRivals.Entrant(
                "mirror", spec, sheet.params.redlineRpm, sheet.params.idleRpm, Racing.finalRatio(state.finalDrive), perfect,
            )
            val meters = Racing.lengthOf(state.raceLength).meters
            val rivalSetup = DragRivals.setup(entrant, meters)
            // The player's setup carries the block's stock displacement for the sound model. Everything the sim reads is the same.
            val playerSetup = RaceDrive.setup(state).copy(displacementCc = spec.displacementCc)
            assertEquals("p$p ${build.name} setup", playerSetup, rivalSetup)
            val seed = 4_242L
            val mine = AutoRace(playerSetup, perfect, seed).finish()
            val theirs = DragRivals.pass(rivalSetup, perfect, seed)
            assertEquals("p$p ${build.name} ET", mine.run.time, theirs.time, 1e-9)
            assertEquals("p$p ${build.name} trap", mine.run.speed, theirs.trapMps, 1e-9)
            // And the clean-run helper the player's own hint uses agrees with them to within the pilot's tiny read noise.
            val clean = RaceDrive.driveWell(playerSetup)
            assertTrue("clean ${clean.time} ai ${theirs.time}", theirs.time > clean.time * 0.97 && theirs.time < clean.time * 1.12)
        }
    }

    @Test
    fun noRivalTimeBeatsWhatItsBuildAllows() {
        val meters = RaceDrive.FINISH_M
        for ((name, e) in everyRival()) {
            val setup = DragRivals.setup(e, meters)
            val flawless = DragRivals.flawless(e, meters)
            assertTrue("$name flawless pass finishes", flawless.reached && !flawless.jumped)
            val powerFloor = DragRivals.powerFloor(setup)
            val sixtyFloor = DragRivals.sixtyFloor(setup)
            assertTrue("$name flawless ${flawless.time} under the power floor $powerFloor", flawless.time > powerFloor)
            assertTrue("$name flawless 60ft ${flawless.sixtyFt} under the traction/engine floor $sixtyFloor", flawless.sixtyFt > sixtyFloor)
            for (seed in 1..6) {
                val pass = DragRivals.run(e, meters, 30L * seed)
                assertTrue("$name seed $seed finishes", pass.reached && !pass.jumped)
                assertTrue("$name ET ${pass.time} floor $powerFloor", pass.time > powerFloor)
                assertTrue("$name 60ft ${pass.sixtyFt} floor $sixtyFloor", pass.sixtyFt > sixtyFloor)
                assertTrue("$name ET ${pass.time} beat flawless ${flawless.time}", pass.time >= flawless.time * 0.99)
                assertTrue("$name 60ft ${pass.sixtyFt} beat flawless ${flawless.sixtyFt}", pass.sixtyFt >= flawless.sixtyFt * 0.95)
                assertTrue("$name 60ft within the ET", pass.sixtyFt < pass.time * 0.5 && pass.rolled <= pass.sixtyFt)
            }
        }
    }

    @Test
    fun aRivalLaunchNeverPutsMoreForceDownThanTheTiresHold() {
        for ((name, e) in everyRival()) {
            val setup = DragRivals.setup(e, RaceDrive.FINISH_M)
            for (seed in listOf(3L, 11L)) {
                val race = AutoRace(setup, e.skills, seed)
                var worst = 0.0
                var seen = 0
                var guard = 0
                while (!race.over && guard++ < 30_000) {
                    val run = race.run
                    val launching = !race.staging && run.gear == 1 && run.speed < 14.0 && !run.hooked
                    val v0 = run.speed
                    val t0 = run.time
                    race.advance(1.0 / 60.0)
                    if (launching && run.time > t0 && run.speed > v0) {
                        val drag = RaceDrive.dragForce(setup, v0)
                        val force = setup.massKg * (run.speed - v0) / (run.time - t0) + drag
                        worst = maxOf(worst, force / setup.gripN)
                        seen++
                    }
                }
                assertTrue("$name seed $seed never launched", seen > 5)
                assertTrue("$name seed $seed launch used ${"%.3f".format(worst)} x the grip", worst <= 1.001)
            }
        }
    }

    @Test
    fun difficultyComesFromTheDriversHands() {
        val club = GameState(prestiges = 4)
        val e = Racing.fieldForQuick(club, Racing.Tier.NATIONAL).rival
        val meters = RaceDrive.FINISH_M
        val setup = DragRivals.setup(e, meters)
        fun avg(skills: Skills) = (1..10).map { DragRivals.pass(setup, skills, 77L * it) }.filter { it.reached && !it.jumped }.map { it.time }.average()
        val sloppy = avg(Skills(0.25, 0.45, 0.25, 0.25))
        val fair = avg(Skills(0.6, 0.6, 0.6, 0.6))
        val sharp = avg(perfect)
        assertTrue("sloppy $sloppy fair $fair sharp $sharp", sloppy > fair && fair > sharp)
        // A better build with the same hands is quicker too.
        val weak = DragRivals.Entrant("weak", e.spec.copy(hp = e.spec.hp * 0.6, tq = e.spec.tq * 0.6), e.redline, e.idle, e.finalDrive, e.skills)
        assertTrue(DragRivals.flawless(weak, meters).time > DragRivals.flawless(e, meters).time)
    }

    @Test
    fun theLaneFollowsARealLaunchAndNotAConstantSpeed() {
        val e = Racing.fieldForQuick(GameState(prestiges = 2), Racing.Tier.INTERNATIONAL).rival
        val pass = DragRivals.run(e, RaceDrive.FINISH_M, 9L)
        assertTrue(pass.reached)
        assertEquals(0.0, pass.distanceAt(0.0, RaceDrive.FINISH_M), 1e-6)
        assertEquals(RaceDrive.FINISH_M, pass.distanceAt(pass.time + 0.5, RaceDrive.FINISH_M), 1e-6)
        assertEquals(DragRivals.SIXTY_FT, pass.distanceAt(pass.sixtyFt, RaceDrive.FINISH_M), 0.6)
        var last = -1.0
        for (i in 0..40) {
            val d = pass.distanceAt(pass.time * i / 40.0, RaceDrive.FINISH_M)
            assertTrue("never goes backwards", d >= last)
            last = d
        }
        // Constant speed would put it a quarter of the way at a quarter of the time. A car that is still gaining speed has not.
        assertTrue(pass.distanceAt(pass.time * 0.25, RaceDrive.FINISH_M) < RaceDrive.FINISH_M * 0.25 * 0.8)
        // It crosses the line at a believable trap speed, not a stored one.
        assertTrue(pass.trapMps > RaceDrive.FINISH_M / pass.time * 1.3)
    }

    @Test
    fun theSameSeedIsTheSameRunAndAnotherSeedIsAnotherRun() {
        val e = Racing.fieldForQuick(GameState(prestiges = 5), Racing.Tier.CLUB).rival
        val a = DragRivals.run(e, RaceDrive.FINISH_M, 5L)
        val b = DragRivals.run(e, RaceDrive.FINISH_M, 5L)
        val c = DragRivals.run(e, RaceDrive.FINISH_M, 6L)
        assertEquals(a.time, b.time, 0.0)
        assertEquals(a.sixtyFt, b.sixtyFt, 0.0)
        assertFalse(a.time == c.time)
    }

    @Test
    fun aRedLightIsRestagedSoTheRivalAlwaysPostsATime() {
        val e = Racing.fieldForQuick(GameState(prestiges = 1), Racing.Tier.CLUB).rival.let {
            it.copy(skills = Skills(0.2, 0.0, 0.2, 0.2))
        }
        var restaged = 0
        for (seed in 1..40) {
            val pass = DragRivals.run(e, RaceDrive.FINISH_M, seed * 101L)
            assertFalse(pass.jumped)
            assertTrue(pass.restages <= DragRivals.RESTAGES)
            if (pass.restages > 0) restaged++
        }
        // A driver with no clutch feel does throw red lights, and is called back for them.
        val unskilled = (1..40).count { DragRivals.pass(DragRivals.setup(e, RaceDrive.FINISH_M), e.skills, it * 101L).jumped }
        assertTrue("unskilled jumps $unskilled restaged $restaged", unskilled == 0 || restaged > 0)
    }
}
