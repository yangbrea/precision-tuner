package com.precisiontuner

import kotlin.math.abs

/**
 * Stabilized visual verdict for the tuning UI.
 *
 * This is UI-only: it never feeds back into pitch detection or the cue gate,
 * and the algorithm layer keeps consuming the raw per-frame [TunerState.cents]
 * and [TunerState.isInTune] untouched.
 */
enum class TuneVisualState { WAITING, LOW, IN_TUNE, HIGH }

/**
 * Debounces the raw per-frame cents into a stable [TuneVisualState] so the
 * tuner UI does not flicker while cents hover around the ±5¢ in-tune boundary.
 *
 * - Entering [TuneVisualState.IN_TUNE] requires [ENTER_FRAMES_REQUIRED]
 *   consecutive frames within ±[enterCents] (~180 ms at the app's 46 ms hop).
 * - Once IN_TUNE, only [EXIT_FRAMES_REQUIRED] consecutive frames beyond
 *   ±[exitCents] on one side leave it; 5–8¢ critical jitter stays "in tune".
 * - Up to [WAIT_FRAMES_REQUIRED] - 1 consecutive unreliable frames (no pitch,
 *   not tracking) leave the current state untouched; the Nth unreliable frame
 *   in a row falls back to WAITING.
 * - A target change (target note, active string, mode, instrument, tuning)
 *   resets the state immediately so a stale verdict never carries over.
 *
 * This class never produces sounds, pulses, or haptics; the one-shot "locked"
 * feedback stays driven by [com.precisiontuner.pitch.InTuneCueGate] via
 * [TunerState.inTuneFlash].
 */
class TuneVisualStabilizer(
    private val enterCents: Double = ENTER_CENTS,
    private val enterFramesRequired: Int = ENTER_FRAMES_REQUIRED,
    private val exitCents: Double = EXIT_CENTS,
    private val exitFramesRequired: Int = EXIT_FRAMES_REQUIRED,
    private val waitFramesRequired: Int = WAIT_FRAMES_REQUIRED,
) {
    private var targetKey: String? = null
    private var state = TuneVisualState.WAITING
    // Consecutive frames within ±enterCents (confirmation toward IN_TUNE).
    private var enterFrames = 0
    // Consecutive frames beyond ±exitCents on one side (hysteresis exit).
    private var exitFrames = 0
    private var exitSide: TuneVisualState? = null
    // Consecutive frames without reliable input.
    private var lostFrames = 0

    /** The stabilized state after the most recent observation. */
    val currentState: TuneVisualState
        get() = state

    /**
     * Feeds one frame of (possibly valid) input. Returns the resulting state.
     *
     * [target] identifies the current tuning target; a change resets the state
     * before the current frame is evaluated. [tracking] false or a null /
     * non-finite [cents] counts as an unreliable frame.
     */
    fun observe(target: String?, cents: Double?, tracking: Boolean): TuneVisualState {
        if (target != targetKey) {
            targetKey = target
            resetCounters()
        }
        if (!tracking || cents == null || !cents.isFinite()) {
            return observeLost()
        }
        lostFrames = 0

        val distance = abs(cents)
        if (distance <= enterCents) {
            enterFrames++
            exitFrames = 0
            exitSide = null
            if (state != TuneVisualState.IN_TUNE) {
                // Not yet confirmed: follow the raw sign so the verdict always
                // matches the displayed cents; the confirmation window prevents
                // 调准-boundary chatter, and centering happens only after lock.
                state = if (cents < 0) TuneVisualState.LOW else TuneVisualState.HIGH
                if (enterFrames >= enterFramesRequired) {
                    state = TuneVisualState.IN_TUNE
                    enterFrames = 0
                }
            }
        } else {
            enterFrames = 0
            if (state == TuneVisualState.IN_TUNE) {
                if (distance > exitCents) {
                    val side = if (cents < 0) TuneVisualState.LOW else TuneVisualState.HIGH
                    if (exitSide == side) {
                        exitFrames++
                    } else {
                        exitSide = side
                        exitFrames = 1
                    }
                    if (exitFrames >= exitFramesRequired) {
                        state = side
                        exitFrames = 0
                        exitSide = null
                    }
                } else {
                    // 5–8¢ critical band: keep the tuned verdict, no exit streak.
                    exitFrames = 0
                    exitSide = null
                }
            } else {
                state = if (cents < 0) TuneVisualState.LOW else TuneVisualState.HIGH
                exitFrames = 0
                exitSide = null
            }
        }
        return state
    }

    /** Feeds one frame with no reliable input (silence, dropout, out of range). */
    fun observeInvalid(): TuneVisualState {
        return observeLost()
    }

    /** Clears the target and every counter, returning to WAITING. */
    fun reset() {
        targetKey = null
        resetCounters()
    }

    private fun observeLost(): TuneVisualState {
        lostFrames++
        if (lostFrames >= waitFramesRequired) {
            resetCounters()
        }
        // 1..N-1 lost frames keep the current state (and its counters) intact so
        // a brief dropout never flickers the UI.
        return state
    }

    private fun resetCounters() {
        state = TuneVisualState.WAITING
        enterFrames = 0
        exitFrames = 0
        exitSide = null
        lostFrames = 0
    }

    companion object {
        /** In-tune window (±5¢) used to confirm and hold the tuned state. */
        const val ENTER_CENTS = 5.0
        /** Consecutive frames within ±5¢ before IN_TUNE is shown (~180 ms at 21.5 fps). */
        const val ENTER_FRAMES_REQUIRED = 4
        /** Hysteresis: IN_TUNE is left only beyond ±8¢. */
        const val EXIT_CENTS = 8.0
        /** Consecutive frames beyond ±8¢ (same side) before leaving IN_TUNE. */
        const val EXIT_FRAMES_REQUIRED = 3
        /** Consecutive unreliable frames before falling back to WAITING. */
        const val WAIT_FRAMES_REQUIRED = 3
    }
}
