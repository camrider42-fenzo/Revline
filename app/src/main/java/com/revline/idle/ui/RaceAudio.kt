package com.revline.idle.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.exp

/**
 * Which note the speaker plays while a race is on screen.
 * Under 8x the pitch follows the car. At 8x and faster the film is too quick
 * for that, so the speaker stays on the dyno note instead of warbling.
 * A paused, finished, or muted race is quiet.
 */
object RaceAudio {
    /** Playback at this multiple, and every faster chip, uses the dyno note. */
    const val DYNO_FROM = 8.0

    /** How long the spoken rpm takes to catch the car, so one frame is not a click. */
    const val SMOOTH_S = 0.05f

    enum class Source { TRACK, DYNO, QUIET }

    /** [playing] means the film is running. A paused or finished race is quiet, including at 8x. */
    fun source(playback: Double, playing: Boolean): Source = when {
        !playing -> Source.QUIET
        playback >= DYNO_FROM -> Source.DYNO
        else -> Source.TRACK
    }

    /** A drag pass has no film speed. It follows the car while the pass is going. */
    fun dragSource(running: Boolean): Source = if (running) Source.TRACK else Source.QUIET

    /**
     * Rpm the speaker should hold after [dt] real seconds, moving from [from] toward [target].
     * A zero step holds. The move never overshoots, so a shift is a drop you can hear, not a pop.
     */
    fun smoothRpm(from: Float, target: Float, dt: Float, tau: Float = SMOOTH_S): Float {
        if (!target.isFinite()) return if (from.isFinite()) from else 0f
        if (!from.isFinite()) return target
        if (dt <= 0f || tau <= 0f) return from
        val blend = (1.0 - exp((-dt / tau).toDouble())).toFloat().coerceIn(0f, 1f)
        return from + (target - from) * blend
    }

    /** Pedal the circuit gauges imply. Braking is a lift. Otherwise the car is on it. */
    fun circuitThrottle(braking: Boolean): Float = if (braking) 0.12f else 0.92f

    /** Fuel cut when the trace is on the redline. The voice already chops on this flag. */
    fun circuitLimiter(rpm: Double, redline: Double): Boolean = redline > 0.0 && rpm >= redline * 0.98
}

/**
 * Who currently owns [crank]. The dyno publishes only while the source is [RaceAudio.Source.DYNO].
 * A race sets [RaceAudio.Source.TRACK] and publishes the car, or [RaceAudio.Source.QUIET] to stop.
 */
class EngineSpeaker(val crank: EngineCrank) {
    var source by mutableStateOf(RaceAudio.Source.DYNO)
}

val LocalEngineSpeaker = compositionLocalOf<EngineSpeaker?> { null }
