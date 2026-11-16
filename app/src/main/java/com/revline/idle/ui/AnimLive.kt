package com.revline.idle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Whether a picture should keep asking for frames.
 * The speaker does not read this. A hidden rotor still leaves the note running.
 */
internal object AnimLive {
    fun drawing(resumed: Boolean, onScreen: Boolean, pageSelected: Boolean, saver: Boolean = false): Boolean =
        resumed && onScreen && pageSelected && !saver

    /** True when the layout rectangle overlaps the window. A sliver still counts. */
    fun onScreen(left: Float, top: Float, right: Float, bottom: Float, windowW: Float, windowH: Float): Boolean {
        if (right - left <= 0.5f || bottom - top <= 0.5f) return false
        if (windowW <= 0f || windowH <= 0f) return false
        return left < windowW && top < windowH && right > 0f && bottom > 0f
    }
}

/** False on a tab or page that is composed but not the one on screen. */
val LocalAnimPage = compositionLocalOf { true }

/**
 * Draw-clock ticks taken while a picture was allowed to run.
 * Tests read these. The game does not branch on them.
 */
internal object DrawClock {
    var engine: Int = 0
    var film: Int = 0
    var gauge: Int = 0
    var bubble: Int = 0

    fun reset() {
        engine = 0
        film = 0
        gauge = 0
        bubble = 0
    }
}

/** Publishes into the crank. Separate from [DrawClock] so a hidden picture cannot mute the note. */
internal object AudioClock {
    var publishes: Int = 0
    fun reset() { publishes = 0 }
}

internal class DrawLive(val drawing: Boolean, val modifier: Modifier)

@Composable
internal fun rememberResumed(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> resumed = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return resumed
}

/**
 * Draw when the activity is resumed, this page is the selected one, and the
 * node overlaps the window. [modifier] has to sit on that node.
 * Starts as on-screen so the first frame is not a snap before layout.
 */
@Composable
internal fun rememberDrawLive(): DrawLive {
    val resumed = rememberResumed()
    val page = LocalAnimPage.current
    var onScreen by remember { mutableStateOf(true) }
    val track = Modifier.onGloballyPositioned { coords ->
        val shown = if (!coords.isAttached) {
            false
        } else {
            val b = coords.boundsInWindow()
            val root = coords.findRootCoordinates().size
            AnimLive.onScreen(b.left, b.top, b.right, b.bottom, root.width.toFloat(), root.height.toFloat())
        }
        if (shown != onScreen) onScreen = shown
    }
    val drawing = AnimLive.drawing(resumed, onScreen, page, LocalBatterySaver.current)
    return DrawLive(drawing, track)
}
