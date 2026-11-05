package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.Upgrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineFamilyTest {
    @Test
    fun oneAllowsRuleHidesNonsensicalParts() {
        for (id in EngineFamily.PISTON_ONLY) {
            assertTrue(id, EngineFamily.PISTON.allows(id))
            assertFalse(id, EngineFamily.ROTARY.allows(id))
        }
        for (id in EngineFamily.ROTARY_ONLY) {
            assertTrue(id, EngineFamily.ROTARY.allows(id))
            assertFalse(id, EngineFamily.PISTON.allows(id))
        }
        assertTrue(EngineFamily.PISTON.allows("turbo"))
        assertTrue(EngineFamily.ROTARY.allows("turbo"))
    }

    @Test
    fun shopListsFollowTheFamilyFilter() {
        val piston = Upgrades.forFamily(EngineFamily.PISTON).map { it.id }.toSet()
        val rotary = Upgrades.forFamily(EngineFamily.ROTARY).map { it.id }.toSet()
        assertTrue("cam" in piston && "cam" !in rotary)
        assertTrue("apex_seals" in rotary && "apex_seals" !in piston)
        assertTrue(Upgrades.all.all { EngineFamily.PISTON.allows(it.id) || EngineFamily.ROTARY.allows(it.id) })
        assertEquals(piston.intersect(rotary), piston.intersect(rotary)) // shared set is well-defined
        assertTrue(piston.intersect(EngineFamily.ROTARY_ONLY).isEmpty())
        assertTrue(rotary.intersect(EngineFamily.PISTON_ONLY).isEmpty())
    }

    @Test
    fun rotaryParamsIgnorePistonHardwareAndUsePorts() {
        val base = GameState(prestiges = Block.ROTARY.ladderStep, blockId = Block.ROTARY.name, upgrades = mapOf("cam" to 4, "springs" to 5, "pistons" to 3))
        val ported = base.copy(upgrades = mapOf("port_bridge" to 2, "apex_seals" to 3, "eccentric" to 2))
        val stockHp = Logic.sheet(GameState(prestiges = Block.ROTARY.ladderStep, blockId = Block.ROTARY.name)).stats.peakHorsepower
        val baseSheet = Logic.sheet(base)
        val portedSheet = Logic.sheet(ported)
        assertEquals(EngineFamily.ROTARY, baseSheet.params.family)
        assertEquals(stockHp, baseSheet.stats.peakHorsepower, 0.05)
        assertTrue(portedSheet.stats.peakHorsepower > baseSheet.stats.peakHorsepower + 5.0)
        assertTrue(Logic.paramsFrom(ported).redlineRpm > Logic.paramsFrom(base).redlineRpm)
    }

    @Test
    fun rotaryRebuildGoalsPreferRpmPeakOverTorqueClimb() {
        assertTrue(Block.ROTARY.sheetHp(true) > Block.ROTARY.sheetHp(false) * 1.15)
        assertTrue(Block.ROTARY.redlineRpm >= 8500.0)
        assertTrue(Block.ROTARY.redlineCap() > Block.V12.redlineCap())
        assertTrue(Block.ROTARY.mpgGoal > Block.V6.mpgGoal)
        assertTrue(Block.ROTARY.mpgGoal < Block.INLINE_FOUR.mpgGoal)
    }

    @Test
    fun oldSavesKeepPistonFamilyAndCannotBuyRotaryParts() {
        val s = GameState(prestiges = Block.V8.ordinal, upgrades = mapOf("cam" to 2, "turbo" to 1), money = 1e9)
        assertEquals(EngineFamily.PISTON, Logic.paramsFrom(s).family)
        assertFalse(Logic.canBuyUpgrade(s, "apex_seals"))
        assertFalse(Logic.canBuyUpgrade(s, "rotor3"))
    }
}
