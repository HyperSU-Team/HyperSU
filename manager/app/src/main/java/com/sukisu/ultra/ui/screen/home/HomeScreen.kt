package com.sukisu.ultra.ui.screen.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.sukisu.ultra.R
import com.sukisu.ultra.magica.MagicaService
import com.sukisu.ultra.ui.component.dialog.rememberLoadingDialog
import com.sukisu.ultra.ui.navigation3.Navigator
import com.sukisu.ultra.ui.navigation3.Route
import com.sukisu.ultra.ui.viewmodel.HomeViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun HomePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val loadingDialog = rememberLoadingDialog()
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }

    var hasActivated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage && !hasActivated) {
            hasActivated = true
            viewModel.refresh()
        }
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            viewModel.refresh()
        }
        initialResumeHandled.value = true
        onPauseOrDispose { }
    }

    // A jailbreak in progress. Held as state rather than inside a coroutine so the timeout is not
    // tied to this composable: the scope is cancelled when the home tab leaves the composition,
    // which used to leave the dialog up with nothing left to dismiss it.
    var jailbreakPending by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(jailbreakPending) {
        if (!jailbreakPending) return@LaunchedEffect
        delay(30_000.milliseconds)
        jailbreakPending = false
        Toast.makeText(context, R.string.jailbreak_timeout, Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(jailbreakPending) {
        if (jailbreakPending) loadingDialog.showLoading() else loadingDialog.hide()
    }

    // Remembered so the home page can skip recomposing when nothing it shows has changed.
    // A fresh HomeActions on every composition invalidates every child that takes it, which is
    // the whole page - and this page recomposes on every scroll of its facts list.
    val actions = remember(navigator, uriHandler, context) {
        HomeActions(
            onInstallClick = { navigator.push(Route.Install()) },
            onOpenUrl = uriHandler::openUri,
            onJailbreakClick = {
                // Manager will be force-stopped and restarted by late-load on success. If that does
                // not happen within the timeout, the jailbreak likely failed.
                jailbreakPending = true
                context.startService(Intent(context, MagicaService::class.java))
            },
        )
    }

    HomePagerFolk(
        state = uiState,
        actions = actions,
        bottomInnerPadding = bottomInnerPadding,
    )
}
