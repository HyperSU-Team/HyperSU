package com.sukisu.ultra.ui.screen.flash

import android.net.Uri
import android.os.Parcelable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RemoveModerator
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import com.sukisu.ultra.R
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.util.saveTextToDownloads
import com.sukisu.ultra.ui.util.FlashResult
import com.sukisu.ultra.ui.util.LkmSelection
import com.sukisu.ultra.ui.util.downloadBoot
import com.sukisu.ultra.ui.util.flashModule
import com.sukisu.ultra.ui.util.installBoot
import com.sukisu.ultra.ui.util.restoreBoot
import com.sukisu.ultra.ui.util.uninstallPermanently
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

enum class FlashingStatus {
    FLASHING,
    SUCCESS,
    FAILED
}

enum class UninstallType(val icon: ImageVector, val title: Int, val message: Int) {
    TEMPORARY(
        Icons.Rounded.RemoveModerator,
        R.string.settings_uninstall_temporary,
        R.string.settings_uninstall_temporary_message
    ),
    PERMANENT(
        Icons.Rounded.DeleteForever,
        R.string.settings_uninstall_permanent,
        R.string.settings_uninstall_permanent_message
    ),
    RESTORE_STOCK_IMAGE(
        Icons.Rounded.RestartAlt,
        R.string.settings_restore_stock_image,
        R.string.settings_restore_stock_image_message
    ),
    NONE(Icons.Rounded.Adb, 0, 0)
}

@Parcelize
sealed class FlashIt : Parcelable {
    @Parcelize
    data class FlashBoot(
        val boot: Uri? = null,
        val lkm: LkmSelection,
        val ota: Boolean,
        val partition: String? = null,
        val allowShell: Boolean = false,
        val enableAdb: Boolean = false,
        val backup: Boolean = false,
        val spoofRelease: String = "",
        val spoofVersion: String = "",
    ) : FlashIt()

    @Parcelize
    data class DownloadBoot(
        val url: String,
        val partition: String,
        val lkm: LkmSelection,
        val allowShell: Boolean = false,
        val enableAdb: Boolean = false,
        val backup: Boolean = false,
        val spoofRelease: String = "",
        val spoofVersion: String = "",
    ) : FlashIt()

    @Parcelize
    data class FlashModules(val uris: List<Uri>) : FlashIt()

    @Parcelize
    data object FlashRestore : FlashIt()

    @Parcelize
    data object FlashUninstall : FlashIt()
}

fun flashModulesSequentially(
    uris: List<Uri>,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): FlashResult {
    for (uri in uris) {
        val result = try {
            flashModule(uri, onStdout, onStderr)
        } finally {
            // A module shared into the manager is copied into the cache by the intent handler;
            // that copy has no other owner and used to stay there for the life of the install.
            deleteCachedModuleCopy(uri)
        }
        if (result.code != 0) {
            return FlashResult(result.code, result.err, result.showReboot)
        }
    }
    return FlashResult(0, "", true)
}

private fun deleteCachedModuleCopy(uri: Uri) {
    if (uri.scheme != "file") return
    val file = uri.path?.let(::File) ?: return
    if (file.parentFile?.absolutePath == ksuApp.cacheDir.absolutePath && file.name.startsWith("module_")) {
        file.delete()
    }
}

fun flashIt(
    flashIt: FlashIt,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): FlashResult {
    return try {
        when (flashIt) {
            is FlashIt.FlashBoot -> installBoot(
                flashIt.boot,
                flashIt.lkm,
                flashIt.ota,
                flashIt.partition,
                flashIt.allowShell,
                flashIt.enableAdb,
                flashIt.backup,
                flashIt.spoofRelease,
                flashIt.spoofVersion,
                onStdout,
                onStderr
            )

            is FlashIt.DownloadBoot -> downloadBoot(
                flashIt.url,
                flashIt.partition,
                flashIt.lkm,
                flashIt.allowShell,
                flashIt.enableAdb,
                flashIt.backup,
                flashIt.spoofRelease,
                flashIt.spoofVersion,
                onStdout,
                onStderr
            )

            is FlashIt.FlashModules -> flashModulesSequentially(flashIt.uris, onStdout, onStderr)
            FlashIt.FlashRestore -> restoreBoot(onStdout, onStderr)
            FlashIt.FlashUninstall -> uninstallPermanently(onStdout, onStderr)
        }
    } catch (e: Exception) {
        FlashResult(-1, e.message ?: "Flash failed", false)
    }
}

/**
 * Application-scoped state for one boot/module flash.
 *
 * The root shell write cannot be cancelled, so the job must not belong to a composition: a
 * rotation or a dark-mode change recreated the activity, the old composition's completion
 * callbacks died with it, and the recreated screen sat on "Flashing" with NavLock held - even
 * though the write had finished. The screen observes these flows instead of owning the job.
 */
object FlashRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _showReboot = MutableStateFlow(false)
    val showReboot: StateFlow<Boolean> = _showReboot.asStateFlow()

    private val _status = MutableStateFlow(FlashingStatus.FLASHING)
    val status: StateFlow<FlashingStatus> = _status.asStateFlow()

    /** The running (or last finished) flash's log; survives activity recreation. */
    val logContent = StringBuffer()

    private var job: Job? = null

    val isRunning: Boolean
        get() = job?.isActive == true

    /** Starts [flashIt] unless another write is already running. */
    fun start(flashIt: FlashIt) {
        if (isRunning) return
        _text.value = ""
        _showReboot.value = false
        _status.value = FlashingStatus.FLASHING
        synchronized(logContent) { logContent.setLength(0) }
        job = scope.launch {
            var currentText = ""
            val result = withContext(Dispatchers.IO) {
                flashIt(flashIt, onStdout = { line ->
                    val incoming = "$line\n"
                    // The packer's clear-screen sequence arrives as raw ESC bytes.
                    if (incoming.startsWith("[H[J")) {
                        currentText = incoming.substring(6)
                    } else {
                        currentText += incoming
                    }
                    _text.value = currentText
                    synchronized(logContent) { logContent.append(line).append("\n") }
                }, onStderr = { line ->
                    synchronized(logContent) { logContent.append(line).append("\n") }
                })
            }
            if (result.code != 0) {
                currentText += "Error code: ${result.code}.\n ${result.err} Please save and check the log.\n"
                _text.value = currentText
            }
            if (result.showReboot) {
                currentText += "\n\n\n"
                _text.value = currentText
                _showReboot.value = true
            }
            _status.value = if (result.code == 0) FlashingStatus.SUCCESS else FlashingStatus.FAILED
        }
    }
}

fun saveLog(
    context: android.content.Context,
    logContent: CharSequence,
    scope: CoroutineScope,
    showMessage: (String) -> Unit
): () -> Unit {
    return {
        scope.launch {
            val format = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
            val date = format.format(Date())
            val fileName = "HyperSU_install_log_${date}.log"
            runCatching {
                withContext(Dispatchers.IO) {
                    saveTextToDownloads(context, fileName, logContent.toString())
                }
            }.onSuccess {
                showMessage("Log saved to $it")
            }.onFailure {
                showMessage("Failed to save log: ${it.message ?: "unknown error"}")
            }
        }
    }
}

private const val JAILBREAK_WARNING_COUNTDOWN = 10

@Composable
fun JailbreakFlashWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableIntStateOf(JAILBREAK_WARNING_COUNTDOWN) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000.milliseconds)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(android.R.string.dialog_alert_title)) },
        text = {
            Text(
                stringResource(R.string.jailbreak_flash_warning),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = countdown == 0
            ) {
                Text(
                    if (countdown > 0)
                        stringResource(R.string.jailbreak_flash_warning_countdown, countdown)
                    else
                        stringResource(R.string.install_next)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
