package com.revline.idle.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.revline.idle.DynoPhase
import com.revline.idle.UiModel
import com.revline.idle.economy.DriverBuilds
import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import com.revline.idle.game.Logic
import com.revline.idle.game.Racing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drag and Circuit are separate pages. These tests drive the real RaceScreen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w690dp-h830dp-xhdpi")
class RacePagesTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun dragAndCircuitShowOnlyTheirOwnControls() {
        val game = Logic.newGame()
        rule.setContent {
            RaceScreen(
                UiModel(game, Logic.sheet(game), 800.0, DynoPhase.IDLE, null, raceNote = "The till is short."),
                {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, { _, _ -> },
            )
        }
        rule.onNodeWithText("DISTANCE").assertIsDisplayed()
        rule.onNodeWithText("DRAG TIRES").assertExists()
        rule.onNodeWithText("Run race").assertDoesNotExist()
        rule.onNodeWithText("CIRCUIT TIRES").assertDoesNotExist()
        rule.onNodeWithText("CIRCUIT", substring = false).assertIsEnabled().performClick()
        rule.onNodeWithText("Run race").assertExists()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("Run race"))
        rule.onNodeWithText("Run race").assertIsDisplayed()
        rule.onNodeWithText("CIRCUIT TIRES").assertExists()
        rule.onNodeWithText("DISTANCE").assertDoesNotExist()
        rule.onNodeWithText("DRAG TIRES").assertDoesNotExist()
        rule.onNodeWithText("The till is short.").assertExists()
        rule.onNodeWithText("on the Drag page", substring = true).assertExists()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("DRAG"))
        rule.onNodeWithText("DRAG", substring = false).performClick()
        rule.onNodeWithText("The till is short.").assertIsDisplayed()
        rule.onNodeWithText("on the Circuit page", substring = true).assertExists()
    }

    @Test fun scrollOffsetsStayIndependent() {
        show()
        val page = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        rule.onNode(page).performScrollToNode(hasText("A duct lets heat out", substring = true))
        rule.onNodeWithText("A duct lets heat out", substring = true).assertIsDisplayed()
        rule.onNodeWithText("DISTANCE").assertIsNotDisplayed()
        rule.onNodeWithText("CIRCUIT", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("A few laps round a real circuit", substring = true).assertIsDisplayed()
        rule.onNodeWithText("A duct lets heat out", substring = true).assertIsNotDisplayed()
        rule.onNode(page).performScrollToNode(hasText("A duct lets heat out", substring = true))
        rule.onNodeWithText("A duct lets heat out", substring = true).assertIsDisplayed()
        rule.onNodeWithText("DRAG", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("A duct lets heat out", substring = true).assertIsDisplayed()
        rule.onNodeWithText("DISTANCE").assertIsNotDisplayed()
        rule.onNodeWithText("CIRCUIT", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("A duct lets heat out", substring = true).assertIsDisplayed()
    }

    @Test fun circuitPickSurvivesTheOtherPageAndRotation() {
        val tester = StateRestorationTester(rule)
        val shown = androidx.compose.runtime.mutableStateOf(true)
        tester.setContent { if (shown.value) screen() }
        rule.onNodeWithText("CIRCUIT", substring = false).performClick()
        rule.onNodeWithText("MONACO").performClick()
        rule.onNodeWithText("Free race.", substring = true).assertExists()
        rule.onNodeWithText("The lap the car and driver have.", substring = true).assertExists()
        rule.onNodeWithText("PUSH").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Quicker every lap.", substring = true).assertExists()
        rule.onNodeWithText("DRAG", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("CIRCUIT", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Free race.", substring = true).assertExists()
        rule.onNodeWithText("Quicker every lap.", substring = true).assertExists()
        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Free race.", substring = true).assertExists()
        rule.onNodeWithText("Quicker every lap.", substring = true).assertExists()
        rule.onNodeWithText("DISTANCE").assertDoesNotExist()
        rule.runOnIdle { shown.value = false }
        rule.waitForIdle()
        rule.runOnIdle { shown.value = true }
        rule.waitForIdle()
        rule.onNodeWithText("DISTANCE").assertIsDisplayed()
        rule.onNodeWithText("Free race.", substring = true).assertDoesNotExist()
    }

    @Test fun offscreenResultOpensOneReplayAndMovesTheVenueOnce() {
        val game = Logic.newGame().copy(devMode = true, hiredDrivers = setOf("dusty"))
        val ui = androidx.compose.runtime.mutableStateOf(UiModel(game, Logic.sheet(game), 800.0, DynoPhase.IDLE, null))
        var runs = 0
        var revises = 0
        rule.setContent {
            RaceScreen(
                ui.value, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {},
                { _, _, _ -> runs++ }, { _, _ -> revises++ },
            )
        }
        val page = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        rule.onNodeWithText("CIRCUIT", substring = false).performClick()
        rule.onNodeWithText("MONACO").performClick()
        rule.onNodeWithText("Free race.", substring = true).assertExists()
        rule.onNode(page).performScrollToNode(hasText("Run race"))
        rule.onNodeWithText("Run race").performClick()
        assertEquals(1, runs)
        rule.onNodeWithText("DRAG", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.runOnIdle { ui.value = ui.value.copy(circuitBusy = true) }
        rule.onNodeWithText("CIRCUIT", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Racing...").assertIsNotEnabled()
        assertEquals(1, runs)
        rule.onNodeWithText("DRAG", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        val outcome = finishedRace()
        check(outcome.result.log.rows.isNotEmpty()) { "fixture log is empty" }
        rule.runOnIdle { ui.value = ui.value.copy(circuitBusy = false, circuitOutcome = outcome) }
        rule.onNodeWithText("Close").assertDoesNotExist()
        assertEquals(0, revises)
        rule.onNodeWithText("CIRCUIT", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Close").assertExists()
        rule.onNodeWithText("CHAMPIONSHIP ROUND", substring = true).assertExists()
        assertEquals(1, runs)
        assertEquals(0, revises)
        rule.onNodeWithText("DRAG", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Close").assertDoesNotExist()
        rule.onNodeWithText("CIRCUIT", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Close").assertExists()
        rule.onNodeWithText("Close").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Close").assertDoesNotExist()
        rule.onNode(page).performScrollToNode(hasText("MONACO"))
        rule.onNodeWithText("MONACO").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Free race.", substring = true).assertExists()
        rule.onNodeWithText("DRAG", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("CIRCUIT", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Free race.", substring = true).assertExists()
        rule.onNodeWithText("Close").assertDoesNotExist()
        rule.onNode(page).performScrollToNode(hasText("Watch the race"))
        rule.onNodeWithText("Watch the race").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Close").assertExists()
        assertEquals(1, runs)
        assertEquals(0, revises)
    }

    private fun show() = rule.setContent { screen() }

    @androidx.compose.runtime.Composable
    private fun screen() {
        val game = Logic.newGame()
        RaceScreen(
            UiModel(game, Logic.sheet(game), 800.0, DynoPhase.IDLE, null, raceNote = "The till is short."),
            {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {},             { _, _, _ -> }, { _, _ -> },
        )
    }

    private fun finishedRace(): Circuits.Outcome {
        val c = Circuits.all.first { it.id == "spa" }
        val d = Drivers.byId("colette")!!
        val car = Circuits.carOf(DriverBuilds.make(3, Racing.Build.MEDIUM, "race", "close", "lsd", 0.8))
        val result = Circuits.race(Circuits.plan(car, c, d), d, Circuits.parPlan(3, c), 7L)
        return Circuits.Outcome(result, 0.0, 0.0, 0.0, 0.0, "")
    }
}
