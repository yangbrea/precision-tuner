package com.precisiontuner.settings

import androidx.compose.ui.graphics.Color
import com.precisiontuner.tuning.Temperament

/** Dark vs light app theme. */
enum class ThemeMode { DARK, LIGHT }

/** Which visualization is shown in the tuner screens. */
enum class VisualMode { SPECTRUM, WAVEFORM }

/** Which gauge style is drawn under the note readout. */
enum class GaugeStyle(val label: String) {
    RAIL("刻度条"),
    DIAL("表盘"),
    TRAIL("流光"),
}

/** Gauge styles that need the tall dial layout (everything except the short rail). */
val GaugeStyle.isTall: Boolean
    get() = this != GaugeStyle.RAIL

/** Debug-only pitch engine selection for Tiny CREPE A/B testing. */
enum class DetectionEngine(val label: String) {
    PYIN_LITE("pYIN-lite"),
    CREPE_HYBRID("CREPE混合"),
    CREPE_PRIMARY("CREPE主检测"),
}

/** User-configurable app settings. */
data class AppSettings(
    val accent: AccentColor = AccentColor.GREEN,
    /** Pitch-confidence gate (0.30–0.80): higher rejects weaker frames. */
    val sensitivityThreshold: Float = 0.50f,
    /** Rolling-average window size (1–21): larger = more stable, slower response. */
    val smoothingWindow: Int = 5,
    val filterStrength: Float = 0.5f, // 0..1, 0 = off
    val themeMode: ThemeMode = ThemeMode.DARK,
    val visualMode: VisualMode = VisualMode.SPECTRUM,
    val gaugeStyle: GaugeStyle = GaugeStyle.RAIL,
    val temperament: Temperament = Temperament.EQUAL,
    val detectionEngine: DetectionEngine = DetectionEngine.CREPE_HYBRID,

    // Selected instrument & tuning for the instrument tuner.
    val instrumentId: String = "guitar",
    val tuningId: String = "standard",
)

/**
 * Maps the pitch-confidence gate to the noise-floor analysis SNR requirement.
 * The legacy three-tier values (0.45/0.50/0.60 → 2.0/2.5/3.0) fall inside this
 * linear ramp from 2.0 (threshold 0.30) to 3.0 (threshold 0.80).
 */
fun signalToNoiseRatio(threshold: Float): Double =
    2.0 + (threshold.coerceIn(0.30f, 0.80f) - 0.30f) / 0.50f

/** Preset accent ("theme") colors. Only the "in tune" accent changes. */
enum class AccentColor(val label: String, val color: Color) {
    GREEN("绿", Color(0xFF00E676)),
    BLUE("蓝", Color(0xFF40C4FF)),
    ORANGE("橙", Color(0xFFFFB300)),
    PINK("粉", Color(0xFFFF4081)),
    PURPLE("紫", Color(0xFFB388FF)),
    BROWN("棕", Color(0xFF8D6E63)),
}
