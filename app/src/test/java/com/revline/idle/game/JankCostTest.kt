package com.revline.idle.game

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
import com.revline.idle.model.FuelKind
import com.revline.idle.ui.EngineArtDraw
import com.revline.idle.ui.WankelGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Times the work that runs while the bay is on screen. The numbers are the
 * evidence for the fold pass; the assertions only lock the results that must
 * not drift and a ceiling so a later change cannot bring the hitch back.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class JankCostTest {
    @Test fun costs() {
        val stock = Logic.newGame()
        val v8 = GameState(
            prestiges = 6,
            blockId = Block.V8.name,
            fuel = FuelKind.PREMIUM_93,
            upgrades = mapOf("turbo" to 3, "intercooler" to 2, "cam" to 2, "injectors" to 2, "fuel_pump" to 2, "radiator" to 2),
            throttle = 0.7,
            money = 4_000.0,
        )
        val rotary = GameState(
            prestiges = Block.ROTARY.ladderStep,
            blockId = Block.ROTARY.name,
            fuel = FuelKind.PREMIUM_93,
            upgrades = mapOf("apex_seals" to 2, "turbo" to 2, "intercooler" to 1),
            throttle = 0.8,
            money = 8_000.0,
        )
        val log = StringBuilder()
        fun line(name: String, ms: Double) {
            log.append(String.format("%-28s %8.3f ms%n", name, ms))
        }
        line("point soak I4", ms { EngineSim.point(Logic.paramsFrom(stock), 1800.0, 0.4, 6.0) })
        line("point soak V8", ms { EngineSim.point(Logic.paramsFrom(v8), 4200.0, 0.7, 12.0) })
        line("evaluate I4", ms { EngineSim.evaluate(Logic.paramsFrom(stock)) })
        line("evaluate V8", ms { EngineSim.evaluate(Logic.paramsFrom(v8)) })
        line("evaluate rotary", ms { EngineSim.evaluate(Logic.paramsFrom(rotary)) })
        line("sheet V8", ms { Logic.sheet(v8) })
        val ranked = SheetAim.rank(v8)
        line("aim rank V8", ms { SheetAim.rank(v8) })
        line("aim pick V8", ms { SheetAim.pick(ranked, v8.money + 10_000.0, AimSpend.AFFORDABLE) })
        line("aim recommend V8", ms { SheetAim.recommend(v8) })
        line("aim cooling V8", ms { SheetAim.recommend(v8, AimGoal.COOLING, AimSpend.AFFORDABLE) })
        line("preview all V8", ms { Upgrades.all.forEach { Logic.preview(v8, it.id) } })
        line("previews once V8", ms { Logic.previews(v8) })
        val previewKey = listOf(v8.upgrades, v8.researched, v8.fuel, v8.perks, v8.knowledge, v8.prestiges, 14)
        Logic.cachedPreviews(previewKey, v8)
        line("previews cached V8", ms { Logic.cachedPreviews(previewKey, v8) })
        line("drag forecast V8", ms { DragTune.forecast(v8) })
        line("driver forecast V8", ms { Drivers.forecast(RaceDrive.setup(v8)) })
        line("save encode V8", ms { SaveCodec.encode(v8.copy(upgrades = v8.upgrades, researched = setOf("billet_node"))) })
        val rich = v8.copy(
            upgrades = Upgrades.all.take(12).associate { it.id to 1 },
            researched = setOf("head", "fuel_node"),
            chassisLevel = mapOf("seven" to 1, "gt40" to 2),
            hiredDrivers = setOf("dusty"),
        )
        line("save encode rich", ms { SaveCodec.encode(rich) })
        line("draw I4 fold", drawMs(stock, 1812, 728))
        line("draw V8 fold", drawMs(v8, 1812, 728))
        line("draw rotary fold", drawMs(rotary, 1812, 728))
        val e = 20.0
        val r = e * WankelGeometry.DEFAULT_R_OVER_E
        val first = WankelGeometry.sampleHousing(r, e, 120)
        val again = WankelGeometry.sampleHousing(r, e, 120)
        line("housing sample cached", ms { WankelGeometry.sampleHousing(r, e, 120) })
        println(log)
        assertEquals(first, again)
        check(first === again) { "housing sample was rebuilt for the same panel" }
        for (state in listOf(stock, v8, rotary)) {
            val pass = DragTune.forecast(state)
            val ticked = state.copy(money = 1.0, runEarnings = 9.0, researchPoints = 3.0, savedAtEpochMs = 5L, soakC = 22.0)
            assertEquals(pass, DragTune.forecast(ticked))
            assertEquals(pass, DragTune.forecast(DragTune.forecastKey(ticked)))
        }
        val geared = DragTune.forecast(v8.copy(dragTune = mapOf("gear" to 0.45)))
        assertNotEquals(DragTune.forecast(v8).time, geared.time, 1e-6)
        for (goal in listOf<AimGoal?>(null) + AimGoal.entries) {
            for (spend in AimSpend.entries) {
                for (money in listOf(0.0, 80.0, 50_000.0)) {
                    val held = v8.copy(money = money)
                    val viaPick = SheetAim.pick(SheetAim.rank(v8, goal), money, spend)
                    assertEquals(SheetAim.recommend(held, goal, spend), viaPick)
                }
            }
        }
        assertEquals(SheetAim.rank(v8, AimGoal.POWER), SheetAim.rank(v8.copy(money = 99_000.0), AimGoal.POWER))
        val batch = Logic.previews(v8)
        for (upgrade in Upgrades.all) {
            assertEquals(Logic.preview(v8, upgrade.id), batch[upgrade.id])
        }
        assertEquals(batch, Logic.cachedPreviews(previewKey, v8.copy(money = 1.0)))
    }

    private fun ms(block: () -> Unit): Double {
        repeat(2) { block() }
        val start = System.nanoTime()
        val n = 8
        repeat(n) { block() }
        return (System.nanoTime() - start) / 1e6 / n
    }

    private fun drawMs(state: GameState, w: Int, h: Int): Double {
        val params = Logic.paramsFrom(state)
        val rpm = Logic.targetRpm(params.redlineRpm, state.throttle, params.idleRpm).toFloat()
        val img = ImageBitmap(w, h)
        fun frame(angle: Float) {
            CanvasDrawScope().draw(Density(2.75f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
                drawRect(Color(0xFF0B1811))
                val stroke = (size.width / 170f).coerceIn(1.7f, 3.4f)
                EngineArtDraw.draw(this, params, state.upgrades, stroke, angle, clock = 1.2f, spinRpm = rpm, throttle = state.throttle.toFloat())
            }
        }
        frame(20f)
        val start = System.nanoTime()
        val n = 6
        repeat(n) { i -> frame(20f + i * 17f) }
        img.asAndroidBitmap()
        return (System.nanoTime() - start) / 1e6 / n
    }
}
