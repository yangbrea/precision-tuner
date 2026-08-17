package com.precisiontuner

/** Which tool is currently active. */
enum class TunerMode { INSTRUMENT, CHROMATIC, METRONOME }

/** How the current signal relates to the active tuning target. */
enum class DetectionPhase { WAITING, TRACKING, OUT_OF_RANGE }

/**
 * Immutable UI state of the tuner.
 *
 * When [detectedFrequency] is null the app shows "waiting for input".
 */
data class TunerState(
    val hasPermission: Boolean = false,
    val isListening: Boolean = false,
    val mode: TunerMode = TunerMode.INSTRUMENT,

    // Primary readout. In manual mode this is the selected target; otherwise it
    // is the detected/nearest tuning note.
    val detectedFrequency: Double? = null,
    val noteName: String? = null,
    val octave: Int? = null,
    val midi: Int? = null,
    val cents: Double? = null,
    val confidence: Double = 0.0,
    val detectionPhase: DetectionPhase = DetectionPhase.WAITING,

    // Actual broad-band observation. In manual mode the main note fields stay
    // fixed on the selected target while these fields describe a wrong string.
    val observedNoteName: String? = null,
    val observedOctave: Int? = null,

    // Instrument mode: manually selected string (null = auto-detect), and the
    // currently active string number (1..N, null when unknown).
    val selectedString: Int? = null,
    val activeString: Int? = null,

    // Concert pitch reference.
    val referenceA4: Double = 440.0,

    // Normalized magnitude spectrum (display bands), for visualization.
    val spectrum: List<Float> = emptyList(),

    // Downsampled time-domain waveform (normalized -1..1), for visualization.
    val waveform: List<Float> = emptyList(),

    // Reference tone playback state (ear tuning).
    val isReferenceTonePlaying: Boolean = false,

    // Increments on each "not in tune -> in tune" rising edge; drives the
    // one-shot "locked" flash animation.
    val inTuneFlash: Int = 0,
) {
    /** True when the detected pitch is within a small window of the target. */
    val isInTune: Boolean
        get() = detectionPhase == DetectionPhase.TRACKING &&
            cents != null && kotlin.math.abs(cents) <= IN_TUNE_CENTS

    companion object {
        const val IN_TUNE_CENTS = 5.0
    }
}
