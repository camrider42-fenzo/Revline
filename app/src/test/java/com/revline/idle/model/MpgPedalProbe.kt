package com.revline.idle.model

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import org.junit.Test
import java.io.File

/** Writes the profit-pedal mpg per rebuild for several builds, against the goal. Not an assertion test. */
class MpgPedalProbe {
    @Test fun write() {
        val research = ResearchTree.all.map { it.id }.toSet()
        val maxed = Upgrades.all.associate { it.id to it.maxLevel }
        val eco = Logic.ECONOMY_IDS
        val all11 = Logic.ECONOMY_IDS + Logic.NEW_ECONOMY_IDS
        val out = StringBuilder()
        out.appendLine("rebuild engine            goal  stock  forty  sixty  ecoOnly  eco+new ecoHalf  maxed  cap | (sixty ecoHalf=half levels of eco parts on top of nothing else)")
        for (i in Block.entries.indices) {
            val b = Block.forPrestige(i)
            fun mpg(s: GameState) = Logic.sheet(s).profit.mpg
            val stock = mpg(GameState(prestiges = i))
            val forty = mpg(GameState(prestiges = i, knowledge = i, researched = research, upgrades = BlockPaceTest.forty))
            val sixty = mpg(GameState(prestiges = i, knowledge = i, researched = research, upgrades = BlockPaceTest.sixty))
            val ecoOnly = mpg(GameState(prestiges = i, researched = research, upgrades = eco.associateWith { Upgrades.byId(it).maxLevel }))
            val ecoNew = mpg(GameState(prestiges = i, researched = research, upgrades = all11.associateWith { Upgrades.byId(it).maxLevel }))
            val ecoHalf = mpg(GameState(prestiges = i, researched = research, upgrades = eco.associateWith { (Upgrades.byId(it).maxLevel + 1) / 2 }))
            val full = mpg(GameState(prestiges = i, knowledge = i, researched = research, upgrades = maxed))
            out.appendLine("%d %-22s %6.1f %6.1f %6.1f %6.1f %7.1f %7.1f %7.1f %6.1f %5.1f".format(i, b.title, b.mpgGoal, stock, forty, sixty, ecoOnly, ecoNew, ecoHalf, full, b.mpgCap()))
        }
        File("build/mpg-pedal.txt").apply { parentFile.mkdirs() }.writeText(out.toString())
    }
}
