package com.precisiontuner.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.precisiontuner.settings.AccentColor
import com.precisiontuner.settings.AppSettings
import com.precisiontuner.BuildConfig
import com.precisiontuner.settings.DetectionEngine
import com.precisiontuner.settings.GaugeStyle
import com.precisiontuner.settings.ThemeMode
import com.precisiontuner.settings.VisualMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onAccentChange: (AccentColor) -> Unit,
    onSensitivityThresholdChange: (Float) -> Unit,
    onSmoothingWindowChange: (Int) -> Unit,
    onFilterChange: (Float) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onVisualModeChange: (VisualMode) -> Unit,
    onGaugeStyleChange: (GaugeStyle) -> Unit,
    onDetectionEngineChange: (DetectionEngine) -> Unit,
    onManageTunings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        SectionTitle("调弦预设")
        OutlinedButton(onClick = onManageTunings, modifier = Modifier.fillMaxWidth()) {
            Text("管理自定义调弦")
        }
        Hint("添加、编辑或删除可持久保存的调弦预设")

        Spacer(Modifier.height(28.dp))
        SectionTitle("主题模式")
        SegmentedCapsule(
            labels = listOf("深色", "浅色"),
            selected = if (settings.themeMode == ThemeMode.DARK) 0 else 1,
            onSelect = { onThemeModeChange(if (it == 0) ThemeMode.DARK else ThemeMode.LIGHT) },
        )

        Spacer(Modifier.height(28.dp))
        SectionTitle("主题色")
        AccentRow(selected = settings.accent, onSelect = onAccentChange)

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

        if (BuildConfig.DEBUG && BuildConfig.TINY_CREPE_ENABLED) {
            Spacer(Modifier.height(28.dp))
            SectionTitle("实验检测引擎")
            DetectionEngineRow(settings.detectionEngine, onDetectionEngineChange)
            Hint("混合模式仅在整数倍冲突时由CREPE仲裁;主检测始终优先使用模型")
        }

        Spacer(Modifier.height(28.dp))
        SectionTitle("滤波强度")
        FilterSlider(value = settings.filterStrength, onChange = onFilterChange)
        Hint("强度越高,越能滤除高频噪声与泛音,低音弦更稳定")
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
