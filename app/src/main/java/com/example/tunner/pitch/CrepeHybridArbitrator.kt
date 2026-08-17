package com.example.tunner.pitch

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

data class CrepeHybridState(
    val anchorFrequency: Double?,
    val triggerReason: String?,
    val supportedFrequency: Double?,
    val confirmationFrames: Int,
    val decisionSource: String,
)

/** Uses CREPE only to choose among integer-related DSP hypotheses. */
class CrepeHybridArbitrator(private val releaseFrames: Int = 22) {
    private var anchorFrequency: Double? = null
    private var missingFrames = 0

    var state = CrepeHybridState(null, null, null, 0, "dsp")
        private set

    fun triggerReason(candidates: List<PitchCandidate>): String? {
        val credible = candidates.filter { it.voicedProbability >= MIN_TRIGGER_CONFIDENCE }
        val pair = credible.indices.firstNotNullOfOrNull { first ->
            ((first + 1) until credible.size).firstNotNullOfOrNull { second ->
                integerRatio(credible[first].frequency, credible[second].frequency)?.let { ratio ->
                    "candidate_${ratio}x"
                }
            }
        }
        if (pair != null) return pair
        val best = credible.maxByOrNull { it.probability } ?: return null
        val anchor = anchorFrequency ?: return null
        val ratio = integerRatio(best.frequency, anchor) ?: return null
        return "anchor_${ratio}x"
    }

    fun arbitrate(
        candidates: List<PitchCandidate>,
        neuralFrequency: Double?,
        neuralConfidence: Double,
        minimumConfidence: Double,
        triggerReason: String?,
    ): List<PitchCandidate> {
        if (triggerReason == null || neuralFrequency == null || neuralConfidence < minimumConfidence) {
            state = CrepeHybridState(anchorFrequency, triggerReason, null, 0, "dsp_fallback")
            return candidates
        }

        val candidateMatch = candidates.minByOrNull { centsDistance(it.frequency, neuralFrequency) }
            ?.takeIf { centsDistance(it.frequency, neuralFrequency) <= SUPPORT_CENTS }
        val anchor = anchorFrequency
        val anchorMatches = anchor != null && centsDistance(anchor, neuralFrequency) <= SUPPORT_CENTS
        val supported = when {
            candidateMatch != null && (!anchorMatches ||
                centsDistance(candidateMatch.frequency, neuralFrequency) <= centsDistance(anchor!!, neuralFrequency)) -> {
                candidateMatch.copy(
                    probability = max(candidateMatch.probability, neuralConfidence),
                    voicedProbability = max(candidateMatch.voicedProbability, neuralConfidence),
                )
            }
            anchorMatches -> PitchCandidate(
                frequency = neuralFrequency,
                periodicity = neuralConfidence,
                spectralQuality = 1.0,
                probability = neuralConfidence,
                voicedProbability = neuralConfidence,
            )
            else -> null
        }
        if (supported == null) {
            state = CrepeHybridState(anchorFrequency, triggerReason, null, 0, "dsp_unmatched")
            return candidates
        }
        val confirmationFrames = if (state.supportedFrequency?.let {
                centsDistance(it, supported.frequency) <= SUPPORT_CENTS
            } == true
        ) state.confirmationFrames + 1 else 1
        state = CrepeHybridState(
            anchorFrequency, triggerReason, supported.frequency, confirmationFrames, "crepe",
        )
        return listOf(supported)
    }

    fun observeAccepted(frequency: Double) {
        if (!frequency.isFinite() || frequency <= 0.0) return
        anchorFrequency = frequency
        missingFrames = 0
        state = state.copy(anchorFrequency = frequency)
    }

    fun observeMissing() {
        missingFrames++
        if (missingFrames >= releaseFrames) {
            anchorFrequency = null
            missingFrames = 0
            state = CrepeHybridState(null, null, null, 0, "anchor_released")
        }
    }

    fun reset() {
        anchorFrequency = null
        missingFrames = 0
        state = CrepeHybridState(null, null, null, 0, "reset")
    }

    private fun integerRatio(first: Double, second: Double): Int? {
        val high = max(first, second)
        val low = minOf(first, second)
        return (2..3).firstOrNull { ratio ->
            abs(1200.0 * ln(high / low / ratio) / ln(2.0)) <= INTEGER_TOLERANCE_CENTS
        }
    }

    private fun centsDistance(first: Double, second: Double): Double =
        abs(1200.0 * ln(first / second) / ln(2.0))

    private companion object {
        const val MIN_TRIGGER_CONFIDENCE = 0.45
        const val INTEGER_TOLERANCE_CENTS = 55.0
        const val SUPPORT_CENTS = 80.0
    }
}
