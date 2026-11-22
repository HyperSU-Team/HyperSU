package com.sukisu.ultra.ui.screen.templateeditor

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import com.sukisu.ultra.R
import com.sukisu.ultra.toOrdinalList
import com.sukisu.ultra.toRootProfileFlags
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.util.deleteAppProfileTemplate
import com.sukisu.ultra.ui.viewmodel.TemplateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TemplateEditorScreen(template: TemplateViewModel.TemplateInfo, readOnly: Boolean) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val isCreation = template.id.isBlank()
    val autoSave = !isCreation

    var currentTemplate by rememberSaveable { mutableStateOf(template) }
    var idErrorHint by remember { mutableStateOf("") }
    val autoSaveScope = rememberCoroutineScope()
    var autoSaveJob by remember { mutableStateOf<Job?>(null) }
    var idCheckJob by remember { mutableStateOf<Job?>(null) }
    // Guards the async back/delete paths so a double tap cannot pop the back stack twice.
    var isFinishing by remember { mutableStateOf(false) }

    val saveTemplateFailed = stringResource(id = R.string.app_profile_template_save_failed)
    val idConflictError = stringResource(id = R.string.app_profile_template_id_exist)
    val idInvalidError = stringResource(id = R.string.app_profile_template_id_invalid)

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun finishEditing() {
        // Flush a pending debounced autosave before the composition (and its scope) goes away.
        // Read-only entry never edits, so it never has a pending save to flush.
        if (autoSave && !readOnly) {
            if (isFinishing) return
            isFinishing = true
            autoSaveJob?.cancel()
            val templateToSave = currentTemplate
            // saveTemplate is a root shell job; run it off the main thread.
            autoSaveScope.launch {
                if (withContext(Dispatchers.IO) { saveTemplate(templateToSave) }) {
                    navigator.setResult("template_edit", true)
                } else {
                    isFinishing = false
                    showToast(saveTemplateFailed)
                }
            }
            return
        }
        if (!readOnly) {
            navigator.setResult("template_edit", true)
        } else {
            navigator.pop()
        }
    }

    fun updateTemplate(updatedTemplate: TemplateViewModel.TemplateInfo) {
        currentTemplate = updatedTemplate
        if (!autoSave || readOnly) return
        // Debounced: this used to run one root shell exec per keystroke, synchronously on the
        // main thread, and a failed save snapped the field back with no feedback.
        autoSaveJob?.cancel()
        autoSaveJob = autoSaveScope.launch {
            delay(600)
            if (!withContext(Dispatchers.IO) { saveTemplate(currentTemplate) }) {
                showToast(saveTemplateFailed)
            }
        }
    }

    val uiState = TemplateEditorUiState(
        template = currentTemplate,
        initialTemplate = template,
        readOnly = readOnly,
        isCreation = isCreation,
        idErrorHint = idErrorHint,
    )

    fun saveCurrentTemplate() {
        val templateToSave = currentTemplate
        autoSaveJob?.cancel()
        autoSaveScope.launch {
            // idCheck queries ksud; keep it off the main thread together with the save.
            when (if (isCreation) withContext(Dispatchers.IO) { idCheck(templateToSave.id) } else 0) {
                1 -> {
                    showToast(idConflictError)
                    return@launch
                }

                2 -> {
                    showToast(idInvalidError)
                    return@launch
                }
            }

            val saved = withContext(Dispatchers.IO) { saveTemplate(templateToSave, isCreation) }
            if (saved) {
                navigator.setResult("template_edit", true)
            } else {
                showToast(saveTemplateFailed)
            }
        }
    }

    val actions = TemplateEditorActions(
        onBack = dropUnlessResumed { finishEditing() },
        onDelete = {
            val id = currentTemplate.id
            if (!isFinishing) {
                isFinishing = true
                autoSaveJob?.cancel()
                autoSaveScope.launch {
                    if (withContext(Dispatchers.IO) { deleteAppProfileTemplate(id) }) {
                        navigator.setResult("template_edit", true)
                    } else {
                        isFinishing = false
                    }
                }
            }
        },
        onSave = ::saveCurrentTemplate,
        onNameChange = { value ->
            updateTemplate(currentTemplate.copy(name = value))
        },
        onIdChange = { value ->
            currentTemplate = currentTemplate.copy(id = value)
            // isTemplateExist is a root shell exec: debounce it and run it off the main thread
            // instead of blocking on every keystroke.
            idCheckJob?.cancel()
            idCheckJob = autoSaveScope.launch {
                delay(300)
                val exists = withContext(Dispatchers.IO) { isTemplateExist(value) }
                idErrorHint = if (exists) {
                    idConflictError
                } else if (!isValidTemplateId(value)) {
                    idInvalidError
                } else {
                    ""
                }
            }
        },
        onAuthorChange = { value ->
            updateTemplate(currentTemplate.copy(author = value))
        },
        onDescriptionChange = { value ->
            updateTemplate(currentTemplate.copy(description = value))
        },
        onProfileChange = { profile ->
            updateTemplate(
                currentTemplate.copy(
                    uid = profile.uid,
                    gid = profile.gid,
                    groups = profile.groups,
                    capabilities = profile.capabilities,
                    context = profile.context,
                    namespace = profile.namespace,
                    rules = profile.rules.split("\n"),
                    flags = profile.flags.toRootProfileFlags().toOrdinalList(),
                )
            )
        },
    )

    TemplateEditorScreenFolk(
        state = uiState,
        actions = actions,
    )
}