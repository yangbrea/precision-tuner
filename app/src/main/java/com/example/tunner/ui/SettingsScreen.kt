package com.example.tunner.ui

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tunner.settings.AccentColor
import com.example.tunner.settings.AppSettings
import com.example.tunner.settings.Sensitivity
import com.example.tunner.ui.theme.TunerOnDark
import com.example.tunner.ui.theme.TunerOnDarkMuted
import com.example.tunner.ui.theme.TunerSurfaceVariant
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onAccentChange: (AccentColor) -> Unit,
    onSensitivityChange: (Sensitivity) -> Unit,
    onFilterChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        SectionTitle("主题色")
        AccentRow(selected = settings.accent, onSelect = onAccentChange)

        Spacer(Modifier.height(28.dp))
        SectionTitle("响应灵敏度")
        SensitivityRow(selected = settings.sensitivity, onSelect = onSensitivityChange)
        Hint("高 = 响应快、指针较灵敏;低 = 更稳定、略慢")

        Spacer(Modifier.height(28.dp))
        SectionTitle("滤波强度")
        FilterSlider(value = settings.filterStrength, onChange = onFilterChange)
        Hint("强度越高,越能滤除高频噪声与泛音,低音弦更稳定")
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = TunerOnDark,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text = text, fontSize = 12.sp, color = TunerOnDarkMuted)
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
private fun SensitivityRow(selected: Sensitivity, onSelect: (Sensitivity) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Sensitivity.entries.forEach { s ->
            val isSelected = s == selected
            val accent = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) accent.copy(alpha = 0.2f) else TunerSurfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) accent else Color(0xFF2A2A30),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(s) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = s.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) accent else TunerOnDark,
                )
            }
        }
    }
}

@Composable
private fun FilterSlider(value: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "关", fontSize = 13.sp, color = TunerOnDarkMuted)
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = 0f..1f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(text = "强", fontSize = 13.sp, color = TunerOnDarkMuted)
        }
        Text(
            text = "当前 ${(value * 100).roundToInt()}%",
            fontSize = 13.sp,
            color = TunerOnDarkMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
