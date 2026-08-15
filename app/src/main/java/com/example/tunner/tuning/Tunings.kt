package com.example.tunner.tuning

import kotlin.math.abs

/**
 * One string of a guitar, in a particular tuning.
 *
 * @param number    1..6, where 1 is the highest-pitched (thin E) string.
 * @param noteName  display name without octave, e.g. "E".
 * @param fullNote  scientific name, e.g. "E4".
 * @param midi      MIDI note number of the target pitch.
 * @param frequency target frequency in Hz (equal temperament, A4 = 440).
 */
data class GuitarString(
    val number: Int,
    val noteName: String,
    val fullNote: String,
    val midi: Int,
    val frequency: Double,
)

object GuitarTuning {

    /** Standard tuning, ordered from 1st (high E) to 6th (low E). */
    val STANDARD: List<GuitarString> = listOf(
        GuitarString(1, "E", "E4", 64, 329.63),
        GuitarString(2, "B", "B3", 59, 246.94),
        GuitarString(3, "G", "G3", 55, 196.00),
        GuitarString(4, "D", "D3", 50, 146.83),
        GuitarString(5, "A", "A2", 45, 110.00),
        GuitarString(6, "E", "E2", 40, 82.41),
    )

    /** The string whose target note is closest to [midi], or null if the list is empty. */
    fun nearestString(midi: Int): GuitarString? =
        STANDARD.minByOrNull { abs(it.midi - midi) }

    fun byNumber(number: Int): GuitarString? = STANDARD.firstOrNull { it.number == number }
}
