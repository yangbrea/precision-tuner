package com.example.tunner.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.tunner.tuning.CustomTuningPreset
import com.example.tunner.tuning.CustomTuningStore

/**
 * Persists [AppSettings] in SharedPreferences and exposes it as a [StateFlow].
 *
 * Writes are write-through: the flow is updated immediately and the value is
 * saved asynchronously.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setAccent(accent: AccentColor) = update { it.copy(accent = accent) }

    fun setSensitivity(sensitivity: Sensitivity) = update { it.copy(sensitivity = sensitivity) }

    fun setFilterStrength(strength: Float) =
        update { it.copy(filterStrength = strength.coerceIn(0f, 1f)) }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun setVisualMode(mode: VisualMode) = update { it.copy(visualMode = mode) }

    fun setDetectionEngine(engine: DetectionEngine) = update { it.copy(detectionEngine = engine) }

    /** Select an instrument and reset its tuning to the instrument's default. */
    fun setInstrument(instrumentId: String, tuningId: String) =
        update { it.copy(instrumentId = instrumentId, tuningId = tuningId) }

    fun setTuning(tuningId: String) = update { it.copy(tuningId = tuningId) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        prefs.edit()
            .putString(KEY_ACCENT, next.accent.name)
            .putString(KEY_SENSITIVITY, next.sensitivity.name)
            .putFloat(KEY_FILTER, next.filterStrength)
            .putString(KEY_THEME, next.themeMode.name)
            .putString(KEY_VISUAL, next.visualMode.name)
            .putString(KEY_DETECTION_ENGINE, next.detectionEngine.name)
            .putString(KEY_INSTRUMENT, next.instrumentId)
            .putString(KEY_TUNING, next.tuningId)
            .apply()
    }

    private fun load(): AppSettings {
        val accent = runCatching {
            AccentColor.valueOf(prefs.getString(KEY_ACCENT, null) ?: "")
        }.getOrDefault(AccentColor.GREEN)
        val sensitivity = runCatching {
            Sensitivity.valueOf(prefs.getString(KEY_SENSITIVITY, null) ?: "")
        }.getOrDefault(Sensitivity.MEDIUM)
        val filter = prefs.getFloat(KEY_FILTER, 0.5f).coerceIn(0f, 1f)
        val theme = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "")
        }.getOrDefault(ThemeMode.DARK)
        val visual = runCatching {
            VisualMode.valueOf(prefs.getString(KEY_VISUAL, null) ?: "")
        }.getOrDefault(VisualMode.SPECTRUM)
        val detectionEngine = runCatching {
            DetectionEngine.valueOf(prefs.getString(KEY_DETECTION_ENGINE, null) ?: "")
        }.getOrDefault(DetectionEngine.CREPE_SHADOW)
        var instrument = prefs.getString(KEY_INSTRUMENT, null) ?: "guitar"
        var tuning = prefs.getString(KEY_TUNING, null) ?: "standard"
        // The old app used (custom, standard) for its single mutable tuning.
        // That data is intentionally not migrated, so make the stale selection usable.
        if (instrument == CustomTuningStore.CUSTOM_INSTRUMENT_ID &&
            !tuning.startsWith(CustomTuningPreset.TUNING_ID_PREFIX)) {
            instrument = "guitar"
            tuning = "standard"
        }
        return AppSettings(
            accent = accent,
            sensitivity = sensitivity,
            filterStrength = filter,
            themeMode = theme,
            visualMode = visual,
            detectionEngine = detectionEngine,
            instrumentId = instrument,
            tuningId = tuning,
        )
    }

    private companion object {
        const val PREFS_NAME = "tuner_settings"
        const val KEY_ACCENT = "accent"
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_FILTER = "filterStrength"
        const val KEY_THEME = "theme"
        const val KEY_VISUAL = "visual"
        const val KEY_DETECTION_ENGINE = "detectionEngine"
        const val KEY_INSTRUMENT = "instrument"
        const val KEY_TUNING = "tuning"
    }
}
