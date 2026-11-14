package com.sukisu.ultra.ui.component.uninstalldialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkSelectableRow
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.navigation3.Route
import com.sukisu.ultra.ui.screen.flash.FlashIt
import com.sukisu.ultra.ui.screen.flash.UninstallType
import com.sukisu.ultra.ui.screen.flash.UninstallType.PERMANENT
import com.sukisu.ultra.ui.screen.flash.UninstallType.RESTORE_STOCK_IMAGE
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * The uninstall chooser, in the FolkPatch design: a list of ways to uninstall,
 * each with its own icon, description and a confirmation before anything runs.
 *
 * Behaviour is unchanged from the old Material variant - the two destructive
 * routes still push the matching flash flow, and the confirmation still comes
 * from the shared confirm dialog.
 */
@Composable
fun UninstallDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val options = listOf(PERMANENT, RESTORE_STOCK_IMAGE)
    val showConfirmDialog = remember { mutableStateOf(false) }
    val runType = remember { mutableStateOf<UninstallType?>(null) }

    val run = { type: UninstallType ->
        when (type) {
            PERMANENT -> navigator.pushFlash(Route.Flash(FlashIt.FlashUninstall))
            RESTORE_STOCK_IMAGE -> navigator.pushFlash(Route.Flash(FlashIt.FlashRestore))
            else -> Unit
        }
    }

    if (show) {
        FolkAlertDialog(onDismissRequest = onDismissRequest, width = 340.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.settings_uninstall),
                    style = FolkType.Title,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    options.forEach { type ->
                        FolkSelectableRow(
                            title = stringResource(type.title),
                            summary = stringResource(type.message),
                            selected = false,
                            onClick = {
                                showConfirmDialog.value = true
                                runType.value = type
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            showConfirmDialog.value = false
            onDismissRequest()
            runType.value?.let { type -> run(type) }
        },
        onDismiss = {
            showConfirmDialog.value = false
        },
    )

    val dialogTitle = runType.value?.let { type ->
        options.find { it == type }?.let { stringResource(it.title) }
    } ?: ""
    val dialogContent = runType.value?.let { type ->
        options.find { it == type }?.let { stringResource(it.message) }
    }

    if (showConfirmDialog.value) {
        confirmDialog.showConfirm(title = dialogTitle, content = dialogContent)
    }
}
