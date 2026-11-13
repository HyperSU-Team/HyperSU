package com.sukisu.ultra.ui.screen.flash

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.isSoftRebootPreferred
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.navigation3.NavLock
import com.sukisu.ultra.ui.screen.module.ModuleListRefresh
import com.sukisu.ultra.ui.util.reboot

@Composable
fun FlashScreen(flashIt: FlashIt) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val logContent = remember { StringBuilder() }
    var showRebootAction by rememberSaveable { mutableStateOf(false) }
    var flashingStatus by rememberSaveable { mutableStateOf(FlashingStatus.FLASHING) }
    val needJailbreakWarning = flashIt is FlashIt.FlashBoot && Natives.isLateLoadMode
    // Soft reboot keeps the jailbreak and still applies modules
    val softReboot = flashIt is FlashIt.FlashModules && isSoftRebootPreferred()
    var flashingEnabled by rememberSaveable { mutableStateOf(!needJailbreakWarning) }
    val snackbarHost = remember { SnackbarHostState() }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHost.showSnackbar(message)
        }
    }

    FlashEffect(
        flashIt = flashIt,
        text = text,
        logContent = logContent,
        onTextUpdate = { text = it },
        onShowRebootChange = { showRebootAction = it },
        onFlashingStatusChange = { flashingStatus = it },
        enabled = flashingEnabled,
    )

    LaunchedEffect(flashingStatus) {
        // A finished flash changes what the module list should show, so ask it to reload on its
        // next resume. The success page stays up: it carries the reboot action, and closing the
        // activity a second after a successful install took that page away before it could be used.
        if (flashingStatus == FlashingStatus.SUCCESS) {
            ModuleListRefresh.request()
        }
        // The navigation host must not dismiss this screen while the write is running; a swipe
        // from the edge cancelled the flash halfway through.
        if (flashingStatus == FlashingStatus.FLASHING) {
            NavLock.acquire()
        } else {
            NavLock.release()
        }
    }

    DisposableEffect(Unit) {
        onDispose { NavLock.release() }
    }

    val state = FlashUiState(
        text = text,
        showRebootAction = showRebootAction,
        flashingStatus = flashingStatus,
        showJailbreakWarning = needJailbreakWarning && !flashingEnabled,
        rebootLabelRes = if (softReboot) R.string.reboot_soft else R.string.reboot,
    )
    val actions = FlashScreenActions(
        onBack = dropUnlessResumed {
            // Back is refused while the write is running: popping the screen cancels it.
            if (flashingStatus == FlashingStatus.FLASHING) {
                showMessage(context.getString(R.string.flash_in_progress))
            } else {
                navigator.pop()
            }
        },
        onSaveLog = saveLog(logContent, scope) { showMessage(it) },
        onReboot = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    reboot(if (softReboot) "soft_reboot" else "")
                }
            }
        },
        onConfirmJailbreakWarning = { flashingEnabled = true },
        onDismissJailbreakWarning = dropUnlessResumed { navigator.pop() },
    )

    FlashScreenFolk(state, actions, snackbarHost)
}
