package com.example.tunner.settings

import androidx.compose.ui.graphics.Color

/** Dark vs light app theme. */
enum class ThemeMode { DARK, LIGHT }

/** Which visualization is shown in the tuner screens. */
enum class VisualMode { SPECTRUM, WAVEFORM }

/** Which gauge style is drawn under the note readout. */
enum class GaugeStyle(val label: String) {
    RAIL("刻度条"),
    DIAL("表盘"),
}

/** Debug-only pitch engine selection for Tiny CREPE A/B testing. */
enum class DetectionEngine(val label: String) {
    PYIN_LITE("pYIN-lite"),
    CREPE_HYBRID("CREPE混合"),
    CREPE_PRIMARY("CREPE主检测"),
}

/** User-configurable app settings. */
data class AppSettings(
    val accent: AccentColor = AccentColor.GREEN,
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val filterStrength: Float = 0.5f, // 0..1, 0 = off
    val themeMode: ThemeMode = ThemeMode.DARK,
    val visualMode: VisualMode = VisualMode.SPECTRUM,
    val gaugeStyle: GaugeStyle = GaugeStyle.RAIL,
    val detectionEngine: DetectionEngine = DetectionEngine.CREPE_HYBRID,

    // Selected instrument & tuning for the instrument tuner.
    val instrumentId: String = "guitar",
    val tuningId: String = "standard",
)

/** Preset accent ("theme") colors. Only the "in tune" accent changes. */
enum class AccentColor(val label: String, val color: Color) {
    GREEN("绿", Color(0xFF00E676)),
    BLUE("蓝", Color(0xFF40C4FF)),
    ORANGE("橙", Color(0xFFFFB300)),
    PINK("粉", Color(0xFFFF4081)),
    PURPLE("紫", Color(0xFFB388FF)),
    CYAN("青", Color(0xFF18FFFF)),
}

/**
 * Tuner response speed vs. stability. [windowSize] is the median-smoothing
 * window (smaller = faster response, more jitter) and [confidence] is the pitch
 * confidence gate.
 */
enum class Sensitivity(val label: String, val windowSize: Int, val confidence: Double) {
    HIGH("高", 3, 0.45),
    MEDIUM("中", 5, 0.50),
    LOW("低", 9, 0.60),
}
