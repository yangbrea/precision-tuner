package com.precisiontuner.tuning

import kotlin.math.abs
import kotlin.math.ln

/**
 * One string (open course) of an instrument, in a particular tuning.
 *
 * @param number    1..N, where 1 is the highest-pitched string.
 * @param noteName  display name without octave, e.g. "E".
 * @param fullNote  scientific name, e.g. "E4".
 * @param midi      MIDI note number of the target pitch.
 */
data class InstrumentString(
    val number: Int,
    val noteName: String,
    val fullNote: String,
    val midi: Int,
) {
    /** Equal-temperament frequency for the current A4 reference. */
    val frequency: Double get() = NoteMapper.frequencyFromMidi(midi)

    /** Signed deviation of [detectedFrequency] from this string's target. */
    fun centsFrom(detectedFrequency: Double): Double =
        1200.0 * ln(detectedFrequency / frequency) / ln(2.0)
}

/** A named tuning of an instrument: an ordered list of open-string notes. */
data class Tuning(
    val id: String,
    val name: String,
    val strings: List<InstrumentString>,
) {
    /** The string whose target note is closest to [midi], or null if empty. */
    fun nearestString(midi: Int): InstrumentString? =
        strings.minByOrNull { abs(it.midi - midi) }

    /** The target string nearest to [frequency] on a logarithmic pitch scale. */
    fun nearestString(frequency: Double): InstrumentString? {
        if (!frequency.isFinite() || frequency <= 0.0) return null
        return strings.minByOrNull { abs(ln(frequency / it.frequency)) }
    }

    fun byNumber(number: Int): InstrumentString? = strings.firstOrNull { it.number == number }
}

/** A stringed instrument with one or more tunings. */
data class Instrument(
    val id: String,
    val name: String,
    val defaultTuningId: String,
    val tunings: List<Tuning>,
) {
    fun defaultTuning(): Tuning =
        tunings.firstOrNull { it.id == defaultTuningId } ?: tunings.first()
}

/**
 * Built-in catalog of instruments and their tunings. Adding a new instrument is
 * a pure data change here.
 */
object InstrumentCatalog {

    val instruments: List<Instrument> = listOf(
        Instrument(
            id = "guitar",
            name = "吉他",
            defaultTuningId = "standard",
            tunings = listOf(
                Tuning("standard", "标准调弦", listOf(
                    s(1, "E", "E4", 64), s(2, "B", "B3", 59), s(3, "G", "G3", 55),
                    s(4, "D", "D3", 50), s(5, "A", "A2", 45), s(6, "E", "E2", 40),
                )),
                Tuning("drop_d", "Drop D", listOf(
                    s(1, "E", "E4", 64), s(2, "B", "B3", 59), s(3, "G", "G3", 55),
                    s(4, "D", "D3", 50), s(5, "A", "A2", 45), s(6, "D", "D2", 38),
                )),
                Tuning("half_down", "降半音", listOf(
                    s(1, "D#", "D#4", 63), s(2, "A#", "A#3", 58), s(3, "F#", "F#3", 54),
                    s(4, "C#", "C#3", 49), s(5, "G#", "G#2", 44), s(6, "D#", "D#2", 39),
                )),
            ),
        ),
        Instrument(
            id = "bass",
            name = "贝斯",
            defaultTuningId = "standard",
            tunings = listOf(
                Tuning("standard", "标准调弦", listOf(
                    s(1, "G", "G2", 43), s(2, "D", "D2", 38),
                    s(3, "A", "A1", 33), s(4, "E", "E1", 28),
                )),
                Tuning("drop_d", "Drop D", listOf(
                    s(1, "G", "G2", 43), s(2, "D", "D2", 38),
                    s(3, "A", "A1", 33), s(4, "D", "D1", 26),
                )),
            ),
        ),
        Instrument(
            id = "ukulele",
            name = "尤克里里",
            defaultTuningId = "standard",
            tunings = listOf(
                Tuning("standard", "标准 (High-G)", listOf(
                    s(1, "A", "A4", 69), s(2, "E", "E4", 64),
                    s(3, "C", "C4", 60), s(4, "G", "G4", 67),
                )),
                Tuning("low_g", "Low-G", listOf(
                    s(1, "A", "A4", 69), s(2, "E", "E4", 64),
                    s(3, "C", "C4", 60), s(4, "G", "G3", 55),
                )),
            ),
        ),
        Instrument(
            id = "violin",
            name = "小提琴",
            defaultTuningId = "standard",
            tunings = listOf(
                Tuning("standard", "标准调弦", listOf(
                    s(1, "E", "E5", 76), s(2, "A", "A4", 69),
                    s(3, "D", "D4", 62), s(4, "G", "G3", 55),
                )),
            ),
        ),
        Instrument(
            id = "viola",
            name = "中提琴",
            defaultTuningId = "standard",
            tunings = listOf(
                Tuning("standard", "标准调弦", listOf(
                    s(1, "A", "A4", 69), s(2, "D", "D4", 62),
                    s(3, "G", "G3", 55), s(4, "C", "C3", 48),
                )),
            ),
        ),
        Instrument(
            id = "cello",
            name = "大提琴",
            defaultTuningId = "standard",
            tunings = listOf(
                Tuning("standard", "标准调弦", listOf(
                    s(1, "A", "A3", 57), s(2, "D", "D3", 50),
                    s(3, "G", "G2", 43), s(4, "C", "C2", 36),
                )),
            ),
        ),
        Instrument(
            id = "double_bass",
            name = "低音提琴",
            defaultTuningId = "standard",
            tunings = listOf(
                Tuning("standard", "标准调弦", listOf(
                    s(1, "G", "G2", 43), s(2, "D", "D2", 38),
                    s(3, "A", "A1", 33), s(4, "E", "E1", 28),
                )),
            ),
        ),
        Instrument(
            id = "zhongruan",
            name = "中阮",
            defaultTuningId = "standard",
            tunings = listOf(
                Tuning("standard", "标准调弦 (G-D-G-D)", listOf(
                    s(1, "D", "D4", 62), s(2, "G", "G3", 55),
                    s(3, "D", "D3", 50), s(4, "G", "G2", 43),
                )),
            ),
        ),
        Instrument(
            id = "pipa",
            name = "琵琶",
            defaultTuningId = "standard",
            tunings = listOf(
                Tuning("standard", "标准调弦 (A-D-E-A)", listOf(
                    s(1, "A", "A3", 57), s(2, "E", "E3", 52),
                    s(3, "D", "D3", 50), s(4, "A", "A2", 45),
                )),
            ),
        ),
        Instrument(
            id = "erhu",
            name = "二胡",
            defaultTuningId = "standard",
            tunings = listOf(
                Tuning("standard", "标准调弦 (D-A)", listOf(
                    s(1, "A", "A4", 69), s(2, "D", "D4", 62),
                )),
            ),
        ),
        Instrument(
            id = "daruan",
            name = "大阮",
            defaultTuningId = "standard",
            tunings = listOf(
                Tuning("standard", "标准调弦 (D-A-D-A)", listOf(
                    s(1, "A", "A3", 57), s(2, "D", "D3", 50),
                    s(3, "A", "A2", 45), s(4, "D", "D2", 38),
                )),
            ),
        ),
    )

    fun instrument(id: String): Instrument? = instruments.firstOrNull { it.id == id }

    /** Resolve a tuning, falling back to the instrument's default. */
    fun tuning(instrumentId: String, tuningId: String): Tuning? {
        val inst = instrument(instrumentId) ?: return null
        return inst.tunings.firstOrNull { it.id == tuningId } ?: inst.defaultTuning()
    }

    private fun s(number: Int, noteName: String, fullNote: String, midi: Int) =
        InstrumentString(number, noteName, fullNote, midi)
}
