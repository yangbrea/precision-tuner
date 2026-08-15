package com.example.tunner

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tunner.audio.AudioInput
import com.example.tunner.audio.LowPassFilter
import com.example.tunner.audio.ReferenceToneEngine
import com.example.tunner.pitch.HybridPitchDetector
import com.example.tunner.settings.AccentColor
import com.example.tunner.settings.AppSettings
import com.example.tunner.settings.Sensitivity
import com.example.tunner.settings.SettingsRepository
import com.example.tunner.settings.ThemeMode
import com.example.tunner.tuning.CustomTuningStore
import com.example.tunner.tuning.InstrumentCatalog
import com.example.tunner.tuning.NoteMapper
import com.example.tunner.tuning.Tuning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log

/**
 * Owns the audio capture → pitch detection → note mapping pipeline and exposes
 * a throttled [TunerState] plus persisted [AppSettings] for the UI.
 */
class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private val settingsRepository = SettingsRepository(application)
    val settings: StateFlow<AppSettings> = settingsRepository.settings

    private val customStore = CustomTuningStore(application)
    val customMidis: StateFlow<List<Int>> = customStore.midis

    private val detector = HybridPitchDetector()
    private val filter = LowPassFilter()
    private val audioInput = AudioInput(SAMPLE_RATE)
    private val referenceTone = ReferenceToneEngine()
    private var collectJob: Job? = null

    // Rolling window of recent valid frequencies for median smoothing.
    private val freqWindow = ArrayDeque<Double>()

    // Frame buffers (window is the filtered sliding frame; hop is the raw chunk
    // read each cycle and filtered before being appended).
    private val window = ShortArray(FRAME_SIZE)
    private val hop = ShortArray(HOP)

    // Hysteresis: consecutive frames without an accepted pitch before the
    // displayed note is dropped (≈1.0s at 21.5 fps).
    private var missedFrames = 0
    private var frameCount = 0L

    /** Called by the UI after the RECORD_AUDIO permission result is known. */
    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) startListening()
    }

    fun setMode(mode: TunerMode) {
        stopReferenceTone()
        _state.update { it.copy(mode = mode) }
        resetDetection()
        // Pause capture on the metronome tab (so clicks aren't picked up), and
        // resume it when returning to a tuning tab.
        if (mode == TunerMode.METRONOME) {
            stopListening()
        } else if (_state.value.hasPermission) {
            startListening()
        }
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

    fun updateThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun updateInstrument(instrumentId: String) {
        val defaultTuning = InstrumentCatalog.instrument(instrumentId)?.defaultTuningId ?: "standard"
        settingsRepository.setInstrument(instrumentId, defaultTuning)
        _state.update { it.copy(selectedString = null) }
        freqWindow.clear()
    }

    fun updateTuning(tuningId: String) {
        settingsRepository.setTuning(tuningId)
        _state.update { it.copy(selectedString = null) }
        freqWindow.clear()
    }

    /** Resolve the effective tuning, handling the custom tuning specially. */
    fun resolveTuning(instrumentId: String, tuningId: String): Tuning? =
        if (instrumentId == CustomTuningStore.CUSTOM_ID) customStore.tuning()
        else InstrumentCatalog.tuning(instrumentId, tuningId)

    fun shiftCustomString(index: Int, delta: Int) = customStore.shiftString(index, delta)

    fun addCustomString() = customStore.addString()

    fun removeCustomString() = customStore.removeString()

    /** Play/stop the reference tone at [frequency] (Hz). */
    fun toggleReferenceTone(frequency: Double) {
        if (_state.value.isReferenceTonePlaying) {
            stopReferenceTone()
        } else {
            referenceTone.start(frequency)
            _state.update { it.copy(isReferenceTonePlaying = true) }
        }
    }

    fun stopReferenceTone() {
        referenceTone.stop()
        _state.update { it.copy(isReferenceTonePlaying = false) }
    }

    fun startListening() {
        if (collectJob != null) return
        _state.update { it.copy(isListening = true) }
        collectJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                audioInput.start()
                // Prime: read a full frame and filter it once.
                if (!audioInput.read(window, 0, FRAME_SIZE)) return@launch
                filter.process(window, settings.value.filterStrength)
                while (isActive) {
                    processWindow()
                    // Read HOP new raw samples, filter only them (stateful, so
                    // each sample is filtered exactly once), then slide.
                    if (!audioInput.read(hop, 0, HOP)) break
                    filter.process(hop, settings.value.filterStrength)
                    System.arraycopy(window, HOP, window, 0, FRAME_SIZE - HOP)
                    System.arraycopy(hop, 0, window, FRAME_SIZE - HOP, HOP)
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
        referenceTone.stop()
        _state.update { it.copy(isListening = false, isReferenceTonePlaying = false) }
        resetDetection()
    }

    private fun processWindow() {
        val s = settings.value
        val mode = _state.value.mode
        val tuning = resolveTuning(s.instrumentId, s.tuningId)

        // String lock: when a string is manually selected, constrain detection
        // to that string's pitch (±~100 cents) for robustness against harmonics
        // and other strings.
        val selectedNumber = _state.value.selectedString
        val lockedString = if (mode == TunerMode.INSTRUMENT && selectedNumber != null) {
            tuning?.byNumber(selectedNumber)
        } else null

        val pitch = if (lockedString != null) {
            detector.detectLocked(window, SAMPLE_RATE, lockedString.frequency)
        } else {
            detector.detect(window, SAMPLE_RATE)
        }
        val spectrum = detector.lastSpectrum?.toList() ?: emptyList()
        val accepted = pitch != null && pitch.confidence >= s.sensitivity.confidence

        if (!accepted) {
            missedFrames++
            if (missedFrames >= HOLD_FRAMES) {
                freqWindow.clear()
                missedFrames = 0
                _state.update { it.copy(
                    detectedFrequency = null,
                    noteName = null,
                    octave = null,
                    midi = null,
                    cents = null,
                    confidence = 0.0,
                    spectrum = spectrum,
                    activeString = if (it.mode == TunerMode.INSTRUMENT) it.selectedString else null,
                ) }
            } else {
                // Hold the last note through a brief dip; refresh spectrum only.
                _state.update { it.copy(spectrum = spectrum) }
            }
            logFrame(pitch?.frequency, pitch?.confidence ?: 0.0, accepted = false)
            return
        }

        missedFrames = 0
        freqWindow.addLast(pitch.frequency)
        val windowSize = s.sensitivity.windowSize
        if (freqWindow.size > windowSize) freqWindow.removeFirst()
        if (freqWindow.size < windowSize) {
            _state.update { it.copy(spectrum = spectrum) }
            return
        }

        val sorted = freqWindow.sorted()
        val medianFreq = sorted[sorted.size / 2]
        val a4 = _state.value.referenceA4

        // Locked: report deviation from the selected string's target.
        val noteName: String
        val octave: Int
        val midi: Int
        val cents: Double
        if (lockedString != null) {
            noteName = lockedString.noteName
            midi = lockedString.midi
            octave = lockedString.midi / 12 - 1
            cents = 1200.0 * log(medianFreq / lockedString.frequency, 2.0)
        } else {
            val note = NoteMapper.noteFromFrequency(medianFreq, a4)
            noteName = note.name
            octave = note.octave
            midi = note.midi
            cents = NoteMapper.cents(medianFreq, a4)
        }

        logFrame(pitch.frequency, pitch.confidence, accepted = true)

        _state.update { st ->
            val activeString = when {
                st.mode != TunerMode.INSTRUMENT -> null
                st.selectedString != null -> st.selectedString
                else -> tuning?.nearestString(midi)?.number
            }
            st.copy(
                detectedFrequency = medianFreq,
                noteName = noteName,
                octave = octave,
                midi = midi,
                cents = cents,
                confidence = pitch.confidence,
                spectrum = spectrum,
                activeString = activeString,
            )
        }
    }

    private fun logFrame(freq: Double?, confidence: Double, accepted: Boolean) {
        frameCount++
        if (frameCount % LOG_EVERY != 0L) return
        Log.d(TAG, "f=${freq ?: "null"} conf=${"%.2f".format(confidence)} accepted=$accepted missed=$missedFrames")
    }

    private fun resetDetection() {
        freqWindow.clear()
        missedFrames = 0
        _state.update {
            it.copy(
                detectedFrequency = null,
                noteName = null,
                octave = null,
                midi = null,
                cents = null,
                confidence = 0.0,
                spectrum = emptyList(),
                activeString = if (it.mode == TunerMode.INSTRUMENT) it.selectedString else null,
            )
        }
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }

    private companion object {
        const val TAG = "Tuner"
        const val SAMPLE_RATE = 44100
        const val FRAME_SIZE = 4096   // ~93 ms window
        const val HOP = 2048          // ~46 ms update interval (50% overlap)
        const val HOLD_FRAMES = 22    // ≈1.0s hold before dropping the note
        const val LOG_EVERY = 10L     // throttle debug logging
    }
}
