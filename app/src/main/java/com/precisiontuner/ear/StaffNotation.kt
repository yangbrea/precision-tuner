package com.precisiontuner.ear

/** Clefs supported by the single-note staff-reading exercise. */
enum class StaffClef(val accessibilityName: String) {
    TREBLE("高音谱号"),
    BASS("低音谱号"),
}

enum class NoteLetter(val semitone: Int) {
    C(0), D(2), E(4), F(5), G(7), A(9), B(11),
}

enum class Accidental(val symbol: String) {
    NATURAL(""),
    SHARP("♯"),
    FLAT("♭"),
}

/**
 * Explicit written spelling for a staff question. MIDI alone is insufficient:
 * C♯ and D♭ sound alike but occupy different staff positions.
 */
data class StaffNotation(
    val midi: Int,
    val letter: NoteLetter,
    val octave: Int,
    val accidental: Accidental,
    val clef: StaffClef = clefForMidi(midi),
) {
    val displayName: String get() = "${letter.name}${accidental.symbol}$octave"

    init {
        require(midi in 0..127) { "MIDI out of range: $midi" }
        val writtenPitch = Math.floorMod(letter.semitone + accidentalDelta(accidental), 12)
        require(writtenPitch == Math.floorMod(midi, 12)) {
            "Spelling $displayName does not match MIDI $midi"
        }
    }

    companion object {
        fun fromMidi(midi: Int, preferFlat: Boolean = false): StaffNotation {
            require(midi in 0..127) { "MIDI out of range: $midi" }
            val octave = midi / 12 - 1
            val (letter, accidental) = when (Math.floorMod(midi, 12)) {
                0 -> NoteLetter.C to Accidental.NATURAL
                1 -> if (preferFlat) NoteLetter.D to Accidental.FLAT else NoteLetter.C to Accidental.SHARP
                2 -> NoteLetter.D to Accidental.NATURAL
                3 -> if (preferFlat) NoteLetter.E to Accidental.FLAT else NoteLetter.D to Accidental.SHARP
                4 -> NoteLetter.E to Accidental.NATURAL
                5 -> NoteLetter.F to Accidental.NATURAL
                6 -> if (preferFlat) NoteLetter.G to Accidental.FLAT else NoteLetter.F to Accidental.SHARP
                7 -> NoteLetter.G to Accidental.NATURAL
                8 -> if (preferFlat) NoteLetter.A to Accidental.FLAT else NoteLetter.G to Accidental.SHARP
                9 -> NoteLetter.A to Accidental.NATURAL
                10 -> if (preferFlat) NoteLetter.B to Accidental.FLAT else NoteLetter.A to Accidental.SHARP
                else -> NoteLetter.B to Accidental.NATURAL
            }
            return StaffNotation(midi, letter, octave, accidental)
        }
    }
}

fun clefForMidi(midi: Int): StaffClef =
    if (midi <= 59) StaffClef.BASS else StaffClef.TREBLE

private fun accidentalDelta(accidental: Accidental): Int = when (accidental) {
    Accidental.NATURAL -> 0
    Accidental.SHARP -> 1
    Accidental.FLAT -> -1
}
