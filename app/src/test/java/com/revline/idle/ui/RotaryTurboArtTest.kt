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
 * A fitted turbo is drawn on the housing. The old corner brackets and the
 * loose bottom dash are not part of the engine.
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

    @Test
    fun strayCornerMarksAreNotDrawnOnARotary() {
        val single = render(state(mapOf("turbo" to 1)))
        val bare = render(state(emptyMap()))
        val twin = render(engine(Block.ROTARY, mapOf("turbo" to 1)))
        val twinBare = render(engine(Block.ROTARY, emptyMap()))
        for ((name, bmp) in listOf("1R turbo" to single, "1R bare" to bare, "13B turbo" to twin, "13B bare" to twinBare)) {
            assertTrue("$name top-left ${lit(bmp, 0.04f, 0.04f, 0.30f, 0.28f)}", lit(bmp, 0.04f, 0.04f, 0.30f, 0.28f) < 40)
            assertTrue("$name top-right ${lit(bmp, 0.86f, 0.10f, 0.98f, 0.30f)}", lit(bmp, 0.86f, 0.10f, 0.98f, 0.30f) < 40)
            assertTrue("$name bottom-left ${lit(bmp, 0.04f, 0.82f, 0.24f, 0.96f)}", lit(bmp, 0.04f, 0.82f, 0.24f, 0.96f) < 40)
        }
        val onEngine = lit(twin, 0.76f, 0.35f, 0.92f, 0.62f) - lit(twinBare, 0.76f, 0.35f, 0.92f, 0.62f)
        assertTrue("13B turbo stays on the housing ($onEngine)", onEngine > 300)
        val pistonBare = render(engine(Block.THUMPER, emptyMap()))
        val pistonTurbo = render(engine(Block.THUMPER, mapOf("turbo" to 1)))
        val piston = lit(pistonTurbo, 0.55f, 0.15f, 0.85f, 0.55f) - lit(pistonBare, 0.55f, 0.15f, 0.85f, 0.55f)
        assertTrue("piston turbo still draws ($piston)", piston > 300)
    }

    private fun engine(block: Block, upgrades: Map<String, Int>) = GameState(
        prestiges = block.ladderStep,
        blockId = block.name,
        upgrades = upgrades,
        researched = setOf("exhaust_fab", "turbo_node"),
    )

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
    private fun litBesideHousing(bmp: Bitmap): Int = lit(bmp, 0.64f, 0.32f, 0.78f, 0.65f)

    private fun lit(bmp: Bitmap, x0: Float, y0: Float, x1: Float, y1: Float): Int {
        var n = 0
        val w = bmp.width
        val h = bmp.height
        for (y in (h * y0).toInt() until (h * y1).toInt()) {
            for (x in (w * x0).toInt() until (w * x1).toInt()) {
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
