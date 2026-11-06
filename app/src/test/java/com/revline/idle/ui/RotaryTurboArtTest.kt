package com.revline.idle.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.model.Block
import com.revline.idle.model.Induction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The single-rotor bay must show a turbo on the housing when one is fitted.
 * The corner bracket shared with the other rotaries sits clear of that housing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RotaryTurboArtTest {
    @Test
    fun singleRotorDrawsATurboBesideTheHousingWhenFitted() {
        val bare = state(emptyMap())
        val fitted = state(mapOf("turbo" to 1))
        assertEquals(Induction.TURBO, Logic.paramsFrom(fitted).induction)
        assertEquals(Induction.NA, Logic.paramsFrom(bare).induction)
        val na = litBesideHousing(render(bare))
        val turbo = litBesideHousing(render(fitted))
        assertTrue("no turbo ink beside a bare single rotor ($na)", na < 40)
        assertTrue("turbo ink beside the housing $turbo vs bare $na", turbo > na + 400)
    }

    private fun state(upgrades: Map<String, Int>) = GameState(
        prestiges = Block.ROTARY_1R.ladderStep,
        blockId = Block.ROTARY_1R.name,
        upgrades = upgrades,
        researched = setOf("exhaust_fab", "turbo_node"),
    )

    private fun render(state: GameState): Bitmap {
        val params = Logic.paramsFrom(state)
        val img = ImageBitmap(1000, 480)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(1000f, 480f)) {
            drawRect(Color.Black)
            val stroke = (size.width / 170f).coerceIn(1.7f, 3.4f)
            EngineArtDraw.draw(this, params, state.upgrades, stroke, angle = 40f, clock = 1.2f, spinRpm = 3000f, throttle = 0.8f)
        }
        return img.asAndroidBitmap()
    }

    /** Exhaust side of a centered single housing on a 1000×480 bench. Empty unless a turbo is drawn there. */
    private fun litBesideHousing(bmp: Bitmap): Int {
        var n = 0
        for (y in (480 * 0.32f).toInt() until (480 * 0.65f).toInt()) {
            for (x in (1000 * 0.64f).toInt() until (1000 * 0.78f).toInt()) {
                val p = bmp.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r + g + b > 30) n++
            }
        }
        return n
    }
}
