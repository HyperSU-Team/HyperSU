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
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.dialog.ConfirmResult
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.util.clearDynamicManager
import com.sukisu.ultra.ui.util.getDynamicManagerConfig
import com.sukisu.ultra.ui.util.setDynamicManagerApk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Multi-manager adaptation: which signatures, besides this manager's own, the kernel accepts.
 *
 * The kernel reports its active managers as (uid, signature index) pairs; the index is what tells
 * a manager app apart from an ordinary app, and 255 is the dynamic slot this screen writes to.
 */
@Composable
fun DynamicManagerScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()
    var state by remember { mutableStateOf(DynamicManagerState()) }

    suspend fun reload() {
        val (config, apps) = withContext(Dispatchers.IO) {
            // A failing root shell must not crash the screen or leave it stuck in `submitting`.
            runCatching { getDynamicManagerConfig() }.getOrNull() to loadManagerApps(context.packageManager)
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

    fun grant(app: DynamicManagerApp) {
        if (state.submitting) return
        scope.launch {
            val confirmed = confirmDialog.awaitConfirm(
                title = context.getString(R.string.dynamic_manager_grant_confirm_title),
                content = context.getString(R.string.dynamic_manager_grant_confirm_message, app.label),
                confirm = context.getString(R.string.dynamic_manager_grant_confirm_action),
            )
            if (confirmed != ConfirmResult.Confirmed) return@launch
            state = state.copy(submitting = true)
            val ok = withContext(Dispatchers.IO) {
                runCatching { setDynamicManagerApk(app.apkPath) }.getOrDefault(false)
            }
            reload()
            toast(
                if (ok) R.string.dynamic_manager_set_success
                else R.string.dynamic_manager_set_failed,
            )
        }
    }

    val actions = DynamicManagerActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSelectApp = { app -> if (!app.isAuthorized) grant(app) },
        onClear = {
            if (!state.submitting && state.isSet) {
                scope.launch {
                    val confirmed = confirmDialog.awaitConfirm(
                        title = context.getString(R.string.dynamic_manager_clear_confirm_title),
                        content = context.getString(R.string.dynamic_manager_clear_confirm_message),
                        confirm = context.getString(android.R.string.ok),
                    )
                    if (confirmed != ConfirmResult.Confirmed) return@launch
                    state = state.copy(submitting = true)
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { clearDynamicManager() }.getOrDefault(false)
                    }
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

/**
 * Whether the app ships `libksud.so`.
 *
 * The extracted library directory is the quick answer, but a modern APK keeps its native libraries
 * inside the archive (`extractNativeLibs=false`), so that directory holds no `libksud.so` even
 * though the app ships one - which is why this list showed only this manager, whose own manifest
 * still extracts them. The archive is checked as well.
 */
private fun shipsKsudLibrary(app: android.content.pm.ApplicationInfo, apkPath: String): Boolean {
    val libDir = app.nativeLibraryDir
    if (libDir != null && MANAGER_LIBRARIES.any { File(libDir, it).isFile }) return true
    return runCatching {
        java.util.zip.ZipFile(apkPath).use { zip ->
            zip.entries().asSequence().any { entry ->
                val name = entry.name
                name.startsWith("lib/") && name.substringAfterLast('/') in MANAGER_LIBRARIES
            }
        }
    }.getOrDefault(false)
}

/**
 * Native libraries that only a root manager ships.
 *
 * `libksud.so` covers the KernelSU family. `libkptools.so` / `libapd.so` are APatch's tooling - its
 * manager carries neither a `ksud` nor an APatch `ksud`, so those two names are what identify it.
 * Only the file name is compared: the ABI directory varies and a manager may ship a single ABI.
 */
private val MANAGER_LIBRARIES = setOf("libksud.so", "libkptools.so", "libapd.so")

/**
 * Manager apps are the ones that ship `libksud.so`; the kernel's manager table says which uid is
 * currently authorized and, if so, through which signature slot.
 */
private fun loadManagerApps(pm: PackageManager): List<DynamicManagerApp> {
    val indexes = runCatching { readManagerIndexes() }.getOrDefault(emptyMap())
    return runCatching {
        pm.getInstalledPackages(0)
            .mapNotNull { info ->
                val app = info.applicationInfo ?: return@mapNotNull null
                val apkPath = app.publicSourceDir ?: return@mapNotNull null
                if (!shipsKsudLibrary(app, apkPath)) return@mapNotNull null
                val icon = runCatching {
                    pm.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
                }.getOrNull()
                DynamicManagerApp(
                    label = app.loadLabel(pm).toString().ifBlank { app.packageName },
                    packageName = app.packageName,
                    uid = app.uid,
                    apkPath = apkPath,
                    icon = icon,
                    signatureIndex = indexes[app.uid % PER_USER_RANGE],
                )
            }
            .sortedWith(
                compareByDescending<DynamicManagerApp> { it.isAuthorized }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )
    }.getOrDefault(emptyList())
}

private fun readManagerIndexes(): Map<Int, Int> {
    val flat = Natives.getManagers() ?: return emptyMap()
    val result = mutableMapOf<Int, Int>()
    var i = 0
    while (i + 1 < flat.size) {
        result[flat[i] % PER_USER_RANGE] = flat[i + 1]
        i += 2
    }
    return result
}

private const val PER_USER_RANGE = 100_000
