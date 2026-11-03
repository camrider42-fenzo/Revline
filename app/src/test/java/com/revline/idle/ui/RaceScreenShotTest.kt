package com.revline.idle.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.revline.idle.economy.DriverBuilds
import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import com.revline.idle.game.Racing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real race dialog contents, composed at the unfolded Fold's size (18:21.6, about 18:21.6 portrait, 690 x 830 dp) and captured. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w690dp-h830dp-xhdpi")
class RaceScreenShotTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun raceScreen() {
        val c = Circuits.all.first { it.id == "silverstone" }
        val d = Drivers.byId("colette")!!
        val car = Circuits.carOf(DriverBuilds.make(3, Racing.Build.MEDIUM, "race", "close", "lsd", 0.8))
        val r = Circuits.race(Circuits.plan(car, c, d), d, Circuits.parPlan(3, c), 7L)
        rule.mainClock.autoAdvance = false
        rule.setContent { CircuitRaceBody(r, "p911", false) {} }
        rule.mainClock.advanceTimeBy(40_000)
        val v = rule.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(android.graphics.Canvas(bmp)) }
        CircuitShots.dir.mkdirs()
        java.io.File(CircuitShots.dir, "race-screen.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
