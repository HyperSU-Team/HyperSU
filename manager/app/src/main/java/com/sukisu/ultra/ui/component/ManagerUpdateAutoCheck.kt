package com.sukisu.ultra.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.core.net.toUri
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.util.GithubMirrors
import com.sukisu.ultra.ui.util.ManagerUpdate
import com.sukisu.ultra.ui.util.ManagerUpdateChecker
import com.sukisu.ultra.ui.util.ManagerUpdateSettings
import com.sukisu.ultra.ui.util.installApk
import kotlinx.coroutines.launch

/**
 * The automatic manager update check.
 *
 * Hosted once, above the navigation host, so the prompt can appear on any page. It asks GitHub at
 * most once every twelve hours and only when [ManagerUpdateSettings.isEnabled], which is on by
 * default; the settings row is a switch for exactly this.
 */
@Composable
fun ManagerUpdateAutoCheck() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled = ManagerUpdateSettings.isEnabled
    var update by remember { mutableStateOf<ManagerUpdate?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        if (!ManagerUpdateSettings.isCheckDue(context)) return@LaunchedEffect
        ManagerUpdateSettings.markChecked(context)
        runCatching { ManagerUpdateChecker.check() }
            .onSuccess { found -> if (found != null) update = found }
    }

    update?.let { available ->
        ManagerUpdateDialog(
            update = available,
            onDismiss = { update = null },
            onInstall = {
                update = null
                if (!busy) {
                    busy = true
                    scope.launch {
                        // The release host redirects to a host that is loopback on a filtered
                        // network; the mirrors are the working path there.
                        val uri = GithubMirrors.downloadThroughMirrors(
                            url = available.apkUrls.firstOrNull() ?: return@launch,
                            fileName = "HyperSU-${available.versionName}.apk",
                        )
                        if (uri != null) {
                            installApk(context, uri)
                        } else {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, available.pageUrl.toUri()))
                            }
                        }
                        busy = false
                    }
                }
            },
            onOpenPage = {
                update = null
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, available.pageUrl.toUri()))
                }
            },
        )
    }
}
