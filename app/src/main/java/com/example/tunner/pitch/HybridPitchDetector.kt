package com.example.tunner.pitch

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Hybrid pitch detector: FFT for coarse localization, YIN for fine refinement.
 *
 * Pipeline:
 *  1. Hann-windowed FFT → magnitude spectrum → dominant peak → parabolic
 *     interpolation (on log magnitude) → sub-bin coarse frequency, with a
 *     sub-harmonic check to disambiguate octaves.
 *  2. [YinPitchDetector.detectGuided] searches lags only around `Fs / coarse`,
 *     giving sub-sample period accuracy (fine frequency).
 *  3. Falls back to full-range YIN if the guided search is inconclusive.
 *
 * Also exposes [lastSpectrum] — the normalized display-band spectrum used for
 * visualization.
 */
class HybridPitchDetector(
    private val fftSize: Int = 4096,
    private val yin: YinPitchDetector = YinPitchDetector(),
) : PitchDetector {

    var lastSpectrum: FloatArray? = null
        private set

    override fun detect(buffer: ShortArray, sampleRate: Int): Pitch? {
        val n = min(fftSize, buffer.size)
        if (n < 64) {
            lastSpectrum = null
            return null
        }

        val (coarse, spectrum) = coarseFundamental(buffer, sampleRate, n)
        lastSpectrum = spectrum

        // Refine with YIN, guided by the coarse estimate. If the guided search
        // is missing or only marginal, fall back to the full-range YIN (which
        // has its own silence/noise gates) rather than giving up or returning a
        // weak result.
        val guided = coarse?.let { yin.detectGuided(buffer, sampleRate, it) }
        if (guided != null && guided.confidence >= GUIDED_CONFIDENCE_FLOOR) {
            return guided
        }
        return yin.detect(buffer, sampleRate)
    }

    /** String-locked detection: refine YIN directly around [targetFrequency]. */
    fun detectLocked(buffer: ShortArray, sampleRate: Int, targetFrequency: Double): Pitch? =
        yin.detectLocked(buffer, sampleRate, targetFrequency)

    private fun coarseFundamental(
        buffer: ShortArray,
        sampleRate: Int,
        n: Int,
    ): Pair<Double?, FloatArray> {
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val win = hann(n)
        for (i in 0 until n) {
            re[i] = buffer[i] / 32768.0 * win[i]
        }
        Fft.transform(re, im)

        val half = n / 2
        val mag = DoubleArray(half) { i -> sqrt(re[i] * re[i] + im[i] * im[i]) }
        val spectrum = Spectrum.build(mag, sampleRate, n)

        val binHz = sampleRate.toDouble() / n
        val binMin = max(1, (MIN_COARSE_HZ / binHz).toInt())
        val binMax = min(half - 2, (MAX_COARSE_HZ / binHz).toInt())
        if (binMin > binMax) return null to spectrum

        var peakBin = binMin
        var peakVal = mag[binMin]
        for (b in binMin..binMax) {
            if (mag[b] > peakVal) {
                peakVal = mag[b]
                peakBin = b
            }
        }
        if (peakVal < PEAK_FLOOR) return null to spectrum

        val peakFreq = interpolatePeakLog(mag, peakBin) * binHz

        // Octave disambiguation: descend to the lowest sub-harmonic (of the
        // peak) that still has significant energy, to land on the fundamental.
        var divisor = 1
        for (d in 2..3) {
            val candidate = peakFreq / d
            if (candidate < MIN_COARSE_HZ) continue
            val cBin = (candidate / binHz).roundToInt()
            if (cBin in 1 until half - 1) {
                val neighbor = max(mag[cBin - 1], max(mag[cBin], mag[cBin + 1]))
                if (neighbor > SUBHARMONIC_RATIO * peakVal) divisor = d
            }
        }

        return (peakFreq / divisor) to spectrum
    }

    private companion object {
        const val MIN_COARSE_HZ = 30.0
        const val MAX_COARSE_HZ = 2500.0
        const val SUBHARMONIC_RATIO = 0.4
        const val PEAK_FLOOR = 1.0 // reject silence (a full-scale tone peaks ~500)
        const val GUIDED_CONFIDENCE_FLOOR = 0.5 // below this, use full-range YIN
    }
}
