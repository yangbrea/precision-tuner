package com.example.tunner.metronome

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tunner.audio.MetronomeEngine
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
            engine.start()
            var beat = 1
            var nextNanos = System.nanoTime()
            while (isActive) {
                val s = _state.value
                val beatNanos = (60_000_000_000.0 / s.bpm).toLong()
                val subNanos = beatNanos / s.subdivision
                for (sub in 0 until s.subdivision) {
                    val downbeat = sub == 0
                    engine.playClick(
                        accent = downbeat && s.accentEnabled && beat == 1,
                        subdivision = !downbeat,
                        volume = if (downbeat) s.volume else s.volume * SUB_VOLUME,
                    )
                    if (downbeat) _state.update { it.copy(currentBeat = beat) }

                    // NanoTime-based scheduling avoids cumulative drift.
                    nextNanos += subNanos
                    val waitMillis = (nextNanos - System.nanoTime()) / 1_000_000
                    if (waitMillis > 0) delay(waitMillis)
                }
                beat = if (beat >= s.beatsPerBar) 1 else beat + 1
            }
        }
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

    fun setAccent(enabled: Boolean) = _state.update { it.copy(accentEnabled = enabled) }

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
        const val MIN_BPM = 30
        const val MAX_BPM = 300
        const val SUB_VOLUME = 0.6f // sub-beat clicks are softer
    }
}
