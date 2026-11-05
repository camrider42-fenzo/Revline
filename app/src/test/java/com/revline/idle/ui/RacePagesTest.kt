package com.revline.idle.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.revline.idle.DynoPhase
import com.revline.idle.UiModel
import com.revline.idle.game.Logic
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

    private fun show() = rule.setContent { screen() }

    @androidx.compose.runtime.Composable
    private fun screen() {
        val game = Logic.newGame()
        RaceScreen(
            UiModel(game, Logic.sheet(game), 800.0, DynoPhase.IDLE, null, raceNote = "The till is short."),
            {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, { _, _ -> },
        )
    }
}
