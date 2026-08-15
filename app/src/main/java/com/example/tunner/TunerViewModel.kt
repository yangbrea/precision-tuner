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
import kotlin.math.abs

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
    private var smoothingPhase: DetectionPhase? = null

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
        stopReferenceTone()
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
        stopReferenceTone()
        val defaultTuning = InstrumentCatalog.instrument(instrumentId)?.defaultTuningId ?: "standard"
        settingsRepository.setInstrument(instrumentId, defaultTuning)
        _state.update { it.copy(selectedString = null) }
        resetDetection()
    }

    fun updateTuning(tuningId: String) {
        stopReferenceTone()
        settingsRepository.setTuning(tuningId)
        _state.update { it.copy(selectedString = null) }
        resetDetection()
    }

    /** Resolve the effective tuning, handling the custom tuning specially. */
    fun resolveTuning(instrumentId: String, tuningId: String): Tuning? =
        if (instrumentId == CustomTuningStore.CUSTOM_ID) customStore.tuning()
        else InstrumentCatalog.tuning(instrumentId, tuningId)

    fun shiftCustomString(index: Int, delta: Int) {
        stopReferenceTone()
        customStore.shiftString(index, delta)
        resetDetection()
    }

    fun addCustomString() {
        stopReferenceTone()
        customStore.addString()
        _state.update { it.copy(selectedString = null) }
        resetDetection()
    }

    fun removeCustomString() {
        stopReferenceTone()
        customStore.removeString()
        _state.update { it.copy(selectedString = null) }
        resetDetection()
    }

    /** Play/stop the currently selected string's reference tone. */
    fun toggleReferenceTone() {
        if (_state.value.isReferenceTonePlaying) {
            stopReferenceTone()
            return
        }

        val state = _state.value
        if (state.mode != TunerMode.INSTRUMENT) return
        val selectedNumber = state.selectedString ?: return
        val currentSettings = settings.value
        val target = resolveTuning(currentSettings.instrumentId, currentSettings.tuningId)
            ?.byNumber(selectedNumber) ?: return
        referenceTone.start(target.frequency)
        _state.update { it.copy(isReferenceTonePlaying = true) }
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

        // Always run the broad-band detector first. Besides finding the actual
        // input pitch, this refreshes the FFT spectrum even in manual mode.
        val broadPitch = detector.detect(window, SAMPLE_RATE)
        val spectrum = detector.lastSpectrum?.toList() ?: emptyList()
        val broadAccepted = broadPitch != null &&
            broadPitch.confidence >= s.sensitivity.confidence

        if (!broadAccepted) {
            missedFrames++
            if (missedFrames >= HOLD_FRAMES) {
                freqWindow.clear()
                smoothingPhase = null
                missedFrames = 0
                _state.update { it.copy(
                    detectedFrequency = null,
                    noteName = lockedString?.noteName,
                    octave = lockedString?.let { target -> target.midi / 12 - 1 },
                    midi = lockedString?.midi,
                    cents = null,
                    confidence = 0.0,
                    detectionPhase = DetectionPhase.WAITING,
                    observedNoteName = null,
                    observedOctave = null,
                    spectrum = spectrum,
                    activeString = if (it.mode == TunerMode.INSTRUMENT) it.selectedString else null,
                ) }
            } else {
                // Hold the last note through a brief dip; refresh spectrum only.
                _state.update { it.copy(spectrum = spectrum) }
            }
            logFrame(broadPitch?.frequency, broadPitch?.confidence ?: 0.0, accepted = false)
            return
        }

        val broad = broadPitch!!
        var phase = DetectionPhase.TRACKING
        var pitch = broad
        if (lockedString != null) {
            val broadCents = lockedString.centsFrom(broad.frequency)
            if (abs(broadCents) > MANUAL_LOCK_CENTS) {
                phase = DetectionPhase.OUT_OF_RANGE
            } else {
                // Close to the selected target, use the narrow YIN search for
                // stable fine tuning. Fall back to the valid broad estimate.
                val refined = detector.detectLocked(window, SAMPLE_RATE, lockedString.frequency)
                if (refined != null && refined.confidence >= s.sensitivity.confidence) {
                    pitch = refined
                }
            }
        }

        missedFrames = 0
        if (smoothingPhase != phase) {
            freqWindow.clear()
            if (smoothingPhase != null) {
                _state.update { it.copy(
                    detectedFrequency = null,
                    cents = null,
                    confidence = 0.0,
                    detectionPhase = DetectionPhase.WAITING,
                    observedNoteName = null,
                    observedOctave = null,
                    spectrum = spectrum,
                ) }
            }
            smoothingPhase = phase
        }
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
        val observedNote = NoteMapper.noteFromFrequency(medianFreq, a4)

        // Instrument mode always reports deviation from a target in the active
        // tuning. Chromatic mode continues to use the nearest 12-TET note.
        val noteName: String
        val octave: Int
        val midi: Int
        val cents: Double
        val targetString = if (mode == TunerMode.INSTRUMENT) {
            lockedString ?: tuning?.nearestString(medianFreq)
        } else null
        if (targetString != null) {
            noteName = targetString.noteName
            midi = targetString.midi
            octave = targetString.midi / 12 - 1
            cents = targetString.centsFrom(medianFreq)
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
                else -> targetString?.number
            }
            st.copy(
                detectedFrequency = medianFreq,
                noteName = noteName,
                octave = octave,
                midi = midi,
                cents = cents,
                confidence = pitch.confidence,
                detectionPhase = phase,
                observedNoteName = if (phase == DetectionPhase.OUT_OF_RANGE) observedNote.name else null,
                observedOctave = if (phase == DetectionPhase.OUT_OF_RANGE) observedNote.octave else null,
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
        smoothingPhase = null
        missedFrames = 0
        val currentState = _state.value
        val currentSettings = settings.value
        val manualTarget = if (
            currentState.mode == TunerMode.INSTRUMENT && currentState.selectedString != null
        ) {
            resolveTuning(currentSettings.instrumentId, currentSettings.tuningId)
                ?.byNumber(currentState.selectedString)
        } else null
        _state.update {
            it.copy(
                detectedFrequency = null,
                noteName = manualTarget?.noteName,
                octave = manualTarget?.let { target -> target.midi / 12 - 1 },
                midi = manualTarget?.midi,
                cents = null,
                confidence = 0.0,
                detectionPhase = DetectionPhase.WAITING,
                observedNoteName = null,
                observedOctave = null,
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
        const val MANUAL_LOCK_CENTS = 100.0
        const val LOG_EVERY = 10L     // throttle debug logging
    }
}
