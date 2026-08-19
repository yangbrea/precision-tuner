package com.precisiontuner.ear

/**
 * System-preset question pools per [Difficulty] for every exercise.
 *
 * All pools keep at least [QuestionGenerator.OPTION_COUNT] candidates so a
 * 4-option question can always be generated. Pools grow with difficulty:
 * register (note), pool size (interval/chord/scale) and, on HARD scales,
 * a random ascending/descending playback direction applied by the generator.
 */
object DifficultyPresets {

    // ---- 单音识别: candidate MIDI pools ----------------------------------

    /** C4(60) .. B4(71) naturals (white keys). */
    val NOTE_EASY: List<Int> = listOf(60, 62, 64, 65, 67, 69, 71)

    /** C4(60) .. B4(71), all 12 semitones. */
    val NOTE_MEDIUM: List<Int> = (60..71).toList()

    /** C4(60) .. B5(83), all 24 semitones (octave matters). */
    val NOTE_HARD: List<Int> = (60..83).toList()

    fun notePool(difficulty: Difficulty): List<Int> = when (difficulty) {
        Difficulty.EASY -> NOTE_EASY
        Difficulty.MEDIUM -> NOTE_MEDIUM
        Difficulty.HARD -> NOTE_HARD
    }

    // ---- 五线谱识谱: independent visual-reading pools -------------------

    /** C4..B4 natural notes; the first level stays entirely on treble staff. */
    val STAFF_EASY: List<Int> = NOTE_EASY

    /** C3..B5 chromatic, using bass below C4 and treble from C4 upward. */
    val STAFF_MEDIUM: List<Int> = (48..83).toList()

    /** C2..C6 chromatic, including the outer ledger-line cases. */
    val STAFF_HARD: List<Int> = (36..84).toList()

    fun staffPool(difficulty: Difficulty): List<Int> = when (difficulty) {
        Difficulty.EASY -> STAFF_EASY
        Difficulty.MEDIUM -> STAFF_MEDIUM
        Difficulty.HARD -> STAFF_HARD
    }

    // ---- 音程: semitone pools --------------------------------------------

    fun intervalSemitones(difficulty: Difficulty): Set<Int> = when (difficulty) {
        Difficulty.EASY -> IntervalLibrary.BASIC_SEMITONES
        Difficulty.MEDIUM, Difficulty.HARD ->
            IntervalLibrary.ALL.map { it.semitones }.toSet()
    }

    // ---- 和弦: chord pools -----------------------------------------------

    /** 三和弦 + 属七/小七 — the common-beginner sixth. */
    val CHORD_MEDIUM_NAMES: Set<String> = setOf(
        "大三和弦", "小三和弦", "减三和弦", "增三和弦", "属七和弦", "小七和弦",
    )

    fun chords(difficulty: Difficulty): List<ChordLibrary.Chord> = when (difficulty) {
        Difficulty.EASY -> ChordLibrary.TRIADS
        Difficulty.MEDIUM -> ChordLibrary.ALL.filter { it.name in CHORD_MEDIUM_NAMES }
        Difficulty.HARD -> ChordLibrary.ALL
    }

    // ---- 音阶: scale pools -----------------------------------------------

    fun scales(difficulty: Difficulty): List<ScaleLibrary.Scale> = when (difficulty) {
        Difficulty.EASY -> ScaleLibrary.MAJOR_MINOR
        Difficulty.MEDIUM, Difficulty.HARD -> ScaleLibrary.ALL
    }

    // ---- 节奏: rhythm-pattern pools --------------------------------------

    fun rhythmPatterns(difficulty: Difficulty): List<RhythmPattern> = when (difficulty) {
        Difficulty.EASY -> RhythmLibrary.EASY
        Difficulty.MEDIUM -> RhythmLibrary.MEDIUM
        Difficulty.HARD -> RhythmLibrary.HARD
    }
}
