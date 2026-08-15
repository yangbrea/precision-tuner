package com.example.tunner.tuning

import kotlin.math.log
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A named pitch in 12-tone equal temperament (12-TET).
 *
 * @param name    sharp-spelling note name, e.g. "C", "C♯", "B".
 * @param octave  scientific octave number (A4 -> octave 4).
 * @param midi    MIDI note number (A4 -> 69).
 * @param frequency the exact equal-temperament frequency of this note for the given A4 reference.
 */
data class Note(
    val name: String,
    val octave: Int,
    val midi: Int,
    val frequency: Double,
) {
    /** Full name with octave, e.g. "A♯4". */
    val fullName: String get() = "$name$octave"
}

/**
 * Maps between frequency and the nearest 12-TET note / cents deviation.
 *
 * A4 defaults to 440 Hz but is configurable to support different concert pitches.
 */
object NoteMapper {

    const val DEFAULT_A4 = 440.0
    const val A4_MIDI = 69

    val NOTE_NAMES: List<String> = listOf(
        "C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"
    )

    private fun log2(x: Double): Double = log(x, 2.0)

    /** MIDI note number nearest to [freq] under reference [a4]. */
    fun midiFromFrequency(freq: Double, a4: Double = DEFAULT_A4): Int =
        (A4_MIDI + 12.0 * log2(freq / a4)).roundToInt()

    /** Equal-temperament frequency of [midi] under reference [a4]. */
    fun frequencyFromMidi(midi: Int, a4: Double = DEFAULT_A4): Double =
        a4 * 2.0.pow((midi - A4_MIDI) / 12.0)

    /** Cents deviation of [freq] from the nearest 12-TET note (negative = flat). */
    fun cents(freq: Double, a4: Double = DEFAULT_A4): Double {
        val midi = midiFromFrequency(freq, a4)
        val reference = frequencyFromMidi(midi, a4)
        return 1200.0 * log2(freq / reference)
    }

    /** Nearest 12-TET note for [freq]. */
    fun noteFromFrequency(freq: Double, a4: Double = DEFAULT_A4): Note {
        val midi = midiFromFrequency(freq, a4)
        val name = NOTE_NAMES[((midi % 12) + 12) % 12]
        val octave = midi / 12 - 1
        return Note(name, octave, midi, frequencyFromMidi(midi, a4))
    }
}
