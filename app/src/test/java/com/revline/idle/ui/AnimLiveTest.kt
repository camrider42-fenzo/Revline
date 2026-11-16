package com.revline.idle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.revline.idle.game.Logic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class AnimLiveTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before fun resetClocks() {
        DrawClock.reset()
        AudioClock.reset()
    }

    @Test fun aPictureDrawsOnlyWhileResumedOnScreenAndSelected() {
        assertTrue(AnimLive.drawing(resumed = true, onScreen = true, pageSelected = true))
        assertFalse(AnimLive.drawing(resumed = false, onScreen = true, pageSelected = true))
        assertFalse(AnimLive.drawing(resumed = true, onScreen = false, pageSelected = true))
        assertFalse(AnimLive.drawing(resumed = true, onScreen = true, pageSelected = false))
        assertFalse(AnimLive.drawing(resumed = true, onScreen = true, pageSelected = true, saver = true))
    }

    @Test fun boundsOutsideTheWindowAreNotOnScreen() {
        assertTrue(AnimLive.onScreen(0f, 0f, 100f, 40f, 400f, 800f))
        assertTrue(AnimLive.onScreen(-20f, -10f, 5f, 8f, 400f, 800f))
        assertFalse(AnimLive.onScreen(-200f, -80f, -40f, -10f, 400f, 800f))
        assertFalse(AnimLive.onScreen(0f, 900f, 100f, 980f, 400f, 800f))
        assertFalse(AnimLive.onScreen(10f, 10f, 10f, 40f, 400f, 800f))
    }

    @Test fun theEngineClockStopsOffScreenAndStartsAgain() {
        val params = Logic.sheet(Logic.newGame()).params
        var hide by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                EngineArt(
                    params, 3000f, 80f, 400f,
                    modifier = Modifier.offset(y = if (hide) (-4000).dp else 0.dp).size(180.dp, 100.dp),
                )
            }
        }
        rule.mainClock.advanceTimeByFrame()
        val before = DrawClock.engine
        rule.mainClock.advanceTimeBy(320)
        val shown = DrawClock.engine - before
        assertTrue("on screen the rotor takes frames ($shown)", shown >= 8)

        hide = true
        rule.mainClock.advanceTimeBy(64)
        val hiddenAt = DrawClock.engine
        rule.mainClock.advanceTimeBy(480)
        assertEquals("off screen the rotor clock is stopped, avoided the next 480ms", hiddenAt, DrawClock.engine)

        hide = false
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        repeat(8) { rule.mainClock.advanceTimeByFrame() }
        val back = DrawClock.engine
        rule.mainClock.advanceTimeBy(320)
        assertTrue("coming back onto the screen starts the clock", DrawClock.engine > back)
    }

    @Test fun theEngineClockStopsWhenThePageIsNotSelected() {
        val params = Logic.sheet(Logic.newGame()).params
        rule.setContent {
            CompositionLocalProvider(LocalAnimPage provides false) {
                EngineArt(params, 3000f, 80f, 400f, modifier = Modifier.size(180.dp, 100.dp))
            }
        }
        rule.waitForIdle()
        val held = DrawClock.engine
        rule.mainClock.advanceTimeBy(320)
        assertEquals(held, DrawClock.engine)
    }

    @Test fun theEngineClockStopsWhilePausedAndRestartsOnResume() {
        val params = Logic.sheet(Logic.newGame()).params
        val owner = ManualOwner()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                EngineArt(params, 3000f, 80f, 400f, modifier = Modifier.size(180.dp, 100.dp))
            }
        }
        rule.mainClock.advanceTimeByFrame()
        val before = DrawClock.engine
        rule.mainClock.advanceTimeBy(320)
        assertTrue(DrawClock.engine > before)

        rule.runOnUiThread { owner.move(Lifecycle.State.STARTED) }
        rule.mainClock.advanceTimeBy(64)
        val paused = DrawClock.engine
        rule.mainClock.advanceTimeBy(480)
        assertEquals("paused activity does not spin the rotor", paused, DrawClock.engine)

        rule.runOnUiThread {
            owner.move(Lifecycle.State.RESUMED)
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        repeat(8) { rule.mainClock.advanceTimeByFrame() }
        val resumed = DrawClock.engine
        rule.mainClock.advanceTimeBy(320)
        assertTrue("resume starts the rotor again", DrawClock.engine > resumed)
    }

    @Test fun theDynoNoteKeepsPublishingWhileTheRotorIsHidden() {
        val params = Logic.sheet(Logic.newGame()).params
        val speaker = EngineSpeaker(EngineCrank())
        var rpm by mutableStateOf(1400f)
        rule.setContent {
            CompositionLocalProvider(LocalAnimPage provides false) {
                DynoNote(speaker, params, rpm, 0.4f, live = true, limiter = false)
                EngineArt(params, rpm, 80f, 400f, modifier = Modifier.size(180.dp, 100.dp))
            }
        }
        rule.waitForIdle()
        assertEquals(1400f, speaker.crank.rpm, 1f)
        val pubs = AudioClock.publishes
        val frames = DrawClock.engine
        rpm = 6200f
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(320)
        assertEquals(6200f, speaker.crank.rpm, 1f)
        assertTrue("the note published the new rpm", AudioClock.publishes > pubs)
        assertEquals("the hidden rotor did not take a frame", frames, DrawClock.engine)
    }

    @Test fun rpmFollowKeepsMovingWhenThePictureIsNotDrawing() {
        assertFalse(AnimLive.drawing(resumed = true, onScreen = false, pageSelected = true))
        val params = Logic.sheet(Logic.newGame()).params
        val crank = EngineCrank()
        val first = SpeakerFollow.step(crank, params, Float.NaN, 2200f, 0.05f, 0.9f, limiter = false)
        val second = SpeakerFollow.step(crank, params, first, 6400f, 0.05f, 0.9f, limiter = false)
        assertTrue(crank.rpm > first)
        assertEquals(second, crank.rpm, 0.5f)
        assertTrue(second > first)
        assertEquals(0, DrawClock.engine)
    }
}

/** A lifecycle the test can pause without backgrounding the activity the compose rule owns. */
private class ManualOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun move(state: Lifecycle.State) {
        registry.currentState = state
    }
}
