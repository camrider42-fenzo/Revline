package com.revline.idle.model

/**
 * How the tailpipe speaks. Stock is a muffled log manifold.
 * Headers bring a rasp, a cat-back opens the pipe, a cam lopes, and a tip adds bark.
 */
data class ExhaustNote(
    val open: Double = 0.0,
    val rasp: Double = 0.0,
    val bark: Double = 0.0,
    val lope: Double = 0.0,
)

fun exhaustNote(
    backpressure: Double,
    headers: Int,
    longTube: Int,
    tip: Int,
    catback: Int,
    cam: Int,
    ceramic: Boolean,
): ExhaustNote {
    val header = (headers / 4.0).coerceIn(0.0, 1.0)
    val tubes = (longTube / 3.0).coerceIn(0.0, 1.0)
    val outlet = (tip / 3.0).coerceIn(0.0, 1.0)
    val cat = (catback / 4.0).coerceIn(0.0, 1.0)
    val stick = (cam / 4.0).coerceIn(0.0, 1.0)
    val coat = if (ceramic) 0.18 else 0.0
    val fromBp = ((1.08 - backpressure) / 0.75).coerceIn(0.0, 1.0)
    val open = (0.22 * fromBp + 0.40 * header + 0.26 * tubes + 0.24 * cat + 0.10 * outlet).coerceIn(0.0, 1.0)
    val rasp = (0.72 * header + 0.48 * tubes + 0.28 * stick).coerceIn(0.0, 1.0)
    val bark = (0.55 * outlet + 0.42 * cat + 0.28 * header + coat).coerceIn(0.0, 1.0)
    val lope = (0.82 * stick + 0.18 * tubes).coerceIn(0.0, 1.0)
    return ExhaustNote(open, rasp, bark, lope)
}
