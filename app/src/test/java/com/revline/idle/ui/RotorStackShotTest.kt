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
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Still frames of the multi-rotor bay. Set ROTOR_STACK_DIR to write them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RotorStackShotTest {
    @Test
    fun multiRotorFrames() {
        val root = System.getenv("ROTOR_STACK_DIR") ?: return
        val dir = File(root)
        dir.mkdirs()
        frames().forEach { (name, bmp) ->
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun frames(): List<Pair<String, Bitmap>> = listOf(
        "13b" to engine(Block.ROTARY, mapOf("apex_seals" to 2, "turbo" to 1)),
        "20b" to engine(Block.ROTARY_20B, mapOf("apex_seals" to 2)),
        "26b" to engine(Block.ROTARY_26B, mapOf("apex_seals" to 2, "port_bridge" to 1)),
    )

    private fun engine(block: Block, upgrades: Map<String, Int>): Bitmap {
        val state = GameState(
            prestiges = block.ladderStep,
            blockId = block.name,
            upgrades = upgrades,
            researched = setOf("exhaust_fab", "turbo_node"),
            throttle = 0.85,
        )
        val params = Logic.paramsFrom(state)
        val img = ImageBitmap(1200, 640)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(1200f, 640f)) {
            drawRect(Color.Black)
            val stroke = (size.width / 170f).coerceIn(1.7f, 3.4f)
            EngineArtDraw.draw(this, params, state.upgrades, stroke, angle = 70f, clock = 1.4f, spinRpm = 4200f, throttle = 0.85f)
        }
        return img.asAndroidBitmap()
    }
}
