package com.sukisu.ultra

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.UserManager
import android.system.Os
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.ui.viewmodel.SuperUserViewModel
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.util.Locale

lateinit var ksuApp: KernelSUApplication

class KernelSUApplication : Application(), ViewModelStoreOwner {

    companion object {
        private const val TAG = "KernelSUApplication"

        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val applicationInfoClass = ApplicationInfo::class.java
                val method = applicationInfoClass.getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }

    lateinit var okhttpClient: OkHttpClient
    private val appViewModelStore by lazy { ViewModelStore() }

    /** Whether the user-storage configuration has been read into memory. */
    @Volatile
    private var loaded = false

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.sukisu.ultra.ui.util.LocaleHelper.wrap(base))
    }

    private fun isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked == true

    override fun onCreate() {
        super.onCreate()
        ksuApp = this

        // Needed by code that runs before the user unlocks too - the boot receiver's process, the
        // kernel's own helpers - and none of it lives in the user's storage.
        Os.setenv("TMPDIR", cacheDir.absolutePath, true)
        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "HyperSU/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }.build()
        runCatching {
            val webroot = File(dataDir, "webroot")
            if (!webroot.exists()) {
                webroot.mkdir()
            }
        }

        if (!isUserUnlocked()) {
            // Started before the user unlocked the device. The app is directBootAware, so a boot
            // broadcast can start this process, and it can still be alive when the user opens the
            // app afterwards. Returning here without doing anything else used to mean the
            // appearance, music and font configuration was never read for the life of that process:
            // the theme came back as the built-in one after a reboot even though every preference
            // and wallpaper file was still there.
            registerUnlockReceiver()
            return
        }

        ensureLoaded()
    }

    /**
     * Reads the configuration the first frame needs: wallpaper, typeface, music, sounds, switch
     * style and the superuser list.
     *
     * Idempotent, and a no-op until the user's storage is readable. Each load is isolated: a
     * preference that cannot be read (a corrupt or wrongly-typed entry left by an older build, a
     * truncated file) must cost the user a theme, never the app. Without this the exception escaped
     * onCreate, and since the failure happens before any UI exists there was no way to recover from
     * inside the app.
     */
    fun ensureLoaded() {
        if (loaded || !isUserUnlocked()) return
        loaded = true

        runCatching {
            // Wallpaper settings must be in memory before the first frame decides whether the
            // page background is transparent, so read them once here rather than in composition.
            com.sukisu.ultra.ui.theme.BackgroundConfig.load(this)
        }.onFailure { android.util.Log.e(TAG, "BackgroundConfig.load failed", it) }

        runCatching {
            // Same for the typeface: the opening frame already needs the right font family.
            com.sukisu.ultra.ui.theme.FontConfig.load(this)
        }.onFailure { android.util.Log.e(TAG, "FontConfig.load failed", it) }

        runCatching {
            // Background music: read its preferences first, then let the lifecycle callbacks own
            // playback so no player starts before the config is in memory.
            com.sukisu.ultra.ui.theme.MusicConfig.load(this)
        }.onFailure { android.util.Log.e(TAG, "MusicConfig.load failed", it) }

        runCatching {
            com.sukisu.ultra.ui.util.MusicManager.init(this)
        }.onFailure { android.util.Log.e(TAG, "MusicManager.init failed", it) }

        runCatching {
            // Click / startup sounds are read by SoundEffectManager on demand.
            com.sukisu.ultra.ui.theme.SoundEffectConfig.load(this)
        }.onFailure { android.util.Log.e(TAG, "SoundEffectConfig.load failed", it) }

        runCatching {
            // Read on demand by VibrationManager, the same way the sound settings are.
            com.sukisu.ultra.ui.theme.VibrationConfig.load(this)
        }.onFailure { android.util.Log.e(TAG, "VibrationConfig.load failed", it) }

        runCatching {
            // Both are read while composing every settings row, so they have to be in memory
            // before the first frame rather than loaded on demand.
            com.sukisu.ultra.ui.theme.SliderStyleConfig.load(this)
            com.sukisu.ultra.ui.component.SwitchIconState.showIcon =
                com.sukisu.ultra.data.repository.SettingsRepositoryImpl().showSwitchIcon
        }.onFailure { android.util.Log.e(TAG, "slider / switch style load failed", it) }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val enable = SettingsRepositoryImpl().enablePredictiveBack
                HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")
                setEnableOnBackInvokedCallback(applicationInfo, enable)
            }
        }.onFailure { android.util.Log.e(TAG, "predictive back setup failed", it) }

        runCatching {
            ViewModelProvider(this)[SuperUserViewModel::class.java].loadAppList()
        }.onFailure { android.util.Log.e(TAG, "superuser preload failed", it) }
    }

    /**
     * Waits for the device to be unlocked and then reads the configuration.
     *
     * The receiver unregisters itself on the first delivery; the activity also asks [ensureLoaded]
     * before it draws anything, so a delivery that arrives late is harmless.
     */
    private fun registerUnlockReceiver() {
        runCatching {
            val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED).apply {
                addAction(Intent.ACTION_BOOT_COMPLETED)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        runCatching { unregisterReceiver(this) }
                        ensureLoaded()
                    }
                },
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { android.util.Log.e(TAG, "could not wait for the user unlock", it) }
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}
