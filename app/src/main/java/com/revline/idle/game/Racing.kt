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

    val mule = Chassis("mule", "Shop mule", 640.0, Drive.RWD, Place.FRONT, 0.72, 1.45, 1, 0, 0.0, 0.0, "A bedstead with wheels. Not a famous car.")

    /**
     * Lotus Seven 387 kg, Classic & Sports Car (lightest published form 375 kg).
     * Mini 850 604 kg, Cooper S about 30 kg more, same source.
     * Giulia Sprint GTA 745 kg, 1965 homologation figure.
     * E-Type lightweight about 1,016 kg.
     * Cobra 289 985 kg, FIA historic file.
     * Ferrari 250 GTO 880 kg, published kerb.
     * Ford GT40 910 kg, 1967 car folio.
     * Porsche 917K 800 kg, the 1970 5-litre class minimum it was built to.
     */
    val chassis: List<Chassis> = listOf(
        Chassis("seven", "Lotus Seven", 387.0, Drive.RWD, Place.FRONT, 0.68, 1.15, 2, 0, 40.0, 480.0, "387 kg kerb"),
        Chassis("mini", "Mini Cooper S", 634.0, Drive.FWD, Place.FRONT, 0.48, 1.55, 2, 0, 80.0, 900.0, "634 kg, Cooper S"),
        Chassis("gta", "Alfa Giulia Sprint GTA", 745.0, Drive.RWD, Place.FRONT, 0.42, 1.70, 3, 1, 220.0, 2400.0, "745 kg homologation"),
        Chassis("etype", "Jaguar E-Type lightweight", 1016.0, Drive.RWD, Place.FRONT, 0.44, 1.78, 3, 2, 420.0, 4200.0, "about 1,016 kg"),
        Chassis("cobra", "Shelby Cobra 289", 985.0, Drive.RWD, Place.FRONT, 0.58, 1.62, 3, 3, 700.0, 6400.0, "985 kg, FIA historic"),
        Chassis("gto", "Ferrari 250 GTO", 880.0, Drive.RWD, Place.FRONT, 0.38, 1.72, 3, 4, 1400.0, 9800.0, "880 kg kerb"),
        Chassis("gt40", "Ford GT40", 910.0, Drive.RWD, Place.MID, 0.39, 1.58, 4, 5, 2200.0, 15000.0, "910 kg kerb"),
        Chassis("p917", "Porsche 917K", 800.0, Drive.RWD, Place.MID, 0.36, 1.52, 5, 6, 4200.0, 24000.0, "800 kg class minimum"),
    )

    val gearboxes = listOf(
        Gearbox("crash", "3-speed crash box", 0.90, 0.72, 28.0, null, 0, 0.0),
        Gearbox("four", "4-speed", 0.91, 0.84, 34.0, null, 0, 0.0),
        Gearbox("close", "Close-ratio 4-speed", 0.92, 0.91, 32.0, "box_close", 1, 1600.0),
        Gearbox("dog", "5-speed dog box", 0.93, 0.97, 38.0, "box_dog", 4, 5200.0),
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

    val studies = listOf(
        Study("box_close", "Close-ratio gears", "A tighter set so the engine stays in the power.", 60.0, minPrestige = 1),
        Study("box_dog", "Dog engagement", "No synchros. The shift is a bang, and the box stays in gear.", 900.0, "box_close", 4),
        Study("axle_lsd", "Limited-slip", "Both rear tires take the torque.", 50.0),
        Study("axle_irs", "Independent rear", "The live axle comes out. The unsprung weight goes with it.", 400.0, "axle_lsd", 2),
        Study("axle_cv", "Plated differential", "A clutch pack and CV joints. The late prototypes used them.", 1600.0, "axle_irs", 5),
        Study("tire_radial", "Radials", "A belted tire. More grip, same rolling size.", 30.0),
        Study("tire_race", "Race rubber", "A soft compound on a light wheel.", 280.0, "tire_radial", 2),
        Study("tire_slick", "Slicks", "No tread. They need a dry road and a car that can put them down.", 1200.0, "tire_race", 4),
    ) + chassis.map {
        Study("chassis_${it.id}", it.name, "${it.source}. ${it.place.name.lowercase(Locale.US)} engine, ${it.drive.name}.", it.studyCost, minPrestige = it.minPrestige)
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
    )

    fun chassisById(id: String): Chassis = chassis.find { it.id == id } ?: mule

    fun car(state: GameState): Car = assess(specOf(state))

    fun specOf(state: GameState): Spec {
        val sheet = Logic.sheet(state)
        val body = chassisById(state.chassis)
        val level = (state.chassisLevel[body.id] ?: 0).coerceIn(0, 3)
        val box = gearboxes.find { it.id == state.transmission } ?: gearboxes.first { it.id == "four" }
        val axle = axles.find { it.id == state.driveline } ?: axles.first()
        val tire = tires.find { it.id == state.tire } ?: tires.first()
        val drive = runCatching { Drive.valueOf(state.driveType) }.getOrNull() ?: body.drive
        val place = runCatching { Place.valueOf(state.enginePlace) }.getOrNull() ?: body.place
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

    fun assess(spec: Spec): Car {
        val body = spec.chassis
        val shaved = 0.04 * spec.level.coerceIn(0, 3)
        val massFloor = body.kg * 0.92
        var mass = (body.kg * (1.0 - shaved)).coerceAtLeast(massFloor)
        mass += 35.0 + spec.displacementCc * 0.042
        mass += spec.gearbox.kg + spec.axle.kg + spec.tire.kg
        if (spec.drive != body.drive) mass += 40.0
        if (spec.place != body.place) mass += 30.0
        if (spec.drive == Drive.AWD) mass += 55.0
        var eff = spec.gearbox.efficiency * spec.axle.efficiency
        if (spec.drive == Drive.AWD) eff *= 0.94
        if (spec.drive != body.drive) eff *= 0.97
        if (spec.place != body.place) eff *= 0.98
        val wheelHp = spec.hp * eff * spec.gearbox.band
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
        val slots = spec.slots.coerceAtLeast(0)
        val drag = body.cd * body.area * (1.0 + 0.018 * slots) * spec.tire.drag
        // A small radiator cooks a big engine. The launch is what the tires can hold.
        val heat = wheelHp / (32.0 + 18.0 * slots.coerceAtLeast(1))
        val fade = ((heat - 0.70).coerceAtLeast(0.0) * 0.55).coerceAtMost(0.72)
        val held = (1.0 - fade).coerceIn(0.34, 1.0)
        val traction = spec.tire.mu * spec.axle.grip * balance * mass * 0.55
        val accel = wheelHp * held / mass.coerceAtLeast(80.0)
        val launch = (wheelTq.coerceAtMost(traction) / mass) * spec.tire.mu * spec.axle.grip * balance
        val vMax = sqrt((wheelHp * held / drag.coerceAtLeast(0.2)).coerceAtLeast(0.01))
        val pace = 1000.0 * accel.pow(0.46) * launch.pow(0.22) * vMax.pow(0.32)
        return Car(spec, mass, wheelHp, pace, fade)
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
        })
        val next = if (win) state.copy(money = state.money + purse) else state
        val verdict = if (win) "Won ${formatDollars(purse)}" else "Lost"
        val note = "${tier.name.lowercase(Locale.US)}  your pace ${you.pace.toInt()} vs ${ghost.pace.toInt()}. $verdict."
        return next to note
    }

    fun ladder(state: GameState): Pair<GameState, String> {
        val rungs = ladderRungs()
        val index = state.raceLadder.coerceIn(0, rungs.lastIndex)
        val rung = rungs[index]
        val you = car(state)
        val win = you.pace > rung.pace
        val purse = 30.0 * 2.6.pow(index.toDouble())
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
        if (body.id == mule.id) return state.copy(chassis = mule.id)
        val studyId = "chassis_${body.id}"
        if (studyId !in state.raceStudies) return state
        if (body.minPrestige > state.prestiges) return state
        val have = state.chassisLevel[body.id] ?: 0
        if (state.chassis != body.id && have >= 1) {
            return state.copy(chassis = body.id, coolingSlots = state.coolingSlots.coerceAtMost(slotMax(body, have)))
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
        )
    }

    fun fitPart(state: GameState, kind: String, id: String): GameState {
        val price = partPrice(state, kind, id)
        val study = partStudy(kind, id)
        if (study != null && study !in state.raceStudies) return state
        if (!state.devMode && price > 0.0 && state.money < price) return state
        val paid = if (state.devMode || price <= 0.0) 0.0 else price
        val already = when (kind) {
            "box" -> state.transmission == id
            "axle" -> state.driveline == id
            "tire" -> state.tire == id
            "drive" -> state.driveType == id
            "place" -> state.enginePlace == id
            else -> true
        }
        if (already) return state
        val spent = state.copy(money = state.money - paid)
        return when (kind) {
            "box" -> spent.copy(transmission = id)
            "axle" -> spent.copy(driveline = id)
            "tire" -> spent.copy(tire = id)
            "drive" -> spent.copy(driveType = id)
            "place" -> spent.copy(enginePlace = id)
            else -> state
        }
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

    private fun partPrice(state: GameState, kind: String, id: String): Double {
        val scale = Block.forPrestige(state.prestiges).upgradePriceScale(state.prestiges)
        val base = when (kind) {
            "box" -> gearboxes.find { it.id == id }?.price ?: 0.0
            "axle" -> axles.find { it.id == id }?.price ?: 0.0
            "tire" -> tires.find { it.id == id }?.price ?: 0.0
            else -> 0.0
        }
        return base * scale
    }

    private fun partStudy(kind: String, id: String): String? = when (kind) {
        "box" -> gearboxes.find { it.id == id }?.study
        "axle" -> axles.find { it.id == id }?.study
        "tire" -> tires.find { it.id == id }?.study
        else -> null
    }

    private data class Ghost(val title: String, val pace: Double, val label: String)

    private fun classGhost(state: GameState, tier: Tier): Ghost {
        val block = Block.forPrestige(state.prestiges)
        val frac = when (tier) {
            Tier.CLUB -> 0.38
            Tier.NATIONAL -> 0.85
            Tier.INTERNATIONAL -> 1.02
        }
        val body = when (tier) {
            Tier.CLUB -> mule
            Tier.NATIONAL -> chassis.getOrNull((state.prestiges - 1).coerceIn(0, chassis.lastIndex)) ?: chassis.first()
            Tier.INTERNATIONAL -> chassis.last { it.minPrestige <= state.prestiges }
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
            Spec(hp, hp * 5252.0 / 3600.0, cc, body, if (tier == Tier.INTERNATIONAL) 2 else 0, box, axle, tire, body.slots, body.drive, body.place),
        )
        return Ghost(body.name, built.pace, body.name)
    }

    data class Rung(val title: String, val pace: Double)

    fun ladderRungs(): List<Rung> = Block.entries.flatMapIndexed { index, block ->
        val body = chassis[index.coerceAtMost(chassis.lastIndex)]
        listOf(0.70 to "club", 1.12 to "national").map { (frac, name) ->
            val cc = com.revline.idle.model.EngineSim.displacementCc(block.boreMm, block.strokeMm, block.cylinders)
            val hp = block.hpGoal * frac
            val tire = if (frac < 1.0) tires.first { it.id == "radial" } else tires.first { it.id == "race" }
            val box = if (frac < 1.0) gearboxes.first { it.id == "four" } else gearboxes.first { it.id == "close" }
            val pace = assess(
                Spec(hp, hp * 5252.0 / 3600.0, cc, body, 1, box, axles.first { it.id == "lsd" }, tire, body.slots, body.drive, body.place),
            ).pace
            Rung("${block.title} $name", pace)
        }
    }

    private data class Rival(val label: String, val pace: Double)

    private fun rival(state: GameState, seed: Long): Rival {
        val rng = Random(seed)
        val block = Block.forPrestige(state.prestiges)
        val body = (listOf(mule) + chassis.filter { it.minPrestige <= state.prestiges }).random(rng)
        val built = rng.nextDouble() < 0.5
        val tire = if (built) tires.first { it.id == "slick" } else tires.first { it.id == "radial" }
        val box = if (built) gearboxes.first { it.id == "dog" } else gearboxes.first { it.id == "four" }
        val axle = if (built) axles.first { it.id == "irs" } else axles.first { it.id == "lsd" }
        val frac = if (built) 1.05 + rng.nextDouble() * 0.55 else 0.35 + rng.nextDouble() * 0.25
        val cc = EngineSim.displacementCc(block.boreMm, block.strokeMm, block.cylinders)
        val hp = block.sheetHp(false) * frac
        val pace = assess(
            Spec(hp, hp * 5252.0 / 3600.0, cc, body, rng.nextInt(0, 3), box, axle, tire, body.slots, body.drive, body.place),
        ).pace
        val label = "${body.name}, ${box.name.lowercase(Locale.US)}, ${tire.name.lowercase(Locale.US)}, ${hp.toInt()} hp"
        return Rival(label, pace)
    }
}
