package com.example.tunner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tunner.metronome.MetronomeViewModel
import com.example.tunner.settings.ThemeMode
import com.example.tunner.ui.TunerApp
import com.example.tunner.ui.theme.TunerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TunerViewModel by viewModels()
    private val metronomeViewModel: MetronomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            TunerTheme(
                accent = settings.accent.color,
                darkTheme = settings.themeMode == ThemeMode.DARK,
            ) {
                TunerApp(viewModel, metronomeViewModel)
            }
        }
    }
}
