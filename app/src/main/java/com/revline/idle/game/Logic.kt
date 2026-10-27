package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.economy.ContractOutput
import com.revline.idle.economy.Income
import com.revline.idle.economy.Offline
import com.revline.idle.economy.Prestige
import com.revline.idle.economy.formatCompact
import com.revline.idle.economy.formatDollars
import com.revline.idle.economy.formatDuration
import com.revline.idle.model.Block
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
    /** Change at the pedal the engine is holding right now. */
    val hp: Double,
    val torque: Double,
    val mpg: Double,
    val reliability: Double,
    val coolantC: Double,
    val oilC: Double,
    val egtC: Double,
    /** Change on the wide-open pull, which is what the rebuild sheet uses. */
    val peakHp: Double,
    val peakTorque: Double,
    val cruiseMpg: Double,
    val payPerSec: Double,
)

enum class ResearchStatus { OWNED, LOCKED, OPEN }

object Logic {
    const val IDLE_RPM = 820.0
    const val REST_THROTTLE = 0.18

    /** Throttle stop to crank speed. 0 is a stall. Anything open is idle up to redline. */
    fun targetRpm(redline: Double, throttle: Double, idle: Double = IDLE_RPM): Double {
        val t = throttle.coerceIn(0.0, 1.0)
        if (t <= 0.0) return 0.0
        val floor = idle.coerceIn(500.0, redline - 300.0)
        val span = (redline - floor).coerceAtLeast(1.0)
        return floor + t * span
    }

    fun stalled(throttle: Double): Boolean = throttle <= 0.0

    /** Head temperature. It climbs at [per10s] and will not go past 80°C of soak. */
    fun stepSoak(soakC: Double, per10s: Double, dtSec: Double): Double =
        (soakC + per10s * (dtSec / 10.0)).coerceIn(0.0, 80.0)

    /** Throttle plate. The last part of the pedal opens the engine into boost and a rich mixture. */
    fun loadFor(throttle: Double): Double {
        val t = throttle.coerceIn(0.0, 1.0)
        return (0.16 + 0.84 * t.pow(1.32)).coerceIn(0.16, 1.0)
    }

    fun incomeAt(
        params: EngineParams,
        rpm: Double,
        reliability: Double,
        multiplier: Double,
        throttle: Double,
        stats: EngineStats,
        accumulatedSoakC: Double? = null,
    ): Double {
        if (stalled(throttle) || rpm < 200.0) return 0.0
        val point = EngineSim.point(params, rpm, loadFor(throttle), accumulatedSoakC)
        val output = ContractOutput(stats.peakHorsepower, stats.peakTorqueLbFt, stats.cruiseMpg)
        return Income.perSecond(point, reliability, multiplier, params.fuel.dollarsPerGallon, output)
    }

    /**
     * Pedal opening that pays the most once the crank has caught the stop.
     * Sampled every 5%. Flooring it is not assumed to be the answer.
     */
    fun bestThrottle(state: GameState): Double {
        val params = paramsFrom(state)
        val stats = EngineSim.evaluate(params)
        val mult = Prestige.multiplier(state.knowledge)
        var bestT = 0.05
        var bestPay = -1.0
        var t = 0.05
        while (t <= 1.001) {
            val rpm = targetRpm(params.redlineRpm, t, params.idleRpm)
            val pay = incomeAt(params, rpm, stats.reliability, mult, t, stats)
            if (pay > bestPay) {
                bestPay = pay
                bestT = t
            }
            t += 0.05
        }
        return bestT.coerceIn(0.0, 1.0)
    }

    /**
     * Parts that do not move a cool, unboosted little engine through the physics
     * still have to move the sheet, or buying them looks like a no-op.
     * The bonus is a real fraction of a horsepower on a 50 cc single and fades
     * on the bigger blocks, where the same part already works through the model.
     */
    private fun installedHp(block: Block, state: GameState): Double {
        fun lvl(id: String) = state.upgrades[id] ?: 0
        val latent = listOf(
            "jets", "fuel_pump", "studs", "cap", "radiator", "duct", "water_pump",
            "thermostat", "afr_map", "oil_cooler", "electric_pump", "inconel",
            "waterless", "ceramic", "knock_box", "boost_controller", "torque_model", "meth",
        ).sumOf { lvl(it) }
        val tools = listOf(
            "lap", "gap", "boot", "tip", "needle", "oil_service", "fins", "wrench",
        ).sumOf { lvl(it) }
        val cc = EngineSim.displacementCc(block.boreMm, block.strokeMm, block.cylinders).coerceAtLeast(40.0)
        val fade = (160.0 / cc).coerceIn(1.0, 1.8)
        return (latent * 0.45 + tools * 1.15) * fade
    }

    fun paramsFrom(state: GameState): EngineParams {
        val block = Block.forPrestige(state.prestiges)
        fun lvl(id: String) = state.upgrades[id] ?: 0
        val filter = lvl("filter")
        val boot = lvl("boot")
        val lap = lvl("lap")
        val gap = lvl("gap")
        val tip = lvl("tip")
        val needle = lvl("needle")
        val oilService = lvl("oil_service")
        val fins = lvl("fins")
        val wrench = lvl("wrench")
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
        val nitrous = lvl("nitrous")
        val overboost = lvl("overboost")
        val limiter = lvl("limiter")
        val dynoFuel = lvl("dyno_fuel")
        val abuse = listOf(nitrous, overboost, limiter, dynoFuel).count { it > 0 }

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
        val controllerPsi = controller * 1.3 + Tuners.value(state, "boost")
        var spool = when (induction) {
            Induction.TWIN_TURBO -> 2500.0
            Induction.TURBO -> 3650.0
            else -> 3800.0
        }
        if (ceramic > 0 && (induction == Induction.TURBO || induction == Induction.TWIN_TURBO)) {
            spool -= 180.0
        }
        spool -= 160.0 * (state.perks["spool"] ?: 0)
        var bp = (1.0 - 0.045 * catback - 0.06 * headers - 0.05 * longTube - 0.012 * tip).coerceIn(0.35, 1.15)
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
        var ve = block.peakVe +
            0.020 * filter + 0.024 * cai + 0.020 * manifold + 0.028 * itb +
            0.010 * catback + 0.018 * headers + 0.020 * longTube +
            0.032 * cam + 0.020 * vvt + 0.046 * vvl +
            0.022 * lap + 0.018 * boot + 0.014 * tip + 0.012 * gap + 0.010 * fins +
            0.018 * (state.perks["flow"] ?: 0)
        var frac = block.vePeakFraction + 0.012 * manifold + 0.022 * itb + 0.012 * headers +
            0.018 * longTube + 0.040 * cam + 0.015 * vvl + Tuners.value(state, "cam")
        if (limiter > 0) {
            val breathing = block.redlineRpm + 180.0 * springs + if (vvl > 0) 150.0 else 0.0
            val extended = breathing + 450.0 * limiter
            val capped = frac.coerceIn(0.35, 0.92)
            frac = capped * breathing / extended
        }
        var width = 3100.0 + 80.0 * cai - 140.0 * itb - 220.0 * cam + 650.0 * vvt + 400.0 * vvl
        var strength = 1.0 + 0.055 * springs + 0.12 * studs + 0.26 * pistons +
            0.16 * rods + 0.12 * crank + 0.36 * billet + 0.045 * wrench + 0.08 * (state.perks["fixtures"] ?: 0)
        var flow = block.fuelFlow + 0.18 * jets + 0.10 * needle + 0.40 * pump + 0.32 * tbi + 0.60 * injectors + 0.95 * hpfp +
            0.06 * (state.perks["pump"] ?: 0)
        var timing = block.timingDeg + 1.6 * baseTiming + 1.8 * mapped + 0.55 * coils + 0.85 * gap
        if (torque) timing += 1.4
        timing += Tuners.value(state, "spark")
        var knock = 0.18 + if (knockBox > 0) 0.60 else 0.0
        if (torque) knock += 0.10
        var ign = 0.95 + 0.012 * plugs + 0.015 * coils + 0.008 * gap + 0.012 * (state.perks["readers"] ?: 0)
        var trim = 0.10 * afr
        if (torque) trim += 0.12
        trim += Tuners.value(state, "mixture")
        val pumpFlow = 1.0 + 0.07 * waterPump + if (electric) 0.08 else 0.0
        var ua = (block.radiatorUa + 260.0 * radiator) * (1.0 + 0.10 * duct) * (0.92 + 0.08 * pumpFlow)
        if (waterless) ua *= 0.84
        val stat = when (thermostat) {
            0 -> 88.0
            1 -> 82.0
            else -> 71.0
        } + Tuners.value(state, "thermostat")
        val capPsi = when (cap) {
            0 -> 13.0
            1 -> 16.0
            else -> 20.0
        }
        val friction = (
            1.0 - 0.05 * crank - 0.035 * oilService - 0.028 * wrench -
                (if (synthetic) 0.04 else 0.0) - 0.012 * (state.perks["breakin"] ?: 0)
            ).coerceIn(0.72, 1.2)
        return EngineParams(
            cylinders = block.cylinders,
            boreMm = (block.boreMm + block.boreMm * 0.038 * bore).coerceIn(block.boreMm * 0.9, block.boreMm * 1.25),
            strokeMm = block.strokeMm,
            compressionRatio = (block.compression + 0.48 * compression).coerceIn(7.5, 14.5),
            peakVe = ve.coerceIn(0.55, 1.35),
            vePeakFraction = frac.coerceIn(0.35, 0.92),
            veWidthRpm = width.coerceIn(900.0, 6500.0),
            redlineRpm = (block.redlineRpm + 180.0 * springs + 450.0 * limiter + Tuners.value(state, "redline") + if (vvl > 0) 150.0 else 0.0).coerceIn(4500.0, 14000.0),
            backpressure = bp.coerceIn(0.30, 1.40),
            timingDeg = timing.coerceIn(6.0, 38.0),
            knockControl = knock.coerceIn(0.05, 0.95),
            ignitionQuality = ign.coerceIn(0.70, 0.995),
            fuelFlowFactor = flow.coerceIn(0.4, 12.0),
            afrTrim = trim.coerceIn(-1.5, 2.6),
            jetQuality = ((jets + 0.35 * needle) / 4.0).coerceIn(0.0, 1.0),
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
            oilCoolerUa = (110.0 * oil + 18.0 * fins).coerceAtLeast(0.0),
            thermostatC = stat.coerceIn(68.0, 94.0),
            capPsi = capPsi,
            coolant = if (waterless) Coolant.WATERLESS else Coolant.GLYCOL_50,
            syntheticOil = synthetic,
            electricPump = electric,
            methFraction = (0.45 * meth).coerceIn(0.0, 1.2),
            exhaustWrap = if (ceramic > 0) 1.0 else 0.0,
            inconelValves = inconel,
            octaneBonus = 1.2 * (state.perks["octane"] ?: 0),
            underhoodDeltaC = (12.0 - 2.4 * cai - 0.30 * boot - 0.25 * fins - 1.5 * (state.perks["bay_air"] ?: 0)).coerceIn(1.5, 25.0),
            individualThrottles = itb > 0,
            installedHp = installedHp(block, state),
            banks = block.banks,
            airCooled = block.airCooled && cai < 3,
            idleRpm = block.idleRpm,
            blockTitle = block.title,
            nitrousFraction = (0.34 * nitrous).coerceIn(0.0, 1.2),
            overboostPsi = (5.0 * overboost).coerceIn(0.0, 20.0),
            leanPull = (0.55 * dynoFuel).coerceIn(0.0, 2.0),
            heatFactor = (1.0 + 0.28 * nitrous + 0.16 * overboost + 0.14 * dynoFuel + 0.10 * limiter).coerceIn(1.0, 3.2),
            rpmPower = (0.06 * limiter).coerceIn(0.0, 0.25),
            dynoFactor = (
                (1.0 + 0.45 * nitrous + 0.28 * overboost + 0.22 * dynoFuel + 0.18 * limiter) *
                    (1.0 + 0.10 * (state.perks["slips"] ?: 0))
                ).coerceIn(1.0, 6.0),
            abuse = abuse,
            coolingHeadroom = (1.0
                + 0.20 * radiator
                + 0.08 * duct
                + 0.07 * waterPump
                + (if (electric) 0.10 else 0.0)
                + 0.16 * oil
                + 0.10 * cai
                + 0.05 * boot
                + 0.12 * ic
                + 0.14 * meth
                + 0.05 * thermostat
                + 0.08 * fins
                + 0.10 * (state.perks["cores"] ?: 0)
                + (if (waterless) -0.06 else 0.0)
                ).coerceIn(0.7, 5.0),
        )
    }

    fun sheet(state: GameState): Sheet {
        val params = paramsFrom(state)
        val stats = EngineSim.evaluate(params)
        val mult = Prestige.multiplier(state.knowledge)
        val notebook = state.perks["notebook"] ?: 0
        val rp = (0.05 + 0.035 * stats.peakHorsepower.coerceAtLeast(0.0).pow(0.8)) * (1.0 + 0.22 * notebook) * mult
        val rpm = targetRpm(params.redlineRpm, state.throttle, params.idleRpm)
        val block = Block.forPrestige(state.prestiges)
        val pay = incomeAt(params, rpm, stats.reliability, mult, state.throttle, stats) * block.payScale()
        return Sheet(params, stats, pay, rp, mult)
    }

    fun dynoReward(stats: EngineStats, params: EngineParams? = null): Double {
        val base = 4.0 + 0.6 * stats.peakHorsepower.coerceAtLeast(0.0).pow(0.8)
        return base * (params?.dynoFactor ?: 1.0).coerceAtLeast(1.0)
    }

    /**
     * Failures per second with the past-the-limit parts on. A cool cruise still
     * breaks. A hot, floored pull breaks much sooner. Nothing is fitted, nothing breaks.
     */
    fun failurePerSec(state: GameState, stats: EngineStats): Double {
        val params = paramsFrom(state)
        if (params.abuse <= 0) return 0.0
        val rpm = targetRpm(params.redlineRpm, state.throttle, params.idleRpm)
        val live = EngineSim.point(params, rpm, loadFor(state.throttle))
        val cool = ((live.coolantTempC - 100.0) / 30.0).coerceIn(0.0, 1.0)
        val oil = ((live.oilTempC - 140.0) / 40.0).coerceIn(0.0, 1.0)
        val egt = ((live.egtC - 880.0) / 180.0).coerceIn(0.0, 1.0)
        val hot = maxOf(cool, oil, egt)
        val weak = ((70.0 - stats.reliability) / 70.0).coerceIn(0.0, 1.0)
        val load = state.throttle.coerceIn(0.0, 1.0)
        return 0.00018 * params.abuse + 0.0042 * hot * (0.30 + 0.70 * load) + 0.0015 * weak * (0.25 + 0.75 * load)
    }

    /** A blown part, a repair bill, and a short stretch where the contract pays nothing. */
    fun breakDown(state: GameState, nowMs: Long): GameState {
        val current = sheet(state)
        val bill = (current.incomePerSec * 6.0 * 60.0).coerceAtLeast(50.0)
        val notes = current.stats.reliabilityNotes
        val why = when {
            notes.any { it.startsWith("Coolant") } -> "The head lifted. Coolant on the floor."
            notes.any { it.startsWith("EGT") } -> "An exhaust valve gave up in the heat."
            notes.any { it.startsWith("Oil") } -> "A bearing spun."
            notes.any { it.contains("Boost") || it.contains("Piston") } -> "A rod let go at the top of the pull."
            notes.any { it == "Knock" } -> "Detonation peppered a piston."
            else -> "Something in the bottom end let go."
        }
        return state.copy(
            money = (state.money - bill).coerceAtLeast(0.0),
            downUntilEpochMs = nowMs + 40_000L,
            breakdowns = state.breakdowns + 1,
            lastFailure = "$why Repair ${formatDollars(bill)}. The contract is quiet for 40 seconds.",
        )
    }

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
            "Nobody was here to make a dyno pull, so research is only a fifth of the passive rate."
        return next to msg
    }

    fun costMultiplier(state: GameState): Double =
        (1.0 - 0.05 * (state.perks["tooling"] ?: 0)).coerceAtLeast(0.82)

    fun upgradeCost(state: GameState, id: String): Double {
        if (state.devMode) return 0.0
        val u = Upgrades.byId(id)
        val level = state.upgrades[id] ?: 0
        val scale = Block.forPrestige(state.prestiges).upgradePriceScale(state.prestiges)
        return u.baseCost * u.growth.pow(level.toDouble()) * costMultiplier(state) * 0.5 * scale
    }

    /**
     * What to print instead of a price when the next level is closed.
     * Names the upgrade you still have to buy. If the gate is only a study, names that study.
     */
    fun lockLabel(state: GameState, id: String): String? {
        val research = lockReason(state, id) ?: return null
        val unmet = UpgradeMap.parents(Upgrades.byId(id)).filter { (state.upgrades[it] ?: 0) <= 0 }
        val names = if (unmet.isNotEmpty()) unmet.map { Upgrades.byId(it).name } else listOf(research)
        return "Needs " + names.joinToString(" · ")
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

    /** What the fitted level cost, at today's prices. Dev mode refunds nothing. */
    fun upgradeRefund(state: GameState, id: String): Double {
        val level = state.upgrades[id] ?: 0
        if (level <= 0) return 0.0
        val prior = if (level == 1) state.upgrades - id else state.upgrades + (id to level - 1)
        return upgradeCost(state.copy(upgrades = prior), id)
    }

    fun canRemoveUpgrade(state: GameState, id: String): Boolean =
        Upgrades.all.any { it.id == id } && (state.upgrades[id] ?: 0) > 0

    fun removeUpgrade(state: GameState, id: String): GameState {
        if (!canRemoveUpgrade(state, id)) return state
        val level = state.upgrades[id] ?: 0
        val refund = upgradeRefund(state, id)
        val next = if (level == 1) state.upgrades - id else state.upgrades + (id to level - 1)
        return state.copy(money = state.money + refund, upgrades = next)
    }

    fun preview(state: GameState, id: String): StatDelta? {
        val u = Upgrades.all.find { it.id == id } ?: return null
        val level = state.upgrades[id] ?: 0
        if (level >= u.maxLevel) return null
        val now = sheet(state)
        val next = sheet(state.copy(upgrades = state.upgrades + (id to level + 1)))
        val nowRpm = targetRpm(now.params.redlineRpm, state.throttle, now.params.idleRpm)
        val nextRpm = targetRpm(next.params.redlineRpm, state.throttle, next.params.idleRpm)
        val load = loadFor(state.throttle)
        val nowPoint = EngineSim.point(now.params, nowRpm, load)
        val nextPoint = EngineSim.point(next.params, nextRpm, load)
        return StatDelta(
            hp = nextPoint.horsepower - nowPoint.horsepower,
            torque = nextPoint.torqueLbFt - nowPoint.torqueLbFt,
            mpg = nextPoint.mpg - nowPoint.mpg,
            reliability = next.stats.reliability - now.stats.reliability,
            coolantC = nextPoint.coolantTempC - nowPoint.coolantTempC,
            oilC = nextPoint.oilTempC - nowPoint.oilTempC,
            egtC = nextPoint.egtC - nowPoint.egtC,
            peakHp = next.stats.peakHorsepower - now.stats.peakHorsepower,
            peakTorque = next.stats.peakTorqueLbFt - now.stats.peakTorqueLbFt,
            cruiseMpg = next.stats.cruiseMpg - now.stats.cruiseMpg,
            payPerSec = next.incomePerSec - now.incomePerSec,
        )
    }

    fun researchStatus(state: GameState, id: String): ResearchStatus {
        val node = ResearchTree.byId(id)
        if (id in state.researched) return ResearchStatus.OWNED
        if (node.minPrestige > state.prestiges) return ResearchStatus.LOCKED
        if (node.requires.any { it !in state.researched }) return ResearchStatus.LOCKED
        return ResearchStatus.OPEN
    }

    fun canBuyResearch(state: GameState, id: String): Boolean {
        if (researchStatus(state, id) != ResearchStatus.OPEN) return false
        return state.researchPoints >= researchCost(state, id)
    }

    fun researchCost(state: GameState, id: String): Double =
        if (state.devMode) 0.0 else ResearchTree.byId(id).cost

    fun buyResearch(state: GameState, id: String): GameState {
        if (!canBuyResearch(state, id)) return state
        val cost = researchCost(state, id)
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

    fun canPrestige(state: GameState): Boolean {
        val stats = sheet(state).stats
        return Prestige.meets(stats.peakHorsepower, stats.peakTorqueLbFt, stats.cruiseMpg, Block.forPrestige(state.prestiges))
    }

    /** Cash on a fresh long-block: the four cheapest tools that block can buy. */
    fun startingMoney(prestiges: Int): Double {
        val probe = GameState(prestiges = prestiges.coerceAtLeast(0))
        return Upgrades.all
            .filter { lockReason(probe, it.id) == null }
            .map { upgradeCost(probe, it.id) }
            .sorted()
            .take(4)
            .sum()
    }

    fun newGame(): GameState = GameState(money = startingMoney(0))

    fun prestige(state: GameState): GameState {
        val stats = sheet(state).stats
        val gain = Prestige.knowledgeGain(
            stats.peakHorsepower,
            stats.peakTorqueLbFt,
            stats.cruiseMpg,
            Block.forPrestige(state.prestiges),
        )
        if (gain <= 0) return state
        return state.copy(
            money = startingMoney(state.prestiges + 1),
            runEarnings = 0.0,
            researchPoints = state.researchPoints * 0.20,
            knowledge = state.knowledge + gain,
            prestiges = state.prestiges + 1,
            upgrades = emptyMap(),
            tune = emptyMap(),
            fuel = FuelKind.PUMP_87,
            throttle = REST_THROTTLE,
            downUntilEpochMs = 0L,
            lastFailure = "",
            soakC = 0.0,
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
        val cc = EngineSim.displacementCc(params)
        val size = if (cc < 900.0) {
            "${cc.toInt()}cc"
        } else {
            String.format(java.util.Locale.US, "%.1fL", cc / 1000.0)
        }
        val ind = when (params.induction) {
            Induction.NA -> "naturally aspirated"
            Induction.SUPERCHARGER -> "supercharged"
            Induction.TURBO -> "turbo"
            Induction.TWIN_TURBO -> "twin-turbo"
        }
        return "${params.blockTitle}, $size, $ind"
    }
}
