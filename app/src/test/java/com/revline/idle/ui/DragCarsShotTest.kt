package com.revline.idle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.revline.idle.game.Racing
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DragCarsShotTest {
    @Test
    fun contactSheet() {
        val ids = listOf("mule") + Racing.chassis.map { it.id }
        val cols = 4
        val rows = (ids.size + cols - 1) / cols
        val cw = 420
        val ch = 190
        DragShots.shoot("pass1-cars", cw * cols, ch * rows) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0F14), Color(0xFF1B222A))))
            ids.forEachIndexed { i, id ->
                val cx = (i % cols) * cw + cw / 2f
                val gy = (i / cols) * ch + ch * 0.82f
                drawRect(Color(0xFF2A2F35), Offset((i % cols) * cw.toFloat(), gy), Size(cw.toFloat(), 3f))
                val color = if (i % 2 == 0) Color(0xFFE4FF47) else Color(0xFFFF3B30)
                val accent = if (i % 2 == 0) Color(0xFF111111) else Color.White
                drawDragCar(id, false, color, accent, CarPose(cx, gy, 110f, 0f, 0f, 0f, 0f, 0f, 0f))
            }
        }
    }

    @Test
    fun detailSheet() {
        val ids = listOf("sprite", "vette", "p917", "stratos", "etype", "sel63")
        val cols = 2
        val cw = 760
        val ch = 330
        DragShots.shoot("pass1-cars-detail", cw * cols, ch * 3) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0F14), Color(0xFF1B222A))))
            ids.forEachIndexed { i, id ->
                val cx = (i % cols) * cw + cw / 2f
                val gy = (i / cols) * ch + ch * 0.82f
                drawRect(Color(0xFF2A2F35), Offset((i % cols) * cw.toFloat(), gy), Size(cw.toFloat(), 3f))
                val color = if (i % 2 == 0) Color(0xFFE4FF47) else Color(0xFFFF3B30)
                val accent = if (i % 2 == 0) Color(0xFF111111) else Color.White
                drawDragCar(id, false, color, accent, CarPose(cx, gy, 240f, 0f, 0f, 0f, 0f, 0f, 0f))
            }
        }
    }

    @Test
    fun wheelSheet() {
        val speeds = listOf(0f, 12f, 28f, 50f, 90f)
        DragShots.shoot("pass2-wheels", 1500, 760) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0F14), Color(0xFF1B222A))))
            speeds.forEachIndexed { i, om ->
                val cx = 150f + i * 300f
                for ((row, slick) in listOf(true, false).withIndex()) {
                    val r = if (slick) 130f else 108f
                    val c = Offset(cx, 190f + row * 300f)
                    withUnits(c, 1f) {
                        drawDragWheel(Offset.Zero, r, -0.4f - om * 0.02f, om, slick, Color(0xFFFF3B30), if (slick) 1f else 0f, 1f)
                    }
                }
            }
        }
        DragShots.shoot("pass2-cars-launch", 1500, 640) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0F14), Color(0xFF1B222A))))
            listOf(
                Triple("vette", 0f, 0.06f), Triple("gt40", 8f, 0.05f), Triple("seven", 45f, 0.015f),
                Triple("p911", 0f, 0.07f), Triple("cobra", 25f, 0.03f), Triple("etype", 70f, 0.01f),
            ).forEachIndexed { i, (id, speed, pitch) ->
                val cx = 400f + (i % 3) * 360f
                val gy = 250f + (i / 3) * 300f
                drawRect(Color(0xFF2A2F35), Offset((i % 3) * 360f + 220f, gy), Size(360f, 3f))
                drawDragCar(id, false, if (i % 2 == 0) Color(0xFFE4FF47) else Color(0xFFFF3B30), if (i % 2 == 0) Color(0xFF111111) else Color.White,
                    CarPose(cx, gy, 150f, pitch, 30f * i, if (i == 3) 0.6f else 0f, speed, i * 0.3f, 0f, spun = i * 0.7f))
            }
        }
    }

    private fun DrawScope.withUnits(origin: Offset, scale: Float, block: DrawScope.() -> Unit) {
        withTransform({ translate(origin.x, origin.y); scale(scale, -scale, Offset.Zero) }) { block() }
    }
}
