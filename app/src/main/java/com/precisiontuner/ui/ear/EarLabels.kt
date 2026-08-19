package com.precisiontuner.ui.ear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.precisiontuner.ear.Difficulty
import com.precisiontuner.ear.ExerciseType
import com.precisiontuner.ear.PracticeMode

/** UI labels and icons for the ear-training section (Chinese, inline like the rest of the app). */
internal fun ExerciseType.label(): String = when (this) {
    ExerciseType.NOTE -> "单音识别"
    ExerciseType.INTERVAL -> "音程听辨"
    ExerciseType.CHORD -> "和弦听辨"
    ExerciseType.SCALE -> "音阶听辨"
    ExerciseType.STAFF_READING -> "五线谱识谱"
    ExerciseType.RHYTHM -> "节奏听写"
}

internal fun ExerciseType.description(): String = when (this) {
    ExerciseType.NOTE -> "辨认单个音符的音名"
    ExerciseType.INTERVAL -> "辨认两音之间的音程关系"
    ExerciseType.CHORD -> "辨认和弦的性质"
    ExerciseType.SCALE -> "辨认音阶的种类"
    ExerciseType.STAFF_READING -> "看五线谱辨认音名"
    ExerciseType.RHYTHM -> "看五线谱节奏并敲击复现"
}

internal fun ExerciseType.icon(): ImageVector = when (this) {
    ExerciseType.NOTE -> Icons.Filled.MusicNote
    ExerciseType.INTERVAL -> Icons.Filled.SwapHoriz
    ExerciseType.CHORD -> Icons.Filled.Piano
    ExerciseType.SCALE -> Icons.AutoMirrored.Filled.ShowChart
    ExerciseType.STAFF_READING -> Icons.Filled.LibraryMusic
    ExerciseType.RHYTHM -> Icons.Filled.AvTimer
}

internal fun ExerciseType.prompt(): String = when (this) {
    ExerciseType.NOTE -> "听音，选择正确的音名"
    ExerciseType.INTERVAL -> "听音，选择正确的音程"
    ExerciseType.CHORD -> "听音，选择正确的和弦"
    ExerciseType.SCALE -> "听音，选择正确的音阶"
    ExerciseType.STAFF_READING -> "看五线谱，选择正确的音名"
    ExerciseType.RHYTHM -> "先听参考节奏，再按谱面敲击复现"
}

internal fun Difficulty.label(): String = when (this) {
    Difficulty.EASY -> "简单"
    Difficulty.MEDIUM -> "中等"
    Difficulty.HARD -> "困难"
}

/**
 * Per-exercise difficulty annotation: describes only the current exercise's
 * pool under [this] difficulty (difficulty itself is global).
 */
internal fun Difficulty.descriptionFor(exerciseType: ExerciseType): String = when (exerciseType) {
    ExerciseType.NOTE -> when (this) {
        Difficulty.EASY -> "音域 C4–B4 自然音"
        Difficulty.MEDIUM -> "音域 C4–B4 全部 12 音"
        Difficulty.HARD -> "音域 C4–B5 全部 24 音"
    }
    ExerciseType.INTERVAL -> when (this) {
        Difficulty.EASY -> "基础音程 7 种"
        Difficulty.MEDIUM, Difficulty.HARD -> "全部 13 种音程"
    }
    ExerciseType.CHORD -> when (this) {
        Difficulty.EASY -> "三和弦 4 种"
        Difficulty.MEDIUM -> "三和弦 + 属七/小七 6 种"
        Difficulty.HARD -> "全部和弦 8 种"
    }
    ExerciseType.SCALE -> when (this) {
        Difficulty.EASY -> "大小调体系 4 种"
        Difficulty.MEDIUM -> "全部音阶 8 种"
        Difficulty.HARD -> "全部音阶 8 种 · 随机升降序"
    }
    ExerciseType.STAFF_READING -> when (this) {
        Difficulty.EASY -> "高音谱号 C4–B4 · 自然音"
        Difficulty.MEDIUM -> "高低音谱号 C3–B5 · 含升降号"
        Difficulty.HARD -> "高低音谱号 C2–C6 · 含升降号"
    }
    ExerciseType.RHYTHM -> when (this) {
        Difficulty.EASY -> "四分/八分/二分基础节奏"
        Difficulty.MEDIUM -> "附点、切分与十六分"
        Difficulty.HARD -> "十六分连奏与复杂切分"
    }
}

internal fun PracticeMode.title(): String = when (this) {
    PracticeMode.ENDLESS -> "无尽模式"
    PracticeMode.CHALLENGE -> "挑战模式"
    PracticeMode.TEST -> "测试模式"
}

internal fun PracticeMode.description(): String = when (this) {
    PracticeMode.ENDLESS -> "无限题目，练到满意"
    PracticeMode.CHALLENGE -> "答错扣命，五次出局"
    PracticeMode.TEST -> "限量题目，检验水平"
}

internal fun PracticeMode.icon(): ImageVector = when (this) {
    PracticeMode.ENDLESS -> Icons.Filled.AllInclusive
    PracticeMode.CHALLENGE -> Icons.Filled.Favorite
    PracticeMode.TEST -> Icons.Filled.Flag
}
