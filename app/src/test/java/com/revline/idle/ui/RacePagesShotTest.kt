package com.revline.idle.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import java.io.File

/** Fold (690×830 dp, 18:21.6) and phone pictures of the two race pages. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w690dp-h830dp-xhdpi")
class RacePagesShotTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun fold() {
        show()
        capture("drag-fold.png")
        open("CIRCUIT")
        capture("circuit-fold.png")
        scrollTo("DRIVERS")
        capture("circuit-fold-shared.png")
        open("DRAG")
        scrollTo("DRIVERS")
        capture("drag-fold-shared.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h731dp-xhdpi")
    fun phone() {
        show()
        capture("drag-phone.png")
        open("CIRCUIT")
        capture("circuit-phone.png")
    }

    private fun show() {
        val game = Logic.newGame()
        rule.setContent {
            RaceScreen(
                UiModel(game, Logic.sheet(game), 800.0, DynoPhase.IDLE, null, raceNote = "The till is short."),
                {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, { _, _ -> },
            )
        }
    }

    private fun open(tab: String) {
        rule.onNodeWithText(tab, substring = false).performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun scrollTo(label: String) {
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(label))
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(android.graphics.Canvas(bmp)) }
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val dir = if (cwd.name == "app") File(cwd, "build/reports/race-pages") else File(cwd, "app/build/reports/race-pages")
        dir.mkdirs()
        File(dir, name).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
