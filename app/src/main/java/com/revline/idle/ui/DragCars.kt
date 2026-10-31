package com.revline.idle.ui

import kotlin.math.sin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp

/** Paths for one chassis, built once in car units (y up) and reused by every frame. */
internal class DragPaths(val look: DragLook) {
    val body = rounded(look.body)
    val glass = look.glass.map { rounded(it) }
    val glassBox = look.glass.map { box(it, 3) }
    val cuts = look.cuts.map { line(it) }
    val fins = look.fins.map { poly(it) }
    val bodyTop = look.body.let { b -> (0 until b.size / 3).maxOf { b[it * 3 + 1] } }
    val glassTop = look.glass.maxOfOrNull { g -> (0 until g.size / 3).maxOf { g[it * 3 + 1] } } ?: look.body.let { bodyTop }

    companion object {
        private val built = HashMap<DragLook, DragPaths>()
        fun of(look: DragLook): DragPaths = built.getOrPut(look) { DragPaths(look) }

        fun rounded(v: FloatArray): Path {
            val n = v.size / 3
            val path = Path()
            fun x(i: Int) = v[(i % n) * 3]
            fun y(i: Int) = v[(i % n) * 3 + 1]
            fun k(i: Int) = v[(i % n) * 3 + 2].coerceIn(0f, 0.5f)
            fun a(i: Int) = Offset(x(i) + (x(i - 1 + n) - x(i)) * k(i), y(i) + (y(i - 1 + n) - y(i)) * k(i))
            fun b(i: Int) = Offset(x(i) + (x(i + 1) - x(i)) * k(i), y(i) + (y(i + 1) - y(i)) * k(i))
            path.moveTo(b(0).x, b(0).y)
            for (i in 1 until n) {
                path.lineTo(a(i).x, a(i).y)
                path.quadraticTo(x(i), y(i), b(i).x, b(i).y)
            }
            path.lineTo(a(0).x, a(0).y)
            path.quadraticTo(x(0), y(0), b(0).x, b(0).y)
            path.close()
            return path
        }

        private fun box(v: FloatArray, stride: Int): Rect {
            var l = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = Float.MAX_VALUE; var t = -Float.MAX_VALUE
            for (i in 0 until v.size / stride) {
                l = minOf(l, v[i * stride]); r = maxOf(r, v[i * stride]); b = minOf(b, v[i * stride + 1]); t = maxOf(t, v[i * stride + 1])
            }
            return Rect(l, b, r, t)
        }

        fun line(v: FloatArray): Path = Path().apply {
            moveTo(v[0], v[1])
            var i = 2
            while (i + 1 < v.size) { lineTo(v[i], v[i + 1]); i += 2 }
        }

        fun poly(v: FloatArray): Path = line(v).apply { close() }
    }
}

/** Where a car is and what it is doing this frame. Distances are meters, angles are radians. */
internal class CarPose(
    val x: Float,
    val ground: Float,
    val scale: Float,
    val pitch: Float,
    val distance: Float,
    val slip: Float,
    val speed: Float,
    val time: Float,
    val gas: Float,
    /** Extra turns on the driven wheels while they spin, radians. */
    val spun: Float = 0f,
)

internal fun DrawScope.drawDragCar(id: String, mid: Boolean, color: Color, accent: Color, pose: CarPose) {
    val look = DragLooks.of(id, mid)
    val paths = DragPaths.of(look)
    val model = Wireframe.of(id, mid)
    val s = pose.scale
    val px = 1f / s
    val rear = model.axles.minBy { it[0] }
    val front = model.axles.maxBy { it[0] }
    val tailX = model.lines.minOf { minOf(it[0], it[2]) }
    val noseX = model.lines.maxOf { maxOf(it[0], it[2]) }
    val midX = (tailX + noseX) / 2f
    val spinning = pose.slip > 0.02f
    val ox = pose.x - midX * s
    fun at(nx: Float, ny: Float) = Offset(ox + nx * s, pose.ground - ny * s)
    if (pose.gas > 0.04f) {
        drawPuffs(at(tailX, 0.22f), pose.time * 1.3f, s, false, pose.gas, pose.speed)
    }
    if (spinning) {
        drawPuffs(at(rear[0], rear[1]), pose.time, s, true, pose.slip.coerceIn(0.15f, 1f), pose.speed)
    }
    withTransform({
        translate(ox, pose.ground)
        scale(s, -s, Offset.Zero)
    }) {
        // Contact shadow, then the lit body on top of it.
        val shadowW = (noseX - tailX) * 0.62f
        drawOval(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), center = Offset(midX, 0f), radius = shadowW),
            topLeft = Offset(midX - shadowW, -0.05f), size = Size(shadowW * 2f, 0.1f),
        )
        withTransform({ rotate(Math.toDegrees(pose.pitch.toDouble()).toFloat(), Offset(rear[0], 0f)) }) {
            drawBody(look, paths, color, accent, rear, front, model.tire, noseX, tailX, px, pose)
        }
    }
}

private fun DrawScope.drawBody(
    look: DragLook,
    paths: DragPaths,
    color: Color,
    accent: Color,
    rear: FloatArray,
    front: FloatArray,
    tire: Float,
    noseX: Float,
    tailX: Float,
    px: Float,
    pose: CarPose,
) {
    val rearR = tire * 1.14f
    val frontR = tire * 0.94f
    val top = paths.bodyTop
    val lit = lerp(color, Color.White, 0.32f)
    val shade = lerp(color, Color.Black, 0.72f)
    val paint = Brush.verticalGradient(
        0f to lit, 0.38f to color, 1f to shade,
        startY = top, endY = 0.12f,
    )
    // Fins and wing stands sit behind the body so the paint overlaps their roots.
    for (fin in paths.fins) drawPath(fin, paint)
    look.wing?.let { w -> if (w[2] > 0f) wingStands(w, color, px) }
    look.hoop?.let { h ->
        drawLine(lerp(color, Color.Black, 0.55f), Offset(h[0], h[1]), Offset(h[0] - 0.03f, h[2]), px * 3.2f, StrokeCap.Round)
    }
    // Neon rim: the team color glows round the whole silhouette, like the old wireframe did.
    glowPath(color, paths.body, px * 1.7f, 0.85f)
    drawPath(paths.body, paint)
    clipPath(paths.body) {
        look.stripe?.let { st ->
            drawRect(accent.copy(alpha = 0.9f), Offset(tailX - 0.1f, st[0]), Size(noseX - tailX + 0.2f, st[1] - st[0]))
        }
        // Rocker and bumper shade.
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f), startY = 0.42f, endY = 0.1f),
            Offset(tailX - 0.1f, 0.05f), Size(noseX - tailX + 0.2f, 0.4f),
        )
        // Wheel wells.
        for ((ax, r) in listOf(rear[0] to rearR, front[0] to frontR)) {
            val well = r * 1.2f * look.skirt.coerceAtLeast(1f).let { if (ax == rear[0]) it else 1f }
            drawCircle(Color(0xFF05070A), well, Offset(ax, r))
            drawCircle(Color.White.copy(alpha = 0.18f), well, Offset(ax, r), style = Stroke(px * 1.4f))
        }
        for (cut in paths.cuts) drawPath(cut, Color.Black.copy(alpha = 0.55f), style = Stroke(px * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    look.louvers?.let { l ->
        val n = l[3].toInt()
        for (i in 0 until n) {
            val lx = l[0] + (l[1] - l[0]) * (i + 0.5f) / n
            drawLine(Color.Black.copy(alpha = 0.6f), Offset(lx, l[2]), Offset(lx - 0.05f, l[2] + 0.09f), px * 2f, StrokeCap.Round)
        }
    }
    look.scoop?.let { sc ->
        val p = Path().apply {
            moveTo(sc[0], sc[1]); lineTo(sc[0] + sc[2] * 0.18f, sc[1] + sc[3]); lineTo(sc[0] + sc[2], sc[1] + sc[3] * 0.7f); lineTo(sc[0] + sc[2], sc[1]); close()
        }
        drawPath(p, Color(0xFF05070A))
        drawPath(p, Color.White.copy(alpha = 0.3f), style = Stroke(px * 1.2f))
    }
    // Glass with a diagonal glint.
    paths.glass.forEachIndexed { i, g ->
        val box = paths.glassBox[i]
        drawPath(g, Brush.verticalGradient(0f to Color(0xFF3A5568), 1f to Color(0xFF070D14), startY = box.top, endY = box.bottom))
        clipPath(g) {
            val w = box.width
            val glint = Path().apply {
                moveTo(box.left + w * 0.18f, box.bottom); lineTo(box.left + w * 0.34f, box.bottom)
                lineTo(box.left + w * 0.52f, box.top); lineTo(box.left + w * 0.36f, box.top); close()
            }
            drawPath(glint, Color.White.copy(alpha = 0.22f))
        }
        drawPath(g, Color.Black.copy(alpha = 0.7f), style = Stroke(px * 1.2f))
    }
    look.helmet?.let { h ->
        // Shoulders in a dark suit, then the helmet and its visor.
        drawOval(Color(0xFF1A2230), Offset(h[0] - 0.17f, h[1] - 0.2f), Size(0.2f, 0.2f))
        drawCircle(Brush.verticalGradient(0f to Color.White, 1f to Color(0xFF8A94A0), startY = h[1] + 0.1f, endY = h[1] - 0.1f), 0.1f, Offset(h[0], h[1]))
        drawOval(Color(0xFF0B1620), Offset(h[0] + 0.03f, h[1] - 0.01f), Size(0.07f, 0.04f))
    }
    if (look.helmet == null && paths.glass.isNotEmpty()) {
        val g = paths.glassBox.first()
        drawOval(lerp(color, Color.White, 0.15f), Offset(g.right - 0.015f, g.bottom - 0.005f), Size(0.045f, 0.03f))
    }
    // Shoulder highlight, the one bright edge on top of the paint.
    drawPath(
        paths.body,
        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.75f), 1f to Color.Transparent, startY = top, endY = top - 0.22f),
        style = Stroke(px * 1.5f, join = StrokeJoin.Round),
    )
    if (look.flares) {
        for ((ax, r) in listOf(rear[0] to rearR, front[0] to frontR)) {
            val arc = r * 1.34f
            drawArc(
                Brush.verticalGradient(0f to lit, 1f to shade, startY = r + arc, endY = r), 0f, 180f, false,
                Offset(ax - arc, r - arc), Size(arc * 2f, arc * 2f), style = Stroke(r * 0.3f, cap = StrokeCap.Round),
            )
        }
    }
    if (look.bumpers) {
        val chrome = Brush.verticalGradient(0f to Color.White, 1f to Color(0xFF6A7580), startY = 0.4f, endY = 0.14f)
        val rb = Offset(tailX - 0.025f, 0.2f)
        drawRoundRect(chrome, rb, Size(0.06f, 0.2f), androidx.compose.ui.geometry.CornerRadius(0.02f))
        drawRoundRect(chrome, Offset(noseX - 0.035f, 0.2f), Size(0.06f, 0.2f), androidx.compose.ui.geometry.CornerRadius(0.02f))
    }
    look.pipes?.let { p ->
        drawLine(Color(0xFF9AA4AE), Offset(p[0], p[2]), Offset(p[1], p[2]), px * 3.4f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.6f), Offset(p[0], p[2] + px), Offset(p[1], p[2] + px), px * 1.2f, StrokeCap.Round)
    }
    look.wing?.let { w -> wingPlank(w, color, px) }
    // Lights: tail strip, headlamp and a faint beam down the lane.
    val t = look.tail
    drawRoundRect(Color(0xFFFF2A1E), Offset(tailX + 0.005f, t[0] - t[1] / 2f), Size(0.05f, t[1]), androidx.compose.ui.geometry.CornerRadius(0.015f))
    val l = look.lamp
    val beam = Path().apply {
        moveTo(l[0] + l[2], l[1] + l[3] * 0.4f); lineTo(l[0] + 0.55f, l[1] + 0.08f); lineTo(l[0] + 0.55f, l[1] - 0.16f); lineTo(l[0] + l[2], l[1] - l[3] * 0.4f); close()
    }
    drawPath(beam, Brush.horizontalGradient(0f to Color(0xFFFFF3C8).copy(alpha = 0.16f), 1f to Color.Transparent, startX = l[0], endX = l[0] + 0.55f))
    drawOval(Color(0xFFFFF6D6), Offset(l[0] - l[2], l[1] - l[3]), Size(l[2] * 2f, l[3] * 2f))
    // Slicks behind, skinny fronts ahead; each turns by the road it has covered.
    val roll = -pose.distance / METERS_PER_UNIT
    val rearSlip = pose.slip.coerceIn(0f, 1f)
    val launch = (pose.pitch * 22f).coerceIn(0f, 1f)
    val rearOmega = pose.speed / (rearR * METERS_PER_UNIT) + rearSlip * 70f
    drawDragWheel(Offset(rear[0], rearR), rearR, roll / rearR - pose.spun, rearOmega, true, color, launch, px)
    drawDragWheel(Offset(front[0], frontR), frontR, roll / frontR, pose.speed / (frontR * METERS_PER_UNIT), false, accent, 0f, px)
}

private fun DrawScope.wingStands(w: FloatArray, color: Color, px: Float) {
    val dark = lerp(color, Color.Black, 0.6f)
    drawLine(dark, Offset(w[0] + 0.07f, w[1]), Offset(w[0] + 0.07f, w[1] + w[2]), px * 3f)
    drawLine(dark, Offset(w[0] + w[3] - 0.07f, w[1]), Offset(w[0] + w[3] - 0.07f, w[1] + w[2]), px * 3f)
}

private fun DrawScope.wingPlank(w: FloatArray, color: Color, px: Float) {
    val y = w[1] + w[2]
    val lit = lerp(color, Color.White, 0.3f)
    val shade = lerp(color, Color.Black, 0.6f)
    drawRoundRect(
        Brush.verticalGradient(0f to lit, 1f to shade, startY = y + 0.05f, endY = y),
        Offset(w[0], y), Size(w[3], 0.045f), androidx.compose.ui.geometry.CornerRadius(0.02f),
    )
}


/** Where the front axle sits against the center of the drawing, and how far the tail trails it, in car units. */
internal class CarAnchor(val frontFromMid: Float, val tailBehindFront: Float, val noseAheadOfFront: Float)

internal fun dragAnchor(id: String, mid: Boolean): CarAnchor {
    val model = Wireframe.of(id, mid)
    val front = model.axles.maxBy { it[0] }[0]
    val tail = model.lines.minOf { minOf(it[0], it[2]) }
    val nose = model.lines.maxOf { maxOf(it[0], it[2]) }
    return CarAnchor(front - (tail + nose) / 2f, front - tail, nose - front)
}

internal fun dragRearFromMid(id: String, mid: Boolean): Float {
    val model = Wireframe.of(id, mid)
    val a = dragAnchor(id, mid)
    return model.axles.minOf { it[0] } - (model.axles.maxOf { it[0] } - a.frontFromMid)
}

/** Soft round puffs that drift back and fade: grey wisps from the pipes, thick white tire smoke when the wheels spin. */
private fun DrawScope.drawPuffs(origin: Offset, time: Float, scale: Float, tire: Boolean, amount: Float, speed: Float) {
    val count = if (tire) 7 + (amount * 7f).toInt() else 6
    val trail = 1f + (speed / 45f).coerceIn(0f, 1f)
    for (i in 0 until count) {
        val phase = ((time * if (tire) 1.5f else 1.9f) + i / count.toFloat()) % 1f
        val back = (0.12f + phase) * scale * (if (tire) 1.5f else 0.95f) * trail
        val up = phase * phase * scale * (if (tire) 0.6f else 0.26f)
        val sway = sin(time * 3f + i * 1.7f) * scale * 0.05f * phase
        val c = origin + Offset(-back + sway, -up)
        val r = (0.03f + phase * (if (tire) 0.14f + 0.12f * amount else 0.08f)) * scale
        val a = (1f - phase) * (1f - phase) * amount * (if (tire) 0.42f else 0.2f)
        val ink = if (tire) Color(0xFFE6EAEE) else Color(0xFF9AA4AE)
        drawCircle(ink.copy(alpha = a * 0.5f), r, c)
        drawCircle(ink.copy(alpha = a), r * 0.6f, c)
    }
}
