package com.precisiontuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.precisiontuner.ear.EarTrainingViewModel
import com.precisiontuner.metronome.MetronomeViewModel
import com.precisiontuner.settings.ThemeMode
import com.precisiontuner.ui.TunerApp
import com.precisiontuner.ui.theme.TunerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TunerViewModel by viewModels()
    private val metronomeViewModel: MetronomeViewModel by viewModels()
    private val earTrainingViewModel: EarTrainingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            TunerTheme(
                accent = settings.accent.color,
                darkTheme = settings.themeMode == ThemeMode.DARK,
            ) {
                TunerApp(viewModel, metronomeViewModel, earTrainingViewModel)
            }
        }
    }
}
