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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The new faces are ink on the casting, and the empty corners of a rotary stay empty. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EngineDepthRenderTest {
    @Test fun theInlineCaseGrowsALitLid() {
        val bmp = render(Block.INLINE_FOUR, mapOf("headers" to 1))
        val lid = lit(bmp, 0.36f, 0.27f, 0.68f, 0.34f)
        assertTrue("the lid is a plane, not a single wire ($lid)", lid > 2500)
    }

    @Test fun aRotorFaceFillsAndTheTurboStaysOnTheHousing() {
        val bmp = render(Block.ROTARY_1R, mapOf("turbo" to 1))
        val face = lit(bmp, 0.42f, 0.40f, 0.58f, 0.62f)
        assertTrue("the rotor face is filled ($face)", face > 4000)
        val turbo = lit(bmp, 0.64f, 0.32f, 0.82f, 0.68f)
        assertTrue("turbo ink stays beside the housing ($turbo)", turbo > 400)
        assertTrue("top-left ${lit(bmp, 0.04f, 0.04f, 0.30f, 0.28f)}", lit(bmp, 0.04f, 0.04f, 0.30f, 0.28f) < 40)
        assertTrue("top-right ${lit(bmp, 0.86f, 0.10f, 0.98f, 0.30f)}", lit(bmp, 0.86f, 0.10f, 0.98f, 0.30f) < 40)
        assertTrue("bottom-left ${lit(bmp, 0.04f, 0.82f, 0.24f, 0.96f)}", lit(bmp, 0.04f, 0.82f, 0.24f, 0.96f) < 40)
    }

    @Test fun theVeeSumpAndTheFourRotorKeepTheirMass() {
        val vee = render(Block.V8, mapOf("headers" to 1))
        val sump = lit(vee, 0.40f, 0.72f, 0.62f, 0.90f)
        assertTrue("the sump is a pan ($sump)", sump > 1500)
        val four = render(Block.ROTARY_26B, mapOf("apex_seals" to 1))
        val faces = lit(four, 0.25f, 0.35f, 0.75f, 0.72f)
        assertTrue("the 26B rotors are filled ($faces)", faces > 8000)
        assertTrue("26B top-left ${lit(four, 0.02f, 0.02f, 0.12f, 0.18f)}", lit(four, 0.02f, 0.02f, 0.12f, 0.18f) < 40)
    }

    private fun render(block: Block, upgrades: Map<String, Int>): Bitmap {
        val state = GameState(
            prestiges = block.ladderStep,
            blockId = block.name,
            upgrades = upgrades,
            researched = setOf("exhaust_fab", "turbo_node"),
            throttle = 0.85,
        )
        val params = Logic.paramsFrom(state)
        val img = ImageBitmap(1000, 480)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(1000f, 480f)) {
            drawRect(Color.Black)
            val stroke = (size.width / 170f).coerceIn(1.7f, 3.4f)
            EngineArtDraw.draw(this, params, state.upgrades, stroke, angle = 70f, clock = 1.4f, spinRpm = 4200f, throttle = 0.85f)
        }
        return img.asAndroidBitmap()
    }

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
                if (r + g + b > 40) n++
            }
        }
        return n
    }
}
