package com.sukisu.ultra

import android.app.Application
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

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.sukisu.ultra.ui.util.LocaleHelper.wrap(base))
    }

    private fun isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked == true

    override fun onCreate() {
        super.onCreate()
        ksuApp = this

        if (!isUserUnlocked()) {
            return
        }

        // Appearance configuration is read here because the first frame needs it. Each load is
        // isolated: a preference that cannot be read (a corrupt or wrongly-typed entry left by an
        // older build, a truncated file) must cost the user a theme, never the app. Without this
        // the exception escaped onCreate, and since the failure happens before any UI exists there
        // was no way to recover from inside the app.
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val enable = SettingsRepositoryImpl().enablePredictiveBack
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")
            setEnableOnBackInvokedCallback(applicationInfo, enable)
        }

        val superUserViewModel = ViewModelProvider(this)[SuperUserViewModel::class.java]
        superUserViewModel.loadAppList()

        val webroot = File(dataDir, "webroot")
        if (!webroot.exists()) {
            webroot.mkdir()
        }

        // Provide working env for rust's temp_dir()
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
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}
