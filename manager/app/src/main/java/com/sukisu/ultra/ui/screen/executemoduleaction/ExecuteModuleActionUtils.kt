package com.sukisu.ultra.ui.screen.executemoduleaction

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.ModuleRepositoryImpl
import com.sukisu.ultra.ui.util.runModuleAction
import com.sukisu.ultra.ui.util.saveTextToDownloads
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExecuteModuleActionEffect(
    moduleId: String,
    text: String,
    logContent: StringBuilder,
    fromShortcut: Boolean,
    onTextUpdate: (String) -> Unit,
    onComplete: () -> Unit = {},
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val noModule = stringResource(R.string.no_such_module)
    val moduleUnavailable = stringResource(R.string.module_unavailable)
    val moduleActionSuccess = stringResource(R.string.module_action_success)

    // A saveable flag, not the output text: a rotation before the script printed anything left the
    // text empty, so the recreated composition ran the action script a second time.
    var started by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (started) return@LaunchedEffect
        started = true
        if (text.isNotEmpty()) {
            return@LaunchedEffect
        }
        val repo = ModuleRepositoryImpl()
        val modules = repo.getModules().getOrDefault(emptyList())
        val moduleInfo = modules.find { info -> info.id == moduleId }
        if (moduleInfo == null) {
            Toast.makeText(context, noModule.format(moduleId), Toast.LENGTH_SHORT).show()
            onExit()
            return@LaunchedEffect
        }
        if (!moduleInfo.hasActionScript) {
            onExit()
            return@LaunchedEffect
        }
        if (!moduleInfo.enabled || moduleInfo.update || moduleInfo.remove) {
            Toast.makeText(context, moduleUnavailable.format(moduleInfo.name), Toast.LENGTH_SHORT).show()
            onExit()
            return@LaunchedEffect
        }
        var actionResult: Boolean
        var currentText = text
        val mainHandler = Handler(Looper.getMainLooper())
        withContext(Dispatchers.IO) {
            runModuleAction(
                moduleId = moduleId,
                onStdout = {
                    val tempText = "$it\n"
                    if (tempText.startsWith("[H[J")) { // clear command
                        currentText = tempText.substring(6)
                    } else {
                        currentText += tempText
                    }
                    mainHandler.post {
                        onTextUpdate(currentText)
                    }
                    logContent.append(it).append("\n")
                },
                onStderr = {
                    logContent.append(it).append("\n")
                }
            ).let {
                actionResult = it
            }
        }
        if (actionResult && fromShortcut) {
            Toast.makeText(
                context,
                moduleActionSuccess,
                Toast.LENGTH_SHORT
            ).show()
        }
        onComplete()
    }
}

fun saveLog(
    context: android.content.Context,
    logContent: StringBuilder,
    scope: CoroutineScope,
    showMessage: (String) -> Unit
): () -> Unit {
    return {
        scope.launch {
            val format = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
            val date = format.format(Date())
            val fileName = "HyperSU_module_action_log_${date}.log"
            runCatching {
                withContext(Dispatchers.IO) {
                    saveTextToDownloads(context, fileName, logContent.toString())
                }
            }.onSuccess {
                showMessage("${context.getString(R.string.log_saved)}: $it")
            }.onFailure {
                showMessage(context.getString(R.string.log_save_failed, it.message ?: context.getString(R.string.error_unknown)))
            }
        }
    }
}
