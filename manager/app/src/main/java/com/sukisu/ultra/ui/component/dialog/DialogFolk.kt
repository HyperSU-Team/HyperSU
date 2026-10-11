package com.sukisu.ultra.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkLoadingIndicator
import com.sukisu.ultra.ui.component.markdown.MarkdownContent
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * The loading dialog, in the FolkPatch design: a fixed-width surface holding a
 * single spinner-and-label row. Replaces both the Material and the Miuix
 * variants, so a caller no longer chooses a design.
 */
@Composable
fun LoadingDialogFolk(
    showDialog: MutableState<Boolean>,
) {
    if (!showDialog.value) return

    FolkAlertDialog(
        onDismissRequest = { showDialog.value = false },
        width = 280.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            FolkLoadingIndicator(text = stringResource(R.string.processing))
        }
    }
}

/**
 * The confirm dialog, in the FolkPatch design: title, optional body (plain,
 * Markdown or HTML) and a dismiss/confirm pair with the confirm action filled.
 *
 * Replaces both the Material and the Miuix variants. The body is height-capped
 * and scrolls so a long changelog cannot push the buttons off screen.
 */
@Composable
fun ConfirmDialogFolk(
    visuals: ConfirmDialogVisuals,
    confirm: () -> Unit,
    dismiss: () -> Unit,
    showDialog: MutableState<Boolean>,
) {
    if (!showDialog.value) return

    FolkAlertDialog(
        onDismissRequest = {
            dismiss()
            showDialog.value = false
        },
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = visuals.title,
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
            )

            val content = visuals.content
            if (!content.isNullOrBlank()) {
                Spacer(Modifier.padding(top = 12.dp))
                val scrollState = rememberScrollState()
                // MarkdownContent renders its document without a scroller of its own, so this
                // band is what has to move: capping it without scrolling left a long changelog
                // clipped at 380dp with no way to read the rest. The cap stays so the buttons
                // remain on screen.
                Box(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(scrollState),
                ) {
                    when {
                        visuals.isMarkdown -> MarkdownContent(content = content, isMarkdown = true)
                        visuals.isHtml -> MarkdownContent(content = content, isMarkdown = false)
                        else -> Text(
                            text = content,
                            style = FolkType.Summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.padding(top = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        dismiss()
                        showDialog.value = false
                    },
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(text = visuals.dismiss ?: stringResource(id = android.R.string.cancel))
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = {
                        confirm()
                        showDialog.value = false
                    },
                    colors = FolkButtonDefaults.filledColors(),
                ) {
                    Text(text = visuals.confirm ?: stringResource(id = android.R.string.ok))
                }
            }
        }
    }
}
