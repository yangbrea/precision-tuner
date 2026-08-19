package com.precisiontuner.pitch

import kotlin.math.abs

/**
 * Rolling average of accepted pitch frequencies.
 *
 * The queue is keyed by the current note ([key]) so a note change never blends
 * frequencies from the previous note into the new one (no cross-note smearing),
 * and a missing signal ([frequency] == null or non-finite) clears the queue so a
 * fresh note starts without lag from an old one.
 */
class RollingPitchSmoother(windowSize: Int) {

    private var window = windowSize.coerceAtLeast(1)
    private val queue = ArrayDeque<Pair<String, Double>>()

    /** Returns the mean of the queued frequencies for [key], or null when cleared. */
    fun push(key: String?, frequency: Double?): Double? {
        if (key == null || frequency == null || !frequency.isFinite() ||
            abs(frequency) < 1e-6
        ) {
            queue.clear()
            return null
        }
        queue.removeAll { it.first != key }
        queue.addLast(key to frequency)
        while (queue.size > window) queue.removeFirst()
        return queue.sumOf { it.second } / queue.size
    }

    fun setWindowSize(size: Int) {
        window = size.coerceAtLeast(1)
        while (queue.size > window) queue.removeFirst()
    }

    fun reset() {
        queue.clear()
    }
}
