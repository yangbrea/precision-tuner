package com.example.tunner

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tunner.audio.AudioInput
import com.example.tunner.audio.CueSoundPlayer
import com.example.tunner.audio.LowPassFilter
import com.example.tunner.audio.ReferenceToneEngine
import com.example.tunner.audio.downsampleWaveform
import com.example.tunner.pitch.HybridPitchDetector
import com.example.tunner.pitch.AutomaticStringState
import com.example.tunner.pitch.AutomaticStringTracker
import com.example.tunner.pitch.InTuneCueGate
import com.example.tunner.pitch.NoiseFloorEstimator
import com.example.tunner.pitch.OnlinePitchTracker
import com.example.tunner.pitch.Pitch
import com.example.tunner.pitch.PitchCandidate
import com.example.tunner.pitch.RmsOnsetDetector
import com.example.tunner.settings.AccentColor
import com.example.tunner.settings.AppSettings
import com.example.tunner.settings.Sensitivity
import com.example.tunner.settings.SettingsRepository
import com.example.tunner.settings.ThemeMode
import com.example.tunner.settings.VisualMode
import com.example.tunner.tuning.CustomTuningStore
import com.example.tunner.tuning.CustomTuningPreset
import com.example.tunner.tuning.SavePresetResult
import com.example.tunner.tuning.InstrumentCatalog
import com.example.tunner.tuning.InstrumentString
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
import kotlin.math.ln

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
    val customPresets: StateFlow<List<CustomTuningPreset>> = customStore.presets

    private val detector = HybridPitchDetector()
    private val filter = LowPassFilter()
    private val audioInput = AudioInput(SAMPLE_RATE)
    private val referenceTone = ReferenceToneEngine()
    private val cueSound = CueSoundPlayer()
    private var collectJob: Job? = null

    private val noiseEstimator = NoiseFloorEstimator()
    private val pitchTracker = OnlinePitchTracker(lagFrames = TRACKER_LAG_FRAMES)
    private val onsetDetector = RmsOnsetDetector()
    private val automaticStringTracker = AutomaticStringTracker(releaseFrames = HOLD_FRAMES)
    private var automaticStringState = AutomaticStringState(null, null, 0, null, 0, "reset")
    private val inTuneCueGate = InTuneCueGate()

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
        pitchTracker.reset()
    }

    fun updateFilterStrength(strength: Float) = settingsRepository.setFilterStrength(strength)

    fun updateThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun updateVisualMode(mode: VisualMode) = settingsRepository.setVisualMode(mode)

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

    /** Resolve built-in and custom tunings through the same persisted selection. */
    fun resolveTuning(instrumentId: String, tuningId: String): Tuning? =
        customStore.presetForTuningId(tuningId)?.toTuning()
            ?: InstrumentCatalog.tuning(instrumentId, tuningId)

    fun selectCustomPreset(preset: CustomTuningPreset) {
        stopReferenceTone()
        settingsRepository.setInstrument(preset.instrumentId ?: CustomTuningStore.CUSTOM_INSTRUMENT_ID, preset.tuningId)
        _state.update { it.copy(selectedString = null) }
        resetDetection()
    }

    fun createCustomPreset(name: String, instrumentId: String?, midis: List<Int>): SavePresetResult =
        customStore.create(name, instrumentId, midis)

    fun updateCustomPreset(id: String, name: String, instrumentId: String?, midis: List<Int>): SavePresetResult {
        val result = customStore.update(id, name, instrumentId, midis)
        if (result == SavePresetResult.SAVED && settings.value.tuningId == "${CustomTuningPreset.TUNING_ID_PREFIX}$id") {
            stopReferenceTone()
            val updated = customStore.preset(id)!!
            settingsRepository.setInstrument(updated.instrumentId ?: CustomTuningStore.CUSTOM_INSTRUMENT_ID, updated.tuningId)
            _state.update { it.copy(selectedString = null, activeString = null) }
            resetDetection()
        }
        return result
    }

    fun deleteCustomPreset(id: String) {
        val selected = settings.value.tuningId == "${CustomTuningPreset.TUNING_ID_PREFIX}$id"
        val deleted = customStore.delete(id) ?: return
        if (selected) {
            stopReferenceTone()
            val fallbackInstrument = deleted.instrumentId ?: "guitar"
            val fallbackTuning = InstrumentCatalog.instrument(fallbackInstrument)?.defaultTuningId ?: "standard"
            settingsRepository.setInstrument(fallbackInstrument, fallbackTuning)
            _state.update { it.copy(selectedString = null, activeString = null) }
            resetDetection()
        }
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

    private fun playInTuneCue() {
        viewModelScope.launch(Dispatchers.Default) {
            cueSound.play()
        }
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
        val startedNanos = System.nanoTime()
        val s = settings.value
        val mode = _state.value.mode
        val tuning = resolveTuning(s.instrumentId, s.tuningId)
        val waveform = downsampleWaveform(window)

        // String lock: when a string is manually selected, constrain detection
        // to that string's pitch (±~100 cents) for robustness against harmonics
        // and other strings.
        val selectedNumber = _state.value.selectedString
        val manualString = if (mode == TunerMode.INSTRUMENT && selectedNumber != null) {
            tuning?.byNumber(selectedNumber)
        } else null
        val automaticMode = mode == TunerMode.INSTRUMENT && selectedNumber == null
        val previousAutomaticString = if (automaticMode) automaticStringTracker.activeString(tuning) else null

        val rms = noiseEstimator.rms(window)
        if (!noiseEstimator.shouldAnalyze(rms, signalToNoiseRatio(s.sensitivity))) {
            noiseEstimator.observeRejected(rms)
            if (automaticMode) {
                automaticStringState = automaticStringTracker.submit(
                    tuning, emptyList(), false, false, 0.0, s.sensitivity.confidence,
                )
                _state.update { it.copy(activeString = automaticStringState.activeString?.number) }
            }
            val target = manualString ?: automaticStringTracker.activeString(tuning)
            pitchTracker.submit(emptyList(), target?.frequency)
            rejectFrame(target, waveform, emptyList(), rms, "noise_gate", 0, false, startedNanos)
            return
        }

        val onset = onsetDetector.observe(rms, noiseEstimator.noiseFloor)

        // In manual mode the target-guided detector runs independently of the
        // broad estimate. This is the key guard against E3 being rejected just
        // because the FFT/YIN broad path briefly reports its E2 sub-harmonic.
        val detectionTarget = manualString ?: previousAutomaticString
        val lockedPitch = detectionTarget?.let {
            detector.detectLocked(window, SAMPLE_RATE, it.frequency)
        }
        val broadCandidates = detector.detectCandidates(window, SAMPLE_RATE)
        val spectrum = detector.lastSpectrum?.toList() ?: emptyList()
        val lockedAccepted = lockedPitch != null &&
            lockedPitch.confidence >= s.sensitivity.confidence &&
            abs(detectionTarget.centsFrom(lockedPitch.frequency)) <= MANUAL_LOCK_CENTS

        if (automaticMode) {
            automaticStringState = automaticStringTracker.submit(
                tuning = tuning,
                broadCandidates = broadCandidates,
                lockedAccepted = lockedAccepted,
                onset = onset,
                signalToNoiseRatio = rms / noiseEstimator.noiseFloor.coerceAtLeast(1e-9),
                minimumVoicedProbability = s.sensitivity.confidence,
            )
            _state.update { it.copy(activeString = automaticStringState.activeString?.number) }
        }
        val lockedString = manualString ?: automaticStringTracker.activeString(tuning)
        val acceptedLockedPitch = lockedPitch?.takeIf {
            detectionTarget?.number == lockedString?.number && lockedAccepted
        }

        val candidates = buildList {
            addAll(broadCandidates.filter { candidate ->
                candidate.voicedProbability >= s.sensitivity.confidence &&
                    (manualString != null || mode != TunerMode.INSTRUMENT ||
                        lockedString?.let { abs(it.centsFrom(candidate.frequency)) <= AUTO_TARGET_CENTS } == true)
            })
            if (acceptedLockedPitch != null) {
                val confidence = acceptedLockedPitch.confidence
                add(PitchCandidate(
                    frequency = acceptedLockedPitch.frequency,
                    periodicity = confidence,
                    spectralQuality = 1.0,
                    probability = confidence * confidence * confidence,
                    voicedProbability = confidence,
                ))
            }
        }.sortedByDescending { it.probability }.fold(mutableListOf<PitchCandidate>()) { kept, candidate ->
            if (kept.none { candidateCentsDistance(it.frequency, candidate.frequency) < 25.0 }) kept += candidate
            kept
        }.take(MAX_TRACKER_CANDIDATES)

        if (candidates.isEmpty()) {
            noiseEstimator.observeRejected(rms)
            pitchTracker.submit(emptyList(), lockedString?.frequency, onset)
            rejectFrame(lockedString, waveform, spectrum, rms, "periodicity", 0, onset, startedNanos)
            return
        }

        missedFrames = 0
        val tracked = pitchTracker.submit(candidates, lockedString?.frequency, onset)
        if (tracked == null) {
            inTuneCueGate.observeInvalid()
            _state.update { it.copy(spectrum = spectrum, waveform = waveform) }
            return
        }
        val pitch = tracked.pitch
        if (pitch == null || pitch.confidence < s.sensitivity.confidence) {
            noiseEstimator.observeRejected(rms)
            rejectFrame(
                lockedString, waveform, spectrum, rms, "viterbi_unvoiced",
                candidates.size, onset, startedNanos,
            )
            return
        }
        val medianFreq = pitch.frequency
        val phase = if (lockedString != null &&
            abs(lockedString.centsFrom(medianFreq)) > MANUAL_LOCK_CENTS
        ) DetectionPhase.OUT_OF_RANGE else DetectionPhase.TRACKING
        val a4 = _state.value.referenceA4
        val observedNote = NoteMapper.noteFromFrequency(medianFreq, a4)

        // Instrument mode always reports deviation from a target in the active
        // tuning. Chromatic mode continues to use the nearest 12-TET note.
        val noteName: String
        val octave: Int
        val midi: Int
        val cents: Double
        val targetString = if (mode == TunerMode.INSTRUMENT) lockedString else null
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

        logFrame(
            pitch.frequency, pitch.confidence, true, rms, "viterbi",
            candidates.size, onset, startedNanos,
        )

        val cueTarget = if (mode == TunerMode.INSTRUMENT) {
            targetString?.let { "instrument:${s.instrumentId}:${s.tuningId}:${it.number}:${it.midi}" }
        } else {
            "chromatic:$midi"
        }
        val cueTriggered = inTuneCueGate.observe(
            target = cueTarget,
            cents = cents,
            tracking = phase == DetectionPhase.TRACKING,
        )
        val flashTick = _state.value.inTuneFlash + if (cueTriggered) 1 else 0
        if (cueTriggered) playInTuneCue()

        _state.update { st ->
            val activeString = when {
                st.mode != TunerMode.INSTRUMENT -> null
                st.selectedString != null -> st.selectedString
                else -> automaticStringTracker.activeString(tuning)?.number
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
                spectrum = spectrum, waveform = waveform,
                activeString = activeString,
                inTuneFlash = flashTick,
            )
        }
    }

    private fun rejectFrame(
        lockedString: InstrumentString?,
        waveform: List<Float>,
        spectrum: List<Float>,
        rms: Double,
        reason: String,
        candidateCount: Int,
        onset: Boolean,
        startedNanos: Long,
    ) {
        inTuneCueGate.observeInvalid()
        missedFrames++
        if (missedFrames >= HOLD_FRAMES) {
            pitchTracker.reset()
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
                waveform = waveform,
                activeString = if (it.mode == TunerMode.INSTRUMENT) it.selectedString else null,
            ) }
        } else {
            _state.update { it.copy(spectrum = spectrum, waveform = waveform) }
        }
        logFrame(null, 0.0, false, rms, reason, candidateCount, onset, startedNanos)
    }

    private fun candidateCentsDistance(a: Double, b: Double): Double =
        abs(1200.0 * ln(a / b) / ln(2.0))

    private fun signalToNoiseRatio(sensitivity: Sensitivity): Double = when (sensitivity) {
        Sensitivity.HIGH -> 2.0
        Sensitivity.MEDIUM -> 2.5
        Sensitivity.LOW -> 3.0
    }

    private fun logFrame(
        freq: Double?,
        confidence: Double,
        accepted: Boolean,
        rms: Double,
        reason: String,
        candidateCount: Int,
        onset: Boolean,
        startedNanos: Long,
    ) {
        frameCount++
        if (frameCount % LOG_EVERY != 0L) return
        Log.d(
            TAG,
            "f=${freq ?: "null"} conf=${"%.2f".format(confidence)} " +
                "rms=${"%.5f".format(rms)} noise=${"%.5f".format(noiseEstimator.noiseFloor)} " +
                "candidates=$candidateCount onset=$onset " +
                "processingMs=${"%.1f".format((System.nanoTime() - startedNanos) / 1_000_000.0)} " +
                "accepted=$accepted reason=$reason missed=$missedFrames " +
                "autoActive=${automaticStringState.activeString?.fullNote} " +
                "autoPending=${automaticStringState.pendingString?.fullNote} " +
                "autoFrames=${automaticStringState.confirmationFrames} " +
                "autoCents=${automaticStringState.candidateCents?.let { "%.1f".format(it) }} " +
                "autoGuard=${automaticStringState.onsetGuardFrames} " +
                "autoReason=${automaticStringState.reason} " +
                "cueArmed=${inTuneCueGate.state.armed} " +
                "cueCenter=${inTuneCueGate.state.centerFrames} " +
                "cueFar=${inTuneCueGate.state.farFrames}",
        )
    }

    private fun resetDetection() {
        pitchTracker.reset()
        onsetDetector.reset()
        noiseEstimator.reset()
        automaticStringTracker.reset()
        automaticStringState = AutomaticStringState(null, null, 0, null, 0, "reset")
        inTuneCueGate.reset()
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
        const val AUTO_TARGET_CENTS = 250.0
        const val TRACKER_LAG_FRAMES = 3
        const val MAX_TRACKER_CANDIDATES = 5
        const val LOG_EVERY = 10L     // throttle debug logging
    }
}
