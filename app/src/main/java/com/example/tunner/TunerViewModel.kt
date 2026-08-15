package com.example.tunner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tunner.audio.AudioInput
import com.example.tunner.audio.LowPassFilter
import com.example.tunner.pitch.YinPitchDetector
import com.example.tunner.settings.AccentColor
import com.example.tunner.settings.AppSettings
import com.example.tunner.settings.Sensitivity
import com.example.tunner.settings.SettingsRepository
import com.example.tunner.tuning.GuitarTuning
import com.example.tunner.tuning.NoteMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the audio capture → pitch detection → note mapping pipeline and exposes
 * a throttled [TunerState] plus persisted [AppSettings] for the UI.
 */
class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private val settingsRepository = SettingsRepository(application)
    val settings: StateFlow<AppSettings> = settingsRepository.settings

    private val detector = YinPitchDetector()
    private val filter = LowPassFilter()
    private val audioInput = AudioInput(SAMPLE_RATE)
    private var collectJob: Job? = null

    // Rolling window of recent valid frequencies for median smoothing.
    private val freqWindow = ArrayDeque<Double>()

    private val window = ShortArray(FRAME_SIZE)

    /** Called by the UI after the RECORD_AUDIO permission result is known. */
    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) startListening()
    }

    fun setMode(mode: TunerMode) {
        _state.update { it.copy(mode = mode) }
        resetDetection()
    }

    fun selectString(number: Int?) {
        _state.update { it.copy(selectedString = number) }
        resetDetection()
    }

    fun setReferenceA4(a4: Double) {
        _state.update { it.copy(referenceA4 = a4) }
    }

    fun updateAccent(accent: AccentColor) = settingsRepository.setAccent(accent)

    fun updateSensitivity(sensitivity: Sensitivity) {
        settingsRepository.setSensitivity(sensitivity)
        // Changing the smoothing window invalidates the rolling median buffer.
        freqWindow.clear()
    }

    fun updateFilterStrength(strength: Float) = settingsRepository.setFilterStrength(strength)

    fun startListening() {
        if (collectJob != null) return
        _state.update { it.copy(isListening = true) }
        collectJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                audioInput.start()
                // Prime the sliding window with a full frame.
                if (!audioInput.read(window, 0, FRAME_SIZE)) return@launch
                while (isActive) {
                    processWindow()
                    // Slide by HOP and top up with new samples (50% overlap).
                    System.arraycopy(window, HOP, window, 0, FRAME_SIZE - HOP)
                    if (!audioInput.read(window, FRAME_SIZE - HOP, HOP)) break
                }
            } finally {
                audioInput.stop()
                _state.update { it.copy(isListening = false) }
            }
        }
    }

    fun stopListening() {
        collectJob?.cancel()
        collectJob = null
        audioInput.stop()
        _state.update { it.copy(isListening = false) }
        resetDetection()
    }

    private fun processWindow() {
        val s = settings.value

        // Pre-filter the frame to reduce high-frequency noise / harmonics.
        filter.process(window, s.filterStrength)

        val pitch = detector.detect(window, SAMPLE_RATE)
        if (pitch == null || pitch.confidence < s.sensitivity.confidence) {
            freqWindow.clear()
            _state.update { it.copy(
                detectedFrequency = null,
                noteName = null,
                octave = null,
                midi = null,
                cents = null,
                confidence = 0.0,
                activeString = if (it.mode == TunerMode.GUITAR) it.selectedString else null,
            ) }
            return
        }

        freqWindow.addLast(pitch.frequency)
        val windowSize = s.sensitivity.windowSize
        if (freqWindow.size > windowSize) freqWindow.removeFirst()
        if (freqWindow.size < windowSize) return

        val sorted = freqWindow.sorted()
        val medianFreq = sorted[sorted.size / 2]

        val a4 = _state.value.referenceA4
        val note = NoteMapper.noteFromFrequency(medianFreq, a4)
        val cents = NoteMapper.cents(medianFreq, a4)

        _state.update { st ->
            val activeString = when {
                st.mode != TunerMode.GUITAR -> null
                st.selectedString != null -> st.selectedString
                else -> GuitarTuning.nearestString(note.midi)?.number
            }
            st.copy(
                detectedFrequency = medianFreq,
                noteName = note.name,
                octave = note.octave,
                midi = note.midi,
                cents = cents,
                confidence = pitch.confidence,
                activeString = activeString,
            )
        }
    }

    private fun resetDetection() {
        freqWindow.clear()
        _state.update {
            it.copy(
                detectedFrequency = null,
                noteName = null,
                octave = null,
                midi = null,
                cents = null,
                confidence = 0.0,
                activeString = if (it.mode == TunerMode.GUITAR) it.selectedString else null,
            )
        }
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val FRAME_SIZE = 4096   // ~93 ms window
        const val HOP = 2048          // ~46 ms update interval (50% overlap)
    }
}
