package com.example.tunner.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tunner.TunerMode
import com.example.tunner.TunerState
import com.example.tunner.TunerViewModel
import com.example.tunner.metronome.MetronomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerApp(viewModel: TunerViewModel, metronomeViewModel: MetronomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val customPresets by viewModel.customPresets.collectAsStateWithLifecycle()
    val metronomeState by metronomeViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showPresetManager by rememberSaveable { mutableStateOf(false) }

    val navigateBackFromSettings: () -> Unit = remember(showPresetManager) {
        {
            if (showPresetManager) {
                showPresetManager = false
            } else {
                showSettings = false
            }
        }
    }

    BackHandler(enabled = showSettings) {
        navigateBackFromSettings()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onPermissionResult(true)
        } else {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopListening()
            metronomeViewModel.stop()
        }
    }

    Scaffold(
        modifier = Modifier.edgeSwipeBack(
            enabled = showSettings,
            onBack = navigateBackFromSettings,
        ),
        topBar = {
            TopAppBar(
                title = { Text(when { showPresetManager -> "调弦预设"; showSettings -> "设置"; else -> "调音器 Tuner" }) },
                navigationIcon = {
                    if (showSettings || showPresetManager) {
                        IconButton(onClick = navigateBackFromSettings) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
                TunerBottomBar(
                    mode = state.mode,
                    onModeChange = viewModel::setMode,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                showPresetManager -> TuningPresetScreen(
                    presets = customPresets,
                    onCreate = viewModel::createCustomPreset,
                    onUpdate = viewModel::updateCustomPreset,
                    onDelete = viewModel::deleteCustomPreset,
                )
                showSettings -> SettingsScreen(
                    settings = settings,
                    onAccentChange = viewModel::updateAccent,
                    onSensitivityChange = viewModel::updateSensitivity,
                    onFilterChange = viewModel::updateFilterStrength,
                    onThemeModeChange = viewModel::updateThemeMode,
                    onVisualModeChange = viewModel::updateVisualMode,
                    onManageTunings = { showPresetManager = true },
                )
                state.mode == TunerMode.METRONOME -> MetronomeScreen(
                    state = metronomeState,
                    onToggle = metronomeViewModel::toggle,
                    onSetBpm = metronomeViewModel::setBpm,
                    onSetBeatsPerBar = metronomeViewModel::setBeatsPerBar,
                    onSetSubdivision = metronomeViewModel::setSubdivision,
                    onSetNoteValue = metronomeViewModel::setNoteValue,
                    onTap = metronomeViewModel::tap,
                    onSetVolume = metronomeViewModel::setVolume,
                )
                !state.hasPermission && permissionRequested -> PermissionDenied(
                    onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
                state.mode == TunerMode.INSTRUMENT -> InstrumentScreen(
                    state = state,
                    settings = settings,
                    tuning = viewModel.resolveTuning(settings.instrumentId, settings.tuningId),
                    customPresets = customPresets,
                    onSelectString = viewModel::selectString,
                    onSelectInstrument = viewModel::updateInstrument,
                    onSelectTuning = viewModel::updateTuning,
                    onToggleReferenceTone = viewModel::toggleReferenceTone,
                    onSelectCustomPreset = viewModel::selectCustomPreset,
                )
                else -> ChromaticScreen(
                    state = state,
                    settings = settings,
                    onReferenceChange = viewModel::setReferenceA4,
                )
            }
        }
    }
}

@Composable
private fun TunerBottomBar(
    mode: TunerMode,
    onModeChange: (TunerMode) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = mode == TunerMode.INSTRUMENT,
            onClick = { onModeChange(TunerMode.INSTRUMENT) },
            icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
            label = { Text("乐器调音") },
        )
        NavigationBarItem(
            selected = mode == TunerMode.CHROMATIC,
            onClick = { onModeChange(TunerMode.CHROMATIC) },
            icon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
            label = { Text("半音阶调音") },
        )
        NavigationBarItem(
            selected = mode == TunerMode.METRONOME,
            onClick = { onModeChange(TunerMode.METRONOME) },
            icon = { Icon(Icons.Filled.AvTimer, contentDescription = null) },
            label = { Text("节拍器") },
        )
    }
}

@Composable
private fun PermissionDenied(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "调音器需要使用麦克风", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(text = "请授予录音权限以开始调音", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest) {
                Text("授予权限")
            }
        }
    }
}
