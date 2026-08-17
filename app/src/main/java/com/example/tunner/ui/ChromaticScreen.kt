package com.example.tunner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunner.TunerState
import com.example.tunner.settings.AppSettings
import com.example.tunner.settings.GaugeStyle
import com.example.tunner.settings.VisualMode
import kotlin.math.roundToInt

@Composable
fun ChromaticScreen(
    state: TunerState,
    settings: AppSettings,
    onReferenceChange: (Double) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "十二平均律 · A4 = ${state.referenceA4.roundToInt()} Hz",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
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
    }
}
