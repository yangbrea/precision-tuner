package com.precisiontuner.metronome

/** Immutable state of the metronome. */
data class MetronomeState(
    val bpm: Int = 120,
    val beatsPerBar: Int = 4,
    val subdivision: Int = 1, // 1 = none, 2 = eighths, 3 = triplets, 4 = sixteenths
    val noteValue: Int = 4,   // 4 (quarter) or 8 (eighth) — time-signature denominator
    val isPlaying: Boolean = false,
    val currentBeat: Int = 0, // 0 = not started
    val accentEnabled: Boolean = true,
    val volume: Float = 0.7f,
)
