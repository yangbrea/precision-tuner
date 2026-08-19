package com.precisiontuner.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.precisiontuner.tuning.NoteMapper
import kotlin.math.abs
import kotlin.math.pow

/**
 * Plays piano strikes from the bundled Splendid Grand Piano public-domain
 * samples (assets/reference/piano/<keycenter>.wav, 62 keycenters spanning
 * MIDI 23..108).
 *
 * A target pitch is played from the nearest sampled key scaled by the
 * playback rate, which covers every note plus custom tunings and the
 * adjustable A4 reference (rate stays well within the ±2 semitone gap).
 *
 * Supports polyphony: [playChord] fires several notes at once (ear-training
 * chords), while [play] stays the single-note ear-tuning reference tone.
 * Active streams are tracked so [stop] silences every ringing note.
 */
class PianoReferenceEngine(context: Context) : AutoCloseable {

    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()

    // PCM frame count of each sampled key, populated while the WAVs are loaded
    // below (declared first: the soundIds loader references it).
    private val sampleFrames = IntArray(KEYCENTERS.size)

    private val soundIds = IntArray(KEYCENTERS.size) { i ->
        val midi = KEYCENTERS[i]
        context.assets.openFd("reference/piano/${midi.toString().padStart(3, '0')}.wav").use { afd ->
            // Record the PCM frame count (standard 44-byte RIFF header, 16-bit
            // mono) so playback duration can be predicted for UI state reset.
            sampleFrames[i] = ((afd.length - WAV_HEADER_BYTES) / BYTES_PER_FRAME).toInt()
            pool.load(afd, 1)
        }
    }

    // Active playback streams, oldest first. SoundPool returns 0 when the pool
    // is exhausted; the oldest stream is then dropped so the newest note always
    // sounds (rapid chord re-triggers steal the slot).
    private val activeStreams = IntArray(MAX_STREAMS)
    private var streamCount = 0

    /** Plays the nearest piano sample for [midi], pitch-matched via playback rate. */
    fun playMidi(midi: Int, volume: Float = 1f) {
        val clamped = midi.coerceIn(MIN_MIDI, MAX_MIDI)
        val v = volume.coerceIn(0f, 1f)
        val streamId = pool.play(
            soundIds[sampleIndexForMidi(clamped)],
            v, v, 1, 0, sampleRateForMidi(clamped),
        )
        if (streamId == 0) return
        if (streamCount == MAX_STREAMS) {
            pool.stop(activeStreams[0])
            System.arraycopy(activeStreams, 1, activeStreams, 0, MAX_STREAMS - 1)
            streamCount--
        }
        activeStreams[streamCount] = streamId
        streamCount++
    }

    /** Plays several notes simultaneously (a chord). */
    fun playChord(midis: List<Int>, volume: Float = POLYPHONY_VOLUME) {
        midis.forEach { playMidi(it, volume) }
    }

    /** Plays the nearest piano sample, pitch-matched to [frequency]. */
    fun play(frequency: Double) {
        if (!frequency.isFinite() || frequency <= 0.0) return
        playMidi(NoteMapper.midiFromFrequency(frequency))
    }

    /** Silences every strike still ringing. */
    fun stop() {
        for (i in 0 until streamCount) {
            pool.stop(activeStreams[i])
        }
        streamCount = 0
    }

    /**
     * Predicted playback duration of the reference tone for [midi], including
     * the playback-rate stretch: low notes (rate < 1) ring longer, high notes
     * shorter. Used by the tuner to reset the "playing" UI state when the
     * single piano strike has decayed.
     */
    fun playDurationMillis(midi: Int): Long {
        val best = sampleIndexForMidi(midi)
        return durationMillis(sampleFrames[best], sampleRateForMidi(midi))
    }

    override fun close() = pool.release()

    companion object {
        const val MIN_MIDI = 21
        const val MAX_MIDI = 108
        const val MAX_STREAMS = 8
        const val SAMPLE_RATE = 44100
        const val WAV_HEADER_BYTES = 44
        const val BYTES_PER_FRAME = 2 // 16-bit mono

        /** Per-note volume for multi-note playback; several concurrent streams
         *  at full volume would clip the mix. */
        const val POLYPHONY_VOLUME = 0.8f

        /** Sampled keys from the Splendid Grand Piano MF layer. */
        val KEYCENTERS = intArrayOf(
            23, 27, 29, 31, 33, 35, 37, 38, 40, 41, 43, 45, 47, 48, 50, 52, 53,
            55, 56, 57, 58, 59, 60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79,
            80, 81, 82, 83, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97,
            98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108,
        )

        /**
         * Measured deviation (cents) of each bundled sample from its nominal
         * equal-temperament pitch, one per [KEYCENTERS] entry, read by the
         * app's own YinPitchDetector (ReferenceSamplePitchTest). Octave/garbage
         * readings at the extremes were replaced with the local median.
         */
        val CALIBRATION = intArrayOf(
            -4, -9, 11, 10, 12, 12, 12, 17, 19, 9, 7, 7, 9, 9, 9, 9, 8, 10, 11,
            10, 5, 6, 7, 7, 5, 6, 7, 8, 9, 5, 5, 6, 7, 7, 8, 14, 7, 5, 7, 9, 12,
            12, 10, 5, 6, 10, 17, 12, 18, 23, -1, 12, 22, 12, 12, 12, 12, 12, 12,
            12, 12, 12,
        )

        /** Index into [KEYCENTERS] of the sampled key nearest to [midi]. */
        fun sampleIndexForMidi(midi: Int): Int {
            var best = 0
            var bestDistance = Int.MAX_VALUE
            for (i in KEYCENTERS.indices) {
                val distance = abs(KEYCENTERS[i] - midi)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                }
            }
            return best
        }

        /**
         * Playback-rate multiplier that pitch-matches [midi] to the nearest
         * sampled key, compensated by that key's measured deviation from equal
         * temperament. Samples are spaced ≤ 2 semitones apart, so the rate is
         * always within [MIN_RATE, MAX_RATE].
         */
        fun sampleRateForMidi(midi: Int): Float {
            val best = sampleIndexForMidi(midi)
            val keycenter = KEYCENTERS[best]
            val sampleFrequency = NoteMapper.frequencyFromMidi(keycenter)
            val targetFrequency = NoteMapper.frequencyFromMidi(midi)
            val calibrationCents = CALIBRATION[best]
            val rate = (targetFrequency / sampleFrequency).toFloat() *
                (2f).pow((-calibrationCents / 1200f))
            return rate.coerceIn(MIN_RATE, MAX_RATE)
        }

        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f

        /** Playback duration in milliseconds for a sample of [sampleFrames]
         *  frames played at [rate]; pure so it can be unit-tested on the JVM. */
        fun durationMillis(sampleFrames: Int, rate: Float): Long {
            if (sampleFrames <= 0 || rate <= 0f) return 0L
            return (sampleFrames / (SAMPLE_RATE * rate.toDouble()) * 1000.0).toLong()
        }
    }
}
