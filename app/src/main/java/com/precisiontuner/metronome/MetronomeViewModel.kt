package com.precisiontuner.metronome

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.precisiontuner.audio.MetronomeEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MetronomeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val engine = MetronomeEngine()
    private var playJob: Job? = null

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<MetronomeState> = _state.asStateFlow()

    private val tapTimes = ArrayDeque<Long>()

    fun toggle() {
        if (_state.value.isPlaying) stop() else start()
    }

    fun start() {
        if (playJob != null) return
        _state.update { it.copy(isPlaying = true, currentBeat = 0) }
        playJob = viewModelScope.launch(Dispatchers.Default) {
            val s0 = _state.value
            // Queue beat 1 at the head of the buffer; it sounds as soon as the
            // audio path starts (no silence priming delay, no wall-clock drift).
            engine.start(firstAccent = s0.accentEnabled, firstVolume = s0.volume)
            _state.update { it.copy(currentBeat = 1) }
            val schedule = MetronomeSchedule(
                sampleRate = MetronomeEngine.SAMPLE_RATE,
                bpm = s0.bpm,
                subdivision = s0.subdivision,
                beatsPerBar = s0.beatsPerBar,
                accentEnabled = s0.accentEnabled,
            )
            while (isActive) {
                val s = _state.value
                schedule.update(s.bpm, s.subdivision, s.beatsPerBar, s.accentEnabled)
                val click = schedule.nextClick()
                // Feed silence up to the click's start frame. All writes are
                // non-blocking: when the buffer is full they return 0 and we
                // retry after a short pause, so the queue is paced by actual
                // playback consumption and the heard intervals are exactly the
                // frame spacing.
                while (isActive && engine.framesQueued < click.startFrame) {
                    val want = (click.startFrame - engine.framesQueued)
                        .coerceAtMost(MetronomeEngine.SILENCE_CHUNK.toLong())
                        .toInt()
                    retrySilence(this) { engine.writeSilence(want) }
                }
                if (!isActive) break
                retryClick(this) {
                    engine.playClick(
                        accent = click.accent,
                        subdivision = click.subdivision,
                        volume = s.volume * if (click.downbeat) 1f else SUB_VOLUME,
                    )
                }
                if (click.downbeat) _state.update { it.copy(currentBeat = schedule.currentBeat()) }
            }
        }
    }

    /**
     * Keeps attempting a non-blocking silence write until it makes progress. A
     * 0 return means the track buffer is full (playback has not drained
     * enough), so we pause briefly and retry; a negative return means the
     * track is gone (e.g. stopped), so we give up.
     */
    private suspend fun retrySilence(scope: CoroutineScope, write: () -> Int) {
        while (scope.isActive) {
            val w = write()
            if (w != 0) return
            delay(2)
        }
    }

    /** Keeps resuming the pending click until the whole click has been queued. */
    private suspend fun retryClick(scope: CoroutineScope, write: () -> Boolean) {
        while (scope.isActive && !write()) delay(2)
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        engine.stop()
        _state.update { it.copy(isPlaying = false, currentBeat = 0) }
    }

    fun setBpm(bpm: Int) {
        _state.update { it.copy(bpm = bpm.coerceIn(MIN_BPM, MAX_BPM)) }
        persist()
    }

    fun setBeatsPerBar(n: Int) {
        _state.update { it.copy(beatsPerBar = n.coerceIn(1, 12)) }
        persist()
    }

    fun setSubdivision(n: Int) {
        _state.update { it.copy(subdivision = n.coerceIn(1, 4)) }
        persist()
    }

    fun setNoteValue(n: Int) {
        _state.update { it.copy(noteValue = if (n == 8) 8 else 4) }
        persist()
    }

    fun setAccent(enabled: Boolean) {
        _state.update { it.copy(accentEnabled = enabled) }
        persist()
    }

    fun setVolume(volume: Float) = _state.update { it.copy(volume = volume.coerceIn(0f, 1f)) }

    /** Tap tempo: average the last few tap intervals and set BPM. */
    fun tap() {
        val now = System.nanoTime()
        tapTimes.addLast(now)
        if (tapTimes.size > 5) tapTimes.removeFirst()
        if (tapTimes.size >= 2) {
            val intervals = tapTimes.zipWithNext { a, b -> b - a }
            val avgNanos = intervals.average()
            if (avgNanos > 0) {
                val bpm = (60_000_000_000.0 / avgNanos).toInt().coerceIn(MIN_BPM, MAX_BPM)
                _state.update { it.copy(bpm = bpm) }
                persist()
            }
        }
    }

    private fun persist() {
        prefs.edit()
            .putInt(KEY_BPM, _state.value.bpm)
            .putInt(KEY_BEATS, _state.value.beatsPerBar)
            .putInt(KEY_SUBDIVISION, _state.value.subdivision)
            .putInt(KEY_NOTE_VALUE, _state.value.noteValue)
            .putBoolean(KEY_ACCENT, _state.value.accentEnabled)
            .apply()
    }

    private fun loadState(): MetronomeState {
        val bpm = prefs.getInt(KEY_BPM, 120).coerceIn(MIN_BPM, MAX_BPM)
        val beats = prefs.getInt(KEY_BEATS, 4).coerceIn(1, 12)
        val subdivision = prefs.getInt(KEY_SUBDIVISION, 1).coerceIn(1, 4)
        val noteValue = if (prefs.getInt(KEY_NOTE_VALUE, 4) == 8) 8 else 4
        return MetronomeState(
            bpm = bpm,
            beatsPerBar = beats,
            subdivision = subdivision,
            noteValue = noteValue,
            accentEnabled = prefs.getBoolean(KEY_ACCENT, true),
        )
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private companion object {
        const val PREFS_NAME = "metronome_prefs"
        const val KEY_BPM = "bpm"
        const val KEY_BEATS = "beats"
        const val KEY_SUBDIVISION = "subdivision"
        const val KEY_NOTE_VALUE = "noteValue"
        const val KEY_ACCENT = "accent"
        const val MIN_BPM = 30
        const val MAX_BPM = 300
        const val SUB_VOLUME = 0.6f // sub-beat clicks are softer
    }
}
