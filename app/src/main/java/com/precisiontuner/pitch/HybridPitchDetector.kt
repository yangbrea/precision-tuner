package com.precisiontuner.pitch

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
    var lastSubharmonicSuppression: String? = null
        private set

    override fun detect(buffer: ShortArray, sampleRate: Int): Pitch? {
        val n = min(fftSize, buffer.size)
        if (n < 64) return null
        val (coarse, spectrum) = coarseCandidates(buffer, sampleRate, n)
        lastSpectrum = spectrum
        val evaluated = coarse.mapNotNull { candidate ->
            yin.detectGuided(buffer, sampleRate, candidate.frequency, CANDIDATE_SEARCH_FRACTION)
                ?.let { pitch ->
                    Pitch(
                        pitch.frequency,
                        (pitch.confidence * 0.88 + candidate.spectralQuality * 0.12).coerceIn(0.0, 1.0),
                    )
                }
        }
        val bestConfidence = evaluated.maxOfOrNull { it.confidence }
        if (bestConfidence != null && bestConfidence >= GUIDED_CONFIDENCE_FLOOR) {
            return evaluated.filter { it.confidence >= bestConfidence - SINGLE_FRAME_TIE_MARGIN }
                .maxByOrNull { it.frequency }
        }
        return yin.detect(buffer, sampleRate)
    }

    fun detectCandidates(
        buffer: ShortArray,
        sampleRate: Int,
        maxCandidates: Int = 5,
    ): List<PitchCandidate> {
        val n = min(fftSize, buffer.size)
        if (n < 64) {
            lastSpectrum = null
            return emptyList()
        }

        val (coarse, spectrum) = coarseCandidates(buffer, sampleRate, n)
        lastSpectrum = spectrum
        val guidedCandidates = coarse.mapNotNull { coarseCandidate ->
            yin.detectGuided(buffer, sampleRate, coarseCandidate.frequency, CANDIDATE_SEARCH_FRACTION)
                ?.let { pitch ->
                    PitchCandidate(
                        frequency = pitch.frequency,
                        periodicity = pitch.confidence,
                        spectralQuality = coarseCandidate.spectralQuality,
                        probability = pitch.confidence * pitch.confidence * pitch.confidence,
                        voicedProbability = pitch.confidence,
                    )
                }
        }
        val scored = (guidedCandidates + yin.detectCandidates(buffer, sampleRate, maxCandidates = maxCandidates * 2))
            .map { candidate ->
                val spectralQuality = coarse.maxOfOrNull { coarseCandidate ->
                    val distance = centsDistance(candidate.frequency, coarseCandidate.frequency)
                    coarseCandidate.spectralQuality * kotlin.math.exp(-distance / SPECTRAL_MATCH_CENTS)
                } ?: 0.0
                candidate.copy(
                    spectralQuality = spectralQuality,
                    probability = (candidate.probability * 0.82 +
                        candidate.periodicity * spectralQuality * 0.18).coerceIn(0.0, 1.0),
                    voicedProbability = (candidate.periodicity * 0.9 + spectralQuality * 0.1)
                        .coerceIn(0.0, 1.0),
                )
            }
        val merged = scored
            .sortedByDescending { it.probability }
            .fold(mutableListOf<PitchCandidate>()) { kept, candidate ->
                val duplicate = kept.indexOfFirst { centsDistance(it.frequency, candidate.frequency) < 25.0 }
                if (duplicate < 0) {
                    kept += candidate
                } else if (candidate.primaryPeriod && !kept[duplicate].primaryPeriod) {
                    kept[duplicate] = kept[duplicate].copy(primaryPeriod = true)
                }
                kept
            }
        return suppressFalseSubharmonics(merged)
            .sortedByDescending { it.probability }
            .take(maxCandidates)
    }

    /**
     * YIN also has minima at integer multiples of the real period. Prefer its
     * earliest threshold-clearing (shortest credible) period unless a longer
     * period is materially more periodic, which preserves genuine low notes.
     */
    private fun suppressFalseSubharmonics(candidates: List<PitchCandidate>): List<PitchCandidate> {
        val suppressed = mutableListOf<String>()
        val result = candidates.filter { low ->
            val suppressor = candidates.firstOrNull { high ->
                high.frequency > low.frequency &&
                    high.primaryPeriod &&
                    integerRatio(high.frequency / low.frequency) != null &&
                    high.periodicity >= low.periodicity - SUBHARMONIC_PERIODICITY_MARGIN
            }
            if (suppressor != null) {
                suppressed += "${"%.1f".format(low.frequency)}->${"%.1f".format(suppressor.frequency)}" +
                    "(${integerRatio(suppressor.frequency / low.frequency)}x," +
                    "p=${"%.2f".format(low.periodicity)}/${"%.2f".format(suppressor.periodicity)})"
                false
            } else {
                true
            }
        }
        lastSubharmonicSuppression = suppressed.takeIf { it.isNotEmpty() }?.joinToString(",")
        return result
    }

    private fun integerRatio(ratio: Double): Int? =
        (2..3).firstOrNull { harmonic ->
            kotlin.math.abs(1200.0 * kotlin.math.ln(ratio / harmonic) / kotlin.math.ln(2.0)) <=
                INTEGER_RATIO_TOLERANCE_CENTS
        }

    /** String-locked detection: refine YIN directly around [targetFrequency]. */
    fun detectLocked(buffer: ShortArray, sampleRate: Int, targetFrequency: Double): Pitch? =
        yin.detectLocked(buffer, sampleRate, targetFrequency)

    private fun centsDistance(a: Double, b: Double): Double =
        kotlin.math.abs(1200.0 * kotlin.math.ln(a / b) / kotlin.math.ln(2.0))

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
        const val SPECTRAL_MATCH_CENTS = 240.0
        const val SINGLE_FRAME_TIE_MARGIN = 0.08
        const val CANDIDATE_SEARCH_FRACTION = 0.12
        const val SUBHARMONIC_PERIODICITY_MARGIN = 0.07
        const val INTEGER_RATIO_TOLERANCE_CENTS = 45.0
    }
}
