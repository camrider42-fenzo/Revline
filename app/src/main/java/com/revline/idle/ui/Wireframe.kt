package com.revline.idle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rough side-view wireframes for the strip. Nose is +x, the road is y = 0.
 * Each one is only the lines that make the car recognizable at a glance.
 */
object Wireframe {
    data class Model(val lines: List<FloatArray>, val axles: List<FloatArray>, val tire: Float)

    fun of(id: String, mid: Boolean = false): Model = when {
        id == "r5" && mid -> cars.getValue("r5mid")
        else -> cars[id] ?: cars.getValue("mule")
    }

    fun length(id: String): Float = span(of(id)) { it[0] to it[2] }
    fun height(id: String): Float = span(of(id)) { it[1] to it[3] }

    /** Stable print of the lines, so two chassis cannot silently share a drawing. */
    fun signature(id: String): String = of(id).lines.joinToString(";") { seg ->
        seg.joinToString(",") { ((it * 20f).toInt()).toString() }
    }

    private fun span(model: Model, pick: (FloatArray) -> Pair<Float, Float>): Float {
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        for (seg in model.lines) {
            val (a, b) = pick(seg)
            lo = minOf(lo, a, b)
            hi = maxOf(hi, a, b)
        }
        return hi - lo
    }

    fun DrawScope.draw(
        id: String,
        mid: Boolean,
        color: Color,
        x: Float,
        ground: Float,
        scale: Float,
        roll: Float,
        slip: Float,
        stroke: Float,
        time: Float,
        gas: Float,
    ) {
        val spinning = slip > 0.02f
        val model = of(id, mid)
        val xs = model.lines.flatMap { listOf(it[0], it[2]) }
        val midX = ((xs.minOrNull() ?: 0f) + (xs.maxOrNull() ?: 0f)) / 2f
        fun at(nx: Float, ny: Float) = Offset(x + (nx - midX) * scale, ground - ny * scale)
        val rear = model.axles.minBy { it[0] }
        val tailX = model.lines.minOf { minOf(it[0], it[2]) }
        // Gas and smoke sit behind the body, so the cabin stays a line drawing.
        if (gas > 0.04f) {
            cloud(at(tailX, 0.22f), time * 1.3f, scale, stroke, smoke = false, amount = gas)
        }
        if (spinning) {
            cloud(at(rear[0], rear[1]), time, scale, stroke, smoke = true, amount = slip.coerceIn(0.15f, 1f))
        }
        for (seg in model.lines) {
            glowLine(color, at(seg[0], seg[1]), at(seg[2], seg[3]), stroke, 0.85f)
        }
        model.axles.forEach { axle ->
            val driven = spinning && axle[0] == rear[0]
            wheel(if (driven) Red else color, at(axle[0], axle[1]), model.tire * scale, roll, stroke)
        }
        if (spinning) {
            val tail = at(rear[0] - model.tire * 2.2f, 0.02f)
            val heel = at(rear[0] + model.tire * 0.2f, 0.02f)
            glowLine(Red, tail, heel, stroke * 1.35f, 0.9f)
        }
    }

    private fun DrawScope.cloud(
        origin: Offset,
        time: Float,
        scale: Float,
        stroke: Float,
        smoke: Boolean,
        amount: Float = 1f,
    ) {
        val count = if (smoke) 6 + (amount * 8f).toInt() else 7
        val puff = (scale / 52f).coerceIn(0.75f, 1.45f)
        for (i in 0 until count) {
            val phase = ((time * if (smoke) 1.6f else 2.2f) + i / count.toFloat()) % 1f
            val back = (0.18f + phase) * scale * (if (smoke) 1.55f else 1.35f)
            val up = phase * phase * scale * (if (smoke) 0.72f else 0.42f)
            val side = sin(time * 3f + i) * scale * 0.07f * phase
            val spot = origin + Offset(-back + side, -up)
            val radius = (2.2f + phase * (if (smoke) 5f + 7f * amount else 4.6f)) * puff
            val fade = (1f - phase) * amount * if (smoke) 0.85f else 0.7f
            val ink = if (smoke) White else Amber
            glowCircle(ink, radius, spot, stroke, filled = true, gain = fade.coerceIn(0.05f, 0.95f))
            if (!smoke && phase < 0.45f) {
                glowCircle(Red, radius * 0.42f, spot, stroke, filled = true, gain = (fade * 0.85f).coerceIn(0.04f, 0.7f))
            }
        }
    }

    private fun DrawScope.wheel(color: Color, center: Offset, radius: Float, roll: Float, stroke: Float) {
        glowCircle(color, radius, center, stroke, filled = false, gain = 0.85f)
        val step = (Math.PI / 2.0).toFloat()
        for (spoke in 0..1) {
            val ang = roll + spoke * step
            val dx = cos(ang) * radius * 0.75f
            val dy = sin(ang) * radius * 0.75f
            glowLine(color, Offset(center.x - dx, center.y - dy), Offset(center.x + dx, center.y + dy), stroke * 0.65f, 0.65f)
        }
    }

    private class Ink {
        val lines = ArrayList<FloatArray>()
        val axles = ArrayList<FloatArray>()
        var tire = 0.16f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            lines += floatArrayOf(x1, y1, x2, y2)
        }
        fun poly(vararg p: Float) {
            var i = 2
            while (i + 1 < p.size) {
                line(p[i - 2], p[i - 1], p[i], p[i + 1])
                i += 2
            }
        }
        fun arch(cx: Float, cy: Float, r: Float) {
            val n = 4
            for (i in 0 until n) {
                val a0 = Math.PI * i / n
                val a1 = Math.PI * (i + 1) / n
                line(
                    cx + (cos(a0) * r).toFloat(), cy + (sin(a0) * r).toFloat(),
                    cx + (cos(a1) * r).toFloat(), cy + (sin(a1) * r).toFloat(),
                )
            }
        }
        fun done() = Model(lines, axles, tire)
    }

    private fun ink(tire: Float = 0.16f, block: Ink.() -> Unit): Model {
        val pen = Ink()
        pen.tire = tire
        pen.block()
        return pen.done()
    }

    private val cars: Map<String, Model> = mapOf(
        "mule" to ink(0.18f) {
            poly(-1.05f, 0.22f, 1.05f, 0.22f, 1.05f, 0.42f, 0.15f, 0.42f, 0.15f, 0.95f, -0.55f, 0.95f, -0.55f, 0.42f, -1.05f, 0.42f, -1.05f, 0.22f)
            line(-0.55f, 0.42f, 0.15f, 0.42f)
            line(0.15f, 0.42f, 0.15f, 0.95f)
            axles += floatArrayOf(-0.62f, 0.18f)
            axles += floatArrayOf(0.72f, 0.18f)
        },
        "p50" to ink(0.17f) {
            poly(-0.48f, 0.2f, 0.5f, 0.2f, 0.42f, 0.7f, 0.05f, 1.05f, -0.38f, 1.0f, -0.5f, 0.45f, -0.48f, 0.2f)
            line(-0.05f, 0.35f, -0.05f, 0.92f)
            axles += floatArrayOf(-0.22f, 0.17f)
            axles += floatArrayOf(0.28f, 0.17f)
        },
        "isetta" to ink(0.16f) {
            poly(-0.72f, 0.18f, 0.62f, 0.18f, 0.78f, 0.55f, 0.28f, 1.12f, -0.48f, 1.12f, -0.78f, 0.55f, -0.72f, 0.18f)
            line(0.45f, 0.28f, 0.12f, 0.95f)
            axles += floatArrayOf(-0.42f, 0.16f)
            axles += floatArrayOf(0.4f, 0.16f)
        },
        "cv2" to ink(0.17f) {
            poly(-1.05f, 0.28f, 1.0f, 0.28f, 0.95f, 0.7f, 0.55f, 1.25f, -0.62f, 1.25f, -0.95f, 0.75f, -1.05f, 0.28f)
            arch(-0.55f, 0.22f, 0.28f)
            arch(0.62f, 0.22f, 0.28f)
            line(-0.15f, 0.55f, -0.15f, 1.15f)
            axles += floatArrayOf(-0.55f, 0.17f)
            axles += floatArrayOf(0.62f, 0.17f)
        },
        "kr200" to ink(0.14f) {
            poly(-1.15f, 0.16f, 0.85f, 0.14f, 0.95f, 0.32f, 0.15f, 0.62f, -0.35f, 0.78f, -1.1f, 0.32f, -1.15f, 0.16f)
            line(-0.2f, 0.28f, -0.05f, 0.7f)
            line(-0.05f, 0.7f, 0.35f, 0.42f)
            axles += floatArrayOf(-0.55f, 0.14f)
            axles += floatArrayOf(0.48f, 0.14f)
        },
        "fiat500" to ink(0.16f) {
            poly(-0.7f, 0.18f, 0.55f, 0.16f, 0.7f, 0.45f, 0.35f, 0.95f, -0.25f, 1.05f, -0.65f, 0.55f, -0.7f, 0.18f)
            line(0.05f, 0.35f, -0.05f, 0.95f)
            axles += floatArrayOf(-0.38f, 0.16f)
            axles += floatArrayOf(0.32f, 0.16f)
        },
        "saab96" to ink(0.16f) {
            poly(-0.95f, 0.18f, 0.85f, 0.16f, 1.05f, 0.4f, 0.45f, 0.72f, 0.15f, 0.95f, -0.55f, 0.95f, -0.9f, 0.45f, -0.95f, 0.18f)
            line(-0.15f, 0.4f, -0.2f, 0.88f)
            axles += floatArrayOf(-0.55f, 0.16f)
            axles += floatArrayOf(0.58f, 0.16f)
        },
        "sprite" to ink(0.17f) {
            line(-0.9f, 0.22f, 0.95f, 0.2f)
            line(0.15f, 0.22f, 0.05f, 0.55f)
            line(0.05f, 0.55f, -0.25f, 0.48f)
            arch(-0.5f, 0.2f, 0.32f)
            arch(0.55f, 0.2f, 0.32f)
            line(0.85f, 0.38f, 0.95f, 0.38f)
            line(0.78f, 0.32f, 0.78f, 0.44f)
            axles += floatArrayOf(-0.5f, 0.17f)
            axles += floatArrayOf(0.55f, 0.17f)
        },
        "mini" to ink(0.16f) {
            poly(-0.85f, 0.18f, 0.85f, 0.18f, 0.85f, 0.58f, 0.62f, 0.98f, -0.62f, 0.98f, -0.85f, 0.58f, -0.85f, 0.18f)
            line(-0.15f, 0.4f, -0.15f, 0.92f)
            line(0.35f, 0.4f, 0.35f, 0.92f)
            axles += floatArrayOf(-0.62f, 0.16f)
            axles += floatArrayOf(0.62f, 0.16f)
        },
        "amazon" to ink(0.16f) {
            poly(-1.15f, 0.2f, 1.15f, 0.2f, 1.15f, 0.55f, 0.72f, 0.55f, 0.55f, 0.95f, -0.45f, 0.95f, -0.62f, 0.55f, -1.15f, 0.55f, -1.15f, 0.2f)
            line(-0.05f, 0.4f, -0.08f, 0.88f)
            line(0.28f, 0.4f, 0.25f, 0.88f)
            axles += floatArrayOf(-0.72f, 0.16f)
            axles += floatArrayOf(0.78f, 0.16f)
        },
        "elan" to ink(0.15f) {
            poly(-0.85f, 0.16f, 1.15f, 0.14f, 1.05f, 0.32f, 0.25f, 0.48f, 0.05f, 0.72f, -0.45f, 0.7f, -0.75f, 0.38f, -0.85f, 0.16f)
            line(-0.1f, 0.32f, -0.15f, 0.65f)
            axles += floatArrayOf(-0.48f, 0.15f)
            axles += floatArrayOf(0.62f, 0.15f)
        },
        "gta" to ink(0.16f) {
            poly(-0.85f, 0.18f, 1.05f, 0.16f, 1.05f, 0.42f, 0.35f, 0.55f, 0.1f, 0.82f, -0.45f, 0.82f, -0.7f, 0.48f, -0.85f, 0.18f)
            line(-0.85f, 0.18f, -0.85f, 0.48f)
            line(-0.15f, 0.35f, -0.18f, 0.75f)
            axles += floatArrayOf(-0.48f, 0.16f)
            axles += floatArrayOf(0.62f, 0.16f)
        },
        "b2002" to ink(0.16f) {
            poly(-1.05f, 0.2f, 1.1f, 0.2f, 1.1f, 0.52f, 0.55f, 0.52f, 0.4f, 0.92f, -0.5f, 0.92f, -0.68f, 0.52f, -1.05f, 0.52f, -1.05f, 0.2f)
            line(-0.12f, 0.4f, -0.15f, 0.85f)
            line(0.18f, 0.4f, 0.15f, 0.85f)
            axles += floatArrayOf(-0.62f, 0.16f)
            axles += floatArrayOf(0.72f, 0.16f)
        },
        "seven" to ink(0.15f) {
            line(-1.0f, 0.2f, 1.05f, 0.18f)
            line(-1.0f, 0.32f, 0.9f, 0.3f)
            line(-0.2f, 0.2f, 0.15f, 0.55f)
            line(0.15f, 0.55f, -0.05f, 0.48f)
            line(-0.7f, 0.2f, -0.35f, 0.32f)
            line(0.4f, 0.18f, 0.7f, 0.32f)
            arch(-0.55f, 0.18f, 0.28f)
            arch(0.62f, 0.18f, 0.28f)
            axles += floatArrayOf(-0.55f, 0.15f)
            axles += floatArrayOf(0.62f, 0.15f)
        },
        "r5" to ink(0.16f) {
            poly(-0.8f, 0.18f, 0.85f, 0.18f, 0.9f, 0.48f, 0.45f, 0.95f, -0.35f, 0.95f, -0.72f, 0.55f, -0.8f, 0.18f)
            line(-0.35f, 0.95f, -0.72f, 0.4f)
            line(0.05f, 0.4f, 0.02f, 0.88f)
            axles += floatArrayOf(-0.48f, 0.16f)
            axles += floatArrayOf(0.55f, 0.16f)
        },
        "r5mid" to ink(0.16f) {
            poly(-0.8f, 0.18f, 0.85f, 0.18f, 0.9f, 0.48f, 0.45f, 0.95f, -0.35f, 0.95f, -0.72f, 0.55f, -0.8f, 0.18f)
            line(-0.35f, 0.95f, -0.72f, 0.4f)
            line(-0.15f, 0.45f, -0.55f, 0.45f)
            line(-0.15f, 0.45f, -0.25f, 0.72f)
            line(-0.55f, 0.45f, -0.45f, 0.68f)
            axles += floatArrayOf(-0.48f, 0.16f)
            axles += floatArrayOf(0.55f, 0.16f)
        },
        "etype" to ink(0.15f) {
            poly(-0.75f, 0.16f, 1.35f, 0.14f, 1.25f, 0.32f, 0.35f, 0.42f, 0.12f, 0.7f, -0.4f, 0.72f, -0.7f, 0.4f, -0.75f, 0.16f)
            line(0.05f, 0.32f, -0.02f, 0.65f)
            line(0.85f, 0.28f, 1.05f, 0.28f)
            axles += floatArrayOf(-0.4f, 0.15f)
            axles += floatArrayOf(0.85f, 0.15f)
        },
        "stratos" to ink(0.16f) {
            poly(-0.95f, 0.22f, 0.9f, 0.16f, 0.75f, 0.48f, -0.05f, 0.78f, -0.7f, 0.62f, -0.95f, 0.35f, -0.95f, 0.22f)
            line(-0.15f, 0.4f, -0.25f, 0.7f)
            line(0.15f, 0.32f, -0.05f, 0.7f)
            axles += floatArrayOf(-0.48f, 0.16f)
            axles += floatArrayOf(0.42f, 0.16f)
        },
        "p911" to ink(0.16f) {
            poly(-1.05f, 0.2f, 0.85f, 0.16f, 0.95f, 0.38f, 0.2f, 0.55f, -0.15f, 0.78f, -0.85f, 0.42f, -1.05f, 0.2f)
            line(-0.15f, 0.78f, -0.95f, 0.32f)
            line(0.15f, 0.35f, 0.05f, 0.7f)
            axles += floatArrayOf(-0.58f, 0.16f)
            axles += floatArrayOf(0.55f, 0.16f)
        },
        "vette" to ink(0.16f) {
            poly(-0.9f, 0.18f, 1.2f, 0.16f, 1.15f, 0.4f, 0.45f, 0.48f, 0.15f, 0.72f, -0.35f, 0.7f, -0.75f, 0.4f, -0.9f, 0.18f)
            line(0.55f, 0.42f, 0.85f, 0.55f)
            line(0.85f, 0.55f, 0.55f, 0.48f)
            line(-0.05f, 0.35f, -0.1f, 0.65f)
            axles += floatArrayOf(-0.5f, 0.16f)
            axles += floatArrayOf(0.72f, 0.16f)
        },
        "gt40" to ink(0.15f) {
            poly(-1.15f, 0.16f, 0.85f, 0.14f, 0.75f, 0.32f, 0.15f, 0.48f, -0.25f, 0.5f, -0.7f, 0.32f, -1.15f, 0.22f, -1.15f, 0.16f)
            line(-0.05f, 0.28f, -0.12f, 0.46f)
            line(0.2f, 0.26f, 0.05f, 0.46f)
            axles += floatArrayOf(-0.62f, 0.15f)
            axles += floatArrayOf(0.42f, 0.15f)
        },
        "cobra" to ink(0.18f) {
            line(-0.85f, 0.22f, 0.95f, 0.2f)
            line(0.1f, 0.22f, -0.05f, 0.48f)
            line(-0.05f, 0.48f, -0.28f, 0.38f)
            arch(-0.45f, 0.22f, 0.36f)
            arch(0.55f, 0.22f, 0.38f)
            axles += floatArrayOf(-0.45f, 0.18f)
            axles += floatArrayOf(0.55f, 0.18f)
        },
        "sel63" to ink(0.16f) {
            poly(-1.35f, 0.22f, 1.35f, 0.22f, 1.35f, 0.62f, 0.85f, 0.62f, 0.7f, 1.05f, -0.55f, 1.05f, -0.75f, 0.62f, -1.35f, 0.62f, -1.35f, 0.22f)
            line(-0.15f, 0.48f, -0.18f, 0.98f)
            line(0.22f, 0.48f, 0.18f, 0.98f)
            line(0.55f, 0.48f, 0.48f, 0.95f)
            axles += floatArrayOf(-0.9f, 0.16f)
            axles += floatArrayOf(0.95f, 0.16f)
        },
        "p917" to ink(0.15f) {
            poly(-1.25f, 0.16f, 0.7f, 0.14f, 0.6f, 0.32f, -0.05f, 0.48f, -0.45f, 0.42f, -1.15f, 0.28f, -1.25f, 0.16f)
            line(-1.05f, 0.28f, -0.95f, 0.72f)
            line(-0.15f, 0.28f, -0.22f, 0.45f)
            axles += floatArrayOf(-0.7f, 0.15f)
            axles += floatArrayOf(0.32f, 0.15f)
        },
        "daytona" to ink(0.16f) {
            poly(-0.8f, 0.18f, 1.3f, 0.15f, 1.2f, 0.35f, 0.4f, 0.48f, 0.05f, 0.75f, -0.45f, 0.72f, -0.75f, 0.4f, -0.8f, 0.18f)
            line(0.05f, 0.75f, -0.55f, 0.32f)
            line(0.15f, 0.32f, 0.02f, 0.68f)
            axles += floatArrayOf(-0.42f, 0.16f)
            axles += floatArrayOf(0.78f, 0.16f)
        },
        "jensen" to ink(0.16f) {
            poly(-1.05f, 0.2f, 1.15f, 0.18f, 1.1f, 0.4f, 0.4f, 0.52f, 0.12f, 0.82f, -0.35f, 0.82f, -0.95f, 0.4f, -1.05f, 0.2f)
            line(0.12f, 0.82f, -0.85f, 0.35f)
            line(0.05f, 0.4f, -0.02f, 0.75f)
            axles += floatArrayOf(-0.58f, 0.16f)
            axles += floatArrayOf(0.72f, 0.16f)
        },
    )
}
