package com.precisiontuner.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.precisiontuner.tuning.NoteMapper

/**
 * Plays a piano strike as the ear-tuning reference tone (replaces the old
 * looping sine engine).
 *
 * One WAV per MIDI note (021..108, full piano range) is bundled under
 * assets/reference/piano/<midi>.wav. The exact target frequency is matched by
 * picking the nearest note sample and scaling the playback rate, which covers
 * custom tunings and the adjustable A4 reference (rate stays within one
 * semitone of the sample).
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

    private val soundIds = IntArray(MAX_MIDI - MIN_MIDI + 1) { i ->
        val midi = MIN_MIDI + i
        context.assets.openFd("reference/piano/${midi.toString().padStart(3, '0')}.wav").use { afd ->
            pool.load(afd, 1)
        }
    }
    private var lastStreamId = 0

    /** Plays the nearest piano sample, pitch-matched to [frequency]. */
    fun play(frequency: Double) {
        if (!frequency.isFinite() || frequency <= 0.0) return
        val midi = NoteMapper.midiFromFrequency(frequency).coerceIn(MIN_MIDI, MAX_MIDI)
        val sampleFrequency = NoteMapper.frequencyFromMidi(midi)
        val rate = (frequency / sampleFrequency).toFloat().coerceIn(0.5f, 2.0f)
        lastStreamId = pool.play(
            soundIds[midi - MIN_MIDI],
            1f, 1f, 1, 0, rate,
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
    }
}
