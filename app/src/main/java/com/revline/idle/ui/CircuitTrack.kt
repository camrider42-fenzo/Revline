package com.revline.idle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Where a circuit sits on the canvas: the layout is scaled to fill it, and the road is a fixed share of the width so two
 * cars fit side by side on every track whatever the size. Distances along the line are in layout units, everything on
 * the canvas is in pixels.
 */
internal class CircuitGeo(val w: Float, val h: Float, id: String) {
    val line: CircuitLine = CircuitLayouts.of(id)
    val roadW = w * line.roadShare
    val carLen = roadW * CAR_OF_ROAD
    /** Where the two lanes run, across the road from its middle. */
    val laneOffset = roadW * LANE_OF_ROAD
    private val padX = roadW * 2.6f
    private val padY = roadW * 2.2f
    val u: Float
    val ox: Float
    val oy: Float
    /** The road's width in layout units. */
    val roadUnits: Float
    /** One car length in layout units. */
    val carUnits: Float
    /** Spare picture, in layout units, after the physical line and its pad are fitted. The drawn line is not fitted again. */
    val slackX: Float
    val slackY: Float
    /** Painted centerline. Same arc as [line], shifted apart only where two stretches would otherwise touch. */
    val draw: CircuitDraw

    init {
        val bw = line.maxX - line.minX
        val bh = line.maxY - line.minY
        u = min((w - 2 * padX) / bw, (h - 2 * padY) / bh)
        ox = (w - bw * u) / 2f - line.minX * u
        oy = (h - bh * u) / 2f - line.minY * u
        roadUnits = roadW / u
        carUnits = carLen / u
        slackX = ((w - 2 * padX) / u - bw).coerceAtLeast(0f)
        slackY = ((h - 2 * padY) / u - bh).coerceAtLeast(0f)
        draw = CircuitDraw.of(line, roadUnits, slackX, slackY)
    }

    private val tmp = FloatArray(3)

    /** Page position of the point [lat] pixels to the right of the drawn line at physical arc [s]. */
    fun point(s: Float, lat: Float): Offset {
        draw.at(s, tmp)
        val h = tmp[2]
        return Offset(ox + tmp[0] * u - sin(h) * lat, oy + tmp[1] * u + cos(h) * lat)
    }

    /** Tangent of the drawn line at physical arc [s]. */
    fun heading(s: Float): Float {
        draw.at(s, tmp)
        return tmp[2]
    }

    /** Curvature of the drawn line, for the racing line, kerbs and steering. */
    fun bend(s: Float): Float = draw.curvature(s)

    /**
     * The racing line, in pixels right of the middle at each layout sample: toward the inside through a corner by up to
     * [RACING_OF_ROAD] of the road, eased in and out over a few road widths so it never kinks.
     */
    private val racing: FloatArray = run {
        val n = line.xs.size
        // The line's shape along the lap follows the real corner, not the small shifts that open a crowded bend.
        // The gain stays near the old road width so a wider ribbon does not throw a car across the lane.
        val sense = roadUnits.coerceAtMost(4.5f)
        val raw = FloatArray(n) { (line.curvature(it * line.step) * sense * 4f).coerceIn(-1f, 1f) }
        fun blur(a: FloatArray, halfUnits: Float): FloatArray {
            val k = max(1, (halfUnits / line.step).toInt())
            return FloatArray(n) { i -> var sum = 0f; for (d in -k..k) sum += a[((i + d) % n + n) % n]; sum / (2 * k + 1) }
        }
        val smooth = blur(blur(raw, sense * 1.5f), sense * 3f)
        FloatArray(n) { smooth[it].coerceIn(-1f, 1f) * roadW * RACING_OF_ROAD }
    }

    /** The racing line's offset from the middle at arc [s], in pixels to the right. */
    fun racingLat(s: Float): Float {
        val n = racing.size
        var f = (s / line.step) % n
        if (f < 0f) f += n
        val i = f.toInt().coerceIn(0, n - 1)
        val t = f - i
        return racing[i] * (1f - t) + racing[(i + 1) % n] * t
    }

    companion object {
        const val CAR_OF_ROAD = 0.8f
        const val LANE_OF_ROAD = 0.24f
        const val RACING_OF_ROAD = 0.28f
    }
}

internal class CircuitScene(val geo: CircuitGeo, val image: ImageBitmap)

/** A car on the circuit: [s] is how far round the lap in layout units, [lat] is pixels to the right of the line. */
internal class CircuitCar(val look: String, val mid: Boolean, val color: Color, val accent: Color, val s: Float, val lat: Float)

private val asphaltLit = Color(0xFF3C4046)
private val asphaltDark = Color(0xFF1C1F23)
private val crowd = intArrayOf(0xFFE4FF47.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF6B5A.toInt(), 0xFF4DFFF0.toInt(), 0xFFFFC14A.toInt(), 0xFF8A94A0.toInt())

internal fun bakeCircuitScene(geo: CircuitGeo, density: Density, title: String, subtitle: String): CircuitScene {
    val img = ImageBitmap(geo.w.toInt().coerceAtLeast(1), geo.h.toInt().coerceAtLeast(1))
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(img), Size(geo.w, geo.h)) { circuitScenery(geo, title, subtitle) }
    return CircuitScene(geo, img)
}

/** Rows on the grid: two cars to a row, room for the whole field and you. */
internal val GRID_ROWS = (com.revline.idle.game.Circuits.FIELD + 2) / 2

/** Arc distance of a grid slot: [row] 0 is the front row, [sign] picks the lane (+1 right), and the left lane sits a little behind. */
internal fun gridArc(geo: CircuitGeo, row: Int, sign: Int): Float =
    -(geo.carUnits * 0.9f + row * geo.carUnits * 1.7f) - (if (sign > 0) 0f else geo.carUnits * 0.28f)

/** The side of the start straight with more room, which gets the pit lane; the stand goes on the other. */
internal fun pitSide(geo: CircuitGeo): Int {
    val line = geo.line
    var best = line.pitSide
    var bestRoom = -1f
    var realRoom = 0f
    for (side in intArrayOf(line.pitSide, -line.pitSide)) {
        var room = Float.MAX_VALUE
        val probe = FloatArray(3)
        val draw = geo.draw
        val left = line.minX - geo.slackX / 2f - 2.6f * geo.roadUnits
        val right = line.maxX + geo.slackX / 2f + 2.6f * geo.roadUnits
        val top = line.minY - geo.slackY / 2f - 2.2f * geo.roadUnits
        val bot = line.maxY + geo.slackY / 2f + 2.2f * geo.roadUnits
        var s = -20f
        while (s <= 20f) {
            draw.at(s, probe)
            val px = probe[0] - side * kotlin.math.sin(probe[2]) * geo.roadUnits * 2.6f
            val py = probe[1] + side * kotlin.math.cos(probe[2]) * geo.roadUnits * 2.6f
            for (i in draw.xs.indices step 3) {
                val arc = (i * line.step - s).let { d -> val m = ((d % line.length) + line.length) % line.length; min(m, line.length - m) }
                if (arc > 24f) room = min(room, hypot(draw.xs[i] - px, draw.ys[i] - py))
            }
            if (px < left || px > right || py < top || py > bot) room = min(room, 0f)
            s += 5f
        }
        // The real pit lane's side wins unless there is no room for it there.
        if (side == line.pitSide) { realRoom = room; best = side; bestRoom = room }
        else if (realRoom < geo.roadUnits * 1.6f && room > bestRoom) { bestRoom = room; best = side }
    }
    return best
}

private fun DrawScope.circuitScenery(geo: CircuitGeo, title: String, subtitle: String) {
    val line = geo.line
    val w = geo.roadW
    val d = density
    val rnd = Random(line.id.hashCode().toLong())
    val side = pitSide(geo)

    /** The line at a lateral offset, over [s0]..[s1], or the whole loop when both are null. */
    fun path(lat: Float, s0: Float? = null, s1: Float? = null): Path {
        val p = Path()
        if (s0 == null || s1 == null) {
            val n = line.xs.size
            var first = true
            var i = 0
            while (i < n) {
                val o = geo.point(i * line.step, lat)
                if (first) { p.moveTo(o.x, o.y); first = false } else p.lineTo(o.x, o.y)
                i += 2
            }
            p.close()
        } else {
            val st = line.step * 2f
            var s = s0
            var first = true
            while (s < s1) {
                val o = geo.point(s, lat)
                if (first) { p.moveTo(o.x, o.y); first = false } else p.lineTo(o.x, o.y)
                s += st
            }
            val o = geo.point(s1, lat)
            if (first) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
        }
        return p
    }

    // Grass, mottled so it is not a flat fill.
    drawRect(Brush.verticalGradient(0f to Color(0xFF0E2016), 1f to Color(0xFF07100B)), Offset.Zero, Size(geo.w, geo.h))
    repeat(60) {
        val c = Offset(rnd.nextFloat() * geo.w, rnd.nextFloat() * geo.h)
        val r = geo.w * (0.04f + rnd.nextFloat() * 0.08f)
        val tint = if (rnd.nextBoolean()) Color(0xFF244A31) else Color.Black
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.16f), Color.Transparent), center = c, radius = r), r, c)
    }
    val blades = ArrayList<Offset>()
    val dark = ArrayList<Offset>()
    repeat((geo.w * geo.h / 260f).toInt()) {
        val o = Offset(rnd.nextFloat() * geo.w, rnd.nextFloat() * geo.h)
        if (rnd.nextInt(3) == 0) blades.add(o) else dark.add(o)
    }
    drawPoints(blades, PointMode.Points, Color(0xFF2B5337).copy(alpha = 0.55f), 1.4f * d, StrokeCap.Round)
    drawPoints(dark, PointMode.Points, Color.Black.copy(alpha = 0.35f), 1.6f * d, StrokeCap.Round)

    // The label goes in whichever corner the road keeps clearest.
    val labelW = max(title.length * 0.7f * w * 1.15f, subtitle.length * 0.6f * w * 0.5f) + w * 1.2f
    val labelH = w * 3.4f
    fun crowded(left: Boolean, top: Boolean): Int {
        val x0 = if (left) 0f else geo.w - labelW - w
        val y0 = if (top) 0f else geo.h - labelH - w * 0.6f
        var hits = 0
        var s = 0f
        while (s < geo.line.length) {
            val p = geo.point(s, 0f)
            if (p.x > x0 - w && p.x < x0 + labelW + w && p.y > y0 - w && p.y < y0 + labelH + w) hits++
            s += 1f
        }
        return hits
    }
    val corners = listOf(true to true, false to true, true to false, false to false)
    val (atLeft, atTop) = corners.minByOrNull { crowded(it.first, it.second) }!!
    val ty = if (atTop) w * 1.9f else geo.h - w * 2.0f - w * 0.9f
    val tx = if (atLeft) w * 1.1f else geo.w - w * 1.1f
    textLabel(title, tx, ty, w * 1.15f, Color.White.copy(alpha = 0.16f), if (atLeft) 0 else 2)
    textLabel(subtitle, tx, ty + w * 1.0f, w * 0.5f, Color.White.copy(alpha = 0.34f), if (atLeft) 0 else 2)

    // Floodlight pools, first so the track sits in them.
    val lights = 10
    for (k in 0 until lights) {
        val s = line.length * (k + 0.3f) / lights
        val outside = if (geo.bend(s) > 0f) -1 else 1
        val sideOf = if (abs(geo.bend(s)) < 0.004f) (if (k % 2 == 0) 1 else -1) else outside
        val c = geo.point(s, sideOf * w * 1.3f)
        val r = w * 3.2f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3C8).copy(alpha = 0.2f), Color.Transparent), center = c, radius = r), r, c)
    }
    val loop = path(0f)
    drawPath(loop, Color(0xFF0A1A11).copy(alpha = 0.9f), style = Stroke(w * 3.1f, join = StrokeJoin.Round))
    drawPath(loop, Color(0xFF181B20), style = Stroke(w * 1.8f, join = StrokeJoin.Round))
    drawPath(loop, Color.White.copy(alpha = 0.05f), style = Stroke(w * 1.8f + 5f * d, join = StrokeJoin.Round))

    // Pit lane and garages on one side of the start straight, the stand on the other.
    val straight = line.startStraight
    var lo = maxOf(straight.start + 5f, -26f)
    var hi = minOf(straight.endInclusive - 5f, 26f)
    if (hi - lo < 14f) { lo = -8f; hi = 8f }
    val laneLat = side * w * 1.3f
    val garageLat = side * w * 1.9f
    val entry = Path().apply {
        val a = geo.point(lo - 7f, side * w * 0.5f)
        val b = geo.point(lo, laneLat)
        moveTo(a.x, a.y)
        val c1 = geo.point(lo - 3f, side * w * 0.5f)
        val c2 = geo.point(lo - 3f, laneLat)
        cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
    }
    val exit = Path().apply {
        val a = geo.point(hi, laneLat)
        val b = geo.point(hi + 7f, side * w * 0.5f)
        moveTo(a.x, a.y)
        val c1 = geo.point(hi + 3f, laneLat)
        val c2 = geo.point(hi + 3f, side * w * 0.5f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
    }
    val lane = path(laneLat, lo, hi)
    val garage = path(garageLat, lo, hi)
    for (p in listOf(entry, exit)) drawPath(p, Brush.verticalGradient(0f to asphaltLit, 1f to asphaltDark), style = Stroke(w * 0.5f, cap = StrokeCap.Butt))
    drawPath(lane, Brush.verticalGradient(0f to asphaltLit, 1f to asphaltDark), style = Stroke(w * 0.52f, cap = StrokeCap.Butt))
    drawPath(lane, Color.White.copy(alpha = 0.55f), style = Stroke(1.3f * d, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * d, 5f * d), 0f)))
    drawPath(garage, Color(0xFF151A22), style = Stroke(w * 0.62f, cap = StrokeCap.Butt))
    drawPath(garage, Color.White.copy(alpha = 0.2f), style = Stroke(1.2f * d, cap = StrokeCap.Butt))
    val doors = path(garageLat - side * w * 0.2f, lo + 1f, hi - 1f)
    val dash = w * 0.2f
    val doorColors = intArrayOf(0xFFFFC14A.toInt(), 0xFF4DFFF0.toInt(), 0xFFFFFFFF.toInt(), 0xFFE4FF47.toInt(), 0xFFFF6B5A.toInt())
    for ((i, c) in doorColors.withIndex()) {
        drawPath(doors, Color(c).copy(alpha = 0.85f), style = Stroke(w * 0.12f, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 4f), -dash * i)))
    }

    val standLat = -side * w * 1.5f
    val stand = path(standLat, -17f, 17f)
    drawPath(stand, Color(0xFF141922), style = Stroke(w * 0.9f, cap = StrokeCap.Butt))
    drawPath(stand, Color.White.copy(alpha = 0.18f), style = Stroke(1.2f * d, cap = StrokeCap.Butt))
    val buckets = Array(crowd.size) { ArrayList<Offset>() }
    repeat(260) {
        val s = -16f + rnd.nextFloat() * 32f
        val lat = standLat + (rnd.nextFloat() - 0.5f) * w * 0.78f
        buckets[rnd.nextInt(crowd.size)].add(geo.point(s, lat))
    }
    for ((i, pts) in buckets.withIndex()) drawPoints(pts, PointMode.Points, Color(crowd[i]).copy(alpha = 0.6f), 1.8f * d, StrokeCap.Round)

    // The road: edge line, tarmac with its two worn racing lines, grain and tar seams.
    drawPath(loop, Brush.verticalGradient(0f to asphaltLit, 1f to asphaltDark), style = Stroke(w, join = StrokeJoin.Round))
    for (sign in intArrayOf(-1, 1)) {
        drawPath(path(sign * geo.laneOffset), Color.Black.copy(alpha = 0.2f), style = Stroke(w * 0.3f, join = StrokeJoin.Round))
    }
    val lit = ArrayList<Offset>()
    val shade = ArrayList<Offset>()
    val n = (line.length * w * 0.75f / (d * 2.2f)).toInt().coerceIn(2000, 14000)
    repeat(n) {
        val o = geo.point(rnd.nextFloat() * line.length, (rnd.nextFloat() - 0.5f) * w * 0.94f)
        if (rnd.nextInt(3) == 0) lit.add(o) else shade.add(o)
    }
    drawPoints(shade, PointMode.Points, Color.Black.copy(alpha = 0.33f), 1.3f * d, StrokeCap.Round)
    drawPoints(lit, PointMode.Points, Color(0xFFB8BEC6).copy(alpha = 0.2f), 1.1f * d, StrokeCap.Round)
    var seam = 12f
    while (seam < line.length) {
        drawLine(Color.Black.copy(alpha = 0.18f), geo.point(seam, -w * 0.5f), geo.point(seam + 1.4f, w * 0.5f), 1.2f * d)
        seam += 38f + rnd.nextFloat() * 26f
    }
    for (sign in intArrayOf(-1, 1)) {
        drawPath(path(sign * (w * 0.5f - 0.05f * w)), Color.White.copy(alpha = 0.86f), style = Stroke(0.05f * w + 0.4f * d, join = StrokeJoin.Round))
    }

    // Kerbs, red and white, on the inside of each flagged corner.
    for (k in line.cornerArc.indices) {
        val s = line.cornerArc[k]
        val sd = line.cornerSide[k]
        val turn = abs(CircuitLine.angleDiff(geo.heading(s - 6f), geo.heading(s + 6f)))
        // Only the apex and exit, never longer than the bend's own inside arc, and pulled in on a radius too tight for it.
        var peak = 0f
        var q = s - 6f
        while (q <= s + 6f) { peak = max(peak, abs(geo.bend(q))); q += 0.5f }
        val radius = if (peak > 1e-4f) 1f / peak else 1e4f
        val total = min((turn * 7f).coerceIn(3f, 9f), turn * radius).coerceAtLeast(1.5f)
        val kw = w * 0.15f
        val kerbLat = min(w * 0.5f + kw * 0.1f, radius * geo.u * 0.8f)
        val kerb = path(sd * kerbLat, s - total * 0.3f, s + total * 0.7f)
        val seg = kw * 1.3f
        drawPath(kerb, Color.White, style = Stroke(kw, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(seg, seg), 0f)))
        drawPath(kerb, Color(0xFFE02A20), style = Stroke(kw, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(seg, seg), seg)))
    }

    // Where the loop crosses itself the road goes over on a bridge with parapets and a shadow.
    if (line.bridgeArc >= 0f) {
        val span = geo.roadUnits * 1.9f
        val deck = path(0f, line.bridgeArc - span, line.bridgeArc + span)
        drawPath(deck, Color.Black.copy(alpha = 0.5f), style = Stroke(w * 1.5f, cap = StrokeCap.Butt))
        drawPath(deck, Color(0xFF7D8791), style = Stroke(w * 1.14f, cap = StrokeCap.Butt))
        drawPath(deck, Brush.verticalGradient(0f to asphaltLit, 1f to asphaltDark), style = Stroke(w * 1.0f, cap = StrokeCap.Butt))
        for (sign in intArrayOf(-1, 1)) {
            drawPath(path(sign * (w * 0.5f - 0.025f * w), line.bridgeArc - span, line.bridgeArc + span), Color.White.copy(alpha = 0.86f), style = Stroke(0.05f * w + 0.4f * d, cap = StrokeCap.Butt))
        }
    }

    // Grid slots behind the line, two to a row, where the lanes run.
    // The slots follow the line back through any bend, each box turned to the local tangent.
    for (row in 0 until GRID_ROWS) {
        for (sign in intArrayOf(-1, 1)) {
            val s = gridArc(geo, row, sign)
            val c = geo.point(s, sign * geo.laneOffset)
            val hd = Math.toDegrees(geo.heading(s).toDouble()).toFloat()
            withTransform({ translate(c.x, c.y); rotate(hd, Offset.Zero) }) {
                val bl = geo.carLen * 1.12f
                val bw = w * 0.44f
                val outline = Color.White.copy(alpha = 0.72f)
                val sw = 1.4f * d
                drawLine(outline, Offset(-bl / 2f, -bw / 2f), Offset(-bl / 2f, bw / 2f), sw)
                drawLine(outline, Offset(-bl / 2f, -bw / 2f), Offset(-bl * 0.18f, -bw / 2f), sw)
                drawLine(outline, Offset(-bl / 2f, bw / 2f), Offset(-bl * 0.18f, bw / 2f), sw)
                drawLine(outline, Offset(bl / 2f, -bw / 2f), Offset(bl * 0.32f, -bw / 2f), sw)
                drawLine(outline, Offset(bl / 2f, bw / 2f), Offset(bl * 0.32f, bw / 2f), sw)
                drawLine(outline, Offset(bl / 2f, -bw / 2f), Offset(bl / 2f, bw / 2f), sw)
            }
        }
    }

    // Start and finish: a checkered line two squares deep.
    val sf = geo.point(0f, 0f)
    withTransform({ translate(sf.x, sf.y); rotate(Math.toDegrees(geo.heading(0f).toDouble()).toFloat(), Offset.Zero) }) {
        val cols = 10
        val cell = w / cols
        for (c in 0 until cols) for (r in 0 until 2) {
            drawRect(if ((c + r) % 2 == 0) Color.White else Color(0xFF0B0B0B), Offset(-cell + r * cell, -w / 2f + c * cell), Size(cell, cell))
        }
        drawRect(Color.Black.copy(alpha = 0.7f), Offset(-cell, -w / 2f), Size(cell * 2f, w), style = Stroke(1.2f * d))
    }

    // Corner numbers on the outside of each bend, thinned where they would run together.
    var last: Offset? = null
    for (k in line.cornerArc.indices) {
        val p = geo.point(line.cornerArc[k], -line.cornerSide[k] * w * 1.15f)
        val prev = last
        if (prev == null || hypot(p.x - prev.x, p.y - prev.y) > w * 1.3f) {
            textLabel((k + 1).toString(), p.x, p.y + w * 0.16f, w * 0.44f, Color.White.copy(alpha = 0.6f), 1)
            last = p
        }
    }
}

internal fun DrawScope.textLabel(text: String, x: Float, baseline: Float, px: Float, color: Color, align: Int) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = when (align) { 1 -> android.graphics.Paint.Align.CENTER; 2 -> android.graphics.Paint.Align.RIGHT; else -> android.graphics.Paint.Align.LEFT }
        textSize = px
        typeface = android.graphics.Typeface.MONOSPACE
        this.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, baseline, paint)
}

/** The baked circuit with cars on it. */
internal fun DrawScope.drawCircuitScene(scene: CircuitScene, cars: List<CircuitCar>) {
    drawImage(scene.image)
    val geo = scene.geo
    for (car in cars) {
        val plan = TopPlan.of(car.look, car.mid)
        val wheelbaseUnits = (plan.frontAxle - plan.rearAxle) / plan.length * geo.carUnits
        val steer = (geo.bend(car.s) * wheelbaseUnits * 1.5f).coerceIn(-0.5f, 0.5f)
        val back = geo.point(car.s - 0.5f, 0f)
        val fore = geo.point(car.s + 0.5f, 0f)
        val along = hypot(fore.x - back.x, fore.y - back.y).coerceAtLeast(1e-3f)
        val yaw = kotlin.math.atan2(geo.racingLat(car.s + 0.5f) - geo.racingLat(car.s - 0.5f), along)
        drawTopCar(car.look, car.mid, car.color, car.accent, geo.point(car.s, car.lat), geo.heading(car.s) + yaw, geo.carLen, steer)
    }
}

/** The close-up: the same scene drawn [zoomFor] times larger, with the picture a window onto it that follows your car. */
internal object CircuitView {
    private const val MAX_ZOOM = 2.4f
    private const val MAX_PIXELS = 6_000_000f

    /** How much bigger the world is than the window in the close-up, held down so the baked picture stays a sane size. */
    fun zoomFor(w: Float, h: Float): Float = min(MAX_ZOOM, kotlin.math.sqrt(MAX_PIXELS / (w * h))).coerceAtLeast(1f)

    /** Top-left of the window, always centered on [focus], so the followed car never jumps when the map edge comes near. */
    fun window(focus: Offset, viewW: Float, viewH: Float): Offset = Offset(focus.x - viewW / 2f, focus.y - viewH / 2f)

    /** What the close-up follows: your car (the last in [cars]) and nothing else. */
    fun focus(geo: CircuitGeo, cars: List<CircuitCar>): Offset = cars.last().let { geo.point(it.s, it.lat) }
}

/** The scene with its cars; in the close-up only the part around your car (the last in [cars]) shows. */
internal fun DrawScope.drawCircuitView(scene: CircuitScene, cars: List<CircuitCar>, viewW: Float, viewH: Float, followYou: Boolean) {
    if (!followYou || scene.geo.w <= viewW + 0.5f) {
        drawCircuitScene(scene, cars)
        return
    }
    val geo = scene.geo
    val o = CircuitView.window(CircuitView.focus(geo, cars), viewW, viewH)
    drawRect(Color(0xFF0B1811), Offset.Zero, Size(viewW, viewH))
    withTransform({ translate(-o.x, -o.y) }) { drawCircuitScene(scene, cars) }
}

internal class CircuitSceneHolder {
    private var key: List<Any> = emptyList()
    private var scene: CircuitScene? = null

    fun get(scope: DrawScope, id: String, title: String, subtitle: String, zoom: Float = 1f): CircuitScene {
        val k = listOf(scope.size.width, scope.size.height, id, title, subtitle, scope.density, zoom)
        val cached = scene
        if (cached != null && k == key) return cached
        val geo = CircuitGeo(scope.size.width * zoom, scope.size.height * zoom, id)
        return bakeCircuitScene(geo, scope, title, subtitle).also { scene = it; key = k }
    }
}
