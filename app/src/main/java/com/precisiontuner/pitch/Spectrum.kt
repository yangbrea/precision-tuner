package com.precisiontuner.pitch

import kotlin.math.min

/**
 * Spectrum visualization constants and helpers.
 *
 * The detector produces a fixed number of display bands spanning [0, MAX_HZ].
 * Bands are stored as power (magnitude²) max-pooled values and displayed on a
 * **power-ratio scale with top headroom**: every band is
 * `bandPower / globalPeakPower × [DISPLAY_CEILING]`, so the strongest band in
 * the frame shows at 0.94 instead of touching the top and a harmonic at 90% of
 * the peak's amplitude (81% power) shows at ≈0.76 — real level differences stay
 * clearly visible and only genuinely equal peaks render at the same height.
 */
object Spectrum {
    const val BANDS = 128
    const val MAX_HZ = 1500.0

    /** Display ceiling: the strongest band maps to this height (6% headroom). */
    const val DISPLAY_CEILING = 0.94f

    /**
     * Collapse a half-FFT magnitude spectrum ([magnitude], one entry per bin)
     * into [BANDS] display bands using power (magnitude²) max-pooling, then
     * normalize against the **global** strongest band: `height = power / peak ×
     * DISPLAY_CEILING`. The reference is always the frame-wide maximum power —
     * never a detected fundamental — so no artificial peak ordering is applied.
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
            for (i in bands.indices) {
                bands[i] = bands[i] / peak * DISPLAY_CEILING
            }
        }
        return bands
    }
}
