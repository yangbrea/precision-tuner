package com.example.tunner.metronome

/** Immutable state of the metronome. */
data class MetronomeState(
    val bpm: Int = 120,
    val beatsPerBar: Int = 4,
    val isPlaying: Boolean = false,
    val currentBeat: Int = 0, // 0 = not started
    val accentEnabled: Boolean = true,
    val volume: Float = 0.7f,
)
