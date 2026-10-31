package com.revline.idle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.util.Random
import kotlin.math.sin

/**
 * Where everything sits on the strip canvas. Two lanes are stacked like a split screen: the rival's lane in the
 * top half, yours in the bottom half. Each lane is a night scene (sky, hills, grandstand under lights, a pit wall
 * and the asphalt), and each car's front axle runs from its own start line to the shared finish line.
 */
internal class DragGeo(val w: Float, val h: Float, yourId: String, yourMid: Boolean, rivalId: String) {
    val pad = 18f
    val left = pad
    val right = w - pad
    val scale = minOf(h * 0.15f, w * 0.105f).coerceIn(56f, 112f)
    val carPad = (scale * 0.22f).coerceAtLeast(10f)
    val roadH = h * 0.1f
    val wallH = h * 0.042f
    val finishX = right - scale * 0.54f - carPad * 0.5f
    private val anchors = arrayOf(dragAnchor(rivalId, false), dragAnchor(yourId, yourMid))

    fun panelTop(lane: Int) = if (lane == 0) 0f else h / 2f
    fun panelBottom(lane: Int) = if (lane == 0) h / 2f else h
    fun roadTop(lane: Int) = panelBottom(lane) - roadH
    fun wallTop(lane: Int) = roadTop(lane) - wallH
    fun ground(lane: Int) = roadTop(lane) + roadH * 0.4f
    fun startX(lane: Int) = left + carPad + anchors[lane].tailBehindFront * scale
    /** Screen x of the front axle for a given share of the run. */
    fun axleX(lane: Int, progress: Float) = startX(lane) + (finishX - startX(lane)) * progress.coerceIn(0f, 1f)
    fun carX(lane: Int, progress: Float) = axleX(lane, progress) - anchors[lane].frontFromMid * scale
}

internal class DragScene(val geo: DragGeo, val far: ImageBitmap, val mid: ImageBitmap, val near: ImageBitmap) {
    companion object {
        const val FAR_DRIFT = 0.05f
        const val MID_DRIFT = 0.14f
    }
}

private val laneTint = arrayOf(Color(0xFFFF3B30), Color(0xFFE4FF47))
private val crowd = intArrayOf(0xFFE4FF47.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF6B5A.toInt(), 0xFF4DFFF0.toInt(), 0xFFFFC14A.toInt(), 0xFF8A94A0.toInt())

internal fun bakeDragScene(geo: DragGeo, density: Density, finishM: Float): DragScene {
    val w = geo.w.toInt()
    val h = geo.h.toInt()
    val scope = CanvasDrawScope()
    fun make(width: Int, block: DrawScope.() -> Unit): ImageBitmap {
        val img = ImageBitmap(width.coerceAtLeast(1), h.coerceAtLeast(1))
        scope.draw(density, LayoutDirection.Ltr, Canvas(img), Size(width.toFloat(), h.toFloat())) { block() }
        return img
    }
    val farW = (geo.w * (1f + DragScene.FAR_DRIFT)).toInt()
    val midW = (geo.w * (1f + DragScene.MID_DRIFT)).toInt()
    val far = make(farW) { for (lane in 0..1) sky(geo, lane, farW.toFloat()) }
    val mid = make(midW) { for (lane in 0..1) stands(geo, lane, midW.toFloat()) }
    val near = make(w) { for (lane in 0..1) roadside(geo, lane, finishM) }
    return DragScene(geo, far, mid, near)
}

private fun DrawScope.sky(g: DragGeo, lane: Int, w: Float) {
    val top = g.panelTop(lane)
    val horizon = g.wallTop(lane)
    val tint = laneTint[lane]
    val rnd = Random(11L + lane * 7)
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF02040A), 0.5f to lerp(Color(0xFF0A1022), tint, 0.05f), 0.85f to lerp(Color(0xFF1B1630), tint, 0.16f), 1f to lerp(Color(0xFF2B2030), tint, 0.3f),
            startY = top, endY = horizon,
        ),
        Offset(0f, top), Size(w, horizon - top),
    )
    // Stars.
    val stars = List(70) { Offset(rnd.nextFloat() * w, top + rnd.nextFloat() * (horizon - top) * 0.6f) }
    drawPoints(stars, PointMode.Points, Color.White.copy(alpha = 0.55f), 2.2f, StrokeCap.Round)
    drawPoints(stars.filterIndexed { i, _ -> i % 5 == 0 }, PointMode.Points, Color.White.copy(alpha = 0.9f), 3.4f, StrokeCap.Round)
    // Hills and a tree line, then the far-off lights of a town.
    val hillBase = horizon
    val hills = Path().apply {
        moveTo(0f, hillBase)
        var x = 0f
        while (x <= w + 8f) {
            val y = hillBase - g.h * (0.045f + 0.02f * sin(x * 0.006f + lane) + 0.012f * sin(x * 0.019f + 1.7f)) 
            lineTo(x, y)
            x += 8f
        }
        lineTo(w, hillBase); close()
    }
    drawPath(hills, Brush.verticalGradient(0f to Color(0xFF10141B), 1f to Color(0xFF07090D), startY = hillBase - g.h * 0.08f, endY = hillBase))
    val trees = Path()
    var tx = 0f
    while (tx < w) {
        val th = g.h * (0.012f + 0.016f * rnd.nextFloat())
        val tw = th * 0.55f
        val base = hillBase - g.h * 0.012f
        trees.moveTo(tx, base); trees.lineTo(tx + tw / 2f, base - th); trees.lineTo(tx + tw, base); trees.close()
        tx += tw * (0.7f + rnd.nextFloat() * 0.8f)
    }
    drawPath(trees, Color(0xFF05070A))
    val town = List(44) { Offset(rnd.nextFloat() * w, hillBase - g.h * (0.01f + 0.04f * rnd.nextFloat())) }
    drawPoints(town, PointMode.Points, Color(0xFFFFE9B0).copy(alpha = 0.55f), 2.4f, StrokeCap.Round)
}

private fun DrawScope.stands(g: DragGeo, lane: Int, w: Float) {
    val base = g.wallTop(lane) + g.wallH * 0.2f
    val rnd = Random(97L + lane * 13)
    val rows = 7
    val rowH = g.h * 0.024f
    val top = base - rows * rowH
    val x0 = w * 0.05f
    val x1 = w * 0.95f
    // Concrete tiers, darker toward the back.
    for (r in 0 until rows) {
        val y = base - (r + 1) * rowH
        val shade = 0xFF14181F.toInt() + (r * 0x020202)
        drawRect(Color(shade), Offset(x0, y), Size(x1 - x0, rowH))
        drawRect(Color.White.copy(alpha = 0.06f), Offset(x0, y), Size(x1 - x0, 1.5f))
    }
    // The crowd: dots per tier in a handful of colors, so it reads as people and not noise.
    for (c in crowd.indices) {
        val pts = ArrayList<Offset>()
        for (r in 0 until rows) {
            val y = base - (r + 0.5f) * rowH + 1f
            var x = x0 + 4f + rnd.nextFloat() * 6f
            while (x < x1 - 4f) {
                if (rnd.nextInt(crowd.size) == c && rnd.nextFloat() < 0.8f) pts += Offset(x, y)
                x += 7f
            }
        }
        drawPoints(pts, PointMode.Points, Color(crowd[c]).copy(alpha = 0.7f), 4.2f, StrokeCap.Round)
    }
    // Roof with a strip of warm lights under the lip.
    drawRect(Color(0xFF0A0C10), Offset(x0 - 6f, top - g.h * 0.012f), Size(x1 - x0 + 12f, g.h * 0.012f))
    drawRect(Color(0xFFFFD28A).copy(alpha = 0.55f), Offset(x0 - 6f, top - 1.5f), Size(x1 - x0 + 12f, 2f))
    val bulbs = ArrayList<Offset>()
    var bx = x0
    while (bx < x1) { bulbs += Offset(bx, top + 1f); bx += 22f }
    drawPoints(bulbs, PointMode.Points, Color(0xFFFFE6B0), 3.2f, StrokeCap.Round)
    // Light towers on the stands' front edge.
    val towers = 4
    for (i in 0 until towers) {
        val tx = w * (0.14f + i * 0.24f) + (rnd.nextFloat() - 0.5f) * w * 0.04f
        val headY = g.panelTop(lane) + g.h * 0.045f
        drawLine(Color(0xFF0A0C10), Offset(tx, base), Offset(tx, headY), 3f)
        drawLine(Color(0xFF1A1F27), Offset(tx - 1f, base), Offset(tx - 1f, headY), 1f)
        val glow = lerp(Color(0xFFFFEFCF), laneTint[lane], 0.12f)
        drawCircle(Brush.radialGradient(0f to glow.copy(alpha = 0.45f), 1f to Color.Transparent, center = Offset(tx, headY), radius = g.h * 0.16f), g.h * 0.16f, Offset(tx, headY))
        val cone = Path().apply {
            moveTo(tx - 10f, headY + 4f); lineTo(tx + 10f, headY + 4f)
            lineTo(tx + g.h * 0.2f, base); lineTo(tx - g.h * 0.2f, base); close()
        }
        drawPath(cone, Brush.verticalGradient(0f to glow.copy(alpha = 0.11f), 1f to Color.Transparent, startY = headY, endY = base))
        drawRect(Color(0xFF14181F), Offset(tx - 14f, headY - 6f), Size(28f, 12f))
        val lamps = List(4) { Offset(tx - 10.5f + it * 7f, headY) }
        drawPoints(lamps, PointMode.Points, Color.White, 5f, StrokeCap.Round)
    }
}

private fun DrawScope.roadside(g: DragGeo, lane: Int, finishM: Float) {
    val roadTop = g.roadTop(lane)
    val wallTop = g.wallTop(lane)
    val bottom = g.panelBottom(lane)
    val rnd = Random(5L + lane * 31)
    val w = size.width
    // Pit wall: concrete with a cap, joints, and ad boards.
    drawRect(Brush.verticalGradient(0f to Color(0xFF4A4F57), 0.1f to Color(0xFF30343A), 1f to Color(0xFF14171B), startY = wallTop, endY = roadTop), Offset(0f, wallTop), Size(w, g.wallH))
    drawRect(Color(0xFF7A8088), Offset(0f, wallTop), Size(w, 2f))
    var jx = 0f
    while (jx < w) { drawLine(Color.Black.copy(alpha = 0.4f), Offset(jx, wallTop + 2f), Offset(jx, roadTop), 1.2f); jx += g.scale * 1.1f }
    val adColors = intArrayOf(0xFFE4FF47.toInt(), 0xFFFF3B30.toInt(), 0xFF4DFFF0.toInt(), 0xFFFFC14A.toInt(), 0xFFFFFFFF.toInt())
    var ax = g.scale * 0.35f
    while (ax < w - g.scale) {
        val len = g.scale * (0.9f + rnd.nextFloat() * 0.5f)
        val c = Color(adColors[rnd.nextInt(adColors.size)])
        val bh = g.wallH * 0.5f
        val by = wallTop + g.wallH * 0.3f
        drawRect(c.copy(alpha = 0.78f), Offset(ax, by), Size(len, bh))
        drawRect(Color.Black.copy(alpha = 0.55f), Offset(ax + len * 0.1f, by + bh * 0.3f), Size(len * 0.5f, bh * 0.4f))
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(ax + len * 0.65f, by + bh * 0.3f), Size(len * 0.22f, bh * 0.4f))
        ax += len + g.scale * (0.5f + rnd.nextFloat() * 1.2f)
    }
    // Asphalt: lit at the far edge, darker toward you, with grain and tar seams.
    drawRect(
        Brush.verticalGradient(0f to Color(0xFF3C4046), 0.35f to Color(0xFF2A2D32), 1f to Color(0xFF131518), startY = roadTop, endY = bottom),
        Offset(0f, roadTop), Size(w, bottom - roadTop),
    )
    drawRect(Color.Black.copy(alpha = 0.5f), Offset(0f, roadTop), Size(w, 2.5f))
    val light = List(600) { Offset(rnd.nextFloat() * w, roadTop + 3f + rnd.nextFloat() * (bottom - roadTop - 3f)) }
    drawPoints(light, PointMode.Points, Color.White.copy(alpha = 0.12f), 1.6f, StrokeCap.Round)
    val dark = List(500) { Offset(rnd.nextFloat() * w, roadTop + 3f + rnd.nextFloat() * (bottom - roadTop - 3f)) }
    drawPoints(dark, PointMode.Points, Color.Black.copy(alpha = 0.35f), 2f, StrokeCap.Round)
    repeat(5) {
        val sx = rnd.nextFloat() * w
        val sy = roadTop + (bottom - roadTop) * (0.3f + 0.6f * rnd.nextFloat())
        val seam = Path().apply {
            moveTo(sx, sy)
            var x = sx; var y = sy
            repeat(10) { x += 12f + rnd.nextFloat() * 14f; y += (rnd.nextFloat() - 0.5f) * 5f; lineTo(x, y) }
        }
        drawPath(seam, Color.Black.copy(alpha = 0.45f), style = Stroke(1.4f))
    }
    // Lane edge, a dashed guide, and the glossy prep strip + rubber on the launch zone.
    val ground = g.ground(lane)
    drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, bottom - 5f), Offset(w, bottom - 5f), 3f)
    val guideY = ground + g.roadH * 0.3f
    drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, guideY), Offset(w, guideY), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 20f)))
    val sx = g.startX(lane)
    val zone = g.scale * 3.2f
    drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent, startX = sx - g.scale * 0.2f, endX = sx + zone), Offset(sx - g.scale * 0.2f, roadTop + 2f), Size(zone + g.scale * 0.2f, bottom - roadTop - 2f))
    drawRect(Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.06f), 1f to Color.Transparent, startX = sx, endX = sx + zone * 0.7f), Offset(sx, roadTop + 2f), Size(zone * 0.7f, g.roadH * 0.5f))
    repeat(16) {
        val len = zone * (0.3f + rnd.nextFloat() * 0.7f)
        val y = ground + (rnd.nextFloat() - 0.5f) * g.roadH * 0.5f
        val x = sx - g.scale * 0.1f + rnd.nextFloat() * zone * 0.15f
        drawLine(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent, startX = x, endX = x + len), Offset(x, y), Offset(x + len, y), 1.4f + rnd.nextFloat() * 2f)
    }
    // Distance boards on the wall, and ticks on the road at the same spot.
    for (f in listOf(0.25f, 0.5f, 0.75f)) {
        val bx = g.startX(lane) + (g.finishX - g.startX(lane)) * f
        drawLine(Color.White.copy(alpha = 0.5f), Offset(bx, roadTop + 2f), Offset(bx, bottom - 5f), 2f)
        val bw = g.scale * 0.62f
        val bh = g.wallH * 0.62f
        drawRect(Color(0xFFF3F5F7), Offset(bx - bw / 2f, wallTop - bh * 0.35f), Size(bw, bh))
        drawRect(Color.Black.copy(alpha = 0.8f), Offset(bx - bw / 2f, wallTop - bh * 0.35f), Size(bw, bh), style = Stroke(1.5f))
        label("${(finishM * f).toInt()}", bx, wallTop - bh * 0.35f + bh * 0.72f, bh * 0.6f)
    }
    // Start line and staging beams for this lane's car.
    drawRect(Color.White.copy(alpha = 0.9f), Offset(sx - 2f, roadTop + 2f), Size(4f, bottom - roadTop - 7f))
    drawRect(Color.White.copy(alpha = 0.35f), Offset(sx - g.scale * 0.22f, roadTop + 2f), Size(2f, bottom - roadTop - 7f))
    // Finish: a checker strip, and the banner arch behind where the car stops.
    val fx = g.finishX
    val cell = (bottom - roadTop - 7f) / 6f
    for (row in 0 until 6) for (col in 0 until 2) {
        drawRect(if ((row + col) % 2 == 0) Color.White else Color.Black, Offset(fx - cell + col * cell, roadTop + 2f + row * cell), Size(cell, cell))
    }
    val bannerTop = g.panelTop(lane) + g.h * 0.1f
    val post = Color(0xFF5A626C)
    drawLine(post, Offset(fx - cell * 1.6f, wallTop), Offset(fx - cell * 1.6f, bannerTop), 5f)
    drawLine(post, Offset(fx + cell * 1.6f, wallTop), Offset(fx + cell * 1.6f, bannerTop), 5f)
    drawLine(Color.White.copy(alpha = 0.25f), Offset(fx - cell * 1.6f - 1f, wallTop), Offset(fx - cell * 1.6f - 1f, bannerTop), 1.2f)
    val sq = g.h * 0.0125f
    val cols = ((cell * 3.2f) / sq).toInt().coerceAtLeast(4)
    val bx0 = fx - cols * sq / 2f
    for (c in 0 until cols) for (r in 0 until 3) {
        drawRect(if ((c + r) % 2 == 0) Color.White else Color(0xFF0B0B0B), Offset(bx0 + c * sq, bannerTop + r * sq), Size(sq, sq))
    }
    drawRect(Color.Black.copy(alpha = 0.7f), Offset(bx0, bannerTop), Size(cols * sq, sq * 3f), style = Stroke(1.6f))
    drawRect(Color(0xFF14181F), Offset(bx0 - 4f, bannerTop - 5f), Size(cols * sq + 8f, 5f))
}

private fun DrawScope.label(text: String, cx: Float, baseline: Float, px: Float) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = px
        typeface = android.graphics.Typeface.MONOSPACE
        color = android.graphics.Color.BLACK
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, cx, baseline, paint)
}

/** Everything the strip needs from a race frame; physics stays where it was. */
internal class DragFrame(
    val distance: Float,
    val rival: Float,
    val finish: Float,
    val slip: Float,
    val speed: Float,
    val clock: Float,
    val gasYou: Float,
    val gasRival: Float,
    val countdown: Float,
    val lights: Float,
    val done: Boolean,
    val won: Boolean,
    val jumped: Boolean,
)

/** Things that smooth out between frames: body pitch from acceleration, spinning turns, rubber laid down. */
internal class DragMemory {
    val pitch = FloatArray(2)
    val speed = FloatArray(2)
    val spun = FloatArray(2)
    val rubber = FloatArray(MAX_BINS)
    private var lastClock = Float.NaN
    private var lastRival = 0f
    private var lastYour = 0f
    private var lastSpeed = FloatArray(2)

    fun step(g: DragGeo, f: DragFrame) {
        val dt = f.clock - lastClock
        if (lastClock.isNaN() || dt < 0f) {
            lastClock = f.clock; lastRival = f.rival; lastYour = f.speed
            speed[0] = 0f; speed[1] = f.speed; lastSpeed[0] = 0f; lastSpeed[1] = f.speed
            return
        }
        if (dt < 1e-4f) return
        lastClock = f.clock
        if (dt > 0.25f) { lastRival = f.rival; lastSpeed[1] = f.speed; return }
        val rivalV = (f.rival - lastRival) / dt
        lastRival = f.rival
        speed[0] += (rivalV - speed[0]) * minOf(1f, dt * 10f)
        speed[1] = f.speed
        for (lane in 0..1) {
            val accel = (speed[lane] - lastSpeed[lane]) / dt
            lastSpeed[lane] = speed[lane]
            val target = (accel * 0.0045f).coerceIn(-0.035f, 0.085f)
            pitch[lane] += (target - pitch[lane]) * minOf(1f, dt * 7f)
        }
        spun[1] = (spun[1] + f.slip * dt * 60f) % (Math.PI.toFloat() * 2f)
        if (f.slip > 0.05f) {
            val x = g.carX(1, f.distance / f.finish) + rearOffset * g.scale
            val bin = ((x / g.w) * rubber.size).toInt().coerceIn(0, rubber.size - 1)
            rubber[bin] = minOf(1f, rubber[bin] + f.slip * dt * 4f)
            if (bin + 1 < rubber.size) rubber[bin + 1] = minOf(1f, rubber[bin + 1] + f.slip * dt * 2f)
        }
    }

    var rearOffset = 0f

    companion object { const val MAX_BINS = 160 }
}

internal fun DrawScope.drawDragScene(
    scene: DragScene,
    f: DragFrame,
    mem: DragMemory,
    yourId: String,
    yourMid: Boolean,
    rivalId: String,
) {
    val g = scene.geo
    mem.rearOffset = dragRearFromMid(yourId, yourMid)
    mem.step(g, f)
    val progress = floatArrayOf((f.rival / f.finish).coerceIn(0f, 1f), (f.distance / f.finish).coerceIn(0f, 1f))
    for (lane in 0..1) {
        clipRect(0f, g.panelTop(lane), g.w, g.panelBottom(lane)) {
            drawImage(scene.far, Offset(-progress[lane] * g.w * DragScene.FAR_DRIFT, 0f))
            drawImage(scene.mid, Offset(-progress[lane] * g.w * DragScene.MID_DRIFT, 0f))
        }
    }
    drawImage(scene.near, Offset.Zero)
    // Finish glows when the run is over: lime for a win, red for a loss.
    if (f.done) {
        val c = if (f.won) Color(0xFFE4FF47) else Color(0xFFFF3B30)
        for (lane in 0..1) {
            glowLine(c, Offset(g.finishX, g.wallTop(lane)), Offset(g.finishX, g.panelBottom(lane) - 5f), 2.4f, 1f)
            val banner = g.panelTop(lane) + g.h * 0.1f - 8f
            glowLine(c, Offset(g.finishX - 26f, banner), Offset(g.finishX + 26f, banner), 3f, 1f)
        }
    }
    // Rubber laid down by the player's wheelspin.
    val binW = g.w / mem.rubber.size
    for (i in mem.rubber.indices) {
        val d = mem.rubber[i]
        if (d < 0.03f) continue
        drawRect(Color.Black.copy(alpha = 0.7f * d), Offset(i * binW, g.ground(1) - 3f), Size(binW + 0.5f, 6f))
    }
    for (lane in 0..1) {
        val id = if (lane == 0) rivalId else yourId
        val mid = lane == 1 && yourMid
        val sx = g.startX(lane)
        // Pre-stage post with its three lamps.
        val postTop = g.wallTop(lane) - g.h * 0.075f
        drawRect(Color(0xFF0C0E12), Offset(sx - 1.5f, postTop), Size(3f, g.wallTop(lane) + g.wallH * 0.4f - postTop))
        val lampColor = when {
            f.jumped -> Color(0xFFFF3B30)
            f.countdown > 0f || f.lights > 0f -> Color(0xFFFFC14A)
            else -> Color(0xFF4DFF6A)
        }
        for (k in 0 until 3) {
            val c = Offset(sx, postTop + 3f + k * 7f)
            drawCircle(lampColor.copy(alpha = 0.25f), 6f, c)
            drawCircle(lampColor, 2.6f, c)
        }
        val color = if (lane == 0) Color(0xFFFF3B30) else Color(0xFFE4FF47)
        val accent = if (lane == 0) Color.White else Color(0xFF101010)
        drawDragCar(
            id, mid, color, accent,
            CarPose(
                x = g.carX(lane, progress[lane]),
                ground = g.ground(lane),
                scale = g.scale,
                pitch = mem.pitch[lane],
                distance = if (lane == 0) f.rival else f.distance,
                slip = if (lane == 0) 0f else f.slip,
                speed = if (lane == 0) mem.speed[0].coerceAtLeast(0f) else f.speed,
                time = f.clock,
                gas = if (lane == 0) f.gasRival else f.gasYou,
                spun = mem.spun[lane],
            ),
        )
    }
}

/** A christmas tree: dark housing, bezels, and lamps that glow when lit and sit faint when not. */
internal fun DrawScope.drawDragTree(colors: List<Color>, lit: List<Boolean>, center: Offset, lamp: Float, xs: List<Float>) {
    val left = size.width * (xs.first() - 0.07f)
    val right = size.width * (xs.last() + 0.07f)
    val top = center.y - lamp * 1.45f
    val h = lamp * 2.9f
    drawRoundRect(
        Brush.verticalGradient(0f to Color(0xFF30353C), 0.5f to Color(0xFF14171B), 1f to Color(0xFF0A0B0E), startY = top, endY = top + h),
        Offset(left, top), Size(right - left, h), androidx.compose.ui.geometry.CornerRadius(lamp * 0.8f),
    )
    drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(left, top), Size(right - left, h), androidx.compose.ui.geometry.CornerRadius(lamp * 0.8f), style = Stroke(1.5f))
    xs.forEachIndexed { i, at ->
        val c = Offset(size.width * at, center.y)
        drawCircle(Brush.verticalGradient(0f to Color(0xFF6A7078), 1f to Color(0xFF1B1E23), startY = c.y - lamp * 1.2f, endY = c.y + lamp * 1.2f), lamp * 1.2f, c)
        if (lit[i]) {
            drawCircle(Brush.radialGradient(0f to colors[i].copy(alpha = 0.5f), 1f to Color.Transparent, center = c, radius = lamp * 2.6f), lamp * 2.6f, c)
            drawCircle(Brush.radialGradient(0f to Color.White, 0.35f to colors[i], 1f to lerp(colors[i], Color.Black, 0.45f), center = c - Offset(lamp * 0.2f, lamp * 0.25f), radius = lamp * 1.1f), lamp, c)
        } else {
            drawCircle(colors[i].copy(alpha = 0.14f), lamp, c)
            drawCircle(Color.Black.copy(alpha = 0.6f), lamp, c, style = Stroke(1.2f))
        }
    }
}

/** Bakes the scenery once per size and race, off the per-frame path. */
internal class DragSceneHolder {
    private var key: List<Any> = emptyList()
    private var scene: DragScene? = null

    fun get(scope: DrawScope, yourId: String, yourMid: Boolean, rivalId: String, finishM: Float): DragScene {
        val k = listOf(scope.size.width, scope.size.height, yourId, yourMid, rivalId, finishM)
        val cached = scene
        if (cached != null && k == key) return cached
        val geo = DragGeo(scope.size.width, scope.size.height, yourId, yourMid, rivalId)
        return bakeDragScene(geo, scope, finishM).also { scene = it; key = k }
    }
}
