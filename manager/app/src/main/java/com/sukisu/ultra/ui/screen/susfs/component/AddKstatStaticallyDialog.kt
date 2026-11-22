package com.sukisu.ultra.ui.screen.susfs.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
 * The static Kstat configuration editor, in the FolkPatch design: the path plus
 * the twelve stat fields, the `default` hint, and a Cancel/Add pair. Every
 * field keeps the original parsing, defaulting and confirmation behaviour.
 */
@SuppressLint("SdCardPath")
@Composable
fun AddKstatStaticallyDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, String, String, String, String, String, String, String, String) -> Unit,
    isLoading: Boolean,
    initialConfig: String = ""
) {
    var newKstatPath by remember { mutableStateOf("") }
    var newKstatIno by remember { mutableStateOf("") }
    var newKstatDev by remember { mutableStateOf("") }
    var newKstatNlink by remember { mutableStateOf("") }
    var newKstatSize by remember { mutableStateOf("") }
    var newKstatAtime by remember { mutableStateOf("") }
    var newKstatAtimeNsec by remember { mutableStateOf("") }
    var newKstatMtime by remember { mutableStateOf("") }
    var newKstatMtimeNsec by remember { mutableStateOf("") }
    var newKstatCtime by remember { mutableStateOf("") }
    var newKstatCtimeNsec by remember { mutableStateOf("") }
    var newKstatBlocks by remember { mutableStateOf("") }
    var newKstatBlksize by remember { mutableStateOf("") }

    val resetFields = {
        newKstatPath = ""
        newKstatIno = ""
        newKstatDev = ""
        newKstatNlink = ""
        newKstatSize = ""
        newKstatAtime = ""
        newKstatAtimeNsec = ""
        newKstatMtime = ""
        newKstatMtimeNsec = ""
        newKstatCtime = ""
        newKstatCtimeNsec = ""
        newKstatBlocks = ""
        newKstatBlksize = ""
    }

    LaunchedEffect(showDialog, initialConfig) {
        if (showDialog && initialConfig.isNotEmpty()) {
            val parts = initialConfig.split("|")
            if (parts.size >= 13) {
                newKstatPath = parts[0]
                newKstatIno = if (parts[1] == "default") "" else parts[1]
                newKstatDev = if (parts[2] == "default") "" else parts[2]
                newKstatNlink = if (parts[3] == "default") "" else parts[3]
                newKstatSize = if (parts[4] == "default") "" else parts[4]
                newKstatAtime = if (parts[5] == "default") "" else parts[5]
                newKstatAtimeNsec = if (parts[6] == "default") "" else parts[6]
                newKstatMtime = if (parts[7] == "default") "" else parts[7]
                newKstatMtimeNsec = if (parts[8] == "default") "" else parts[8]
                newKstatCtime = if (parts[9] == "default") "" else parts[9]
                newKstatCtimeNsec = if (parts[10] == "default") "" else parts[10]
                newKstatBlocks = if (parts[11] == "default") "" else parts[11]
                newKstatBlksize = if (parts[12] == "default") "" else parts[12]
            }
        } else if (showDialog && initialConfig.isEmpty()) {
            resetFields()
        }
    }

    if (!showDialog) return

    val isEditing = initialConfig.isNotEmpty()

    FolkAlertDialog(
        onDismissRequest = {
            onDismiss()
            resetFields()
        },
        width = 340.dp,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(
                    if (isEditing) R.string.edit_kstat_statically_title
                    else R.string.add_kstat_statically_title
                ),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newKstatPath,
                    onValueChange = { newKstatPath = it },
                    label = { Text(stringResource(R.string.file_or_directory_path_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true,
                )

                KstatFieldPair("ino", newKstatIno, { newKstatIno = it }, "dev", newKstatDev, { newKstatDev = it }, !isLoading)
                KstatFieldPair("nlink", newKstatNlink, { newKstatNlink = it }, "size", newKstatSize, { newKstatSize = it }, !isLoading)
                KstatFieldPair("atime", newKstatAtime, { newKstatAtime = it }, "atime_nsec", newKstatAtimeNsec, { newKstatAtimeNsec = it }, !isLoading)
                KstatFieldPair("mtime", newKstatMtime, { newKstatMtime = it }, "mtime_nsec", newKstatMtimeNsec, { newKstatMtimeNsec = it }, !isLoading)
                KstatFieldPair("ctime", newKstatCtime, { newKstatCtime = it }, "ctime_nsec", newKstatCtimeNsec, { newKstatCtimeNsec = it }, !isLoading)
                KstatFieldPair("blocks", newKstatBlocks, { newKstatBlocks = it }, "blksize", newKstatBlksize, { newKstatBlksize = it }, !isLoading)

                Text(
                    text = stringResource(R.string.hint_use_default_value),
                    style = FolkType.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        onDismiss()
                        resetFields()
                    },
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (newKstatPath.isNotBlank()) {
                            onConfirm(
                                newKstatPath.trim(),
                                newKstatIno.trim().ifBlank { "default" },
                                newKstatDev.trim().ifBlank { "default" },
                                newKstatNlink.trim().ifBlank { "default" },
                                newKstatSize.trim().ifBlank { "default" },
                                newKstatAtime.trim().ifBlank { "default" },
                                newKstatAtimeNsec.trim().ifBlank { "default" },
                                newKstatMtime.trim().ifBlank { "default" },
                                newKstatMtimeNsec.trim().ifBlank { "default" },
                                newKstatCtime.trim().ifBlank { "default" },
                                newKstatCtimeNsec.trim().ifBlank { "default" },
                                newKstatBlocks.trim().ifBlank { "default" },
                                newKstatBlksize.trim().ifBlank { "default" }
                            )
                            resetFields()
                        }
                    },
                    enabled = newKstatPath.isNotBlank() && !isLoading,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(if (isEditing) R.string.susfs_save else R.string.add))
                }
            }
        }
    }
}

/** Two stat fields side by side, which is how the twelve of them fit. */
@Composable
private fun KstatFieldPair(
    firstLabel: String,
    firstValue: String,
    onFirstChange: (String) -> Unit,
    secondLabel: String,
    secondValue: String,
    onSecondChange: (String) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = firstValue,
            onValueChange = onFirstChange,
            label = { Text(firstLabel) },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true,
        )
        OutlinedTextField(
            value = secondValue,
            onValueChange = onSecondChange,
            label = { Text(secondLabel) },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true,
        )
    }
}
