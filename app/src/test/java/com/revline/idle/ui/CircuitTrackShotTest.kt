package com.revline.idle.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import com.revline.idle.game.Circuits
import androidx.compose.ui.geometry.Offset
import com.revline.idle.economy.DriverBuilds
import com.revline.idle.game.Drivers
import com.revline.idle.game.Racing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CircuitTrackShotTest {
    private val lime = Color(0xFFE4FF47)
    private val red = Color(0xFFFF3B30)

    @Test fun overviews() {
        for (c in Circuits.all) {
            CircuitShots.shoot(c.id, 936, (936f / CircuitLayouts.aspect(c.id)).toInt()) {
                val scene = bakeCircuitScene(CircuitGeo(size.width, size.height, c.id), Density(2.6f), c.name.uppercase(), "${c.lapKm} km  ${c.corners} corners")
                val geo = scene.geo
                val cars = listOf(
                    CircuitCar("p911", false, lime, Color(0xFF101010), gridArc(geo, 0, 1), geo.laneOffset),
                    CircuitCar("vette", false, red, Color.White, gridArc(geo, 0, -1), -geo.laneOffset),
                )
                drawCircuitScene(scene, cars)
            }
        }
    }

    private fun race(id: String, seed: Long): Circuits.Result {
        val c = Circuits.all.first { it.id == id }
        val d = Drivers.byId("colette")!!
        val car = Circuits.carOf(DriverBuilds.make(3, Racing.Build.MEDIUM, "race", "close", "lsd", 0.8))
        return Circuits.race(Circuits.plan(car, c, d), d, Circuits.parPlan(3, c), seed)
    }

    @Test fun raceFrames() {
        val asp = CircuitLayouts.aspect("silverstone")
        for ((name, w, h) in listOf(Triple("", 936, (936f / asp).toInt()), Triple("-big", 2808, (2808f / asp).toInt()))) {
            val r = race("silverstone", 7L)
            val replay = Replay.build(r)!!
            // The moment the two run closest through the middle of the race.
            var tMid = replay.duration * 0.4
            var best = Double.MAX_VALUE
            var t = replay.duration * 0.25
            while (t < replay.duration * 0.8) {
                val d = kotlin.math.abs(replay.you.progress(t) - replay.rival.progress(t))
                if (d < best) { best = d; tMid = t }
                t += 0.5
            }
            for ((file, at) in listOf("race-start" to 0.0, "race-mid" to tMid)) {
                CircuitShots.shoot(file + name, w, h, density = 2.6f * w / 936f) {
                    val scene = bakeCircuitScene(CircuitGeo(size.width, size.height, "silverstone"), this, "SILVERSTONE", "${r.circuit.lapKm} km  ${r.circuit.corners} corners")
                    drawCircuitScene(scene, ReplayLook.cars(scene.geo, replay, at, "p911", false))
                }
            }
        }
    }

    @Test fun followFrames() {
        val r = race("silverstone", 7L)
        val replay = Replay.build(r)!!
        val asp = CircuitLayouts.aspect("silverstone")
        val w = 936
        val h = (w / asp).toInt()
        for ((file, at) in listOf("follow-start" to 0.0, "silverstone-grid" to 0.0, "follow-mid" to replay.duration * 0.4)) {
            CircuitShots.shoot(file, w, h) {
                val zoom = CircuitView.zoomFor(size.width, size.height)
                val scene = bakeCircuitScene(CircuitGeo(size.width * zoom, size.height * zoom, "silverstone"), Density(2.6f * zoom), "SILVERSTONE", "${r.circuit.lapKm} km  ${r.circuit.corners} corners")
                drawCircuitView(scene, ReplayLook.cars(scene.geo, replay, at, "p911", false), size.width, size.height, followYou = true)
            }
        }
    }

    @Test fun carSheet() {
        val ids = listOf("mule", "p50", "isetta", "cv2", "kr200", "fiat500", "saab96", "sprite", "mini", "amazon", "elan", "gta", "b2002", "seven", "r5", "etype", "stratos", "p911", "vette", "gt40", "cobra", "sel63", "p917", "daytona", "jensen")
        val cols = 5
        val cell = 200
        CircuitShots.shoot("car-sheet", cols * cell, ((ids.size + cols - 1) / cols) * cell + 20) {
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3C4046), Color(0xFF131518))))
            for ((i, id) in ids.withIndex()) {
                val c = Offset((i % cols + 0.5f) * cell, (i / cols + 0.5f) * cell + 10)
                val player = i % 2 == 0
                drawTopCar(id, false, if (player) lime else red, if (player) Color(0xFF101010) else Color.White, c, 0f, 150f, 0f)
            }
        }
    }
}
