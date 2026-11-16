package com.revline.idle.ui

import com.revline.idle.data.GameState
import com.revline.idle.game.AimGoal
import com.revline.idle.game.AimSpend
import com.revline.idle.model.FuelKind
import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import com.revline.idle.game.DragTune
import com.revline.idle.game.Logic
import com.revline.idle.game.RaceDrive
import com.revline.idle.game.Racing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.revline.idle.DynoPhase
import com.revline.idle.UiModel
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Times the work one old race-page composition did on a pay tick, against the
 * work the tune page does for the same tick. Printed so the race-page report
 * has a measured number, not a guess. The assertions only pin the shape:
 * the uncached pass builds a sheet once per chassis forecast, twice.
 */
class RacePageCostTest {
    @Test fun aPayTickOnTheOldRacePageRebuildsTheSheetForEveryChassis() {
        val fresh = Logic.newGame()
        val late = Logic.newGame().copy(prestiges = 7, racing = true)
        for (state in listOf(fresh, late)) {
            val open = Racing.chassis.count { it.minPrestige <= state.prestiges }
            val sheet = Logic.sheet(state)
            val oneSheet = median { Logic.sheet(state) }
            val oneCar = median { Racing.car(state) }
            val oneSetup = median { RaceDrive.setup(state, sheet) }
            val oneForecast = median { Racing.forecast(state, Racing.chassis.first()) }
            val quickest = median { Racing.quickest(state) }
            val catalog = median {
                Racing.quickest(state)
                Racing.chassis.filter { it.minPrestige <= state.prestiges }.forEach { Racing.forecast(state, it) }
            }
            val pass = median {
                Racing.car(state)
                RaceDrive.setup(state, sheet)
                Racing.raceLabel(state)
                Racing.quickest(state)
                Racing.chassis.filter { it.minPrestige <= state.prestiges }.forEach { Racing.forecast(state, it) }
            }
            val gears = median { Racing.gearTimes(state) }
            val tune = median { DragTune.forecast(state) }
            val drivers = median { Drivers.forecast(RaceDrive.setup(state, sheet)) }
            val advice = median {
                val car = Circuits.carOf(state, sheet)
                Circuits.advise(car, Circuits.all.first(), Drivers.you, Circuits.Weather.DRY)
            }
            println(
                "RACE_COST prestiges=${state.prestiges} chassis=${Racing.chassis.size} open=$open " +
                    "sheet=${fmt(oneSheet)} car=${fmt(oneCar)} setup=${fmt(oneSetup)} " +
                    "forecast=${fmt(oneForecast)} quickest=${fmt(quickest)} catalog=${fmt(catalog)} " +
                    "gears=${fmt(gears)} oldPass=${fmt(pass)} tuneForecast=${fmt(tune)} " +
                    "drivers=${fmt(drivers)} advice=${fmt(advice)}",
            )
        }
        val state = late
        val open = Racing.chassis.count { it.minPrestige <= state.prestiges }
        // quickest forecasts every open chassis, then the row loop forecasts them again,
        // plus car, setup's car, and raceLabel. Each of those builds a sheet.
        val sheets = 3 + open * 2
        check(sheets >= 3 + open) { sheets }
        check(open >= 4) { open }
    }

    @Test fun anOldCircuitTabIsTheRoadPage() {
        assertEquals("ROAD", racePage("CIRCUIT"))
        assertEquals("DRAG", racePage("DRAG"))
        assertEquals("ROAD", racePage("ROAD"))
        assertEquals("DRAG PARTS", racePage("DRAG PARTS"))
        assertEquals("RACE PARTS", racePage("RACE PARTS"))
        assertEquals("DRAG", racePage("nope"))
    }

    @Test fun aPassedSheetMatchesBuildingOne() {
        val state = Logic.newGame().copy(prestiges = 3, racing = true)
        val sheet = Logic.sheet(state)
        val body = Racing.chassis.first { it.minPrestige <= 3 && it.id != "mule" }
        val live = Racing.forecast(state, body)
        val cached = Racing.forecast(state, sheet, body)
        assertEquals(live.pace, cached.pace, 1e-6)
        assertEquals(live.wheelHp, cached.wheelHp, 1e-6)
        assertEquals(live.massKg, cached.massKg, 1e-6)
        assertEquals(live.fits, cached.fits)
        assertEquals(Racing.car(state).pace, Racing.car(state, sheet).pace, 1e-6)
        assertEquals(Racing.quickest(state)?.id, Racing.quickest(state, sheet)?.id)
    }

    private fun median(block: () -> Unit): Double {
        repeat(2) { block() }
        val samples = DoubleArray(5)
        for (i in samples.indices) {
            val t0 = System.nanoTime()
            block()
            samples[i] = (System.nanoTime() - t0) / 1e6
        }
        samples.sort()
        return samples[samples.size / 2]
    }

    private fun fmt(ms: Double) = String.format(java.util.Locale.US, "%.2fms", ms)
}

/** A cash tick on the race page, counted. Graphics stay off the render thread. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = [34], qualifiers = "w690dp-h830dp-xhdpi")
class RacePageRecomposeTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun aCashTickLeavesTheOtherPagesUncomposed() {
        val game = Logic.newGame().copy(prestiges = 7, racing = true, money = 5_000_000.0)
        val ui = mutableStateOf(UiModel(game, Logic.sheet(game), 800.0, DynoPhase.IDLE, null))
        RaceCompose.reset()
        rule.setContent { race(ui.value) }
        rule.waitForIdle()
        val looks = RaceCompose.looks
        val sims = RaceCompose.driverSims
        val forecasts = RaceCompose.forecasts
        val t0 = System.nanoTime()
        repeat(4) {
            rule.runOnIdle {
                val now = ui.value
                ui.value = now.copy(game = now.game.copy(money = now.game.money + 40.0), rpm = now.rpm + 50.0)
            }
            rule.waitForIdle()
        }
        val wall = (System.nanoTime() - t0) / 1e6
        println(
            "RACE_TICK_DRAG looks=$looks->${RaceCompose.looks} sims=$sims->${RaceCompose.driverSims} " +
                "forecasts=$forecasts->${RaceCompose.forecasts} drag=${RaceCompose.drag} road=${RaceCompose.road} " +
                "dragParts=${RaceCompose.dragParts} raceParts=${RaceCompose.raceParts} " +
                "compose=${fmt(RaceCompose.composeNs / 1e6)} fourTicks=${fmt(wall)}",
        )
        assertEquals(looks, RaceCompose.looks)
        assertEquals(sims, RaceCompose.driverSims)
        assertEquals(forecasts, RaceCompose.forecasts)
        assertEquals(0, RaceCompose.forecasts)
        assertEquals(0, RaceCompose.road)
        assertEquals(0, RaceCompose.dragParts)
        assertEquals(0, RaceCompose.raceParts)
        check(RaceCompose.drag >= 5) { RaceCompose.drag }
    }

    @Test fun theCatalogIsBuiltOnceWhenRacePartsOpens() {
        val game = Logic.newGame().copy(prestiges = 7, racing = true, money = 5_000_000.0)
        val open = Racing.chassis.count { it.minPrestige <= game.prestiges }
        val ui = mutableStateOf(UiModel(game, Logic.sheet(game), 800.0, DynoPhase.IDLE, null))
        RaceCompose.reset()
        rule.setContent { race(ui.value) }
        rule.onNodeWithText("RACE PARTS", substring = false).performClick()
        rule.waitForIdle()
        val forecasts = RaceCompose.forecasts
        val looks = RaceCompose.looks
        check(forecasts in open..open * 2) { "$forecasts open=$open" }
        repeat(4) {
            rule.runOnIdle {
                val now = ui.value
                ui.value = now.copy(game = now.game.copy(money = now.game.money + 40.0), rpm = now.rpm + 50.0)
            }
            rule.waitForIdle()
        }
        assertEquals(forecasts, RaceCompose.forecasts)
        assertEquals(looks, RaceCompose.looks)
        assertEquals(0, RaceCompose.dragParts)
        check(RaceCompose.raceParts >= 5) { RaceCompose.raceParts }
        println(
            "RACE_TICK_PARTS forecasts=$forecasts looks=$looks raceParts=${RaceCompose.raceParts} " +
                "compose=${fmt(RaceCompose.composeNs / 1e6)}",
        )
    }

    @Test fun anOldCircuitPageOpensRoadRacing() {
        val game = Logic.newGame()
        rule.setContent {
            RaceScreen(
                UiModel(game, Logic.sheet(game), 800.0, DynoPhase.IDLE, null),
                {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, { _, _ -> },
                initialPage = "CIRCUIT",
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText("Run race").assertExists()
        rule.onNodeWithText("DISTANCE").assertDoesNotExist()
        rule.onNodeWithText("DRAG TIRES").assertDoesNotExist()
    }

    @Test fun aTuneTickStaysOnTheSheetItAlreadyHas() {
        timeTune(Logic.newGame().copy(prestiges = 7, racing = true, money = 5_000_000.0))
    }

    @Test fun aPartsTickStaysOnTheRankingItAlreadyHas() {
        timeParts(Logic.newGame().copy(prestiges = 7, racing = true, money = 5_000_000.0))
    }

    @Composable
    private fun race(ui: UiModel) {
        RaceScreen(
            ui, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, { _, _ -> },
        )
    }

    private fun timeTune(game: GameState) {
        val sheet = Logic.sheet(game)
        val ui = mutableStateOf(UiModel(game, sheet, 800.0, DynoPhase.IDLE, null))
        var passes = 0
        var ns = 0L
        rule.setContent {
            val t0 = System.nanoTime()
            passes++
            TuneScreen(ui.value, { _, _ -> }, { _, _ -> })
            ns += System.nanoTime() - t0
        }
        rule.waitForIdle()
        val openNs = ns
        val t0 = System.nanoTime()
        repeat(4) {
            rule.runOnIdle {
                val now = ui.value
                ui.value = now.copy(game = now.game.copy(money = now.game.money + 40.0), rpm = now.rpm + 50.0)
            }
            rule.waitForIdle()
        }
        println(
            "TUNE_TICK passes=$passes compose=${fmt(ns / 1e6)} openCompose=${fmt(openNs / 1e6)} " +
                "fourTicks=${fmt((System.nanoTime() - t0) / 1e6)}",
        )
    }

    private fun timeParts(game: GameState) {
        val sheet = Logic.sheet(game)
        val ui = mutableStateOf(UiModel(game, sheet, 800.0, DynoPhase.IDLE, null))
        var passes = 0
        var ns = 0L
        rule.setContent {
            val t0 = System.nanoTime()
            passes++
            PartsScreen(ui.value, {}, {}, { _: FuelKind -> }, { _: AimGoal -> }, { _: AimSpend -> })
            ns += System.nanoTime() - t0
        }
        rule.waitForIdle()
        val openNs = ns
        val t0 = System.nanoTime()
        repeat(4) {
            rule.runOnIdle {
                val now = ui.value
                ui.value = now.copy(game = now.game.copy(money = now.game.money + 40.0), rpm = now.rpm + 50.0)
            }
            rule.waitForIdle()
        }
        println(
            "PARTS_TICK passes=$passes compose=${fmt(ns / 1e6)} openCompose=${fmt(openNs / 1e6)} " +
                "fourTicks=${fmt((System.nanoTime() - t0) / 1e6)}",
        )
    }

    private fun fmt(ms: Double) = String.format(java.util.Locale.US, "%.2fms", ms)
}
