package com.precisiontuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Plays metronome clicks through an [AudioTrack] (STREAM_MUSIC, mono PCM).
 *
 * All writes are non-blocking ([AudioTrack.WRITE_NON_BLOCKING]) and the engine
 * counts every frame actually accepted into the buffer ([framesQueued]). The
 * caller drives the schedule from that count: the frame a click starts at
 * determines when it is heard (frame ÷ sample rate plus a constant buffer
 * latency), so beat intervals stay exact regardless of write timing or
 * device start-up latency.
 *
 * [start] queues the first click at the head of the buffer *before* [play],
 * so beat 1 sounds as soon as the audio path starts instead of after a primed
 * buffer of silence.
 */
class MetronomeEngine {

    private var track: AudioTrack? = null
    private var queuedFrames = 0L

    private val accentClick = ClickSound.generate(ClickSound.ACCENT_FREQ)
    private val normalClick = ClickSound.generate(ClickSound.NORMAL_FREQ)
    private val subdivisionClick = ClickSound.generate(ClickSound.SUBDIVISION_FREQ)

    private val silenceChunk = ShortArray(SILENCE_CHUNK)

    /** The click currently being queued (non-blocking writes can be partial). */
    private var pendingClick: ShortArray? = null
    private var pendingOffset = 0

    /**
     * Queues the first (beat-1) click at the head of the buffer, then starts
     * playback. The buffer is empty before [play], so the click is accepted
     * immediately and is heard as soon as the HAL starts consuming.
     */
    fun start(firstAccent: Boolean, firstVolume: Float) {
        if (track != null) return
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufBytes = minBuf.coerceAtLeast(8192)
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
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        queuedFrames = 0
        pendingClick = null
        val first = scale(if (firstAccent) accentClick else normalClick, firstVolume)
        var offset = 0
        while (offset < first.size) {
            val w = t.write(first, offset, first.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            if (w <= 0) break
            offset += w
        }
        queuedFrames = offset.toLong()
        t.play()
        track = t
    }

    /** Total frames accepted into the current track's buffer since [start]. */
    val framesQueued: Long
        get() = queuedFrames

    /**
     * One attempt to queue up to [maxFrames] frames of silence (chunked
     * non-blocking writes).
     *
     * @return frames actually queued (> 0), 0 when the buffer is full (retry
     *         later), or < 0 when the track is gone.
     */
    fun writeSilence(maxFrames: Int): Int {
        val t = track ?: return ERROR_DEAD
        if (maxFrames <= 0) return 0
        var remaining = maxFrames
        var total = 0
        while (remaining > 0) {
            val chunk = minOf(remaining, silenceChunk.size)
            val w = t.write(silenceChunk, 0, chunk, AudioTrack.WRITE_NON_BLOCKING)
            if (w <= 0) {
                if (total > 0) {
                    queuedFrames += total
                    return total
                }
                return w
            }
            total += w
            remaining -= w
        }
        queuedFrames += total
        return total
    }

    /**
     * Starts (or resumes) queueing one click; non-blocking writes can accept
     * only part of the click, so the caller must keep calling until the whole
     * click has been queued.
     *
     * @return true when the click is fully queued, false while it is still
     *         pending (buffer full — retry later), and true-but-idle when the
     *         track is gone.
     */
    fun playClick(accent: Boolean, subdivision: Boolean, volume: Float): Boolean {
        val t = track ?: return true
        if (pendingClick == null) {
            val src = when {
                accent -> accentClick
                subdivision -> subdivisionClick
                else -> normalClick
            }
            pendingClick = scale(src, volume)
            pendingOffset = 0
        }
        val buf = pendingClick ?: return true
        while (pendingOffset < buf.size) {
            val w = t.write(buf, pendingOffset, buf.size - pendingOffset, AudioTrack.WRITE_NON_BLOCKING)
            if (w <= 0) return false
            pendingOffset += w
            queuedFrames += w
        }
        pendingClick = null
        pendingOffset = 0
        return true
    }

    fun stop() {
        val t = track ?: return
        track = null
        queuedFrames = 0
        pendingClick = null
        pendingOffset = 0
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

    companion object {
        const val SAMPLE_RATE = 44100
        /** Silence chunk per write attempt (50 ms), small enough for a snappy stop. */
        const val SILENCE_CHUNK = 2205
        private const val ERROR_DEAD = -1
        private const val CHANNEL = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
