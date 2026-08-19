package com.precisiontuner.ear

/**
 * Interval definitions keyed by semitone distance. Names are Chinese (the app's
 * UI language, matching the hardcoded-string convention used across screens).
 */
object IntervalLibrary {

    data class Interval(val name: String, val semitones: Int)

    /** Every interval the exercise can use, one per semitone count 0..12. */
    val ALL: List<Interval> = listOf(
        Interval("纯一度", 0),
        Interval("小二度", 1),
        Interval("大二度", 2),
        Interval("小三度", 3),
        Interval("大三度", 4),
        Interval("纯四度", 5),
        Interval("三全音", 6),
        Interval("纯五度", 7),
        Interval("小六度", 8),
        Interval("大六度", 9),
        Interval("小七度", 10),
        Interval("大七度", 11),
        Interval("纯八度", 12),
    )

    /** 基础音程: a common beginner-friendly subset. */
    val BASIC_SEMITONES: Set<Int> = setOf(2, 4, 5, 7, 9, 11, 12)

    /** Semitone counts that belong to [BASIC_SEMITONES], in ascending order. */
    val BASIC: List<Interval> = ALL.filter { it.semitones in BASIC_SEMITONES }

    /** The interval with the given semitone count. */
    fun bySemitone(semitones: Int): Interval =
        ALL.first { it.semitones == semitones }
}
