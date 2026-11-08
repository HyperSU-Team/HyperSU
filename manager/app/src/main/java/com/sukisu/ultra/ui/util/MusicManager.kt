package com.sukisu.ultra.ui.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.sukisu.ultra.ui.theme.MusicConfig
import java.io.File

/**
 * Owns the background-music player, ported from FolkPatch `util/MusicManager.kt`.
 *
 * FolkPatch parks a `ProcessLifecycleOwner` observer for foreground/background; this build has
 * no `lifecycle-process` dependency, so the same behaviour is derived from
 * [Application.ActivityLifecycleCallbacks]: the count of started activities decides when to
 * pause and when to resume.
 */
object MusicManager {
    private const val TAG = "MusicManager"
    private var mediaPlayer: MediaPlayer? = null
    private var appContext: Context? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private var startedActivities = 0
    private var callbacksRegistered = false
    private var pausedByBackground = false

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
            if (startedActivities == 1) onAppForeground()
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            if (startedActivities == 0) onAppBackground()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    /** Playback never starts from [init]: the first started activity does it, so a player that
     *  is still preparing is never asked to start twice. */
    fun init(ctx: Context) {
        // WebUIActivity runs in a separate ":webui" process; a second MediaPlayer there would
        // play over the one in the main process.
        if (!isMainProcess(ctx)) return

        appContext = ctx.applicationContext
        if (!callbacksRegistered) {
            (ctx.applicationContext as? Application)
                ?.registerActivityLifecycleCallbacks(activityCallbacks)
            callbacksRegistered = true
        }
    }

    private fun isMainProcess(ctx: Context): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                ?: return true
            val pid = android.os.Process.myPid()
            am.runningAppProcesses?.find { it.pid == pid }?.processName
        }
        return processName == null || processName == ctx.packageName
    }

    private fun onAppForeground() {
        if (!MusicConfig.isMusicEnabled) return
        // A track the user paused stays paused across app restarts; only an interruption by the
        // app going to the background is resumed.
        if (MusicConfig.isUserPaused) {
            pausedByBackground = false
            return
        }
        // Resume what was interrupted, or start fresh when auto-play is on. Auto-play alone
        // would leave a track the user just started silent after every trip to the background.
        if (pausedByBackground || MusicConfig.isAutoPlayEnabled) {
            if (mediaPlayer == null) prepareAndPlay() else play()
        }
        pausedByBackground = false
    }

    private fun onAppBackground() {
        pausedByBackground = _isPlaying.value
        pauseInternal()
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                if (mediaPlayer?.isPlaying == true) {
                    _currentPosition.value = mediaPlayer?.currentPosition ?: 0
                }
                delay(1000)
            }
        }
    }

    private fun prepareAndPlay() {
        val context = appContext ?: return
        val file = MusicConfig.getMusicFile(context) ?: return

        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            } else {
                mediaPlayer?.reset()
            }

            mediaPlayer?.apply {
                setDataSource(context, Uri.fromFile(file))
                setVolume(MusicConfig.volume, MusicConfig.volume)
                isLooping = MusicConfig.isLoopingEnabled
                setOnPreparedListener { mp ->
                    mp.start()
                    _duration.value = mp.duration
                    _isPlaying.value = true
                    startProgressUpdater()
                }
                setOnCompletionListener {
                    if (!isLooping) {
                        _isPlaying.value = false
                        _currentPosition.value = 0
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    _isPlaying.value = false
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play music", e)
            _isPlaying.value = false
        }
    }

    fun play() {
        markUserPaused(false)
        if (mediaPlayer == null) {
            prepareAndPlay()
        } else {
            try {
                if (mediaPlayer?.isPlaying != true) {
                    mediaPlayer?.start()
                    _isPlaying.value = true
                    startProgressUpdater()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in play()", e)
                // Recover: the player may have been reset while it was still preparing.
                prepareAndPlay()
            }
        }
    }

    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
            _currentPosition.value = position
        } catch (e: Exception) {
            Log.e(TAG, "Error in seekTo()", e)
        }
    }

    fun pause() {
        markUserPaused(true)
        pauseInternal()
    }

    /** Pauses without recording it as the user's decision (used when the app leaves the screen). */
    private fun pauseInternal() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _isPlaying.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in pause()", e)
        }
    }

    /** Remembers whether the pause came from the user, so reopening the app does not resume it. */
    private fun markUserPaused(paused: Boolean) {
        if (MusicConfig.isUserPaused == paused) return
        MusicConfig.setUserPausedState(paused)
        appContext?.let { MusicConfig.save(it) }
    }

    fun toggle() {
        if (_isPlaying.value) pause() else play()
    }

    fun stop() {
        try {
            progressJob?.cancel()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            _isPlaying.value = false
            _currentPosition.value = 0
            _duration.value = 0
        } catch (e: Exception) {
            Log.e(TAG, "Error in stop()", e)
        }
    }

    fun updateVolume(volume: Float) {
        try {
            mediaPlayer?.setVolume(volume, volume)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume", e)
        }
    }

    fun updateLooping(looping: Boolean) {
        try {
            mediaPlayer?.isLooping = looping
        } catch (e: Exception) {
            Log.e(TAG, "Error setting looping", e)
        }
    }

    /**
     * Called when a setting or the file itself changed.
     *
     * The "was it playing" question has to be read before [stop], which clears the flag -
     * asking afterwards makes that half of the condition dead and a track that was audible
     * would stay silent after any setting change.
     */
    fun reload() {
        val wasPlaying = _isPlaying.value
        stop()
        if (!MusicConfig.isUserPaused &&
            MusicConfig.isMusicEnabled &&
            (MusicConfig.isAutoPlayEnabled || wasPlaying)
        ) {
            prepareAndPlay()
        }
    }
}
