package com.revline.idle.economy

import org.junit.Test

class PlaythroughReportTest {
    @Test
    fun report() {
        val n = (System.getenv("PLAY_SEEDS") ?: "0").toInt()
        if (n == 0) return
        val r = (1..n).map { PlaythroughHarness.play(it, (System.getenv("PLAY_H") ?: "300").toDouble()) }
        java.io.File("/tmp/play.txt").writeText(PlaythroughHarness.report(r))
    }
}
