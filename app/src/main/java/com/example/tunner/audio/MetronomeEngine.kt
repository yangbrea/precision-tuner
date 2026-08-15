package com.example.tunner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Plays metronome clicks through an [AudioTrack] (STREAM_MUSIC, mono PCM).
 *
 * The click samples are pre-generated; [playClick] scales them by volume and
 * writes them. A uniform write→playback latency does not affect beat timing
 * because every beat is delayed equally.
 */
class MetronomeEngine {

    private var track: AudioTrack? = null

    private val accentClick = ClickSound.generate(ClickSound.ACCENT_FREQ)
    private val normalClick = ClickSound.generate(ClickSound.NORMAL_FREQ)

    fun start() {
        if (track != null) return
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(ENCODING)
                    .setChannelMask(CHANNEL)
                    .build()
            )
            .setBufferSizeInBytes(minBuf.coerceAtLeast(8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.play()
        track = t
    }

    fun playClick(accent: Boolean, volume: Float) {
        val t = track ?: return
        val src = if (accent) accentClick else normalClick
        val buf = scale(src, volume)
        t.write(buf, 0, buf.size)
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

    private fun scale(src: ShortArray, volume: Float): ShortArray {
        val v = volume.coerceIn(0f, 1f)
        if (v >= 1f) return src
        return ShortArray(src.size) { i -> (src[i] * v).toInt().toShort() }
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
