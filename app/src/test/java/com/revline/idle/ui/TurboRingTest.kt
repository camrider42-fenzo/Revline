package com.revline.idle.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A turbo is one housing ring. The old back rim and the scroll sat on a
 * second center, down and to the right of the housing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TurboRingTest {
    @Test fun aSingleRotorTurboIsOneRing() {
        val bmp = render(Block.ROTARY_1R, mapOf("turbo" to 1, "apex_seals" to 1))
        val (center, radius) = rotaryTurbo(1, bmp.width.toFloat(), bmp.height.toFloat())
        assertOneRing(bmp, "1R turbo", center, radius, -50, 80)
    }

    @Test fun a13BTurboIsOneRing() {
        val bmp = render(Block.ROTARY, mapOf("turbo" to 1, "apex_seals" to 1))
        val (center, radius) = rotaryTurbo(2, bmp.width.toFloat(), bmp.height.toFloat())
        assertOneRing(bmp, "13B turbo", center, radius, -50, 80)
    }

    @Test fun anInlineTurboAndItsFlywheelAreOneRingEach() {
        val bmp = render(Block.INLINE_FOUR, mapOf("turbo" to 1))
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val (turbo, turboR) = pistonTurbo(w, h, cylinders = 4, banks = 1)
        // The header plume leaves to the right. The open side of the housing is down-right,
        // which is where the old rim used to stick out.
        assertOneRing(bmp, "inline turbo", turbo, turboR, 24, 80)
        val (fly, flyR) = pistonFlywheel(w, h, cylinders = 4, banks = 1)
        assertOneRing(bmp, "inline flywheel", fly, flyR, -20, 70)
    }

    /**
     * [fromDeg]..[toDeg] is the open side of the part, away from the block.
     * Screen 0° points right and positive angles point down.
     */
    private fun assertOneRing(bmp: Bitmap, name: String, center: Offset, radius: Float, fromDeg: Int, toDeg: Int) {
        var angles = 0
        var steps = 0
        var deg = fromDeg
        while (deg <= toDeg) {
            steps++
            val cores = cluster(radialPeaks(bmp, center, radius, deg.toDouble()).filter { it.second >= 450 }, 5f)
            val onHousing = cores.filter { abs(it.first - radius) < radius * 0.20f }
            val outside = cores.filter { it.first > radius * 1.20f }
            assertTrue(
                "$name has one housing core at $deg° (on ${onHousing.size}, past ${outside.size}: ${cores.joinToString { "${it.first.toInt()}@${it.second}" }})",
                onHousing.size == 1 && outside.isEmpty(),
            )
            angles++
            deg += 10
        }
        assertTrue("$name ring is drawn ($angles of $steps)", angles >= steps - 1)
        val bulge = bulgePastTheHousing(bmp, center, radius, fromDeg.coerceAtLeast(10), toDeg)
        assertTrue("$name has no second circle past the housing ($bulge)", bulge < 4)
    }

    /**
     * Bright cores from just inside the housing to just outside it.
     * Blades and spokes stop short of this band. A second outline shows up as another core.
     */
    private fun radialPeaks(bmp: Bitmap, center: Offset, radius: Float, deg: Double): List<Pair<Float, Int>> {
        val a = Math.toRadians(deg)
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val samples = ArrayList<Pair<Float, Int>>()
        var d = radius * 0.90f
        while (d <= radius * 1.48f) {
            val x = center.x + dx * d
            val y = center.y + dy * d
            samples += d to lum(bmp, x, y)
            d += 1.3f
        }
        val peaks = ArrayList<Pair<Float, Int>>()
        for (i in 1 until samples.lastIndex) {
            val here = samples[i].second
            if (here >= samples[i - 1].second && here > samples[i + 1].second) {
                peaks += samples[i]
            }
        }
        return peaks
    }

    /** Peaks a few pixels apart are one stroke. A second outline sits much farther out. */
    private fun cluster(peaks: List<Pair<Float, Int>>, gap: Float): List<Pair<Float, Int>> {
        val sorted = peaks.sortedBy { it.first }
        val out = ArrayList<Pair<Float, Int>>()
        var current = sorted.firstOrNull() ?: return out
        for (peak in sorted.drop(1)) {
            if (peak.first - current.first <= gap) {
                if (peak.second > current.second) current = peak
            } else {
                out += current
                current = peak
            }
        }
        out += current
        return out
    }

    /** The old rim stuck out down-right of the housing. That wedge should be dark. */
    private fun bulgePastTheHousing(bmp: Bitmap, center: Offset, radius: Float, fromDeg: Int, toDeg: Int): Int {
        var n = 0
        for (deg in fromDeg..toDeg step 8) {
            val a = Math.toRadians(deg.toDouble())
            val d = radius * 1.42f
            if (lum(bmp, center.x + cos(a).toFloat() * d, center.y + sin(a).toFloat() * d) > 90) n++
        }
        return n
    }

    private fun rotaryTurbo(rotors: Int, w: Float, h: Float): Pair<Offset, Float> {
        val near = RotorStack.centers(rotors, w, h).first()
        val housing = h * 0.030f * (WankelGeometry.DEFAULT_R_OVER_E.toFloat() + 1f)
        val radius = h * 0.062f
        return Offset(near.x + housing + radius * 1.45f, near.y - h * 0.06f) to radius
    }

    private fun pistonTurbo(w: Float, h: Float, cylinders: Int, banks: Int): Pair<Offset, Float> {
        val right = blockRight(w, cylinders, banks)
        return Offset(right + w * 0.065f, h * 0.34f) to h * 0.064f
    }

    private fun pistonFlywheel(w: Float, h: Float, cylinders: Int, banks: Int): Pair<Offset, Float> {
        val right = blockRight(w, cylinders, banks)
        val radius = h * 0.065f
        return Offset(right + radius * 0.5f, h * 0.73f) to radius
    }

    private fun blockRight(w: Float, cylinders: Int, banks: Int): Float {
        val perBank = if (banks >= 2) (cylinders / 2).coerceAtLeast(1) else cylinders
        val span = when {
            perBank <= 1 -> w * 0.28f
            perBank == 2 -> w * 0.40f
            perBank == 3 -> w * 0.50f
            else -> w * 0.54f
        }
        val left = if (banks >= 2) w * 0.28f else w * 0.36f - span * 0.15f
        return left + span
    }

    private fun render(block: Block, upgrades: Map<String, Int>): Bitmap {
        val state = GameState(
            prestiges = block.ladderStep,
            blockId = block.name,
            upgrades = upgrades,
            researched = setOf("exhaust_fab", "turbo_node"),
            throttle = 0.8,
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

    private fun lum(bmp: Bitmap, x: Float, y: Float): Int {
        val ix = x.toInt()
        val iy = y.toInt()
        if (ix !in 0 until bmp.width || iy !in 0 until bmp.height) return 0
        val p = bmp.getPixel(ix, iy)
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return r + g + b
    }
}
