package com.example.tunner

/** Which tool is currently active. */
enum class TunerMode { INSTRUMENT, CHROMATIC, METRONOME }

/**
 * Immutable UI state of the tuner.
 *
 * When [detectedFrequency] is null the app shows "waiting for input".
 */
data class TunerState(
    val hasPermission: Boolean = false,
    val isListening: Boolean = false,
    val mode: TunerMode = TunerMode.INSTRUMENT,

    // Detected pitch (nearest 12-TET note).
    val detectedFrequency: Double? = null,
    val noteName: String? = null,
    val octave: Int? = null,
    val midi: Int? = null,
    val cents: Double? = null,
    val confidence: Double = 0.0,

    // Instrument mode: manually selected string (null = auto-detect), and the
    // currently active string number (1..N, null when unknown).
    val selectedString: Int? = null,
    val activeString: Int? = null,

    // Concert pitch reference.
    val referenceA4: Double = 440.0,

    // Normalized magnitude spectrum (display bands), for visualization.
    val spectrum: List<Float> = emptyList(),
) {
    /** True when the detected pitch is within a small window of the target. */
    val isInTune: Boolean
        get() = cents != null && kotlin.math.abs(cents) <= IN_TUNE_CENTS

    companion object {
        const val IN_TUNE_CENTS = 5.0
    }
}
