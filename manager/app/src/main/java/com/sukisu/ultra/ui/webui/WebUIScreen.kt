package com.sukisu.ultra.ui.webui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.theme.tokens.FolkType

@Composable
fun rememberFileLauncher(webUIState: WebUIState): ActivityResultLauncher<Intent> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                data.clipData?.let { clipData ->
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } ?: data.data?.let { arrayOf(it) }
            }
        } else null
        webUIState.onFileChooserResult(uris)
    }
}

/**
 * The module WebUI container in the FolkPatch design.
 *
 * The WebView plumbing, the insets bridge, the back handling and the JS
 * alert/confirm/prompt/file-chooser bridge are unchanged; only the chrome and
 * the dialogs moved to the single Folk design, so there is no UI-mode branch
 * left here.
 */
@Composable
fun WebUIScreen(webUIState: WebUIState) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val drawingInsets = WindowInsets.safeDrawing
    val systemBarsInsets = WindowInsets.systemBars
    val imeInsets = WindowInsets.ime
    val innerPadding = if (webUIState.isInsetsEnabled) imeInsets.asPaddingValues() else drawingInsets.asPaddingValues()
    val fileLauncher = rememberFileLauncher(webUIState)

    LaunchedEffect(density, layoutDirection, systemBarsInsets, webUIState.isInsetsEnabled) {
        if (!webUIState.isInsetsEnabled) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val top = (systemBarsInsets.getTop(density) / density.density).toInt()
            val bottom = (systemBarsInsets.getBottom(density) / density.density).toInt()
            val left = (systemBarsInsets.getLeft(density, layoutDirection) / density.density).toInt()
            val right = (systemBarsInsets.getRight(density, layoutDirection) / density.density).toInt()
            Insets(top, bottom, left, right)
        }.collect { newInsets ->
            if (webUIState.currentInsets != newInsets) {
                webUIState.currentInsets = newInsets
                webUIState.webView?.evaluateJavascript(newInsets.js, null)
            }
        }
    }

    BackHandler(enabled = webUIState.webCanGoBack) {
        webUIState.webView?.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        webUIState.webView?.let { webView ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { _ ->
                    // The state object outlives one composition (and a loading crossfade can
                    // detach and re-attach this view). A WebView still owned by its previous
                    // parent cannot be added again.
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        if (!webUIState.isUrlLoaded) {
                            val homePage = "https://mui.kernelsu.org/index.html"
                            if (width > 0 && height > 0) {
                                loadUrl(homePage)
                                webUIState.isUrlLoaded = true
                            } else {
                                val listener = object : View.OnLayoutChangeListener {
                                    override fun onLayoutChange(
                                        v: View, left: Int, top: Int, right: Int, bottom: Int,
                                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                                    ) {
                                        if (v.width > 0 && v.height > 0) {
                                            (v as WebView).loadUrl(homePage)
                                            webUIState.isUrlLoaded = true
                                            v.removeOnLayoutChangeListener(this)
                                        }
                                    }
                                }
                                addOnLayoutChangeListener(listener)
                            }
                        }
                    }
                },
                update = { view ->
                    view.requestLayout()
                }
            )
        }
    }

    HandleWebUIEvent(webUIState, fileLauncher)

    HandleWebViewLifecycle(webUIState)
    HandleConfigurationChanges(webUIState)
}

/**
 * The JS bridge dialogs, in the FolkPatch design: one alert, one confirm and
 * one prompt, each answering the pending `JsResult` exactly as before.
 */
@Composable
fun HandleWebUIEvent(
    webUIState: WebUIState,
    fileLauncher: ActivityResultLauncher<Intent>
) {
    when (val event = webUIState.uiEvent) {
        is WebUIEvent.ShowAlert -> {
            val showDialog = remember(event) { mutableStateOf(true) }
            if (showDialog.value) {
                FolkAlertDialog(onDismissRequest = {
                    webUIState.onAlertResult()
                    showDialog.value = false
                }) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = event.message,
                            style = FolkType.Summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                webUIState.onAlertResult()
                                showDialog.value = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            colors = FolkButtonDefaults.textColors(),
                        ) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            }
        }

        is WebUIEvent.ShowConfirm -> {
            val showDialog = remember(event) { mutableStateOf(true) }
            if (showDialog.value) {
                FolkAlertDialog(onDismissRequest = {
                    webUIState.onConfirmResult(false)
                    showDialog.value = false
                }) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = event.message,
                            style = FolkType.Summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    webUIState.onConfirmResult(false)
                                    showDialog.value = false
                                },
                                colors = FolkButtonDefaults.textColors(),
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                            TextButton(
                                onClick = {
                                    webUIState.onConfirmResult(true)
                                    showDialog.value = false
                                },
                                colors = FolkButtonDefaults.textColors(),
                            ) {
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                }
            }
        }

        is WebUIEvent.ShowPrompt -> {
            val showDialog = remember(event) { mutableStateOf(true) }
            val state = remember(event) { mutableStateOf(event.defaultValue) }
            if (showDialog.value) {
                FolkAlertDialog(onDismissRequest = {
                    webUIState.onPromptResult(null)
                    showDialog.value = false
                }) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = event.message,
                            style = FolkType.Summary,
                            fontWeight = FontWeight.Medium,
                        )
                        OutlinedTextField(
                            value = state.value,
                            onValueChange = { state.value = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            singleLine = true,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    webUIState.onPromptResult(null)
                                    showDialog.value = false
                                },
                                colors = FolkButtonDefaults.textColors(),
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                            TextButton(
                                onClick = {
                                    webUIState.onPromptResult(state.value)
                                    showDialog.value = false
                                },
                                colors = FolkButtonDefaults.textColors(),
                            ) {
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                }
            }
        }

        is WebUIEvent.ShowFileChooser -> {
            LaunchedEffect(event) {
                try {
                    fileLauncher.launch(event.intent)
                } catch (_: Exception) {
                    webUIState.onFileChooserResult(null)
                }
            }
        }

        else -> {}
    }
}

@Composable
private fun HandleWebViewLifecycle(webUIState: WebUIState) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, webUIState) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webUIState.webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> webUIState.webView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun HandleConfigurationChanges(webUIState: WebUIState) {
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.fontScale, webUIState.webView) {
        webUIState.webView?.settings?.textZoom = (configuration.fontScale * 100).toInt()
    }
}
