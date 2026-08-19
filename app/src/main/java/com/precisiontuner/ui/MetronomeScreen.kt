package com.precisiontuner.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.precisiontuner.metronome.MetronomeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeScreen(
    state: MetronomeState,
    onToggle: () -> Unit,
    onSetBpm: (Int) -> Unit,
    onSetBeatsPerBar: (Int) -> Unit,
    onSetSubdivision: (Int) -> Unit,
    onSetNoteValue: (Int) -> Unit,
    onTap: () -> Unit,
    onSetAccent: (Boolean) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showSubdivisionPicker by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val subdivisionLabel = when (state.subdivision) {
        2 -> "八分"
        3 -> "三连音"
        4 -> "十六分"
        else -> "无"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NeonScreenBackground(
            active = state.isPlaying,
            pulseTick = state.currentBeat,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (state.isPlaying) "LIVE TEMPO" else "TEMPO LAB",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            NeonPanel(
                highlighted = state.isPlaying,
                modifier = Modifier.size(248.dp),
            ) {
                MetronomeRing(
                    bpm = state.bpm,
                    beatsPerBar = state.beatsPerBar,
                    noteValue = state.noteValue,
                    subdivision = state.subdivision,
                    isPlaying = state.isPlaying,
                    currentBeat = state.currentBeat,
                    onSetBpm = onSetBpm,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                )
            }

            Spacer(Modifier.height(6.dp))
        // BPM: ±1 / ±10 / slider.
            Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSetBpm(state.bpm - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = "减慢")
            }
            IconButton(onClick = { onSetBpm(state.bpm - 10) }) {
                Text("−10", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = state.bpm.toFloat(),
                onValueChange = { onSetBpm(it.toInt()) },
                valueRange = 30f..300f,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onSetBpm(state.bpm + 10) }) {
                Text("+10", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { onSetBpm(state.bpm + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = "加快")
            }
            }

        // BPM presets.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120, 160).forEach { preset ->
                    Chip(label = preset.toString(), selected = state.bpm == preset, onClick = { onSetBpm(preset) })
                }
            }

            Spacer(Modifier.height(8.dp))
        // Time signature and subdivision: two separate pickers on one row.
        // Both stay open after a change; the user closes them (tap outside/back).
            NeonPanel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PickerSegment(
                        label = "${state.beatsPerBar}/${state.noteValue} 拍",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showTimePicker = true
                        },
                    )
                    PickerSegment(
                        label = "细分 $subdivisionLabel",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSubdivisionPicker = true
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        // TAP + accent toggle on one row.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTap()
                }) { Text("TAP 节拍") }
                Chip(
                    label = if (state.accentEnabled) "重音拍 开" else "重音拍 关",
                    selected = state.accentEnabled,
                    onClick = { onSetAccent(!state.accentEnabled) },
                )
            }

            Spacer(Modifier.weight(1f))

        // Start / stop.
            FilledIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
                modifier = Modifier.size(88.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "停止" else "开始",
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }

    if (showTimePicker) {
        TimeSignaturePicker(
            state = state,
            onSetBeatsPerBar = onSetBeatsPerBar,
            onSetNoteValue = onSetNoteValue,
            onDismiss = { showTimePicker = false },
        )
    }
    if (showSubdivisionPicker) {
        SubdivisionPicker(
            state = state,
            onSetSubdivision = onSetSubdivision,
            onDismiss = { showSubdivisionPicker = false },
        )
    }
}

@Composable
private fun PickerSegment(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSignaturePicker(
    state: MetronomeState,
    onSetBeatsPerBar: (Int) -> Unit,
    onSetNoteValue: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "拍号",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))

            Text("每小节拍数", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSetBeatsPerBar(state.beatsPerBar - 1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "减少拍数")
                }
                Text(
                    text = state.beatsPerBar.toString(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.width(56.dp).padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { onSetBeatsPerBar(state.beatsPerBar + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "增加拍数")
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("分母", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 8).forEach { nv ->
                    Chip(label = nv.toString(), selected = state.noteValue == nv, onClick = { onSetNoteValue(nv) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubdivisionPicker(
    state: MetronomeState,
    onSetSubdivision: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val labels = listOf("无", "八分", "三连音", "十六分")
    val label = labels.getOrElse(state.subdivision - 1) { "无" }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "细分",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSetSubdivision(state.subdivision - 1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "细分减少")
                }
                Text(
                    text = label,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.width(120.dp).padding(horizontal = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { onSetSubdivision(state.subdivision + 1) }) {
                    Icon(Icons.Filled.Add, contentDescription = "细分增加")
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { index, subLabel ->
                    Chip(label = subLabel, selected = state.subdivision == index + 1, onClick = { onSetSubdivision(index + 1) })
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .border(
                width = 1.5.dp,
                color = if (selected) primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) primary else MaterialTheme.colorScheme.onBackground)
    }
}
