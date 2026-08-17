package com.example.tunner.pitch

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/** Log-frequency median smoothing plus confirmation of abrupt note/octave changes. */
class PitchStabilizer(
    private var windowSize: Int = 5,
    private val changeConfirmationFrames: Int = 3,
) {
    private val history = ArrayDeque<Double>()
    private val pending = ArrayDeque<Double>()
    private var outputLog2: Double? = null

    fun configureWindow(size: Int) {
        val normalized = size.coerceAtLeast(1)
        if (normalized != windowSize) {
            windowSize = normalized
            reset()
        }
    }

    fun submit(frequency: Double, transitionConfirmed: Boolean = false): Double? {
        if (!frequency.isFinite() || frequency <= 0.0) return outputFrequency()
        val value = log2(frequency)
        if (transitionConfirmed) {
            history.clear()
            pending.clear()
            history.addLast(value)
            outputLog2 = value
            return frequency
        }
        val current = outputLog2

        if (current == null) {
            addHistory(value)
            if (history.size < minOf(windowSize, changeConfirmationFrames)) return null
            outputLog2 = median(history)
            return outputFrequency()
        }

        val distance = centsBetween(value, current)
        if (distance <= CONTINUOUS_CENTS) {
            pending.clear()
            addHistory(value)
            outputLog2 = median(history)
            return outputFrequency()
        }

        val pendingCenter = pending.takeIf { it.isNotEmpty() }?.let(::median)
        if (pendingCenter == null || centsBetween(value, pendingCenter) <= PENDING_CLUSTER_CENTS) {
            pending.addLast(value)
        } else {
            pending.clear()
            pending.addLast(value)
        }
        if (pending.size < changeConfirmationFrames) return outputFrequency()

        history.clear()
        pending.forEach(::addHistory)
        pending.clear()
        outputLog2 = median(history)
        return outputFrequency()
    }

    fun reset() {
        history.clear()
        pending.clear()
        outputLog2 = null
    }

    private fun addHistory(value: Double) {
        history.addLast(value)
        while (history.size > windowSize) history.removeFirst()
    }

    private fun outputFrequency(): Double? = outputLog2?.let { 2.0.pow(it) }
    private fun median(values: Collection<Double>): Double = values.sorted()[values.size / 2]
    private fun log2(value: Double): Double = ln(value) / ln(2.0)
    private fun centsBetween(a: Double, b: Double): Double = abs(a - b) * 1200.0

    private companion object {
        const val CONTINUOUS_CENTS = 150.0
        const val PENDING_CLUSTER_CENTS = 80.0
    }
}
