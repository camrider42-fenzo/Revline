package com.revline.idle.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * How hot the bay drawing looks. Coolant, oil, exhaust, and soak each rise from a cool stop
 * to a hot one. The brightest of the four is the glow. Dropping any of them pulls the color back.
 * The wires stay the neon ones: the color moves from the instrument hue toward amber, then red.
 */
internal object HeatGlow {
    fun intensity(coolantC: Float, oilC: Float, egtC: Float, soakC: Float): Float = maxOf(
        channel(coolantC, 96f, 122f),
        channel(oilC, 115f, 155f),
        channel(egtC, 620f, 940f),
        channel(soakC, 4f, 28f),
    )

    /** Head and intake. Lime while cool. Amber, then red, as coolant, oil, or soak climb. */
    fun head(coolantC: Float, oilC: Float, soakC: Float): Color = shift(
        Lime,
        maxOf(channel(coolantC, 96f, 122f), channel(oilC, 115f, 155f), channel(soakC, 4f, 28f)),
    )

    /** Exhaust and the manifold. Amber while cool. Red as the gas or the soak climbs. */
    fun exhaust(egtC: Float, soakC: Float): Color = shift(
        Amber,
        maxOf(channel(egtC, 520f, 900f), channel(soakC, 4f, 28f)),
    )

    /** How hard the exhaust wire burns. Same window the old gain used, plus soak. */
    fun gain(egtC: Float, soakC: Float): Float {
        val heat = maxOf(channel(egtC, 420f, 900f), channel(soakC, 6f, 30f))
        return (0.55f + 0.9f * heat).coerceIn(0.55f, 1.45f)
    }

    private fun channel(value: Float, cool: Float, hot: Float): Float {
        val span = (hot - cool).coerceAtLeast(1f)
        return ((value - cool) / span).coerceIn(0f, 1f)
    }

    private fun shift(base: Color, heat: Float): Color {
        if (heat <= 0.02f) return base
        val toward = lerp(Amber, Red, ((heat - 0.35f) / 0.65f).coerceIn(0f, 1f))
        return lerp(base, toward, (0.15f + 0.75f * heat).coerceIn(0f, 0.9f))
    }
}
