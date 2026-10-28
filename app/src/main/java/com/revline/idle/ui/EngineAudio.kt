package com.revline.idle.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.revline.idle.model.EngineParams
import com.revline.idle.model.Induction
import java.util.concurrent.atomic.AtomicInteger

/**
 * One crank for the drawing and the speaker. The frame clock advances it.
 * The mixer reads it.
 */
class EngineCrank {
    private val angleBits = AtomicInteger(20f.toRawBits())
    private val rpmBits = AtomicInteger(800f.toRawBits())
    private val boreBits = AtomicInteger(86f.toRawBits())
    private val crBits = AtomicInteger(9.5f.toRawBits())
    private val cylinders = AtomicInteger(4)
    private val banks = AtomicInteger(1)
    private val throttleBits = AtomicInteger(0.18f.toRawBits())
    private val running = AtomicInteger(1)
    private val induction = AtomicInteger(Induction.NA.ordinal)
    private val redlineBits = AtomicInteger(7000f.toRawBits())
    private val openBits = AtomicInteger(0)
    private val raspBits = AtomicInteger(0)
    private val barkBits = AtomicInteger(0)
    private val lopeBits = AtomicInteger(0)

    var angle: Float
        get() = Float.fromBits(angleBits.get())
        set(value) {
            angleBits.set(value.toRawBits())
        }

    val rpm: Float
        get() = Float.fromBits(rpmBits.get())

    fun publish(params: EngineParams, rpm: Float, throttle: Float, live: Boolean) {
        rpmBits.set(rpm.toRawBits())
        boreBits.set(params.boreMm.toFloat().toRawBits())
        crBits.set(params.compressionRatio.toFloat().toRawBits())
        cylinders.set(params.cylinders)
        banks.set(params.banks)
        throttleBits.set(throttle.toRawBits())
        running.set(if (live) 1 else 0)
        induction.set(params.induction.ordinal)
        redlineBits.set(params.redlineRpm.toFloat().toRawBits())
        openBits.set(params.exhaustNote.open.toFloat().toRawBits())
        raspBits.set(params.exhaustNote.rasp.toFloat().toRawBits())
        barkBits.set(params.exhaustNote.bark.toFloat().toRawBits())
        lopeBits.set(params.exhaustNote.lope.toFloat().toRawBits())
    }

    fun ear(): EngineEar = EngineEar(
        boreMm = Float.fromBits(boreBits.get()).toDouble(),
        compression = Float.fromBits(crBits.get()).toDouble(),
        cylinders = cylinders.get(),
        banks = banks.get(),
        throttle = Float.fromBits(throttleBits.get()).toDouble(),
        running = running.get() == 1,
        induction = Induction.entries[induction.get().coerceIn(0, Induction.entries.lastIndex)],
        rpm = Float.fromBits(rpmBits.get()).toDouble(),
        redlineRpm = Float.fromBits(redlineBits.get()).toDouble(),
        exhaustOpen = Float.fromBits(openBits.get()).toDouble(),
        exhaustRasp = Float.fromBits(raspBits.get()).toDouble(),
        exhaustBark = Float.fromBits(barkBits.get()).toDouble(),
        exhaustLope = Float.fromBits(lopeBits.get()).toDouble(),
    )
}

/** Streams the crank. Pulses line up with the flames because both read [EngineCrank]. */
class EngineAudio(private val crank: EngineCrank) {
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var stop = false

    fun start() {
        if (thread != null) return
        val rate = 44100
        val min = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(min.coerceAtLeast(rate / 20) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return
        }
        this.track = track
        stop = false
        thread = Thread({
            mix(track, rate)
        }, "revline-engine").also { it.start() }
        track.play()
    }

    fun stop() {
        stop = true
        thread?.join(400)
        thread = null
        track?.let {
            try {
                it.pause()
                it.flush()
                it.release()
            } catch (_: IllegalStateException) {
            }
        }
        track = null
    }

    private fun mix(track: AudioTrack, rate: Int) {
        val voice = EngineVoice()
        val frames = 256
        val buf = ShortArray(frames)
        var audioAngle = crank.angle
        while (!stop) {
            val ear = crank.ear()
            val published = crank.angle
            val drift = wrap(published - audioAngle)
            audioAngle = EngineMotion.advanceCrank(audioAngle, 0f, 0f).let { (it + drift * 0.25f + 720f) % 720f }
            val degPerSec = if (ear.running) crank.rpm * EngineMotion.VISUAL_CRANK else 0f
            val step = degPerSec / rate
            for (i in buf.indices) {
                val sample = voice.sample(ear, audioAngle, rate)
                buf[i] = (sample * 28000f).toInt().coerceIn(-32767, 32767).toShort()
                audioAngle += step
                if (audioAngle >= 720f) audioAngle -= 720f
            }
            var wrote = 0
            while (wrote < buf.size && !stop) {
                val n = track.write(buf, wrote, buf.size - wrote)
                if (n <= 0) break
                wrote += n
            }
        }
    }

    private fun wrap(delta: Float): Float {
        var d = delta
        while (d > 360f) d -= 720f
        while (d < -360f) d += 720f
        return d
    }
}
