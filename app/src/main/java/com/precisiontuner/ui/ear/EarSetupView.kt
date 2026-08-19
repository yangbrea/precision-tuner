package com.precisiontuner.ui.ear

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.precisiontuner.ear.Difficulty
import com.precisiontuner.ear.EarSettings
import com.precisiontuner.ear.ExerciseType
import com.precisiontuner.ear.PracticeMode

/**
 * 选关大厅: choose a difficulty (system preset), a mode (无尽/挑战/测试), the
 * per-exercise extras, then start the session. Modes are rendered as
 * selectable level cards with a game-like selected glow.
 */
@Composable
fun EarSetupView(
    exerciseType: ExerciseType,
    settings: EarSettings,
    difficulty: Difficulty,
    onStart: (PracticeMode) -> Unit,
    onBackToMenu: () -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    onMelodic: (Boolean) -> Unit,
    onNoteReferenceTone: (Boolean) -> Unit,
    onTestCount: (Int) -> Unit,
) {
    var selectedMode by rememberSaveable { mutableStateOf(PracticeMode.ENDLESS) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackToMenu) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回练习菜单")
            }
            Text(
                text = exerciseType.label(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))

        SectionTitle("选择难度")
        Spacer(Modifier.height(10.dp))
        DifficultySettings(exerciseType, difficulty, onDifficulty)

        Spacer(Modifier.height(18.dp))
        SectionTitle("选择模式")
        PracticeMode.entries.forEach { mode ->
            ModeCard(mode = mode, selected = mode == selectedMode, onClick = { selectedMode = mode })
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("练习设置")
        Spacer(Modifier.height(10.dp))
        when (exerciseType) {
            ExerciseType.INTERVAL -> {
                MelodicIntervalSettings(settings.melodicInterval, onMelodic)
                Spacer(Modifier.height(14.dp))
            }
            ExerciseType.NOTE -> {
                NoteReferenceSettings(settings.noteReferenceTone, onNoteReferenceTone)
                Spacer(Modifier.height(14.dp))
            }
            else -> Unit
        }

        // 测试题数 only matters for the limited-length test mode.
        if (selectedMode == PracticeMode.TEST) {
            TestCountSettings(settings.testQuestionCount, onTestCount)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onStart(selectedMode) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("开始练习", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DifficultySettings(
    exerciseType: ExerciseType,
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    FilterChip(
                        selected = difficulty == d,
                        onClick = { onDifficulty(d) },
                        label = { Text(d.label()) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = difficulty.descriptionFor(exerciseType),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeCard(mode: PracticeMode, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        border = if (selected) BorderStroke(2.dp, accent) else null,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) accent.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = mode.icon(),
                    contentDescription = null,
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = mode.title(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = mode.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun MelodicIntervalSettings(melodic: Boolean, onMelodic: (Boolean) -> Unit) {
    SettingLabel("播放方式")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = melodic,
            onClick = { onMelodic(true) },
            label = { Text("旋律音程") },
        )
        FilterChip(
            selected = !melodic,
            onClick = { onMelodic(false) },
            label = { Text("和声音程") },
        )
    }
}

@Composable
private fun NoteReferenceSettings(enabled: Boolean, onEnabled: (Boolean) -> Unit) {
    SettingLabel("基准音提示")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = enabled,
            onClick = { onEnabled(true) },
            label = { Text("带基准音（先播 C4）") },
        )
        FilterChip(
            selected = !enabled,
            onClick = { onEnabled(false) },
            label = { Text("不带基准音") },
        )
    }
}

@Composable
private fun TestCountSettings(count: Int, onTestCount: (Int) -> Unit) {
    SettingLabel("测试题数")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(5, 10, 20).forEach { n ->
            FilterChip(
                selected = count == n,
                onClick = { onTestCount(n) },
                label = { Text("$n 题") },
            )
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
