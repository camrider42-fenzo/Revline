package com.revline.idle

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.revline.idle.data.GameState
import com.revline.idle.data.SaveRepository
import com.revline.idle.economy.formatDollars
import com.revline.idle.game.AimGoal
import com.revline.idle.game.Drivers.Driver
import com.revline.idle.game.Drivers
import com.revline.idle.game.Logic
import com.revline.idle.game.RaceDrive
import com.revline.idle.game.Circuits
import com.revline.idle.game.Racing
import com.revline.idle.game.Tuners
import com.revline.idle.game.Sheet
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
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

data class Strip(
    val setup: RaceDrive.Setup,
    val field: Racing.Field,
    val yourPace: Double,
    val ladder: Boolean,
    /** Who is at the wheel for this run. Null is the player. */
    val driver: Driver? = null,
    /** What he was paid when the lights came up. */
    val fee: Double = 0.0,
    val seed: Long = 0L,
)

data class UiModel(
    val game: GameState,
    val sheet: Sheet,
    val rpm: Double,
    val phase: DynoPhase,
    val offline: String?,
    val raceNote: String? = null,
    val strip: Strip? = null,
    /** True while the crank is on the fuel cut, on the bench or on a dyno pull. */
    val limiter: Boolean = false,
    /** The last circuit race, shown on the race screen until the next one starts. */
    val circuitOutcome: Circuits.Outcome? = null,
    /** True while a circuit race is being simulated. */
    val circuitBusy: Boolean = false,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SaveRepository(File(app.filesDir, "revline"))
    private val saveMutex = Mutex()
    var ui by mutableStateOf(blank())
        private set
    private var phase = DynoPhase.IDLE
    private var pullT = 0.0
    private var onLimiter = false
    private var clock = 0.0
    private var sinceSave = 0.0
    private var rpm = Logic.IDLE_RPM

    init {
        val now = System.currentTimeMillis()
        val (loaded, msg) = Logic.applyOffline(openOrNew(), now)
        val sheet = Logic.sheet(loaded)
        rpm = Logic.targetRpm(sheet.params.redlineRpm, loaded.throttle, sheet.params.idleRpm)
        ui = UiModel(loaded, sheet, rpm, DynoPhase.IDLE, msg)
        viewModelScope.launch { loop() }
    }

    private fun publish(next: UiModel) {
        ui = next
    }

    private suspend fun loop() {
        var last = System.nanoTime()
        while (viewModelScope.isActive) {
            val saver = ui.game.batterySaver
            delay(if (saver) 1000 else 200)
            val nowNs = System.nanoTime()
            val dt = ((nowNs - last) / 1e9).coerceIn(0.0, if (saver) 1.25 else 0.5)
            last = nowNs
            clock += dt
            val spun = advanceRpm(dt, ui.sheet.params.redlineRpm, ui.game.throttle, ui.sheet.params.idleRpm)
            val opening = if (phase == DynoPhase.IDLE) ui.game.throttle else 1.0
            val block = Block.forPrestige(ui.game.prestiges)
            val soakRate = EngineSim.soakPer10s(ui.sheet.params, spun, opening)
            val soaked = Logic.stepSoak(ui.game.soakC, soakRate, dt)
            val pay = Logic.incomeAt(
                ui.sheet.params,
                spun,
                ui.sheet.stats.reliability,
                ui.sheet.multiplier,
                opening,
                ui.sheet.stats,
                soaked,
            ) * block.payScale()
            val nowMs = System.currentTimeMillis()
            val down = nowMs < ui.game.downUntilEpochMs
            var game = Logic.accrue(ui.game.copy(soakC = soaked), dt, nowMs, if (down) 0.0 else pay, ui.sheet.rpPerSec)
            if (!down) {
                val hazard = Logic.failurePerSec(ui.game, ui.sheet.stats, opening, spun)
                val chance = 1.0 - exp(-hazard * dt)
                if (chance > 0.0 && kotlin.random.Random.nextDouble() < chance) {
                    game = Logic.breakDown(game, nowMs, opening, spun)
                }
            }
            publish(
                ui.copy(
                    game = game,
                    rpm = spun,
                    phase = phase,
                    limiter = onLimiter,
                    sheet = ui.sheet.copy(incomePerSec = if (nowMs < game.downUntilEpochMs) 0.0 else pay),
                ),
            )
            sinceSave += dt
            if (sinceSave > 5.0) {
                sinceSave = 0.0
                save()
            }
        }
    }

    private fun advanceRpm(dt: Double, redline: Double, throttle: Double, idle: Double): Double {
        val wobble = if (ui.game.batterySaver) 0.0 else 16.0 * sin(clock * 2.4) + 6.0 * sin(clock * 6.1)
        val floor = idle.coerceIn(500.0, redline - 300.0)
        onLimiter = false
        when (phase) {
            DynoPhase.PULL -> {
                pullT += dt
                val climb = 5.5
                val hold = 7.3
                if (pullT < climb) {
                    val u = (pullT / climb).coerceIn(0.0, 1.0)
                    val e = u * u * (3.0 - 2.0 * u)
                    rpm = floor + (redline - floor) * e
                } else if (pullT < hold) {
                    rpm = redline
                    onLimiter = true
                } else {
                    phase = DynoPhase.RETURN
                    pullT = 0.0
                    rpm = redline
                }
            }
            DynoPhase.RETURN -> {
                pullT += dt
                val u = (pullT / 2.2).coerceIn(0.0, 1.0)
                val stop = Logic.targetRpm(redline, throttle, floor)
                if (pullT >= 2.2) {
                    phase = DynoPhase.IDLE
                    pullT = 0.0
                    rpm = stop
                } else {
                    rpm = redline + (stop - redline) * u
                }
            }
            DynoPhase.IDLE -> {
                val target = if (throttle <= 0.0) {
                    0.0
                } else {
                    Logic.targetRpm(redline, throttle, floor) + wobble * 0.35
                }
                val follow = 1.0 - exp(-dt / if (throttle <= 0.0) 0.55 else 0.42)
                rpm += (target - rpm) * follow
                if (throttle >= 0.97 && rpm > redline - 180.0) {
                    rpm = redline
                    onLimiter = true
                }
            }
        }
        return rpm.coerceIn(0.0, redline + 40.0)
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
        val reward = Logic.dynoReward(ui.sheet.rpPerSec, ui.sheet.params.dynoFactor)
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
    fun remove(id: String) = mutate { Logic.removeUpgrade(it, id) }
    fun research(id: String) = mutate { Logic.buyResearch(it, id) }
    fun perk(id: String) = mutate { Logic.buyPerk(it, id) }
    fun fuel(kind: FuelKind) = mutate { Logic.selectFuel(it, kind) }
    fun nudgeTune(id: String, direction: Int) = mutate { Tuners.nudge(it, id, direction) }
    fun prestige(racing: Boolean) = mutate { Logic.prestige(it, racing) }
    fun toggleDevMode() = mutate { it.copy(devMode = !it.devMode) }
    fun toggleBatterySaver() = mutate { it.copy(batterySaver = !it.batterySaver) }
    fun setAimGoal(goal: AimGoal) = mutate { it.copy(aimGoal = goal.name) }
    fun studyRace(id: String) = race {
        val next = Racing.study(it, id)
        next to if (next != it) "Studied ${Racing.studies.first { s -> s.id == id }.name}. Tap again to fit it." else Racing.studyBlocked(it, id)
    }
    fun fitChassis(id: String) = race { Racing.fitChassis(it, id) to null }
    fun fitRace(kind: String, id: String) = race {
        val next = Racing.fitPart(it, kind, id)
        next to if (next != it) "Fitted." else Racing.partBlocked(it, kind, id)
    }
    fun setCooling(slots: Int) = race { Racing.setSlots(it, slots) to null }
    fun setFinal(id: String) = race {
        val next = Racing.setFinal(it, id)
        next to if (next != it) "Fitted." else Racing.partBlocked(it, "final", id)
    }
    fun setLength(id: String) = race { Racing.setLength(it, id) to null }

    fun openQuick(tier: Racing.Tier) = openStrip(ladder = false) { Racing.fieldForQuick(it, tier) to it }

    fun openLadder() = openStrip(ladder = true) { Racing.fieldForLadder(it) to it }

    fun openStake() {
        if (ui.strip != null) return
        val opened = Racing.openStake(ui.game, System.nanoTime())
        val hand = opened?.let { (g, f) -> Drivers.charge(g, f) }
        if (opened != null && hand == null) {
            val who = Drivers.selected(ui.game)
            publish(ui.copy(raceNote = "${who?.name ?: "The driver"} wants his fee on top of the stake. The till is short."))
            return
        }
        if (opened == null) {
            val cost = Racing.stakeCost(ui.game.prestiges)
            publish(ui.copy(raceNote = "The stake is ${formatDollars(cost)}. The till is short."))
            return
        }
        val field = opened.second
        val game = hand!!.state
        publish(
            ui.copy(
                game = game,
                sheet = Logic.sheet(game),
                strip = Strip(RaceDrive.setup(game), field, Racing.car(game).pace, ladder = false, driver = hand.driver, fee = hand.fee, seed = System.nanoTime()),
                raceNote = null,
            ),
        )
        save()
    }

    fun finishMeet(won: Boolean, time: Double, rival: Double, jumped: Boolean = false) {
        val strip = ui.strip ?: return
        val note = Racing.stripNote(ui.game, strip.field, won && !jumped, time, rival, strip.ladder, jumped, strip.driver?.name, strip.fee)
        val game = Racing.award(ui.game, strip.field, won && !jumped, strip.ladder)
        val sheet = if (game == ui.game) ui.sheet else Logic.sheet(game)
        publish(ui.copy(game = game, sheet = sheet, strip = null, raceNote = note))
        save()
    }

    private fun openStrip(ladder: Boolean, fieldOf: (GameState) -> Pair<Racing.Field, GameState>) {
        if (ui.strip != null) return
        val (field, before) = fieldOf(ui.game)
        val hand = Drivers.charge(before, field)
        if (hand == null) {
            val who = Drivers.selected(before)
            val fee = who?.let { Drivers.fee(before, it, field) } ?: 0.0
            publish(ui.copy(raceNote = "${who?.name ?: "The driver"} wants ${formatDollars(fee)} to drive this one. The till is short. Drive yourself or pick someone cheaper."))
            return
        }
        val game = hand.state
        publish(
            ui.copy(
                game = game,
                sheet = if (game == ui.game) ui.sheet else Logic.sheet(game),
                strip = Strip(RaceDrive.setup(game), field, Racing.car(game).pace, ladder, hand.driver, hand.fee, System.nanoTime()),
                raceNote = null,
            ),
        )
        if (hand.fee > 0.0) save()
    }

    /** Pays the entry and the driver, simulates the race off the main thread, then pays the prize and any engine rebuild. */
    fun runCircuit(id: String) {
        if (ui.circuitBusy || ui.strip != null) return
        val circuit = Circuits.all.firstOrNull { it.id == id } ?: return
        when (val entry = Circuits.open(ui.game)) {
            is Circuits.Entry.Refused -> publish(ui.copy(raceNote = entry.note))
            is Circuits.Entry.Paid -> {
                val paid = entry.state
                publish(ui.copy(game = paid, circuitBusy = true, circuitOutcome = null, raceNote = null))
                save()
                val seed = System.nanoTime()
                viewModelScope.launch {
                    val outcome = withContext(Dispatchers.Default) {
                        Circuits.finish(paid, Circuits.carOf(paid), circuit, entry.driver, entry.fee, entry.entry, seed)
                    }
                    // The till has moved on since the lights went green: pay onto what is there now.
                    val game = ui.game.let { it.copy(money = (it.money + outcome.prize - outcome.repair).coerceAtLeast(0.0)) }
                    publish(ui.copy(game = game, circuitBusy = false, circuitOutcome = outcome))
                    save()
                }
            }
        }
    }

    fun hireDriver(id: String) = race { state ->
        val driver = Drivers.byId(id)
        val next = Drivers.hire(state, id)
        next to if (driver != null && next != state) "${driver.name} hired. Pick him on the race screen." else null
    }

    /** Blank drives yourself. A driver the till could not pay for the ladder rung is refused. */
    fun pickDriver(id: String) = race { state ->
        val driver = Drivers.byId(id)
        val next = Drivers.select(state, id)
        next to if (driver != null && next == state && id.isNotEmpty() && id !in state.hiredDrivers) "Hire ${driver.name} first." else null
    }

    private fun race(block: (GameState) -> Pair<GameState, String?>) {
        val before = ui.game
        val (game, note) = block(before)
        val sheet = if (game == before) ui.sheet else Logic.sheet(game)
        publish(ui.copy(game = game, sheet = sheet, raceNote = note ?: ui.raceNote))
        if (game != before) save()
    }

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

    private fun openOrNew(): GameState {
        val saved = repo.load()
        val untouched = saved.savedAtEpochMs == 0L &&
            saved.money == 0.0 &&
            saved.prestiges == 0 &&
            saved.knowledge == 0 &&
            saved.upgrades.isEmpty() &&
            saved.researched.isEmpty()
        return if (untouched) Logic.newGame() else saved
    }

    private fun blank(): UiModel {
        val game = Logic.newGame()
        return UiModel(game, Logic.sheet(game), 820.0, DynoPhase.IDLE, null)
    }
}
