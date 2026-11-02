package com.revline.idle.ui

import androidx.compose.ui.graphics.drawscope.inset
import com.revline.idle.economy.DriverBuilds
import com.revline.idle.game.Circuits
import com.revline.idle.game.Drivers
import com.revline.idle.game.Racing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One picture of the full race screen: map, dash and (at the flag) the best-lap traces. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RaceScreenShotTest {
    @Test fun raceScreen() {
        val c = Circuits.all.first { it.id == "silverstone" }
        val d = Drivers.byId("colette")!!
        val car = Circuits.carOf(DriverBuilds.make(3, Racing.Build.MEDIUM, "race", "close", "lsd", 0.8))
        val r = Circuits.race(Circuits.plan(car, c, d), d, Circuits.parPlan(3, c), 7L)
        val replay = Replay.build(r)!!
        val w = 936
        val mapH = (w / CircuitLayouts.aspect("silverstone")).toInt()
        val dashH = (w / DASH_ASPECT).toInt()
        val liveH = (w / 3.2f).toInt()
        val chartH = (w / 1.5f).toInt()
        val at = replay.duration * 0.4
        CircuitShots.shoot("race-screen", w, mapH + dashH + liveH + chartH, density = 2.6f) {
            val scene = bakeCircuitScene(CircuitGeo(w.toFloat(), mapH.toFloat(), "silverstone"), this, "SILVERSTONE", "${r.circuit.lapKm} km  ${r.circuit.corners} corners")
            drawCircuitScene(scene, ReplayLook.cars(scene.geo, replay, at, "p911", false))
            inset(0f, mapH.toFloat(), 0f, (liveH + chartH).toFloat()) { drawCircuitDash(replay.you.dash(at)) }
            inset(0f, (mapH + dashH).toFloat(), 0f, chartH.toFloat()) { drawLiveTrace(replay.you.lapTrace(at), replay.rival.lapTrace(at), liveCeiling(replay), ReplayLook.livery(replay.rival.name), replay.you.lapNumber(at)) }
            val laps = replay.bestLaps()
            inset(0f, (mapH + dashH + liveH).toFloat(), 0f, 0f) { drawBestLaps(laps) }
        }
    }
}
