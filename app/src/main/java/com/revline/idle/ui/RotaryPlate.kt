package com.revline.idle.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The housing does not turn with the shaft. Each subpixel phase of a centre is
 * rasterized once (the same strokes as the live path) and stamped with no
 * filtering, so a later frame matches the strokes pixel for pixel.
 * Tests set [enabled] off to draw those strokes directly.
 */
internal object RotaryPlate {
    var enabled: Boolean = true

    private val slots = object : LinkedHashMap<Long, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ImageBitmap>?): Boolean = size > 8
    }

    fun clear() {
        slots.clear()
    }

    fun draw(
        scope: DrawScope,
        cx: Float,
        cy: Float,
        R: Float,
        e: Float,
        stroke: Float,
        ink: Color,
        wall: Offset,
    ) {
        if (!enabled) {
            scope.paintHousing(cx, cy, R, e, stroke, ink, wall)
            return
        }
        val reach = (R + e) * 1.40f + stroke * 16f
        val left = floor(cx - reach)
        val top = floor(cy - reach)
        val bx = cx - left
        val by = cy - top
        val destW = scope.size.width
        val destH = scope.size.height
        val key = key(R, e, stroke, ink, wall, bx, by, left, top, destW, destH)
        val plate = slots[key] ?: bake(key, reach, bx, by, left, top, destW, destH, R, e, stroke, ink, wall)
        scope.drawImage(plate, topLeft = Offset(left, top))
    }

    private fun bake(
        key: Long,
        reach: Float,
        bx: Float,
        by: Float,
        left: Float,
        top: Float,
        destW: Float,
        destH: Float,
        R: Float,
        e: Float,
        stroke: Float,
        ink: Color,
        wall: Offset,
    ): ImageBitmap {
        val side = ceil(reach * 2f + 2f).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val image = bmp.asImageBitmap()
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(image),
            Size(side.toFloat(), side.toFloat()),
        ) {
            // Same clip the live strokes see, so a bore that runs off the panel
            // anti-aliases against the same edge.
            clipRect(0f - left, 0f - top, destW - left, destH - top) {
                paintHousing(bx, by, R, e, stroke, ink, wall)
            }
        }
        slots[key] = image
        return image
    }

    private fun key(
        R: Float,
        e: Float,
        stroke: Float,
        ink: Color,
        wall: Offset,
        bx: Float,
        by: Float,
        left: Float,
        top: Float,
        destW: Float,
        destH: Float,
    ): Long {
        var h = 0x9E3779B97F4A7C15u.toLong()
        fun mix(v: Long) {
            h = h xor v
            h *= 0x100000001B3L
        }
        mix(R.toBits().toLong())
        mix(e.toBits().toLong())
        mix(stroke.toBits().toLong())
        mix(ink.value.toLong())
        mix(wall.x.toBits().toLong())
        mix(wall.y.toBits().toLong())
        mix(bx.toBits().toLong())
        mix(by.toBits().toLong())
        mix(left.toBits().toLong())
        mix(top.toBits().toLong())
        mix(destW.toBits().toLong())
        mix(destH.toBits().toLong())
        return h
    }
}

/** Bore plus the dim far wall. This is the picture the plate copies. */
internal fun DrawScope.paintHousing(
    cx: Float,
    cy: Float,
    R: Float,
    e: Float,
    stroke: Float,
    ink: Color,
    wall: Offset,
) {
    val housing = WankelGeometry.sampleHousing(R.toDouble(), e.toDouble(), 120)
    val front = housing.map { Offset(cx + it.x.toFloat(), cy + it.y.toFloat()) }
    glowPath(ink, front.map { it + wall }.closedPath(), stroke * 0.7f, 0.28f)
    for (s in housing.indices) {
        glowLine(ink, front[s], front[(s + 1) % front.size], stroke * 0.9f, 0.75f)
    }
}
