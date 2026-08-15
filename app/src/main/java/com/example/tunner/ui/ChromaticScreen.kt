package com.example.tunner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunner.TunerState
import com.example.tunner.ui.theme.TunerOnDarkMuted
import kotlin.math.roundToInt

@Composable
fun ChromaticScreen(
    state: TunerState,
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

        Spacer(Modifier.weight(1f))

        // A4 reference pitch.
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "基准音 A4（默认 440 Hz）",
                fontSize = 13.sp,
                color = TunerOnDarkMuted,
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
                color = TunerOnDarkMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
