package com.revline.idle.data

import com.revline.idle.model.FuelKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class GameState(
    val money: Double = 0.0,
    val runEarnings: Double = 0.0,
    val researchPoints: Double = 0.0,
    val knowledge: Int = 0,
    val prestiges: Int = 0,
    /**
     * Fitted long-block id ([com.revline.idle.model.Block.name]) when an alternative
     * at this prestige step is chosen. Blank uses the step default (piston / 13B).
     * Old saves omit this and keep the default.
     */
    val blockId: String = "",
    /** This long-block is the race spec: higher redline, higher sheet power, lower sheet mpg. */
    val racing: Boolean = false,
    /** Race rebuilds in a row. Each one makes the next cost more research. A street rebuild resets it. */
    val raceStreak: Int = 0,
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
    /** Drag tune: knob id to value. Missing keys are the default. Saves from before 1.84 have none. See [com.revline.idle.game.DragTune]. */
    val dragTune: Map<String, Double> = emptyMap(),
    /** Drivers on the roster you have hired (ids). Hiring is free. A driver charges a fee only for a race he drives. */
    val hiredDrivers: Set<String> = emptySet(),
    /** The hired driver the race screen's toggle has selected. Blank drives it yourself. */
    val driverId: String = "",
    /** Testing switch: parts and studies cost nothing. Gates still apply. */
    val devMode: Boolean = false,
    /** Stops the bench drawing and the speaker. The contract keeps paying. */
    val batterySaver: Boolean = false,
    /** What the next-part line is chasing: POWER, TORQUE, CRUISE, PAY, or COOLING. An old save keeps the one it had. */
    val aimGoal: String = "POWER",
    /**
     * How that line spends: CHEAPEST, AFFORDABLE, ALMOST, or BEST.
     * Old saves omit this and keep BEST, the biggest step the line used to pick.
     */
    val aimSpend: String = "BEST",
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
    /** The championship season in progress, from 1. */
    val seasonNo: Int = 1,
    /** Rounds of this season already raced: the round up is circuit number seasonRound in the list. */
    val seasonRound: Int = 0,
    /** Championship points by driver name, the player as "You". */
    val seasonPoints: Map<String, Int> = emptyMap(),
    val seasonWins: Map<String, Int> = emptyMap(),
    /** Finished seasons, latest last. */
    val seasonHistory: List<SeasonRecord> = emptyList(),
    val version: Int = SAVE_VERSION,
)

/** A finished season: where the player ended up and who took the title. */
@Serializable
data class SeasonRecord(val season: Int, val position: Int, val points: Int, val champion: String)

/** 1: one tire for drag and circuit. 2: separate drag and circuit tires, race components. 3: circuit race counter (weather) and the championship season. */
const val SAVE_VERSION = 4

object SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(state: GameState): String = json.encodeToString(state)

    /** A save with no version field is from before versions existed: version 1, whatever a new GameState defaults to. */
    fun decode(text: String): GameState = decodeOrNull(text) ?: throw IllegalArgumentException("save does not decode")

    /** Strict read. Null when the text is not a whole save. Never throws. */
    fun decodeOrNull(text: String): GameState? = try {
        scrub(migrate(decodeTree(text)))
    } catch (_: Throwable) {
        null
    }

    /**
     * A save cut off mid-write. Keeps the longest prefix that is still a save
     * (missing fields stay at their defaults) and drops a half-written tail.
     * Null when nothing in the text is a save. Never throws.
     */
    fun salvage(text: String): GameState? {
        decodeOrNull(text)?.let { return it }
        var body = text
        repeat(64) {
            val trimmed = dropIncompleteTail(body)
            decodeOrNull(closeOpen(trimmed))?.let { return it }
            val cut = trimmed.lastIndexOf('\n')
            if (cut <= 0) return null
            body = trimmed.substring(0, cut)
        }
        return null
    }

    private fun decodeTree(text: String): GameState {
        val tree = json.parseToJsonElement(text)
        val stamped = if (tree is JsonObject && "version" !in tree) JsonObject(tree + ("version" to JsonPrimitive(1))) else tree
        return json.decodeFromJsonElement(GameState.serializer(), stamped)
    }

    /** Non-finite numbers cannot ride into the bench. A normal save has none. */
    private fun scrub(state: GameState): GameState {
        fun d(v: Double) = if (v.isFinite()) v else 0.0
        val tune = state.tune
        val drag = state.dragTune
        val cleanTune = if (tune.values.all { it.isFinite() }) tune else tune.mapValues { d(it.value) }
        val cleanDrag = if (drag.values.all { it.isFinite() }) drag else drag.mapValues { d(it.value) }
        if (state.money.isFinite() && state.runEarnings.isFinite() && state.researchPoints.isFinite() &&
            state.throttle.isFinite() && state.soakC.isFinite() && cleanTune === tune && cleanDrag === drag
        ) {
            return state
        }
        return state.copy(
            money = d(state.money),
            runEarnings = d(state.runEarnings),
            researchPoints = d(state.researchPoints),
            throttle = d(state.throttle),
            soakC = d(state.soakC),
            tune = cleanTune,
            dragTune = cleanDrag,
        )
    }

    private fun dropIncompleteTail(text: String): String {
        val t = text.trimEnd()
        if (t.isEmpty()) return t
        val nl = t.lastIndexOf('\n')
        val last = if (nl >= 0) t.substring(nl + 1) else t
        return if (lineComplete(last)) t else if (nl >= 0) t.substring(0, nl) else ""
    }

    private fun lineComplete(line: String): Boolean {
        val last = line.trim()
        if (last.isEmpty()) return true
        var inString = false
        var escape = false
        for (c in last) {
            if (inString) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') inString = false
            } else if (c == '"') {
                inString = true
            }
        }
        if (inString || escape) return false
        val end = last.last()
        return end == '{' || end == '[' || end == '}' || end == ']' || end == ',' || end == '"' ||
            end.isDigit() || last.endsWith("true") || last.endsWith("false") || last.endsWith("null")
    }

    /** Closes whatever `{` and `[` the prefix left open, after a trailing comma. */
    private fun closeOpen(text: String): String {
        var s = text.trimEnd()
        if (s.endsWith(",")) s = s.dropLast(1).trimEnd()
        var braces = 0
        var brackets = 0
        var inString = false
        var escape = false
        for (c in s) {
            if (inString) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> braces++
                '}' -> braces--
                '[' -> brackets++
                ']' -> brackets--
            }
        }
        if (inString) s += "\""
        return s + "]".repeat(brackets.coerceAtLeast(0)) + "}".repeat(braces.coerceAtLeast(0))
    }

    /**
     * A version 1 save had one tire for every kind of race. The drag tire stays as it was. The circuit gets the nearest
     * tire of its own for every tire the save had studied, bought or fitted, so nothing is lost and nothing is refunded.
     * Version 4 keeps the chassis the player picked. A bench engine that will not fit it races the shop mule, and the
     * picked car comes back on its own once an engine that fits is on the stand. Nothing is refunded.
     */
    fun migrate(state: GameState): GameState {
        if (state.version >= SAVE_VERSION) return state
        // 2 and 3 added fields whose defaults are the right start, and 4 does not rewrite the chassis.
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
    private val bak = File(dir, "revline_save.json.bak")
    private val bad = File(dir, "revline_save.json.bad")
    private val tmp = File(dir, "revline_save.json.tmp")

    /**
     * The best save that will open. A whole file wins. A file cut off mid-write
     * keeps whatever prefix still reads. The previous good file is next.
     * A damaged file is copied aside and left where it is. Never throws.
     */
    fun load(): GameState {
        val main = readTextOrNull(file)
        if (main != null) {
            SaveCodec.decodeOrNull(main)?.let { return it }
            preserveDamaged()
            val salvaged = SaveCodec.salvage(main)
            if (salvaged != null && hasProgress(salvaged)) return salvaged
        }
        val previous = readTextOrNull(bak)
        if (previous != null) {
            SaveCodec.decodeOrNull(previous)?.let { return it }
            val salvaged = SaveCodec.salvage(previous)
            if (salvaged != null && hasProgress(salvaged)) return salvaged
        }
        val pending = readTextOrNull(tmp)
        if (pending != null) {
            SaveCodec.decodeOrNull(pending)?.let { return it }
            val salvaged = SaveCodec.salvage(pending)
            if (salvaged != null && hasProgress(salvaged)) return salvaged
        }
        if (main != null) SaveCodec.salvage(main)?.let { return it }
        return GameState()
    }

    /**
     * Writes the whole save to a temp file, then moves it onto the live file.
     * The previous live file is renamed to the backup first, so a kill during
     * the move still has a complete save to open. [retainPrevious] keeps that
     * backup. A restart passes false and drops it only after the new file is in place.
     */
    fun save(state: GameState, retainPrevious: Boolean = true) {
        val text = SaveCodec.encode(state)
        dir.mkdirs()
        writeAtomic(tmp, text)
        val current = readTextOrNull(file)
        val currentWhole = current != null && SaveCodec.decodeOrNull(current) != null
        if (current != null && !currentWhole) preserveDamaged()
        if (retainPrevious && currentWhole) moveAtomic(file, bak)
        moveAtomic(tmp, file)
        if (!retainPrevious) bak.delete()
    }

    /** Copies a live file that did not read cleanly. Does not delete it. */
    fun preserveDamaged() {
        if (!file.isFile || file.length() == 0L || bad.exists()) return
        try {
            dir.mkdirs()
            val copy = File(dir, "revline_save.json.bad.copy")
            writeStream(copy, file.readBytes())
            moveAtomic(copy, bad)
        } catch (_: Throwable) {
        }
    }

    private fun hasProgress(state: GameState): Boolean =
        state.money != 0.0 || state.savedAtEpochMs != 0L || state.knowledge != 0 || state.prestiges != 0 ||
            state.upgrades.isNotEmpty() || state.researched.isNotEmpty() || state.runEarnings != 0.0

    private fun readTextOrNull(target: File): String? {
        if (!target.isFile || target.length() == 0L) return null
        return try {
            target.readText()
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeAtomic(target: File, text: String) {
        val scratch = File(target.parentFile, target.name + ".writing")
        writeStream(scratch, text.toByteArray(Charsets.UTF_8))
        moveAtomic(scratch, target)
    }

    private fun writeStream(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            out.write(bytes)
            out.flush()
            try {
                out.fd.sync()
            } catch (_: Throwable) {
            }
        }
    }

    private fun moveAtomic(from: File, to: File) {
        val options = arrayOf(StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        try {
            Files.move(from.toPath(), to.toPath(), *options)
        } catch (_: Throwable) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
