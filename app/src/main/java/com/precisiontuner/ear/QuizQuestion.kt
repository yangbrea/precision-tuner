package com.precisiontuner.ear

/**
 * A single generated recognition question. [options] holds four shuffled
 * answer labels (including the correct one at [answerIndex]); [noteMidis] are
 * the notes to play; [harmonic] marks intervals played simultaneously.
 *
 * [rhythmPattern] is set only for [ExerciseType.RHYTHM], which is a
 * performance answer (no options): the user taps the rhythm and the result is
 * scored against the pattern instead of picking [answerIndex].
 */
data class QuizQuestion(
    val type: ExerciseType,
    val answerName: String,
    val options: List<String>,
    val answerIndex: Int,
    val noteMidis: List<Int>,
    val harmonic: Boolean = false,
    val staffNotation: StaffNotation? = null,
    val rhythmPattern: RhythmPattern? = null,
)
