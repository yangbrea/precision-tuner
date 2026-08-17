package com.example.tunner.pitch

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

data class TrackedPitch(
    val pitch: Pitch?,
    val candidateCount: Int,
    val voicedProbability: Double,
)

/**
 * Small fixed-lag Viterbi tracker inspired by pYIN. It keeps the actual YIN
 * candidates and finds a likely path; it never averages unrelated pitches.
 */
class OnlinePitchTracker(private val lagFrames: Int = 3) {
    private data class Frame(val candidates: List<PitchCandidate>, val target: Double?, val onset: Boolean)
    private data class State(val candidate: PitchCandidate?)
    private data class Path(val score: Double, val states: List<State>)

    private val frames = ArrayDeque<Frame>()
    private var lastOutputFrequency: Double? = null

    fun submit(
        candidates: List<PitchCandidate>,
        targetFrequency: Double? = null,
        onset: Boolean = false,
    ): TrackedPitch? {
        frames.addLast(Frame(candidates.take(MAX_CANDIDATES), targetFrequency, onset))
        while (frames.size > lagFrames) frames.removeFirst()
        if (frames.size < lagFrames) return null

        var paths = states(frames.first()).map { state ->
            Path(emission(state, frames.first()), listOf(state))
        }
        frames.drop(1).forEach { frame ->
            paths = states(frame).map { state ->
                paths.maxBy { previous ->
                    previous.score + transition(previous.states.last(), state, frame.onset)
                }.let { previous ->
                    Path(
                        previous.score + transition(previous.states.last(), state, frame.onset) + emission(state, frame),
                        previous.states + state,
                    )
                }
            }
        }

        var current = paths.maxBy { it.score }.states.last().candidate
        val previousOutput = lastOutputFrequency
        if (current != null && previousOutput != null &&
            centsDistance(current.frequency, previousOutput) > CONTINUOUS_CENTS
        ) {
            val confirmedInEveryFrame = frames.all { frame ->
                frame.candidates.any {
                    centsDistance(it.frequency, current!!.frequency) <= CONFIRM_CLUSTER_CENTS
                }
            }
            if (!confirmedInEveryFrame) current = null
        }
        if (current != null) lastOutputFrequency = current.frequency
        return TrackedPitch(
            pitch = current?.let { Pitch(it.frequency, it.voicedProbability) },
            candidateCount = frames.last().candidates.size,
            voicedProbability = current?.voicedProbability ?: 0.0,
        )
    }

    fun reset() {
        frames.clear()
        lastOutputFrequency = null
    }

    private fun states(frame: Frame): List<State> = frame.candidates.map(::State) + State(null)

    private fun emission(state: State, frame: Frame): Double {
        val candidate = state.candidate
        if (candidate == null) {
            val voiced = frame.candidates.maxOfOrNull { it.voicedProbability } ?: 0.0
            return ln((1.0 - voiced).coerceIn(MIN_PROBABILITY, 1.0))
        }
        var score = ln(candidate.probability.coerceIn(MIN_PROBABILITY, 1.0))
        frame.target?.let { target ->
            val distance = centsDistance(candidate.frequency, target)
            score += TARGET_PRIOR_WEIGHT * exp(-distance / TARGET_PRIOR_WIDTH_CENTS)
        }
        return score
    }

    private fun transition(from: State, to: State, onset: Boolean): Double {
        val a = from.candidate
        val b = to.candidate
        if (a == null && b == null) return -0.05
        if (a == null) return if (onset) -0.20 else -0.75
        if (b == null) return -0.65

        val distance = centsDistance(a.frequency, b.frequency)
        var cost = (distance / TRANSITION_SCALE_CENTS).coerceAtMost(MAX_TRANSITION_COST)
        if (onset) cost *= ONSET_TRANSITION_MULTIPLIER
        if (isOctaveJump(distance)) cost += OCTAVE_JUMP_COST
        return -cost
    }

    private fun isOctaveJump(cents: Double): Boolean =
        abs(cents - 1200.0) <= OCTAVE_TOLERANCE_CENTS ||
            abs(cents - 2400.0) <= OCTAVE_TOLERANCE_CENTS

    private fun centsDistance(a: Double, b: Double): Double =
        abs(1200.0 * ln(a / b) / ln(2.0))

    private companion object {
        const val MAX_CANDIDATES = 5
        const val MIN_PROBABILITY = 0.001
        const val TRANSITION_SCALE_CENTS = 220.0
        const val MAX_TRANSITION_COST = 5.0
        const val OCTAVE_JUMP_COST = 3.0
        const val OCTAVE_TOLERANCE_CENTS = 90.0
        const val ONSET_TRANSITION_MULTIPLIER = 0.35
        const val TARGET_PRIOR_WEIGHT = 0.45
        const val TARGET_PRIOR_WIDTH_CENTS = 180.0
        const val CONTINUOUS_CENTS = 150.0
        const val CONFIRM_CLUSTER_CENTS = 80.0
    }
}

/** Detects a pluck/note onset from a rapid rise over the recent RMS envelope. */
class RmsOnsetDetector {
    private var envelope = 0.0

    fun observe(rms: Double, noiseFloor: Double): Boolean {
        val onset = envelope > 0.0 && rms >= noiseFloor * 4.0 && rms >= envelope * 1.9
        val alpha = if (rms < envelope) 0.18 else 0.08
        envelope += alpha * (rms - envelope)
        return onset
    }

    fun reset() {
        envelope = 0.0
    }
}
