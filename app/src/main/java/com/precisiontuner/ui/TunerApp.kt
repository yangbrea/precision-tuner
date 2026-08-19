package com.precisiontuner.ui

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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.precisiontuner.TunerMode
import com.precisiontuner.TunerState
import com.precisiontuner.TunerViewModel
import com.precisiontuner.ear.EarTrainingViewModel
import com.precisiontuner.metronome.MetronomeViewModel
import com.precisiontuner.ui.ear.EarTrainingScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerApp(
    viewModel: TunerViewModel,
    metronomeViewModel: MetronomeViewModel,
    earTrainingViewModel: EarTrainingViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val customPresets by viewModel.customPresets.collectAsStateWithLifecycle()
    val metronomeState by metronomeViewModel.state.collectAsStateWithLifecycle()
    val earExercise by earTrainingViewModel.activeExercise.collectAsStateWithLifecycle()
    val earAtMenu by earTrainingViewModel.atMenu.collectAsStateWithLifecycle()
    val earDifficulties by earTrainingViewModel.difficulties.collectAsStateWithLifecycle()
    val earSession by earTrainingViewModel.currentSession.collectAsStateWithLifecycle()
    val earSettings by earTrainingViewModel.settings.collectAsStateWithLifecycle()
    val earPlaying by earTrainingViewModel.isPlaying.collectAsStateWithLifecycle()
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

    // Stop capture and cue playback whenever this UI is not visible, and resume
    // when it comes back. A backgrounded activity's pipeline must never keep
    // recording or playing sounds — even a duplicated (hidden) instance.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.setUiActive(false)
                    viewModel.stopListening()
                    metronomeViewModel.stop()
                    earTrainingViewModel.onPause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.setUiActive(true)
                    if (state.hasPermission && state.mode != TunerMode.METRONOME &&
                        state.mode != TunerMode.EAR_TRAINING
                    ) {
                        viewModel.startListening()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            observer.onStateChanged(lifecycleOwner, Lifecycle.Event.ON_RESUME)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setUiActive(false)
            viewModel.stopListening()
            metronomeViewModel.stop()
            earTrainingViewModel.onPause()
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                ),
            )
        },
        bottomBar = {
            if (!showSettings) {
                TunerBottomBar(
                    mode = state.mode,
                    onModeChange = { mode ->
                        viewModel.setMode(mode)
                        // Leaving the ear-training tab stops its playback; the
                        // tuner's capture resumes inside TunerViewModel.setMode.
                        if (mode != TunerMode.EAR_TRAINING) {
                            earTrainingViewModel.onPause()
                        }
                    },
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
                    onSensitivityThresholdChange = viewModel::updateSensitivityThreshold,
                    onSmoothingWindowChange = viewModel::updateSmoothingWindow,
                    onFilterChange = viewModel::updateFilterStrength,
                    onThemeModeChange = viewModel::updateThemeMode,
                    onVisualModeChange = viewModel::updateVisualMode,
                    onGaugeStyleChange = viewModel::updateGaugeStyle,
                    onDetectionEngineChange = viewModel::updateDetectionEngine,
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
                    onSetAccent = metronomeViewModel::setAccent,
                )
                // Ear training is pure recognition (no microphone needed), so it
                // must stay reachable even when RECORD_AUDIO was denied.
                state.mode == TunerMode.EAR_TRAINING -> EarTrainingScreen(
                    atMenu = earAtMenu,
                    activeExercise = earExercise,
                    difficulties = earDifficulties,
                    session = earSession,
                    settings = earSettings,
                    isPlaying = earPlaying,
                    onSelectExercise = earTrainingViewModel::selectExercise,
                    onBackToMenu = earTrainingViewModel::backToMenu,
                    onStart = earTrainingViewModel::startSession,
                    onReplay = earTrainingViewModel::replay,
                    onSelectAnswer = earTrainingViewModel::selectAnswer,
                    onNext = earTrainingViewModel::nextQuestion,
                    onEnd = earTrainingViewModel::endSession,
                    onBackToSetup = earTrainingViewModel::backToSetup,
                    onRestart = earTrainingViewModel::restartSession,
                    onDifficulty = earTrainingViewModel::setDifficulty,
                    onMelodic = earTrainingViewModel::setMelodicInterval,
                    onNoteReferenceTone = earTrainingViewModel::setNoteReferenceTone,
                    onTestCount = earTrainingViewModel::setTestQuestionCount,
                    onRhythmSubmit = earTrainingViewModel::submitRhythm,
                    onRhythmTap = earTrainingViewModel::playTapSound,
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
                    onTemperamentChange = viewModel::updateTemperament,
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
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = mode == TunerMode.INSTRUMENT,
            onClick = { onModeChange(TunerMode.INSTRUMENT) },
            icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
            label = { Text("乐器调音") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = mode == TunerMode.CHROMATIC,
            onClick = { onModeChange(TunerMode.CHROMATIC) },
            icon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
            label = { Text("半音阶调音") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = mode == TunerMode.METRONOME,
            onClick = { onModeChange(TunerMode.METRONOME) },
            icon = { Icon(Icons.Filled.AvTimer, contentDescription = null) },
            label = { Text("节拍器") },
            colors = itemColors,
        )
        NavigationBarItem(
            selected = mode == TunerMode.EAR_TRAINING,
            onClick = { onModeChange(TunerMode.EAR_TRAINING) },
            icon = { Icon(Icons.Filled.Headphones, contentDescription = null) },
            label = { Text("视听练耳") },
            colors = itemColors,
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
