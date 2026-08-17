package com.example.tunner.pitch

import kotlin.math.abs

data class InTuneCueState(
    val armed: Boolean,
    val centerFrames: Int,
    val farFrames: Int,
)

/** Debounces the audible and visual "in tune" event with hysteresis. */
class InTuneCueGate(
    private val centerCents: Double = 5.0,
    private val centerFramesRequired: Int = DEFAULT_CENTER_FRAMES,
    private val rearmCents: Double = 12.0,
    private val rearmFramesRequired: Int = 4,
) {
    private var targetKey: String? = null
    private var armed = true
    private var centerFrames = 0
    private var farFrames = 0

    val state: InTuneCueState
        get() = InTuneCueState(armed, centerFrames, farFrames)

    fun observe(target: String?, cents: Double?, tracking: Boolean): Boolean {
        if (target != targetKey) {
            targetKey = target
            armed = true
            centerFrames = 0
            farFrames = 0
        }
        if (!tracking || cents == null || target == null) {
            centerFrames = 0
            farFrames = 0
            return false
        }

        val distance = abs(cents)
        if (armed) {
            farFrames = 0
            centerFrames = if (distance <= centerCents) centerFrames + 1 else 0
            if (centerFrames >= centerFramesRequired) {
                armed = false
                centerFrames = 0
                return true
            }
        } else {
            centerFrames = 0
            farFrames = if (distance > rearmCents) farFrames + 1 else 0
            if (farFrames >= rearmFramesRequired) {
                armed = true
                farFrames = 0
            }
        }
        return false
    }

    /** Breaks consecutive-frame confirmation without changing armed state or target. */
    fun observeInvalid() {
        centerFrames = 0
        farFrames = 0
    }

    fun reset() {
        targetKey = null
        armed = true
        centerFrames = 0
        farFrames = 0
    }

    companion object {
        /**
         * Consecutive in-tune frames (|cents| <= [centerCents]) required before
         * the cue fires. A pluck glides through the center quickly, so a short
         * window would cue on a momentary pass: at the app's ~21.5 fps frame
         * rate (46 ms hop) 10 frames ≈ 460 ms of sustained in-tune pitch.
         */
        const val DEFAULT_CENTER_FRAMES = 10
    }
}
