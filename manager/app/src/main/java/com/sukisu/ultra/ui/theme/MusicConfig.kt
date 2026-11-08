package com.sukisu.ultra.ui.theme

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Background-music preferences, ported from FolkPatch `ui/theme/MusicConfig.kt`.
 *
 * The file lives under `filesDir/music` rather than the URI the picker returned: a content URI
 * is only readable while the granting app keeps its permission, and this has to survive a reboot.
 */
object MusicConfig {
    private const val PREFS_NAME = "music_settings"
    private const val KEY_MUSIC_ENABLED = "music_enabled"
    private const val KEY_MUSIC_FILENAME = "music_filename"
    private const val KEY_AUTO_PLAY = "auto_play"
    private const val KEY_LOOPING_ENABLED = "looping_enabled"
    private const val KEY_VOLUME = "volume"
    private const val KEY_USER_PAUSED = "music_user_paused"
    private const val TAG = "MusicConfig"

    var isMusicEnabled: Boolean by mutableStateOf(false)
        private set

    var musicFilename: String? by mutableStateOf(null)
        private set

    var isAutoPlayEnabled: Boolean by mutableStateOf(false)
        private set

    var isLoopingEnabled: Boolean by mutableStateOf(false)
        private set

    var volume: Float by mutableFloatStateOf(1.0f)
        private set

    /**
     * Whether the user paused the music themselves, as opposed to the app pausing it when it went
     * to the background. Only this flag is persisted: reopening the app must not resume a track
     * the user deliberately stopped, but must resume one that was interrupted.
     */
    var isUserPaused: Boolean by mutableStateOf(false)
        private set

    fun setMusicEnabledState(enabled: Boolean) {
        isMusicEnabled = enabled
        // Turning music back on is an explicit request to hear it.
        if (enabled) isUserPaused = false
    }

    fun setAutoPlayEnabledState(enabled: Boolean) {
        isAutoPlayEnabled = enabled
    }

    fun setLoopingEnabledState(enabled: Boolean) {
        isLoopingEnabled = enabled
    }

    fun setMusicFilenameValue(filename: String?) {
        musicFilename = filename
    }

    fun setVolumeValue(value: Float) {
        volume = value
    }

    fun setUserPausedState(paused: Boolean) {
        isUserPaused = paused
    }

    fun getMusicDir(context: Context): File {
        val dir = File(context.filesDir, "music")
        // A plain file with the same name makes mkdirs fail silently, and the next write then
        // hits ENOTDIR; clear the placeholder and rebuild.
        if (!dir.isDirectory) {
            if (dir.exists()) dir.delete()
            dir.mkdirs()
        }
        return dir
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isMusicEnabled = prefs.booleanPref(KEY_MUSIC_ENABLED, false)
        musicFilename = prefs.stringPref(KEY_MUSIC_FILENAME, null)
        isAutoPlayEnabled = prefs.booleanPref(KEY_AUTO_PLAY, false)
        isLoopingEnabled = prefs.booleanPref(KEY_LOOPING_ENABLED, false)
        volume = prefs.floatPref(KEY_VOLUME, 1.0f)
        isUserPaused = prefs.booleanPref(KEY_USER_PAUSED, false)

        // A config pointing at a file that is no longer there must not leave the toggle on.
        if (isMusicEnabled && musicFilename != null) {
            val file = File(getMusicDir(context), musicFilename!!)
            if (!file.exists()) {
                isMusicEnabled = false
                musicFilename = null
                save(context)
            }
        }
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_MUSIC_ENABLED, isMusicEnabled)
            .putString(KEY_MUSIC_FILENAME, musicFilename)
            .putBoolean(KEY_AUTO_PLAY, isAutoPlayEnabled)
            .putBoolean(KEY_LOOPING_ENABLED, isLoopingEnabled)
            .putFloat(KEY_VOLUME, volume)
            .putBoolean(KEY_USER_PAUSED, isUserPaused)
            .apply()
    }

    /** Copies a picked audio file into filesDir and turns the music on. */
    fun saveMusicFile(context: Context, uri: Uri): Boolean {
        val newFilename = "background_music_${System.currentTimeMillis()}.mp3"
        val oldFilename = musicFilename
        val destFile = File(getMusicDir(context), newFilename)

        val written = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save music file", e)
            false
        }

        if (!written || destFile.length() == 0L) {
            destFile.delete()
            return false
        }

        // Drop the previous file only once the new one is safely on disk.
        if (oldFilename != null && oldFilename != newFilename) {
            File(getMusicDir(context), oldFilename).delete()
        }

        isMusicEnabled = true
        musicFilename = newFilename
        save(context)
        return true
    }

    fun clearMusic(context: Context) {
        musicFilename?.let { File(getMusicDir(context), it).delete() }
        isMusicEnabled = false
        musicFilename = null
        isUserPaused = false
        save(context)
    }

    fun getMusicFile(context: Context): File? {
        val filename = musicFilename ?: return null
        val file = File(getMusicDir(context), filename)
        return if (file.exists()) file else null
    }
}
