package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.economy.Income
import com.revline.idle.economy.Offline
import com.revline.idle.economy.Prestige
import com.revline.idle.economy.formatCompact
import com.revline.idle.economy.formatDollars
import com.revline.idle.economy.formatDuration
import com.revline.idle.model.Coolant
import com.revline.idle.model.EngineParams
import com.revline.idle.model.EngineSim
import com.revline.idle.model.EngineStats
import com.revline.idle.model.FuelKind
import com.revline.idle.model.Induction
import com.revline.idle.model.Mixture
import kotlin.math.pow

data class Sheet(
    val params: EngineParams,
    val stats: EngineStats,
    val incomePerSec: Double,
    val rpPerSec: Double,
    val multiplier: Double,
)

data class StatDelta(
    val hp: Double,
    val torque: Double,
    val mpg: Double,
    val reliability: Double,
    val coolantC: Double,
    val oilC: Double,
    val egtC: Double,
)

enum class ResearchStatus { OWNED, LOCKED, OPEN }

object Logic {
    const val IDLE_RPM = 820.0
    const val REST_THROTTLE = 0.18

    /** Throttle stop to crank speed. 0 is idle, 1 is the redline this block can turn. */
    fun targetRpm(redline: Double, throttle: Double): Double {
        val span = (redline - IDLE_RPM).coerceAtLeast(1.0)
        return IDLE_RPM + throttle.coerceIn(0.0, 1.0) * span
    }

    fun incomeAt(params: EngineParams, rpm: Double, reliability: Double, multiplier: Double): Double =
        Income.perSecond(EngineSim.point(params, rpm), reliability, multiplier)

    /**
     * Throttle opening whose steady rpm pays the most on this build.
     * Sampled every 100 rpm. The pedal is how a person finds the same spot.
     */
    fun bestThrottle(state: GameState): Double {
        val params = paramsFrom(state)
        val stats = EngineSim.evaluate(params)
        val mult = Prestige.multiplier(state.knowledge)
        var bestRpm = IDLE_RPM
        var bestPay = -1.0
        var rpm = IDLE_RPM
        while (rpm <= params.redlineRpm + 0.5) {
            val at = rpm.coerceAtMost(params.redlineRpm)
            val pay = incomeAt(params, at, stats.reliability, mult)
            if (pay > bestPay) {
                bestPay = pay
                bestRpm = at
            }
            rpm += 100.0
        }
        return ((bestRpm - IDLE_RPM) / (params.redlineRpm - IDLE_RPM).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
    }

    fun paramsFrom(state: GameState): EngineParams {
        fun lvl(id: String) = state.upgrades[id] ?: 0
        val filter = lvl("filter")
        val cai = lvl("cold_air")
        val manifold = lvl("manifold")
        val itb = lvl("itb")
        val catback = lvl("catback")
        val headers = lvl("headers")
        val longTube = lvl("long_tube")
        val ceramic = lvl("ceramic")
        val jets = lvl("jets")
        val pump = lvl("fuel_pump")
        val tbi = lvl("tbi_unit")
        val injectors = lvl("injectors")
        val hpfp = lvl("hpfp")
        val plugs = lvl("plugs")
        val coils = lvl("coils")
        val baseTiming = lvl("base_timing")
        val mapped = lvl("mapped_timing")
        val knockBox = lvl("knock_box")
        val springs = lvl("springs")
        val cam = lvl("cam")
        val vvt = lvl("vvt")
        val vvl = lvl("vvl")
        val sc = lvl("supercharger")
        val turbo = lvl("turbo")
        val twin = lvl("twin")
        val ic = lvl("intercooler")
        val controller = lvl("boost_controller")
        val studs = lvl("studs")
        val pistons = lvl("pistons")
        val rods = lvl("rods")
        val crank = lvl("crank")
        val bore = lvl("bore")
        val compression = lvl("compression")
        val billet = lvl("billet")
        val radiator = lvl("radiator")
        val duct = lvl("duct")
        val waterPump = lvl("water_pump")
        val electric = lvl("electric_pump") > 0
        val oil = lvl("oil_cooler")
        val meth = lvl("meth")
        val thermostat = lvl("thermostat")
        val cap = lvl("cap")
        val afr = lvl("afr_map")
        val torque = lvl("torque_model") > 0
        val synthetic = lvl("synthetic") > 0
        val inconel = lvl("inconel") > 0
        val waterless = lvl("waterless") > 0

        val induction = when {
            twin > 0 -> Induction.TWIN_TURBO
            turbo > 0 -> Induction.TURBO
            sc > 0 -> Induction.SUPERCHARGER
            else -> Induction.NA
        }
        var boost = when (induction) {
            Induction.NA -> 0.0
            Induction.SUPERCHARGER -> 5.5 + (sc - 1) * 3.3
            Induction.TURBO -> 6.5 + (turbo - 1) * 3.2
            Induction.TWIN_TURBO -> 6.5 + (maxOf(turbo, 1) - 1) * 3.2 + twin * 2.4
        }
        val controllerPsi = controller * 1.3
        var spool = when (induction) {
            Induction.TWIN_TURBO -> 2500.0
            Induction.TURBO -> 3650.0
            else -> 3800.0
        }
        if (ceramic > 0 && (induction == Induction.TURBO || induction == Induction.TWIN_TURBO)) {
            spool -= 180.0
        }
        var bp = (1.0 - 0.045 * catback - 0.06 * headers - 0.05 * longTube).coerceIn(0.35, 1.15)
        when (induction) {
            Induction.TURBO -> bp += 0.16
            Induction.TWIN_TURBO -> bp += 0.10
            Induction.SUPERCHARGER -> bp += 0.04
            Induction.NA -> Unit
        }
        val mixture = when {
            hpfp > 0 -> Mixture.DIRECT
            injectors > 0 -> Mixture.PORT
            tbi > 0 -> Mixture.TBI
            else -> Mixture.CARB
        }
        val fuel = allowedFuel(state, state.fuel)
        var ve = 0.93 +
            0.012 * filter + 0.015 * cai + 0.012 * manifold + 0.018 * itb +
            0.006 * catback + 0.010 * headers + 0.012 * longTube +
            0.020 * cam + 0.012 * vvt + 0.030 * vvl +
            0.012 * (state.perks["flow"] ?: 0)
        var frac = 0.58 + 0.012 * manifold + 0.022 * itb + 0.012 * headers +
            0.018 * longTube + 0.040 * cam + 0.015 * vvl
        var width = 3100.0 + 80.0 * cai - 140.0 * itb - 220.0 * cam + 650.0 * vvt + 400.0 * vvl
        var strength = 1.0 + 0.035 * springs + 0.08 * studs + 0.18 * pistons +
            0.11 * rods + 0.08 * crank + 0.25 * billet + 0.06 * (state.perks["fixtures"] ?: 0)
        var flow = 1.25 + 0.12 * jets + 0.28 * pump + 0.22 * tbi + 0.42 * injectors + 0.70 * hpfp
        var timing = 16.0 + 1.15 * baseTiming + 1.30 * mapped + 0.40 * coils
        if (torque) timing += 1.4
        var knock = 0.18 + if (knockBox > 0) 0.60 else 0.0
        if (torque) knock += 0.10
        var ign = 0.95 + 0.012 * plugs + 0.015 * coils
        var trim = 0.10 * afr
        if (torque) trim += 0.12
        val pumpFlow = 1.0 + 0.07 * waterPump + if (electric) 0.08 else 0.0
        var ua = (760.0 + 260.0 * radiator) * (1.0 + 0.10 * duct) * (0.92 + 0.08 * pumpFlow)
        if (waterless) ua *= 0.84
        val stat = when (thermostat) {
            0 -> 88.0
            1 -> 82.0
            else -> 71.0
        }
        val capPsi = when (cap) {
            0 -> 13.0
            1 -> 16.0
            else -> 20.0
        }
        val friction = (1.0 - 0.04 * crank - if (synthetic) 0.025 else 0.0).coerceIn(0.78, 1.2)
        return EngineParams(
            boreMm = (86.0 + 2.0 * bore).coerceIn(80.0, 100.0),
            compressionRatio = (9.5 + 0.38 * compression).coerceIn(8.0, 14.5),
            peakVe = ve.coerceIn(0.55, 1.35),
            vePeakFraction = frac.coerceIn(0.35, 0.92),
            veWidthRpm = width.coerceIn(900.0, 6500.0),
            redlineRpm = (6500.0 + 230.0 * springs + if (vvl > 0) 150.0 else 0.0).coerceIn(4500.0, 9800.0),
            backpressure = bp.coerceIn(0.30, 1.40),
            timingDeg = timing.coerceIn(6.0, 38.0),
            knockControl = knock.coerceIn(0.05, 0.95),
            ignitionQuality = ign.coerceIn(0.70, 0.995),
            fuelFlowFactor = flow.coerceIn(0.4, 12.0),
            afrTrim = trim.coerceIn(0.0, 2.2),
            jetQuality = (jets / 4.0).coerceIn(0.0, 1.0),
            mixture = mixture,
            fuel = fuel,
            maxBoostPsi = boost.coerceIn(0.0, 40.0),
            boostControllerPsi = controllerPsi,
            induction = induction,
            spoolRpm = spool.coerceIn(1400.0, 6000.0),
            intercoolerEff = (if (ic <= 0) 0.0 else 0.30 + (ic - 1) * 0.15).coerceIn(0.0, 0.86),
            friction = friction,
            internalsStrength = strength.coerceIn(0.8, 3.2),
            radiatorUa = ua.coerceIn(80.0, 8000.0),
            oilCoolerUa = (110.0 * oil).coerceAtLeast(0.0),
            thermostatC = stat,
            capPsi = capPsi,
            coolant = if (waterless) Coolant.WATERLESS else Coolant.GLYCOL_50,
            syntheticOil = synthetic,
            electricPump = electric,
            methFraction = (0.45 * meth).coerceIn(0.0, 1.2),
            exhaustWrap = if (ceramic > 0) 1.0 else 0.0,
            inconelValves = inconel,
            octaneBonus = 1.2 * (state.perks["octane"] ?: 0),
            underhoodDeltaC = (12.0 - 2.4 * cai).coerceIn(1.5, 25.0),
            individualThrottles = itb > 0,
        )
    }

    fun sheet(state: GameState): Sheet {
        val params = paramsFrom(state)
        val stats = EngineSim.evaluate(params)
        val mult = Prestige.multiplier(state.knowledge)
        val notebook = state.perks["notebook"] ?: 0
        val rp = (0.22 + stats.peakHorsepower / 850.0) * (1.0 + 0.22 * notebook)
        val rpm = targetRpm(params.redlineRpm, state.throttle)
        val pay = incomeAt(params, rpm, stats.reliability, mult)
        return Sheet(params, stats, pay, rp, mult)
    }

    fun dynoReward(stats: EngineStats): Double = 8.0 + stats.peakHorsepower * 0.055

    fun accrue(state: GameState, dtSec: Double, nowMs: Long, incomePerSec: Double, rpPerSec: Double): GameState {
        if (dtSec <= 0.0) return state.copy(savedAtEpochMs = nowMs)
        val gained = incomePerSec.coerceAtLeast(0.0) * dtSec
        return state.copy(
            money = state.money + gained,
            runEarnings = state.runEarnings + gained,
            researchPoints = state.researchPoints + rpPerSec.coerceAtLeast(0.0) * dtSec,
            savedAtEpochMs = nowMs,
        )
    }

    fun applyOffline(state: GameState, nowMs: Long): Pair<GameState, String?> {
        if (state.savedAtEpochMs <= 0L) return state.copy(savedAtEpochMs = nowMs) to null
        val elapsed = (nowMs - state.savedAtEpochMs).coerceIn(0L, Offline.CAP_MS)
        if (elapsed < Offline.MIN_MS) return state.copy(savedAtEpochMs = nowMs) to null
        val sec = elapsed / 1000.0
        val s = sheet(state)
        val money = s.incomePerSec * sec * Offline.MONEY_FACTOR
        val rp = s.rpPerSec * sec * Offline.RESEARCH_FACTOR
        val next = state.copy(
            money = state.money + money,
            runEarnings = state.runEarnings + money,
            researchPoints = state.researchPoints + rp,
            savedAtEpochMs = nowMs,
        )
        val msg = "The contract kept paying with the bay dark. ${formatDollars(money)} and " +
            "${formatCompact(rp)} research, over ${formatDuration(elapsed)}. " +
            "Nobody was here to make a dyno pull, so research is only the passive half."
        return next to msg
    }

    fun costMultiplier(state: GameState): Double =
        (1.0 - 0.05 * (state.perks["tooling"] ?: 0)).coerceAtLeast(0.82)

    fun upgradeCost(state: GameState, id: String): Double {
        val u = Upgrades.byId(id)
        val level = state.upgrades[id] ?: 0
        return u.baseCost * u.growth.pow(level.toDouble()) * costMultiplier(state) * 0.5
    }

    fun lockReason(state: GameState, id: String): String? {
        val u = Upgrades.byId(id)
        val level = state.upgrades[id] ?: 0
        if (level >= u.maxLevel) return null
        val next = level + 1
        val nodes = buildList {
            u.requires?.let { add(it) }
            u.levelRequires[next]?.let { add(it) }
        }
        val missing = nodes.firstOrNull { it !in state.researched } ?: return null
        return ResearchTree.byId(missing).name
    }

    fun canBuyUpgrade(state: GameState, id: String): Boolean {
        val u = Upgrades.all.find { it.id == id } ?: return false
        val level = state.upgrades[id] ?: 0
        if (level >= u.maxLevel) return false
        if (lockReason(state, id) != null) return false
        return state.money >= upgradeCost(state, id)
    }

    fun buyUpgrade(state: GameState, id: String): GameState {
        if (!canBuyUpgrade(state, id)) return state
        val level = state.upgrades[id] ?: 0
        val cost = upgradeCost(state, id)
        return state.copy(
            money = state.money - cost,
            upgrades = state.upgrades + (id to level + 1),
        )
    }

    fun preview(state: GameState, id: String): StatDelta? {
        val u = Upgrades.all.find { it.id == id } ?: return null
        val level = state.upgrades[id] ?: 0
        if (level >= u.maxLevel) return null
        val now = sheet(state).stats
        val next = sheet(state.copy(upgrades = state.upgrades + (id to level + 1))).stats
        return StatDelta(
            hp = next.peakHorsepower - now.peakHorsepower,
            torque = next.peakTorqueLbFt - now.peakTorqueLbFt,
            mpg = next.cruiseMpg - now.cruiseMpg,
            reliability = next.reliability - now.reliability,
            coolantC = next.peakCoolantC - now.peakCoolantC,
            oilC = next.peakOilC - now.peakOilC,
            egtC = next.peakEgtC - now.peakEgtC,
        )
    }

    fun researchStatus(state: GameState, id: String): ResearchStatus {
        val node = ResearchTree.byId(id)
        if (id in state.researched) return ResearchStatus.OWNED
        if (node.requires.any { it !in state.researched }) return ResearchStatus.LOCKED
        return ResearchStatus.OPEN
    }

    fun canBuyResearch(state: GameState, id: String): Boolean {
        if (researchStatus(state, id) != ResearchStatus.OPEN) return false
        return state.researchPoints >= ResearchTree.byId(id).cost
    }

    fun buyResearch(state: GameState, id: String): GameState {
        if (!canBuyResearch(state, id)) return state
        val cost = ResearchTree.byId(id).cost
        return state.copy(
            researchPoints = state.researchPoints - cost,
            researched = state.researched + id,
        )
    }

    fun spentKnowledge(state: GameState): Int {
        var spent = 0
        for (perk in Perks.all) {
            val rank = state.perks[perk.id] ?: 0
            for (i in 0 until rank.coerceAtMost(perk.costs.size)) spent += perk.costs[i]
        }
        return spent
    }

    fun knowledgeFree(state: GameState): Int = state.knowledge - spentKnowledge(state)

    fun canBuyPerk(state: GameState, id: String): Boolean {
        val perk = Perks.all.find { it.id == id } ?: return false
        val rank = state.perks[id] ?: 0
        if (rank >= perk.costs.size) return false
        return knowledgeFree(state) >= perk.costs[rank]
    }

    fun buyPerk(state: GameState, id: String): GameState {
        if (!canBuyPerk(state, id)) return state
        val rank = state.perks[id] ?: 0
        return state.copy(perks = state.perks + (id to rank + 1))
    }

    fun canPrestige(state: GameState): Boolean = Prestige.knowledgeGain(state.runEarnings) > 0

    fun prestige(state: GameState): GameState {
        val gain = Prestige.knowledgeGain(state.runEarnings)
        if (gain <= 0) return state
        return state.copy(
            money = 0.0,
            runEarnings = 0.0,
            researchPoints = state.researchPoints * 0.20,
            knowledge = state.knowledge + gain,
            prestiges = state.prestiges + 1,
            upgrades = emptyMap(),
            fuel = FuelKind.PUMP_87,
            throttle = REST_THROTTLE,
        )
    }

    fun fuelUnlocked(state: GameState, fuel: FuelKind): Boolean = when (fuel) {
        FuelKind.PUMP_87 -> true
        FuelKind.PREMIUM_93 -> "premium" in state.researched
        FuelKind.E85 -> "e85" in state.researched
        FuelKind.RACE_100 -> "race" in state.researched
    }

    fun selectFuel(state: GameState, fuel: FuelKind): GameState {
        if (!fuelUnlocked(state, fuel)) return state
        return state.copy(fuel = fuel)
    }

    fun allowedFuel(state: GameState, fuel: FuelKind): FuelKind =
        if (fuelUnlocked(state, fuel)) fuel else FuelKind.PUMP_87

    fun engineTitle(params: EngineParams): String {
        val liters = EngineSim.displacementCc(params) / 1000.0
        val ind = when (params.induction) {
            Induction.NA -> "naturally aspirated"
            Induction.SUPERCHARGER -> "supercharged"
            Induction.TURBO -> "turbo"
            Induction.TWIN_TURBO -> "twin-turbo"
        }
        return String.format(java.util.Locale.US, "%.2fL inline-four, %s", liters, ind)
    }
}
