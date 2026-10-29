package com.revline.idle.game

import com.revline.idle.data.GameState
import com.revline.idle.economy.formatDollars
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Vintage race cars and the parts that make them go.
 * Weights are published kerb or homologation figures, not invented power ratings.
 * A chassis can be lightened a little. It cannot be lightened past a period race trim.
 */
object Racing {
    enum class Drive { FWD, RWD, AWD }
    enum class Place { FRONT, MID, REAR }
    enum class Tier { CLUB, NATIONAL, INTERNATIONAL }
    enum class Build { LIGHT, MEDIUM, HEAVY }

    enum class Length(val id: String, val meters: Double, val title: String, val scale: Double) {
        EIGHTH("eighth", 201.2, "1/8 mile", 0.70),
        QUARTER("quarter", 402.3, "1/4 mile", 1.0),
        HALF("half", 804.7, "1/2 mile", 1.65),
    }

    data class Chassis(
        val id: String,
        val name: String,
        val kg: Double,
        val drive: Drive,
        val place: Place,
        val cd: Double,
        val area: Double,
        val slots: Int,
        val minPrestige: Int,
        val studyCost: Double,
        val price: Double,
        /** Where the weight comes from, shown on the car. */
        val source: String,
        val build: Build,
        /** Which long-block this tub was built around. 0 is the moped. */
        val group: Int,
        /** Engine places this car actually used. The first is the one it left the works with. */
        val places: List<Place>,
        /** Displacement the bay will take. A bigger engine does not make power in it. */
        val maxCc: Double,
        /** Chassis dyno. Light packaging reads low. A heavy bay reads the engine. */
        val dyno: Double,
    )

    data class Gearbox(
        val id: String,
        val name: String,
        val efficiency: Double,
        val band: Double,
        val kg: Double,
        val study: String?,
        val minPrestige: Int,
        val price: Double,
        /** Gear ratios, first to top. A close box stays in the power. A crash box has gaps. */
        val ratios: List<Double>,
        /** Seconds with the clutch in. A dog box is a bang. A crash box is a pause. */
        val shift: Double,
    )

    data class Axle(
        val id: String,
        val name: String,
        val efficiency: Double,
        val grip: Double,
        val kg: Double,
        val study: String?,
        val minPrestige: Int,
        val price: Double,
    )

    data class Tire(
        val id: String,
        val name: String,
        val mu: Double,
        val drag: Double,
        val kg: Double,
        val study: String?,
        val minPrestige: Int,
        val price: Double,
    )

    data class Study(val id: String, val name: String, val detail: String, val cost: Double, val requires: String? = null, val minPrestige: Int = 0)

    val mule = Chassis(
        "mule", "Shop mule", 640.0, Drive.RWD, Place.FRONT, 0.72, 1.45, 1, 0, 0.0, 0.0,
        "A bedstead with wheels. Not a famous car.", Build.HEAVY, -1, listOf(Place.FRONT), 50_000.0, 0.90,
    )

    /**
     * One light, one medium, and one heavy tub for each long-block.
     * Weights are published kerb or race figures. The bay and the places are the real ones.
     * Peel P50 59 kg, Carfolio. BMW Isetta 250 353 kg, Carfolio. Early 2CV about 560 kg.
     * Messerschmitt KR200 about 230 kg. Fiat 500 499 kg. Saab 96 about 880 kg.
     * Frogeye Sprite about 603 kg. Mini Cooper S 634 kg. Volvo Amazon about 1,090 kg.
     * Lotus Elan S1 640 kg, workshop manual. Alfa GTA 745 kg homologation. BMW 2002 about 1,030 kg.
     * Lotus Seven 387 kg. Renault 5 about 730 kg, front as a road car and mid as the Turbo.
     * E-Type lightweight about 1,016 kg.
     * Lancia Stratos 960 kg. Early 911 about 1,080 kg. Corvette about 1,360 kg, 1963.
     * GT40 910 kg. Cobra 289 985 kg, FIA historic. 300 SEL 6.3 1,780 kg.
     * 917K 800 kg, the 1970 5-litre minimum. Daytona competizione 1,240 kg dry. Jensen Interceptor about 1,680 kg.
     */
    val chassis: List<Chassis> = listOf(
        Chassis("p50", "Peel P50", 59.0, Drive.FWD, Place.FRONT, 0.70, 0.55, 1, 0, 16.0, 140.0, "59 kg, Carfolio", Build.LIGHT, 0, listOf(Place.FRONT), 90.0, 0.74),
        Chassis("isetta", "BMW Isetta 250", 353.0, Drive.RWD, Place.REAR, 0.62, 1.15, 2, 0, 36.0, 380.0, "353 kg, Isetta 250", Build.MEDIUM, 0, listOf(Place.REAR), 360.0, 0.92),
        Chassis("cv2", "Citroen 2CV", 560.0, Drive.FWD, Place.FRONT, 0.52, 1.55, 3, 0, 60.0, 720.0, "560 kg, early 2CV", Build.HEAVY, 0, listOf(Place.FRONT), 750.0, 1.06),

        Chassis("kr200", "Messerschmitt KR200", 230.0, Drive.RWD, Place.REAR, 0.48, 0.85, 1, 1, 40.0, 460.0, "about 230 kg", Build.LIGHT, 1, listOf(Place.REAR), 340.0, 0.78),
        Chassis("fiat500", "Fiat 500", 499.0, Drive.RWD, Place.REAR, 0.48, 1.35, 2, 1, 70.0, 820.0, "499 kg, Nuova 500", Build.MEDIUM, 1, listOf(Place.REAR), 700.0, 0.94),
        Chassis("saab96", "Saab 96", 880.0, Drive.FWD, Place.FRONT, 0.46, 1.70, 4, 1, 120.0, 1500.0, "about 880 kg", Build.HEAVY, 1, listOf(Place.FRONT), 1600.0, 1.08),

        Chassis("sprite", "Austin-Healey Sprite", 603.0, Drive.RWD, Place.FRONT, 0.52, 1.25, 2, 2, 80.0, 980.0, "about 603 kg, Frogeye", Build.LIGHT, 2, listOf(Place.FRONT), 750.0, 0.80),
        Chassis("mini", "Mini Cooper S", 634.0, Drive.FWD, Place.FRONT, 0.48, 1.55, 2, 2, 80.0, 900.0, "634 kg, Cooper S", Build.MEDIUM, 2, listOf(Place.FRONT), 1400.0, 0.90),
        Chassis("amazon", "Volvo Amazon", 1090.0, Drive.RWD, Place.FRONT, 0.48, 1.85, 5, 2, 160.0, 2100.0, "about 1,090 kg", Build.HEAVY, 2, listOf(Place.FRONT), 2400.0, 1.10),

        Chassis("elan", "Lotus Elan", 640.0, Drive.RWD, Place.FRONT, 0.42, 1.35, 2, 3, 180.0, 1800.0, "640 kg, Elan S1 workshop", Build.LIGHT, 3, listOf(Place.FRONT), 1700.0, 0.82),
        Chassis("gta", "Alfa Giulia Sprint GTA", 745.0, Drive.RWD, Place.FRONT, 0.42, 1.70, 3, 3, 220.0, 2400.0, "745 kg homologation", Build.MEDIUM, 3, listOf(Place.FRONT), 2500.0, 0.98),
        Chassis("b2002", "BMW 2002", 1030.0, Drive.RWD, Place.FRONT, 0.44, 1.75, 5, 3, 280.0, 3200.0, "about 1,030 kg", Build.HEAVY, 3, listOf(Place.FRONT), 4000.0, 1.10),

        Chassis("seven", "Lotus Seven", 387.0, Drive.RWD, Place.FRONT, 0.68, 1.15, 2, 4, 40.0, 480.0, "387 kg kerb", Build.LIGHT, 4, listOf(Place.FRONT), 2400.0, 0.80),
        Chassis("r5", "Renault 5", 730.0, Drive.FWD, Place.FRONT, 0.40, 1.72, 3, 4, 240.0, 2600.0, "about 730 kg. Front in the road car. Mid in the Turbo.", Build.MEDIUM, 4, listOf(Place.FRONT, Place.MID), 2800.0, 0.98),
        Chassis("etype", "Jaguar E-Type lightweight", 1016.0, Drive.RWD, Place.FRONT, 0.44, 1.78, 5, 4, 420.0, 4200.0, "about 1,016 kg", Build.HEAVY, 4, listOf(Place.FRONT), 5600.0, 1.10),

        Chassis("stratos", "Lancia Stratos", 960.0, Drive.RWD, Place.MID, 0.40, 1.55, 2, 5, 700.0, 6400.0, "960 kg", Build.LIGHT, 5, listOf(Place.MID), 4200.0, 0.84),
        Chassis("p911", "Porsche 911", 1080.0, Drive.RWD, Place.REAR, 0.40, 1.68, 3, 5, 900.0, 8200.0, "about 1,080 kg, early 911", Build.MEDIUM, 5, listOf(Place.REAR), 5600.0, 1.0),
        Chassis("vette", "Chevrolet Corvette", 1360.0, Drive.RWD, Place.FRONT, 0.48, 1.85, 5, 5, 1100.0, 9800.0, "about 1,360 kg, 1963", Build.HEAVY, 5, listOf(Place.FRONT), 7500.0, 1.12),

        Chassis("gt40", "Ford GT40", 910.0, Drive.RWD, Place.MID, 0.39, 1.58, 2, 6, 2200.0, 15000.0, "910 kg kerb", Build.LIGHT, 6, listOf(Place.MID), 5800.0, 0.86),
        Chassis("cobra", "Shelby Cobra 289", 985.0, Drive.RWD, Place.FRONT, 0.58, 1.62, 3, 6, 700.0, 6400.0, "985 kg, FIA historic", Build.MEDIUM, 6, listOf(Place.FRONT), 6200.0, 1.0),
        Chassis("sel63", "Mercedes 300 SEL 6.3", 1780.0, Drive.RWD, Place.FRONT, 0.45, 2.20, 5, 6, 1800.0, 18000.0, "1,780 kg", Build.HEAVY, 6, listOf(Place.FRONT), 8500.0, 1.14),

        Chassis("p917", "Porsche 917K", 800.0, Drive.RWD, Place.MID, 0.36, 1.52, 2, 7, 4200.0, 24000.0, "800 kg class minimum", Build.LIGHT, 7, listOf(Place.MID), 7200.0, 0.86),
        Chassis("daytona", "Ferrari Daytona competizione", 1240.0, Drive.RWD, Place.FRONT, 0.40, 1.80, 3, 7, 2800.0, 20000.0, "1,240 kg dry", Build.MEDIUM, 7, listOf(Place.FRONT), 7800.0, 1.0),
        Chassis("jensen", "Jensen Interceptor", 1680.0, Drive.RWD, Place.FRONT, 0.46, 2.05, 5, 7, 2200.0, 22000.0, "about 1,680 kg", Build.HEAVY, 7, listOf(Place.FRONT), 9000.0, 1.12),
    )

    val gearboxes = listOf(
        Gearbox("crash", "3-speed crash box", 0.90, 0.72, 28.0, "box_crash", 0, 320.0, listOf(2.87, 1.55, 1.00), 0.58),
        Gearbox("four", "4-speed", 0.91, 0.84, 34.0, null, 0, 0.0, listOf(2.52, 1.74, 1.27, 1.00), 0.36),
        Gearbox("close", "Close-ratio 4-speed", 0.92, 0.91, 32.0, "box_close", 1, 1600.0, listOf(1.92, 1.51, 1.23, 1.00), 0.22),
        Gearbox("dog", "5-speed dog box", 0.93, 0.97, 38.0, "box_dog", 4, 5200.0, listOf(2.64, 1.88, 1.48, 1.22, 1.00), 0.13),
    )

    val axles = listOf(
        Axle("open", "Live axle, open diff", 0.97, 0.90, 42.0, null, 0, 0.0),
        Axle("lsd", "Limited-slip", 0.96, 1.00, 46.0, "axle_lsd", 0, 900.0),
        Axle("irs", "Independent, limited-slip", 0.95, 1.07, 36.0, "axle_irs", 2, 2800.0),
        Axle("cv", "CV shafts, plated diff", 0.94, 1.12, 30.0, "axle_cv", 5, 6400.0),
    )

    val tires = listOf(
        Tire("bias", "Bias-ply street", 0.82, 1.00, 32.0, null, 0, 0.0),
        Tire("radial", "Period radial", 0.94, 1.00, 28.0, "tire_radial", 0, 400.0),
        Tire("race", "Dunlop race", 1.08, 1.02, 24.0, "tire_race", 2, 1800.0),
        Tire("slick", "Slick", 1.26, 1.05, 22.0, "tire_slick", 4, 4600.0),
    )

    data class Final(val id: String, val name: String, val ratio: Double, val study: String?, val price: Double)

    /** The mule starts on the road gear. The other two are a study, then a purchase. */
    val finals = listOf(
        Final("short", "Short 4.56", 4.56, "final_short", 640.0),
        Final("road", "Road 3.73", 3.73, null, 0.0),
        Final("tall", "Tall 3.07", 3.07, "final_tall", 640.0),
    )

    val studies = listOf(
        Study("box_crash", "Crash box", "Three gears and a slow shift. The study comes before the box.", 24.0),
        Study("final_short", "Short final drive", "A 4.56 gear. It leaves harder and runs out of rpm.", 40.0),
        Study("final_tall", "Tall final drive", "A 3.07 gear. It is lazy off the line and carries the top.", 40.0),
        Study("box_close", "Close-ratio gears", "A tighter set so the engine stays in the power.", 60.0, minPrestige = 1),
        Study("box_dog", "Dog engagement", "No synchros. The shift is a bang, and the box stays in gear.", 900.0, "box_close", 4),
        Study("axle_lsd", "Limited-slip", "Both rear tires take the torque.", 50.0),
        Study("axle_irs", "Independent rear", "The live axle comes out. The unsprung weight goes with it.", 400.0, "axle_lsd", 2),
        Study("axle_cv", "Plated differential", "A clutch pack and CV joints. The late prototypes used them.", 1600.0, "axle_irs", 5),
        Study("tire_radial", "Radials", "A belted tire. More grip, same rolling size.", 30.0),
        Study("tire_race", "Race rubber", "A soft compound on a light wheel.", 280.0, "tire_radial", 2),
        Study("tire_slick", "Slicks", "No tread. They need a dry road and a car that can put them down.", 1200.0, "tire_race", 4),
    ) + chassis.map {
        val spots = it.places.joinToString(" or ") { spot -> spot.name.lowercase(Locale.US) }
        Study(
            "chassis_${it.id}",
            it.name,
            "${it.source}. ${it.build.name.lowercase(Locale.US)}. Engine sits $spots. Bay to ${(it.maxCc / 1000.0).let { liters -> if (liters < 0.8) "${it.maxCc.toInt()} cc" else "${"%.1f".format(liters)} L" }}.",
            it.studyCost,
            minPrestige = it.minPrestige,
        )
    }

    data class Spec(
        val hp: Double,
        val tq: Double,
        val displacementCc: Double,
        val chassis: Chassis,
        val level: Int,
        val gearbox: Gearbox,
        val axle: Axle,
        val tire: Tire,
        val slots: Int,
        val drive: Drive,
        val place: Place,
    )

    data class Car(
        val spec: Spec,
        val massKg: Double,
        val wheelHp: Double,
        val pace: Double,
        val fade: Double,
        val fits: Boolean = true,
    )

    fun lengthOf(id: String): Length = Length.entries.find { it.id == id } ?: Length.QUARTER

    fun groupChassis(group: Int, build: Build): Chassis =
        chassis.firstOrNull { it.group == group && it.build == build } ?: mule

    fun fits(body: Chassis, cc: Double): Boolean = cc <= body.maxCc

    fun chassisById(id: String): Chassis = chassis.find { it.id == id } ?: mule

    fun driveFor(body: Chassis, place: Place): Drive =
        if (body.id == "r5" && place == Place.MID) Drive.RWD else body.drive

    fun car(state: GameState): Car = assess(specOf(state), lengthOf(state.raceLength))

    /** Fastest chassis this engine can sit in, on the distance selected. */
    fun quickest(state: GameState): Chassis? =
        chassis
            .filter { it.minPrestige <= state.prestiges }
            .map { it to forecast(state, it) }
            .filter { (_, look) -> look.fits }
            .maxByOrNull { (_, look) -> look.pace }
            ?.first

    /**
     * A clean run in each final drive, on the strip selected.
     * The pace card does not see the gear. This does.
     */
    fun gearTimes(state: GameState): Map<String, Double> {
        val setup = RaceDrive.setup(state)
        val meters = lengthOf(state.raceLength).meters
        return listOf("short", "road", "tall").associateWith { id ->
            RaceDrive.driveWell(setup.copy(finalDrive = finalRatio(id), distanceM = meters)).time
        }
    }

    /** What this engine would do in [body] on the strip you have selected. */
    fun forecast(state: GameState, body: Chassis): Car {
        val now = specOf(state)
        val place = body.place
        return assess(
            now.copy(chassis = body, place = place, drive = driveFor(body, place), slots = body.slots),
            lengthOf(state.raceLength),
        )
    }

    fun specOf(state: GameState): Spec {
        val sheet = Logic.sheet(state)
        val body = chassisById(state.chassis)
        val level = (state.chassisLevel[body.id] ?: 0).coerceIn(0, 3)
        val box = gearboxes.find { it.id == state.transmission } ?: gearboxes.first { it.id == "four" }
        val axle = axles.find { it.id == state.driveline } ?: axles.first()
        val tire = tires.find { it.id == state.tire } ?: tires.first()
        val asked = runCatching { Place.valueOf(state.enginePlace) }.getOrNull()
        val place = if (asked != null && asked in body.places) asked else body.place
        val drive = driveFor(body, place)
        val slots = state.coolingSlots.coerceIn(0, slotMax(body, level))
        return Spec(
            hp = sheet.stats.peakHorsepower,
            tq = sheet.stats.peakTorqueLbFt,
            displacementCc = EngineSim.displacementCc(sheet.params),
            chassis = body,
            level = level,
            gearbox = box,
            axle = axle,
            tire = tire,
            slots = slots,
            drive = drive,
            place = place,
        )
    }

    fun slotMax(body: Chassis, level: Int): Int = body.slots + if (level >= 3) 1 else 0

    fun assess(spec: Spec, length: Length = Length.QUARTER): Car {
        val body = spec.chassis
        val shaved = 0.04 * spec.level.coerceIn(0, 3)
        val massFloor = body.kg * 0.92
        var mass = (body.kg * (1.0 - shaved)).coerceAtLeast(massFloor)
        mass += 35.0 + spec.displacementCc * 0.042
        mass += spec.gearbox.kg + spec.axle.kg + spec.tire.kg
        val native = driveFor(body, spec.place)
        if (spec.drive != native) mass += 40.0
        if (spec.place !in body.places) mass += 30.0
        if (spec.drive == Drive.AWD && native != Drive.AWD) mass += 55.0
        var eff = spec.gearbox.efficiency * spec.axle.efficiency
        if (spec.drive == Drive.AWD && native != Drive.AWD) eff *= 0.94
        if (spec.drive != native) eff *= 0.97
        if (spec.place !in body.places) eff *= 0.98
        val stuffed = spec.displacementCc > body.maxCc
        val choke = if (stuffed) 0.35 else 1.0
        val made = spec.hp * eff * spec.gearbox.band * choke
        // The dyno reading is the cramped bay. The track still has the power, until the radiator gives up.
        val wheelHp = made * body.dyno
        val naturalTq = spec.hp * 5252.0 / 3600.0
        val tqFactor = (spec.tq / naturalTq.coerceAtLeast(1.0)).coerceIn(0.75, 1.25)
        val wheelTq = naturalTq * tqFactor * eff
        val balance = when (spec.place) {
            Place.FRONT -> 0.96
            Place.MID -> 1.08
            Place.REAR -> 1.01
        } * when (spec.drive) {
            Drive.FWD -> 0.88
            Drive.RWD -> 1.0
            Drive.AWD -> 1.08
        }
        val slots = if (stuffed) 0 else spec.slots.coerceAtLeast(0)
        val drag = body.cd * body.area * (1.0 + 0.018 * slots) * spec.tire.drag
        // A bay that is full runs hot. A heavy car's bay is mostly empty around a small engine.
        val bay = if (body.id == "mule") 2400.0 else body.maxCc
        val fill = (spec.displacementCc / bay.coerceAtLeast(40.0)).coerceIn(0.15, 1.8)
        val heat = made * fill / (36.0 + 24.0 * slots)
        val fade = if (stuffed) {
            ((heat - 0.70).coerceAtLeast(0.0) * 0.45).coerceIn(0.42, 0.72)
        } else {
            ((heat - 0.90).coerceAtLeast(0.0) * 0.38).coerceAtMost(0.48)
        }
        val held = (1.0 - fade).coerceIn(0.34, 1.0)
        val traction = spec.tire.mu * spec.axle.grip * balance * mass * 0.55
        val road = when {
            body.id == "mule" -> 0.94
            body.build == Build.LIGHT -> 1.26
            body.build == Build.MEDIUM -> 1.0
            else -> 0.90
        }
        val accel = made * held * road / mass.coerceAtLeast(80.0)
        val launch = (wheelTq.coerceAtMost(traction) / mass) * spec.tire.mu * spec.axle.grip * balance
        val vMax = sqrt((made * held * road / drag.coerceAtLeast(0.2)).coerceAtLeast(0.01))
        val (accelExp, launchExp, speedExp) = when (length) {
            Length.EIGHTH -> Triple(0.34, 0.46, 0.20)
            Length.QUARTER -> Triple(0.46, 0.22, 0.32)
            Length.HALF -> Triple(0.36, 0.12, 0.52)
        }
        val pace = 1000.0 * accel.pow(accelExp) * launch.pow(launchExp) * vMax.pow(speedExp)
        return Car(spec, mass, wheelHp, pace, fade, fits = !stuffed)
    }

    fun stakeCost(prestiges: Int): Double = 1_000.0 * 10.0.pow(prestiges.coerceIn(0, 7).toDouble())

    fun quick(state: GameState, tier: Tier): Pair<GameState, String> {
        val you = car(state)
        val ghost = classGhost(state, tier)
        val win = you.pace > ghost.pace
        val seconds = when (tier) {
            Tier.CLUB -> 50.0
            Tier.NATIONAL -> 160.0
            Tier.INTERNATIONAL -> 520.0
        }
        val purse = (Logic.sheet(state).incomePerSec * seconds).coerceAtLeast(when (tier) {
            Tier.CLUB -> 4.0
            Tier.NATIONAL -> 20.0
            Tier.INTERNATIONAL -> 80.0
        }) * lengthOf(state.raceLength).scale
        val next = if (win) state.copy(money = state.money + purse) else state
        val verdict = if (win) "Won ${formatDollars(purse)}" else "Lost"
        val note = "${tier.name.lowercase(Locale.US)}  your pace ${you.pace.toInt()} vs ${ghost.pace.toInt()}. $verdict."
        return next to note
    }

    fun ladder(state: GameState): Pair<GameState, String> {
        val rungs = ladderRungs(lengthOf(state.raceLength))
        val index = state.raceLadder.coerceIn(0, rungs.lastIndex)
        val rung = rungs[index]
        val you = car(state)
        val win = you.pace > rung.pace
        val purse = 30.0 * 2.6.pow(index.toDouble()) * lengthOf(state.raceLength).scale
        val next = if (!win) {
            state
        } else {
            state.copy(money = state.money + purse, raceLadder = (index + 1).coerceAtMost(rungs.lastIndex))
        }
        val verdict = if (win) {
            if (index == rungs.lastIndex) "Won ${formatDollars(purse)}. The ladder is done."
            else "Won ${formatDollars(purse)}. Next is ${rungs[index + 1].title}."
        } else {
            "Short. ${rung.title} is pace ${rung.pace.toInt()}."
        }
        return next to "Ladder ${index + 1}/${rungs.size}  ${rung.title}. Your pace ${you.pace.toInt()}. $verdict"
    }

    data class Field(
        val title: String,
        val detail: String,
        val pace: Double,
        val purse: Double,
        val stake: Double,
        val rivalId: String = "mule",
    )

    fun fieldForQuick(state: GameState, tier: Tier): Field {
        val ghost = classGhost(state, tier)
        val seconds = when (tier) {
            Tier.CLUB -> 50.0
            Tier.NATIONAL -> 160.0
            Tier.INTERNATIONAL -> 520.0
        }
        val purse = (Logic.sheet(state).incomePerSec * seconds).coerceAtLeast(when (tier) {
            Tier.CLUB -> 4.0
            Tier.NATIONAL -> 20.0
            Tier.INTERNATIONAL -> 80.0
        }) * lengthOf(state.raceLength).scale
        return Field(tier.name.lowercase(Locale.US), ghost.label, ghost.pace, purse, 0.0, ghost.id)
    }

    fun fieldForLadder(state: GameState): Field {
        val rungs = ladderRungs(lengthOf(state.raceLength))
        val index = state.raceLadder.coerceIn(0, rungs.lastIndex)
        val rung = rungs[index]
        return Field("ladder ${index + 1}", rung.title, rung.pace, 30.0 * 2.6.pow(index.toDouble()) * lengthOf(state.raceLength).scale, 0.0, rung.chassisId)
    }

    /** Takes the stake up front. Null when the till is short. */
    fun openStake(state: GameState, seed: Long): Pair<GameState, Field>? {
        val cost = stakeCost(state.prestiges)
        if (!state.devMode && state.money < cost) return null
        val rival = rival(state, seed)
        val paid = if (state.devMode) 0.0 else cost
        val next = state.copy(money = state.money - paid)
        val purse = if (state.devMode) 0.0 else cost * 2.0
        return next to Field("stake", rival.label, rival.pace, purse, cost, rival.id)
    }

    fun award(state: GameState, field: Field, won: Boolean, ladder: Boolean): GameState {
        if (!won) return state
        val money = state.money + field.purse
        val step = if (ladder) (state.raceLadder + 1).coerceAtMost(ladderRungs().lastIndex) else state.raceLadder
        return state.copy(money = money, raceLadder = step)
    }

    /** [state] is the car before the purse is paid, so the ladder rung is the one just run. */
    fun stripNote(state: GameState, field: Field, won: Boolean, time: Double, rival: Double, ladder: Boolean): String {
        val clock = String.format(Locale.US, "%.2fs vs %.2fs", time, rival)
        if (field.stake > 0.0) {
            if (state.devMode || field.purse <= 0.0) {
                val verdict = if (won) "The run was good enough." else "The run was short."
                return "Stake was not on the table. $clock. $verdict"
            }
            val verdict = if (won) "Won ${formatDollars(field.purse)}." else "The stake is gone."
            return "Stake ${formatDollars(field.stake)} vs ${field.detail}. $clock. $verdict"
        }
        if (ladder) {
            val rungs = ladderRungs()
            val index = state.raceLadder.coerceIn(0, rungs.lastIndex)
            val rung = rungs[index]
            if (!won) return "Ladder ${index + 1}/${rungs.size}  ${rung.title}. $clock. Short."
            val more = if (index >= rungs.lastIndex) "The ladder is done." else "Next is ${rungs[index + 1].title}."
            return "Ladder ${index + 1}/${rungs.size}  ${rung.title}. $clock. Won ${formatDollars(field.purse)}. $more"
        }
        val verdict = if (won) "Won ${formatDollars(field.purse)}." else "Lost."
        return "${field.title}  ${field.detail}. $clock. $verdict"
    }

    fun stake(state: GameState, seed: Long): Pair<GameState, String> {
        val cost = stakeCost(state.prestiges)
        if (!state.devMode && state.money < cost) {
            return state to "The stake is ${formatDollars(cost)}. The till is short."
        }
        val rival = rival(state, seed)
        val you = car(state)
        val win = you.pace > rival.pace
        val paid = if (state.devMode) 0.0 else cost
        val next = state.copy(money = state.money - paid + if (win) cost * 2.0 else 0.0)
        val verdict = if (win) "Won ${formatDollars(cost * 2.0)}" else "The stake is gone."
        return next to "Stake ${formatDollars(cost)} vs ${rival.label}. ${rival.pace.toInt()} pace, you ${you.pace.toInt()}. $verdict"
    }

    fun study(state: GameState, id: String): GameState {
        val node = studies.find { it.id == id } ?: return state
        if (id in state.raceStudies) return state
        if (node.minPrestige > state.prestiges) return state
        if (node.requires != null && node.requires !in state.raceStudies) return state
        if (!state.devMode && state.researchPoints < node.cost) return state
        val spent = if (state.devMode) 0.0 else node.cost
        return state.copy(researchPoints = state.researchPoints - spent, raceStudies = state.raceStudies + id)
    }

    fun fitChassis(state: GameState, id: String): GameState {
        val body = chassisById(id)
        if (body.id == mule.id) return state.copy(chassis = mule.id, enginePlace = keptPlace(state, body))
        val studyId = "chassis_${body.id}"
        if (studyId !in state.raceStudies) return state
        if (body.minPrestige > state.prestiges) return state
        val have = state.chassisLevel[body.id] ?: 0
        if (state.chassis != body.id && have >= 1) {
            return state.copy(
                chassis = body.id,
                coolingSlots = state.coolingSlots.coerceAtMost(slotMax(body, have)),
                enginePlace = keptPlace(state, body),
            )
        }
        val nextLevel = if (state.chassis == body.id) have + 1 else 1
        if (nextLevel > 3) return state
        if (state.chassis != body.id && have >= 1) return state
        val cost = chassisPrice(state, body, nextLevel)
        if (!state.devMode && state.money < cost) return state
        val paid = if (state.devMode) 0.0 else cost
        return state.copy(
            money = state.money - paid,
            chassis = body.id,
            chassisLevel = state.chassisLevel + (body.id to nextLevel),
            coolingSlots = state.coolingSlots.coerceAtMost(slotMax(body, nextLevel)),
            enginePlace = keptPlace(state, body),
        )
    }

    private fun keptPlace(state: GameState, body: Chassis): String {
        val asked = runCatching { Place.valueOf(state.enginePlace) }.getOrNull()
        return if (asked != null && asked in body.places) state.enginePlace else ""
    }

    fun fitPart(state: GameState, kind: String, id: String): GameState {
        if (kind == "drive") return state
        if (kind == "place") {
            val spot = runCatching { Place.valueOf(id) }.getOrNull() ?: return state
            if (spot !in chassisById(state.chassis).places) return state
        }
        if (fitted(state, kind, id)) return state
        val bought = purchase(state, kind, id) ?: return state
        return when (kind) {
            "box" -> bought.copy(transmission = id)
            "axle" -> bought.copy(driveline = id)
            "tire" -> bought.copy(tire = id)
            "place" -> bought.copy(enginePlace = id, driveType = driveFor(chassisById(state.chassis), Place.valueOf(id)).name)
            else -> state
        }
    }

    fun setLength(state: GameState, id: String): GameState {
        if (Length.entries.none { it.id == id }) return state
        return state.copy(raceLength = id)
    }

    fun setFinal(state: GameState, id: String): GameState {
        if (finals.none { it.id == id }) return state
        if (state.finalDrive == id) return state
        val bought = purchase(state, "final", id) ?: return state
        return bought.copy(finalDrive = id)
    }

    fun finalRatio(id: String): Double = finals.find { it.id == id }?.ratio ?: 3.73

    /** What the chip should say, and which study a tap still has to buy. */
    data class Gate(val label: String, val study: String?)

    fun gate(state: GameState, kind: String, id: String, name: String): Gate {
        val on = fitted(state, kind, id)
        val study = partStudy(kind, id)
        if (!on && study != null && study !in state.raceStudies) {
            val cost = studies.first { it.id == study }.cost.toInt()
            return Gate("$name · $cost RP", study)
        }
        val base = rawPrice(kind, id)
        val owned = base <= 0.0 || "$kind:$id" in state.raceOwned || on
        if (!owned) return Gate("$name · ${formatDollars(partPrice(state, kind, id))}", null)
        return Gate(name, null)
    }

    fun setSlots(state: GameState, slots: Int): GameState {
        val body = chassisById(state.chassis)
        val level = state.chassisLevel[body.id] ?: 0
        return state.copy(coolingSlots = slots.coerceIn(0, slotMax(body, level)))
    }

    fun chassisPrice(state: GameState, body: Chassis, level: Int): Double {
        val scale = Block.forPrestige(state.prestiges).upgradePriceScale(state.prestiges)
        return body.price * scale * 1.15.pow((level - 1).coerceAtLeast(0).toDouble())
    }

    private fun rawPrice(kind: String, id: String): Double = when (kind) {
        "box" -> gearboxes.find { it.id == id }?.price ?: 0.0
        "axle" -> axles.find { it.id == id }?.price ?: 0.0
        "tire" -> tires.find { it.id == id }?.price ?: 0.0
        "final" -> finals.find { it.id == id }?.price ?: 0.0
        else -> 0.0
    }

    private fun partPrice(state: GameState, kind: String, id: String): Double {
        val scale = Block.forPrestige(state.prestiges).upgradePriceScale(state.prestiges)
        return rawPrice(kind, id) * scale
    }

    private fun partStudy(kind: String, id: String): String? = when (kind) {
        "box" -> gearboxes.find { it.id == id }?.study
        "axle" -> axles.find { it.id == id }?.study
        "tire" -> tires.find { it.id == id }?.study
        "final" -> finals.find { it.id == id }?.study
        else -> null
    }

    private fun fitted(state: GameState, kind: String, id: String): Boolean = when (kind) {
        "box" -> state.transmission == id
        "axle" -> state.driveline == id
        "tire" -> state.tire == id
        "final" -> state.finalDrive == id
        "drive" -> state.driveType == id
        "place" -> state.enginePlace == id
        else -> false
    }

    /** Study first, then money once. A part you already bought is free to put back on. */
    private fun purchase(state: GameState, kind: String, id: String): GameState? {
        val study = partStudy(kind, id)
        if (study != null && study !in state.raceStudies) return null
        val base = rawPrice(kind, id)
        val key = "$kind:$id"
        val owned = base <= 0.0 || key in state.raceOwned
        val price = if (owned) 0.0 else partPrice(state, kind, id)
        if (!state.devMode && price > 0.0 && state.money < price) return null
        val paid = if (state.devMode || price <= 0.0) 0.0 else price
        val ownedNow = if (base > 0.0) state.raceOwned + key else state.raceOwned
        return state.copy(money = state.money - paid, raceOwned = ownedNow)
    }

    private data class Ghost(val title: String, val pace: Double, val label: String, val id: String)

    private fun classGhost(state: GameState, tier: Tier): Ghost {
        val block = Block.forPrestige(state.prestiges)
        val frac = when (tier) {
            Tier.CLUB -> 0.42
            Tier.NATIONAL -> 0.85
            Tier.INTERNATIONAL -> 0.58
        }
        val group = state.prestiges.coerceIn(0, Block.entries.lastIndex)
        val body = when (tier) {
            Tier.CLUB -> groupChassis(group, Build.HEAVY)
            Tier.NATIONAL -> groupChassis(group, Build.MEDIUM)
            Tier.INTERNATIONAL -> groupChassis(group, Build.LIGHT)
        }
        val tire = when (tier) {
            Tier.CLUB -> tires.first()
            Tier.NATIONAL -> tires.first { it.id == "race" }
            Tier.INTERNATIONAL -> tires.first { it.id == "slick" }
        }
        val box = when (tier) {
            Tier.CLUB -> gearboxes.first()
            Tier.NATIONAL -> gearboxes.first { it.id == "four" }
            Tier.INTERNATIONAL -> gearboxes.first { it.id == "dog" }
        }
        val axle = when (tier) {
            Tier.CLUB -> axles.first()
            Tier.NATIONAL -> axles.first { it.id == "lsd" }
            Tier.INTERNATIONAL -> axles.first { it.id == "irs" }
        }
        val cc = EngineSim.displacementCc(block.boreMm, block.strokeMm, block.cylinders)
        val hp = block.hpGoal * frac
        val built = assess(
            Spec(hp, hp * 5252.0 / 3600.0, cc, body, if (tier == Tier.INTERNATIONAL) 2 else 0, box, axle, tire, body.slots, driveFor(body, body.place), body.place),
            lengthOf(state.raceLength),
        )
        return Ghost(body.name, built.pace, body.name, body.id)
    }

    data class Rung(val title: String, val pace: Double, val chassisId: String = "mule")

    fun ladderRungs(length: Length = Length.QUARTER): List<Rung> = Block.entries.flatMapIndexed { index, block ->
        listOf(0.70 to "club", 1.12 to "national").map { (frac, name) ->
            val body = groupChassis(index, if (frac < 1.0) Build.MEDIUM else Build.LIGHT)
            val cc = com.revline.idle.model.EngineSim.displacementCc(block.boreMm, block.strokeMm, block.cylinders)
            val hp = block.hpGoal * frac
            val tire = if (frac < 1.0) tires.first { it.id == "radial" } else tires.first { it.id == "race" }
            val box = if (frac < 1.0) gearboxes.first { it.id == "four" } else gearboxes.first { it.id == "close" }
            val pace = assess(
                Spec(hp, hp * 5252.0 / 3600.0, cc, body, 1, box, axles.first { it.id == "lsd" }, tire, body.slots, driveFor(body, body.place), body.place),
                length,
            ).pace
            Rung("${block.title} $name", pace, body.id)
        }
    }

    private data class Rival(val label: String, val pace: Double, val id: String)

    private fun rival(state: GameState, seed: Long): Rival {
        val rng = Random(seed)
        val block = Block.forPrestige(state.prestiges)
        val built = rng.nextDouble() < 0.5
        val tire = if (built) tires.first { it.id == "slick" } else tires.first { it.id == "radial" }
        val box = if (built) gearboxes.first { it.id == "dog" } else gearboxes.first { it.id == "four" }
        val axle = if (built) axles.first { it.id == "irs" } else axles.first { it.id == "lsd" }
        val frac = if (built) 1.05 + rng.nextDouble() * 0.55 else 0.35 + rng.nextDouble() * 0.25
        val cc = EngineSim.displacementCc(block.boreMm, block.strokeMm, block.cylinders)
        val hp = block.sheetHp(false) * frac
        val pool = chassis.filter { it.minPrestige <= state.prestiges && fits(it, cc) }
        val body = (listOf(mule) + pool).random(rng)
        val pace = assess(
            Spec(hp, hp * 5252.0 / 3600.0, cc, body, rng.nextInt(0, 3), box, axle, tire, body.slots, driveFor(body, body.place), body.place),
            lengthOf(state.raceLength),
        ).pace
        val label = "${body.name}, ${box.name.lowercase(Locale.US)}, ${tire.name.lowercase(Locale.US)}, ${hp.toInt()} hp"
        return Rival(label, pace, body.id)
    }
}
