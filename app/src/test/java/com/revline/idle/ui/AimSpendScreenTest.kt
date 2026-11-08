package com.revline.idle.ui

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.revline.idle.DynoPhase
import com.revline.idle.UiModel
import com.revline.idle.data.GameState
import com.revline.idle.game.AimSpend
import com.revline.idle.game.Logic
import com.revline.idle.model.Block
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The spend switch on the parts page, at the unfolded Fold size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w690dp-h830dp-xhdpi")
class AimSpendScreenTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun eachSpendModeSelectsAndFitsTheFold() {
        val game = mutableStateOf(sample())
        rule.setContent {
            CompositionLocalProvider(LocalBatterySaver provides true) {
                val current = game.value
                PartsScreen(
                    UiModel(current, Logic.sheet(current), 1800.0, DynoPhase.IDLE, null),
                    onBuy = {},
                    onRemove = {},
                    onFuel = {},
                    onGoal = {},
                    onSpend = { game.value = game.value.copy(aimSpend = it.name) },
                )
            }
        }
        val root = System.getenv("AIM_SPEND_DIR")
        val dir = if (root != null) File(root).also { it.mkdirs() } else null
        for (mode in AimSpend.entries) {
            rule.onNode(hasContentDescription("aim spend ${mode.name}", substring = true))
                .performSemanticsAction(SemanticsActions.OnClick)
            rule.waitForIdle()
            rule.onNode(hasContentDescription("aim spend ${mode.name} on")).assertExists()
            rule.onNodeWithText(mode.label).assertExists()
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            val v = rule.activity.window.decorView
            assertTrue("fold is wider than three quarters of its height", v.width > v.height * 3 / 4)
            if (dir != null) {
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                rule.runOnUiThread { v.draw(android.graphics.Canvas(bmp)) }
                File(dir, "aim-${mode.name.lowercase()}.png").outputStream().use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }

    private fun sample(): GameState = GameState(
        prestiges = Block.INLINE_FOUR.ordinal,
        money = 400.0,
        aimGoal = "POWER",
        aimSpend = "CHEAPEST",
        batterySaver = true,
        throttle = 0.55,
    )
}
