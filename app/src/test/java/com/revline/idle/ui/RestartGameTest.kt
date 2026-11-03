package com.revline.idle.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.revline.idle.GameViewModel
import com.revline.idle.data.GameState
import com.revline.idle.data.SaveRepository
import com.revline.idle.game.Logic
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestartGameTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun resetGivesABrandNewGameAndClearsEverythingSaved() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(app.filesDir, "revline")
        SaveRepository(dir).save(GameState(money = 98765.0, knowledge = 7, prestiges = 3, devMode = true, researched = setOf("premium"), upgrades = mapOf("filter" to 2)))
        File(dir, "revline_save.json.tmp").writeText("half a save")
        assertTrue(dir.exists())
        val vm = GameViewModel(app)
        assertEquals(7, vm.ui.game.knowledge)
        assertTrue(vm.ui.game.devMode)

        vm.resetGame()

        assertEquals(Logic.newGame(), vm.ui.game)
        assertEquals(Logic.sheet(Logic.newGame()), vm.ui.sheet)
        assertEquals(null, vm.ui.circuitOutcome)
        assertFalse("the save folder is gone", dir.exists())
        assertEquals(GameState(), SaveRepository(dir).load())
        // Relaunching now finds nothing to load and opens a new game too.
        assertEquals(Logic.newGame().money, GameViewModel(app).ui.game.money, 0.0)
        // The new game plays on: a change after the reset is a change to the fresh game.
        vm.toggleDevMode()
        assertNotEquals(Logic.newGame(), vm.ui.game)
        assertEquals(0, vm.ui.game.knowledge)
    }

    @Test fun cancelDoesNothingAndResetAsksForConfirmationFirst() {
        var resets = 0
        rule.setContent { RestartGame { resets++ } }
        rule.onNodeWithText("Restart game").performClick()
        rule.onNodeWithText("Restart game?").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(0, resets)
        rule.onNodeWithText("Restart game?").assertDoesNotExist()
        rule.onNodeWithText("Restart game").performClick()
        rule.onNodeWithText("Reset").performClick()
        assertEquals(1, resets)
        rule.onNodeWithText("Restart game?").assertDoesNotExist()
    }
}
