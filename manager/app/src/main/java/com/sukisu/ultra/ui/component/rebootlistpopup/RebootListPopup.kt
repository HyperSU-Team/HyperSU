package com.sukisu.ultra.ui.component.rebootlistpopup

import android.content.Context
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.KsuIsValid
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.util.reboot

data class RebootListOption(
    @param:StringRes val labelRes: Int,
    val reason: String,
)

@Composable
fun getRebootListOption(): List<RebootListOption> {
    val pm = LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?

    @Suppress("DEPRECATION")
    val isRebootingUserspaceSupported = pm?.isRebootingUserspaceSupported == true

    return buildList {
        add(RebootListOption(R.string.reboot, ""))
        if (isRebootingUserspaceSupported) {
            add(RebootListOption(R.string.reboot_userspace, "userspace"))
        }
        add(RebootListOption(R.string.reboot_soft, "soft_reboot"))
        add(RebootListOption(R.string.reboot_recovery, "recovery"))
        add(RebootListOption(R.string.reboot_bootloader, "bootloader"))
        add(RebootListOption(R.string.reboot_download, "download"))
        add(RebootListOption(R.string.reboot_edl, "edl"))
    }
}

/** Reboots on selection, but confirms first in jailbreak mode where a plain reboot drops root. */
@Composable
fun rememberRebootAction(): (String) -> Unit {
    val title = stringResource(R.string.reboot)
    val message = stringResource(R.string.jailbreak_reboot_warning)
    val confirmDialog = rememberConfirmDialog(onConfirm = { reboot() })

    return remember(title, message, confirmDialog) {
        { reason ->
            if (Natives.isLateLoadMode && reason.isEmpty()) {
                confirmDialog.showConfirm(title = title, content = message)
            } else {
                reboot(reason)
            }
        }
    }
}

/**
 * The reboot targets as menu rows, shared by every top bar that offers a
 * reboot. Kept separate from [RebootListPopup] so a screen with its own menu
 * can reuse just the rows.
 */
@Composable
fun RebootDropdownItems(onItemClick: (String) -> Unit) {
    val options = getRebootListOption()
    options.forEachIndexed { index, option ->
        SelectableDropdownMenuItem(
            selected = false,
            onClick = { onItemClick(option.reason) },
            text = { Text("  " + stringResource(option.labelRes)) },
            shapes = MenuDefaults.itemShape(index = index, count = options.size),
        )
    }
}

/**
 * The reboot entry point for a top bar, in the FolkPatch design: a power icon
 * that opens a menu of reboot targets.
 *
 * The option list and the jailbreak-mode confirmation are unchanged; only the
 * presentation is the single Folk design now.
 */
@Composable
fun RebootListPopup() {
    var expanded by remember { mutableStateOf(false) }

    KsuIsValid {
        val onReboot = rememberRebootAction()

        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = stringResource(id = R.string.reboot),
            )
        }

        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                RebootDropdownItems { reason ->
                    expanded = false
                    onReboot(reason)
                }
            }
        }
    }
}
