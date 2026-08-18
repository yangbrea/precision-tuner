package com.precisiontuner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.precisiontuner.tuning.CustomTuningPreset
import com.precisiontuner.tuning.CustomTuningStore
import com.precisiontuner.tuning.InstrumentCatalog
import com.precisiontuner.tuning.NoteMapper
import com.precisiontuner.tuning.SavePresetResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningPresetScreen(
    presets: List<CustomTuningPreset>,
    onCreate: (String, String?, List<Int>) -> SavePresetResult,
    onUpdate: (String, String, String?, List<Int>) -> SavePresetResult,
    onDelete: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<CustomTuningPreset?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CustomTuningPreset?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Button(onClick = { showNew = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("新增预设")
        }
        Spacer(Modifier.height(20.dp))
        if (presets.isEmpty()) {
            Text("还没有自定义调弦", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val groups = InstrumentCatalog.instruments.map { it.id to it.name } + (null to "未分类")
            groups.forEach { (instrumentId, label) ->
                val items = presets.filter { it.instrumentId == instrumentId }
                if (items.isNotEmpty()) {
                    Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    items.forEach { preset ->
                        ListItem(
                            headlineContent = { Text(preset.name) },
                            supportingContent = { Text(preset.midis.joinToString("  ") { midiLabel(it) }) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { editing = preset }) { Icon(Icons.Filled.Edit, "编辑") }
                                    IconButton(onClick = { deleting = preset }) { Icon(Icons.Filled.Delete, "删除") }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }

    if (showNew || editing != null) {
        PresetEditor(
            preset = editing,
            onDismiss = { showNew = false; editing = null },
            onSave = { name, instrumentId, midis ->
                editing?.let { onUpdate(it.id, name, instrumentId, midis) } ?: onCreate(name, instrumentId, midis)
            },
        )
    }

    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除预设？") },
            text = { Text("“${preset.name}”删除后无法恢复。") },
            confirmButton = { TextButton(onClick = { onDelete(preset.id); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetEditor(
    preset: CustomTuningPreset?,
    onDismiss: () -> Unit,
    onSave: (String, String?, List<Int>) -> SavePresetResult,
) {
    var name by remember(preset?.id) { mutableStateOf(preset?.name.orEmpty()) }
    var instrumentId by remember(preset?.id) { mutableStateOf(preset?.instrumentId) }
    var midis by remember(preset?.id) { mutableStateOf(preset?.midis ?: listOf(CustomTuningStore.DEFAULT_NEW_MIDI)) }
    var menuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(if (preset == null) "新增调弦预设" else "编辑调弦预设", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it; error = null }, label = { Text("预设名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Box {
                OutlinedTextField(
                    value = instrumentId?.let { InstrumentCatalog.instrument(it)?.name } ?: "未分类",
                    onValueChange = {}, readOnly = true, label = { Text("关联乐器") },
                    modifier = Modifier.fillMaxWidth().clickable { menuOpen = true },
                )
                Box(Modifier.matchParentSize().clip(RoundedCornerShape(4.dp)).clickable { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("未分类") }, onClick = { instrumentId = null; menuOpen = false })
                    InstrumentCatalog.instruments.forEach { inst ->
                        DropdownMenuItem(text = { Text(inst.name) }, onClick = { instrumentId = inst.id; menuOpen = false })
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            midis.forEachIndexed { index, midi ->
                var draft by remember(midis, index) { mutableStateOf<String?>(null) }
                var error by remember(midis, index) { mutableStateOf<String?>(null) }
                val focusManager = LocalFocusManager.current
                val isLast = index == midis.lastIndex
                val commit: () -> Unit = {
                    val text = draft
                    if (text != null) {
                        val trimmed = text.trim()
                        if (trimmed.isEmpty()) {
                            draft = null
                            error = null
                        } else {
                            val parsed = NoteMapper.midiFromName(trimmed, midi / 12 - 1)
                            when {
                                parsed == null -> {
                                    // Prompt, then revert the field to the pitch it had before typing.
                                    error = "无法识别,示例:E4、C#3、Bb2"
                                    draft = null
                                }
                                parsed < CustomTuningStore.MIN_MIDI || parsed > CustomTuningStore.MAX_MIDI -> {
                                    error = "超出 C1–C7 范围"
                                    draft = null
                                }
                                else -> {
                                    midis = midis.toMutableList().also { it[index] = parsed }
                                    draft = null
                                    error = null
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("第${index + 1}弦", modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            draft = null; error = null
                            midis = midis.toMutableList().also { it[index] = (midi - 1).coerceAtLeast(CustomTuningStore.MIN_MIDI) }
                        }) { Icon(Icons.Filled.Remove, "降半音") }
                        OutlinedTextField(
                            value = draft ?: midiLabel(midi),
                            onValueChange = { draft = it; error = null },
                            singleLine = true,
                            isError = error != null,
                            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Text,
                                imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { commit(); focusManager.clearFocus() },
                                onNext = { commit(); focusManager.moveFocus(FocusDirection.Down) },
                            ),
                            modifier = Modifier
                                .width(88.dp)
                                .onFocusChanged { if (!it.isFocused) commit() },
                        )
                        IconButton(onClick = {
                            draft = null; error = null
                            midis = midis.toMutableList().also { it[index] = (midi + 1).coerceAtMost(CustomTuningStore.MAX_MIDI) }
                        }) { Icon(Icons.Filled.Add, "升半音") }
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { if (midis.size < CustomTuningStore.MAX_STRINGS) midis = midis + CustomTuningStore.DEFAULT_NEW_MIDI },
                    enabled = midis.size < CustomTuningStore.MAX_STRINGS, modifier = Modifier.weight(1f),
                ) { Text("添加弦") }
                OutlinedButton(
                    onClick = { if (midis.size > 1) midis = midis.dropLast(1) },
                    enabled = midis.size > 1, modifier = Modifier.weight(1f),
                ) { Text("删除弦") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(onClick = {
                    when (onSave(name, instrumentId, midis)) {
                        SavePresetResult.SAVED -> onDismiss()
                        SavePresetResult.EMPTY_NAME -> error = "请输入预设名称"
                        SavePresetResult.DUPLICATE_NAME -> error = "该分类下已有同名预设"
                        SavePresetResult.INVALID_STRINGS -> error = "调弦必须包含 1–8 根有效弦"
                    }
                }) { Text("保存") }
            }
        }
    }
}

private fun midiLabel(midi: Int): String {
    val name = NoteMapper.NOTE_NAMES[Math.floorMod(midi, 12)]
    return "$name${midi / 12 - 1}"
}
