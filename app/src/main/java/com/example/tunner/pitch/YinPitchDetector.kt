package com.example.tunner.pitch

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * YIN pitch detector (de Cheveigné & Kawahara, 2002).
 *
 * Robust, well-documented monophonic fundamental-frequency estimator. Steps:
 *  1. Difference function d(τ) = Σ (x[j] − x[j+τ])².
 *  2. Cumulative-mean-normalized difference function d'(τ).
 *  3. Absolute threshold: first τ with d'(τ) < threshold (avoids the lag-0 dip).
 *  4. Parabolic interpolation around that τ for sub-sample precision.
 *
 * A "confidence" value (1 − d'(τ)) is returned so callers can gate the display.
 *
 * @param threshold  YIN absolute threshold, typically 0.1–0.2 (lower = stricter).
 * @param maxTau     maximum lag to search, in samples. Determines the lowest
 *                   detectable frequency: Fs / maxTau. Default 2048 @ 44.1 kHz
 *                   covers down to ~21.5 Hz.
 */
class YinPitchDetector(
    private val threshold: Double = 0.1,
    private val maxTau: Int = 2048,
) : PitchDetector {

    override fun detect(buffer: ShortArray, sampleRate: Int): Pitch? {
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

        val tauMax = minOf(maxTau, n / 2)
        if (tauMax < 2) return null

        // 1. Difference function.
        val d = DoubleArray(tauMax)
        for (tau in 1 until tauMax) {
            var sum = 0.0
            val limit = n - tau
            var j = 0
            while (j < limit) {
                val diff = x[j] - x[j + tau]
                sum += diff * diff
                j++
            }
            d[tau] = sum
        }

        // 2. Cumulative mean normalized difference.
        val cmndf = DoubleArray(tauMax)
        cmndf[0] = 1.0
        var running = 0.0
        for (tau in 1 until tauMax) {
            running += d[tau]
            cmndf[tau] = if (running > 0.0) d[tau] * tau / running else 1.0
        }

        // 3. Absolute threshold: first LOCAL minimum of cmndf below the threshold.
        //    (A bare crossing would land on the descending slope just before the
        //    period and bias the estimate sharp, so require a local minimum.)
        var tauEstimate = -1
        for (tau in 1 until tauMax - 1) {
            if (cmndf[tau] < threshold &&
                cmndf[tau] <= cmndf[tau - 1] &&
                cmndf[tau] <= cmndf[tau + 1]
            ) {
                tauEstimate = tau
                break
            }
        }

        // Fallback: global minimum of cmndf, accepted only if periodic enough.
        if (tauEstimate == -1) {
            var bestValue = Double.MAX_VALUE
            var bestTau = -1
            for (tau in 1 until tauMax - 1) {
                if (cmndf[tau] < bestValue) {
                    bestValue = cmndf[tau]
                    bestTau = tau
                }
            }
            if (bestTau == -1 || bestValue > MAX_FALLBACK_CMNDF) return null
            tauEstimate = bestTau
        }

        // 4. Parabolic interpolation for sub-sample accuracy.
        val refinedTau = parabolicInterpolation(cmndf, tauEstimate)
        if (refinedTau <= 0.0) return null

        val frequency = sampleRate / refinedTau
        if (frequency < MIN_FREQUENCY || frequency > sampleRate / 2.0) return null

        val confidence = (1.0 - cmndf[tauEstimate]).coerceIn(0.0, 1.0)
        return Pitch(frequency, confidence)
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

    private companion object {
        const val SILENCE_RMS = 1e-4          // ~ -80 dBFS gate
        const val MAX_FALLBACK_CMNDF = 0.5    // reject non-periodic buffers
        const val MIN_FREQUENCY = 20.0        // Hz
    }
}
