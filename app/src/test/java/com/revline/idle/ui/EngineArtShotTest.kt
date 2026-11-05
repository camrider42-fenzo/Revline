package com.revline.idle.ui

import androidx.compose.ui.graphics.Color
import com.revline.idle.data.GameState
import com.revline.idle.game.Logic
import com.revline.idle.model.FuelKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class EngineArtShotTest {
    @Test
    fun engineAnimShot() {
        val tag = System.getenv("ENGINE_SHOT") ?: return
        val state = GameState(
            prestiges = 3,
            fuel = FuelKind.PREMIUM_93,
            upgrades = mapOf(
                "filter" to 3, "cold_air" to 2, "headers" to 2, "cam" to 2, "springs" to 3,
                "plugs" to 2, "coils" to 2, "turbo" to 2, "intercooler" to 2, "radiator" to 2,
                "fuel_pump" to 2, "injectors" to 2, "pistons" to 1, "studs" to 1, "water_pump" to 1,
            ),
            throttle = 0.85,
        )
        val params = Logic.paramsFrom(state)
        val rpm = Logic.targetRpm(params.redlineRpm, state.throttle).toFloat()
        CircuitShots.shoot("engine-$tag", 640, 480) {
            drawRect(Color(0xFF0B1811))
            val stroke = (size.width / 170f).coerceIn(1.7f, 3.4f)
            EngineArtDraw.draw(this, params, state.upgrades, stroke, angle = 110f, clock = 1.4f, spinRpm = rpm, throttle = 0.85f)
        }
    }
}
