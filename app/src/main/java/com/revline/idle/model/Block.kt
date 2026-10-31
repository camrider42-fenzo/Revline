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
    /**
     * mpg the engine must show at its best-paying pedal, looking no higher than
     * [com.revline.idle.game.Logic.GATE_CAP]. Not cruise. Stock misses it on every engine,
     * the economy parts are what lift it, and a build with the parts and the power clears it.
     */
    val mpgGoal: Double,
) {
    MOPED("50cc moped single", 1, 1, 39.0, 41.4, 8.2, 6800.0, 1700.0, 0.78, 0.70, 140.0, 14.0, 0.45, true, 0.24, 56.0, 222.0, 104.7),
    THUMPER("250cc thumper", 1, 1, 74.0, 58.0, 9.0, 7600.0, 1300.0, 0.86, 0.64, 280.0, 16.0, 0.70, true, 3.01, 60.0, 134.0, 74.0),
    TWIN("500cc parallel twin", 2, 1, 72.0, 61.5, 10.2, 8200.0, 1200.0, 0.90, 0.62, 420.0, 18.0, 0.90, false, 5.84, 105.0, 145.0, 55.0),
    TRIPLE("1.0L inline-three", 3, 1, 76.0, 73.5, 10.8, 7800.0, 950.0, 0.91, 0.58, 560.0, 18.0, 1.05, false, 8.26, 150.0, 179.0, 36.0),
    INLINE_FOUR("2.0L inline-four", 4, 1, 86.0, 86.0, 9.5, 6500.0, 820.0, 0.93, 0.58, 760.0, 16.0, 1.25, false, 12.77, 240.0, 245.0, 22.5),
    V6("3.5L V6", 6, 2, 94.0, 83.0, 10.2, 7000.0, 750.0, 0.98, 0.56, 2000.0, 18.0, 2.05, false, 34.55, 420.0, 350.0, 12.0),
    V8("5.0L V8", 8, 2, 93.0, 92.0, 10.2, 7000.0, 700.0, 1.02, 0.56, 2800.0, 20.0, 3.10, false, 39.94, 580.0, 480.0, 8.7),
    V12("6.5L V12", 12, 2, 89.0, 87.0, 10.6, 8000.0, 900.0, 1.06, 0.62, 3800.0, 22.0, 4.20, false, 50.77, 800.0, 585.0, 6.2),
    ;

    /**
     * Highest tach the stroke will live with. The ladder climbs to 10,000 rpm
     * on the V12. A rev kit and springs can reach it. They cannot pass it.
     */
    fun redlineCap(): Double = when (this) {
        MOPED -> 8000.0
        THUMPER -> 8600.0
        TWIN -> 9000.0
        TRIPLE -> 9200.0
        INLINE_FOUR -> 9400.0
        V6 -> 9600.0
        V8 -> 9800.0
        V12 -> 10000.0
    }

    /**
     * Puts each stock engine's best pedal back on the [stockPay] anchor.
     * The check is maximum output times the share of it this stop is using,
     * times current mpg, so a small engine that lives near its peak needs
     * less scale than a big one whose best stop is only part of the peak.
     */
    fun payScale(): Double = when (this) {
        MOPED -> 0.0131
        THUMPER -> 0.46
        TWIN -> 0.72
        TRIPLE -> 0.96
        INLINE_FOUR -> 0.88
        V6 -> 1.58
        V8 -> 2.10
        V12 -> 4.04
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
        // 0.52 keeps that same list from clearing while the contract is still short.
        val shop = 0.52 * rebuildPace()
        val trim = when (this) {
            MOPED -> 1.0
            THUMPER -> 0.90
            TWIN -> 0.88
            TRIPLE -> 1.20
            INLINE_FOUR -> 1.05
            V6 -> 1.22
            V8 -> 1.90
            V12 -> 1.48
        }
        return minMultiplier * pace * glide * trim * shop
    }

    /**
     * How much slower than before this engine's parts pay for themselves. The old
     * sheet cleared in 0.8 to 1.0 hours on every engine, which was too easy. With this
     * the rebuild runs about 1.6 hours on the moped and 0.2 hours longer on each engine
     * after it, up to about 3 hours on the V12. These were fitted by simulation
     * (BlockPaceTest) rather than derived.
     */
    fun rebuildPace(): Double = when (this) {
        MOPED -> 1.944
        THUMPER -> 1.974
        TWIN -> 1.983
        TRIPLE -> 2.711
        INLINE_FOUR -> 2.721
        V6 -> 3.129
        V8 -> 3.025
        V12 -> 3.545
    }

    /** Race sheet: more power, less cruise. The street numbers stay the rebuild the shop was tuned on. */
    fun sheetHp(racing: Boolean): Double = if (racing) hpGoal * 1.42 else hpGoal

    fun sheetTorque(racing: Boolean): Double = if (racing) torqueGoal * 1.18 else torqueGoal

    /** Light vehicles go further on the same fuel. The V12 car does not. */
    fun mpgScale(): Double = when (this) {
        MOPED -> 1.5
        THUMPER -> 1.18
        TWIN -> 1.03
        TRIPLE -> 0.95
        INLINE_FOUR -> 0.85
        V6 -> 0.87
        V8 -> 0.82
        V12 -> 0.78
    }

    /** Best mpg that class of vehicle can ever show: a moped near 100, the cars between 25 and 55. */
    fun mpgCap(): Double = when (this) {
        MOPED -> 105.0
        THUMPER -> 80.0
        TWIN -> 70.0
        TRIPLE -> 62.0
        INLINE_FOUR -> 55.0
        V6 -> 40.0
        V8 -> 32.0
        V12 -> 26.0
    }

    fun sheetMpg(racing: Boolean): Double = if (racing) mpgGoal * 0.64 else mpgGoal

    fun sheetTitle(racing: Boolean): String = if (racing) "Race $title" else title

    companion object {
        fun forPrestige(prestiges: Int): Block = entries[prestiges.coerceIn(0, entries.lastIndex)]
    }
}
