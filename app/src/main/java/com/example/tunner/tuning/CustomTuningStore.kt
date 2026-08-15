package com.example.tunner.tuning

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the user's custom tuning as a list of MIDI note numbers (1..8 strings)
 * and builds a [Tuning] from them.
 */
class CustomTuningStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _midis = MutableStateFlow(load())
    val midis: StateFlow<List<Int>> = _midis.asStateFlow()

    fun setMidis(list: List<Int>) {
        val v = list.map { it.coerceIn(MIN_MIDI, MAX_MIDI) }.coerceSize()
        _midis.value = v
        prefs.edit().putString(KEY, v.joinToString(",")).apply()
    }

    fun shiftString(index: Int, delta: Int) {
        val list = _midis.value.toMutableList()
        if (index in list.indices) {
            list[index] = (list[index] + delta).coerceIn(MIN_MIDI, MAX_MIDI)
            setMidis(list)
        }
    }

    fun addString() = setMidis(_midis.value + DEFAULT_NEW_MIDI)

    fun removeString() {
        if (_midis.value.size > 1) setMidis(_midis.value.dropLast(1))
    }

    fun tuning(): Tuning {
        val strings = _midis.value.mapIndexed { i, midi ->
            val name = NoteMapper.NOTE_NAMES[((midi % 12) + 12) % 12]
            val octave = midi / 12 - 1
            InstrumentString(i + 1, name, "$name$octave", midi)
        }
        return Tuning(CUSTOM_ID, "自定义", strings)
    }

    private fun load(): List<Int> {
        val raw = prefs.getString(KEY, null) ?: return DEFAULT
        val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in MIN_MIDI..MAX_MIDI }
        return if (parsed.isEmpty()) DEFAULT else parsed.coerceSize()
    }

    private fun List<Int>.coerceSize(): List<Int> = when {
        size > MAX_STRINGS -> take(MAX_STRINGS)
        size < 1 -> DEFAULT
        else -> this
    }

    companion object {
        const val CUSTOM_ID = "custom"
        private const val PREFS_NAME = "custom_tuning"
        private const val KEY = "midis"
        private const val MIN_MIDI = 24
        private const val MAX_MIDI = 96
        private const val MAX_STRINGS = 8
        private const val DEFAULT_NEW_MIDI = 64
        private val DEFAULT = listOf(64, 59, 55, 50, 45, 40) // standard guitar
    }
}
