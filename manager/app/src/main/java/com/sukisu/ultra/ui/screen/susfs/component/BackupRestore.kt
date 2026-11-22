package com.sukisu.ultra.ui.screen.susfs.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.screen.susfs.util.BackupData
import com.sukisu.ultra.ui.screen.susfs.util.SuSFSManager
import com.sukisu.ultra.ui.theme.tokens.FolkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * The backup/restore block of the basic settings, in the FolkPatch design.
 *
 * The whole flow is unchanged: create a backup through `CreateDocument`, read a
 * file back through `OpenDocument`, validate it, show what it contains and only
 * then restore it. Only the presentation moved to Folk dialogs.
 */
@Composable
fun BackupRestoreComponent(
    isLoading: Boolean,
    onLoadingChange: (Boolean) -> Unit,
    onConfigReload: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var internalLoading by remember { mutableStateOf(false) }
    val actualLoading = isLoading || internalLoading

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var selectedBackupFile by remember { mutableStateOf<String?>(null) }
    var backupInfo by remember { mutableStateOf<BackupData?>(null) }

    val backupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { fileUri ->
            coroutineScope.launch {
                try {
                    internalLoading = true
                    onLoadingChange(true)
                    val fileName = SuSFSManager.getDefaultBackupFileName()
                    val tempFile = File(context.cacheDir, fileName)

                    val success = SuSFSManager.createBackup(context, tempFile.absolutePath)
                    if (success) {
                        withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                                    tempFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                tempFile.delete()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    internalLoading = false
                    onLoadingChange(false)
                    showBackupDialog = false
                }
            }
        }
    }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            coroutineScope.launch {
                val tempFile = File(context.cacheDir, "temp_restore.susfs_backup")
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                            tempFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }

                    val backup = SuSFSManager.validateBackupFile(tempFile.absolutePath)
                    if (backup != null) {
                        selectedBackupFile = tempFile.absolutePath
                        backupInfo = backup
                        showRestoreConfirmDialog = true
                    } else {
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runCatching { tempFile.delete() }
                } finally {
                    showRestoreDialog = false
                }
            }
        }
    }

    if (showBackupDialog) {
        FolkAlertDialog(onDismissRequest = { showBackupDialog = false }) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.susfs_backup_title),
                    style = FolkType.Title,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.susfs_backup_description),
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showBackupDialog = false },
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            val timestamp = dateFormat.format(Date())
                            backupFileLauncher.launch("SuSFS_Config_$timestamp.susfs_backup")
                        },
                        enabled = !actualLoading,
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(R.string.susfs_backup_create))
                    }
                }
            }
        }
    }

    if (showRestoreDialog) {
        FolkAlertDialog(onDismissRequest = { showRestoreDialog = false }) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.susfs_restore_title),
                    style = FolkType.Title,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.susfs_restore_description),
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showRestoreDialog = false },
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            restoreFileLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        enabled = !actualLoading,
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(R.string.susfs_restore_select_file))
                    }
                }
            }
        }
    }

    val info = backupInfo
    if (showRestoreConfirmDialog && info != null) {
        val dismissRestoreConfirm = {
            showRestoreConfirmDialog = false
            // Cancelling must not leave the validated copy behind in the cache.
            selectedBackupFile?.let { runCatching { File(it).delete() } }
            selectedBackupFile = null
            backupInfo = null
        }

        FolkAlertDialog(onDismissRequest = dismissRestoreConfirm) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.susfs_restore_confirm_title),
                    style = FolkType.Title,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.susfs_restore_confirm_description),
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Column {
                    val dateFormat =
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", LocalLocale.current.platformLocale)
                    Text(
                        text = stringResource(
                            R.string.susfs_backup_info_date,
                            dateFormat.format(Date(info.timestamp)),
                        ),
                        style = FolkType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.susfs_backup_info_device, info.deviceInfo),
                        style = FolkType.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.susfs_backup_info_version, info.version),
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
                        onClick = dismissRestoreConfirm,
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            selectedBackupFile?.let { filePath ->
                                coroutineScope.launch {
                                    try {
                                        internalLoading = true
                                        onLoadingChange(true)
                                        val success = SuSFSManager.restoreFromBackup(context, filePath)
                                        if (success) {
                                            onConfigReload()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        internalLoading = false
                                        onLoadingChange(false)
                                        showRestoreConfirmDialog = false
                                        // The restore has consumed the copy; leaving it in the
                                        // cache was a permanent leftover for every restore.
                                        selectedBackupFile?.let { runCatching { File(it).delete() } }
                                        delay(100.milliseconds)
                                        selectedBackupFile = null
                                        backupInfo = null
                                    }
                                }
                            }
                        },
                        enabled = !actualLoading,
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(R.string.susfs_restore_confirm))
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.OutlinedButton(
            onClick = { showBackupDialog = true },
            enabled = !actualLoading,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp),
            colors = FolkButtonDefaults.tonalColors(),
        ) {
            Icon(
                imageVector = Icons.Default.Backup,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.susfs_backup_title))
        }
        androidx.compose.material3.OutlinedButton(
            onClick = { showRestoreDialog = true },
            enabled = !actualLoading,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp),
            colors = FolkButtonDefaults.tonalColors(),
        ) {
            Icon(
                imageVector = Icons.Default.Restore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.susfs_restore_title))
        }
    }
}
