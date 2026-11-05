package com.revline.idle.economy

import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.game.Racing
import com.revline.idle.game.ResearchTree
import com.revline.idle.game.Upgrades
import com.revline.idle.model.Block
import com.revline.idle.model.FuelKind
import java.util.Locale
import kotlin.random.Random

/**
 * Headless playthrough: a greedy player (best income gain per dollar, with a little noise per seed),
 * dyno pulls on a timer, quick races and the ladder whenever they pay, rebuilds when allowed.
 * Time is accelerated by stepping straight to the next affordable buy.
 */
object PlaythroughHarness {
    var stuckNote = ""
    class PartStat { var bought = 0; var valueSum = 0.0; var spent = 0.0; var seen = 0; var dead = 0 }

    class Result(
        val seed: Int, val finished: Boolean, val hours: Double, val rebuildHours: List<Double>,
        val longestWaitH: Double, val raceTries: Int, val raceWins: Int, val tierWins: IntArray, val tierTries: IntArray,
        val parts: Map<String, PartStat>, val gainPerDollarRatio: Double,
    )

    fun tune(state: GameState): GameState {
        val fuel = FuelKind.entries.filter { Logic.fuelUnlocked(state, it) }.maxBy { k ->
            val t = state.copy(fuel = k); Logic.sheet(t.copy(throttle = Logic.bestThrottle(t))).incomePerSec
        }
        val s = state.copy(fuel = fuel)
        return s.copy(throttle = Logic.bestThrottle(s))
    }

    fun play(seed: Int, horizonH: Double = 300.0): Result {
        val rnd = Random(seed)
        val noise = 0.15 + 0.1 * rnd.nextDouble()
        val dynoEvery = 25.0 + 30.0 * rnd.nextDouble()
        val parts = HashMap<String, PartStat>()
        var state = tune(GameState(savedAtEpochMs = 1L))
        var sheet = Logic.sheet(state)
        var t = 0.0; var sinceDyno = 0.0; var sinceBuy = 0.0; var longest = 0.0
        val rebuilds = ArrayList<Double>()
        var tries = 0; var wins = 0
        val tw = IntArray(3); val tt = IntArray(3)
        var early = 0.0; var late = 0.0; var nEarly = 0; var nLate = 0
        var guard = 0
        var raceWin = false
        while (t < horizonH * 3600 && guard++ < 60_000) {
            val done = state.researched.size >= ResearchTree.all.size && Upgrades.all.all { (state.upgrades[it.id] ?: 0) >= it.maxLevel }
            if (done) break
            val research = ResearchTree.all.filter { Logic.canBuyResearch(state, it.id) }.minByOrNull { it.cost }
            if (research != null) { state = tune(Logic.buyResearch(state, research.id)); sheet = Logic.sheet(state); continue }
            val ready = FuelKind.entries.filter { Logic.fuelUnlocked(state, it) }.map { state.copy(fuel = it) }.firstOrNull { Logic.canPrestige(it) }
            if (state.prestiges < Block.V8.ordinal && ready != null) {
                state = tune(Logic.prestige(ready)); sheet = Logic.sheet(state); rebuilds += t / 3600; continue
            }
            // value of each affordable-or-not part: income gain per dollar
            val base = sheet.incomePerSec
            val options = Upgrades.all.filter { Logic.lockReason(state, it.id) == null && (state.upgrades[it.id] ?: 0) < it.maxLevel }
                .map { u ->
                    val cost = Logic.upgradeCost(state, u.id)
                    val next = state.copy(upgrades = state.upgrades + (u.id to (state.upgrades[u.id] ?: 0) + 1))
                    val ns = Logic.sheet(next.copy(throttle = Logic.bestThrottle(next)))
                    val gain = ns.incomePerSec - base
                    val gate = Block.forPrestige(state.prestiges).sheetMpg(state.racing)
                    val below = state.prestiges < Block.V8.ordinal && sheet.profit.mpg < gate
                    val breaks = state.prestiges < Block.V8.ordinal && sheet.profit.mpg >= gate && ns.profit.mpg < gate
                    val mpgGain = (ns.profit.mpg - sheet.profit.mpg).coerceAtLeast(0.0)
                    // Below the rebuild mpg gate, value the parts that clear it; otherwise income per dollar.
                    val score = when {
                        breaks -> -1.0
                        below && mpgGain > 1e-6 -> mpgGain / cost.coerceAtLeast(1e-6) * 1000.0 + gain / cost.coerceAtLeast(1e-6)
                        else -> gain / cost.coerceAtLeast(1e-6)
                    }
                    Triple(u.id, cost, score)
                }.let { all -> if (all.any { it.third > -1.0 }) all.filter { it.third > -1.0 } else all }
            if (options.isNotEmpty()) {
                val ranked = options.sortedByDescending { it.third }
                ranked.forEachIndexed { i, o -> parts.getOrPut(o.first) { PartStat() }.apply { seen++; valueSum += if (ranked.size > 1) i.toDouble() / (ranked.size - 1) else 0.0; if (o.third <= 0) dead++ } }
                // patience: wait for the best value unless it's > 3x the cheapest affordable wait
                val pick = options.maxBy { it.third * (1 + noise * (rnd.nextDouble() - 0.5)) - if (it.second > state.money + base * 600) 1e9 else 0.0 }
                if (state.money >= pick.second) {
                    val ps = parts.getOrPut(pick.first) { PartStat() }
                    ps.bought++; ps.spent += pick.second
                    if (t < 3600 * 5) { early += pick.third; nEarly++ } else { late += pick.third; nLate++ }
                    state = tune(Logic.buyUpgrade(state, pick.first)); sheet = Logic.sheet(state)
                    longest = maxOf(longest, sinceBuy); sinceBuy = 0.0
                    raceWin = false
                    for ((i, tier) in Racing.Tier.entries.withIndex()) {
                        val (after, _) = Racing.quick(state, tier); tt[i]++; tries++
                        if (after.money > state.money) { tw[i]++; wins++; raceWin = true }
                    }
                    val (lad, _) = Racing.ladder(state)
                    if (lad.raceLadder != state.raceLadder) state = lad
                    continue
                }
            }
            val gaps = options.map { (it.second - state.money) / sheet.incomePerSec.coerceAtLeast(1e-9) } +
                ResearchTree.all.filter { Logic.researchStatus(state, it.id) == com.revline.idle.game.ResearchStatus.OPEN }
                    .map { (it.cost - state.researchPoints) / sheet.rpPerSec.coerceAtLeast(1e-9) }
            val wait = (gaps.filter { it > 0 }.minOrNull() ?: 30.0).coerceIn(1.0, 60.0)
            state = state.copy(money = state.money + sheet.incomePerSec * wait * (if (raceWin) 2 else 1), runEarnings = state.runEarnings + sheet.incomePerSec * wait,
                researchPoints = state.researchPoints + sheet.rpPerSec * wait)
            sinceDyno += wait; sinceBuy += wait; t += wait
            if (sinceDyno >= dynoEvery) { state = state.copy(researchPoints = state.researchPoints + Logic.dynoReward(sheet.rpPerSec, sheet.params.dynoFactor)); sinceDyno = 0.0 }
        }
        val done = state.researched.size >= ResearchTree.all.size && Upgrades.all.all { (state.upgrades[it.id] ?: 0) >= it.maxLevel }
        if (!done) stuckNote = "seed $seed hp ${sheet.stats.peakHorsepower} tq ${sheet.stats.peakTorqueLbFt} mpg ${sheet.profit.mpg} thr ${state.throttle} fuel ${state.fuel} money ${state.money.toInt()} inc ${sheet.incomePerSec} rp ${state.researchPoints.toInt()} pres ${state.prestiges} missing " +
            Upgrades.all.filter { (state.upgrades[it.id] ?: 0) < it.maxLevel }.map { "${it.id}:${Logic.lockReason(state, it.id)}:${Logic.upgradeCost(state, it.id).toInt()}" } +
            ResearchTree.all.filter { it.id !in state.researched }.map { it.id }
        val ratio = if (nEarly > 0 && nLate > 0) (late / nLate) / (early / nEarly) else 0.0
        return Result(seed, done, t / 3600, rebuilds, longest / 3600, tries, wins, tw, tt, parts, ratio)
    }

    fun report(results: List<Result>): String {
        val sb = StringBuilder()
        fun f(x: Double) = String.format(Locale.US, "%.2f", x)
        sb.append("runs ${results.size} finished ${results.count { it.finished }}\n")
        sb.append("hours mean ${f(results.map { it.hours }.average())} min ${f(results.minOf { it.hours })} max ${f(results.maxOf { it.hours })}\n")
        sb.append("longest wait h mean ${f(results.map { it.longestWaitH }.average())} max ${f(results.maxOf { it.longestWaitH })}\n")
        val nr = results.maxOf { it.rebuildHours.size }
        for (i in 0 until nr) sb.append("rebuild ${i + 1} at h ${f(results.mapNotNull { it.rebuildHours.getOrNull(i) }.average())}\n")
        for (i in 0..2) sb.append("tier $i win ${f(results.sumOf { it.tierWins[i] }.toDouble() / results.sumOf { it.tierTries[i] }.coerceAtLeast(1))}\n")
        val all = HashMap<String, PartStat>()
        results.forEach { r -> r.parts.forEach { (k, v) -> all.getOrPut(k) { PartStat() }.apply { bought += v.bought; valueSum += v.valueSum; spent += v.spent; seen += v.seen; dead += v.dead } } }
        sb.append(stuckNote + "\npart: avg rank pct among options (0 best), share no-gain, bought, spent share\n")
        val tot = all.values.sumOf { it.spent }
        for (u in Upgrades.all) { val s = all[u.id]; sb.append("  ${u.id} ${s?.let { f(it.valueSum / it.seen.coerceAtLeast(1)) + " " + f(it.dead.toDouble() / it.seen.coerceAtLeast(1)) } ?: "unseen"} ${s?.bought ?: 0} ${f((s?.spent ?: 0.0) / tot * 100)}\n") }
        return sb.toString()
    }
}
