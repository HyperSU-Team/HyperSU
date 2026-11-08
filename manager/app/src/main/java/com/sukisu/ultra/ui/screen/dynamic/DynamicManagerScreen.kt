package com.sukisu.ultra.ui.screen.dynamic

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.util.clearDynamicManager
import com.sukisu.ultra.ui.util.getDynamicManagerConfig
import com.sukisu.ultra.ui.util.setDynamicManagerApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Multi-manager adaptation: which signature, besides this manager's own, the kernel accepts.
 *
 * The kernel keeps one extra signature slot ([KSU_SIGNATURE_INDEX_DYNAMIC_MANAGER]) and `ksud`
 * reads the signature straight out of an APK, so the screen only has to name the APK and let the
 * daemon do the hashing.
 */
@Composable
fun DynamicManagerScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(DynamicManagerState()) }

    suspend fun reload() {
        val (config, apps) = withContext(Dispatchers.IO) {
            getDynamicManagerConfig() to installedManagerApps(context.packageManager)
        }
        state = DynamicManagerState(
            size = config?.size ?: 0,
            hash = config?.hash ?: "",
            apps = apps,
            loading = false,
        )
    }

    LaunchedEffect(Unit) { reload() }

    fun toast(message: Int) {
        Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
    }

    val actions = DynamicManagerActions(
        onBack = { navigator.pop() },
        onSelectApp = { app ->
            if (!state.submitting) {
                state = state.copy(submitting = true)
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { setDynamicManagerApk(app.apkPath) }
                    reload()
                    toast(
                        if (ok) R.string.dynamic_manager_set_success
                        else R.string.dynamic_manager_set_failed,
                    )
                }
            }
        },
        onClear = {
            if (!state.submitting && state.isSet) {
                state = state.copy(submitting = true)
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { clearDynamicManager() }
                    reload()
                    toast(
                        if (ok) R.string.dynamic_manager_cleared
                        else R.string.dynamic_manager_clear_failed,
                    )
                }
            }
        },
    )

    DynamicManagerFolk(state, actions)
}

/** Managers are the apps that ship `libksud.so`; the kernel identifies them that way too. */
private fun installedManagerApps(pm: PackageManager): List<DynamicManagerApp> =
    runCatching {
        pm.getInstalledPackages(0)
            .mapNotNull { info ->
                val app = info.applicationInfo ?: return@mapNotNull null
                val libDir = app.nativeLibraryDir ?: return@mapNotNull null
                if (!File(libDir, "libksud.so").isFile) return@mapNotNull null
                val apkPath = app.publicSourceDir ?: return@mapNotNull null
                DynamicManagerApp(
                    label = app.loadLabel(pm).toString().ifBlank { app.packageName },
                    packageName = app.packageName,
                    apkPath = apkPath,
                )
            }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
