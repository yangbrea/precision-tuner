package com.precisiontuner.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.precisiontuner.tuning.NoteMapper
import kotlin.math.abs
import kotlin.math.pow

/**
 * Plays a piano strike as the ear-tuning reference tone (replaces the old
 * looping sine engine).
 *
 * Samples are one WAV per sampled key (assets/reference/piano/<keycenter>.wav,
 * 62 keycenters spanning MIDI 23..108 from the Splendid Grand Piano public
 * domain set). A target pitch is played from the nearest sampled key scaled by
 * the playback rate, which covers every note plus custom tunings and the
 * adjustable A4 reference (rate stays well within the ±2 semitone gap).
 */
class PianoReferenceEngine(context: Context) : AutoCloseable {

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()

    private val soundIds = IntArray(KEYCENTERS.size) { i ->
        val midi = KEYCENTERS[i]
        context.assets.openFd("reference/piano/${midi.toString().padStart(3, '0')}.wav").use { afd ->
            pool.load(afd, 1)
        }
    }
    private var lastStreamId = 0

    /** Plays the nearest piano sample, pitch-matched to [frequency]. */
    fun play(frequency: Double) {
        if (!frequency.isFinite() || frequency <= 0.0) return
        val midi = NoteMapper.midiFromFrequency(frequency).coerceIn(MIN_MIDI, MAX_MIDI)
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (i in KEYCENTERS.indices) {
            val distance = abs(KEYCENTERS[i] - midi)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        val keycenter = KEYCENTERS[best]
        val sampleFrequency = NoteMapper.frequencyFromMidi(keycenter)
        // The bundled set is tuned sharp (A4 ≈ 442 Hz + per-note
        // inharmonicity), so compensate each sampled key by its measured
        // deviation (see ReferenceSamplePitchTest) to play back in tune.
        val calibrationCents = CALIBRATION[best]
        val rate = (frequency / sampleFrequency).toFloat() *
            (2f).pow((-calibrationCents / 1200f))
        lastStreamId = pool.play(
            soundIds[best],
            1f, 1f, 1, 0, rate.coerceIn(0.5f, 2.0f),
        )
    }

    /** Silences any strike still ringing. */
    fun stop() {
        if (lastStreamId != 0) {
            pool.stop(lastStreamId)
            lastStreamId = 0
        }
    }

    override fun close() = pool.release()

    private companion object {
        const val MIN_MIDI = 21
        const val MAX_MIDI = 108

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
    }
}
