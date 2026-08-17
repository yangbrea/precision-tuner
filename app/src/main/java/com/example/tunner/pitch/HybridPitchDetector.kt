package com.example.tunner.pitch

import kotlin.math.max
import kotlin.math.min
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

        val (candidates, spectrum) = coarseCandidates(buffer, sampleRate, n)
        lastSpectrum = spectrum

        // Evaluate the dominant FFT peak and its possible 1/2 and 1/3
        // fundamentals independently. YIN periodicity is the primary score;
        // spectral prominence is only supporting evidence. Among effectively
        // tied periodic candidates, prefer the shortest real period (highest
        // frequency), preventing a second YIN minimum from causing a low-octave
        // result.
        val evaluated = candidates.mapNotNull { candidate ->
            yin.detectGuided(buffer, sampleRate, candidate.frequency, CANDIDATE_SEARCH_FRACTION)
                ?.let { pitch ->
                    val confidence = (pitch.confidence * 0.88 + candidate.spectralQuality * 0.12)
                        .coerceIn(0.0, 1.0)
                    Pitch(pitch.frequency, confidence)
                }
        }
        val bestConfidence = evaluated.maxOfOrNull { it.confidence }
        if (bestConfidence != null && bestConfidence >= GUIDED_CONFIDENCE_FLOOR) {
            return evaluated
                .filter { it.confidence >= bestConfidence - CANDIDATE_TIE_MARGIN }
                .maxByOrNull { it.frequency }
        }
        return yin.detect(buffer, sampleRate)
    }

    /** String-locked detection: refine YIN directly around [targetFrequency]. */
    fun detectLocked(buffer: ShortArray, sampleRate: Int, targetFrequency: Double): Pitch? =
        yin.detectLocked(buffer, sampleRate, targetFrequency)

    private data class CoarseCandidate(val frequency: Double, val spectralQuality: Double)

    private fun coarseCandidates(
        buffer: ShortArray,
        sampleRate: Int,
        n: Int,
    ): Pair<List<CoarseCandidate>, FloatArray> {
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
        if (binMin > binMax) return emptyList<CoarseCandidate>() to spectrum

        var peakBin = binMin
        var peakVal = mag[binMin]
        for (b in binMin..binMax) {
            if (mag[b] > peakVal) {
                peakVal = mag[b]
                peakBin = b
            }
        }
        if (peakVal < PEAK_FLOOR) return emptyList<CoarseCandidate>() to spectrum

        val peakFreq = interpolatePeakLog(mag, peakBin) * binHz
        val background = mag.copyOfRange(binMin, binMax + 1).sorted()[((binMax - binMin) / 2)]
        val peakQuality = ((peakVal / (background + 1e-9) - 1.0) / 12.0).coerceIn(0.0, 1.0)
        val candidates = (1..3).mapNotNull { divisor ->
            val frequency = peakFreq / divisor
            if (frequency < MIN_COARSE_HZ) null else {
                val bin = (frequency / binHz).toInt().coerceIn(1, half - 2)
                val local = max(mag[bin - 1], max(mag[bin], mag[bin + 1]))
                val localQuality = (local / (peakVal + 1e-9)).coerceIn(0.0, 1.0)
                CoarseCandidate(frequency, (peakQuality * 0.7 + localQuality * 0.3).coerceIn(0.0, 1.0))
            }
        }
        return candidates to spectrum
    }

    private companion object {
        const val MIN_COARSE_HZ = 30.0
        const val MAX_COARSE_HZ = 2500.0
        const val PEAK_FLOOR = 1.0 // reject silence (a full-scale tone peaks ~500)
        const val GUIDED_CONFIDENCE_FLOOR = 0.5 // below this, use full-range YIN
        const val CANDIDATE_SEARCH_FRACTION = 0.12
        const val CANDIDATE_TIE_MARGIN = 0.08
    }
}
