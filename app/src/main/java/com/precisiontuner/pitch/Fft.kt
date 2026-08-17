package com.precisiontuner.pitch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * Radix-2 iterative Cooley-Tukey FFT, in-place on a complex signal represented
 * as separate real/imaginary DoubleArrays of equal power-of-two length.
 */
object Fft {

    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(im.size == n) { "real/imag arrays must match" }
        require(n > 1 && (n and (n - 1)) == 0) { "size must be a power of two, got $n" }

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        // Butterfly stages.
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang)
            val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                val half = len shr 1
                for (k in 0 until half) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + half] * curRe - im[i + k + half] * curIm
                    val vIm = re[i + k + half] * curIm + im[i + k + half] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + half] = uRe - vRe
                    im[i + k + half] = uIm - vIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** Hann window of length [size], used to reduce spectral leakage. */
fun hann(size: Int): DoubleArray = DoubleArray(size) { i ->
    0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))
}

/**
 * Parabolic interpolation of a magnitude-spectrum peak using its log-magnitude
 * neighbors, returning a sub-bin position (bin + fractional offset).
 */
fun interpolatePeakLog(mag: DoubleArray, bin: Int): Double {
    if (bin <= 0 || bin >= mag.size - 1) return bin.toDouble()
    val y0 = ln(mag[bin - 1].coerceAtLeast(1e-12))
    val y1 = ln(mag[bin].coerceAtLeast(1e-12))
    val y2 = ln(mag[bin + 1].coerceAtLeast(1e-12))
    val denom = y0 - 2.0 * y1 + y2
    if (abs(denom) < 1e-12) return bin.toDouble()
    return bin + 0.5 * (y0 - y2) / denom
}
