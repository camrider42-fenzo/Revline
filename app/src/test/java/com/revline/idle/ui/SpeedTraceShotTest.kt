package com.revline.idle.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.revline.idle.economy.DriverBuilds
import com.revline.idle.game.CircuitPace
import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import com.revline.idle.game.Racing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Draws /workspace/circuit-gfx/speed-trace.png: the speed of two different cars round one track against the distance. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SpeedTraceShotTest {
    @Test fun speedTrace() {
        val id = "silverstone"
        val c = Circuits.all.first { it.id == id }
        val d = Drivers.byId("colette")!!
        val base = Circuits.carOf(DriverBuilds.make(3, Racing.Build.MEDIUM, "race", "close", "lsd", 0.8))
        val hands = Circuits.plan(base, c, d).hands
        // One car with the engine, one with the brakes and the tires.
        val power = base.copy(setup = base.setup.copy(peakHp = base.setup.peakHp * 1.35, peakTqLbFt = base.setup.peakTqLbFt * 1.35, gripN = base.setup.gripN * 0.93), brake = base.brake * 0.9)
        val grip = base.copy(setup = base.setup.copy(gripN = base.setup.gripN * 1.12), brake = base.brake * 1.3)
        val line = CircuitLayouts.of(id)
        val radii = CircuitRadii.of(line)
        val a = CircuitPace.trace(power, hands, 1.0, radii.radius, radii.ds)
        val b = CircuitPace.trace(grip, hands, 1.0, radii.radius, radii.ds)
        val w = 1800
        val h = 760
        CircuitShots.shoot("speed-trace", w, h, density = 2.6f) {
            drawRect(Color.Black)
            val left = 120f
            val right = w - 40f
            val top = 190f
            val bottom = h - 90f
            val len = radii.n * radii.ds
            fun x(m: Double) = left + (right - left) * (m / len).toFloat()
            val vMax = 100.0
            fun y(mph: Double) = bottom - (bottom - top) * (mph / vMax).toFloat()
            for (kmh in 0..(vMax * 3.6 / 50).toInt() ) {
                val mph = kmh * 50 / 3.6
                if (mph > vMax) break
                drawLine(Color(0xFF2A3010), Offset(left, y(mph)), Offset(right, y(mph)), 2f)
                textLabel("${kmh * 50}", left - 14f, y(mph) + 8f, 26f, Olive, 2)
            }
            textLabel("km/h", left - 14f, top - 30f, 26f, Olive, 2)
            for (km in 0..(len / 1000).toInt()) {
                drawLine(Color(0xFF2A3010), Offset(x(km * 1000.0), top), Offset(x(km * 1000.0), bottom), 2f)
                textLabel("$km km", x(km * 1000.0), bottom + 36f, 26f, Olive, 1)
            }
            // The corners: a tick at the apex and its number.
            for (k in 0 until line.corners) {
                val ix = radii.apex[k]
                val m = ix * radii.ds
                drawLine(Color(0x66FFFFFF), Offset(x(m), top), Offset(x(m), bottom), 1.5f)
                textLabel("${k + 1}", x(m), top - 12f, 22f, White, 1)
            }
            fun path(t: CircuitPace.Trace): Path {
                val p = Path()
                for (i in 0..t.n) {
                    val m = i * t.ds
                    val v = t.v[i % t.n]
                    if (i == 0) p.moveTo(x(m), y(v)) else p.lineTo(x(m), y(v))
                }
                return p
            }
            drawPath(path(a), Red, style = Stroke(width = 5f))
            drawPath(path(b), Cyan, style = Stroke(width = 5f))
            // The hardest braking: where each car lifts.
            val apex = radii.apex.maxBy { ix -> a.v[(ix - 12).coerceAtLeast(0)] - a.v[ix] }
            for ((t, col) in listOf(a to Red, b to Cyan)) {
                val start = apex - (t.brakeDistance(apex) / t.ds).toInt()
                drawCircle(col, 11f, Offset(x(start * t.ds), y(t.v[start])))
            }
            textLabel("SILVERSTONE   SPEED ON THE RACING LINE", left, 50f, 34f, Lime, 0)
            textLabel("POWER CAR  +35% engine, -7% grip, -10% brakes:  ${"%.1f".format(a.lapTime)} s  top ${"%.0f".format(a.topSpeed * 3.6)} km/h", left, 92f, 26f, Red, 0)
            textLabel("GRIP CAR  +12% grip, +30% brakes:  ${"%.1f".format(b.lapTime)} s  top ${"%.0f".format(b.topSpeed * 3.6)} km/h", left, 130f, 26f, Cyan, 0)
            textLabel("dots: where each car starts braking for the hardest corner (${apex.let { _ -> "T${radii.apex.indexOf(apex) + 1}" }})", right, bottom + 76f, 24f, Olive, 2)
        }
    }
}
