package com.precisiontuner.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build

/**
 * Minimal wrapper around [AudioRecord] for continuous mono 16-bit PCM capture.
 *
 * Prefers [MediaRecorder.AudioSource.UNPROCESSED] on API 24+ (no AGC / noise
 * suppression, which is better for pitch detection) and falls back to
 * [MediaRecorder.AudioSource.MIC].
 */
class AudioInput(private val sampleRate: Int = 44100) {

    private var record: AudioRecord? = null

    val isRecording: Boolean get() = record?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    /** Opens and starts recording. Safe to call multiple times (idempotent). */
    fun start() {
        if (record != null) return
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, 8192)

        val sources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intArrayOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)
        } else {
            intArrayOf(MediaRecorder.AudioSource.MIC)
        }

        for (source in sources) {
            val r = AudioRecord(
                source,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (r.state == AudioRecord.STATE_INITIALIZED) {
                r.startRecording()
                record = r
                return
            }
            r.release()
        }
        error("AudioRecord initialization failed")
    }

    /**
     * Reads exactly [length] samples into [dest] starting at [offset].
     * Returns false on error or if recording stopped.
     */
    fun read(dest: ShortArray, offset: Int, length: Int): Boolean {
        val r = record ?: return false
        var total = 0
        while (total < length) {
            val n = r.read(dest, offset + total, length - total)
            if (n <= 0) return false
            total += n
        }
        return true
    }

    /** Stops and releases the recorder. Idempotent. */
    fun stop() {
        val r = record ?: return
        record = null
        try {
            if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                r.stop()
            }
        } catch (_: IllegalStateException) {
            // already stopped
        }
        r.release()
    }
}
