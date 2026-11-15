package com.sukisu.ultra.ui.screen.templateeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.profile.RootProfileConfig
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * The App Profile template editor, in the FolkPatch design.
 *
 * The identity fields are a Folk group above the shared root-profile editor, so
 * a template is edited with the exact same controls as a live per-app profile.
 * A read-only template hides the delete and save actions; otherwise both are in
 * the bar. The title states whether this is a creation, an edit or a view, and
 * carries the template's id@author as a subtitle.
 */
@Composable
fun TemplateEditorScreenFolk(
    state: TemplateEditorUiState,
    actions: TemplateEditorActions,
) {
    val title = when {
        state.isCreation -> stringResource(R.string.app_profile_template_create)
        state.readOnly -> stringResource(R.string.app_profile_template_view)
        else -> stringResource(R.string.app_profile_template_edit)
    }

    FolkScaffold(
        title = title,
        subtitle = state.titleSummary.takeIf { it.isNotBlank() },
        titleStyle = com.sukisu.ultra.ui.component.folk.FolkTitleStyle.Flexible,
        onBack = actions.onBack,
        actions = {
            if (state.readOnly) return@FolkScaffold
            if (!state.isCreation) {
                IconButton(onClick = actions.onDelete) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = stringResource(R.string.app_profile_template_delete),
                    )
                }
            }
            IconButton(onClick = actions.onSave) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = stringResource(R.string.app_profile_template_save),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            FolkSettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                item {
                    OutlinedTextField(
                        value = state.template.name,
                        onValueChange = actions.onNameChange,
                        label = { Text(stringResource(R.string.app_profile_template_name)) },
                        readOnly = state.readOnly,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }

                if (state.isCreation) {
                    item {
                        OutlinedTextField(
                            value = state.template.id,
                            onValueChange = actions.onIdChange,
                            label = { Text(stringResource(R.string.app_profile_template_id)) },
                            isError = state.idErrorHint.isNotEmpty(),
                            supportingText = if (state.idErrorHint.isNotEmpty()) {
                                {
                                    Text(
                                        text = state.idErrorHint,
                                        color = MaterialTheme.colorScheme.error,
                                        style = FolkType.Caption,
                                    )
                                }
                            } else {
                                null
                            },
                            readOnly = state.readOnly,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = state.template.author,
                        onValueChange = actions.onAuthorChange,
                        label = { Text(stringResource(R.string.module_author)) },
                        readOnly = state.readOnly,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.template.description,
                        onValueChange = actions.onDescriptionChange,
                        label = { Text(stringResource(R.string.app_profile_template_description)) },
                        readOnly = state.readOnly,
                        minLines = 1,
                        maxLines = 100,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(13.dp))

            RootProfileConfig(
                fixedName = true,
                enabled = !state.readOnly,
                profile = toNativeProfile(state.template),
                onProfileChange = actions.onProfileChange,
            )

            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        innerPadding.calculateBottomPadding()
                )
            )
        }
    }
}
