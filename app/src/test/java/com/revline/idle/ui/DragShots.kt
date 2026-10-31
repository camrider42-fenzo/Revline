package com.revline.idle.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File

/** Draws a scene on the JVM and saves it as a PNG, so the strip art can be looked at without a device. */
internal object DragShots {
    val dir = File(System.getenv("DRAG_GFX_DIR") ?: "/workspace/drag-gfx")

    fun shoot(name: String, w: Int, h: Int, density: Float = 2.6f, draw: DrawScope.() -> Unit) {
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) { draw() }
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { img.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
