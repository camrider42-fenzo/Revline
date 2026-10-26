package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockLadderTest {
    @Test
    fun prestigeStepsUpInPowerAndChangesThePicture() {
        var previous = 0.0
        var previousPay = 0.0
        for (index in Block.entries.indices) {
            val state = GameState(prestiges = index)
            val sheet = Logic.sheet(state.copy(throttle = Logic.bestThrottle(state)))
            val block = Block.forPrestige(index)
            println("${block.title}  hp ${sheet.stats.peakHorsepower.toInt()}  $ ${"%.2f".format(sheet.incomePerSec)}/s  cyl ${sheet.params.cylinders} banks ${sheet.params.banks}")
            assertTrue(sheet.params.blockTitle, sheet.params.cylinders == block.cylinders)
            assertTrue(sheet.params.banks == block.banks)
            assertTrue("${block.title} hp ${sheet.stats.peakHorsepower} <= $previous", sheet.stats.peakHorsepower > previous * 1.15)
            assertTrue("${block.title} pay ${sheet.incomePerSec}", sheet.incomePerSec > previousPay * 1.05)
            previous = sheet.stats.peakHorsepower
            previousPay = sheet.incomePerSec
        }
    }
}
