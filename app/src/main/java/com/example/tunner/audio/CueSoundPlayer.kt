package com.example.tunner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Plays a one-shot "in tune" cue ("ding") via a short [AudioTrack].
 *
 * The sample is generated with [ClickSound.generate] (a decaying sine) and
 * played once; the track is released automatically when playback finishes.
 */
class CueSoundPlayer(private val sampleRate: Int = 44100) {

    private var track: AudioTrack? = null

    fun play() {
        stop()
        val buf = ClickSound.generate(DING_FREQ, durationMs = DING_MS, amplitude = DING_AMP)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buf.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(buf, 0, buf.size)
        t.setNotificationMarkerPosition(buf.size)
        t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(at: AudioTrack) {
                this@CueSoundPlayer.track = null
                at.release()
            }

            override fun onPeriodicNotification(at: AudioTrack) {}
        })
        t.play()
        track = t
    }

    fun stop() {
        track?.let {
            track = null
            try {
                it.release()
            } catch (_: Exception) {
                // already released
            }
        }
    }

    private companion object {
        const val DING_FREQ = 1568.0 // G6
        const val DING_MS = 140.0
        const val DING_AMP = 0.4
    }
}
