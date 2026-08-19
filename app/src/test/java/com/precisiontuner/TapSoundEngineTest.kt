package com.precisiontuner

import com.precisiontuner.audio.TapSoundEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class TapSoundEngineTest {

    /** Builds a minimal 44-byte-header 16-bit mono WAV with [frames] samples. */
    private fun wavBytes(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val bytes = ByteArray(44 + dataSize)
        // RIFF header, PCM, mono, 44100 Hz, 16-bit (only size fields matter here).
        bytes[22] = 1 // channels = 1
        bytes[24] = 0x44.toByte(); bytes[25] = 0xAC.toByte() // 44100 = 0xAC44
        bytes[34] = 16 // bits per sample
        bytes[40] = (dataSize and 0xFF).toByte()
        bytes[41] = ((dataSize shr 8) and 0xFF).toByte()
        bytes[42] = ((dataSize shr 16) and 0xFF).toByte()
        bytes[43] = ((dataSize shr 24) and 0xFF).toByte()
        samples.forEachIndexed { i, s ->
            bytes[44 + i * 2] = (s.toInt() and 0xFF).toByte()
            bytes[45 + i * 2] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    @Test fun `decodes 16-bit mono pcm after the standard header`() {
        val samples = shortArrayOf(-1, 0, 1, 32767, -32768)
        val pcm = TapSoundEngine.decodePcm(wavBytes(samples), attackMs = 1000)
        assertEquals(5, pcm.size)
        assertEquals(-1, pcm[0].toInt())
        assertEquals(0, pcm[1].toInt())
        assertEquals(1, pcm[2].toInt())
        assertEquals(32767, pcm[3].toInt())
        assertEquals(-32768, pcm[4].toInt())
    }

    @Test fun `attack window truncates longer samples`() {
        // 44100 frames would be 1 s; with 100 ms only 4410 frames are kept.
        val samples = ShortArray(44100)
        val pcm = TapSoundEngine.decodePcm(wavBytes(samples), attackMs = 100)
        assertEquals(4410, pcm.size)
    }

    @Test fun `attack window never exceeds the sample length`() {
        val samples = ShortArray(100)
        val pcm = TapSoundEngine.decodePcm(wavBytes(samples), attackMs = 5000)
        assertEquals(100, pcm.size)
    }
}
