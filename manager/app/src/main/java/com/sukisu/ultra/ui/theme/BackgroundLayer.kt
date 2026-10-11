package com.sukisu.ultra.ui.theme

import android.media.MediaPlayer
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import com.sukisu.ultra.ui.navigation.BottomBarDestination
import com.sukisu.ultra.ui.navigation3.Route
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * Resolves the wallpaper URI shown for [route] according to the current background mode.
 *
 * The manager is a four-tab pager inside [Route.Main]; a pushed detail route keeps the
 * wallpaper of the tab it was opened from, so the background never changes underneath a push
 * or a back gesture.
 */
fun resolveBackgroundUriForRoute(route: NavKey?, mainPage: Int): String? {
    if (!BackgroundConfig.isMultiBackgroundEnabled) return BackgroundConfig.customBackgroundUri

    val pageIndex = when (route) {
        is Route.Home -> BottomBarDestination.Home.pageIndex
        is Route.SuperUser -> BottomBarDestination.SuperUser.pageIndex
        is Route.Module -> BottomBarDestination.Module.pageIndex
        is Route.Settings -> BottomBarDestination.Settings.pageIndex
        // Detail routes and the main pager keep the tab the user is on.
        else -> mainPage
    }

    val uri = when (pageIndex) {
        BottomBarDestination.Home.pageIndex -> BackgroundConfig.homeBackgroundUri
        BottomBarDestination.SuperUser.pageIndex -> BackgroundConfig.superuserBackgroundUri
        BottomBarDestination.Module.pageIndex -> BackgroundConfig.moduleBackgroundUri
        else -> BackgroundConfig.settingsBackgroundUri
    }

    // A tab without its own image falls back to the shared one instead of an empty page.
    return uri ?: BackgroundConfig.customBackgroundUri
}

/**
 * The wallpaper the theme adapts its content colours to.
 *
 * Deliberately one stable image rather than whatever page is on screen: with multi-background
 * mode the palette would otherwise flicker on every tab change. The video wins when it is on,
 * because that is what is actually on screen.
 */
fun themeWallpaperUri(): String? {
    if (BackgroundConfig.isVideoBackgroundEnabled) {
        BackgroundConfig.videoBackgroundUri?.let { return it }
    }
    return BackgroundConfig.customBackgroundUri
        ?: BackgroundConfig.homeBackgroundUri
        ?: BackgroundConfig.superuserBackgroundUri
        ?: BackgroundConfig.moduleBackgroundUri
        ?: BackgroundConfig.settingsBackgroundUri
}

/**
 * The wallpaper layer, drawn behind the whole app.
 *
 * Priority: image (single or per page) over a solid fallback. When the feature is off nothing
 * is drawn, so the theme's own background stays in charge exactly as before.
 */
@Composable
fun BackgroundLayer(
    currentRoute: NavKey? = null,
    mainPage: Int = 0,
) {
    if (!BackgroundConfig.isCustomBackgroundEnabled) return

    // isInDarkTheme() follows the app's own colour mode, not the system one, so the fallback
    // and the dim match the theme the user actually selected.
    val darkTheme = isInDarkTheme()
    // The theme may have raised the dim to keep text readable over a bright wallpaper; that
    // rendered value wins over the raw preference.
    val dim = LocalWallpaperDim.current ?: BackgroundConfig.getEffectiveBackgroundDim(darkTheme)
    val targetUri = resolveBackgroundUriForRoute(currentRoute, mainPage)

    // One group behind the content box in MainActivity: the fallback, the image and the dim
    // are its children, so they stack in composition order and never depend on the zIndex of
    // nodes a helper like Crossfade introduces.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(-1f),
    ) {
        // Solid base so the window background (often white) can never flash through while the
        // image is still decoding or between page switches.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (darkTheme) Color.Black else Color.White),
        )

        // Priority: video > still image, so the moving wallpaper wins when both are set.
        val videoUri = if (BackgroundConfig.isVideoBackgroundEnabled) {
            BackgroundConfig.videoBackgroundUri
        } else {
            null
        }

        if (!videoUri.isNullOrEmpty()) {
            VideoWallpaper(uri = videoUri, dim = dim)
        } else if (!targetUri.isNullOrEmpty()) {
            // Crossfading between the per-page images keeps multi-background mode from popping.
            Crossfade(
                targetState = targetUri,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(320),
                label = "BackgroundWallpaper",
            ) { uri ->
                // Drawn sharp. `Modifier.blur` compiles to a RenderEffect over the whole layer, so
                // it is re-applied to a full-screen surface on every frame the wallpaper is on
                // screen - and a theme import is the worst case, because the new wallpaper appears
                // underneath the loading dialog while the whole app recomposes and the palette is
                // regenerated. That is what made the app stop responding during an import.
                Image(
                    painter = rememberAsyncImagePainter(model = uri),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim)),
            )
        }
    }
}

/**
 * The looping video wallpaper.
 *
 * A decode error is swallowed (`setOnErrorListener` returns true): the solid base behind this
 * keeps the page readable instead of letting the window background flash through.
 */
@Composable
private fun VideoWallpaper(uri: String, dim: Float) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val volume = BackgroundConfig.videoVolume

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }

    key(uri) {
        AndroidView(
            factory = { ctx ->
                object : VideoView(ctx) {
                    // The default AT_MOST measurement letterboxes the clip; fill the layer.
                    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                        setMeasuredDimension(
                            getDefaultSize(0, widthMeasureSpec),
                            getDefaultSize(0, heightMeasureSpec),
                        )
                    }
                }.apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setVideoPath(uri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        mp.setVolume(volume, volume)
                        mediaPlayer = mp
                        start()
                    }
                    setOnErrorListener { _, _, _ -> true }
                }
            },
            update = { mediaPlayer?.setVolume(volume, volume) },
            onRelease = { view -> runCatching { view.stopPlayback() } },
            modifier = Modifier.fillMaxSize(),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dim)),
    )
}
