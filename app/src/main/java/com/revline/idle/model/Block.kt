package com.revline.idle.model

import kotlin.math.pow

/**
 * Long-blocks in rising stock power. Prestige count picks the block.
 * Past the last one, further rebuilds stay on the V12 and only add knowledge.
 */
enum class Block(
    val title: String,
    val cylinders: Int,
    val banks: Int,
    val boreMm: Double,
    val strokeMm: Double,
    val compression: Double,
    val redlineRpm: Double,
    val idleRpm: Double,
    val peakVe: Double,
    val vePeakFraction: Double,
    val radiatorUa: Double,
    val timingDeg: Double,
    val fuelFlow: Double,
    val airCooled: Boolean,
    /** Knowledge-0 dollars per second at the best pedal. Used to pace upgrade prices. */
    val stockPay: Double,
    val hpGoal: Double,
    val torqueGoal: Double,
    val mpgGoal: Double,
) {
    MOPED("50cc moped single", 1, 1, 39.0, 41.4, 8.2, 9000.0, 1700.0, 0.78, 0.70, 140.0, 14.0, 0.45, true, 0.24, 7.2, 14.9, 34.0),
    THUMPER("250cc thumper", 1, 1, 74.0, 58.0, 9.0, 8500.0, 1300.0, 0.86, 0.64, 280.0, 16.0, 0.70, true, 3.01, 23.5, 17.8, 33.8),
    TWIN("500cc parallel twin", 2, 1, 72.0, 61.5, 10.2, 10500.0, 1200.0, 0.90, 0.62, 420.0, 18.0, 0.90, false, 5.84, 55.0, 37.0, 34.0),
    TRIPLE("1.0L inline-three", 3, 1, 76.0, 73.5, 10.8, 7800.0, 950.0, 0.91, 0.58, 560.0, 18.0, 1.05, false, 8.26, 83.0, 74.0, 37.2),
    INLINE_FOUR("2.0L inline-four", 4, 1, 86.0, 86.0, 9.5, 6500.0, 820.0, 0.93, 0.58, 760.0, 16.0, 1.25, false, 12.77, 165.0, 150.0, 29.2),
    V6("3.5L V6", 6, 2, 94.0, 83.0, 10.2, 7000.0, 750.0, 0.98, 0.56, 2000.0, 18.0, 2.05, false, 34.55, 295.0, 266.0, 26.2),
    V8("5.0L V8", 8, 2, 93.0, 92.0, 10.2, 7000.0, 700.0, 1.02, 0.56, 2800.0, 20.0, 3.10, false, 39.94, 420.0, 380.0, 21.2),
    V12("6.5L V12", 12, 2, 89.0, 87.0, 10.6, 8000.0, 900.0, 1.06, 0.62, 3800.0, 22.0, 4.20, false, 50.77, 690.0, 520.0, 18.0),
    ;

    /**
     * Puts each stock engine's best pedal back on the [stockPay] anchor.
     * The check is maximum output times the share of it this stop is using,
     * times current mpg, so a small engine that lives near its peak needs
     * less scale than a big one whose best stop is only part of the peak.
     */
    fun payScale(): Double = when (this) {
        MOPED -> 0.30
        THUMPER -> 1.26
        TWIN -> 1.31
        TRIPLE -> 1.39
        INLINE_FOUR -> 1.28
        V6 -> 2.03
        V8 -> 2.95
        V12 -> 4.70
    }

    /**
     * Part prices on this block. Includes the smallest Engineering Knowledge multiplier
     * you can have on arrival (one point per earlier rebuild) and most of this
     * engine's extra contract versus the moped. The 0.93 soaks up most of that
     * contract, and a small per-engine trim keeps the same basket of parts a
     * little cheaper each time the contract itself barely changes.
     */
    fun upgradePriceScale(prestiges: Int): Double {
        val minMultiplier = 1.0 + 0.12 * prestiges.coerceAtLeast(0)
        val pace = (stockPay / MOPED.stockPay).pow(0.93)
        // Glide keeps a small contract step from stalling. Trim is the leftover
        // correction so the same bolt-on list, on 93, still gets shorter each engine.
        val glide = 0.94.pow(prestiges.coerceAtLeast(0))
        // The shop has to reach about 60% of the parts inside an hour or two, so the
        // fitted street-car prices are scaled down. The opening tools are cheap on top of this.
        val shop = 0.42
        val trim = when (this) {
            MOPED -> 1.0
            THUMPER -> 0.94
            TWIN -> 0.97
            TRIPLE -> 1.08
            INLINE_FOUR -> 0.90
            V6 -> 0.97
            V8 -> 1.0
            V12 -> 1.0
        }
        return minMultiplier * pace * glide * trim * shop
    }

    companion object {
        fun forPrestige(prestiges: Int): Block = entries[prestiges.coerceIn(0, entries.lastIndex)]
    }
}
