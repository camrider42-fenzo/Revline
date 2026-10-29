package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.Racing
import com.revline.idle.model.Block
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plays two shops. One builds for the contract. One builds a car.
 * The contract car should earn more per second. The car should be faster
 * and should have a stake it can win.
 */
class RacePlayTest {
    @Test
    fun aProfitEngineEarnsAndARacerWins() {
        val four = Block.INLINE_FOUR.ordinal
        val research = setOf("exhaust_fab", "perf_cam", "forged", "hyper", "premium", "wasted_spark", "cop", "piggyback")
        val profit = GameState(
            prestiges = four,
            knowledge = 4,
            money = 50_000.0,
            researched = research,
            upgrades = mapOf(
                "cold_air" to 4, "afr_map" to 4, "jets" to 4, "needle" to 3,
                "catback" to 4, "boot" to 4, "tip" to 3, "filter" to 4,
                "headers" to 1,
            ),
            chassis = "gta",
            chassisLevel = mapOf("gta" to 1),
            tire = "radial",
            transmission = "four",
            driveline = "lsd",
            coolingSlots = 2,
        )
        val racerBase = GameState(
            prestiges = four,
            knowledge = 4,
            money = 50_000.0,
            racing = true,
            researched = research,
            upgrades = mapOf(
                "cam" to 4, "headers" to 4, "long_tube" to 2, "compression" to 4,
                "springs" to 4, "cold_air" to 3, "filter" to 4, "coils" to 2,
                "base_timing" to 3, "rev_kit" to 2, "catback" to 2,
            ),
            transmission = "dog",
            driveline = "irs",
            tire = "slick",
            raceStudies = Racing.studies.map { it.id }.toSet(),
        )
        val lines = mutableListOf<String>()
        var best = racerBase
        var bestPace = 0.0
        for (body in Racing.chassis.filter { it.minPrestige <= four }) {
            val trial = racerBase.copy(
                chassis = body.id,
                chassisLevel = mapOf(body.id to 3),
                coolingSlots = Racing.slotMax(body, 3),
                driveType = "",
                enginePlace = "",
            )
            val pace = Racing.car(trial).pace
            val built = Racing.car(trial)
            lines += "racer ${body.id} pace ${pace.toInt()} fade ${(built.fade * 100).toInt()}% ${built.massKg.toInt()} kg wheel ${built.wheelHp.toInt()}"
            if (pace > bestPace) {
                bestPace = pace
                best = trial
            }
        }
        val profitPay = Logic.sheet(profit).incomePerSec
        val racerPay = Logic.sheet(best).incomePerSec
        val profitPace = Racing.car(profit).pace
        val racerPace = Racing.car(best).pace
        lines += "profit ${"%.3f".format(profitPay)}/s pace ${profitPace.toInt()}"
        lines += "racer ${best.chassis} ${"%.3f".format(racerPay)}/s pace ${racerPace.toInt()}"
        val club = Racing.quick(best, Racing.Tier.CLUB)
        val national = Racing.quick(best, Racing.Tier.NATIONAL)
        val intl = Racing.quick(best, Racing.Tier.INTERNATIONAL)
        lines += club.second
        lines += national.second
        lines += intl.second
        val profitClub = Racing.quick(profit, Racing.Tier.CLUB).second
        val profitIntl = Racing.quick(profit, Racing.Tier.INTERNATIONAL).second
        lines += "profit $profitClub"
        lines += "profit $profitIntl"
        var rung = best
        var cleared = 0
        for (step in Racing.ladderRungs().indices) {
            val before = rung.raceLadder
            val ran = Racing.ladder(rung)
            lines += ran.second
            rung = ran.first
            if (rung.raceLadder > before) cleared++ else break
        }
        val bank = 100_000_000.0
        var wins = 0
        repeat(80) { seed ->
            val ran = Racing.stake(best.copy(money = bank), seed.toLong())
            if (ran.first.money > bank) wins++
        }
        var muleWins = 0
        val mule = GameState(prestiges = four, money = bank)
        repeat(80) { seed ->
            val ran = Racing.stake(mule, seed.toLong())
            if (ran.first.money > bank) muleWins++
        }
        lines += "racer stake wins $wins/80  mule $muleWins/80  ladder rungs $cleared"
        println(lines.joinToString("\n"))
        assertTrue(lines.joinToString("\n"), profitPay > racerPay)
        assertTrue(lines.joinToString("\n"), racerPace > profitPace * 1.15)
        assertTrue(lines.joinToString("\n"), club.first.money > best.money)
        assertTrue(lines.joinToString("\n"), national.first.money > best.money)
        assertTrue(lines.joinToString("\n"), intl.first.money > best.money)
        assertTrue(lines.joinToString("\n"), profitClub.contains("Won"))
        assertTrue(lines.joinToString("\n"), profitIntl.contains("Lost"))
        assertTrue(lines.joinToString("\n"), cleared in 8..14)
        assertTrue(lines.joinToString("\n"), wins in 48..72)
        assertTrue(lines.joinToString("\n"), muleWins <= 20)
        assertTrue(lines.joinToString("\n"), wins > muleWins + 20)
    }
}
