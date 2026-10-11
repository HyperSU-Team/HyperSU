package com.sukisu.ultra.ui.component.folk

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * The frame every [BasicAlertDialog] in the app shares: a fixed-width rounded surface. A dialog
 * supplies only its own column, and the shape lives here so it cannot drift apart from dialog to
 * dialog.
 *
 * There is no blur behind it. This used to set `FLAG_BLUR_BEHIND` with a radius of 20 on the
 * dialog's window, which asks the system to blur everything behind it on every frame. That is the
 * same per-frame cost the rest of the blur switch was removed for, and it showed up worst on a
 * theme import: the loading dialog sits on screen while the import finishes, the whole app then
 * recomposes with a new wallpaper and palette underneath it, and the window manager is blurring
 * all of that while the main thread is busy - which is what made the app stop responding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolkAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 310.dp,
    shape: Shape = FolkShape.Dialog,
    dialogProperties: DialogProperties = DialogProperties(
        decorFitsSystemWindows = true,
        usePlatformDefaultWidth = false,
    ),
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = dialogProperties,
    ) {
        Surface(
            modifier = modifier
                .width(width)
                .wrapContentHeight(),
            shape = shape,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            color = AlertDialogDefaults.containerColor,
        ) {
            content()
        }
    }
}
