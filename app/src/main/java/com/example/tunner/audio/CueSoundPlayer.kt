package com.example.tunner.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * Plays the one-shot "in tune" cue ("ding") from the bundled Kenney CC0
 * confirmation sound via a [SoundPool].
 *
 * The asset is loaded asynchronously; [play] is a no-op until the load
 * completes (which is effectively instant for a bundled asset).
 */
class CueSoundPlayer(context: Context) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private var soundId = 0
    private var loaded = false

    init {
        soundId = runCatching {
            context.applicationContext.assets.openFd(ASSET_PATH).use { fd ->
                soundPool.load(fd, 1)
            }
        }.getOrElse {
            Log.e(TAG, "cue sound asset missing: $ASSET_PATH", it)
            0
        }
        if (soundId != 0) {
            soundPool.setOnLoadCompleteListener { _, id, status ->
                if (id == soundId && status == 0) loaded = true
            }
        }
    }

    /** Plays the cue once; safe to call repeatedly (overlapping retriggers stop). */
    fun play() {
        if (!loaded || soundId == 0) return
        soundPool.play(soundId, PLAY_VOLUME, PLAY_VOLUME, 1, 0, 1f)
    }

    /** Releases the pool. Must be called when the owner is disposed. */
    fun close() {
        soundPool.release()
    }

    private companion object {
        const val TAG = "CueSound"
        const val ASSET_PATH = "sounds/confirmation_002.wav"
        const val MAX_STREAMS = 2
        const val PLAY_VOLUME = 0.9f
    }
}
