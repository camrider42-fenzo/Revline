package com.revline.idle.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.revline.idle.GameViewModel
import com.revline.idle.data.GameState
import com.revline.idle.data.SaveCodec
import com.revline.idle.data.SaveRepository
import com.revline.idle.game.Logic
import com.revline.idle.model.Block
import com.revline.idle.model.EngineSim
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The launch that dies after a splash and a moment of audio: fresh career
 * (what Restart writes) and a save cut off mid-write, through the first
 * bench draw and the first second of the mixer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w690dp-h830dp-xhdpi")
class LaunchReproTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun freshCareerAndPartialSavesSurviveTheFirstDrawAndTheMixer() {
        val failures = mutableListOf<String>()
        val fresh = Logic.newGame()
        checkState("fresh", fresh, failures)
        val fold = runCatching { draw(fresh, 2618f, 80f, 420f, width = 1600, height = 735) }
        if (fold.isFailure) failures += "fold bay ${fold.exceptionOrNull()}"
        val career = GameState(
            money = 250000.0,
            knowledge = 6,
            prestiges = Block.V8.ladderStep,
            blockId = Block.V8.name,
            researched = setOf("premium", "turbo", "exhaust_fab"),
            upgrades = mapOf("turbo" to 3, "cam" to 2, "filter" to 4, "headers" to 2),
            tune = mapOf("afr" to 0.4, "timing" to -2.0),
            aimSpend = "ALMOST",
            throttle = 0.55,
        )
        checkState("career", career, failures)
        val encodedFresh = SaveCodec.encode(fresh)
        val encodedCareer = SaveCodec.encode(career)
        for (encoded in listOf("fresh-cut" to encodedFresh, "career-cut" to encodedCareer)) {
            var cut = 1
            while (cut < encoded.second.length) {
                val partial = encoded.second.substring(0, cut)
                val salvaged = runCatching { SaveCodec.salvage(partial) }
                if (salvaged.isFailure) {
                    failures += "${encoded.first}@$cut salvage threw ${salvaged.exceptionOrNull()}"
                } else {
                    salvaged.getOrNull()?.let { checkState("${encoded.first}@$cut", it, failures) }
                }
                cut += (encoded.second.length / 24).coerceAtLeast(40)
            }
        }
        // The exact file Restart leaves behind.
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(app.filesDir, "revline")
        dir.deleteRecursively()
        val vm = GameViewModel(app)
        vm.resetGame()
        val left = File(dir, "revline_save.json").readText()
        val opened = SaveRepository(dir).load()
        checkState("restart-file", opened, failures)
        val crashes = mutableListOf<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> crashes += error }
        try {
            val relaunch = GameViewModel(app)
            val shown = runCatching {
                rule.mainClock.autoAdvance = false
                rule.setContent { RevlineApp(relaunch) }
                repeat(6) { rule.mainClock.advanceTimeByFrame() }
            }
            if (shown.isFailure) failures += "compose restart: ${shown.exceptionOrNull()}"
            rule.runOnUiThread {
                val view = rule.activity.window.decorView
                val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bmp))
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        crashes.forEach { failures += "thread ${it.javaClass.simpleName}: ${it.message}\n${it.stackTraceToString().take(500)}" }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertTrue(left.isNotEmpty())
    }

    private fun checkState(label: String, state: GameState, failures: MutableList<String>) {
        val sheet = runCatching { Logic.sheet(state) }
        if (sheet.isFailure) {
            failures += "$label sheet ${sheet.exceptionOrNull()?.javaClass?.simpleName}: ${sheet.exceptionOrNull()?.message}"
            return
        }
        val built = sheet.getOrThrow()
        val rpm = runCatching { Logic.targetRpm(built.params.redlineRpm, state.throttle, built.params.idleRpm) }
        if (rpm.isFailure) {
            failures += "$label tach ${rpm.exceptionOrNull()}"
            return
        }
        val spun = rpm.getOrThrow()
        val live = runCatching { EngineSim.point(built.params, spun, Logic.loadFor(state.throttle), state.soakC) }
        if (live.isFailure) {
            failures += "$label point ${live.exceptionOrNull()}"
            return
        }
        val point = live.getOrThrow()
        fun bad(name: String, value: Double) {
            if (!value.isFinite()) failures += "$label nonfinite $name=$value"
        }
        bad("hp", built.stats.peakHorsepower)
        bad("pay", built.incomePerSec)
        bad("fuel", point.fuelReleasedKw)
        bad("brake", point.brakeKw)
        bad("egt", point.egtC)
        bad("coolant", point.coolantTempC)
        val drawn = runCatching { draw(state, spun.toFloat(), point.coolantTempC.toFloat(), point.egtC.toFloat()) }
        if (drawn.isFailure) failures += "$label draw ${drawn.exceptionOrNull()?.javaClass?.simpleName}: ${drawn.exceptionOrNull()?.message}"
        val heard = runCatching { mix(state, spun.toFloat()) }
        if (heard.isFailure) failures += "$label audio ${heard.exceptionOrNull()?.javaClass?.simpleName}: ${heard.exceptionOrNull()?.message}"
    }

    private fun draw(state: GameState, rpm: Float, coolant: Float, egt: Float, width: Int = 800, height: Int = 480) {
        val params = Logic.paramsFrom(state)
        val img = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(width.toFloat(), height.toFloat())) {
            EngineArtDraw.draw(this, params, state.upgrades, stroke = 2f, angle = 40f, clock = 1.2f, spinRpm = rpm, throttle = state.throttle.toFloat())
        }
        img.asAndroidBitmap()
    }

    private fun mix(state: GameState, rpm: Float) {
        val params = Logic.paramsFrom(state)
        val crank = EngineCrank()
        crank.publish(params, rpm, state.throttle.toFloat(), live = true, limiterOn = false)
        val voice = EngineVoice()
        var angle = 0f
        val rate = 44100
        val ear0 = crank.ear()
        val period = EngineMotion.periodDeg(ear0.family)
        repeat(4096) {
            val ear = crank.ear()
            val step = ear.rpm.toFloat() * EngineMotion.SOUND_CRANK / rate
            val sample = voice.sample(ear, angle, rate)
            if (!sample.isFinite()) error("nonfinite sample at $angle")
            angle += step
            if (angle >= period) angle -= period
        }
    }
}
