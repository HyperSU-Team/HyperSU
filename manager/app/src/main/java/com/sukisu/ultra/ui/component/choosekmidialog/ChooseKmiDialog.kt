package com.sukisu.ultra.ui.component.choosekmidialog

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkChoiceOptionRow
import com.sukisu.ultra.ui.component.folk.FolkChooserDialog
import com.sukisu.ultra.ui.util.getCurrentKmi
import com.sukisu.ultra.ui.util.getSupportedKmis

/**
 * The KMI chooser, in the FolkPatch design: the supported KMIs as radio rows,
 * with the device's current KMI marked.
 *
 * It draws the same chooser frame as every other list row - the rows here are
 * supplied by this screen only because the current KMI is marked and confirm
 * has to stay disabled for an unsupported value.
 *
 * Behaviour is unchanged - the list and the current value are still probed
 * asynchronously, cancel restores the detected value, and confirm is only
 * enabled for a supported choice.
 */
@Composable
fun ChooseKmiDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSelected: (String?) -> Unit,
) {
    if (!show) return

    val supportedKMIs by produceState(initialValue = emptyList<String>()) {
        value = getSupportedKmis()
    }

    val currentKmi by produceState(initialValue = "") {
        value = getCurrentKmi()
    }

    val selectedKmi = remember(currentKmi) { mutableStateOf(currentKmi) }

    FolkChooserDialog(
        title = stringResource(R.string.select_kmi),
        onDismissRequest = {
            onDismissRequest()
            selectedKmi.value = currentKmi
        },
        onConfirm = {
            onSelected(selectedKmi.value)
            onDismissRequest()
        },
        confirmEnabled = supportedKMIs.contains(selectedKmi.value),
    ) {
        items(supportedKMIs) { kmi ->
            FolkChoiceOptionRow(
                title = kmi,
                summary = if (kmi == currentKmi) {
                    stringResource(R.string.current_device_kmi)
                } else {
                    null
                },
                selected = selectedKmi.value == kmi,
                onClick = { selectedKmi.value = kmi },
            )
        }
    }
}
