package com.sukisu.ultra.ui.kernelFlash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.folkGroupColor
import com.sukisu.ultra.ui.screen.install.InstallMethod
import com.sukisu.ultra.ui.screen.install.archiveSlot
import com.sukisu.ultra.ui.screen.install.archiveUri
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * Confirms the AnyKernel3 archive about to be flashed.
 *
 * Choosing an archive used to lead straight into a patch dialog, so the first thing the user
 * saw was a question about patching rather than an answer about what they had picked. This
 * reports the file and the slot, and flashes only on confirmation.
 */
@Composable
fun FlashConfirmDialog(
    archive: InstallMethod,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val fileName = archive.archiveUri?.lastPathSegment?.substringAfterLast('/').orEmpty()
    val slotLabel = when (archive.archiveSlot) {
        "a" -> stringResource(R.string.slot_a)
        "b" -> stringResource(R.string.slot_b)
        else -> null
    }

    FolkAlertDialog(onDismissRequest = onDismiss, width = 320.dp) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.flash_confirm_title),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = FolkShape.Corner16,
                color = folkGroupColor(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ConfirmRow(
                        label = stringResource(R.string.flash_confirm_file),
                        value = fileName.ifBlank { stringResource(R.string.flash_confirm_unknown) },
                    )
                    slotLabel?.let {
                        ConfirmRow(label = stringResource(R.string.flash_confirm_slot), value = it)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, colors = FolkButtonDefaults.textColors()) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = onConfirm,
                    colors = FolkButtonDefaults.filledColors(),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.flash_confirm_action))
                }
            }
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = FolkType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = FolkType.Summary)
    }
}
