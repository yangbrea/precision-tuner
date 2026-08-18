package com.precisiontuner

import com.precisiontuner.pitch.YinPitchDetector
import com.precisiontuner.tuning.NoteMapper
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log

/**
 * Diagnostic: measure the pitch the app's own YIN detector reads on each
 * bundled reference-tone sample, so the playback engine can be calibrated
 * to play back in tune.
 */
class ReferenceSamplePitchTest {

    private fun readWavMono(path: File): Pair<ShortArray, Int> {
        val buf = ByteBuffer.wrap(path.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.int == 0x46464952) { "not RIFF" } // RIFF
        buf.int // size
        require(buf.int == 0x45564157) { "not WAVE" } // WAVE
        require(buf.int == 0x20746D66) { "no fmt " } // fmt
        val fmtSize = buf.int
        val formatCode = buf.short.toInt()
        require(formatCode == 1 || formatCode == 0xFFFE) { "not PCM (code=$formatCode)" }
        val channels = buf.short.toInt()
        val sampleRate = buf.int
        buf.int // byte rate
        buf.short // block align
        buf.short // bits per sample
        if (fmtSize > 16) {
            buf.position(buf.position() + fmtSize - 16)
        }
        while (buf.remaining() >= 8) {
            val chunkId = buf.int
            val chunkSize = buf.int
            if (chunkId == 0x61746164) { // data
                val sampleCount = chunkSize / 2
                val mono = ShortArray(sampleCount / channels)
                for (i in mono.indices) {
                    buf.position(buf.position() + (channels - 1) * 2)
                    mono[i] = buf.short
                }
                return mono to sampleRate
            }
            buf.position(buf.position() + chunkSize + (chunkSize and 1))
        }
        error("no data chunk")
    }

    @Test
    fun `measure yin pitch of each reference sample`() {
        val dir = File("app/src/main/assets/reference/piano").takeIf { it.isDirectory }
            ?: File("src/main/assets/reference/piano")
        require(dir.isDirectory) { "cannot find piano assets dir: $dir" }

        val yin = YinPitchDetector()
        val results = mutableListOf<Triple<Int, Double, Double>>()

        dir.listFiles().orEmpty().sortedBy { it.name }.forEach { wav ->
            val midi = wav.nameWithoutExtension.toInt()
            val (mono, sampleRate) = readWavMono(wav)
            val start = (0.4 * sampleRate).toInt()
            val len = (1.2 * sampleRate).toInt()
            val end = (start + len).coerceAtMost(mono.size)
            val window = mono.copyOfRange(start, end)

            var best: Double? = null
            var bestConf = 0.0
            var off = 0
            while (off + 4096 <= window.size) {
                val p = yin.detect(window.copyOfRange(off, off + 4096), sampleRate)
                if (p != null && p.confidence > bestConf) {
                    bestConf = p.confidence
                    best = p.frequency
                }
                off += 2048
            }
            val nominal = NoteMapper.frequencyFromMidi(midi)
            val cents = best?.let { 1200 * log(it / nominal, 2.0) } ?: Double.NaN
            results.add(Triple(midi, best ?: 0.0, cents))
        }

        results.forEach { (midi, measured, cents) ->
            println("midi=$midi measured=${"%.2f".format(measured)}Hz cents=${"%.1f".format(cents)}")
        }
        val valid = results.filter { it.third.isFinite() }
        if (valid.isNotEmpty()) {
            val cents = valid.map { it.third }
            println("AVG=${"%.1f".format(cents.average())} " +
                "MED=${"%.1f".format(cents.sorted()[valid.size / 2])} " +
                "MIN=${"%.1f".format(cents.min())} MAX=${"%.1f".format(cents.max())}")
        } else {
            println("NO VALID READINGS")
        }
    }
}
