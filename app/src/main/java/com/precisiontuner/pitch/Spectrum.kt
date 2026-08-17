package com.precisiontuner.pitch

import kotlin.math.min

/**
 * Spectrum visualization constants and helpers.
 *
 * The detector produces a fixed number of display bands spanning [0, MAX_HZ],
 * normalized so the tallest band is 1.0.
 */
object Spectrum {
    const val BANDS = 128
    const val MAX_HZ = 1500.0

    /**
     * Collapse a half-FFT magnitude spectrum ([magnitude], one entry per bin)
     * into [BANDS] display bands using power (magnitude²) max-pooling, then
     * normalize to [0, 1].
     *
     * Squaring emphasizes the dominant peaks and visually de-emphasizes weaker
     * partials (a harmonic at half the magnitude shows at 1/4 the height).
     *
     * @param fftSize   the full FFT length (2 × [magnitude].size).
     */
    fun build(magnitude: DoubleArray, sampleRate: Int, fftSize: Int): FloatArray {
        val bands = FloatArray(BANDS)
        val binHz = sampleRate.toDouble() / fftSize
        val maxBin = min(magnitude.size, (MAX_HZ / binHz).toInt())
        for (b in 1 until maxBin) {
            val freq = b * binHz
            val idx = (freq / MAX_HZ * BANDS).toInt().coerceIn(0, BANDS - 1)
            val power = (magnitude[b] * magnitude[b]).toFloat()
            if (power > bands[idx]) bands[idx] = power
        }
        val peak = bands.maxOrNull() ?: 0f
        if (peak > 0f) {
            for (i in bands.indices) bands[i] /= peak
        }
        return bands
    }
}
