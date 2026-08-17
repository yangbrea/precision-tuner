package com.example.tunner.pitch

import kotlin.math.max
import kotlin.math.sqrt

/** Adaptive input gate. Only rejected/unvoiced frames are allowed to train the floor. */
class NoiseFloorEstimator(
    initialFloor: Double = 0.0005,
    private val absoluteFloor: Double = 0.00015,
) {
    enum class UpdateType { RESET, VOICED, GATE_RECOVERY, UNVOICED_FROZEN, AMBIENT }

    var noiseFloor: Double = initialFloor
        private set
    var lastVoicedRms: Double? = null
        private set
    var lastUpdateType: UpdateType = UpdateType.RESET
        private set

    private val unvoicedRms = ArrayDeque<Double>()
    private var upwardFreezeFrames = 0

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

    /** A frame rejected before pitch analysis may be a decaying note; never learn it upward. */
    fun observeGateRejected(rms: Double) {
        if (!rms.isFinite() || rms < 0.0) return
        tickFreeze()
        // Move below the rejected signal so the gate can eventually reopen;
        // converging directly to rms would permanently fail an SNR > 1 gate.
        val recoveryTarget = max(absoluteFloor, rms / RECOVERY_RATIO)
        if (recoveryTarget < noiseFloor) noiseFloor += 0.12 * (recoveryTarget - noiseFloor)
        noiseFloor = noiseFloor.coerceAtLeast(absoluteFloor)
        lastUpdateType = UpdateType.GATE_RECOVERY
    }

    /** A fully analyzed but non-periodic frame is eligible to teach ambient noise. */
    fun observeUnvoiced(rms: Double) {
        if (!rms.isFinite() || rms < 0.0) return
        tickFreeze()
        unvoicedRms.addLast(rms)
        while (unvoicedRms.size > AMBIENT_WINDOW_FRAMES) unvoicedRms.removeFirst()
        if (upwardFreezeFrames > 0) {
            lastUpdateType = UpdateType.UNVOICED_FROZEN
            return
        }
        val sorted = unvoicedRms.sorted()
        val percentile = sorted[((sorted.size - 1) * AMBIENT_PERCENTILE).toInt()]
        val target = percentile.coerceAtMost(noiseFloor * MAX_UPWARD_RATIO).coerceAtLeast(absoluteFloor)
        val alpha = if (target < noiseFloor) 0.12 else 0.03
        noiseFloor += alpha * (target - noiseFloor)
        noiseFloor = noiseFloor.coerceAtLeast(absoluteFloor)
        lastUpdateType = UpdateType.AMBIENT
    }

    fun observeVoiced(rms: Double) {
        if (!rms.isFinite() || rms < 0.0) return
        lastVoicedRms = rms
        upwardFreezeFrames = VOICED_FREEZE_FRAMES
        unvoicedRms.clear()
        lastUpdateType = UpdateType.VOICED
    }

    fun reset() {
        noiseFloor = 0.0005
        lastVoicedRms = null
        upwardFreezeFrames = 0
        unvoicedRms.clear()
        lastUpdateType = UpdateType.RESET
    }

    private fun tickFreeze() {
        if (upwardFreezeFrames > 0) upwardFreezeFrames--
    }

    private companion object {
        const val RECOVERY_RATIO = 3.25
        const val AMBIENT_WINDOW_FRAMES = 24
        const val AMBIENT_PERCENTILE = 0.20
        const val MAX_UPWARD_RATIO = 1.5
        const val VOICED_FREEZE_FRAMES = 22
    }
}
