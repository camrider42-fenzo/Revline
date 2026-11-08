package com.revline.idle.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.revline.idle.game.Drivers
import com.revline.idle.game.Logic
import com.revline.idle.game.RaceDrive
import com.revline.idle.game.Racing
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Fold picture of a drag pass with the bottle part-way down. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w690dp-h830dp-xhdpi")
class NitrousMeetShotTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun midRunBottle() {
        val game = Logic.newGame().copy(prestiges = 6, kit = mapOf("nos" to 1), chassis = "seven", tire = "slick")
        val sheet = Logic.sheet(game)
        val setup = RaceDrive.setup(game, sheet).copy(shotAtM = 0.0, peakHp = 480.0, peakTqLbFt = 420.0)
        val field = Racing.fieldForQuick(game, Racing.Tier.CLUB)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            RaceMeet(
                setup, field, { _, _, _, _ -> }, EngineCrank(), sheet.params, false,
                game.chassis, false, field.rivalId, Drivers.roster.first(), 0.0, 3L,
            )
        }
        var fill = ""
        run loop@{
            repeat(100) {
                rule.mainClock.advanceTimeBy(200)
                rule.waitForIdle()
                val nodes = rule.onAllNodes(hasContentDescription("nitrous ACTIVE", substring = true)).fetchSemanticsNodes()
                val desc = nodes.firstOrNull()?.config?.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { emptyList() }
                val text = desc?.firstOrNull().orEmpty()
                if (text.startsWith("nitrous ACTIVE")) {
                    fill = text.substringAfter("ACTIVE ").trim()
                    val level = fill.toDoubleOrNull() ?: 1.0
                    if (level in 0.25..0.8) return@loop
                }
            }
        }
        val level = fill.toDoubleOrNull()
        capture("nitrous-midrun.png")
        assertTrue("bottle never opened, last fill '$fill'", level != null && level in 0.15..0.9)
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(android.graphics.Canvas(bmp)) }
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val dir = if (cwd.name == "app") File(cwd, "build/reports/nitrous") else File(cwd, "app/build/reports/nitrous")
        dir.mkdirs()
        File(dir, name).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
