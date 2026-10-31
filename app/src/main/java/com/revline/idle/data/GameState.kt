package com.revline.idle.data

import com.revline.idle.model.FuelKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

@Serializable
data class GameState(
    val money: Double = 0.0,
    val runEarnings: Double = 0.0,
    val researchPoints: Double = 0.0,
    val knowledge: Int = 0,
    val prestiges: Int = 0,
    /** This long-block is the race spec: higher redline, higher sheet power, lower sheet mpg. */
    val racing: Boolean = false,
    /** Fitted chassis. "mule" is the bedstead you start on. */
    val chassis: String = "mule",
    val chassisLevel: Map<String, Int> = emptyMap(),
    val transmission: String = "four",
    /** Final drive. short launches harder and runs out of gear. tall is lazy off the line. */
    val finalDrive: String = "road",
    val driveline: String = "open",
    /** The drag tire. The strip uses this one. Circuits use [circuitTire]. */
    val tire: String = "bias",
    /** The circuit tire: street, sport, semi, racing. Owned and fitted apart from the drag tire. */
    val circuitTire: String = "street",
    /** Race components fitted, line id to level. A missing line is the stock part (level 0). */
    val kit: Map<String, Int> = emptyMap(),
    /** Blank uses the chassis's own layout. */
    val driveType: String = "",
    val enginePlace: String = "",
    val coolingSlots: Int = 1,
    val raceStudies: Set<String> = emptySet(),
    /** Bought after the study: "box:close", "final:short", "tire:radial". The starter parts are not listed. */
    val raceOwned: Set<String> = emptySet(),
    val raceLadder: Int = 0,
    /** Strip length: eighth, quarter, or half. */
    val raceLength: String = "quarter",
    val upgrades: Map<String, Int> = emptyMap(),
    val researched: Set<String> = emptySet(),
    val perks: Map<String, Int> = emptyMap(),
    val fuel: FuelKind = FuelKind.PUMP_87,
    /** Dyno throttle stop, 0 at idle and 1 at redline. Pay follows output used and mpg here. */
    val throttle: Double = 0.18,
    /** Screw positions. Missing keys are the stock stop. Locked screws are ignored. */
    val tune: Map<String, Double> = emptyMap(),
    /** Drivers on the roster you have hired (ids). Hiring is free. A driver charges a fee only for a race he drives. */
    val hiredDrivers: Set<String> = emptySet(),
    /** The hired driver the race screen's toggle has selected. Blank drives it yourself. */
    val driverId: String = "",
    /** Testing switch: parts and studies cost nothing. Gates still apply. */
    val devMode: Boolean = false,
    /** Stops the bench drawing and the speaker. The contract keeps paying. */
    val batterySaver: Boolean = false,
    /** What the next-part line is chasing: POWER, TORQUE, CRUISE, or PAY. */
    val aimGoal: String = "POWER",
    /** Epoch ms until which a blown engine pays no contract. */
    val downUntilEpochMs: Long = 0L,
    val breakdowns: Int = 0,
    val lastFailure: String = "",
    /** Charge heat soaked into the head, °C above a cold start. Climbs with rpm. */
    val soakC: Double = 0.0,
    val savedAtEpochMs: Long = 0L,
    val dynoReadyAtEpochMs: Long = 0L,
    /** Circuit races entered. The weather of the next race is a function of this and the circuit. */
    val circuitRaces: Int = 0,
    val version: Int = SAVE_VERSION,
)

/** 1: one tire for drag and circuit. 2: separate drag and circuit tires, race components. 3: circuit race counter (weather). */
const val SAVE_VERSION = 3

object SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(state: GameState): String = json.encodeToString(state)

    /** A save with no version field is from before versions existed: version 1, whatever a new GameState defaults to. */
    fun decode(text: String): GameState {
        val tree = json.parseToJsonElement(text)
        val stamped = if (tree is JsonObject && "version" !in tree) JsonObject(tree + ("version" to JsonPrimitive(1))) else tree
        return migrate(json.decodeFromJsonElement(GameState.serializer(), stamped))
    }

    /**
     * A version 1 save had one tire for every kind of race. The drag tire stays as it was. The circuit gets the nearest
     * tire of its own for every tire the save had studied, bought or fitted, so nothing is lost and nothing is refunded.
     */
    fun migrate(state: GameState): GameState {
        if (state.version >= SAVE_VERSION) return state
        // 2 to 3 added only fields whose defaults are the right start: nothing to convert.
        if (state.version >= 2) return state.copy(version = SAVE_VERSION)
        val studiedSport = "tire_race" in state.raceStudies || "tire_slick" in state.raceStudies
        val studiedSemi = "tire_slick" in state.raceStudies
        val ownsSport = "tire:race" in state.raceOwned || "tire:slick" in state.raceOwned || state.tire == "race" || state.tire == "slick"
        val ownsSemi = "tire:slick" in state.raceOwned || state.tire == "slick"
        val studies = state.raceStudies + listOfNotNull("ctire_sport".takeIf { studiedSport || ownsSport }, "ctire_semi".takeIf { studiedSemi || ownsSemi })
        val owned = state.raceOwned + listOfNotNull("ctire:sport".takeIf { ownsSport }, "ctire:semi".takeIf { ownsSemi })
        val fitted = when {
            state.tire == "slick" -> "semi"
            state.tire == "race" -> "sport"
            else -> "street"
        }
        return state.copy(circuitTire = fitted, raceStudies = studies, raceOwned = owned, version = SAVE_VERSION)
    }
}

class SaveRepository(private val dir: File) {
    private val file = File(dir, "revline_save.json")

    fun load(): GameState {
        if (!file.exists()) return GameState()
        return try {
            SaveCodec.decode(file.readText())
        } catch (_: Exception) {
            GameState()
        }
    }

    fun save(state: GameState) {
        dir.mkdirs()
        val tmp = File(dir, "revline_save.json.tmp")
        tmp.writeText(SaveCodec.encode(state))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
