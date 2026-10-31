package com.revline.idle.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The drag cars seen from above. The plan outline, cabin, wing, scoop, hoop, helmet and lamps are all worked out from
 * the same [DragLook] numbers as the side view, so a chassis is the same car from either angle: it keeps its length, its
 * wheelbase, where the glass sits and how blunt or pointed its ends are. Car units: x runs nose-ward, y is across the
 * car, and the plan is mirrored about y = 0.
 */
internal class TopPlan(val id: String, mid: Boolean) {
    val look: DragLook = DragLooks.of(id, mid)
    private val model = Wireframe.of(id, mid)
    val tailX: Float = model.lines.minOf { minOf(it[0], it[2]) }
    val noseX: Float = model.lines.maxOf { maxOf(it[0], it[2]) }
    val length = noseX - tailX
    val midX = (tailX + noseX) / 2f
    val tire = model.tire
    val rearAxle: Float = model.axles.minBy { it[0] }[0]
    val frontAxle: Float = model.axles.maxBy { it[0] }[0]
    val rearR = tire * 1.14f
    val frontR = tire * 0.94f
    /** Width over length, a little different for each kind of car. */
    val width: Float = length * (widths[id] ?: 0.42f)
    val half = width / 2f
    private val bodyTop: Float = (0 until look.body.size / 3).maxOf { look.body[it * 3 + 1] }
    /** 0 for a sharp low nose, 1 for a tall bluff one; the same for the tail. */
    private val noseBluff = (top(noseX - length * 0.07f) / bodyTop).coerceIn(0f, 1f)
    private val tailBluff = (top(tailX + length * 0.07f) / bodyTop).coerceIn(0f, 1f)
    private val noseP = 2.2f + 2.3f * noseBluff
    private val tailP = 2.4f + 2.3f * tailBluff

    /** Half the width of the body at x. */
    fun hw(x: Float): Float {
        val u = ((x - tailX) / length).coerceIn(0f, 1f)
        val t = (2f * u - 1f)
        val p = if (t >= 0f) noseP else tailP
        return half * (1f - abs(t).pow(p)).coerceAtLeast(0f).pow(1f / p)
    }

    /** The silhouette's height at x, so the plan can read the side view for how blunt the ends are. */
    private fun top(x: Float): Float {
        val b = look.body
        val n = b.size / 3
        var best = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            val x1 = b[i * 3]; val y1 = b[i * 3 + 1]; val x2 = b[j * 3]; val y2 = b[j * 3 + 1]
            if ((x - x1) * (x - x2) <= 0f && x1 != x2) best = maxOf(best, y1 + (y2 - y1) * (x - x1) / (x2 - x1))
            else if (x1 == x2 && abs(x - x1) < 1e-4f) best = maxOf(best, y1, y2)
        }
        return best
    }

    val body: Path = Path().apply {
        val m = 40
        for (i in 0..m) {
            val x = tailX + length * i / m
            if (i == 0) moveTo(x, -hw(x)) else lineTo(x, -hw(x))
        }
        for (i in m downTo 0) {
            val x = tailX + length * i / m
            lineTo(x, hw(x))
        }
        close()
    }

    val glassX0: Float = look.glass.minOfOrNull { g -> (0 until g.size / 3).minOf { g[it * 3] } } ?: (midX - length * 0.1f)
    val glassX1: Float = look.glass.maxOfOrNull { g -> (0 until g.size / 3).maxOf { g[it * 3] } } ?: (midX + length * 0.1f)
    val open: Boolean = look.helmet != null

    /** Roof panel, and the windshield and rear window in front of and behind it. */
    val roof: Path
    val windshield: Path
    val rearGlass: Path

    init {
        val gl = glassX1 - glassX0
        val rf = 0.34f
        val wsX = glassX1 - gl * 0.3f
        val rwX = glassX0 + gl * 0.2f
        fun c(x: Float, k: Float) = hw(x) * k
        roof = DragPaths.rounded(floatArrayOf(
            rwX, -c(rwX, 0.76f), rf, wsX, -c(wsX, 0.78f), rf, wsX, c(wsX, 0.78f), rf, rwX, c(rwX, 0.76f), rf,
        ))
        windshield = DragPaths.rounded(floatArrayOf(
            wsX, -c(wsX, 0.78f), 0.1f, glassX1, -c(glassX1, 0.6f), 0.25f, glassX1, c(glassX1, 0.6f), 0.25f, wsX, c(wsX, 0.78f), 0.1f,
        ))
        rearGlass = DragPaths.rounded(floatArrayOf(
            glassX0, -c(glassX0, 0.66f), 0.25f, rwX, -c(rwX, 0.76f), 0.1f, rwX, c(rwX, 0.76f), 0.1f, glassX0, c(glassX0, 0.66f), 0.25f,
        ))
    }

    companion object {
        private val built = HashMap<String, TopPlan>()
        fun of(id: String, mid: Boolean = false): TopPlan = synchronized(built) { built.getOrPut("$id/$mid") { TopPlan(id, mid) } }

        /** Width over length. Short fat cars are wide for their length, long GTs are slim. */
        val widths: Map<String, Float> = mapOf(
            "isetta" to 0.5f, "cv2" to 0.5f, "kr200" to 0.5f, "p50" to 0.5f, "fiat500" to 0.46f, "mini" to 0.45f, "mule" to 0.4f,
            "saab96" to 0.43f, "sprite" to 0.44f, "amazon" to 0.4f, "elan" to 0.42f, "gta" to 0.4f, "b2002" to 0.4f, "seven" to 0.46f,
            "r5" to 0.44f, "r5mid" to 0.46f, "etype" to 0.36f, "stratos" to 0.46f, "p911" to 0.4f, "vette" to 0.4f, "gt40" to 0.44f,
            "cobra" to 0.43f, "sel63" to 0.38f, "p917" to 0.42f, "daytona" to 0.37f, "jensen" to 0.39f,
        )
    }
}

private val rubber = Color(0xFF0A0A0C)
private val sidewall = Color(0xFF1E2024)
private val alloy = Color(0xFFC9D1D9)
private val alloyShade = Color(0xFF5B646E)

/**
 * One car from above at [heading] radians (0 points right, clockwise is positive, the page's y runs down) with its
 * middle at [center]. [lengthPx] is the whole car, nose to tail. [steer] turns the front wheels.
 */
internal fun DrawScope.drawTopCar(
    id: String,
    mid: Boolean,
    color: Color,
    accent: Color,
    center: Offset,
    heading: Float,
    lengthPx: Float,
    steer: Float = 0f,
    beams: Boolean = true,
) {
    val plan = TopPlan.of(id, mid)
    val sc = lengthPx / plan.length
    val px = 1f / sc
    val deg = Math.toDegrees(heading.toDouble()).toFloat()
    withTransform({
        translate(center.x, center.y)
        rotate(deg, Offset.Zero)
        scale(sc, sc, Offset.Zero)
        translate(-plan.midX, 0f)
    }) {
        topShadow(plan, heading, sc)
        if (beams) topBeams(plan)
        topWheels(plan, steer, px)
        topBody(plan, color, accent, px)
    }
}

private fun DrawScope.topShadow(plan: TopPlan, heading: Float, sc: Float) {
    // The light sits up and to the left of the page, so the shadow falls down and right whichever way the car points.
    val sx = 0.05f * plan.length
    val sy = 0.07f * plan.length
    val c = cos(heading); val s = sin(heading)
    val ox = c * sx + s * sy
    val oy = -s * sx + c * sy
    for ((grow, a) in listOf(1.1f to 0.16f, 1.05f to 0.2f, 1.0f to 0.26f)) {
        withTransform({
            translate(plan.midX + ox, oy)
            scale(grow, grow, Offset.Zero)
            translate(-plan.midX, 0f)
        }) { drawPath(plan.body, Color.Black.copy(alpha = a)) }
    }
}

private fun DrawScope.topBeams(plan: TopPlan) {
    val l = plan.look.lamp
    val x0 = minOf(l[0], plan.noseX) - 0.02f
    val reach = plan.length * 1.7f
    val spread = plan.width * 0.95f
    val beam = Path().apply {
        moveTo(x0, -plan.half * 0.55f); lineTo(x0 + reach, -spread); lineTo(x0 + reach, spread); lineTo(x0, plan.half * 0.55f); close()
    }
    drawPath(beam, Brush.horizontalGradient(0f to Color(0xFFFFF3C8).copy(alpha = 0.2f), 1f to Color.Transparent, startX = x0, endX = x0 + reach))
}

private fun DrawScope.topWheels(plan: TopPlan, steer: Float, px: Float) {
    val deg = Math.toDegrees(steer.toDouble()).toFloat()
    for (side in intArrayOf(-1, 1)) {
        wheel(plan, plan.rearAxle, side, plan.rearR, plan.width * 0.21f, 0f, true, px)
        wheel(plan, plan.frontAxle, side, plan.frontR, plan.width * 0.165f, deg, false, px)
    }
}

private fun DrawScope.wheel(plan: TopPlan, ax: Float, side: Int, r: Float, tw: Float, steerDeg: Float, driven: Boolean, px: Float) {
    // Well over a third of the tire shows outside the bodywork.
    val cy = side * (plan.hw(ax) - tw * 0.12f)
    withTransform({
        translate(ax, cy)
        rotate(steerDeg, Offset.Zero)
    }) {
        val len = r * 2f
        val corner = CornerRadius(tw * 0.3f)
        drawRoundRect(rubber, Offset(-r, -tw / 2f), Size(len, tw), corner)
        // Tread blocks across the face, and a lit sidewall on the outer edge.
        val blocks = if (driven) 5 else 4
        for (i in 0 until blocks) {
            val tx = -r + len * (i + 0.5f) / blocks
            drawLine(sidewall, Offset(tx, -tw * 0.4f), Offset(tx, tw * 0.4f), px * 1.1f)
        }
        val outer = side * (tw / 2f)
        drawRoundRect(sidewall, Offset(-r * 0.92f, if (side > 0) outer - tw * 0.2f else outer), Size(len * 0.92f, tw * 0.2f), CornerRadius(tw * 0.1f))
        // The rim, the one bright thing on the wheel.
        val rimLen = len * 0.62f
        val rimW = tw * 0.26f
        val ry = if (side > 0) outer - rimW * 0.9f else outer - rimW * 0.1f
        drawRoundRect(
            Brush.horizontalGradient(0f to alloy, 1f to alloyShade, startX = -rimLen / 2f, endX = rimLen / 2f),
            Offset(-rimLen / 2f, ry), Size(rimLen, rimW), CornerRadius(rimW * 0.4f),
        )
        drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(-r, -tw / 2f), Size(len, tw), corner, style = Stroke(px * 1.0f))
    }
}

private fun DrawScope.topBody(plan: TopPlan, color: Color, accent: Color, px: Float) {
    val look = plan.look
    val lit = lerp(color, Color.White, 0.32f)
    val shade = lerp(color, Color.Black, 0.72f)
    val h = plan.half
    val paint = Brush.verticalGradient(0f to shade, 0.26f to color, 0.5f to lit, 0.74f to color, 1f to shade, startY = -h, endY = h)
    look.wing?.let { w -> if (w[2] > 0f) wingStruts(plan, w, color, px) }
    glowPath(color, plan.body, px * 1.4f, 0.6f)
    drawPath(plan.body, paint)
    clipPath(plan.body) {
        // Stripes down the middle, or a thin accent along each flank when the car has none.
        if (look.stripe != null) {
            for (side in intArrayOf(-1, 1)) {
                val y0 = side * h * 0.08f
                drawRect(accent.copy(alpha = 0.92f), Offset(plan.tailX - 0.1f, minOf(y0, y0 + side * h * 0.17f)), Size(plan.length + 0.2f, h * 0.17f))
            }
        } else {
            for (side in intArrayOf(-1, 1)) {
                drawRect(accent.copy(alpha = 0.55f), Offset(plan.tailX - 0.1f, side * h * 0.7f - px * 0.8f), Size(plan.length + 0.2f, px * 1.6f))
            }
        }
        // Darker ends, so the bonnet and boot fall away from the roof.
        drawRect(
            Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.4f), 1f to Color.Transparent, startX = plan.tailX, endX = plan.tailX + plan.length * 0.2f),
            Offset(plan.tailX - 0.1f, -h), Size(plan.length * 0.3f, h * 2f),
        )
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.34f), startX = plan.noseX - plan.length * 0.16f, endX = plan.noseX),
            Offset(plan.noseX - plan.length * 0.16f, -h), Size(plan.length * 0.3f, h * 2f),
        )
        // A bright streak along the bonnet.
        drawLine(Color.White.copy(alpha = 0.22f), Offset(plan.midX + plan.length * 0.1f, -h * 0.34f), Offset(plan.noseX - plan.length * 0.12f, -h * 0.3f), px * 3f, StrokeCap.Round)
    }
    look.louvers?.let { l ->
        val n = l[3].toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val lx = l[0] + (l[1] - l[0]) * (i + 0.5f) / n
            drawLine(Color.Black.copy(alpha = 0.6f), Offset(lx, -h * 0.34f), Offset(lx, h * 0.34f), px * 2f, StrokeCap.Round)
        }
    }
    look.scoop?.let { sc ->
        val rect = Path().apply {
            moveTo(sc[0], -h * 0.16f); lineTo(sc[0] + sc[2], -h * 0.12f); lineTo(sc[0] + sc[2], h * 0.12f); lineTo(sc[0], h * 0.16f); close()
        }
        drawPath(rect, Color(0xFF05070A))
        drawPath(rect, Color.White.copy(alpha = 0.3f), style = Stroke(px * 1.2f))
    }
    if (plan.open) {
        val hm = look.helmet!!
        val cockpit = DragPaths.rounded(floatArrayOf(
            hm[0] - 0.22f, -h * 0.5f, 0.3f, hm[0] + 0.2f, -h * 0.44f, 0.3f, hm[0] + 0.2f, h * 0.44f, 0.3f, hm[0] - 0.22f, h * 0.5f, 0.3f,
        ))
        drawPath(cockpit, Color(0xFF05070A))
        drawPath(cockpit, Color.White.copy(alpha = 0.25f), style = Stroke(px * 1.2f, join = StrokeJoin.Round))
        // Shoulders in a dark suit, then the helmet and its visor.
        drawOval(Color(0xFF1A2230), Offset(hm[0] - 0.14f, -h * 0.4f), Size(0.17f, h * 0.8f))
        drawCircle(Brush.verticalGradient(0f to Color.White, 1f to Color(0xFF8A94A0), startY = -0.1f, endY = 0.1f), 0.095f, Offset(hm[0], 0f))
        drawOval(Color(0xFF0B1620), Offset(hm[0] + 0.03f, -0.055f), Size(0.065f, 0.11f))
        look.hoop?.let { hp -> drawLine(lerp(color, Color.Black, 0.55f), Offset(hp[0], -h * 0.42f), Offset(hp[0], h * 0.42f), px * 3.2f, StrokeCap.Round) }
    } else {
        drawPath(plan.roof, Brush.horizontalGradient(0f to lerp(color, Color.Black, 0.2f), 0.5f to lit, 1f to color, startX = plan.glassX0, endX = plan.glassX1))
        if (look.stripe != null) clipPath(plan.roof) {
            for (side in intArrayOf(-1, 1)) {
                val y0 = side * h * 0.08f
                drawRect(accent.copy(alpha = 0.92f), Offset(plan.glassX0, minOf(y0, y0 + side * h * 0.17f)), Size(plan.glassX1 - plan.glassX0, h * 0.17f))
            }
        }
        for ((g, lightAtNose) in listOf(plan.windshield to true, plan.rearGlass to false)) {
            val a = if (lightAtNose) plan.glassX1 - (plan.glassX1 - plan.glassX0) * 0.3f else plan.glassX0
            val b = if (lightAtNose) plan.glassX1 else plan.glassX0 + (plan.glassX1 - plan.glassX0) * 0.2f
            drawPath(g, Brush.horizontalGradient(0f to Color(0xFF070D14), 1f to Color(0xFF3A5568), startX = if (lightAtNose) a else b, endX = if (lightAtNose) b else a))
            clipPath(g) {
                drawLine(Color.White.copy(alpha = 0.22f), Offset(a, h * 0.5f), Offset(b, h * 0.1f), px * 3f, StrokeCap.Round)
            }
            drawPath(g, Color.Black.copy(alpha = 0.7f), style = Stroke(px * 1.2f))
        }
        drawPath(plan.roof, Color.Black.copy(alpha = 0.35f), style = Stroke(px * 1.1f))
        look.hoop?.let { hp -> drawLine(lerp(color, Color.Black, 0.55f), Offset(hp[0], -h * 0.4f), Offset(hp[0], h * 0.4f), px * 3f, StrokeCap.Round) }
    }
    if (look.flares) {
        for ((ax, r, tw) in listOf(Triple(plan.rearAxle, plan.rearR, plan.width * 0.21f), Triple(plan.frontAxle, plan.frontR, plan.width * 0.165f))) {
            for (side in intArrayOf(-1, 1)) {
                val cy = side * (plan.hw(ax) - tw * 0.12f)
                val top = if (side > 0) cy - tw * 0.5f - tw * 0.05f else cy - tw * 0.5f
                val fender = Offset(ax - r * 1.12f, top)
                drawRoundRect(
                    Brush.verticalGradient(0f to if (side < 0) shade else lit, 1f to if (side < 0) lit else shade, startY = cy - tw * 0.6f, endY = cy + tw * 0.6f),
                    fender, Size(r * 2.24f, tw * 1.05f), CornerRadius(tw * 0.45f),
                )
                drawRoundRect(Color.Black.copy(alpha = 0.55f), fender, Size(r * 2.24f, tw * 1.05f), CornerRadius(tw * 0.45f), style = Stroke(px * 1.1f))
            }
        }
    }
    if (look.bumpers) {
        val chrome = Brush.horizontalGradient(0f to Color.White, 1f to Color(0xFF6A7580), startX = plan.tailX, endX = plan.tailX + 0.06f)
        drawRoundRect(chrome, Offset(plan.tailX - 0.02f, -h * 0.62f), Size(0.055f, h * 1.24f), CornerRadius(0.02f))
        drawRoundRect(chrome, Offset(plan.noseX - 0.035f, -h * 0.62f), Size(0.055f, h * 1.24f), CornerRadius(0.02f))
    }
    look.wing?.let { w -> wingPlank(plan, w, color, px) }
    // Tail lamps and headlamps.
    val tailGlow = Color(0xFFFF2A1E)
    for (side in intArrayOf(-1, 1)) {
        val ty = side * plan.hw(plan.tailX + 0.04f) * 0.66f
        drawCircle(tailGlow.copy(alpha = 0.22f), 0.09f, Offset(plan.tailX + 0.03f, ty))
        drawRoundRect(tailGlow, Offset(plan.tailX + 0.005f, ty - 0.045f), Size(0.045f, 0.09f), CornerRadius(0.015f))
        val lx = minOf(look.lamp[0], plan.noseX - 0.05f)
        val ly = side * plan.hw(lx) * 0.62f
        drawCircle(Color(0xFFFFF3C8).copy(alpha = 0.28f), 0.1f, Offset(lx, ly))
        drawOval(Color(0xFFFFF6D6), Offset(lx - 0.05f, ly - 0.04f), Size(0.1f, 0.08f))
    }
}

private fun DrawScope.wingStruts(plan: TopPlan, w: FloatArray, color: Color, px: Float) {
    val dark = lerp(color, Color.Black, 0.6f)
    val cx = w[0] + w[3] / 2f
    for (side in intArrayOf(-1, 1)) {
        drawLine(dark, Offset(plan.tailX + plan.length * 0.06f, side * plan.half * 0.32f), Offset(cx, side * plan.half * 0.38f), px * 3f)
    }
}

private fun DrawScope.wingPlank(plan: TopPlan, w: FloatArray, color: Color, px: Float) {
    val lit = lerp(color, Color.White, 0.3f)
    val shade = lerp(color, Color.Black, 0.6f)
    val span = if (w[2] > 0f) plan.half * 1.04f else plan.hw(w[0] + w[3] / 2f).coerceAtLeast(plan.half * 0.5f) * 0.98f
    drawRoundRect(
        Brush.horizontalGradient(0f to lit, 1f to shade, startX = w[0], endX = w[0] + w[3]),
        Offset(w[0], -span), Size(w[3], span * 2f), CornerRadius(0.02f),
    )
    drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(w[0], -span), Size(w[3], span * 2f), CornerRadius(0.02f), style = Stroke(px * 1.1f))
    // End plates.
    for (side in intArrayOf(-1, 1)) {
        drawRect(Color(0xFF0B0B0D), Offset(w[0] - 0.01f, if (side > 0) span - 0.025f else -span), Size(w[3] + 0.02f, 0.025f))
    }
}
