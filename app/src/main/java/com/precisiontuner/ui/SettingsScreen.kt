package com.precisiontuner.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.precisiontuner.BuildConfig
import com.precisiontuner.R
import com.precisiontuner.settings.AccentColor
import com.precisiontuner.settings.AppSettings
import com.precisiontuner.settings.DetectionEngine
import com.precisiontuner.settings.GaugeStyle
import com.precisiontuner.settings.ThemeMode
import com.precisiontuner.settings.ThemePreset
import com.precisiontuner.settings.VisualMode
import com.precisiontuner.ui.theme.themePalette
import com.precisiontuner.update.RemoteRelease
import com.precisiontuner.update.UpdateChecker
import com.precisiontuner.update.VersionCompare
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Top-level settings sections reachable from the settings home menu. */
enum class SettingsSection { THEME, TUNING, ABOUT }

/** Settings home: a menu of the multi-level settings sections. */
@Composable
fun SettingsScreen(
    onOpenTheme: () -> Unit,
    onOpenTuning: () -> Unit,
    onManageTunings: () -> Unit,
    onOpenAbout: () -> Unit,
    onResetSettings: () -> Unit,
) {
    var showResetDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        SettingsMenuRow(
            icon = Icons.Filled.Palette,
            title = "主题",
            subtitle = "系统主题、主题模式、主题色、可视化与仪表盘样式",
            onClick = onOpenTheme,
        )
        Spacer(Modifier.height(12.dp))
        SettingsMenuRow(
            icon = Icons.Filled.Tune,
            title = "调音选项",
            subtitle = "基准音、灵敏度、平滑窗口与滤波强度",
            onClick = onOpenTuning,
        )
        Spacer(Modifier.height(12.dp))
        SettingsMenuRow(
            icon = Icons.Filled.LibraryMusic,
            title = "调弦预设",
            subtitle = "添加、编辑或删除可持久保存的调弦预设",
            onClick = onManageTunings,
        )
        Spacer(Modifier.height(12.dp))
        SettingsMenuRow(
            icon = Icons.Filled.Info,
            title = "版本信息",
            subtitle = "功能简介与版本号",
            onClick = onOpenAbout,
        )

        Spacer(Modifier.height(28.dp))
        SettingsMenuRow(
            icon = Icons.Filled.Restore,
            title = "恢复默认设置",
            subtitle = "将所有设置恢复为初始值",
            iconTint = MaterialTheme.colorScheme.error,
            onClick = { showResetDialog = true },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复默认设置?") },
            text = { Text("所有设置将恢复为初始值(主题、基准音、检测参数等)。自定义调弦预设不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onResetSettings()
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            },
        )
    }
}

/** Theme / skin settings: system presets, custom mode, appearance options. */
@Composable
fun ThemeSettingsScreen(
    settings: AppSettings,
    onAccentChange: (AccentColor) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onThemePresetChange: (ThemePreset) -> Unit,
    onVisualModeChange: (VisualMode) -> Unit,
    onGaugeStyleChange: (GaugeStyle) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        SectionTitle("主题设置")
        SegmentedCapsule(
            labels = listOf("自定义设置", "系统主题"),
            selected = if (settings.themePreset == ThemePreset.CLASSIC) 0 else 1,
            onSelect = { index ->
                if (index == 0) {
                    onThemePresetChange(ThemePreset.CLASSIC)
                } else {
                    onThemePresetChange(settings.lastSystemPreset)
                }
            },
        )

        Spacer(Modifier.height(20.dp))
        if (settings.themePreset == ThemePreset.CLASSIC) {
            // Custom mode: mode & accent controls only.
            SectionTitle("主题模式")
            SegmentedCapsule(
                labels = listOf("深色", "浅色"),
                selected = if (settings.themeMode == ThemeMode.DARK) 0 else 1,
                onSelect = { onThemeModeChange(if (it == 0) ThemeMode.DARK else ThemeMode.LIGHT) },
            )

            Spacer(Modifier.height(28.dp))
            SectionTitle("主题色")
            AccentRow(selected = settings.accent, onSelect = onAccentChange)
        } else {
            // System mode: the preset grid only; custom controls are hidden.
            ThemePresetGrid(
                selected = settings.themePreset,
                onSelect = onThemePresetChange,
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionTitle("可视化")
        SegmentedCapsule(
            labels = listOf("频谱", "波形"),
            selected = if (settings.visualMode == VisualMode.SPECTRUM) 0 else 1,
            onSelect = { onVisualModeChange(if (it == 0) VisualMode.SPECTRUM else VisualMode.WAVEFORM) },
        )

        Spacer(Modifier.height(28.dp))
        SectionTitle("仪表盘样式")
        GaugeStyleRow(selected = settings.gaugeStyle, onSelect = onGaugeStyleChange)
        Hint("刻度条：横向精密刻度；表盘：半圆弧形刻度")
    }
}

/** Tuning algorithm settings: detection & reference-pitch options. */
@Composable
fun TuningSettingsScreen(
    settings: AppSettings,
    onReferenceA4Change: (Double) -> Unit,
    onSensitivityThresholdChange: (Float) -> Unit,
    onSmoothingWindowChange: (Int) -> Unit,
    onFilterChange: (Float) -> Unit,
    onDetectionEngineChange: (DetectionEngine) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        SectionTitle("基准音 A4")
        ReferencePitchSlider(value = settings.referenceA4, onChange = onReferenceA4Change)
        Hint("基准音高:对所有调音模式(半音阶、乐器与参考音)全局生效")

        Spacer(Modifier.height(28.dp))
        SectionTitle("灵敏度门限")
        SensitivitySlider(
            value = settings.sensitivityThreshold,
            onChange = onSensitivityThresholdChange,
        )
        Hint("置信度门槛:越高越严格,弱信号帧被忽略;推荐 40%–60%")

        Spacer(Modifier.height(28.dp))
        SectionTitle("平滑窗口")
        SmoothingWindowSlider(
            value = settings.smoothingWindow,
            onChange = onSmoothingWindowChange,
        )
        Hint("滑动平均窗口:越大越稳、响应越慢;换音自动重置不串音")

        Spacer(Modifier.height(28.dp))
        SectionTitle("滤波强度")
        FilterSlider(value = settings.filterStrength, onChange = onFilterChange)
        Hint("强度越高,越能滤除高频噪声与泛音,低音弦更稳定")

        if (BuildConfig.DEBUG && BuildConfig.TINY_CREPE_ENABLED) {
            Spacer(Modifier.height(28.dp))
            SectionTitle("实验检测引擎")
            DetectionEngineRow(settings.detectionEngine, onDetectionEngineChange)
            Hint("混合模式仅在整数倍冲突时由CREPE仲裁;主检测始终优先使用模型")
        }
    }
}

/** Static about page: app identity, version number and a feature overview. */
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        AppIcon()
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Precision Tuner",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "版本 ${BuildConfig.VERSION_NAME}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                if (updateState == UpdateState.Checking) return@OutlinedButton
                updateState = UpdateState.Checking
                scope.launch {
                    updateState = when (val result = UpdateChecker.checkLatest()) {
                        is UpdateChecker.Result.Success ->
                            if (VersionCompare.isNewer(result.release.tagName, BuildConfig.VERSION_NAME)) {
                                UpdateState.Available(result.release)
                            } else {
                                UpdateState.Latest
                            }
                        UpdateChecker.Result.Error -> UpdateState.Failed
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("检查更新")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (val state = updateState) {
                UpdateState.Idle -> "从 GitHub 检查是否有新版本"
                UpdateState.Checking -> "正在检查…"
                UpdateState.Latest -> "已是最新版本"
                is UpdateState.Available -> "发现新版本 ${state.release.tagName}"
                UpdateState.Failed -> "检查失败,请检查网络后重试"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "功能简介",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            AboutItem("乐器调音:吉他、贝斯、尤克里里、小提琴等,自动识别琴弦,支持自定义调弦预设")
            AboutItem("半音阶调音:十二平均律、五度相生律、纯律三种律制,基准音 A4 可调(415–466 Hz)")
            AboutItem("AI 辅助检测:pYIN 与 Tiny CREPE 神经网络混合检测,抑制泛音与谐波干扰")
            AboutItem("节拍器:BPM 预设、点按测速、拍号与细分节奏、重音强调")
            AboutItem("视听练耳:单音、音程、和弦、音阶听辨与五线谱识谱练习")
            AboutItem("可视化:频谱 / 波形视图,刻度条、表盘、流光三种仪表盘样式")
            AboutItem("主题定制:系统主题预设与自定义深浅模式、六种主题色")
        }
        Spacer(Modifier.height(24.dp))
    }

    val available = updateState as? UpdateState.Available
    if (available != null) {
        AlertDialog(
            onDismissRequest = { updateState = UpdateState.Idle },
            title = { Text("发现新版本") },
            text = { Text("最新版本:${available.release.tagName}\n是否前往 GitHub 下载?") },
            confirmButton = {
                TextButton(onClick = {
                    updateState = UpdateState.Idle
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(available.release.htmlUrl)),
                    )
                }) { Text("去下载") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateState.Idle }) { Text("稍后") }
            },
        )
    }
}

/** UI state of the manual update check on the about page. */
private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Latest : UpdateState
    data class Available(val release: RemoteRelease) : UpdateState
    data object Failed : UpdateState
}

@Composable
private fun AboutItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppIcon() {
    // The launcher icon is an adaptive-icon XML on API 26+, which Compose's
    // painterResource cannot decode (only VectorDrawable / raster images).
    // Render the drawable into a bitmap instead, at the display density.
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconBitmap = remember {
        val px = (72.dp.value * density.density).roundToInt()
        context.getDrawable(R.mipmap.ic_launcher)?.toBitmap(px, px)?.asImageBitmap()
    }
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = "应用图标",
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
    }
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: Color? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val iconColor = iconTint ?: accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetectionEngineRow(
    selected: DetectionEngine,
    onSelect: (DetectionEngine) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetectionEngine.entries.forEach { engine ->
            val active = engine == selected
            val accent = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (active) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        1.5.dp,
                        if (active) accent else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(engine) }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    engine.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) accent else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text = text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SegmentedCapsule(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                label = { Text(label) },
            )
        }
    }
}

private data class ThemeCardColors(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val content: Color,
    val outline: Color,
)

@Composable
private fun ThemePresetGrid(
    selected: ThemePreset,
    onSelect: (ThemePreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemePreset.entries
            .filter { it != ThemePreset.CLASSIC }
            .chunked(2)
            .forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowPresets.forEach { preset ->
                    val palette = themePalette(preset)
                    val colors = ThemeCardColors(
                        palette.background, palette.surface, palette.primary,
                        palette.onBackground, palette.outline,
                    )
                    ThemePresetCard(
                        preset = preset,
                        colors = colors,
                        displayMode = preset.lockedMode ?: ThemeMode.DARK,
                        selected = preset == selected,
                        onClick = { onSelect(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowPresets.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemePresetCard(
    preset: ThemePreset,
    colors: ThemeCardColors,
    displayMode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .height(112.dp)
            .clip(shape)
            .background(colors.background, shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.accent else colors.outline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = preset.label,
                color = colors.content,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = when (displayMode) {
                ThemeMode.DARK -> "深色"
                ThemeMode.LIGHT -> "浅色"
            },
            color = colors.content.copy(alpha = 0.72f),
            fontSize = 11.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(colors.background, colors.surface, colors.accent).forEach { color ->
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, colors.outline.copy(alpha = 0.65f), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun GaugeStyleRow(selected: GaugeStyle, onSelect: (GaugeStyle) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GaugeStyle.entries.forEach { style ->
            val isSelected = style == selected
            val accent = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(style) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = style.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) accent else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun AccentRow(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AccentColor.entries.forEach { accent ->
            val isSelected = accent == selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.color, CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color(0xFF3A3A42),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(accent) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = accent.label,
                        tint = Color(0xFF0E0E10),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferencePitchSlider(value: Double, onChange: (Double) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = 415f..466f,
            steps = 50,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "当前 ${value.roundToInt()} Hz",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun SensitivitySlider(value: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0.30f..0.80f,
            steps = 49,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "当前 ${(value * 100).roundToInt()}%",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun SmoothingWindowSlider(value: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 1f..21f,
            steps = 19,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "当前 $value 帧",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun FilterSlider(value: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "关", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(text = "强", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = "当前 ${(value * 100).roundToInt()}%",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
