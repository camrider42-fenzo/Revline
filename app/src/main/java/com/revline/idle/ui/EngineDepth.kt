package com.revline.idle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin

/**
 * Isometric faces for the bay. The wireframe stays the bright edge.
 * A top face and a side face sit behind it, in the same neon colors, so the
 * casting reads as a solid without leaving the instrument-light look.
 * Depth stays a small fraction of the part, so a lip cannot wander into the
 * empty corners of the bay.
 */
internal object EngineDepth {
    enum class Face { TOP, FRONT, SIDE }

    /** Top is the lit plane, the side falls off, the cutaway front stays faint. */
    fun faceAlpha(face: Face): Float = when (face) {
        Face.TOP -> 0.40f
        Face.SIDE -> 0.22f
        Face.FRONT -> 0.12f
    }

    /**
     * Up and to the right. [span] is the part's long side, in px.
     * The lip grows with the part and stops at a fixed cap.
     */
    fun extrusion(span: Float): Offset {
        val d = span.coerceAtLeast(1f)
        val rise = (d * 0.16f).coerceIn(d * 0.08f, 26f)
        val run = (d * 0.11f).coerceIn(d * 0.06f, 22f)
        return Offset(run, -rise)
    }

    /** Foreground hardware drifts a fraction of a stroke. The block does not. */
    fun parallax(stroke: Float, clock: Float): Offset {
        val a = stroke.coerceIn(0f, 8f)
        return Offset(a * 0.85f * sin(clock * 1.7f), a * 0.55f * sin(clock * 1.15f + 0.4f))
    }

    fun slab(left: Float, top: Float, right: Float, bottom: Float, depth: Offset) = Slab(
        front = listOf(Offset(left, top), Offset(right, top), Offset(right, bottom), Offset(left, bottom)),
        top = listOf(Offset(left, top), Offset(right, top), Offset(right, top) + depth, Offset(left, top) + depth),
        side = listOf(Offset(right, top), Offset(right, top) + depth, Offset(right, bottom) + depth, Offset(right, bottom)),
    )
}

internal class Slab(val front: List<Offset>, val top: List<Offset>, val side: List<Offset>)

internal fun DrawScope.drawSlab(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    depth: Offset,
    color: Color,
    stroke: Float,
    front: Boolean = false,
) {
    if (right - left < 2f || bottom - top < 2f) return
    val box = EngineDepth.slab(left, top, right, bottom, depth)
    shadeQuad(box.side, color, EngineDepth.faceAlpha(EngineDepth.Face.SIDE))
    shadeQuad(box.top, color, EngineDepth.faceAlpha(EngineDepth.Face.TOP))
    if (front) shadeQuad(box.front, color, EngineDepth.faceAlpha(EngineDepth.Face.FRONT))
    glowLine(color, box.top[1], box.top[2], stroke * 0.4f, 0.4f)
    glowLine(color, box.top[3], box.top[2], stroke * 0.35f, 0.32f)
    glowLine(color, box.top[0], box.top[3], stroke * 0.35f, 0.28f)
    glowLine(White, box.front[0], box.front[1], stroke * 0.4f, 0.45f)
}

/** A pulley or compressor: a back rim, a face, and a highlight on the near edge. */
internal fun DrawScope.depthDisk(
    center: Offset,
    radius: Float,
    color: Color,
    stroke: Float,
    clock: Float,
    snail: Boolean = false,
) {
    if (radius < 2f) return
    val depth = Offset(radius * 0.36f, radius * 0.14f) + EngineDepth.parallax(stroke, clock) * 0.4f
    val back = center + depth
    if (snail) {
        glowArc(
            color, 24f, 300f,
            Offset(back.x - radius * 1.15f, back.y - radius * 1.05f),
            Size(radius * 2.45f, radius * 2.15f),
            stroke * 0.65f,
            0.4f,
        )
    }
    glowCircle(color, radius * 0.96f, back, stroke * 0.6f, gain = 0.32f)
    drawCircle(color.copy(alpha = 0.16f), radius * 0.88f, center)
    glowArc(
        White, 205f, 85f,
        Offset(center.x - radius, center.y - radius),
        Size(radius * 2f, radius * 2f),
        stroke * 0.4f,
        0.42f,
    )
}

/** Soft pool under a casting. Dark green, the bay's own black, not a new color. */
internal fun DrawScope.groundPool(center: Offset, rx: Float, ry: Float) {
    if (rx < 4f || ry < 2f) return
    drawOval(Color(0xFF0C1A14), Offset(center.x - rx, center.y - ry), Size(rx * 2f, ry * 2f))
    drawOval(
        Color(0xFF163228),
        Offset(center.x - rx * 0.62f, center.y - ry * 0.62f),
        Size(rx * 1.24f, ry * 1.24f),
    )
}

internal fun DrawScope.shadeFan(center: Offset, edge: List<Offset>, color: Color, alpha: Float) {
    if (edge.size < 2 || alpha <= 0.02f) return
    val path = Path()
    path.moveTo(center.x, center.y)
    for (p in edge) path.lineTo(p.x, p.y)
    path.close()
    drawPath(path, color.copy(alpha = alpha))
}

internal fun DrawScope.shadeQuad(corners: List<Offset>, color: Color, alpha: Float) {
    if (corners.size < 3 || alpha <= 0.02f) return
    val path = Path()
    path.moveTo(corners[0].x, corners[0].y)
    for (i in 1 until corners.size) path.lineTo(corners[i].x, corners[i].y)
    path.close()
    drawPath(path, color.copy(alpha = alpha))
}

/**
 * Where each rotor housing sits. Index 0 is the near one, low and to the right,
 * drawn last and at full brightness. Each step back moves up and to the left.
 * A single rotor stays on the old center so its turbo and its corners do not move.
 */
internal object RotorStack {
    fun step(height: Float): Offset {
        val rise = height * 0.115f
        return Offset(-rise * 0.84f, -rise)
    }

    fun centers(count: Int, width: Float, height: Float): List<Offset> {
        val n = count.coerceIn(1, 4)
        val anchor = Offset(width * 0.5f, height * 0.54f)
        if (n == 1) return listOf(anchor)
        val pace = step(height)
        val front = anchor - pace * ((n - 1) / 2f)
        return List(n) { i -> front + pace * i.toFloat() }
    }

    /** Near housing is full. The one at the back falls off. */
    fun gain(index: Int, count: Int): Float {
        val n = count.coerceIn(1, 4)
        if (n <= 1) return 1f
        val t = index.coerceIn(0, n - 1) / (n - 1).toFloat()
        return 1f - 0.52f * t
    }
}

internal fun List<Offset>.closedPath(): Path {
    val path = Path()
    if (isEmpty()) return path
    path.moveTo(first().x, first().y)
    for (i in 1 until size) path.lineTo(this[i].x, this[i].y)
    path.close()
    return path
}
