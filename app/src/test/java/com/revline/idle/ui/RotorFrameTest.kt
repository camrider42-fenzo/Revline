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

/**
 * The unfolded Fold race panel is 220dp × 320dp at 2.625 density (577 × 840).
 * A stamped housing must match the strokes it replaces, and the stamp has to
 * be the cheaper of the two once the bitmap exists.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RotorFrameTest {
    @Test fun stampedHousingMatchesStrokes() {
        val angles = listOf(20f, 70f, 140f, 210f)
        for (angle in angles) {
            RotaryPlate.clear()
            RotaryPlate.enabled = false
            val live = raster(Block.ROTARY_26B, 577, 840, angle)
            RotaryPlate.clear()
            RotaryPlate.enabled = true
            val stamped = raster(Block.ROTARY_26B, 577, 840, angle)
            val delta = maxChannelDelta(live, stamped)
            if (delta != 0) println("26B at $angle° delta $delta ${diffWhere(live, stamped)}")
            // An 8-bit stamp rounds SrcOver by a channel or two. The bore that
            // leaves the panel can be off by a few more where the clip lands.
            assertTrue("26B at $angle° delta $delta", delta <= 5)
        }
        RotaryPlate.enabled = true
    }

    @Test fun frameCost() {
        val log = StringBuilder()
        val race = 577 to 840
        val bench = 1812 to 735
        for ((label, size) in listOf("race" to race, "bench" to bench)) {
            for ((name, block) in listOf(
                "26B" to Block.ROTARY_26B,
                "20B" to Block.ROTARY_20B,
                "13B" to Block.ROTARY,
                "V8" to Block.V8,
                "I4" to Block.INLINE_FOUR,
            )) {
                val (w, h) = size
                RotaryPlate.enabled = false
                repeat(2) { i -> raster(block, w, h, 12f + i) }
                val before = millis(block, size)
                RotaryPlate.clear()
                RotaryPlate.enabled = true
                raster(block, w, h, 12f)
                val after = millis(block, size)
                log.append(String.format("%-8s %-4s  before %7.3f ms  after %7.3f ms%n", label, name, before, after))
                if (label == "race" && name == "26B") {
                    assertTrue("stamped 26B $after ms should beat strokes $before ms", after < before * 0.9)
                }
            }
        }
        println(log)
        RotaryPlate.enabled = true
    }

    private fun millis(block: Block, size: Pair<Int, Int>): Double {
        val (w, h) = size
        val start = System.nanoTime()
        val n = 4
        repeat(n) { i -> raster(block, w, h, 20f + i * 24f) }
        return (System.nanoTime() - start) / 1e6 / n
    }

    private fun raster(block: Block, w: Int, h: Int, angle: Float): Bitmap {
        val state = GameState(prestiges = block.ladderStep, blockId = block.name, throttle = 0.8)
        val params = Logic.paramsFrom(state)
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(2.625f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            drawRect(Color(0xFF000000))
            val stroke = (this.size.width / 170f).coerceIn(1.7f, 3.4f)
            EngineArtDraw.draw(
                this, params, state.upgrades, stroke, angle,
                clock = 1.4f, spinRpm = 6400f, throttle = 0.8f,
            )
        }
        return img.asAndroidBitmap()
    }

    private fun maxChannelDelta(a: Bitmap, b: Bitmap): Int {
        var worst = 0
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val d = channelDelta(a.getPixel(x, y), b.getPixel(x, y))
                if (d > worst) worst = d
            }
        }
        return worst
    }

    private fun diffWhere(a: Bitmap, b: Bitmap): String {
        var n = 0
        var over2 = 0
        var at = ""
        var worst = 0
        var where = ""
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                val d = channelDelta(a.getPixel(x, y), b.getPixel(x, y))
                if (d > 0) {
                    n++
                    if (at.isEmpty()) at = "first @$x,$y d=$d"
                }
                if (d > 2) over2++
                if (d > worst) {
                    worst = d
                    where = "@$x,$y live=${a.getPixel(x, y).toUInt().toString(16)} stamp=${b.getPixel(x, y).toUInt().toString(16)}"
                }
            }
        }
        return "pixels $n over2 $over2 $at worst $where"
    }

    private fun channelDelta(p: Int, q: Int): Int = maxOf(
        kotlin.math.abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)),
        kotlin.math.abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)),
        kotlin.math.abs((p and 0xFF) - (q and 0xFF)),
        kotlin.math.abs((p ushr 24) - (q ushr 24)),
    )
}
