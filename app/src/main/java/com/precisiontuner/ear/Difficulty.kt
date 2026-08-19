package com.precisiontuner.ear

/**
 * System-preset difficulty for the ear-training module. The chosen difficulty
 * decides the question pools of every exercise (see [DifficultyPresets]);
 * it replaces the old per-exercise pool toggles.
 */
enum class Difficulty {
    /** 简单 — narrow pools: naturals, basic intervals, triads, major/minor scales. */
    EASY,

    /** 中等 — wider pools. */
    MEDIUM,

    /** 困难 — full pools, wider registers, random scale direction. */
    HARD,
}
