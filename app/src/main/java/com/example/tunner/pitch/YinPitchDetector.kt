package com.example.tunner.pitch

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * YIN pitch detector (de Cheveigné & Kawahara, 2002).
 *
 * Robust, well-documented monophonic fundamental-frequency estimator. Steps:
 *  1. Difference function d(τ) = Σ (x[j] − x[j+τ])².
 *  2. Cumulative-mean-normalized difference function d'(τ).
 *  3. Absolute threshold: first local minimum of d' below the threshold.
 *  4. Parabolic interpolation around that τ for sub-sample precision.
 *
 * [detectGuided] restricts the lag search to a band around a coarse frequency
 * estimate (e.g. from an FFT), which both speeds the search and disambiguates
 * octaves; it is used by [HybridPitchDetector].
 *
 * @param threshold  YIN absolute threshold, typically 0.1–0.2.
 * @param maxTau     maximum lag to search, in samples.
 */
class YinPitchDetector(
    private val threshold: Double = 0.1,
    private val maxTau: Int = 2048,
) : PitchDetector {

    /** Returns several genuine YIN minima for probabilistic cross-frame tracking. */
    fun detectCandidates(
        buffer: ShortArray,
        sampleRate: Int,
        maxCandidates: Int = 8,
    ): List<PitchCandidate> {
        if (buffer.size < 4 || maxCandidates <= 0) return emptyList()
        val x = DoubleArray(buffer.size)
        var sumSq = 0.0
        for (i in buffer.indices) {
            val value = buffer[i] / 32768.0
            x[i] = value
            sumSq += value * value
        }
        if (sqrt(sumSq / buffer.size) < SILENCE_RMS) return emptyList()

        val tauMax = minOf(maxTau, buffer.size / 2)
        if (tauMax < 2) return emptyList()
        val difference = DoubleArray(tauMax + 1)
        for (tau in 1..tauMax) {
            var sum = 0.0
            var index = 0
            val comparisonLength = buffer.size - tau
            while (index < comparisonLength) {
                val delta = x[index] - x[index + tau]
                sum += delta * delta
                index++
            }
            difference[tau] = sum
        }
        val cmndf = DoubleArray(tauMax + 1)
        cmndf[0] = 1.0
        var running = 0.0
        for (tau in 1..tauMax) {
            running += difference[tau]
            cmndf[tau] = if (running > 0.0) difference[tau] * tau / running else 1.0
        }

        val minTau = maxOf(2, (sampleRate / MAX_CANDIDATE_FREQUENCY).toInt())
        val minima = buildList {
            for (tau in minTau until tauMax) {
                if (cmndf[tau] <= MAX_FALLBACK_CMNDF &&
                    cmndf[tau] <= cmndf[tau - 1] && cmndf[tau] <= cmndf[tau + 1]
                ) add(tau)
            }
        }
        val preferredTau = minima.firstOrNull { cmndf[it] < threshold }
        return minima
            .mapNotNull { tau ->
                val refinedTau = parabolicInterpolation(cmndf, tau)
                val frequency = sampleRate / refinedTau
                if (frequency < MIN_FREQUENCY || frequency > MAX_CANDIDATE_FREQUENCY) null else {
                    val periodicity = (1.0 - cmndf[tau]).coerceIn(0.0, 1.0)
                    // A soft version of pYIN's threshold distribution: weak
                    // minima remain available, but strong periodic minima have
                    // substantially more emission probability.
                    val probability = periodicity * periodicity * periodicity
                    tau to PitchCandidate(frequency, periodicity, 0.0, probability, periodicity)
                }
            }
            .sortedWith(
                compareByDescending<Pair<Int, PitchCandidate>> { it.first == preferredTau }
                    .thenByDescending { it.second.probability }
                    .thenByDescending { it.second.frequency }
            )
            .map { it.second }
            .fold(mutableListOf<PitchCandidate>()) { kept, candidate ->
                if (kept.none { centsDistance(it.frequency, candidate.frequency) < CANDIDATE_NMS_CENTS }) {
                    kept += candidate
                }
                kept
            }
            .take(maxCandidates)
    }

    override fun detect(buffer: ShortArray, sampleRate: Int): Pitch? =
        detectInternal(buffer, sampleRate, searchRange = null)

    /** Refine a coarse frequency estimate by searching lags within ±[fraction] of it. */
    fun detectGuided(
        buffer: ShortArray,
        sampleRate: Int,
        coarseFrequency: Double,
        fraction: Double = 0.15,
    ): Pitch? {
        val tauCenter = (sampleRate / coarseFrequency).roundToInt()
        val margin = maxOf(8, (tauCenter * fraction).toInt())
        val lo = maxOf(1, tauCenter - margin)
        val hi = minOf(maxTau - 1, tauCenter + margin)
        if (lo > hi) return null
        return detectInternal(buffer, sampleRate, searchRange = lo..hi)
    }

    /** Tight search around a target pitch (~±100 cents) for string-locked tuning. */
    fun detectLocked(buffer: ShortArray, sampleRate: Int, targetFrequency: Double): Pitch? =
        detectGuided(buffer, sampleRate, targetFrequency, fraction = LOCK_FRACTION)

    private fun detectInternal(buffer: ShortArray, sampleRate: Int, searchRange: IntRange?): Pitch? {
        val n = buffer.size
        if (n < 4) return null

        // Normalize to [-1, 1] and cheaply reject near-silence.
        val x = DoubleArray(n)
        var sumSq = 0.0
        for (i in 0 until n) {
            val v = buffer[i] / 32768.0
            x[i] = v
            sumSq += v * v
        }
        val rms = sqrt(sumSq / n)
        if (rms < SILENCE_RMS) return null

        val fullTauMax = minOf(maxTau, n / 2)
        if (fullTauMax < 2) return null

        // Compute the difference function only as far as needed.
        val computeUpTo = searchRange?.last?.coerceAtMost(fullTauMax) ?: fullTauMax
        if (computeUpTo < 1) return null

        val d = DoubleArray(computeUpTo + 1)
        for (tau in 1..computeUpTo) {
            var sum = 0.0
            val comparisonLength = n - tau
            var j = 0
            while (j < comparisonLength) {
                val diff = x[j] - x[j + tau]
                sum += diff * diff
                j++
            }
            d[tau] = sum
        }

        val cmndf = DoubleArray(computeUpTo + 1)
        cmndf[0] = 1.0
        var running = 0.0
        for (tau in 1..computeUpTo) {
            running += d[tau]
            cmndf[tau] = if (running > 0.0) d[tau] * tau / running else 1.0
        }

        val tauEstimate = if (searchRange != null) {
            guidedSearch(cmndf, searchRange)
        } else {
            fullSearch(cmndf, computeUpTo)
        }
        if (tauEstimate == -1) return null

        val refinedTau = parabolicInterpolation(cmndf, tauEstimate)
        if (refinedTau <= 0.0) return null

        val frequency = sampleRate / refinedTau
        if (frequency < MIN_FREQUENCY || frequency > sampleRate / 2.0) return null

        val confidence = (1.0 - cmndf[tauEstimate]).coerceIn(0.0, 1.0)
        return Pitch(frequency, confidence)
    }

    /** Full-range YIN: first local minimum below threshold, else global minimum. */
    private fun fullSearch(cmndf: DoubleArray, computeUpTo: Int): Int {
        for (tau in 1 until computeUpTo) {
            if (cmndf[tau] < threshold &&
                cmndf[tau] <= cmndf[tau - 1] &&
                cmndf[tau] <= cmndf[tau + 1]
            ) {
                return tau
            }
        }
        var bestValue = Double.MAX_VALUE
        var bestTau = -1
        for (tau in 1 until computeUpTo) {
            if (cmndf[tau] < bestValue) {
                bestValue = cmndf[tau]
                bestTau = tau
            }
        }
        if (bestTau == -1 || bestValue > MAX_FALLBACK_CMNDF) return -1
        return bestTau
    }

    /** Guided search: deepest minimum within the restricted range. */
    private fun guidedSearch(cmndf: DoubleArray, range: IntRange): Int {
        var bestValue = Double.MAX_VALUE
        var bestTau = -1
        for (t in range) {
            if (cmndf[t] < bestValue) {
                bestValue = cmndf[t]
                bestTau = t
            }
        }
        if (bestTau == -1 || bestValue > MAX_FALLBACK_CMNDF) return -1
        return bestTau
    }

    private fun parabolicInterpolation(cmndf: DoubleArray, tau: Int): Double {
        if (tau <= 0 || tau >= cmndf.size - 1) return tau.toDouble()
        val s0 = cmndf[tau - 1]
        val s1 = cmndf[tau]
        val s2 = cmndf[tau + 1]
        val denominator = s0 - 2.0 * s1 + s2
        if (abs(denominator) < 1e-12) return tau.toDouble()
        return tau + 0.5 * (s0 - s2) / denominator
    }

    private fun centsDistance(a: Double, b: Double): Double =
        abs(1200.0 * kotlin.math.ln(a / b) / kotlin.math.ln(2.0))

    private companion object {
        const val SILENCE_RMS = 1e-4          // ~ -80 dBFS gate
        const val MAX_FALLBACK_CMNDF = 0.6    // accept confidence >= 0.4 (reject noise)
        const val MIN_FREQUENCY = 20.0        // Hz
        const val LOCK_FRACTION = 0.06        // ≈ ±100 cents for string lock
        const val MAX_CANDIDATE_FREQUENCY = 2500.0
        const val CANDIDATE_NMS_CENTS = 35.0
    }
}
