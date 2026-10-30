package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockLadderTest {
    @Test
    fun redlinesClimbAndTheV12StopsAtTenThousand() {
        val stocks = Block.entries.map { it.redlineRpm }
        val caps = Block.entries.map { it.redlineCap() }
        for (i in 1 until caps.size) {
            assertTrue("${Block.entries[i].title} cap ${caps[i]}", caps[i] > caps[i - 1])
        }
        assertTrue(stocks[1] > stocks[0] && stocks[2] > stocks[1])
        assertTrue(Block.MOPED.redlineRpm < 7500.0)
        assertTrue(Block.V12.redlineRpm > Block.V8.redlineRpm)
        assertEquals(10000.0, Block.V12.redlineCap(), 0.0)
        val v12 = GameState(
            prestiges = Block.V12.ordinal,
            upgrades = mapOf("rev_kit" to 4, "springs" to 5, "vvl" to 1, "limiter" to 3),
        )
        assertEquals(10000.0, Logic.paramsFrom(v12).redlineRpm, 1.0)
        val moped = GameState(upgrades = mapOf("rev_kit" to 4, "springs" to 5, "limiter" to 3))
        assertTrue(Logic.paramsFrom(moped).redlineRpm <= Block.MOPED.redlineCap() + 0.1)
        assertTrue(Logic.paramsFrom(GameState(upgrades = mapOf("rev_kit" to 2))).redlineRpm > Block.MOPED.redlineRpm)
    }

    @Test
    fun aRaceRebuildRaisesPowerTargetsAndCutsMpg() {
        for (block in Block.entries) {
            assertTrue("${block.title} hp", block.sheetHp(true) > block.sheetHp(false) * 1.3)
            assertTrue("${block.title} tq", block.sheetTorque(true) > block.sheetTorque(false) * 1.1)
            assertTrue("${block.title} mpg", block.sheetMpg(true) < block.sheetMpg(false) * 0.75)
        }
        val research = com.revline.idle.game.ResearchTree.all.map { it.id }.toSet()
        val maxed = com.revline.idle.game.Upgrades.all.associate { it.id to it.maxLevel }
        val misses = mutableListOf<String>()
        for (index in Block.entries.indices) {
            val block = Block.forPrestige(index)
            val stockSheet = Logic.sheet(GameState(prestiges = index, racing = true))
            val stock = stockSheet.stats
            if (com.revline.idle.economy.Prestige.meets(stock.peakHorsepower, stock.peakTorqueLbFt, stockSheet.profit.mpg, block, true)) {
                misses += "${block.title} stock race already clears"
            }
            val builtSheet = Logic.sheet(GameState(prestiges = index, racing = true, researched = research, upgrades = maxed))
            val built = builtSheet.stats
            if (!com.revline.idle.economy.Prestige.meets(built.peakHorsepower, built.peakTorqueLbFt, builtSheet.profit.mpg, block, true)) {
                misses += "${block.title} maxed race ${built.peakHorsepower.toInt()}/${built.peakTorqueLbFt.toInt()}/${"%.1f".format(builtSheet.profit.mpg)} vs ${block.sheetHp(true).toInt()}/${block.sheetTorque(true).toInt()}/${"%.1f".format(block.sheetMpg(true))}"
            }
            assertTrue(Logic.paramsFrom(GameState(prestiges = index, racing = true)).redlineRpm > block.redlineRpm)
            assertTrue(Logic.paramsFrom(GameState(prestiges = index, racing = true)).blockTitle.startsWith("Race "))
        }
        val ready = GameState(
            money = 1e9,
            researched = research,
            upgrades = maxed,
        )
        val street = Logic.prestige(ready, false)
        val race = Logic.prestige(ready, true)
        assertEquals(1, street.prestiges)
        assertEquals(false, street.racing)
        assertEquals(1, race.prestiges)
        assertEquals(true, race.racing)
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

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
