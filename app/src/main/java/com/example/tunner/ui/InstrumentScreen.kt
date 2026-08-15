package com.example.tunner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunner.TunerState
import com.example.tunner.settings.AppSettings
import com.example.tunner.tuning.CustomTuningStore
import com.example.tunner.tuning.Instrument
import com.example.tunner.tuning.InstrumentCatalog
import com.example.tunner.tuning.InstrumentString
import com.example.tunner.tuning.NoteMapper
import com.example.tunner.tuning.Tuning
import com.example.tunner.ui.theme.TunerAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentScreen(
    state: TunerState,
    settings: AppSettings,
    tuning: Tuning?,
    customMidis: List<Int>,
    onSelectString: (Int?) -> Unit,
    onSelectInstrument: (String) -> Unit,
    onSelectTuning: (String) -> Unit,
    onToggleReferenceTone: () -> Unit,
    onShiftCustomString: (Int, Int) -> Unit,
    onAddCustomString: () -> Unit,
    onRemoveCustomString: () -> Unit,
) {
    val isCustom = settings.instrumentId == CustomTuningStore.CUSTOM_ID
    val instrumentName = if (isCustom) "自定义" else InstrumentCatalog.instrument(settings.instrumentId)?.name ?: "?"
    var showPicker by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { showPicker = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$instrumentName · ${tuning?.name ?: "?"}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择乐器", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val active = state.activeString?.let { tuning?.byNumber(it) }
        Text(
            text = active?.let { "第${it.number}弦 · ${it.fullNote}" } ?: "自动识别",
            fontSize = 16.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Ear-training reference is available only for an explicit string target.
        val selectedTarget = state.selectedString?.let { tuning?.byNumber(it) }
        if (selectedTarget != null) {
            TextButton(onClick = onToggleReferenceTone) {
                Icon(
                    imageVector = if (state.isReferenceTonePlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isReferenceTonePlaying) "停止参考音" else "播放参考音",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (state.isReferenceTonePlaying) "停止参考音" else "参考音 ${selectedTarget.fullNote}")
            }
        }

        Spacer(Modifier.height(8.dp))
        Readout(
            noteName = state.noteName,
            octave = state.octave,
            cents = state.cents,
            frequency = state.detectedFrequency,
        )

        TunerGauge(
            cents = state.cents,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        )

        Spacer(Modifier.height(4.dp))
        SpectrumView(
            spectrum = state.spectrum,
            detectedFrequency = state.detectedFrequency,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )

        Spacer(Modifier.height(8.dp))
        if (tuning != null) {
            StringSelector(
                strings = tuning.strings,
                activeString = state.activeString,
                selectedString = state.selectedString,
                inTune = state.isInTune,
                onSelect = onSelectString,
            )
        }

        if (isCustom) {
            TextButton(onClick = { showEditor = true }) {
                Text("编辑调弦")
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = "点选某根弦可手动调音，再次点选回到自动识别",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showPicker) {
        InstrumentPicker(
            currentInstrumentId = settings.instrumentId,
            currentTuningId = settings.tuningId,
            onDismiss = { showPicker = false },
            onSelect = { inst, t ->
                onSelectInstrument(inst.id)
                onSelectTuning(t.id)
                showPicker = false
            },
            onSelectCustom = {
                onSelectInstrument(CustomTuningStore.CUSTOM_ID)
                showPicker = false
            },
        )
    }

    if (showEditor) {
        CustomTuningEditor(
            midis = customMidis,
            onShift = onShiftCustomString,
            onAdd = onAddCustomString,
            onRemove = onRemoveCustomString,
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun StringSelector(
    strings: List<InstrumentString>,
    activeString: Int?,
    selectedString: Int?,
    inTune: Boolean,
    onSelect: (Int?) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        strings.forEach { s ->
            val isActive = activeString == s.number
            val isSelected = selectedString == s.number
            val background = when {
                isActive && inTune -> primary.copy(alpha = 0.25f)
                isActive -> MaterialTheme.colorScheme.surfaceVariant
                else -> Color.Transparent
            }
            val border = when {
                isActive && inTune -> primary
                isSelected -> TunerAccent
                isActive -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(background, RoundedCornerShape(12.dp))
                    .border(1.5.dp, border, RoundedCornerShape(12.dp))
                    .clickable { onSelect(if (isSelected) null else s.number) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = s.number.toString(),
                    fontSize = 13.sp,
                    maxLines = 1,
                    color = if (isActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = s.noteName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = if (isActive && inTune) primary else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstrumentPicker(
    currentInstrumentId: String,
    currentTuningId: String,
    onDismiss: () -> Unit,
    onSelect: (Instrument, Tuning) -> Unit,
    onSelectCustom: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "选择乐器与调弦",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            // Custom tuning entry.
            Text(
                text = "自定义",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TuningChip(
                    label = "自定义调弦",
                    selected = currentInstrumentId == CustomTuningStore.CUSTOM_ID,
                ) { onSelectCustom() }
            }
            Spacer(Modifier.height(16.dp))

            InstrumentCatalog.instruments.forEach { inst ->
                Text(
                    text = inst.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    inst.tunings.forEach { t ->
                        val selected = inst.id == currentInstrumentId && t.id == currentTuningId
                        TuningChip(t.name, selected) { onSelect(inst, t) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TuningChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.5.dp,
                color = if (selected) primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) primary else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomTuningEditor(
    midis: List<Int>,
    onShift: (Int, Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "编辑自定义调弦",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            midis.forEachIndexed { index, midi ->
                val name = NoteMapper.NOTE_NAMES[((midi % 12) + 12) % 12]
                val octave = midi / 12 - 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "第${index + 1}弦",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onShift(index, -1) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "降半音")
                    }
                    Text(
                        text = "$name$octave",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.width(64.dp),
                    )
                    IconButton(onClick = { onShift(index, 1) }) {
                        Icon(Icons.Filled.Add, contentDescription = "升半音")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Text("添加弦")
                }
                OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) {
                    Text("删除弦")
                }
            }
        }
    }
}
