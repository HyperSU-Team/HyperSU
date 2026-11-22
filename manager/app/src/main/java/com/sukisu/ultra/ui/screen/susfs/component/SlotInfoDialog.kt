package com.sukisu.ultra.ui.screen.susfs.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.statustag.StatusTag
import com.sukisu.ultra.ui.screen.susfs.util.SlotInfo
import com.sukisu.ultra.ui.theme.tokens.FolkType
import com.sukisu.ultra.ui.util.isAbDevice

/**
 * The A/B slot inspector, in the FolkPatch design: one grouped block per slot,
 * each with its uname/build time as facts and the two "use this value" actions,
 * plus a refresh action. Only shown on A/B devices, as before.
 */
@Composable
fun SlotInfoDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    slotInfoList: List<SlotInfo>,
    currentActiveSlot: String,
    isLoadingSlotInfo: Boolean,
    onRefresh: () -> Unit,
    onUseUname: (String) -> Unit,
    onUseBuildTime: (String) -> Unit
) {
    val isAbDevice = produceState(initialValue = false) { value = isAbDevice() }.value
    var visible by remember { mutableStateOf(showDialog && isAbDevice) }

    LaunchedEffect(showDialog, isAbDevice) {
        visible = showDialog && isAbDevice
    }

    if (!visible) return

    FolkAlertDialog(onDismissRequest = onDismiss, width = 340.dp) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.susfs_slot_info_title),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.susfs_current_active_slot, currentActiveSlot),
                style = FolkType.Summary,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(12.dp))

            if (slotInfoList.isEmpty()) {
                Text(
                    text = stringResource(R.string.susfs_slot_info_unavailable),
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    slotInfoList.forEach { slotInfo ->
                        val isCurrent = slotInfo.slotName == currentActiveSlot
                        FolkSettingsGroup {
                            item {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = slotInfo.slotName,
                                            style = FolkType.Title,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrent) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                        )
                                        if (isCurrent) {
                                            Spacer(Modifier.width(6.dp))
                                            StatusTag(
                                                label = stringResource(R.string.susfs_slot_current_badge),
                                                backgroundColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    Text(
                                        text = stringResource(R.string.susfs_slot_uname, slotInfo.uname),
                                        style = FolkType.Summary,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.susfs_slot_build_time, slotInfo.buildTime),
                                        style = FolkType.Summary,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    Spacer(Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Button(
                                            onClick = { onUseUname(slotInfo.uname) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 46.dp),
                                            colors = FolkButtonDefaults.tonalColors(),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.susfs_slot_use_uname),
                                                maxLines = 2,
                                            )
                                        }
                                        Button(
                                            onClick = { onUseBuildTime(slotInfo.buildTime) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 46.dp),
                                            colors = FolkButtonDefaults.tonalColors(),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.susfs_slot_use_build_time),
                                                maxLines = 2,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onRefresh,
                enabled = !isLoadingSlotInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                colors = FolkButtonDefaults.filledColors(),
            ) {
                Text(stringResource(R.string.refresh))
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = FolkButtonDefaults.textColors(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
