package com.revline.idle.data

import com.revline.idle.model.FuelKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class GameState(
    val money: Double = 0.0,
    val runEarnings: Double = 0.0,
    val researchPoints: Double = 0.0,
    val knowledge: Int = 0,
    val prestiges: Int = 0,
    val upgrades: Map<String, Int> = emptyMap(),
    val researched: Set<String> = emptySet(),
    val perks: Map<String, Int> = emptyMap(),
    val fuel: FuelKind = FuelKind.PUMP_87,
    /** Dyno throttle stop, 0 at idle and 1 at redline. The contract is output and economy; this stop only leans it. */
    val throttle: Double = 0.18,
    /** Screw positions. Missing keys are the stock stop. Locked screws are ignored. */
    val tune: Map<String, Double> = emptyMap(),
    /** Testing switch: parts and studies cost nothing. Gates still apply. */
    val devMode: Boolean = false,
    /** Epoch ms until which a blown engine pays no contract. */
    val downUntilEpochMs: Long = 0L,
    val breakdowns: Int = 0,
    val lastFailure: String = "",
    val savedAtEpochMs: Long = 0L,
    val dynoReadyAtEpochMs: Long = 0L,
    val version: Int = 1,
)

object SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(state: GameState): String = json.encodeToString(state)

    fun decode(text: String): GameState = json.decodeFromString(text)
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
