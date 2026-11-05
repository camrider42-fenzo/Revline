package com.revline.idle.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.model.Block
import com.revline.idle.model.FuelKind
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EngineArtShotTest {
    @Test
    fun engineAnimShot() {
        val tag = System.getenv("ENGINE_SHOT") ?: return
        if (tag == "rotary-anim" || tag == "rotary") {
            rotaryAnimShot()
            return
        }
        val state = GameState(
            prestiges = 3,
            fuel = FuelKind.PREMIUM_93,
            upgrades = mapOf(
                "filter" to 3, "cold_air" to 2, "headers" to 2, "cam" to 2, "springs" to 3,
                "plugs" to 2, "coils" to 2, "turbo" to 2, "intercooler" to 2, "radiator" to 2,
                "fuel_pump" to 2, "injectors" to 2, "pistons" to 1, "studs" to 1, "water_pump" to 1,
            ),
            throttle = 0.85,
        )
        val params = Logic.paramsFrom(state)
        val rpm = Logic.targetRpm(params.redlineRpm, state.throttle).toFloat()
        CircuitShots.shoot("engine-$tag", 640, 480) {
            drawRect(Color(0xFF0B1811))
            val stroke = (size.width / 170f).coerceIn(1.7f, 3.4f)
            EngineArtDraw.draw(this, params, state.upgrades, stroke, angle = 110f, clock = 1.4f, spinRpm = rpm, throttle = 0.85f)
        }
    }

    @Test
    fun rotaryAnimShot() {
        val tag = System.getenv("ENGINE_SHOT")
        if (tag != null && tag !in setOf("rotary-anim", "rotary", "rotary-anim-v2", "1")) return
        if (tag == null && System.getenv("CIRCUIT_GFX_DIR") == null) return
        val twin = GameState(
            prestiges = Block.ROTARY.ladderStep,
            blockId = Block.ROTARY.name,
            fuel = FuelKind.PREMIUM_93,
            upgrades = mapOf(
                "filter" to 2, "apex_seals" to 3, "port_bridge" to 1, "oil_meter" to 2,
                "housing_coat" to 1, "eccentric" to 2, "turbo" to 1, "intercooler" to 1,
            ),
            throttle = 0.9,
        )
        val twinParams = Logic.paramsFrom(twin)
        val rpm = Logic.targetRpm(twinParams.redlineRpm, twin.throttle).toFloat()
        val panelW = 420
        val panelH = 420
        fun panel(angle: Float): Bitmap {
            val img = ImageBitmap(panelW, panelH)
            CanvasDrawScope().draw(Density(2.6f), LayoutDirection.Ltr, Canvas(img), Size(panelW.toFloat(), panelH.toFloat())) {
                drawRect(Color(0xFF0B1811))
                val stroke = (size.width / 170f).coerceIn(1.7f, 3.4f)
                EngineArtDraw.draw(this, twinParams, twin.upgrades, stroke, angle = angle, clock = 1.35f, spinRpm = rpm, throttle = 0.9f)
            }
            return img.asAndroidBitmap()
        }
        val frames = listOf(20f, 80f, 140f, 200f).map(::panel)
        val refFile = File(CircuitShots.dir, "refs/wankel-engine-diagram.png")
        val refBmp = if (refFile.isFile) {
            val raw = android.graphics.BitmapFactory.decodeFile(refFile.absolutePath)
            android.graphics.Bitmap.createScaledBitmap(raw, panelW, panelH, true)
        } else {
            frames[0]
        }
        val cols = frames.size + 1
        val out = Bitmap.createBitmap(panelW * cols, panelH, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(out)
        canvas.drawBitmap(refBmp, 0f, 0f, null)
        frames.forEachIndexed { idx, bmp -> canvas.drawBitmap(bmp, panelW * (idx + 1).toFloat(), 0f, null) }
        CircuitShots.dir.mkdirs()
        File(CircuitShots.dir, "rotary-anim.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(CircuitShots.dir, "rotary-anim-v2.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

}
