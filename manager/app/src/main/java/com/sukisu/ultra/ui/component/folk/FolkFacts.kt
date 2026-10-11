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
import androidx.compose.material3.HorizontalDivider
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
import com.sukisu.ultra.ui.theme.tokens.FolkTheme

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

@Composable
fun FolkFactsGroup(
    modifier: Modifier = Modifier,
    content: @Composable FolkFactsScope.() -> Unit,
) {
    val scope = FolkFactsScope()
    scope.content()
    if (scope.items.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        scope.items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(
                    color = FolkTheme.palette.separator,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item()
        }
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = folkPreferenceSummaryStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = value,
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
