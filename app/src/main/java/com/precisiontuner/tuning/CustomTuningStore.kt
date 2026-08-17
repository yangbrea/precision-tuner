package com.precisiontuner.tuning

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CustomTuningPreset(val id: String, val name: String, val instrumentId: String?, val midis: List<Int>) {
    val tuningId: String get() = "$TUNING_ID_PREFIX$id"

    fun toTuning() = Tuning(tuningId, name, midis.mapIndexed { index, midi ->
        val note = NoteMapper.NOTE_NAMES[Math.floorMod(midi, 12)]
        InstrumentString(index + 1, note, "$note${midi / 12 - 1}", midi)
    })

    companion object { const val TUNING_ID_PREFIX = "custom:" }
}

enum class SavePresetResult { SAVED, EMPTY_NAME, DUPLICATE_NAME, INVALID_STRINGS }

/** Persists named presets. The old single-custom-tuning preference is intentionally ignored. */
class CustomTuningStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _presets = MutableStateFlow(load())
    val presets: StateFlow<List<CustomTuningPreset>> = _presets.asStateFlow()

    fun create(name: String, instrumentId: String?, midis: List<Int>): SavePresetResult {
        val result = validate(name, instrumentId, midis, null)
        if (result != SavePresetResult.SAVED) return result
        save(_presets.value + normalized(UUID.randomUUID().toString(), name, instrumentId, midis))
        return result
    }

    fun update(id: String, name: String, instrumentId: String?, midis: List<Int>): SavePresetResult {
        if (_presets.value.none { it.id == id }) return SavePresetResult.INVALID_STRINGS
        val result = validate(name, instrumentId, midis, id)
        if (result != SavePresetResult.SAVED) return result
        save(_presets.value.map { if (it.id == id) normalized(id, name, instrumentId, midis) else it })
        return result
    }

    fun delete(id: String): CustomTuningPreset? {
        val deleted = preset(id) ?: return null
        save(_presets.value.filterNot { it.id == id })
        return deleted
    }

    fun preset(id: String) = _presets.value.firstOrNull { it.id == id }
    fun presetForTuningId(tuningId: String) = tuningId.takeIf { it.startsWith(CustomTuningPreset.TUNING_ID_PREFIX) }
        ?.removePrefix(CustomTuningPreset.TUNING_ID_PREFIX)?.let(::preset)

    private fun validate(name: String, instrumentId: String?, midis: List<Int>, editingId: String?): SavePresetResult = when {
        name.trim().isEmpty() -> SavePresetResult.EMPTY_NAME
        midis.size !in MIN_STRINGS..MAX_STRINGS -> SavePresetResult.INVALID_STRINGS
        _presets.value.any { it.id != editingId && it.instrumentId == validInstrument(instrumentId) && it.name.equals(name.trim(), true) } -> SavePresetResult.DUPLICATE_NAME
        else -> SavePresetResult.SAVED
    }

    private fun normalized(id: String, name: String, instrumentId: String?, midis: List<Int>) =
        CustomTuningPreset(id, name.trim(), validInstrument(instrumentId), midis.map { it.coerceIn(MIN_MIDI, MAX_MIDI) })

    private fun validInstrument(id: String?) = id?.takeIf { InstrumentCatalog.instrument(it) != null }

    private fun save(value: List<CustomTuningPreset>) {
        _presets.value = value
        val json = JSONArray()
        value.forEach { preset -> json.put(JSONObject().apply {
            put("id", preset.id); put("name", preset.name)
            put("instrumentId", preset.instrumentId ?: JSONObject.NULL)
            put("midis", JSONArray(preset.midis))
        }) }
        prefs.edit().putString(KEY_PRESETS, json.toString()).apply()
    }

    private fun load(): List<CustomTuningPreset> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PRESETS, "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val notes = item.getJSONArray("midis")
                val preset = CustomTuningPreset(
                    item.getString("id"), item.getString("name").trim(),
                    item.optString("instrumentId").takeIf { it.isNotBlank() && InstrumentCatalog.instrument(it) != null },
                    (0 until notes.length()).map { notes.getInt(it).coerceIn(MIN_MIDI, MAX_MIDI) },
                )
                if (preset.id.isNotBlank() && preset.name.isNotBlank() && preset.midis.size in MIN_STRINGS..MAX_STRINGS) add(preset)
            }
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    companion object {
        const val CUSTOM_INSTRUMENT_ID = "custom"
        const val MIN_MIDI = 24
        const val MAX_MIDI = 96
        const val MIN_STRINGS = 1
        const val MAX_STRINGS = 8
        const val DEFAULT_NEW_MIDI = 60
        private const val PREFS_NAME = "custom_tuning_presets"
        private const val KEY_PRESETS = "presets_v1"
    }
}
