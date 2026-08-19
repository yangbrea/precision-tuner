package com.precisiontuner.ear

import kotlin.math.abs
import kotlin.math.roundToInt

/** Outcome of scoring one rhythm reproduction. */
data class RhythmScore(
    /** 0..100 accuracy percentage. */
    val score: Int,
    /** How many taps the user produced. */
    val tapped: Int,
    /** How many taps the pattern expects. */
    val expected: Int,
    /** True when [score] reaches [RhythmScorer.CORRECT_THRESHOLD]. */
    val correct: Boolean,
)

/**
 * Scores the user's tap times against a rhythm pattern using the *relative*
 * inter-onset intervals, which makes the result robust to tempo drift and to
 * a leading silence before the first tap (the UI already records times
 * relative to the first tap).
 *
 * The pattern's audible onsets are compared to the taps pairwise: each pair of
 * adjacent intervals is normalized by the total, and the mean absolute
 * difference between normalized user and target intervals drives the score.
 * A wrong tap count is a format error (0).
 */
object RhythmScorer {

    /** Weight applied to the mean normalized interval error. */
    const val ERROR_WEIGHT = 2.5

    /** Accuracy at or above which a reproduction counts as correct. */
    const val CORRECT_THRESHOLD = 85

    /** Minimum tap count for a meaningful interval comparison (≥2 intervals). */
    private const val MIN_TAPS = 3

    fun score(pattern: RhythmPattern, tapTimesMs: List<Long>): RhythmScore {
        val times = tapTimesMs.map { it.toDouble() }.sorted()
        val expected = pattern.expectedTaps
        if (times.size != expected || expected < MIN_TAPS) {
            return RhythmScore(0, times.size, expected, false)
        }

        val userIntervals = times.zipWithNext { a, b -> b - a }
        val targetOnsets = pattern.tapOnsetGrids
        val targetIntervals = targetOnsets.zipWithNext { a, b -> (b - a).toDouble() }

        val userTotal = userIntervals.sum()
        val targetTotal = targetIntervals.sum()
        if (userTotal <= 0.0 || targetTotal <= 0.0) {
            return RhythmScore(0, times.size, expected, false)
        }

        val meanError = userIntervals.zip(targetIntervals) { u, t ->
            abs(u / userTotal - t / targetTotal)
        }.average()

        val score = (100.0 * (1.0 - meanError * ERROR_WEIGHT))
            .roundToInt()
            .coerceIn(0, 100)
        return RhythmScore(score, times.size, expected, score >= CORRECT_THRESHOLD)
    }
}
