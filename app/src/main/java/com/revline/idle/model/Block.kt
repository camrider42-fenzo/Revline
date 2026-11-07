package com.revline.idle.model

import kotlin.math.pow

/**
 * Long-blocks in rising stock power. Prestige count picks the ladder step;
 * [forPrestige] is the piston default at that step. Rotary alternatives share
 * the step whose stock piston power is closest to theirs, chosen via [of]
 * + GameState.blockId. New rotaries append after ROTARY so ordinals 0..8 stay.
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
     * [com.revline.idle.game.Logic.GATE_CAP]. Not cruise. A build that keeps the
     * power parts clears it. Stock misses it, except where boost at that pedal
     * drinks more than the stock engine does.
     */
    val mpgGoal: Double,
    val family: EngineFamily = EngineFamily.PISTON,
    /**
     * Calibrated long-block mass (kg). Piston bases match pre-1.92
     * `35 + stockCc * 0.042` so stock cars keep total mass; rotaries sit light.
     */
    val baseEngineKg: Double,
    /**
     * Prestige ladder step this long-block sits on. Pistons use their ordinal;
     * rotary alternatives share a piston step and are picked through blockId.
     */
    val ladderStep: Int,
) {
    MOPED("50cc moped single", 1, 1, 39.0, 41.4, 8.2, 6800.0, 1700.0, 0.78, 0.70, 140.0, 14.0, 0.45, true, 0.24, 56.0, 222.0, 104.7, baseEngineKg = 37.1, ladderStep = 0),
    THUMPER("250cc thumper", 1, 1, 74.0, 58.0, 9.0, 7600.0, 1300.0, 0.86, 0.64, 280.0, 16.0, 0.70, true, 3.01, 60.0, 134.0, 74.0, baseEngineKg = 45.5, ladderStep = 1),
    TWIN("500cc parallel twin", 2, 1, 72.0, 61.5, 10.2, 8200.0, 1200.0, 0.90, 0.62, 420.0, 18.0, 0.90, false, 5.84, 105.0, 145.0, 55.0, baseEngineKg = 56.0, ladderStep = 2),
    TRIPLE("1.0L inline-three", 3, 1, 76.0, 73.5, 10.8, 7800.0, 950.0, 0.91, 0.58, 560.0, 18.0, 1.05, false, 8.26, 150.0, 179.0, 36.0, baseEngineKg = 77.0, ladderStep = 3),
    INLINE_FOUR("2.0L inline-four", 4, 1, 86.0, 86.0, 9.5, 6500.0, 820.0, 0.93, 0.58, 760.0, 16.0, 1.25, false, 12.77, 240.0, 245.0, 22.5, baseEngineKg = 118.9, ladderStep = 4),
    V6("3.5L V6", 6, 2, 94.0, 83.0, 10.2, 7000.0, 750.0, 0.98, 0.56, 2000.0, 18.0, 2.05, false, 34.55, 420.0, 350.0, 12.0, baseEngineKg = 180.2, ladderStep = 5),
    V8("5.0L V8", 8, 2, 93.0, 92.0, 10.2, 7000.0, 700.0, 1.02, 0.56, 2800.0, 20.0, 3.10, false, 39.94, 580.0, 480.0, 8.7, baseEngineKg = 245.0, ladderStep = 6),
    V12("6.5L V12", 12, 2, 89.0, 87.0, 10.6, 8000.0, 900.0, 1.06, 0.62, 3800.0, 22.0, 4.20, false, 50.77, 800.0, 580.0, 6.4, baseEngineKg = 307.8, ladderStep = 7),
    // Appended after V12 — prestige ordinals for earlier blocks stay put.
    // Goals lean rpm/hp and apex-seal durability, not stump-pulling torque. Thirsty mpg.
    // ladderStep follows closest stock piston power (not rotor count).
    ROTARY("1.3L 13B rotary", 2, 1, 80.0, 130.11, 9.7, 9000.0, 950.0, 1.08, 0.78, 2400.0, 18.0, 2.60, false, 55.0, 245.0, 170.0, 21.0, family = EngineFamily.ROTARY, baseEngineKg = 63.0, ladderStep = 4),
    ROTARY_1R("0.6L single-rotor", 1, 1, 80.0, 130.11, 8.5, 7200.0, 1200.0, 0.55, 0.68, 280.0, 14.0, 0.38, false, 2.80, 58.0, 50.0, 57.0, family = EngineFamily.ROTARY, baseEngineKg = 32.0, ladderStep = 1),
    ROTARY_RENESIS("1.3L Renesis 13B-MSP", 2, 1, 80.0, 130.11, 10.0, 8500.0, 900.0, 1.05, 0.72, 1600.0, 18.0, 1.40, false, 13.5, 240.0, 180.0, 22.0, family = EngineFamily.ROTARY, baseEngineKg = 68.0, ladderStep = 4),
    ROTARY_20B("2.0L 20B three-rotor", 3, 1, 80.0, 130.11, 9.7, 8500.0, 900.0, 1.06, 0.76, 2600.0, 19.0, 2.90, false, 40.0, 580.0, 420.0, 16.0, family = EngineFamily.ROTARY, baseEngineKg = 92.0, ladderStep = 5),
    ROTARY_26B("2.6L 26B four-rotor", 4, 1, 80.0, 130.11, 9.7, 8800.0, 950.0, 1.07, 0.76, 3400.0, 20.0, 3.80, false, 51.0, 460.0, 300.0, 14.0, family = EngineFamily.ROTARY, baseEngineKg = 118.0, ladderStep = 5),
    ;

    /**
     * Highest tach the stroke (or eccentric) will live with. The ladder climbs
     * past 10,000 rpm on the rotary. Rev hardware can reach the cap, not pass it.
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
        ROTARY, ROTARY_RENESIS -> 10500.0
        ROTARY_1R -> 9200.0
        ROTARY_20B -> 10200.0
        ROTARY_26B -> 10400.0
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
        ROTARY -> 4.40
        ROTARY_1R -> 0.46
        ROTARY_RENESIS -> 0.95
        ROTARY_20B -> 2.15
        ROTARY_26B -> 4.10
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
            ROTARY -> 1.55
            ROTARY_1R -> 1.0
            ROTARY_RENESIS -> 1.10
            ROTARY_20B -> 1.85
            ROTARY_26B -> 1.50
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
        ROTARY -> 3.620
        ROTARY_1R -> 1.960
        ROTARY_RENESIS -> 2.740
        ROTARY_20B -> 3.050
        ROTARY_26B -> 3.560
    }

    /** Race sheet: more power, less cruise. The street numbers stay the rebuild the shop was tuned on. */
    fun sheetHp(racing: Boolean): Double = if (racing) hpGoal * 1.21 else hpGoal

    fun sheetTorque(racing: Boolean): Double = if (racing) torqueGoal * 1.09 else torqueGoal

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
        ROTARY -> 0.72
        ROTARY_1R -> 1.35
        ROTARY_RENESIS -> 0.80
        ROTARY_20B -> 0.78
        ROTARY_26B -> 0.74
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
        ROTARY -> 36.0
        ROTARY_1R -> 85.0
        ROTARY_RENESIS -> 50.0
        ROTARY_20B -> 30.0
        ROTARY_26B -> 24.0
    }

    fun sheetMpg(racing: Boolean): Double = if (racing) mpgGoal * 0.64 else mpgGoal

    fun sheetTitle(racing: Boolean): String = if (racing) "Race $title" else title

    companion object {
        /** Highest prestige ladder step (top piston). Rotary alts do not extend it. */
        val maxLadderStep: Int get() = entries.maxOf { it.ladderStep }

        /** Long-blocks that share a prestige step (piston default + rotary alts). */
        fun alternativesAt(step: Int): List<Block> =
            entries.filter { it.ladderStep == step.coerceIn(0, maxLadderStep) }

        /**
         * Default long-block for a prestige step: the piston when both exist,
         * else the first alternative on that step.
         */
        fun forPrestige(prestiges: Int): Block {
            val step = prestiges.coerceIn(0, maxLadderStep)
            val at = alternativesAt(step)
            return at.firstOrNull { it.family == EngineFamily.PISTON }
                ?: at.firstOrNull { it == ROTARY }
                ?: at.first()
        }

        /** Primary (default) block at every ladder step, in order. */
        fun primarySteps(): List<Block> = (0..maxLadderStep).map { forPrestige(it) }

        /**
         * Fitted long-block: [blockId] when it names an alternative on this
         * prestige step, otherwise the step default. Blank/unknown id = default.
         * Old saves with no blockId keep the piston (or 13B) they had.
         */
        fun of(blockId: String, prestiges: Int): Block {
            val step = prestiges.coerceIn(0, maxLadderStep)
            if (blockId.isNotEmpty()) {
                val picked = entries.firstOrNull { it.name == blockId }
                if (picked != null && picked.ladderStep == step) return picked
            }
            return forPrestige(step)
        }
    }
}
