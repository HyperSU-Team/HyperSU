package com.sukisu.ultra.ui.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sukisu.ultra.ui.theme.BottomBarIconConfig

/**
 * One bottom-bar icon: the destination's glyph, or the user's own image when they set one.
 *
 * The custom image is drawn untinted. A nav glyph is a vector that takes the bar's content
 * colour, but a user's picture has its own colours and tinting it would flatten it to a
 * silhouette - which is not what someone picking an icon wants.
 *
 * Read through [BottomBarIconConfig.revision] so replacing an icon redraws: the file path does
 * not change when its contents do, and a plain read would not recompose.
 */
@Composable
fun NavBarIcon(
    destination: BottomBarDestination,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    /**
     * Tint for the built-in glyph. Ignored for a custom image, which keeps its own colours.
     * Defaults to the ambient content colour so callers that have no animation can omit it.
     */
    tint: Color = LocalContentColor.current,
) {
    val context = LocalContext.current
    // Reading the counter is what subscribes this composable to icon changes.
    val revision = BottomBarIconConfig.revision
    val customUri = remember(revision, destination.name) {
        if (BottomBarIconConfig.isEnabled(context)) {
            BottomBarIconConfig.getCustomIconUri(context, destination.name)
        } else {
            null
        }
    }

    if (customUri != null) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(customUri)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(destination.label),
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size),
        )
    } else {
        Icon(
            imageVector = if (selected) destination.iconSelected else destination.iconNotSelected,
            contentDescription = stringResource(destination.label),
            tint = tint,
            modifier = modifier.size(size),
        )
    }
}
