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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val logContent = FlashRunner.logContent
    val needJailbreakWarning = remember(flashIt) { flashIt is FlashIt.FlashBoot && Natives.isLateLoadMode }
    // Soft reboot keeps the jailbreak and still applies modules
    val softReboot = remember(flashIt) { flashIt is FlashIt.FlashModules && isSoftRebootPreferred() }
    var flashingEnabled by rememberSaveable { mutableStateOf(!needJailbreakWarning) }
    // Restored on recreation: the recreated screen must observe the application-scoped job
    // instead of asking for a second write, and a job that finished while the activity was
    // being recreated must not be started over.
    var startRequested by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHost.showSnackbar(message)
        }
    }

    // The write runs in FlashRunner, not here: the old composition-owned coroutine died with
    // the activity on rotation and its result was never delivered to the recreated screen.
    LaunchedEffect(flashIt, flashingEnabled) {
        if (flashingEnabled && !startRequested) {
            startRequested = true
            FlashRunner.start(flashIt)
        }
    }

    val text by FlashRunner.text.collectAsStateWithLifecycle()
    val showRebootAction by FlashRunner.showReboot.collectAsStateWithLifecycle()
    val flashingStatus by FlashRunner.status.collectAsStateWithLifecycle()

    LaunchedEffect(flashingStatus) {
        // A finished flash changes what the module list should show, so ask it to reload on its
        // next resume. The success page stays up: it carries the reboot action, and closing the
        // activity a second after a successful install took that page away before it could be used.
        // A SUCCESS left over from a previous flash (before this screen started its own) is ignored.
        if (flashingStatus == FlashingStatus.SUCCESS && startRequested) {
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

    // Until this screen has started its own flash, FlashRunner still holds the previous run's
    // log and result; show a fresh page instead of that stale success/failure.
    val state = FlashUiState(
        text = if (startRequested) text else "",
        showRebootAction = startRequested && showRebootAction,
        flashingStatus = if (startRequested) flashingStatus else FlashingStatus.FLASHING,
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
        onSaveLog = saveLog(context, logContent, scope) { showMessage(it) },
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
