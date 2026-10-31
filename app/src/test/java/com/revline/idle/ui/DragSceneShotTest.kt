package com.revline.idle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DragSceneShotTest {
    private fun frame(distance: Float, rival: Float, slip: Float, speed: Float, clock: Float, lights: Float = 0f, done: Boolean = false, won: Boolean = false) =
        DragFrame(distance, rival, 402f, slip, speed, clock, 0.8f, 0.55f, 0f, lights, done, won, false)

    private fun DrawScope.scene(yourId: String, rivalId: String, frames: List<DragFrame>, w: Float, h: Float): DragFrame {
        val geo = DragGeo(w, h, yourId, false, rivalId)
        val scene = bakeDragScene(geo, Density(2.6f), 402f)
        val mem = DragMemory()
        frames.forEach { f -> drawDragScene(scene, f, mem, yourId, false, rivalId) }
        return frames.last()
    }

    private fun shoot(name: String, yourId: String, rivalId: String, frames: List<DragFrame>, w: Int = 1000, h: Int = 728) =
        DragShots.shoot(name, w, h) {
            drawRect(Color.Black)
            scene(yourId, rivalId, frames, w.toFloat(), h.toFloat())
        }

    /** Runs the clock forward so pitch and rubber settle the way they do on a device. */
    private fun run(from: Float, to: Float, step: Float, make: (Float) -> DragFrame): List<DragFrame> {
        val out = ArrayList<DragFrame>()
        var t = from
        while (t < to) { out += make(t); t += step }
        return out
    }

    @Test
    fun launchMidFinish() {
        val launch = run(0f, 1.2f, 0.016f) { t -> frame(0f, 0f, 0f, 0f, t, lights = 0.5f) } +
            run(1.2f, 1.7f, 0.016f) { t -> val k = t - 1.2f; frame(k * k * 14f, k * k * 11f, if (t < 1.6f) 0.7f else 0f, k * 28f, t) }
        shoot("final-launch", "vette", "p911", launch)
        val mid = launch + run(1.7f, 5.2f, 0.016f) { t -> val k = t - 1.2f; frame(minOf(402f, k * k * 14f), minOf(402f, k * k * 12f), 0f, minOf(60f, k * 28f), t) }
        shoot("final-mid", "vette", "p911", mid.map { it })
        val fin = mid + run(5.2f, 5.5f, 0.016f) { t -> frame(402f, 399f, 0f, 60f, t, done = true, won = true) }
        shoot("final-finish", "vette", "p911", fin)
    }

    @Test
    fun otherMatchups() {
        val seq = run(0f, 3f, 0.016f) { t -> val k = t; frame(k * k * 9f, k * k * 10f, 0f, minOf(40f, k * 18f), t) }
        shoot("pass3-track", "gt40", "seven", seq)
        shoot("pass3-track-long", "sel63", "p50", seq)
    }
}
