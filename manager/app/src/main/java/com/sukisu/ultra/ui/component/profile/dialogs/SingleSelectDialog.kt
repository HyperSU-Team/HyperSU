package com.sukisu.ultra.ui.component.profile.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkSelectableRow
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * Pick one item from a list, in the FolkPatch design: radio rows in a dialog
 * with a confirm/cancel pair. Generic over the item type so the profile editor
 * can reuse it for namespaces and templates.
 */
@Composable
fun <T> SingleSelectDialog(
    title: String,
    items: List<T>,
    selectedItem: T,
    itemTitle: (T) -> String,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    // Keyed on the incoming selection: this dialog is used from more than one place, and without
    // the key a reopened dialog kept the previous choice rather than showing the current one.
    var selected by remember(selectedItem) { mutableStateOf(selectedItem) }

    FolkAlertDialog(onDismissRequest = onDismiss, width = 340.dp) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.forEach { item ->
                    FolkSelectableRow(
                        title = itemTitle(item),
                        selected = selected == item,
                        onClick = { selected = item },
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
                    onClick = onDismiss,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = {
                        onConfirm(selected)
                        onDismiss()
                    },
                    colors = FolkButtonDefaults.filledColors(),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}
