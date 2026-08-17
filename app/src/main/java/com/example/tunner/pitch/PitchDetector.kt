package com.example.tunner.pitch

/**
 * A detected pitch estimate.
 *
 * @param frequency  fundamental frequency in Hz.
 * @param confidence 0..1, higher means the buffer is more confidently periodic.
 */
data class Pitch(
    val frequency: Double,
    val confidence: Double,
)

/** One frame-level F0 hypothesis used by the probabilistic tracker. */
data class PitchCandidate(
    val frequency: Double,
    val periodicity: Double,
    val spectralQuality: Double,
    val probability: Double,
    val voicedProbability: Double,
    /** True for YIN's earliest local minimum that clears the absolute threshold. */
    val primaryPeriod: Boolean = false,
)

/**
 * Detects the fundamental frequency of a monophonic audio buffer.
 */
interface PitchDetector {

    /**
     * Analyze a PCM buffer and return the detected pitch, or null when no
     * confident pitch is present (silence / noise / non-periodic input).
     *
     * @param buffer    16-bit PCM samples in the range [-32768, 32767].
     * @param sampleRate sample rate in Hz.
     */
    fun detect(buffer: ShortArray, sampleRate: Int): Pitch?
}
