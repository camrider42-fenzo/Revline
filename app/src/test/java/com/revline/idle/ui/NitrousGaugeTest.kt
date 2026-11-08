package com.revline.idle.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.revline.idle.DynoPhase
import com.revline.idle.UiModel
import com.revline.idle.game.Logic
import com.revline.idle.game.RaceDrive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The drag bottle lamp, and that the circuit page does not carry it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = [34], qualifiers = "w690dp-h830dp-xhdpi")
class NitrousGaugeTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun eachPhaseHasItsOwnLamp() {
        val cases = listOf(
            RaceDrive.Bottle(RaceDrive.BottlePhase.OFF, 0.0, 0.0) to "NO KIT",
            RaceDrive.Bottle(RaceDrive.BottlePhase.ARMED, 1.0, 7.0) to "ARMED",
            RaceDrive.Bottle(RaceDrive.BottlePhase.ACTIVE, 0.5, 3.5) to "ACTIVE",
            RaceDrive.Bottle(RaceDrive.BottlePhase.EMPTY, 0.0, 0.0) to "EMPTY",
        )
        val shown = mutableStateOf(cases.first().first)
        rule.setContent { NitrousGauge(shown.value, Modifier.width(72.dp).height(96.dp)) }
        for ((bottle, word) in cases) {
            shown.value = bottle
            rule.waitForIdle()
            rule.onNodeWithText(word).assertIsDisplayed()
            rule.onNode(hasContentDescription("nitrous ${bottle.phase.name}", substring = true)).assertIsDisplayed()
        }
    }

    @Test fun aBareDragPageGreysTheBottleAndTheCircuitPageHidesIt() {
        show(Logic.newGame())
        rule.onNodeWithText("NO KIT").assertIsDisplayed()
        rule.onNodeWithText("CIRCUIT", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("NO KIT").assertDoesNotExist()
        rule.onNodeWithText("ARMED").assertDoesNotExist()
    }

    @Test fun aFittedDragPageShowsAFullBottleAndTheCircuitPageHidesIt() {
        val fitted = Logic.newGame().copy(prestiges = 6, kit = mapOf("nos" to 1))
        show(fitted)
        rule.onNodeWithText("ARMED").assertIsDisplayed()
        rule.onNodeWithText("7.0s").assertIsDisplayed()
        rule.onNode(hasContentDescription("nitrous ARMED 1.00")).assertIsDisplayed()
        rule.onNodeWithText("CIRCUIT", substring = false).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("ARMED").assertDoesNotExist()
        rule.onNodeWithText("7.0s").assertDoesNotExist()
        assertEquals(7.0, RaceDrive.setup(fitted, Logic.sheet(fitted)).bottleS, 1e-6)
    }

    private fun show(game: com.revline.idle.data.GameState) {
        rule.setContent {
            RaceScreen(
                UiModel(game, Logic.sheet(game), 800.0, DynoPhase.IDLE, null),
                {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, { _, _ -> },
            )
        }
    }
}
