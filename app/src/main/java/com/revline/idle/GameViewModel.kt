package com.revline.idle

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.revline.idle.data.GameState
import com.revline.idle.data.SaveRepository
import com.revline.idle.game.Logic
import com.revline.idle.game.Sheet
import com.revline.idle.model.FuelKind
import java.io.File
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class DynoPhase { IDLE, PULL, RETURN }

data class UiModel(
    val game: GameState,
    val sheet: Sheet,
    val rpm: Double,
    val phase: DynoPhase,
    val offline: String?,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SaveRepository(File(app.filesDir, "revline"))
    private val saveMutex = Mutex()
    var ui by mutableStateOf(blank())
        private set
    private var phase = DynoPhase.IDLE
    private var pullT = 0.0
    private var clock = 0.0
    private var sinceSave = 0.0
    private var rpm = Logic.IDLE_RPM

    init {
        val now = System.currentTimeMillis()
        val (raw, msg) = Logic.applyOffline(repo.load(), now)
        val loaded = Logic.ensureStudies(raw)
        val sheet = Logic.sheet(loaded)
        rpm = Logic.targetRpm(sheet.params.redlineRpm, loaded.throttle)
        ui = UiModel(loaded, sheet, rpm, DynoPhase.IDLE, msg)
        viewModelScope.launch { loop() }
    }

    private fun publish(next: UiModel) {
        ui = next
    }

    private suspend fun loop() {
        var last = System.nanoTime()
        while (viewModelScope.isActive) {
            delay(100)
            val nowNs = System.nanoTime()
            val dt = ((nowNs - last) / 1e9).coerceIn(0.0, 0.5)
            last = nowNs
            clock += dt
            val spun = advanceRpm(dt, ui.sheet.params.redlineRpm, ui.game.throttle)
            val opening = if (phase == DynoPhase.IDLE) ui.game.throttle else 1.0
            val pay = Logic.incomeAt(ui.sheet.params, spun, ui.sheet.stats.reliability, ui.sheet.multiplier, opening)
            val game = Logic.accrue(ui.game, dt, System.currentTimeMillis(), pay, ui.sheet.rpPerSec)
            publish(
                ui.copy(
                    game = game,
                    rpm = spun,
                    phase = phase,
                    sheet = ui.sheet.copy(incomePerSec = pay),
                ),
            )
            sinceSave += dt
            if (sinceSave > 5.0) {
                sinceSave = 0.0
                save()
            }
        }
    }

    private fun advanceRpm(dt: Double, redline: Double, throttle: Double): Double {
        val wobble = 16.0 * sin(clock * 2.4) + 6.0 * sin(clock * 6.1)
        when (phase) {
            DynoPhase.PULL -> {
                pullT += dt
                val u = (pullT / 7.0).coerceIn(0.0, 1.0)
                val e = u * u * (3.0 - 2.0 * u)
                if (pullT >= 7.0) {
                    phase = DynoPhase.RETURN
                    pullT = 0.0
                }
                rpm = Logic.IDLE_RPM + (redline - Logic.IDLE_RPM) * e
            }
            DynoPhase.RETURN -> {
                pullT += dt
                val u = (pullT / 2.2).coerceIn(0.0, 1.0)
                val stop = Logic.targetRpm(redline, throttle)
                if (pullT >= 2.2) {
                    phase = DynoPhase.IDLE
                    pullT = 0.0
                    rpm = stop
                } else {
                    rpm = redline + (stop - redline) * u
                }
            }
            DynoPhase.IDLE -> {
                val target = Logic.targetRpm(redline, throttle) + wobble * 0.35
                val follow = 1.0 - exp(-dt / 0.42)
                rpm += (target - rpm) * follow
            }
        }
        return rpm.coerceIn(680.0, redline + 80.0)
    }

    fun setThrottle(position: Double) {
        if (phase != DynoPhase.IDLE) return
        val next = position.coerceIn(0.0, 1.0)
        if (kotlin.math.abs(next - ui.game.throttle) < 0.002) return
        publish(ui.copy(game = ui.game.copy(throttle = next)))
    }

    fun startDyno() {
        if (phase != DynoPhase.IDLE) return
        val now = System.currentTimeMillis()
        if (now < ui.game.dynoReadyAtEpochMs) return
        val reward = Logic.dynoReward(ui.sheet.stats)
        val game = ui.game.copy(
            researchPoints = ui.game.researchPoints + reward,
            dynoReadyAtEpochMs = now + 35_000L,
        )
        phase = DynoPhase.PULL
        pullT = 0.0
        publish(ui.copy(game = game, phase = phase))
    }

    fun dismissOffline() = publish(ui.copy(offline = null))

    fun buy(id: String) = mutate { Logic.buyUpgrade(it, id) }
    fun research(id: String) = mutate { Logic.buyResearch(it, id) }
    fun perk(id: String) = mutate { Logic.buyPerk(it, id) }
    fun fuel(kind: FuelKind) = mutate { Logic.selectFuel(it, kind) }
    fun prestige() = mutate { Logic.prestige(it) }

    private fun mutate(block: (GameState) -> GameState) {
        val game = block(ui.game)
        if (game == ui.game) return
        publish(ui.copy(game = game, sheet = Logic.sheet(game)))
        save()
    }

    private fun save() {
        val snapshot = ui.game
        viewModelScope.launch {
            saveMutex.withLock {
                withContext(Dispatchers.IO) { repo.save(snapshot) }
            }
        }
    }

    override fun onCleared() {
        repo.save(ui.game.copy(savedAtEpochMs = System.currentTimeMillis()))
        super.onCleared()
    }

    private fun blank(): UiModel {
        val game = GameState()
        return UiModel(game, Logic.sheet(game), 820.0, DynoPhase.IDLE, null)
    }
}
