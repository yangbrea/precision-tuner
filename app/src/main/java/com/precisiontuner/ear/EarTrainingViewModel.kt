package com.precisiontuner.ear

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.precisiontuner.audio.CueSoundPlayer
import com.precisiontuner.audio.ErrorSoundPlayer
import com.precisiontuner.audio.PianoEngineShare
import com.precisiontuner.audio.PianoReferenceEngine
import com.precisiontuner.audio.TapSoundEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Owns the 视听练耳 quiz module: one session per exercise, question playback
 * through the shared piano engine, and persisted [EarSettings].
 *
 * Playback is scheduled with a cancellable coroutine (note durations only —
 * drift does not matter here), mirroring the metronome's job-based engine.
 */
class EarTrainingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val piano: PianoReferenceEngine = PianoEngineShare.acquire(application)
    private val cueSound = CueSoundPlayer(application)
    private val errorSound = ErrorSoundPlayer(application)
    // Low-latency piano C4 for rhythm-tap feedback (SoundPool play() lags).
    private val tapSound = TapSoundEngine(application)
    private val random = Random(System.nanoTime())

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<EarSettings> = _settings.asStateFlow()

    /** Per-exercise difficulty (independent per exercise, persisted). */
    private val _difficulties = MutableStateFlow(loadDifficulties())
    val difficulties: StateFlow<Map<ExerciseType, Difficulty>> = _difficulties.asStateFlow()

    /** True while the exercise menu is shown (root of the ear-training section). */
    private val _atMenu = MutableStateFlow(true)
    val atMenu: StateFlow<Boolean> = _atMenu.asStateFlow()

    private val _activeExercise = MutableStateFlow(ExerciseType.NOTE)
    val activeExercise: StateFlow<ExerciseType> = _activeExercise.asStateFlow()

    private val sessions = mutableMapOf<ExerciseType, EarSessionState>().apply {
        ExerciseType.entries.forEach { put(it, EarSessionState()) }
    }

    private val _currentSession = MutableStateFlow(EarSessionState())
    val currentSession: StateFlow<EarSessionState> = _currentSession.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var playJob: Job? = null

    init {
        refreshCurrentSession()
    }

    // ---- navigation ------------------------------------------------------

    /** Enters an exercise from the menu: resets its session to a fresh setup. */
    fun selectExercise(type: ExerciseType) {
        stopPlayback()
        _atMenu.value = false
        _activeExercise.value = type
        sessions[type] = EarSessionState()
        refreshCurrentSession()
    }

    /** Returns to the exercise menu. */
    fun backToMenu() {
        stopPlayback()
        _atMenu.value = true
    }

    // ---- session control -------------------------------------------------

    fun startSession(mode: PracticeMode) {
        stopPlayback()
        val type = _activeExercise.value
        val s = _settings.value
        val difficulty = difficultyFor(type)
        sessions[type] = EarSessionLogic.start(
            mode = mode,
            question = QuestionGenerator.questionAvoiding(
                type, difficulty, s, avoidFingerprint = null, random,
            ),
            questionLimit = s.testQuestionCount,
        )
        refreshCurrentSession()
        // Rhythm dictation is visual and self-paced: the reference plays only
        // when the user taps the play button, never automatically.
        if (type != ExerciseType.RHYTHM) playCurrentQuestion()
    }

    fun replay() = playCurrentQuestion()

    fun nextQuestion() {
        stopPlayback()
        val type = _activeExercise.value
        val session = sessions.getValue(type)
        sessions[type] = EarSessionLogic.nextQuestion(
            session,
            QuestionGenerator.questionAvoiding(
                type,
                difficultyFor(type),
                _settings.value,
                avoidFingerprint = session.question?.let { QuestionGenerator.fingerprint(it) },
                random,
            ),
        )
        refreshCurrentSession()
        if (type != ExerciseType.RHYTHM) playCurrentQuestion()
    }

    fun selectAnswer(index: Int) {
        val type = _activeExercise.value
        val updated = EarSessionLogic.answer(sessions.getValue(type), index)
        sessions[type] = updated
        refreshCurrentSession()
        playAnswerFeedback(updated)
    }

    /**
     * Submits the tapped rhythm times (ms relative to the first tap) for the
     * current RHYTHM question; the scorer decides correctness and the detail
     * line is shown on the feedback banner.
     */
    fun submitRhythm(taps: List<Long>) {
        val type = _activeExercise.value
        if (type != ExerciseType.RHYTHM) return
        val session = sessions.getValue(type)
        val pattern = session.question?.rhythmPattern ?: return
        val scored = RhythmScorer.score(pattern, taps)
        val detail = "节奏准确度 ${scored.score}%（${scored.tapped}/${scored.expected} 音）"
        val updated = EarSessionLogic.answerScored(session, scored.correct, detail)
        sessions[type] = updated
        refreshCurrentSession()
        playAnswerFeedback(updated)
    }

    /** Plays the correct/wrong feedback cue when this answer settles it. */
    private fun playAnswerFeedback(updated: EarSessionState) {
        val correct = updated.isCorrect ?: return
        if (correct) cueSound.play() else errorSound.play()
    }

    /** Ends an endless session manually and shows the result. */
    fun endSession() {
        val type = _activeExercise.value
        sessions[type] = EarSessionLogic.end(sessions.getValue(type))
        stopPlayback()
        refreshCurrentSession()
    }

    fun restartSession() {
        val type = _activeExercise.value
        startSession(sessions.getValue(type).mode)
    }

    fun backToSetup() {
        stopPlayback()
        val type = _activeExercise.value
        sessions[type] = EarSessionLogic.backToSetup(sessions.getValue(type))
        refreshCurrentSession()
    }

    // ---- settings --------------------------------------------------------

    /** Sets the difficulty of the currently open exercise. */
    fun setDifficulty(difficulty: Difficulty) {
        val type = _activeExercise.value
        _difficulties.update { it + (type to difficulty) }
        persistDifficulty(type, difficulty)
    }

    fun setMelodicInterval(melodic: Boolean) = updateSetting { it.copy(melodicInterval = melodic) }

    fun setNoteReferenceTone(enabled: Boolean) = updateSetting { it.copy(noteReferenceTone = enabled) }

    fun setTestQuestionCount(n: Int) = updateSetting {
        it.copy(testQuestionCount = n.coerceIn(MIN_TEST_QUESTIONS, MAX_TEST_QUESTIONS))
    }

    private fun updateSetting(transform: (EarSettings) -> EarSettings) {
        _settings.update(transform)
        persist()
    }

    // ---- playback --------------------------------------------------------

    private fun playCurrentQuestion() {
        stopPlayback()
        val question = _currentSession.value.question ?: return
        // Staff-reading questions are visual only — nothing to play.
        if (question.type == ExerciseType.STAFF_READING) return
        _isPlaying.value = true
        playJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                when {
                    // Rhythm: two pick-up clicks, then the pattern's click track.
                    question.type == ExerciseType.RHYTHM -> {
                        val pattern = question.rhythmPattern ?: return@launch
                        playRhythmReference(pattern)
                    }
                    // Single note: one strike (or a C4 reference first when enabled).
                    question.type == ExerciseType.NOTE -> {
                        if (_settings.value.noteReferenceTone) {
                            piano.playMidi(REFERENCE_C4_MIDI, MELODIC_VOLUME)
                            delay(REFERENCE_HOLD_MS)
                            delay(REFERENCE_GAP_MS)
                        }
                        piano.playMidi(question.noteMidis[0], MELODIC_VOLUME)
                        delay(INTERVAL_TAIL_MS)
                    }
                    // Melodic interval: two notes in sequence.
                    question.type == ExerciseType.INTERVAL && !question.harmonic -> {
                        piano.playMidi(question.noteMidis[0], MELODIC_VOLUME)
                        delay(MELODIC_GAP_MS)
                        piano.playMidi(question.noteMidis[1], MELODIC_VOLUME)
                        delay(INTERVAL_TAIL_MS)
                    }
                    // Harmonic interval or chord: all notes at once.
                    question.type == ExerciseType.CHORD || question.harmonic -> {
                        piano.playChord(question.noteMidis)
                        delay(CHORD_HOLD_MS)
                    }
                    // Scale: notes in sequence (ascending, or descending on HARD).
                    else -> {
                        question.noteMidis.forEachIndexed { i, midi ->
                            piano.playMidi(midi, MELODIC_VOLUME)
                            delay(if (i == question.noteMidis.lastIndex) SCALE_TAIL_MS else SCALE_STEP_MS)
                        }
                    }
                }
            } finally {
                _isPlaying.value = false
            }
        }
    }

    /**
     * Plays the reference rhythm on a single piano C4: one strike per audible
     * note, timed by the 1/12-beat grid. No clicks, no pick-up beats.
     */
    private suspend fun playRhythmReference(pattern: RhythmPattern) {
        val beatMs = (60000.0 / RHYTHM_BPM).toLong()
        pattern.notes.forEach { note ->
            if (!note.isRest) {
                piano.playMidi(RHYTHM_TONE_MIDI, RHYTHM_TONE_VOLUME)
            }
            delay((note.grids * beatMs / 12.0).toLong())
        }
        delay(RHYTHM_TAIL_MS)
    }

    /** Plays the piano C4 tap feedback while the user reproduces a rhythm. */
    fun playTapSound() {
        tapSound.start()
        tapSound.play(RHYTHM_TAP_VOLUME)
    }

    private fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        piano.stop()
        _isPlaying.value = false
    }

    /** Stops playback when the app is backgrounded or the tab is left. */
    fun onPause() = stopPlayback()

    override fun onCleared() {
        stopPlayback()
        tapSound.stop()
        cueSound.close()
        errorSound.close()
        PianoEngineShare.release(piano)
        super.onCleared()
    }

    // ---- persistence -----------------------------------------------------

    private fun persist() {
        val s = _settings.value
        prefs.edit()
            .putBoolean(KEY_MELODIC, s.melodicInterval)
            .putBoolean(KEY_NOTE_REFERENCE, s.noteReferenceTone)
            .putInt(KEY_TEST_COUNT, s.testQuestionCount)
            .apply()
    }

    private fun loadSettings(): EarSettings = EarSettings(
        melodicInterval = prefs.getBoolean(KEY_MELODIC, true),
        noteReferenceTone = prefs.getBoolean(KEY_NOTE_REFERENCE, false),
        testQuestionCount = prefs.getInt(KEY_TEST_COUNT, 10)
            .coerceIn(MIN_TEST_QUESTIONS, MAX_TEST_QUESTIONS),
    )

    private fun loadDifficulties(): Map<ExerciseType, Difficulty> =
        ExerciseType.entries.associateWith { type ->
            enumValueOfSafe(KEY_DIFFICULTY_PREFIX + type.name, Difficulty.EASY)
        }

    private fun persistDifficulty(type: ExerciseType, difficulty: Difficulty) {
        prefs.edit().putString(KEY_DIFFICULTY_PREFIX + type.name, difficulty.name).apply()
    }

    private fun difficultyFor(type: ExerciseType): Difficulty =
        _difficulties.value[type] ?: Difficulty.EASY

    private inline fun <reified T : Enum<T>> enumValueOfSafe(key: String, default: T): T {
        val name = prefs.getString(key, null) ?: return default
        return runCatching { enumValueOf<T>(name) }.getOrDefault(default)
    }

    private fun refreshCurrentSession() {
        _currentSession.value = sessions.getValue(_activeExercise.value)
    }

    private companion object {
        const val PREFS_NAME = "ear_training_prefs"
        const val KEY_DIFFICULTY_PREFIX = "difficulty_"
        const val KEY_MELODIC = "melodicInterval"
        const val KEY_NOTE_REFERENCE = "noteReferenceTone"
        const val KEY_TEST_COUNT = "testQuestionCount"
        const val MIN_TEST_QUESTIONS = 5
        const val MAX_TEST_QUESTIONS = 20

        // Single-note reference tone (C4) played before the target when enabled.
        const val REFERENCE_C4_MIDI = 60
        const val REFERENCE_HOLD_MS = 500L   // how long the reference rings
        const val REFERENCE_GAP_MS = 200L    // silence between reference and target

        // Playback timings (ms). A uniform latency is irrelevant here — the
        // user just listens; only relative spacing matters.
        const val MELODIC_GAP_MS = 150L    // silence between interval notes
        const val INTERVAL_TAIL_MS = 800L  // let the last note ring
        const val CHORD_HOLD_MS = 900L
        const val SCALE_STEP_MS = 350L     // per scale note
        const val SCALE_TAIL_MS = 600L     // final scale note
        const val MELODIC_VOLUME = 0.9f

        // Rhythm reference playback: a single piano C4 for every audible note.
        const val RHYTHM_BPM = 90          // comfortable tapping tempo
        const val RHYTHM_TONE_MIDI = 60    // C4
        const val RHYTHM_TONE_VOLUME = 0.8f
        const val RHYTHM_TAP_VOLUME = 0.7f // tap feedback, slightly quieter
        const val RHYTHM_TAIL_MS = 250L    // silence after the last note
    }
}
