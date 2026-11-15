package com.sukisu.ultra.ui.util

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.sukisu.ultra.ui.theme.SoundEffectConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Plays the click and startup sounds, ported from FolkPatch `util/SoundEffectManager.kt`.
 *
 * One reused [MediaPlayer]: taps arrive far more often than a new player can be prepared, so
 * the existing one is reset rather than recreated.
 */
object SoundEffectManager {
    private const val TAG = "SoundEffectManager"
    private var mediaPlayer: MediaPlayer? = null

    // MediaPlayer must be created/accessed on the same thread; preparing is what may block, so
    // that part runs async.
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun play(context: Context) {
        if (!SoundEffectConfig.isSoundEffectEnabled) return
        scope.launch {
            playSound(
                context,
                SoundEffectConfig.sourceType,
                SoundEffectConfig.presetName,
                SoundEffectConfig.soundEffectFilename,
                "sound",
            )
        }
    }

    fun playStartup(context: Context) {
        if (!SoundEffectConfig.isStartupSoundEnabled) return
        scope.launch {
            playSound(
                context,
                SoundEffectConfig.startupSourceType,
                SoundEffectConfig.startupPresetName,
                SoundEffectConfig.startupSoundFilename,
                "start",
            )
        }
    }

    /** Whether a tap on a control in [scope] should make a sound. */
    fun shouldPlay(scope: String): Boolean =
        SoundEffectConfig.isSoundEffectEnabled &&
            (SoundEffectConfig.scope == SoundEffectConfig.SCOPE_GLOBAL ||
                SoundEffectConfig.scope == scope)

    /** [shouldPlay] plus [play], for the common "a control was tapped" call site. */
    fun playScoped(context: Context, scope: String) {
        if (shouldPlay(scope)) play(context)
    }

    private fun playSound(
        context: Context,
        sourceType: String,
        presetName: String,
        filename: String?,
        assetDir: String,
    ) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            } else {
                mediaPlayer?.reset()
            }

            mediaPlayer?.apply {
                if (sourceType == SoundEffectConfig.SOURCE_TYPE_PRESET) {
                    context.assets.openFd("$assetDir/$presetName.wav").use { afd ->
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                } else {
                    if (filename == null) return
                    val file = File(SoundEffectConfig.getSoundEffectDir(context), filename)
                    if (!file.exists()) return
                    setDataSource(context, Uri.fromFile(file))
                }

                setOnPreparedListener { mp -> mp.start() }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: $what, $extra")
                    mp.reset()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound effect", e)
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
