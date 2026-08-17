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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.precisiontuner.TunerState
import com.precisiontuner.settings.AppSettings
import com.precisiontuner.settings.GaugeStyle
import com.precisiontuner.settings.VisualMode
import com.precisiontuner.tuning.Temperament
import kotlin.math.roundToInt

@Composable
fun ChromaticScreen(
    state: TunerState,
    settings: AppSettings,
    onTemperamentChange: (Temperament) -> Unit,
    onReferenceChange: (Double) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${settings.temperament.label} · A4 = ${state.referenceA4.roundToInt()} Hz",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        TemperamentRow(selected = settings.temperament, onSelect = onTemperamentChange)

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
        )

        TunerGauge(
            cents = state.cents,
            flashTick = state.inTuneFlash,
            style = settings.gaugeStyle,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (settings.gaugeStyle == GaugeStyle.DIAL) 216.dp else 112.dp),
        )

        Spacer(Modifier.height(4.dp))
        if (settings.visualMode == VisualMode.WAVEFORM) {
            WaveformView(
                waveform = state.waveform,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
        } else {
            SpectrumView(
                spectrum = state.spectrum,
                detectedFrequency = state.detectedFrequency,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        // A4 reference pitch.
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "基准音 A4（默认 440 Hz）",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = state.referenceA4.toFloat(),
                onValueChange = { onReferenceChange(it.toDouble()) },
                valueRange = 415f..466f,
                steps = 50, // one step per Hz
            )
            Text(
                text = "${state.referenceA4.roundToInt()} Hz",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        // Keep the reference controls visually separate from the persistent
        // navigation bar. The larger dial consumes most of the flexible space,
        // so it needs an explicit gap instead of relying on Spacer(weight).
        Spacer(
            Modifier.height(if (settings.gaugeStyle == GaugeStyle.DIAL) 20.dp else 8.dp),
        )
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
