package com.precisiontuner.tuning

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/** Tuning temperament used by the chromatic tuner. */
enum class Temperament(val label: String) {
    EQUAL("十二平均律"),
    PYTHAGOREAN("五度相生律"),
    JUST("纯律"),
}

/** A note resolved against a temperament: nearest pitch class plus deviation. */
data class TemperamentNote(
    val name: String,
    val octave: Int,
    val midi: Int,
    val cents: Double,
)

/**
 * Non-equal temperament pitch resolution.
 *
 * Both non-equal scales are C-based and anchored so that A4 equals the
 * reference exactly: frequency(pc, octave) = a4 * (ratio[pc] / ratio[A]) *
 * 2^(octave - 4). Pitch class ratios follow the standard Pythagorean
 * (fifth 3:2) and just (5-limit Ptolemaic) chromatic scales.
 */
object Temperaments {

    /** Frequency of [pitchClass] in [octave] under reference [a4]. */
    fun frequency(temperament: Temperament, pitchClass: Int, octave: Int, a4: Double): Double {
        val pc = Math.floorMod(pitchClass, 12)
        if (temperament == Temperament.EQUAL) {
            return NoteMapper.frequencyFromMidi((octave + 1) * 12 + pc, a4)
        }
        val ratios = RATIOS.getValue(temperament)
        return a4 * ratios[pc] / ratios[A_PITCH_CLASS] *
            Math.pow(2.0, (octave - 4).toDouble())
    }

    /**
     * Nearest note for [frequency] in [temperament] under reference [a4].
     *
     * For non-equal temperaments every pitch class is evaluated at its
     * nearest octave and the one with the smallest |deviation| wins.
     */
    fun nearestNote(frequency: Double, a4: Double, temperament: Temperament): TemperamentNote {
        require(frequency.isFinite() && frequency > 0.0)
        if (temperament == Temperament.EQUAL) {
            val note = NoteMapper.noteFromFrequency(frequency, a4)
            return TemperamentNote(note.name, note.octave, note.midi, NoteMapper.cents(frequency, a4))
        }
        val ratios = RATIOS.getValue(temperament)
        val aRatio = ratios[A_PITCH_CLASS]
        var best: TemperamentNote? = null
        var bestAbs = Double.MAX_VALUE
        for (pc in 0 until 12) {
            // Octave-4 target for this pitch class, then snap to the nearest octave.
            val f4 = a4 * ratios[pc] / aRatio
            val baseCents = 1200.0 * log2(frequency / f4)
            val octave = 4 + (baseCents / 1200.0).roundToInt()
            val cents = baseCents - 1200.0 * (octave - 4)
            val absCents = abs(cents)
            if (absCents < bestAbs) {
                bestAbs = absCents
                best = TemperamentNote(
                    name = NoteMapper.NOTE_NAMES[pc],
                    octave = octave,
                    midi = (octave + 1) * 12 + pc,
                    cents = cents,
                )
            }
        }
        return best ?: TemperamentNote("A", 4, 69, 0.0)
    }

    private const val A_PITCH_CLASS = 9

    /** Pitch class ratios against C (C = 1); only non-equal temperaments use these. */
    private val RATIOS: Map<Temperament, DoubleArray> = mapOf(
        Temperament.PYTHAGOREAN to doubleArrayOf(
            1.0,             // C
            2187.0 / 2048.0, // C#
            9.0 / 8.0,       // D
            32.0 / 27.0,     // Eb
            81.0 / 64.0,     // E
            4.0 / 3.0,       // F
            729.0 / 512.0,   // F#
            3.0 / 2.0,       // G
            128.0 / 81.0,    // Ab
            27.0 / 16.0,     // A
            16.0 / 9.0,      // Bb
            243.0 / 128.0,   // B
        ),
        Temperament.JUST to doubleArrayOf(
            1.0,             // C
            16.0 / 15.0,     // C#
            9.0 / 8.0,       // D
            6.0 / 5.0,       // Eb
            5.0 / 4.0,       // E
            4.0 / 3.0,       // F
            45.0 / 32.0,     // F#
            3.0 / 2.0,       // G
            8.0 / 5.0,       // Ab
            5.0 / 3.0,       // A
            9.0 / 5.0,       // Bb
            15.0 / 8.0,      // B
        ),
    )
}
