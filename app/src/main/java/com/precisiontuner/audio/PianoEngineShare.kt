package com.precisiontuner.audio

import android.content.Context

/**
 * Reference-counted process-wide holder for the piano sample engine.
 *
 * Both the tuner (reference tone) and the ear-training module play piano
 * strikes, and the 62 bundled WAVs decode to roughly 10 MB of PCM inside a
 * SoundPool, so a single shared instance is used instead of loading two pools.
 * The engine is closed when the last holder releases it.
 */
object PianoEngineShare {

    private var engine: PianoReferenceEngine? = null
    private var holders = 0

    @Synchronized
    fun acquire(context: Context): PianoReferenceEngine {
        holders++
        return engine ?: PianoReferenceEngine(context.applicationContext).also { engine = it }
    }

    @Synchronized
    fun release(engine: PianoReferenceEngine) {
        if (this.engine !== engine) return
        holders--
        if (holders <= 0) {
            holders = 0
            this.engine?.close()
            this.engine = null
        }
    }
}
