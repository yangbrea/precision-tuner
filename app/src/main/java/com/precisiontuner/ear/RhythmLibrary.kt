package com.precisiontuner.ear

/**
 * Preset rhythm-pattern pools per difficulty for the 节奏听写 exercise.
 *
 * Durations use the 1/12-beat grid of [RhythmPattern]: quarter = 12, eighth
 * = 6, sixteenth = 3, triplet = 4, dotted values = 18/9/36, half = 24,
 * whole = 48. Every pattern sums to its time-signature bar (48×a/b grid
 * units), has at least 3 audible notes (≥2 inter-onset intervals) and —
 * crucially — contains a length contrast (≥2 distinct intervals), so no
 * pattern reduces to "tap evenly N times". Pools keep plenty of entries for
 * repeat-avoidance.
 */
object RhythmLibrary {

    // ---- 简单: quarters, halves, eighths, simple rests, basic dots --------

    /** 二分与四分 — half then two quarters. */
    val HALF_QUARTER = RhythmPattern("二分与四分", notes = listOf(n(24), n(12), n(12)))

    /** 四分与八分 — quarter, two eighths, quarter, quarter. */
    val QUARTER_EIGHTH = RhythmPattern(
        "四分与八分",
        notes = listOf(n(12), n(6), n(6), n(12), n(12)),
    )

    /** 附点二分与八分 — dotted half then two eighths. */
    val DOTTED_HALF_EIGHTH = RhythmPattern(
        "附点二分与八分",
        notes = listOf(n(36), n(6), n(6)),
    )

    /** 带四分休止 — quarter, quarter rest, quarter, quarter. */
    val QUARTER_REST = RhythmPattern(
        "带四分休止",
        notes = listOf(n(12), n(12, isRest = true), n(12), n(12)),
    )

    /** 八分与四分 — two eighths, quarter, two eighths, quarter. */
    val EIGHTH_QUARTER = RhythmPattern(
        "八分与四分",
        notes = listOf(n(6), n(6), n(12), n(6), n(6), n(12)),
    )

    /** 带二分休止 — eighth, half rest, eighth, quarter. */
    val HALF_REST = RhythmPattern(
        "带二分休止",
        notes = listOf(n(6), n(24, isRest = true), n(6), n(12)),
    )

    /** 八分与二分 — three eighths, half, eighth. */
    val EIGHTH_HALF = RhythmPattern(
        "八分与二分",
        notes = listOf(n(6), n(6), n(6), n(24), n(6)),
    )

    /** 四分与附点 — two quarters, dotted quarter + eighth. */
    val QUARTER_DOTTED = RhythmPattern(
        "四分与附点",
        notes = listOf(n(12), n(12), n(18), n(6)),
    )

    /** 附点与二分 — dotted quarter + eighth, then a half. */
    val DOTTED_HALF = RhythmPattern(
        "附点与二分",
        notes = listOf(n(18), n(6), n(24)),
    )

    /** 四分与二分 — quarter, two eighths, half. */
    val QUARTER_HALF_EIGHTH = RhythmPattern(
        "四分与二分",
        notes = listOf(n(12), n(6), n(6), n(24)),
    )

    /** 两拍四分与八分 — quarter, two eighths in 2/4. */
    val TWO_FOUR_QUARTER_EIGHTH = RhythmPattern(
        "两拍四分与八分",
        beatsPerBar = 2,
        notes = listOf(n(12), n(6), n(6)),
    )

    /** 两拍八分与四分 — eighth, quarter, eighth in 2/4. */
    val TWO_FOUR_EIGHTH_QUARTER = RhythmPattern(
        "两拍八分与四分",
        beatsPerBar = 2,
        notes = listOf(n(6), n(12), n(6)),
    )

    val EASY: List<RhythmPattern> = listOf(
        HALF_QUARTER, QUARTER_EIGHTH, DOTTED_HALF_EIGHTH, QUARTER_REST,
        EIGHTH_QUARTER, HALF_REST, EIGHTH_HALF, QUARTER_DOTTED, DOTTED_HALF,
        QUARTER_HALF_EIGHTH, TWO_FOUR_QUARTER_EIGHTH, TWO_FOUR_EIGHTH_QUARTER,
    )

    // ---- 中等: dotted eighths, syncopation, sixteenths, triplets ---------

    /** 附点节奏 — dotted quarter + eighth, then two quarters. */
    val DOTTED_EIGHTH = RhythmPattern(
        "附点节奏",
        notes = listOf(n(18), n(6), n(12), n(12)),
    )

    /** 切分节奏 — eighth, quarter, eighth, quarter, quarter. */
    val SYNCOPATED = RhythmPattern(
        "切分节奏",
        notes = listOf(n(6), n(12), n(6), n(12), n(12)),
    )

    /** 前八后十六 — eighth, two sixteenths, three quarters. */
    val EIGHTH_TWO_SIXTEENTHS = RhythmPattern(
        "前八后十六",
        notes = listOf(n(6), n(3), n(3), n(12), n(12), n(12)),
    )

    /** 前十六后八 — two sixteenths, eighth, three quarters. */
    val TWO_SIXTEENTHS_EIGHTH = RhythmPattern(
        "前十六后八",
        notes = listOf(n(3), n(3), n(6), n(12), n(12), n(12)),
    )

    /** 带八分休止 — eighth rest inside an eighth run. */
    val EIGHTH_REST = RhythmPattern(
        "带八分休止",
        notes = listOf(n(6), n(6, isRest = true), n(6), n(6), n(6), n(6), n(6), n(6)),
    )

    /** 附点八分与四分 — dotted eighth + sixteenth, then three quarters. */
    val DOTTED_SIXTEENTH = RhythmPattern(
        "附点八分与四分",
        notes = listOf(n(9), n(3), n(12), n(12), n(12)),
    )

    /** 切分与八分 — syncopation ending in an eighth run. */
    val SYNCOPATED_EIGHTH = RhythmPattern(
        "切分与八分",
        notes = listOf(n(6), n(12), n(6), n(6), n(6), n(12)),
    )

    /** 十六分与切分 — sixteenth pick-up into a syncopation. */
    val SIXTEENTH_SYNCOPATED = RhythmPattern(
        "十六分与切分",
        notes = listOf(n(3), n(3), n(6), n(12), n(6), n(12), n(6)),
    )

    /** 三连音与四分 — triplet group then three quarters. */
    val TRIPLET_QUARTER = RhythmPattern(
        "三连音与四分",
        notes = listOf(n(4), n(4), n(4), n(12), n(12), n(12)),
    )

    /** 带休止的切分 — syncopation framed by an eighth rest. */
    val SYNCOPATED_REST = RhythmPattern(
        "带休止的切分",
        notes = listOf(n(6), n(6, isRest = true), n(12), n(6), n(6), n(12)),
    )

    /** 附点与八分 — dotted quarter + eighth, then an eighth run. */
    val DOTTED_EIGHTH_RUN = RhythmPattern(
        "附点与八分",
        notes = listOf(n(18), n(6), n(6), n(6), n(6), n(6)),
    )

    /** 切分与附点 — syncopation into a dotted quarter + eighth. */
    val SYNCOPATED_DOTTED = RhythmPattern(
        "切分与附点",
        notes = listOf(n(6), n(18), n(6), n(6), n(12)),
    )

    /** 十六分组合 — sixteenth pick-ups in both directions. */
    val SIXTEENTH_COMBO = RhythmPattern(
        "十六分组合",
        notes = listOf(n(3), n(3), n(6), n(3), n(3), n(6), n(12), n(12)),
    )

    /** 八分与三连音 — eighths framing a triplet group. */
    val EIGHTH_TRIPLET = RhythmPattern(
        "八分与三连音",
        notes = listOf(n(6), n(4), n(4), n(4), n(6), n(12), n(12)),
    )

    /** 附点八分与切分 — dotted-eighth pick-up into a syncopation. */
    val DOTTED_SIXTEENTH_SYNCOPATED = RhythmPattern(
        "附点八分与切分",
        notes = listOf(n(9), n(3), n(6), n(12), n(6), n(12)),
    )

    /** 三拍附点 — dotted quarter + eighth, quarter in 3/4. */
    val THREE_FOUR_DOTTED = RhythmPattern(
        "三拍附点",
        beatsPerBar = 3,
        notes = listOf(n(18), n(6), n(12)),
    )

    /** 三拍切分 — syncopation in 3/4. */
    val THREE_FOUR_SYNCOPATED = RhythmPattern(
        "三拍切分",
        beatsPerBar = 3,
        notes = listOf(n(6), n(12), n(6), n(12)),
    )

    /** 三拍附点与八分 — dotted quarter + eighth, two eighths in 3/4. */
    val THREE_FOUR_DOTTED_EIGHTH = RhythmPattern(
        "三拍附点与八分",
        beatsPerBar = 3,
        notes = listOf(n(18), n(6), n(6), n(6)),
    )

    /** 六八四分与八分 — quarter + eighth pairs in 6/8. */
    val SIX_EIGHT_QUARTER_EIGHTH = RhythmPattern(
        "六八四分与八分",
        beatsPerBar = 6,
        beatUnit = 8,
        notes = listOf(n(12), n(6), n(12), n(6)),
    )

    val MEDIUM: List<RhythmPattern> = listOf(
        DOTTED_EIGHTH, SYNCOPATED, EIGHTH_TWO_SIXTEENTHS,
        TWO_SIXTEENTHS_EIGHTH, EIGHTH_REST, DOTTED_SIXTEENTH,
        SYNCOPATED_EIGHTH, SIXTEENTH_SYNCOPATED, TRIPLET_QUARTER,
        SYNCOPATED_REST, DOTTED_EIGHTH_RUN, SYNCOPATED_DOTTED,
        SIXTEENTH_COMBO, EIGHTH_TRIPLET, DOTTED_SIXTEENTH_SYNCOPATED,
        THREE_FOUR_DOTTED, THREE_FOUR_SYNCOPATED, THREE_FOUR_DOTTED_EIGHTH,
        SIX_EIGHT_QUARTER_EIGHTH,
    )

    // ---- 困难: sixteenth runs, complex syncopation, mixed rests ----------

    /** 切分组合 — eighth, quarter, eighth, dotted quarter, eighth. */
    val SYNCOPATED_COMBO = RhythmPattern(
        "切分组合",
        notes = listOf(n(6), n(12), n(6), n(18), n(6)),
    )

    /** 十六分四连 — four sixteenths then three quarters. */
    val SIXTEENTH_RUN = RhythmPattern(
        "十六分四连",
        notes = listOf(n(3), n(3), n(3), n(3), n(12), n(12), n(12)),
    )

    /** 三连音接八分 — triplet pair, two eighths, two quarters. */
    val TRIPLET_EIGHTH = RhythmPattern(
        "三连音接八分",
        notes = listOf(n(4), n(4), n(4), n(6), n(6), n(12), n(12)),
    )

    /** 附点切分 — dotted quarter + eighth, two eighths, quarter. */
    val DOTTED_SYNCOPATION = RhythmPattern(
        "附点切分",
        notes = listOf(n(18), n(6), n(6), n(6), n(12)),
    )

    /** 复杂休止 — quarter, quarter rest, eighth, eighth rest, quarter. */
    val COMPLEX_REST = RhythmPattern(
        "复杂休止",
        notes = listOf(n(12), n(12, isRest = true), n(6), n(6, isRest = true), n(12)),
    )

    /** 十六分与附点 — sixteenth run, dotted quarter + eighth, quarter. */
    val SIXTEENTH_DOTTED = RhythmPattern(
        "十六分与附点",
        notes = listOf(n(3), n(3), n(3), n(3), n(18), n(6), n(12)),
    )

    /** 切分与三连音 — syncopation then a triplet group. */
    val SYNCOPATED_TRIPLET = RhythmPattern(
        "切分与三连音",
        notes = listOf(n(6), n(12), n(6), n(4), n(4), n(4), n(12)),
    )

    /** 带休止的十六分 — sixteenths broken by an eighth rest. */
    val SIXTEENTH_REST = RhythmPattern(
        "带休止的十六分",
        notes = listOf(n(3), n(3), n(6, isRest = true), n(3), n(3), n(3), n(3), n(12), n(12)),
    )

    /** 复杂切分 — sixteenth syncopation across the bar. */
    val COMPLEX_SYNCOPATION = RhythmPattern(
        "复杂切分",
        notes = listOf(n(3), n(6), n(3), n(6), n(12), n(6), n(12)),
    )

    /** 附点与十六分 — dotted pairs alternating with sixteenths. */
    val DOTTED_SIXTEENTH_RUN = RhythmPattern(
        "附点与十六分",
        notes = listOf(n(18), n(3), n(3), n(6), n(18)),
    )

    /** 十六分切分 — sixteenth syncopation between eighths. */
    val SIXTEENTH_SYNCOPATION = RhythmPattern(
        "十六分切分",
        notes = listOf(n(6), n(3), n(6), n(3), n(6), n(12), n(12)),
    )

    /** 跨拍十六分 — sixteenth groups crossing beat boundaries. */
    val CROSS_BAR_SIXTEENTH = RhythmPattern(
        "跨拍十六分",
        notes = listOf(n(3), n(3), n(6), n(12), n(3), n(3), n(6), n(12)),
    )

    /** 三连音切分 — triplets intertwined with syncopation. */
    val TRIPLET_SYNCOPATION = RhythmPattern(
        "三连音切分",
        notes = listOf(n(6), n(4), n(4), n(4), n(6), n(4), n(4), n(4), n(12)),
    )

    /** 附点十六分连奏 — dotted-sixteenth runs across the bar. */
    val DOTTED_SIXTEENTH_LEGATO = RhythmPattern(
        "附点十六分连奏",
        notes = listOf(n(3), n(3), n(3), n(18), n(6), n(3), n(3), n(6), n(3)),
    )

    /** 带休止的十六分切分 — sixteenth syncopation with rests. */
    val SIXTEENTH_REST_SYNCOPATED = RhythmPattern(
        "带休止的十六分切分",
        notes = listOf(n(3), n(3, isRest = true), n(6), n(3), n(6), n(12), n(3), n(6), n(6)),
    )

    /** 三拍十六分 — sixteenth run then eighths in 3/4. */
    val THREE_FOUR_SIXTEENTHS = RhythmPattern(
        "三拍十六分",
        beatsPerBar = 3,
        notes = listOf(n(3), n(3), n(3), n(3), n(6), n(6), n(6), n(6)),
    )

    /** 三拍复杂切分 — sixteenth syncopation in 3/4. */
    val THREE_FOUR_COMPLEX_SYNCOPATION = RhythmPattern(
        "三拍复杂切分",
        beatsPerBar = 3,
        notes = listOf(n(6), n(12), n(3), n(3), n(12)),
    )

    /** 六八切分 — syncopation across the two dotted beats of 6/8. */
    val SIX_EIGHT_SYNCOPATED = RhythmPattern(
        "六八切分",
        beatsPerBar = 6,
        beatUnit = 8,
        notes = listOf(n(6), n(6), n(6), n(12), n(6)),
    )

    val HARD: List<RhythmPattern> = listOf(
        SYNCOPATED_COMBO, SIXTEENTH_RUN, TRIPLET_EIGHTH, DOTTED_SYNCOPATION,
        COMPLEX_REST, SIXTEENTH_DOTTED, SYNCOPATED_TRIPLET,
        SIXTEENTH_REST, COMPLEX_SYNCOPATION, DOTTED_SIXTEENTH_RUN,
        SIXTEENTH_SYNCOPATION, CROSS_BAR_SIXTEENTH, TRIPLET_SYNCOPATION,
        DOTTED_SIXTEENTH_LEGATO, SIXTEENTH_REST_SYNCOPATED,
        THREE_FOUR_SIXTEENTHS, THREE_FOUR_COMPLEX_SYNCOPATION,
        SIX_EIGHT_SYNCOPATED,
    )

    private fun n(grids: Int, isRest: Boolean = false) = RhythmNote(grids, isRest)
}
