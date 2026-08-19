package com.precisiontuner

import com.precisiontuner.ear.Difficulty
import com.precisiontuner.ear.EarSettings
import com.precisiontuner.ear.ExerciseType
import com.precisiontuner.ear.QuestionGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuestionGeneratorTest {

    private val settings = EarSettings()

    private fun question(
        type: ExerciseType,
        difficulty: Difficulty = Difficulty.EASY,
        random: Random = Random.Default,
    ): com.precisiontuner.ear.QuizQuestion =
        QuestionGenerator.question(type, difficulty, settings, random)

    private fun optionsAreValid(question: com.precisiontuner.ear.QuizQuestion) {
        assertEquals(4, question.options.size)
        assertEquals(4, question.options.toSet().size)
        assertEquals(question.answerName, question.options[question.answerIndex])
    }

    @Test
    fun `same seed yields the same question`() {
        val a = question(ExerciseType.INTERVAL, random = Random(42))
        val b = question(ExerciseType.INTERVAL, random = Random(42))
        assertEquals(a, b)
    }

    // ---- 防重 ------------------------------------------------------------

    @Test
    fun `fingerprint distinguishes type answer and octave`() {
        val interval = question(ExerciseType.INTERVAL, random = Random(3))
        val note = question(ExerciseType.NOTE, random = Random(3))
        assertTrue(QuestionGenerator.fingerprint(interval) != QuestionGenerator.fingerprint(note))
        // The same midi name in different octaves is a different question.
        assertTrue(
            QuestionGenerator.fingerprint(
                com.precisiontuner.ear.QuizQuestion(
                    type = ExerciseType.NOTE, answerName = "C4", options = listOf("C4", "D4", "E4", "F4"),
                    answerIndex = 0, noteMidis = listOf(60),
                ),
            ) != QuestionGenerator.fingerprint(
                com.precisiontuner.ear.QuizQuestion(
                    type = ExerciseType.NOTE, answerName = "C5", options = listOf("C5", "D5", "E5", "F5"),
                    answerIndex = 0, noteMidis = listOf(72),
                ),
            )
        )
    }

    @Test
    fun `questionAvoiding never returns the avoided fingerprint`() {
        repeat(50) {
            val first = question(ExerciseType.INTERVAL, random = Random(it))
            val avoid = QuestionGenerator.fingerprint(first)
            repeat(100) { j ->
                val next = QuestionGenerator.questionAvoiding(
                    ExerciseType.INTERVAL, Difficulty.EASY, settings, avoid, Random(j * 31 + it),
                )
                assertTrue(QuestionGenerator.fingerprint(next) != avoid)
            }
        }
    }

    @Test
    fun `questionAvoiding without avoidance matches plain generation`() {
        repeat(20) {
            val a = QuestionGenerator.questionAvoiding(
                ExerciseType.NOTE, Difficulty.EASY, settings, null, Random(it),
            )
            val b = question(ExerciseType.NOTE, random = Random(it))
            assertEquals(a, b)
        }
    }

    @Test
    fun `a chain of questions never repeats an adjacent fingerprint`() {
        var previous: com.precisiontuner.ear.QuizQuestion? = null
        repeat(120) { i ->
            val next = QuestionGenerator.questionAvoiding(
                ExerciseType.CHORD, Difficulty.EASY, settings,
                previous?.let { QuestionGenerator.fingerprint(it) },
                Random(i * 7 + 1),
            )
            if (previous != null) {
                assertTrue(QuestionGenerator.fingerprint(next) != QuestionGenerator.fingerprint(previous))
            }
            previous = next
        }
    }

    // ---- 单音识别 --------------------------------------------------------

    @Test
    fun `note question plays a single note with valid options`() {
        repeat(100) {
            val q = question(ExerciseType.NOTE, random = Random(it))
            assertEquals(ExerciseType.NOTE, q.type)
            optionsAreValid(q)
            assertEquals(1, q.noteMidis.size)
            assertTrue(q.noteMidis[0] in DifficultyPresetsTest.notePoolAll())
        }
    }

    @Test
    fun `note answer name matches the played midi`() {
        repeat(50) {
            val q = question(ExerciseType.NOTE, random = Random(it))
            assertEquals(QuestionGenerator.midiName(q.noteMidis[0]), q.answerName)
        }
    }

    @Test
    fun `staff reading generates explicit notation with valid options`() {
        repeat(60) {
            val q = question(ExerciseType.STAFF_READING, random = Random(it))
            assertEquals(ExerciseType.STAFF_READING, q.type)
            optionsAreValid(q)
            assertEquals(1, q.noteMidis.size)
            assertEquals(q.staffNotation?.displayName, q.answerName)
            assertEquals(q.noteMidis[0], q.staffNotation?.midi)
            assertTrue(q.noteMidis[0] in com.precisiontuner.ear.DifficultyPresets.STAFF_EASY)
        }
    }

    @Test
    fun `staff difficulty controls range and accidental spelling`() {
        for (difficulty in Difficulty.entries) {
            repeat(120) {
                val q = question(ExerciseType.STAFF_READING, difficulty, Random(it))
                assertTrue(q.noteMidis.single() in com.precisiontuner.ear.DifficultyPresets.staffPool(difficulty))
                if (difficulty == Difficulty.EASY) {
                    assertEquals(com.precisiontuner.ear.Accidental.NATURAL, q.staffNotation?.accidental)
                }
            }
        }
        val spellings = (0 until 500).map {
            question(ExerciseType.STAFF_READING, Difficulty.HARD, Random(it)).staffNotation?.accidental
        }.toSet()
        assertTrue(com.precisiontuner.ear.Accidental.SHARP in spellings)
        assertTrue(com.precisiontuner.ear.Accidental.FLAT in spellings)
    }

    @Test
    fun `note pool grows with difficulty`() {
        val easy = question(ExerciseType.NOTE, difficulty = Difficulty.EASY, random = Random(1))
        val hard = question(ExerciseType.NOTE, difficulty = Difficulty.HARD, random = Random(1))
        assertTrue(easy.noteMidis[0] in com.precisiontuner.ear.DifficultyPresets.NOTE_EASY)
        assertTrue(hard.noteMidis[0] in com.precisiontuner.ear.DifficultyPresets.NOTE_HARD)
    }

    // ---- 音程 ------------------------------------------------------------

    @Test
    fun `interval question has four distinct options and the right notes`() {
        repeat(50) {
            val q = question(ExerciseType.INTERVAL, random = Random(it))
            assertEquals(ExerciseType.INTERVAL, q.type)
            optionsAreValid(q)
            assertEquals(2, q.noteMidis.size)
            assertTrue(q.noteMidis[0] in QuestionGenerator.ROOT_MIN_MIDI..QuestionGenerator.ROOT_MAX_MIDI)
            assertTrue(q.noteMidis[1] >= q.noteMidis[0])
            assertTrue(q.noteMidis[1] - q.noteMidis[0] <= 12)
            assertTrue(q.harmonic == !settings.melodicInterval)
        }
    }

    @Test
    fun `harmonic interval flag follows settings`() {
        val melodic = question(ExerciseType.INTERVAL, random = Random(7))
        val harmonic = QuestionGenerator.question(
            ExerciseType.INTERVAL, Difficulty.EASY, settings.copy(melodicInterval = false), Random(7),
        )
        assertTrue(!melodic.harmonic)
        assertTrue(harmonic.harmonic)
    }

    @Test
    fun `easy interval answers stay inside the basic set`() {
        val basicSemitones = com.precisiontuner.ear.IntervalLibrary.BASIC_SEMITONES
        repeat(30) {
            val q = question(ExerciseType.INTERVAL, difficulty = Difficulty.EASY, random = Random(it))
            val answerSemitones = q.noteMidis[1] - q.noteMidis[0]
            assertTrue(answerSemitones in basicSemitones)
        }
    }

    // ---- 和弦 ------------------------------------------------------------

    @Test
    fun `chord question plays root plus chord intervals`() {
        repeat(50) {
            val q = question(ExerciseType.CHORD, random = Random(it))
            assertEquals(ExerciseType.CHORD, q.type)
            optionsAreValid(q)
            assertTrue(q.noteMidis.size in 3..4)
            assertTrue(q.noteMidis[0] in QuestionGenerator.ROOT_MIN_MIDI..QuestionGenerator.ROOT_MAX_MIDI)
            // notes ascend from the root within one octave
            assertEquals(q.noteMidis.sorted(), q.noteMidis)
            assertTrue(q.noteMidis.last() - q.noteMidis.first() <= 11)
        }
    }

    @Test
    fun `hard chord answers can be sevenths`() {
        var sawSeventh = false
        repeat(60) {
            val q = question(ExerciseType.CHORD, difficulty = Difficulty.HARD, random = Random(it))
            if (q.noteMidis.size == 4) sawSeventh = true
        }
        assertTrue(sawSeventh)
    }

    // ---- 音阶 ------------------------------------------------------------

    @Test
    fun `scale question plays an octave`() {
        repeat(50) {
            val q = question(ExerciseType.SCALE, random = Random(it))
            assertEquals(ExerciseType.SCALE, q.type)
            optionsAreValid(q)
            assertEquals(8, q.noteMidis.size)
            assertEquals(12, q.noteMidis.max() - q.noteMidis.min())
            assertTrue(q.noteMidis.min() in QuestionGenerator.ROOT_MIN_MIDI..QuestionGenerator.ROOT_MAX_MIDI)
        }
    }

    @Test
    fun `medium scales include church modes`() {
        repeat(30) {
            val q = question(ExerciseType.SCALE, difficulty = Difficulty.MEDIUM, random = Random(it))
            assertTrue(
                com.precisiontuner.ear.ScaleLibrary.ALL.any { it.name == q.answerName },
            )
        }
    }

    @Test
    fun `hard scales may play descending`() {
        var sawDescending = false
        repeat(80) {
            val q = question(ExerciseType.SCALE, difficulty = Difficulty.HARD, random = Random(it))
            if (q.noteMidis != q.noteMidis.sorted()) sawDescending = true
        }
        assertTrue(sawDescending)
    }
}
