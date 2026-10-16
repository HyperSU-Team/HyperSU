package com.sukisu.ultra.ui.kernelFlash.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.sukisu.ultra.ui.component.folk.FolkSelectableRow
import com.sukisu.ultra.ui.theme.tokens.FolkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The A/B slot chooser, in the FolkPatch design.
 *
 * The current slot is read once when the dialog opens and is used as the initial
 * selection; a read failure surfaces as an error line. Confirm stays disabled
 * until a slot is chosen, and the chosen slot is reported as "a" or "b".
 */
@Composable
fun SlotSelectionDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onSlotSelected: (String) -> Unit,
) {
    if (!show) return

    var currentSlot by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedSlot by remember { mutableStateOf<String?>(null) }
    val operationFailedString = stringResource(R.string.operation_failed)

    LaunchedEffect(show) {
        if (show) {
            try {
                currentSlot = withContext(Dispatchers.IO) { getCurrentSlot() }
                selectedSlot = when (currentSlot) {
                    "a" -> "a"
                    "b" -> "b"
                    else -> null
                }
                errorMessage = null
            } catch (_: Exception) {
                errorMessage = operationFailedString
                currentSlot = null
            }
        }
    }

    FolkAlertDialog(
        onDismissRequest = onDismiss,
        width = 340.dp,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.select_slot_title),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: stringResource(R.string.operation_failed),
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.current_slot,
                        currentSlot?.uppercase() ?: stringResource(R.string.not_supported),
                    ),
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Text(
                text = stringResource(R.string.select_slot_description),
                style = FolkType.Summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("a" to stringResource(R.string.slot_a), "b" to stringResource(R.string.slot_b))
                    .forEach { (slot, title) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.SdStorage,
                                contentDescription = null,
                                tint = if (selectedSlot == slot) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            FolkSelectableRow(
                                title = title,
                                selected = selectedSlot == slot,
                                onClick = { selectedSlot = slot },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = {
                        selectedSlot?.let { onSlotSelected(it) }
                        onDismiss()
                    },
                    enabled = selectedSlot != null,
                    colors = FolkButtonDefaults.filledColors(),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}
