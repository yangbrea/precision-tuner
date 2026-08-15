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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunner.metronome.MetronomeState

@Composable
fun MetronomeScreen(
    state: MetronomeState,
    onToggle: () -> Unit,
    onSetBpm: (Int) -> Unit,
    onSetBeatsPerBar: (Int) -> Unit,
    onSetSubdivision: (Int) -> Unit,
    onSetNoteValue: (Int) -> Unit,
    onTap: () -> Unit,
    onSetVolume: (Float) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.bpm.toString(),
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(text = "BPM", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSetBpm(state.bpm - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = "减慢")
            }
            Slider(
                value = state.bpm.toFloat(),
                onValueChange = { onSetBpm(it.toInt()) },
                valueRange = 30f..300f,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onSetBpm(state.bpm + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = "加快")
            }
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onTap) { Text("TAP 节拍") }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSetBeatsPerBar(state.beatsPerBar - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = "减少拍数")
            }
            Text(
                text = "每小节 ${state.beatsPerBar}/${state.noteValue} 拍",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = { onSetBeatsPerBar(state.beatsPerBar + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = "增加拍数")
            }
        }

        // Time-signature denominator (4 or 8) → compound meters.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4, 8).forEach { nv ->
                val selected = state.noteValue == nv
                Chip(label = nv.toString(), selected = selected, onClick = { onSetNoteValue(nv) })
            }
        }

        // Subdivision (none / eighths / triplets / sixteenths).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf(1 to "无", 2 to "八分", 3 to "三连音", 4 to "十六分")
            options.forEach { (n, label) ->
                Chip(label = label, selected = state.subdivision == n, onClick = { onSetSubdivision(n) })
            }
        }

        Spacer(Modifier.height(8.dp))
        // Beat indicator dots (beat 1 is the accent).
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(state.beatsPerBar) { i ->
                val beat = i + 1
                val isCurrent = state.isPlaying && state.currentBeat == beat
                val isAccent = beat == 1
                val color = when {
                    isCurrent && isAccent -> primary
                    isCurrent -> primary.copy(alpha = 0.6f)
                    isAccent -> MaterialTheme.colorScheme.onBackground
                    else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, CircleShape),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Start / stop.
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier.size(96.dp),
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "停止" else "开始",
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = state.volume,
                onValueChange = onSetVolume,
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
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
        Text(label, fontSize = 13.sp, color = if (selected) primary else MaterialTheme.colorScheme.onBackground)
    }
}
