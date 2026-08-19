package com.precisiontuner.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * Plays the one-shot "wrong answer" cue (error_003.wav from the bundled Kenney
 * CC0 interface-sounds set) via a [SoundPool], mirroring [CueSoundPlayer].
 *
 * The asset is loaded asynchronously; [play] is a no-op until the load
 * completes (effectively instant for a bundled asset).
 */
class ErrorSoundPlayer(context: Context) {

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
            Log.e(TAG, "error sound asset missing: $ASSET_PATH", it)
            0
        }
        if (soundId != 0) {
            soundPool.setOnLoadCompleteListener { _, id, status ->
                if (id == soundId && status == 0) loaded = true
            }
        }
    }

    /** Plays the error cue once; safe to call repeatedly (overlapping retriggers stop). */
    fun play() {
        if (!loaded || soundId == 0) return
        soundPool.play(soundId, PLAY_VOLUME, PLAY_VOLUME, 1, 0, 1f)
    }

    /** Releases the pool. Must be called when the owner is disposed. */
    fun close() {
        soundPool.release()
    }

    private companion object {
        const val TAG = "ErrorSound"
        const val ASSET_PATH = "sounds/error_003.wav"
        const val MAX_STREAMS = 2
        const val PLAY_VOLUME = 0.9f
    }
}
