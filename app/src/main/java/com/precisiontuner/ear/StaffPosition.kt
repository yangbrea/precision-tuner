package com.precisiontuner.ear

import kotlin.math.floor

/**
 * Maps explicitly spelled notes onto treble and bass staves.
 *
 * Line offsets are measured in staff-line gaps: E4 (lowest line) = 0,
 * G4 = 1, …, F5 (top line) = 4, with the spaces at half offsets (F4 = 0.5).
 * C4 sits on the first ledger line below the staff (-1), A3 on the second
 * (-2), etc. Pure and deterministic so the staff drawing and the tests share
 * the same rules.
 */
object StaffPosition {

    /** Lowest staff line (E4) and top staff line (F5) in line-gap units. */
    const val LOWEST_LINE = 0.0
    const val TOP_LINE = 4.0

    /** Positions of every staff line: E4 G4 B4 D5 F5. */
    const val LINE_COUNT = 5

    data class StaffNote(
        val lineOffset: Float,
        val ledgerOffsets: List<Int>,
        val accidental: Accidental,
    )

    /** Legacy sharp-spelling helper retained for callers that only have MIDI. */
    fun letterIndex(midi: Int): Int = when (midi % 12) {
        0, 1 -> 0   // C, C#
        2, 3 -> 1   // D, D#
        4 -> 2      // E
        5, 6 -> 3   // F, F#
        7, 8 -> 4   // G, G#
        9, 10 -> 5  // A, A#
        else -> 6   // B
    }

    /**
     * Line-gap offset of [midi] on the G staff: (letter index relative to E4,
     * shifted by octaves) / 2, since adjacent lines are a third (2 letters).
     */
    fun lineOffset(midi: Int): Float {
        val octave = midi / 12 - 1
        return (letterIndex(midi) + 7 * (octave - 4) - 2) / 2f
    }

    /** Position for an explicitly written note under its selected clef. */
    fun lineOffset(notation: StaffNotation): Float {
        val diatonic = notation.octave * 7 + notation.letter.ordinal
        val lowestLine = when (notation.clef) {
            StaffClef.TREBLE -> 4 * 7 + NoteLetter.E.ordinal // E4
            StaffClef.BASS -> 2 * 7 + NoteLetter.G.ordinal   // G2
        }
        return (diatonic - lowestLine) / 2f
    }

    /**
     * Ledger lines needed for [midi]: the single line the note head sits on
     * (integer offset) when it lies on a line, or the nearest line below /
     * above when it sits in a space outside the staff. Empty inside the staff.
     */
    fun ledgerOffsets(lineOffset: Float): List<Int> {
        return when {
            lineOffset <= -1f -> (-1 downTo kotlin.math.ceil(lineOffset.toDouble()).toInt()).toList()
            lineOffset >= 5f -> (5..floor(lineOffset.toDouble()).toInt()).toList()
            else -> emptyList()
        }
    }

    fun ledgerOffsets(midi: Int): List<Int> = ledgerOffsets(lineOffset(midi))

    /** True when the note name uses a sharp accidental (black key). */
    fun isBlackKey(midi: Int): Boolean = midi % 12 in setOf(1, 3, 6, 8, 10)

    fun staffNote(notation: StaffNotation): StaffNote {
        val offset = lineOffset(notation)
        return StaffNote(offset, ledgerOffsets(offset), notation.accidental)
    }

    fun staffNote(midi: Int): StaffNote =
        staffNote(StaffNotation.fromMidi(midi))
}
