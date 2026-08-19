package com.precisiontuner.ear

/** Chord definitions as semitone offsets from the root (root position). */
object ChordLibrary {

    data class Chord(val name: String, val intervals: List<Int>)

    /** 三和弦: major, minor, diminished, augmented. */
    val TRIADS: List<Chord> = listOf(
        Chord("大三和弦", listOf(0, 4, 7)),
        Chord("小三和弦", listOf(0, 3, 7)),
        Chord("减三和弦", listOf(0, 3, 6)),
        Chord("增三和弦", listOf(0, 4, 8)),
    )

    /** 七和弦: dominant, major, minor, half-diminished. */
    val SEVENTHS: List<Chord> = listOf(
        Chord("属七和弦", listOf(0, 4, 7, 10)),
        Chord("大七和弦", listOf(0, 4, 7, 11)),
        Chord("小七和弦", listOf(0, 3, 7, 10)),
        Chord("半减七和弦", listOf(0, 3, 6, 10)),
    )

    /** Every chord. */
    val ALL: List<Chord> = TRIADS + SEVENTHS
}
