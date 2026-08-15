package com.example.tunner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunner.TunerState
import com.example.tunner.tuning.GuitarTuning
import com.example.tunner.ui.theme.TunerAccent
import com.example.tunner.ui.theme.TunerOnDark
import com.example.tunner.ui.theme.TunerOnDarkMuted
import com.example.tunner.ui.theme.TunerSurfaceVariant

@Composable
fun GuitarScreen(
    state: TunerState,
    onSelectString: (Int?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val activeString = state.activeString?.let { GuitarTuning.byNumber(it) }
        Text(
            text = activeString?.let { "第${it.number}弦 · ${it.fullNote}" } ?: "标准调弦 · 自动识别",
            fontSize = 16.sp,
            color = TunerOnDarkMuted,
        )

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
                .height(210.dp),
        )

        Spacer(Modifier.height(8.dp))
        StringSelector(
            activeString = state.activeString,
            selectedString = state.selectedString,
            inTune = state.isInTune,
            onSelect = onSelectString,
        )

        Spacer(Modifier.weight(1f))
        Text(
            text = "点选某根弦可手动调音，再次点选回到自动识别",
            fontSize = 12.sp,
            color = TunerOnDarkMuted,
        )
    }
}

@Composable
private fun StringSelector(
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
        GuitarTuning.STANDARD.forEach { s ->
            val isActive = activeString == s.number
            val isSelected = selectedString == s.number
            val background = when {
                isActive && inTune -> primary.copy(alpha = 0.25f)
                isActive -> TunerSurfaceVariant
                else -> Color.Transparent
            }
            val border = when {
                isActive && inTune -> primary
                isSelected -> TunerAccent
                isActive -> TunerOnDarkMuted
                else -> Color(0xFF2A2A30)
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
                    color = if (isActive) TunerOnDark else TunerOnDarkMuted,
                )
                Text(
                    text = s.noteName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive && inTune) primary else TunerOnDark,
                )
            }
        }
    }
}
