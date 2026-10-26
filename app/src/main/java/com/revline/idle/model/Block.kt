package com.revline.idle.model

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
    val goalEarnings: Double,
) {
    MOPED("50cc moped single", 1, 1, 39.0, 41.4, 8.2, 9000.0, 1700.0, 0.78, 0.70, 140.0, 14.0, 0.45, true, 1_100.0),
    THUMPER("250cc thumper", 1, 1, 74.0, 58.0, 9.0, 8500.0, 1300.0, 0.86, 0.64, 280.0, 16.0, 0.70, true, 11_000.0),
    TWIN("500cc parallel twin", 2, 1, 72.0, 61.5, 10.2, 10500.0, 1200.0, 0.90, 0.62, 420.0, 18.0, 0.90, false, 22_000.0),
    TRIPLE("1.0L inline-three", 3, 1, 76.0, 73.5, 10.8, 7800.0, 950.0, 0.91, 0.58, 560.0, 18.0, 1.05, false, 40_000.0),
    INLINE_FOUR("2.0L inline-four", 4, 1, 86.0, 86.0, 9.5, 6500.0, 820.0, 0.93, 0.58, 760.0, 16.0, 1.25, false, 95_000.0),
    V6("3.5L V6", 6, 2, 94.0, 83.0, 10.2, 7000.0, 750.0, 0.98, 0.56, 2000.0, 18.0, 2.05, false, 180_000.0),
    V8("5.0L V8", 8, 2, 93.0, 92.0, 10.2, 7000.0, 700.0, 1.02, 0.56, 2800.0, 20.0, 3.10, false, 420_000.0),
    V12("6.5L V12", 12, 2, 89.0, 87.0, 10.6, 8000.0, 900.0, 1.06, 0.62, 3800.0, 22.0, 4.20, false, 800_000.0),
    ;

    /** Bigger jobs pay more than the raw fuel bill would allow. The ladder still has to climb. */
    fun payScale(): Double = when (this) {
        V6 -> 1.65
        V8 -> 2.30
        V12 -> 4.40
        else -> 1.0
    }

    companion object {
        fun forPrestige(prestiges: Int): Block = entries[prestiges.coerceIn(0, entries.lastIndex)]
    }
}
