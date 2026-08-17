package com.precisiontuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Plays a continuous reference tone (pure sine) for tuning by ear.
 *
 * The sample buffer is an integer number of periods so the loop point is
 * seamless (no audible click).
 */
class ReferenceToneEngine(private val sampleRate: Int = 44100) {

    private var track: AudioTrack? = null

    val isPlaying: Boolean get() = track != null

    fun start(frequency: Double, volume: Float = 0.5f) {
        stop()
        val buf = generateSine(frequency)
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf.coerceAtLeast(buf.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(buf, 0, buf.size)
        t.setLoopPoints(0, buf.size, -1)
        t.setVolume(volume.coerceIn(0f, 1f))
        t.play()
        track = t
    }

    fun stop() {
        val t = track ?: return
        track = null
        try {
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
        } catch (_: IllegalStateException) {
            // already stopped
        }
        t.release()
    }

    private fun generateSine(frequency: Double): ShortArray {
        val samplesPerPeriod = sampleRate / frequency
        val periods = (sampleRate / samplesPerPeriod).roundToInt().coerceAtLeast(1)
        val n = (samplesPerPeriod * periods).roundToInt().coerceAtLeast(1)
        return ShortArray(n) { i ->
            (0.5 * sin(2.0 * PI * frequency * i / sampleRate) * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
