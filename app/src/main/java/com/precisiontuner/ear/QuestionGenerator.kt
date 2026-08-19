package com.precisiontuner.ear

import com.precisiontuner.tuning.NoteMapper
import java.lang.Math.floorMod
import kotlin.random.Random

/**
 * Generates recognition questions: the correct answer plus three distinct
 * distractors, all shuffled, plus the MIDI notes to play.
 *
 * Pools come from [DifficultyPresets] under [EarSettings.difficulty]. Pure and
 * seedable ([Random] injected) so tests are deterministic.
 */
object QuestionGenerator {

    /** Comfortable listening range for roots: C3(48) .. C5(72). */
    const val ROOT_MIN_MIDI = 48
    const val ROOT_MAX_MIDI = 72

    const val OPTION_COUNT = 4

    /** Retry budget for [questionAvoiding]; pools are ≥ 4 so collisions are rare. */
    const val MAX_RETRY = 20

    /** Generates a question of [type] under [difficulty] and [settings]. */
    fun question(
        type: ExerciseType,
        difficulty: Difficulty,
        settings: EarSettings,
        random: Random = Random.Default,
    ): QuizQuestion {
        return when (type) {
            ExerciseType.NOTE -> noteQuestion(difficulty, settings, random, ExerciseType.NOTE)
            ExerciseType.INTERVAL -> intervalQuestion(difficulty, settings, random)
            ExerciseType.CHORD -> chordQuestion(difficulty, settings, random)
            ExerciseType.SCALE -> scaleQuestion(difficulty, settings, random)
            ExerciseType.STAFF_READING -> staffQuestion(difficulty, random)
            ExerciseType.RHYTHM -> rhythmQuestion(difficulty, random)
        }
    }

    /**
     * Identity of a question for repeat avoidance: exercise + answer label.
     * Note names carry the octave, so "C4" and "C5" count as different.
     */
    fun fingerprint(question: QuizQuestion): String = "${question.type}:${question.answerName}"

    /**
     * Like [question], but retries until the fingerprint differs from
     * [avoidFingerprint] (pass null for a fresh session with no previous
     * question). Falls back after [MAX_RETRY] attempts so it always returns.
     */
    fun questionAvoiding(
        type: ExerciseType,
        difficulty: Difficulty,
        settings: EarSettings,
        avoidFingerprint: String?,
        random: Random = Random.Default,
    ): QuizQuestion {
        repeat(MAX_RETRY) {
            val candidate = question(type, difficulty, settings, random)
            if (avoidFingerprint == null || fingerprint(candidate) != avoidFingerprint) {
                return candidate
            }
        }
        return question(type, difficulty, settings, random)
    }

    /** 单音/读谱识别: a single note, answered by note name ("C4", "F#4", …). */
    private fun noteQuestion(
        difficulty: Difficulty,
        settings: EarSettings,
        random: Random,
        type: ExerciseType,
    ): QuizQuestion {
        val pool = DifficultyPresets.notePool(difficulty)
        require(pool.size >= OPTION_COUNT) { "note pool too small: ${pool.size}" }
        val answer = pool[random.nextInt(pool.size)]
        val answerName = midiName(answer)
        val options = shuffledOptions(
            answerName,
            pool.filter { it != answer }.map { midiName(it) },
            random,
        )
        return QuizQuestion(
            type = type,
            answerName = answerName,
            options = options.first,
            answerIndex = options.second,
            noteMidis = listOf(answer),
        )
    }

    /** Visual-reading question with explicit enharmonic spelling and clef. */
    private fun staffQuestion(difficulty: Difficulty, random: Random): QuizQuestion {
        val pool = DifficultyPresets.staffPool(difficulty)
        require(pool.size >= OPTION_COUNT) { "staff pool too small: ${pool.size}" }
        val answerMidi = pool[random.nextInt(pool.size)]
        val answer = StaffNotation.fromMidi(answerMidi, preferFlat = random.nextBoolean())
        val distractors = pool
            .filter { it != answerMidi }
            .shuffled(random)
            .map { StaffNotation.fromMidi(it, preferFlat = random.nextBoolean()).displayName }
            .distinct()
        val options = shuffledOptions(answer.displayName, distractors, random)
        return QuizQuestion(
            type = ExerciseType.STAFF_READING,
            answerName = answer.displayName,
            options = options.first,
            answerIndex = options.second,
            noteMidis = listOf(answerMidi),
            staffNotation = answer,
        )
    }

    /**
     * Rhythm dictation: the pattern itself is the question. No options — the
     * user taps the rhythm and [com.precisiontuner.ear.RhythmScorer] judges it,
     * so [QuizQuestion.options] stays empty and [answerIndex] is unused (-1).
     */
    private fun rhythmQuestion(difficulty: Difficulty, random: Random): QuizQuestion {
        val pool = DifficultyPresets.rhythmPatterns(difficulty)
        require(pool.isNotEmpty()) { "rhythm pool empty for $difficulty" }
        val answer = pool[random.nextInt(pool.size)]
        return QuizQuestion(
            type = ExerciseType.RHYTHM,
            answerName = answer.name,
            options = emptyList(),
            answerIndex = -1,
            noteMidis = emptyList(),
            rhythmPattern = answer,
        )
    }

    private fun intervalQuestion(difficulty: Difficulty, settings: EarSettings, random: Random): QuizQuestion {
        val active = DifficultyPresets.intervalSemitones(difficulty)
        require(active.size >= OPTION_COUNT) { "interval pool too small: ${active.size}" }
        val candidates = active.sorted()
        val answer = candidates[random.nextInt(candidates.size)]
        val answerName = IntervalLibrary.bySemitone(answer).name
        val options = shuffledOptions(
            answerName,
            candidates.filter { it != answer }.map { IntervalLibrary.bySemitone(it).name },
            random,
        )
        val root = random.nextInt(ROOT_MIN_MIDI, ROOT_MAX_MIDI + 1)
        return QuizQuestion(
            type = ExerciseType.INTERVAL,
            answerName = answerName,
            options = options.first,
            answerIndex = options.second,
            noteMidis = listOf(root, root + answer),
            harmonic = !settings.melodicInterval,
        )
    }

    private fun chordQuestion(difficulty: Difficulty, settings: EarSettings, random: Random): QuizQuestion {
        val active = DifficultyPresets.chords(difficulty)
        require(active.size >= OPTION_COUNT) { "chord pool too small: ${active.size}" }
        val answer = active[random.nextInt(active.size)]
        val options = shuffledOptions(
            answer.name,
            active.filter { it.name != answer.name }.map { it.name },
            random,
        )
        val root = random.nextInt(ROOT_MIN_MIDI, ROOT_MAX_MIDI + 1)
        return QuizQuestion(
            type = ExerciseType.CHORD,
            answerName = answer.name,
            options = options.first,
            answerIndex = options.second,
            noteMidis = answer.intervals.map { root + it },
        )
    }

    private fun scaleQuestion(difficulty: Difficulty, settings: EarSettings, random: Random): QuizQuestion {
        val active = DifficultyPresets.scales(difficulty)
        require(active.size >= OPTION_COUNT) { "scale pool too small: ${active.size}" }
        val answer = active[random.nextInt(active.size)]
        val options = shuffledOptions(
            answer.name,
            active.filter { it.name != answer.name }.map { it.name },
            random,
        )
        val root = random.nextInt(ROOT_MIN_MIDI, ROOT_MAX_MIDI + 1)
        var notes = answer.intervals.map { root + it }
        // HARD difficulty may play the scale descending.
        if (difficulty == Difficulty.HARD && random.nextBoolean()) {
            notes = notes.reversed()
        }
        return QuizQuestion(
            type = ExerciseType.SCALE,
            answerName = answer.name,
            options = options.first,
            answerIndex = options.second,
            noteMidis = notes,
        )
    }

    /** Note name with octave, e.g. "C4", "F#4". */
    fun midiName(midi: Int): String =
        NoteMapper.NOTE_NAMES[floorMod(midi, 12)] + (midi / 12 - 1)

    /**
     * Builds [OPTION_COUNT] shuffled labels: the correct one plus [distractors]
     * (which must be distinct). Returns the labels and the correct index.
     */
    private fun shuffledOptions(
        correct: String,
        distractors: List<String>,
        random: Random,
    ): Pair<List<String>, Int> {
        require(distractors.size >= OPTION_COUNT - 1) {
            "not enough distinct distractors: ${distractors.size}"
        }
        val picked = distractors.shuffled(random).take(OPTION_COUNT - 1)
        val labels = (listOf(correct) + picked).shuffled(random)
        return labels to labels.indexOf(correct)
    }
}
