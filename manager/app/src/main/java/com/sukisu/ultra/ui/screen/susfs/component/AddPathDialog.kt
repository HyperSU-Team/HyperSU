package com.sukisu.ultra.ui.screen.susfs.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * The single-field add/edit dialog used by SUS paths, SUS loop paths, SUS maps
 * and the plain Kstat path list, in the FolkPatch design.
 */
@Composable
fun AddPathDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isLoading: Boolean,
    titleRes: Int,
    labelRes: Int,
    initialValue: String = ""
) {
    var newPath by remember { mutableStateOf(initialValue) }

    LaunchedEffect(showDialog, initialValue) {
        if (showDialog) {
            newPath = initialValue
        }
    }

    if (!showDialog) return

    FolkAlertDialog(onDismissRequest = {
        onDismiss()
        newPath = ""
    }) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(titleRes),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.padding(top = 10.dp))
            OutlinedTextField(
                value = newPath,
                onValueChange = { newPath = it },
                label = { Text(stringResource(labelRes)) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        onDismiss()
                        newPath = ""
                    },
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (newPath.isNotBlank()) {
                            onConfirm(newPath.trim())
                            newPath = ""
                        }
                    },
                    enabled = newPath.isNotBlank() && !isLoading,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(
                        stringResource(
                            if (initialValue.isNotEmpty()) R.string.susfs_save else R.string.add
                        )
                    )
                }
            }
        }
    }
}
