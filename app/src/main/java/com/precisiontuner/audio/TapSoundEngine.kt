package com.precisiontuner.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build

/**
 * Low-latency playback of a full piano C4 strike, used for rhythm-tap
 * feedback. SoundPool's [android.media.SoundPool.play] latency (tens to
 * hundreds of ms) is clearly audible when the user taps along, so this engine
 * replays the whole bundled C4 sample from static [AudioTrack] buffers — the
 * lowest-latency repeat path on Android.
 *
 * A single static track cannot overlap, so [TRACK_COUNT] tracks are rotated:
 * each tap plays from the next buffer and the previous strike rings out
 * naturally (same polyphony behaviour as SoundPool's stream limit, but with
 * static-buffer latency). The sample ends at its natural decay tail, so a tap
 * never sounds cut off.
 */
class TapSoundEngine(context: Context) {

    // The full bundled C4 piano sample (060.wav): 2 s, tail decaying to
    // silence, so playback ends naturally instead of being chopped.
    private val samples: ShortArray = decodePcm(
        context.assets.open("reference/piano/060.wav").use { it.readBytes() },
        attackMs = FULL_SAMPLE_MS,
    )

    private val tracks = arrayOfNulls<AudioTrack>(TRACK_COUNT)
    private var nextTrack = 0

    /** Creates the rotated static tracks and primes the audio path. */
    fun start() {
        if (tracks[0] != null) return
        for (i in 0 until TRACK_COUNT) {
            val bufBytes = maxOf(
                AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING),
                samples.size * BYTES_PER_FRAME,
            )
            val builder = AudioTrack.Builder()
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
                .setBufferSizeInBytes(bufBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
            // Request the low-latency path where the platform offers it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            val t = builder.build()
            t.write(samples, 0, samples.size)
            tracks[i] = t
        }
        // Prime: one play/stop on the first track initializes the audioflinger
        // path so the first real tap does not pay the setup cost.
        tracks[0]?.let { t ->
            t.play()
            t.stop()
        }
    }

    /** Plays one strike from the next rotated buffer. */
    fun play(volume: Float) {
        val t = tracks[nextTrack] ?: return
        nextTrack = (nextTrack + 1) % TRACK_COUNT
        t.setVolume(volume.coerceIn(0f, 1f))
        if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
        t.reloadStaticData()
        t.play()
    }

    fun stop() {
        tracks.forEach { t ->
            if (t != null) {
                try {
                    if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
                } catch (_: IllegalStateException) {
                    // already stopped
                }
                t.release()
            }
        }
        tracks.fill(null)
        nextTrack = 0
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val FULL_SAMPLE_MS = 2000
        const val WAV_HEADER_BYTES = 44
        const val BYTES_PER_FRAME = 2 // 16-bit mono
        const val TRACK_COUNT = 8

        private const val CHANNEL = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /**
         * Decodes the first [attackMs] (or the whole file, when it is longer
         * than the sample) of a standard 44-byte-header, 16-bit mono WAV into
         * PCM. Pure so the header math is unit-testable.
         */
        fun decodePcm(bytes: ByteArray, attackMs: Int): ShortArray {
            require(bytes.size > WAV_HEADER_BYTES) { "WAV smaller than its header" }
            val dataSize = ((bytes[40].toInt() and 0xFF)) or
                ((bytes[41].toInt() and 0xFF) shl 8) or
                ((bytes[42].toInt() and 0xFF) shl 16) or
                ((bytes[43].toInt() and 0xFF) shl 24)
            val maxFrames = dataSize / BYTES_PER_FRAME
            val frames = (attackMs * SAMPLE_RATE / 1000).coerceAtMost(maxFrames)
            return ShortArray(frames) { i ->
                val offset = WAV_HEADER_BYTES + i * BYTES_PER_FRAME
                ((bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8)).toShort()
            }
        }
    }
}
