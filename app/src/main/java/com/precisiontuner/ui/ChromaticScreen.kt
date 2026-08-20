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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.precisiontuner.TuneVisualState
import com.precisiontuner.TunerState
import com.precisiontuner.settings.AppSettings
import com.precisiontuner.settings.VisualMode
import com.precisiontuner.settings.isTall
import com.precisiontuner.tuning.Temperament
import kotlin.math.roundToInt

@Composable
fun ChromaticScreen(
    state: TunerState,
    settings: AppSettings,
    onTemperamentChange: (Temperament) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        NeonScreenBackground(
            cents = state.cents,
            active = state.cents != null,
            pulseTick = state.inTuneFlash,
            visualState = state.visualState,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Text(
            text = "${settings.temperament.label} · A4 = ${settings.referenceA4.roundToInt()} Hz",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        TemperamentRow(selected = settings.temperament, onSelect = onTemperamentChange)

        // Dial-style gauges (dial / trail) are tall, so the gauge and spectrum
        // are sized a bit smaller to keep everything on one screen.
        val tall = settings.gaugeStyle.isTall

        Spacer(Modifier.height(10.dp))
        Readout(
            noteName = state.noteName,
            octave = state.octave,
            cents = state.cents,
            frequency = state.detectedFrequency,
            detectionPhase = state.detectionPhase,
            observedNoteName = state.observedNoteName,
            observedOctave = state.observedOctave,
            flashTick = state.inTuneFlash,
            visualState = state.visualState,
        )

            NeonPanel(
                highlighted = state.visualState == TuneVisualState.IN_TUNE,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TunerGauge(
                    cents = state.cents,
                    flashTick = state.inTuneFlash,
                    style = settings.gaugeStyle,
                    visualState = state.visualState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (tall) 172.dp else 118.dp)
                        .padding(horizontal = 6.dp),
                )
            }

        Spacer(Modifier.height(2.dp))
        // The A4 reference slider moved to settings, so the freed bottom area
        // is now given to the visualization: it fills the remaining vertical
        // space instead of a fixed strip (with a floor so it never collapses).
        if (settings.visualMode == VisualMode.WAVEFORM) {
            WaveformView(
                waveform = state.waveform,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 48.dp),
            )
        } else {
            SpectrumView(
                spectrum = state.spectrum,
                detectedFrequency = state.detectedFrequency,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 48.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TemperamentRow(selected: Temperament, onSelect: (Temperament) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Temperament.entries.forEach { temperament ->
            val isSelected = temperament == selected
            val accent = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(10.dp),
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(temperament) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = temperament.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = if (isSelected) accent else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
