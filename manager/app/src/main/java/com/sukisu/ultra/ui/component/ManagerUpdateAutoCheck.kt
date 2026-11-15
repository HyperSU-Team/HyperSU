package com.sukisu.ultra.ui.component

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sukisu.ultra.ui.util.GithubMirrors
import com.sukisu.ultra.ui.util.ManagerUpdate
import com.sukisu.ultra.ui.util.ManagerUpdateChecker
import com.sukisu.ultra.ui.util.ManagerUpdateSettings
import com.sukisu.ultra.ui.util.installApk
import kotlinx.coroutines.launch

/**
 * The automatic manager update check.
 *
 * Hosted once, above the navigation host, so the prompt can appear on any page. It runs every time
 * the app comes to the foreground (the check itself is debounced by a minute so an activity
 * recreation does not repeat it), and only when [ManagerUpdateSettings.isEnabled], which is on by
 * default; the settings row is a switch for exactly this.
 */
@Composable
fun ManagerUpdateAutoCheck() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val enabled = ManagerUpdateSettings.isEnabled
    var update by remember { mutableStateOf<ManagerUpdate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    // ON_START fires on the first launch and every time the app returns to the foreground, which is
    // what "check whenever the app is opened" means; a rotation recreates the activity and fires it
    // again, so the check is rate-limited instead of trusting the event alone.
    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_START || !enabled || checking) return@LifecycleEventObserver
            if (!ManagerUpdateSettings.isCheckDue(context)) return@LifecycleEventObserver
            ManagerUpdateSettings.markChecked(context)
            checking = true
            scope.launch {
                // check() already returns a Result, so runCatching around it produced
                // Result<Result<..>>.
                try {
                    ManagerUpdateChecker.check()
                        .onSuccess { found -> if (found != null) update = found }
                } finally {
                    checking = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Turning the switch back on should not wait for the next foreground transition.
    LaunchedEffect(enabled) {
        if (!enabled || checking) return@LaunchedEffect
        if (!ManagerUpdateSettings.isCheckDue(context)) return@LaunchedEffect
        ManagerUpdateSettings.markChecked(context)
        checking = true
        try {
            ManagerUpdateChecker.check()
                .onSuccess { found -> if (found != null) update = found }
        } finally {
            checking = false
        }
    }

    update?.let { available ->
        ManagerUpdateDialog(
            update = available,
            onDismiss = { update = null },
            onInstall = {
                update = null
                val url = available.apkUrls.firstOrNull()
                if (!busy && url != null) {
                    busy = true
                    scope.launch {
                        try {
                            val uri = GithubMirrors.downloadThroughMirrors(
                                url = url,
                                fileName = "HyperSU-${available.versionName}.apk",
                            )
                            if (uri != null) {
                                installApk(context, uri)
                            } else {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, available.pageUrl.toUri()))
                                }
                            }
                        } finally {
                            busy = false
                        }
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
