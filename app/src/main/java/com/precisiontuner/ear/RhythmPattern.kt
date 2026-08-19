package com.precisiontuner.ear

/**
 * One rhythmic event (note or rest) expressed on a 1/12-beat grid so that
 * quarters (12), eighths (6), sixteenths (3), triplets (4), dotted values
 * (18 / 9 / 36) and the whole note (48) all stay exact integers.
 *
 * @param grids  duration in 1/12-beat grid units (always > 0).
 * @param isRest true = rest: nothing plays and no tap is expected.
 */
data class RhythmNote(
    val grids: Int,
    val isRest: Boolean = false,
)

/**
 * A preset rhythm pattern: the note/rest sequence of one exercise question.
 * [beatsPerBar] / [beatUnit] form the time signature (e.g. 4/4, 3/4, 6/8,
 * 2/4) and only decorate the staff notation; a pattern does not have to fill
 * the bar. One beat = 12 grid units, so a (a/b) bar holds 48×a/b grid units.
 */
data class RhythmPattern(
    val name: String,
    val beatsPerBar: Int = 4,
    val beatUnit: Int = 4,
    val notes: List<RhythmNote>,
) {
    init {
        require(notes.isNotEmpty()) { "rhythm pattern '$name' has no notes" }
        require(notes.all { it.grids > 0 }) { "rhythm pattern '$name' has a non-positive duration" }
        require(beatsPerBar > 0 && beatUnit > 0) { "rhythm pattern '$name' has an invalid time signature" }
    }

    /** Total grid units of the whole pattern (one beat = 12). */
    val totalGrids: Int
        get() = notes.sumOf { it.grids }

    /** Number of taps the user must reproduce (rests are silent). */
    val expectedTaps: Int
        get() = notes.count { !it.isRest }

    /** Grid offset where each note/rest starts, in order. */
    val onsetGrids: List<Int>
        get() {
            val result = ArrayList<Int>(notes.size)
            var cursor = 0
            for (note in notes) {
                result += cursor
                cursor += note.grids
            }
            return result
        }

    /** Grid offsets of the audible notes only (rests skipped). */
    val tapOnsetGrids: List<Int>
        get() = notes.indices
            .filter { !notes[it].isRest }
            .map { onsetGrids[it] }
}
