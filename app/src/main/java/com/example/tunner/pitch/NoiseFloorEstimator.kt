package com.example.tunner.pitch

import kotlin.math.max
import kotlin.math.sqrt

/** Adaptive input gate. Only rejected/unvoiced frames are allowed to train the floor. */
class NoiseFloorEstimator(
    initialFloor: Double = 0.0005,
    private val absoluteFloor: Double = 0.00015,
) {
    var noiseFloor: Double = initialFloor
        private set

    fun rms(buffer: ShortArray): Double {
        if (buffer.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in buffer) {
            val value = sample / 32768.0
            sum += value * value
        }
        return sqrt(sum / buffer.size)
    }

    fun shouldAnalyze(rms: Double, signalToNoiseRatio: Double): Boolean =
        rms >= max(absoluteFloor, noiseFloor * signalToNoiseRatio)

    fun observeRejected(rms: Double) {
        if (!rms.isFinite() || rms < 0.0) return
        // A clipped observation prevents a single impact from teaching the
        // gate that a loud transient is the new ambient noise floor.
        val observation = rms.coerceAtMost(max(noiseFloor * 1.5, absoluteFloor))
        val alpha = if (observation < noiseFloor) 0.12 else 0.20
        noiseFloor += alpha * (observation - noiseFloor)
        noiseFloor = noiseFloor.coerceAtLeast(absoluteFloor)
    }

    fun reset() {
        noiseFloor = 0.0005
    }
}
