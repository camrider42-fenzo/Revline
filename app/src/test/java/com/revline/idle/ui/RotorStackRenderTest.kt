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

/** The painted stack runs up-left, and the empty corners stay empty. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RotorStackRenderTest {
    @Test fun theStackRunsUpLeftAndClearsTheCorners() {
        for ((name, block) in listOf("13B" to Block.ROTARY, "20B" to Block.ROTARY_20B, "26B" to Block.ROTARY_26B)) {
            val bmp = render(block)
            val split = split(bmp)
            assertTrue("$name has a near housing (${split.lower})", split.lower > 800)
            assertTrue("$name has a far housing (${split.upper})", split.upper > 800)
            assertTrue("$name far housing is to the left (${split.upperX} vs ${split.lowerX})", split.upperX + 12f < split.lowerX)
            assertTrue("$name top-left ${lit(bmp, 0.04f, 0.04f, 0.30f, 0.28f)}", lit(bmp, 0.04f, 0.04f, 0.30f, 0.28f) < 40)
            assertTrue("$name top-right ${lit(bmp, 0.86f, 0.10f, 0.98f, 0.30f)}", lit(bmp, 0.86f, 0.10f, 0.98f, 0.30f) < 40)
            assertTrue("$name bottom-left ${lit(bmp, 0.04f, 0.82f, 0.24f, 0.96f)}", lit(bmp, 0.04f, 0.82f, 0.24f, 0.96f) < 40)
        }
    }

    @Test fun fourRotorsReachHigherThanTwo() {
        val two = bounds(render(Block.ROTARY))
        val four = bounds(render(Block.ROTARY_26B))
        assertTrue("26B stack is taller (${four.height} vs ${two.height})", four.height > two.height + 40)
        assertTrue("26B stays a stack, not a row (${four.width} x ${four.height})", four.height > four.width * 0.7f)
    }

    private fun render(block: Block): Bitmap {
        val state = GameState(
            prestiges = block.ladderStep,
            blockId = block.name,
            upgrades = mapOf("apex_seals" to 1),
            researched = setOf("exhaust_fab"),
        )
        val params = Logic.paramsFrom(state)
        val img = ImageBitmap(1000, 480)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(1000f, 480f)) {
            drawRect(Color.Black)
            val stroke = (size.width / 170f).coerceIn(1.7f, 3.4f)
            EngineArtDraw.draw(this, params, state.upgrades, stroke, angle = 40f, clock = 1.2f, spinRpm = 3000f, throttle = 0.8f)
        }
        return img.asAndroidBitmap()
    }

    private class Split(val upper: Int, val lower: Int, val upperX: Float, val lowerX: Float)

    /** Lit pixels above and below the middle of the ink. The far housing should sit up and left. */
    private fun split(bmp: Bitmap): Split {
        var minY = bmp.height
        var maxY = 0
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                if (bright(bmp.getPixel(x, y))) {
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        val mid = (minY + maxY) / 2
        var up = 0
        var down = 0
        var upX = 0.0
        var downX = 0.0
        for (y in minY..maxY) {
            for (x in 0 until bmp.width) {
                if (!bright(bmp.getPixel(x, y))) continue
                if (y <= mid) {
                    up++
                    upX += x
                } else {
                    down++
                    downX += x
                }
            }
        }
        return Split(up, down, (upX / up.coerceAtLeast(1)).toFloat(), (downX / down.coerceAtLeast(1)).toFloat())
    }

    private class Box(val width: Int, val height: Int)

    private fun bounds(bmp: Bitmap): Box {
        var minX = bmp.width
        var maxX = 0
        var minY = bmp.height
        var maxY = 0
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                if (!bright(bmp.getPixel(x, y))) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return Box(maxX - minX, maxY - minY)
    }

    private fun bright(p: Int): Boolean {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return r + g + b > 40
    }

    private fun lit(bmp: Bitmap, x0: Float, y0: Float, x1: Float, y1: Float): Int {
        var n = 0
        for (y in (bmp.height * y0).toInt() until (bmp.height * y1).toInt()) {
            for (x in (bmp.width * x0).toInt() until (bmp.width * x1).toInt()) {
                if (bright(bmp.getPixel(x, y))) n++
            }
        }
        return n
    }
}
