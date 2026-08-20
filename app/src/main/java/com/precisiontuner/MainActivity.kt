package com.precisiontuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.precisiontuner.ear.EarTrainingViewModel
import com.precisiontuner.metronome.MetronomeViewModel
import com.precisiontuner.settings.ThemeMode
import com.precisiontuner.settings.effectiveThemeMode
import com.precisiontuner.ui.TunerApp
import com.precisiontuner.ui.theme.TunerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TunerViewModel by viewModels()
    private val metronomeViewModel: MetronomeViewModel by viewModels()
    private val earTrainingViewModel: EarTrainingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest theme draws the system launch screen. Switch the real
        // activity to the normal Compose host theme before creating its window.
        setTheme(R.style.Theme_Tunner)
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val lightSystemBars = settings.effectiveThemeMode == ThemeMode.LIGHT
            SideEffect {
                if (lightSystemBars) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ),
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    )
                }
            }
            TunerTheme(
                accent = settings.accent.color,
                darkTheme = settings.effectiveThemeMode == ThemeMode.DARK,
                themePreset = settings.themePreset,
            ) {
                TunerApp(viewModel, metronomeViewModel, earTrainingViewModel)
            }
        }
    }
}
