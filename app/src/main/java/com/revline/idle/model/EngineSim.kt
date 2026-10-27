package com.revline.idle.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlinx.serialization.Serializable

/**
 * Lumped spark-ignition model. Textbook relationships, not a 1D gas-dynamics code:
 * Otto cycle with hot-gas gamma, compressor isentropic efficiency, intercooler
 * effectiveness, rpm-based friction MEP, and a coolant/oil energy balance.
 */
enum class Induction { NA, SUPERCHARGER, TURBO, TWIN_TURBO }

enum class Mixture { CARB, TBI, PORT, DIRECT }

@Serializable
enum class FuelKind(
    val octane: Double,
    val lhvJPerKg: Double,
    val kgPerGallon: Double,
    val stoichAfr: Double,
    val latentJPerKg: Double,
    val dollarsPerGallon: Double,
) {
    PUMP_87(87.0, 43.4e6, 2.80, 14.7, 350e3, 3.40),
    PREMIUM_93(93.0, 43.4e6, 2.80, 14.7, 340e3, 4.50),
    E85(105.0, 26.9e6, 2.96, 9.76, 850e3, 4.10),
    RACE_100(100.0, 43.0e6, 2.84, 14.6, 330e3, 15.00),
}

enum class Coolant { GLYCOL_50, WATERLESS }

data class EngineParams(
    val cylinders: Int = 4,
    val boreMm: Double = 86.0,
    val strokeMm: Double = 86.0,
    val compressionRatio: Double = 9.5,
    val peakVe: Double = 0.93,
    val vePeakFraction: Double = 0.58,
    val veWidthRpm: Double = 3100.0,
    val redlineRpm: Double = 6500.0,
    val backpressure: Double = 1.0,
    val timingDeg: Double = 16.0,
    val knockControl: Double = 0.18,
    val ignitionQuality: Double = 0.95,
    val fuelFlowFactor: Double = 1.25,
    val afrTrim: Double = 0.0,
    val jetQuality: Double = 0.0,
    val mixture: Mixture = Mixture.CARB,
    val fuel: FuelKind = FuelKind.PUMP_87,
    val maxBoostPsi: Double = 0.0,
    val boostControllerPsi: Double = 0.0,
    val induction: Induction = Induction.NA,
    val spoolRpm: Double = 3800.0,
    val intercoolerEff: Double = 0.0,
    val friction: Double = 1.0,
    val internalsStrength: Double = 1.0,
    val radiatorUa: Double = 760.0,
    val oilCoolerUa: Double = 0.0,
    val thermostatC: Double = 88.0,
    val capPsi: Double = 13.0,
    val coolant: Coolant = Coolant.GLYCOL_50,
    val syntheticOil: Boolean = false,
    val electricPump: Boolean = false,
    val methFraction: Double = 0.0,
    val exhaustWrap: Double = 0.0,
    val inconelValves: Boolean = false,
    val octaneBonus: Double = 0.0,
    val underhoodDeltaC: Double = 12.0,
    val individualThrottles: Boolean = false,
    val banks: Int = 1,
    val airCooled: Boolean = false,
    val idleRpm: Double = 820.0,
    val blockTitle: String = "2.0L inline-four",
    /** Nitrous fraction applied on a deep pedal. Brings its own oxygen and fuel, and its own heat. */
    val nitrousFraction: Double = 0.0,
    /** Extra psi held in above mid-rpm. Only does anything once a compressor is fitted. */
    val overboostPsi: Double = 0.0,
    /** AFR points the wide-open map is leaned for the rollers. */
    val leanPull: Double = 0.0,
    /** Multiplier on wall heat. 1 is a normal map. */
    val heatFactor: Double = 1.0,
    /** Extra brake power from turning past the rpm the block was drawn for. */
    val rpmPower: Double = 0.0,
    /** Dyno-slip multiplier. The rollers pay for the abuse the contract does not. */
    val dynoFactor: Double = 1.0,
    /** How many past-the-limit parts are on the engine. */
    val abuse: Int = 0,
    /**
     * Horsepower bolted on by parts whose other effect is latent on this engine
     * (a radiator on a cool moped, a pump that is not yet out of fuel).
     * Scales down on larger blocks. Applied with throttle so the pull sees all of it.
     */
    val installedHp: Double = 0.0,
)

data class OperatingPoint(
    val rpm: Double,
    val horsepower: Double,
    val torqueLbFt: Double,
    val boostPsi: Double,
    val volumetricEfficiency: Double,
    val afr: Double,
    val timingDeg: Double,
    val mbtDeg: Double,
    val chargeTempC: Double,
    val compressorOutC: Double,
    val coolantTempC: Double,
    val oilTempC: Double,
    val egtC: Double,
    val coolantKw: Double,
    val oilToAirKw: Double,
    val oilGeneratedKw: Double,
    val exhaustKw: Double,
    val bayKw: Double,
    val fuelPumpedKw: Double,
    val fuelReleasedKw: Double,
    val brakeKw: Double,
    val frictionKw: Double,
    val wallKw: Double,
    val brakeEfficiency: Double,
    val knockMargin: Double,
    val octane: Double,
    val octaneDemand: Double,
    val unresolvedKnock: Double,
    val fuelLimited: Boolean,
    /** Miles per gallon if this fuel flow were asked to hold 60 mph. Falls as the pull drinks more. */
    val mpg: Double = 0.0,
    /** Throttle-plate load, 1.0 on a dyno pull. */
    val load: Double = 1.0,
    /** Degrees the intake charge picked up from the head and the bay after the cooler. */
    val heatSoakC: Double = 0.0,
)

data class EngineStats(
    val displacementCc: Double,
    val peakHorsepower: Double,
    val peakHorsepowerRpm: Double,
    val peakTorqueLbFt: Double,
    val peakTorqueRpm: Double,
    val cruiseMpg: Double,
    val redlineRpm: Double,
    val reliability: Double,
    val reliabilityNotes: List<String>,
    val meanPistonSpeedMps: Double,
    val peakCoolantC: Double,
    val peakOilC: Double,
    val peakEgtC: Double,
    val peakChargeC: Double,
    val peakCoolantKw: Double,
    val peakOilKw: Double,
    val peakExhaustKw: Double,
    val peakFuelKw: Double,
    val peakBrakeEfficiency: Double,
    val peakBoostPsi: Double,
    val curve: List<OperatingPoint>,
)

object EngineSim {
    const val AMBIENT_C = 25.0
    const val AMBIENT_K = 298.15
    const val ATM_PSI = 14.6959
    const val RHO_REF = 1.184
    const val CP_AIR = 1005.0
    const val CP_EXHAUST = 1280.0
    const val GAMMA_COLD = 1.40
    const val GAMMA_HOT = 1.30
    const val COMPRESSOR_EFF = 0.72
    const val REAL_CYCLE = 0.95
    const val SENSIBLE_EXHAUST = 0.68
    const val OIL_GALLERY_UA = 210.0
    const val OIL_SUMP_UA = 45.0
    const val ROAD_LOAD_W = 15_000.0
    const val CRUISE_RPM = 2_200.0

    fun displacementCc(boreMm: Double, strokeMm: Double, cylinders: Int): Double {
        val radius = boreMm / 2.0
        return cylinders * Math.PI * radius * radius * strokeMm / 1000.0
    }

    fun displacementCc(params: EngineParams): Double =
        displacementCc(params.boreMm, params.strokeMm, params.cylinders)

    fun meanPistonSpeedMps(strokeMm: Double, rpm: Double): Double =
        2.0 * (strokeMm / 1000.0) * rpm / 60.0

    /** Water boiling point vs cap pressure, plus glycol. 0 psig = 100°C, 15 psig ≈ 121°C. */
    fun boilingPointC(capPsi: Double, glycolFraction: Double): Double {
        val water = 100.0 + (21.0 / 15.0) * capPsi.coerceAtLeast(0.0)
        return water + 14.0 * glycolFraction.coerceIn(0.0, 1.0)
    }

    fun coolantBoilingPointC(params: EngineParams): Double = when (params.coolant) {
        Coolant.WATERLESS -> 191.0
        Coolant.GLYCOL_50 -> boilingPointC(params.capPsi, 0.50)
    }

    fun ottoEfficiency(compressionRatio: Double): Double {
        val cr = compressionRatio.coerceAtLeast(6.0)
        return 1.0 - cr.pow(1.0 - GAMMA_HOT)
    }

    /**
     * Steady pull at this rpm. [load] is the throttle plate: 1 is wide open, lower is
     * manifold vacuum, less boost, and a leaner mixture. The result is heat-soaked,
     * the equilibrium of holding that pedal rather than a cold first fire.
     */
    fun point(params: EngineParams, rpm: Double, load: Double = 1.0): OperatingPoint {
        val safeRpm = rpm.coerceAtLeast(400.0)
        val open = load.coerceIn(0.12, 1.0)
        var extra = 0.0
        var result = once(params, safeRpm, open, extra, plate = true)
        repeat(3) {
            extra = heatSoakC(params, result, open)
            result = once(params, safeRpm, open, extra, plate = true)
        }
        return result.copy(load = open, heatSoakC = extra)
    }

    fun evaluate(params: EngineParams): EngineStats {
        val rpms = buildList {
            var r = 1_200.0
            while (r < params.redlineRpm - 1.0) {
                add(r)
                r += 200.0
            }
            add(params.redlineRpm)
        }
        val curve = rpms.map { point(params, it) }
        val peakHp = curve.maxBy { it.horsepower }
        val peakTq = curve.maxBy { it.torqueLbFt }
        val mpg = cruiseMpg(params)
        val reliability = reliability(params, curve)
        return EngineStats(
            displacementCc = displacementCc(params),
            peakHorsepower = peakHp.horsepower,
            peakHorsepowerRpm = peakHp.rpm,
            peakTorqueLbFt = peakTq.torqueLbFt,
            peakTorqueRpm = peakTq.rpm,
            cruiseMpg = mpg,
            redlineRpm = params.redlineRpm,
            reliability = reliability.first,
            reliabilityNotes = reliability.second,
            meanPistonSpeedMps = meanPistonSpeedMps(params.strokeMm, params.redlineRpm),
            peakCoolantC = curve.maxOf { it.coolantTempC },
            peakOilC = curve.maxOf { it.oilTempC },
            peakEgtC = curve.maxOf { it.egtC },
            peakChargeC = curve.maxOf { it.chargeTempC },
            peakCoolantKw = curve.maxOf { it.coolantKw },
            peakOilKw = curve.maxOf { it.oilGeneratedKw },
            peakExhaustKw = curve.maxOf { it.exhaustKw },
            peakFuelKw = curve.maxOf { it.fuelReleasedKw },
            peakBrakeEfficiency = peakHp.brakeEfficiency,
            peakBoostPsi = curve.maxOf { it.boostPsi },
            curve = curve,
        )
    }

    fun cruiseMpg(params: EngineParams): Double {
        val wot = once(params, CRUISE_RPM, load = 1.0, extraChargeC = 0.0, plate = false)
        val unthrottled = wot.brakeKw * 1000.0
        val load = (ROAD_LOAD_W / unthrottled.coerceAtLeast(1500.0)).coerceIn(0.08, 1.0)
        val vd = displacementCc(params) / 1e6
        val pmepKpa = 62.0 * (1.0 - load)
        val pPump = pmepKpa * 1000.0 * vd * (CRUISE_RPM / 120.0)
        val pFric = wot.frictionKw * 1000.0
        val pInd = (wot.brakeKw + wot.frictionKw) * 1000.0
        val etaInd = (pInd / (wot.fuelReleasedKw * 1000.0).coerceAtLeast(1.0)).coerceIn(0.12, 0.55)
        val peakRpm = params.redlineRpm * params.vePeakFraction
        val dist = abs(CRUISE_RPM - peakRpm) / params.veWidthRpm
        val camPen = (1.0 - 0.09 * max(0.0, dist - 0.35)).coerceIn(0.70, 1.0)
        val eta = (etaInd * camPen).coerceIn(0.12, 0.50)
        val fuelW = (ROAD_LOAD_W + pFric + pPump) / eta
        val comb = (0.975 * params.ignitionQuality).coerceIn(0.7, 0.99)
        val mFuel = fuelW / (params.fuel.lhvJPerKg * comb)
        val mpg = 60.0 / (mFuel * 3600.0 / params.fuel.kgPerGallon)
        return mpg.coerceIn(8.0, 70.0)
    }

    private fun reliability(params: EngineParams, curve: List<OperatingPoint>): Pair<Double, List<String>> {
        val notes = mutableListOf<String>()
        var score = 97.0
        val knock = curve.maxOf { it.unresolvedKnock }
        if (knock > 0.35) {
            score -= knock * 5.4
            notes += "Knock"
        }
        val boil = coolantBoilingPointC(params)
        val coolantLimit = boil - 12.0
        val coolant = curve.maxOf { it.coolantTempC }
        if (coolant > coolantLimit) {
            score -= (coolant - coolantLimit) * 0.85
            notes += "Coolant ${coolant.toInt()}°C"
        }
        val oilLimit = if (params.syntheticOil) 160.0 else 145.0
        val oil = curve.maxOf { it.oilTempC }
        if (oil > oilLimit) {
            score -= (oil - oilLimit) * 0.45
            notes += "Oil ${oil.toInt()}°C"
        }
        val egtLimit = if (params.inconelValves) 980.0 else 900.0
        val egt = curve.maxOf { it.egtC }
        if (egt > egtLimit) {
            score -= (egt - egtLimit) * 0.12
            notes += "EGT ${egt.toInt()}°C"
        }
        val mps = meanPistonSpeedMps(params.strokeMm, params.redlineRpm)
        val mpsLimit = 21.0 + (params.internalsStrength - 1.0) * 6.0
        if (mps > mpsLimit) {
            score -= (mps - mpsLimit) * 7.0
            notes += "Piston speed"
        }
        val boost = curve.maxOf { it.boostPsi }
        val boostLimit = (params.internalsStrength - 0.72) * 22.0
        if (boost > boostLimit) {
            score -= (boost - boostLimit) * 1.7
            notes += "Boost vs. internals"
        }
        val lean = curve.any { it.boostPsi > 3.0 && it.afr > params.fuel.stoichAfr + 0.25 }
        if (lean) {
            score -= 16.0
            notes += "Lean under boost"
        }
        if (params.abuse > 0) {
            score -= 7.0 * params.abuse
            score -= (params.heatFactor - 1.0).coerceAtLeast(0.0) * 14.0
            notes += "Past the limit"
        }
        return score.coerceIn(5.0, 99.0) to notes
    }

    /**
     * Manifold and underhood heat soak, °C added to the charge after the intercooler.
     * The head runs hotter than the coolant when the radiator is the bottleneck.
     * A cold-air inlet, a bigger core, an intercooler, and water-meth all cut it.
     */
    fun heatSoakC(params: EngineParams, point: OperatingPoint, load: Double): Double {
        val ua = params.radiatorUa.coerceAtLeast(80.0) + params.oilCoolerUa * 0.65
        val headRise = (point.coolantKw.coerceAtLeast(0.0) * 1000.0) / (ua * 0.42 + 380.0)
        val overStat = (point.coolantTempC - params.thermostatC).coerceAtLeast(0.0)
        val metal = point.coolantTempC * 0.62 + point.oilTempC * 0.38 + headRise + overStat * 0.45
        val residence = (2400.0 / point.rpm.coerceAtLeast(700.0)).pow(0.35).coerceIn(0.55, 1.45)
        val open = load.coerceIn(0.0, 1.0)
        val conductance = (0.07 + 0.30 * open) * residence
        val cai = (params.underhoodDeltaC / 12.0).coerceIn(0.12, 1.15)
        val gap = (metal - point.chargeTempC).coerceAtLeast(0.0)
        val runner = gap * conductance * (0.50 + 0.50 * cai)
        val meth = params.methFraction.coerceIn(0.0, 1.2)
        return (runner - meth * 3.2).coerceIn(0.0, 42.0)
    }

    private fun once(
        params: EngineParams,
        rpm: Double,
        load: Double,
        extraChargeC: Double,
        plate: Boolean,
    ): OperatingPoint {
        val open = if (plate) load.coerceIn(0.12, 1.0) else 1.0
        val boost = if (plate) boostAt(params, rpm) * open.pow(1.08) else 0.0
        val ve = volumetricEfficiency(params, rpm)
        val t1 = AMBIENT_K + params.underhoodDeltaC
        val manifoldPsi = ATM_PSI * (0.30 + 0.70 * open)
        val pr = (manifoldPsi + boost) / ATM_PSI
        val overShare = if (params.overboostPsi > 0.2 && boost > 0.4) {
            (params.overboostPsi / (boost + 1.0)).coerceIn(0.0, 0.65)
        } else {
            0.0
        }
        val icEff = params.intercoolerEff * (1.0 - 0.50 * overShare)
        val compressor = compressorTemp(t1, pr, boost, icEff)
        val tAfterCooler = compressor.second
        val mAirGuess = airMass(params, rpm, ve, tAfterCooler, pr)
        val wotMix = if (!plate) 0.0 else open.pow(1.55)
        val mixed = cruiseAfr(params) + (wotAfr(params) - cruiseAfr(params)) * wotMix
        val target = if (plate && params.leanPull > 0.0) {
            (mixed + params.leanPull * open.pow(1.4)).coerceAtMost(params.fuel.stoichAfr - 0.30)
        } else {
            mixed
        }
        val maxFuel = 0.0096 * params.fuelFlowFactor
        val fuelGuess = minOf(mAirGuess / target, maxFuel)
        val portFrac = portEvapFraction(params)
        val evap = ((fuelGuess * params.fuel.latentJPerKg * portFrac) / (mAirGuess * CP_AIR))
            .coerceIn(0.0, 40.0)
        val methC = if (plate && open > 0.62 && params.methFraction > 0.0) {
            ((fuelGuess * params.methFraction * 1.4e6) / (mAirGuess * CP_AIR)).coerceIn(0.0, 28.0)
        } else {
            0.0
        }
        val tCharge = (tAfterCooler - evap - methC + extraChargeC).coerceAtLeast(273.15)
        val mAir = airMass(params, rpm, ve, tCharge, pr)
        val desired = mAir / target
        val mFuel = minOf(desired, maxFuel)
        val limited = desired > maxFuel * 1.002
        val afr = mAir / mFuel.coerceAtLeast(1e-6)
        val stoich = params.fuel.stoichAfr
        val burned = minOf(mFuel, mAir / stoich)
        val octanePoints = (params.fuel.octane - 87.0).coerceAtLeast(0.0)
        val grade = (octanePoints / 6.0).coerceIn(0.0, 3.2)
        val gradeBurn = (1.0 + 0.09 * grade.coerceAtMost(1.15) + 0.035 * (grade - 1.15).coerceAtLeast(0.0))
            .coerceAtMost(1.26)
        val gradeTiming = (octanePoints * 0.62).coerceAtMost(14.0)
        val comb = (0.975 * params.ignitionQuality * gradeBurn).coerceIn(0.7, 0.995)
        val shot = if (plate) params.nitrousFraction.coerceAtLeast(0.0) * open.pow(1.5) else 0.0
        val leanHit = if (plate) params.leanPull.coerceAtLeast(0.0) * open.pow(1.4) else 0.0
        val released = burned * params.fuel.lhvJPerKg * comb * (1.0 + 0.72 * shot) * (1.0 + 0.08 * leanHit)
        val pumped = mFuel * params.fuel.lhvJPerKg
        val cr = params.compressionRatio
        val etaOtto = ottoEfficiency(cr)
        var wall = 0.155 * (3000.0 / rpm).pow(0.30) * (86.0 / params.boreMm).pow(0.18)
        wall *= 1.0 + 0.0045 * max(0.0, 88.0 - params.thermostatC)
        wall = wall.coerceIn(0.09, 0.30)
        val tChargeC = tCharge - 273.15
        val diCool = if (params.mixture == Mixture.DIRECT) {
            ((mFuel * params.fuel.latentJPerKg * 0.40) / (mAir * CP_AIR)).coerceIn(0.0, 22.0)
        } else {
            0.0
        }
        val octane = params.fuel.octane +
            (params.internalsStrength - 1.0) * 2.2 +
            params.octaneBonus
        val demand = 74.0 +
            4.6 * (cr - 8.5) +
            1.12 * boost +
            0.34 * max(0.0, params.timingDeg + gradeTiming - 12.0) +
            0.16 * max(0.0, tChargeC - 28.0) +
            12.0 * shot -
            (if (params.mixture == Mixture.DIRECT) 3.5 + diCool * 0.15 else 0.0)
        val margin = octane - demand
        val pull = if (margin < 0.0) {
            minOf(-margin * 0.72, 2.2 + params.knockControl * 14.0)
        } else {
            0.0
        }
        val unresolved = if (margin < 0.0) max(0.0, -margin - params.knockControl * 11.0) else 0.0
        val timing = params.timingDeg + gradeTiming - pull
        val mbt = 15.0 + 2.0 * ((rpm - 2500.0) / 1000.0) +
            0.5 * max(0.0, cr - 10.0) - 0.22 * boost
        val miss = timing - mbt
        val phase = max(0.52, 1.0 - 0.0009 * miss * miss)
        // Unresolved knock is octane the ECU did not pull. It burns as detonation, not as torque.
        val detonation = (1.0 - 0.030 * unresolved).coerceIn(0.46, 1.0)
        val etaInd = etaOtto * REAL_CYCLE * (1.0 - wall) * phase * detonation
        val pInd = released * etaInd
        val fmep = fmepKpa(rpm, params, boost, open > 0.9 && boost > 0.4)
        val vd = displacementCc(params) / 1e6
        val pFric = fmep * 1000.0 * vd * (rpm / 120.0)
        val vacuum = if (plate) (1.0 - open).coerceIn(0.0, 1.0) else 0.0
        val pPump = 78.0 * vacuum * vacuum * 1000.0 * vd * (rpm / 120.0)
        val pBrake = max(0.0, pInd - pFric - pPump)
        var qWalls = released * wall
        var qExhaust = max(0.0, released - pInd - qWalls)
        val moved = params.exhaustWrap.coerceIn(0.0, 1.0) * 0.10 * qWalls
        qWalls -= moved
        qExhaust += moved
        val heat = params.heatFactor.coerceIn(1.0, 3.2)
        val qOil = (qWalls * 0.16 + pFric * 0.42) * heat
        val qCool = (qWalls * 0.78 + pFric * 0.50) * heat * (1.0 + 0.35 * shot)
        val qBay = qWalls * 0.06 + pFric * 0.08
        val temps = thermalTemps(params, qCool, qOil)
        val transfer = OIL_GALLERY_UA * (temps.second - temps.first)
        val qRad = qCool + transfer
        val qOilAir = qOil - transfer
        val mEx = mAir + mFuel
        var egt = tChargeC + (qExhaust * SENSIBLE_EXHAUST) / (mEx * CP_EXHAUST)
        if (afr < stoich) egt -= (stoich - afr) * 12.0
        if (afr > stoich + 0.4) egt += (afr - stoich) * 18.0
        egt += shot * 180.0 + leanHit * 70.0
        val push = if (rpm > params.redlineRpm * 0.55) params.rpmPower else params.rpmPower * 0.25
        val hp = (pBrake / 745.7) * (1.0 + push) + params.installedHp * open.coerceIn(0.45, 1.0)
        val richFraction = ((stoich - afr) / stoich).coerceAtLeast(0.0)
        val gulp = if (plate) 1.0 + 1.8 * open.pow(5.0) * (0.65 + richFraction) else 1.0
        val gallonsPerHour = mFuel * 3600.0 / params.fuel.kgPerGallon * gulp
        val mpg = (60.0 / gallonsPerHour.coerceAtLeast(1e-5)).coerceIn(0.4, 400.0)
        return OperatingPoint(
            rpm = rpm,
            horsepower = hp,
            torqueLbFt = hp * 5252.0 / rpm,
            boostPsi = boost,
            volumetricEfficiency = ve,
            afr = afr,
            timingDeg = timing,
            mbtDeg = mbt,
            chargeTempC = tChargeC,
            compressorOutC = compressor.first - 273.15,
            coolantTempC = temps.first,
            oilTempC = temps.second,
            egtC = egt,
            coolantKw = qRad / 1000.0,
            oilToAirKw = qOilAir / 1000.0,
            oilGeneratedKw = qOil / 1000.0,
            exhaustKw = qExhaust / 1000.0,
            bayKw = qBay / 1000.0,
            fuelPumpedKw = pumped / 1000.0,
            fuelReleasedKw = released / 1000.0,
            brakeKw = pBrake / 1000.0,
            frictionKw = (pFric + pPump) / 1000.0,
            wallKw = qWalls / 1000.0,
            brakeEfficiency = if (released > 1.0) pBrake / released else 0.0,
            knockMargin = margin,
            octane = octane,
            octaneDemand = demand,
            unresolvedKnock = unresolved,
            fuelLimited = limited,
            mpg = mpg,
        )
    }

    private fun thermalTemps(params: EngineParams, qCool: Double, qOil: Double): Pair<Double, Double> {
        val oilAir = OIL_SUMP_UA + params.oilCoolerUa
        val gallery = OIL_GALLERY_UA
        var tCool = params.thermostatC
        var tOil = params.thermostatC + 20.0
        repeat(4) {
            tOil = (qOil + oilAir * AMBIENT_C + gallery * tCool) / (oilAir + gallery)
            val transfer = gallery * (tOil - tCool)
            val qRad = qCool + transfer
            val rise = AMBIENT_C + qRad / params.radiatorUa.coerceAtLeast(20.0)
            tCool = max(params.thermostatC, rise)
        }
        return tCool to tOil
    }

    private fun volumetricEfficiency(params: EngineParams, rpm: Double): Double {
        val peakRpm = params.redlineRpm * params.vePeakFraction
        val z = (rpm - peakRpm) / params.veWidthRpm
        val shape = exp(-0.5 * z * z)
        var ve = params.peakVe * (0.60 + 0.40 * shape)
        ve *= (1.02 - 0.04 * params.backpressure)
        return ve.coerceIn(0.20, 1.32)
    }

    private fun boostAt(params: EngineParams, rpm: Double): Double {
        if (params.induction == Induction.NA) return 0.0
        val maxB = params.maxBoostPsi + params.boostControllerPsi
        val x = (rpm / params.redlineRpm).coerceIn(0.0, 1.0)
        val base = if (maxB <= 0.0) {
            0.0
        } else {
            when (params.induction) {
                Induction.SUPERCHARGER -> maxB * (0.38 + 0.62 * x)
                Induction.TURBO -> maxB / (1.0 + exp(-(rpm - params.spoolRpm) / 420.0))
                Induction.TWIN_TURBO -> maxB / (1.0 + exp(-(rpm - params.spoolRpm * 0.66) / 340.0))
                Induction.NA -> 0.0
            }
        }
        val gate = ((rpm / params.redlineRpm) - 0.62) / 0.30
        return base + params.overboostPsi.coerceAtLeast(0.0) * gate.coerceIn(0.0, 1.0)
    }

    /** Returns compressor-out Kelvin and post-intercooler Kelvin. */
    private fun compressorTemp(t1: Double, pr: Double, boost: Double, intercooler: Double): Pair<Double, Double> {
        if (boost <= 0.2) return t1 to t1
        val t2s = t1 * pr.pow((GAMMA_COLD - 1.0) / GAMMA_COLD)
        val t2 = t1 + (t2s - t1) / COMPRESSOR_EFF
        val t3 = t2 - intercooler.coerceIn(0.0, 0.9) * (t2 - (AMBIENT_K + 6.0))
        return t2 to t3.coerceAtLeast(AMBIENT_K + 2.0)
    }

    private fun airMass(params: EngineParams, rpm: Double, ve: Double, tCharge: Double, pr: Double): Double {
        val rho = RHO_REF * (AMBIENT_K / tCharge) * pr
        val vd = displacementCc(params) / 1e6
        return (rpm / 120.0) * vd * ve * rho
    }

    private fun portEvapFraction(params: EngineParams): Double {
        val base = when (params.mixture) {
            Mixture.CARB -> 0.28
            Mixture.TBI -> 0.40
            Mixture.PORT -> 0.48
            Mixture.DIRECT -> 0.08
        }
        return if (params.fuel == FuelKind.E85 && params.mixture != Mixture.DIRECT) {
            (base + 0.12).coerceAtMost(0.70)
        } else {
            base
        }
    }

    fun wotAfr(params: EngineParams): Double {
        val base = when (params.fuel) {
            FuelKind.E85 -> 9.0
            FuelKind.RACE_100 -> 13.05
            else -> 13.35
        }
        val slop = mixtureSlop(params)
        val leanLimit = if (params.fuel == FuelKind.E85) 11.0 else 15.2
        val richLimit = if (params.fuel == FuelKind.E85) 7.3 else 11.2
        return (base - params.afrTrim + slop * 0.85).coerceIn(richLimit, leanLimit)
    }

    fun cruiseAfr(params: EngineParams): Double {
        val base = if (params.fuel == FuelKind.E85) 10.3 else 15.3
        val slop = mixtureSlop(params)
        val rich = if (params.fuel == FuelKind.E85) 8.6 else 13.6
        val lean = if (params.fuel == FuelKind.E85) 12.0 else 16.8
        return (base + params.afrTrim * 0.45 - slop * 0.7).coerceIn(rich, lean)
    }

    private fun mixtureSlop(params: EngineParams): Double {
        val jet = params.jetQuality.coerceIn(0.0, 1.0)
        return when (params.mixture) {
            Mixture.CARB -> (0.50 - 0.28 * jet).coerceAtLeast(0.12)
            Mixture.TBI -> 0.18
            Mixture.PORT -> 0.06
            Mixture.DIRECT -> 0.03
        }
    }

    private fun fmepKpa(rpm: Double, params: EngineParams, boost: Double, wot: Boolean): Double {
        val n = rpm / 1000.0
        var fmep = params.friction * (52.0 + 7.2 * n + 0.95 * n * n + 0.85 * max(0.0, boost))
        if (params.induction == Induction.SUPERCHARGER && wot) fmep += 16.0 + 4.0 * n
        if (params.electricPump) fmep -= 4.0 else fmep += 3.0
        return fmep.coerceAtLeast(20.0)
    }
}
