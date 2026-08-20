package com.precisiontuner.metronome

/**
 * Pure beat→frame state machine for the metronome player (no Android APIs, so
 * it is JVM-testable).
 *
 * The engine pre-queues the very first click (beat 1, sub-division 0) at frame
 * 0 when playback starts, so [nextClick] starts emitting from beat 1,
 * sub-division 1 onwards. Each click carries the frame position its start must
 * occupy in the audio buffer; the player writes silence up to that frame and
 * then the click, which makes every heard interval exactly [framesPerSub]
 * frames regardless of write timing or device start-up latency.
 */
class MetronomeSchedule(
    val sampleRate: Int,
    bpm: Int,
    subdivision: Int,
    beatsPerBar: Int,
    accentEnabled: Boolean,
) {
    var bpm: Int = bpm
        private set
    var subdivision: Int = subdivision
        private set
    var beatsPerBar: Int = beatsPerBar
        private set
    var accentEnabled: Boolean = accentEnabled
        private set

    /** Frames per sub-division at the current tempo. */
    val framesPerSub: Long
        get() = (sampleRate * 60.0 / (bpm * subdivision)).toLong()

    private var beat = 1
    private var sub = 1
    private var frame = 0L

    /** Applies live setting changes without resetting the beat/frame state. */
    fun update(bpm: Int, subdivision: Int, beatsPerBar: Int, accentEnabled: Boolean) {
        this.bpm = bpm
        this.subdivision = subdivision
        this.beatsPerBar = beatsPerBar
        this.accentEnabled = accentEnabled
    }

    /**
     * Advances to and returns the next click after the pre-queued first one
     * (beat 1, sub-division 0 at frame 0).
     */
    fun nextClick(): Click {
        if (sub >= subdivision) {
            sub = 0
            beat = if (beat >= beatsPerBar) 1 else beat + 1
        }
        val downbeat = sub == 0
        frame += framesPerSub
        sub++
        return Click(
            startFrame = frame,
            downbeat = downbeat,
            accent = downbeat && accentEnabled && beat == 1,
            subdivision = !downbeat,
        )
    }

    /** 1-based beat number of the most recently emitted click. */
    fun currentBeat(): Int = beat

    data class Click(
        /** Frame position the click's first sample must occupy in the buffer. */
        val startFrame: Long,
        val downbeat: Boolean,
        val accent: Boolean,
        val subdivision: Boolean,
    )
}
