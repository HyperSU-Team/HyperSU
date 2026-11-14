package com.sukisu.ultra.ui.kernelFlash

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.LocalActivity
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
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.kernelFlash.state.HorizonKernelState
import com.sukisu.ultra.ui.kernelFlash.state.HorizonKernelWorker
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.navigation3.NavLock
import com.sukisu.ultra.ui.util.reboot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun KernelFlashScreen(
    kernelUri: Uri,
    selectedSlot: String? = null,
    kpmPatchEnabled: Boolean = false,
    kpmUndoPatch: Boolean = false
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    var logText by rememberSaveable { mutableStateOf("") }
    var showFloatAction by rememberSaveable { mutableStateOf(false) }
    val logContent = rememberSaveable { StringBuilder() }

    val horizonKernelState = remember {
        // Only an in-flight flash may be picked up again. A finished or failed one is not: reusing
        // it meant a flash of the same archive immediately showed the previous result - the
        // completed page with its reboot button, and nothing was written. Opening this screen always
        // means "flash this archive now", so anything that is done is discarded.
        val cached = KernelFlashStateHolder.currentState
        val cachedFinished = cached?.state?.value?.let { it.isCompleted || it.error.isNotEmpty() } ?: true
        val reusable = cached != null &&
            !cachedFinished &&
            KernelFlashStateHolder.currentUri == kernelUri &&
            KernelFlashStateHolder.currentSlot == selectedSlot &&
            KernelFlashStateHolder.currentKpmPatchEnabled == kpmPatchEnabled &&
            KernelFlashStateHolder.currentKpmUndoPatch == kpmUndoPatch
        if (reusable) {
            cached!!
        } else {
            HorizonKernelState().also {
                KernelFlashStateHolder.currentState = it
                KernelFlashStateHolder.currentUri = kernelUri
                KernelFlashStateHolder.currentSlot = selectedSlot
                KernelFlashStateHolder.currentKpmPatchEnabled = kpmPatchEnabled
                KernelFlashStateHolder.currentKpmUndoPatch = kpmUndoPatch
                KernelFlashStateHolder.isFlashing = false
            }
        }
    }

    // Note: the holder is deliberately *not* cleared here. It is what keeps a running flash alive
    // across an activity recreation (a rotation, a language switch): the worker is a plain thread
    // and the state has to outlive this composition, or a second worker would start writing the
    // boot image. KernelFlashFolk drops a finished or failed state when this screen goes away.

    val flashState by horizonKernelState.state.collectAsStateWithLifecycle()

    // While the write is running the screen cannot be swiped away or backed out of: doing so
    // cancelled the flash coroutine in the middle of writing the boot image. The flag follows the
    // job, so the result page is dismissible again the moment it finishes.
    DisposableEffect(Unit) {
        onDispose { NavLock.release() }
    }
    LaunchedEffect(flashState.isFlashing, flashState.isCompleted, flashState.error) {
        if (flashState.isFlashing && !flashState.isCompleted && flashState.error.isEmpty()) {
            NavLock.acquire()
        } else {
            NavLock.release()
        }
    }

    val flashComplete = stringResource(R.string.horizon_flash_complete)

    val onFlashComplete = {
        showFloatAction = true
        KernelFlashStateHolder.isFlashing = false
    }

    LaunchedEffect(flashState.isCompleted, flashState.error) {
        if (flashState.isCompleted && flashState.error.isEmpty()) {
            val intent = activity?.intent
            val isFromExternalIntent = intent?.action?.let { action ->
                action == Intent.ACTION_VIEW ||
                action == Intent.ACTION_SEND ||
                action == Intent.ACTION_SEND_MULTIPLE
            } ?: false

            if (isFromExternalIntent) {
                delay(1500)
                KernelFlashStateHolder.clear()
                activity.finish()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!KernelFlashStateHolder.isFlashing && !flashState.isCompleted && flashState.error.isEmpty()) {
            withContext(Dispatchers.IO) {
                KernelFlashStateHolder.isFlashing = true
                val worker = HorizonKernelWorker(
                    context = context,
                    state = horizonKernelState,
                    slot = selectedSlot,
                    kpmPatchEnabled = kpmPatchEnabled,
                    kpmUndoPatch = kpmUndoPatch
                )
                worker.uri = kernelUri
                worker.setOnFlashCompleteListener(onFlashComplete)
                worker.start()

                while (flashState.error.isEmpty() && !flashState.isCompleted) {
                    if (flashState.logs.isNotEmpty()) {
                        logText = flashState.logs.joinToString("\n")
                        logContent.clear()
                        logContent.append(logText)
                    }
                    delay(100)
                }

                if (flashState.error.isNotEmpty()) {
                    logText += "\n${flashState.error}\n"
                    logContent.append("\n${flashState.error}\n")
                    KernelFlashStateHolder.isFlashing = false
                }
            }
        } else {
            logText = flashState.logs.joinToString("\n")
            if (flashState.error.isNotEmpty()) {
                logText += "\n${flashState.error}\n"
            } else if (flashState.isCompleted) {
                logText += "\n$flashComplete\n\n\n"
                showFloatAction = true
            }
        }
    }

    val actions = KernelFlashActions(
        onBack = {
            if (!flashState.isFlashing || flashState.isCompleted || flashState.error.isNotEmpty()) {
                if (flashState.isCompleted || flashState.error.isNotEmpty()) {
                    KernelFlashStateHolder.clear()
                }
                navigator.pop()
            }
        },
        onSaveLog = { logContentValue ->
            scope.launch {
                val format = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                val date = format.format(Date())
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "HyperSU_kernel_flash_log_${date}.log"
                )
                file.writeText(logContentValue)
            }
        },
        onReboot = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    reboot()
                }
            }
        }
    )

    KernelFlashFolk(
        state = flashState,
        actions = actions,
        logText = logText,
        kpmPatchEnabled = kpmPatchEnabled,
        kpmUndoPatch = kpmUndoPatch
    )
}
