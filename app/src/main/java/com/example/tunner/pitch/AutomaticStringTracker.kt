package com.example.tunner.pitch

import com.example.tunner.tuning.InstrumentString
import com.example.tunner.tuning.Tuning
import kotlin.math.abs
import kotlin.math.ln

data class AutomaticStringState(
    val activeString: InstrumentString?,
    val pendingString: InstrumentString?,
    val confirmationFrames: Int,
    val candidateCents: Double?,
    val reason: String,
)

/**
 * Latches an open string in instrument auto mode. Broad-band pitch candidates
 * may acquire or propose another string, but only a sustained, plausible open
 * string can replace the active target.
 */
class AutomaticStringTracker(
    private val acquireFrames: Int = 3,
    private val sustainedSwitchFrames: Int = 6,
    private val releaseFrames: Int = 22,
) {
    private var activeNumber: Int? = null
    private var pendingNumber: Int? = null
    private var pendingFrames = 0
    private var pendingFromOnset = false
    private var missedFrames = 0

    fun activeString(tuning: Tuning?): InstrumentString? =
        activeNumber?.let { tuning?.byNumber(it) }

    fun submit(
        tuning: Tuning?,
        broadCandidates: List<PitchCandidate>,
        lockedAccepted: Boolean,
        onset: Boolean,
        signalToNoiseRatio: Double,
        minimumVoicedProbability: Double,
    ): AutomaticStringState {
        if (tuning == null || tuning.strings.isEmpty()) {
            reset()
            return state(null, null, "no_tuning")
        }

        val mapped = broadCandidates.mapNotNull { candidate ->
            val string = tuning.nearestString(candidate.frequency) ?: return@mapNotNull null
            val cents = string.centsFrom(candidate.frequency)
            if (abs(cents) > CAPTURE_CENTS) null else Mapped(candidate, string, cents)
        }
        val credible = mapped
            .filter { it.candidate.voicedProbability >= minimumVoicedProbability }
            .maxByOrNull { it.candidate.probability }
        val active = activeString(tuning)

        if (active == null) {
            if (credible == null) {
                clearPending()
                return state(tuning, null, "acquire_waiting")
            }
            confirm(credible.string.number)
            if (pendingFrames >= acquireFrames) {
                activeNumber = credible.string.number
                missedFrames = 0
                clearPending()
                return state(tuning, credible.cents, "acquired")
            }
            return state(tuning, credible.cents, "acquire_pending")
        }

        val currentEvidence = lockedAccepted || mapped.any {
            it.string.number == active.number &&
                it.candidate.voicedProbability >= minimumVoicedProbability
        }
        if (currentEvidence) missedFrames = 0 else missedFrames++

        val alternate = credible?.takeIf { it.string.number != active.number }
        if (alternate == null) {
            clearPending()
        } else {
            val strongSustained = !onset &&
                alternate.candidate.voicedProbability >= SUSTAINED_VOICED_PROBABILITY &&
                signalToNoiseRatio >= SUSTAINED_SNR &&
                abs(alternate.cents) <= SUSTAINED_TARGET_CENTS
            val continuingOnsetSwitch = pendingFromOnset && pendingNumber == alternate.string.number
            if (onset || continuingOnsetSwitch || strongSustained) {
                confirm(alternate.string.number)
                if (onset) pendingFromOnset = true
                val required = if (pendingFromOnset) acquireFrames else sustainedSwitchFrames
                if (pendingFrames >= required) {
                    activeNumber = alternate.string.number
                    missedFrames = 0
                    clearPending()
                    return state(tuning, alternate.cents, if (onset) "onset_switch" else "sustained_switch")
                }
            } else {
                clearPending()
            }
        }

        if (missedFrames >= releaseFrames) {
            activeNumber = null
            missedFrames = 0
            clearPending()
            return state(tuning, null, "released")
        }
        return state(tuning, alternate?.cents, if (pendingNumber != null) "switch_pending" else "latched")
    }

    fun reset() {
        activeNumber = null
        missedFrames = 0
        clearPending()
    }

    private fun confirm(number: Int) {
        if (pendingNumber == number) pendingFrames++ else {
            pendingNumber = number
            pendingFrames = 1
            pendingFromOnset = false
        }
    }

    private fun clearPending() {
        pendingNumber = null
        pendingFrames = 0
        pendingFromOnset = false
    }

    private fun state(tuning: Tuning?, cents: Double?, reason: String) = AutomaticStringState(
        activeString = activeString(tuning),
        pendingString = pendingNumber?.let { tuning?.byNumber(it) },
        confirmationFrames = pendingFrames,
        candidateCents = cents,
        reason = reason,
    )

    private data class Mapped(
        val candidate: PitchCandidate,
        val string: InstrumentString,
        val cents: Double,
    )

    private companion object {
        const val CAPTURE_CENTS = 250.0
        const val SUSTAINED_TARGET_CENTS = 80.0
        const val SUSTAINED_VOICED_PROBABILITY = 0.85
        const val SUSTAINED_SNR = 4.0
    }
}
