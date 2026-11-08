package com.sukisu.ultra.ui.component.folk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl

/**
 * A list of label/value facts inside one container.
 *
 * Rows are separated by a hairline rather than each becoming its own card, so a
 * block of facts reads as one object. Build rows with [fact]; use [row] for a
 * row that is not a plain label/value pair.
 */
class FolkFactsScope {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun fact(label: String, value: String, valueTone: FolkSeverity = FolkSeverity.Neutral) {
        items.add { FolkFactRow(label = label, value = value, valueTone = valueTone) }
    }

    fun row(content: @Composable () -> Unit) {
        items.add(content)
    }
}

/**
 * A list of label/value facts.
 *
 * No dividers and no container: FolkPatch draws these as plain stacked text, with the label and
 * its value directly under one another and nothing between rows. Drawing hairlines between them
 * turned the block into a settings list, which is a different thing from what this screen is -
 * a readout of what the device is.
 */
@Composable
fun FolkFactsGroup(
    modifier: Modifier = Modifier,
    content: @Composable FolkFactsScope.() -> Unit,
) {
    val scope = FolkFactsScope()
    scope.content()
    if (scope.items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        scope.items.forEach { item -> item() }
    }
}

/**
 * One label/value fact. The value sits on its own line under the label, so a
 * long value (a kernel string, a build fingerprint) cannot squeeze the label
 * into a one-character-per-line column. The value takes its colour from a
 * [FolkSeverity] so a state value (a version that is behind, a service that is
 * off) can be tinted without the caller picking a colour.
 *
 * Tapping copies the value, which is what these rows are for - a kernel string or
 * a fingerprint exists to be pasted somewhere. The copy is silent unless the
 * setting asks for a confirmation, because a toast on every tap would be noise.
 */
@Composable
fun FolkFactRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueTone: FolkSeverity = FolkSeverity.Neutral,
) {
    val context = LocalContext.current
    // Read per row rather than hoisted: the group is built from a scope lambda that cannot read
    // composition, and the setting only changes on the settings page.
    val copyEnabled = remember { SettingsRepositoryImpl().enableInfoCopy }
    val copiedText = stringResource(R.string.copied_to_clipboard)
    // A blank value renders as nothing at all, which is indistinguishable from a row whose text
    // failed to draw. Showing a dash says which one it is - both to the user and to anyone reading
    // a screenshot of it.
    val displayValue = value.ifBlank { "—" }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (copyEnabled && value.isNotBlank()) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { copyToClipboard(context, label, value, copiedText) }
                } else {
                    Modifier
                }
            )
            // FolkPatch's spacing, not a settings row's: the label and its value are one block of
            // text, so the gap inside the row is small and the gap between rows is what separates
            // them. A settings row's taller padding plus a divider made each fact read as its own
            // card, which is why the block looked nothing like the original.
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = folkPreferenceSummaryStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        // A plain Text rather than the wrap-safe one. The wrap-safe version inserts zero-width
        // spaces to give the line breaker somewhere to break a fingerprint, and it was the only
        // change made to this row before the values stopped drawing; whether or not it is the
        // cause, a plain Text is what this row rendered correctly with before, so it is what it
        // renders with now.
        Text(
            text = displayValue,
            style = folkPreferenceValueStyle(),
            color = folkSeverityColor(valueTone),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun copyToClipboard(context: Context, label: String, value: String, confirmation: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
}
