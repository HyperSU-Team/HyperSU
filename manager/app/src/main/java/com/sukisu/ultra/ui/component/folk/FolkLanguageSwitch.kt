package com.sukisu.ultra.ui.component.folk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.util.LanguageSwitchState
import kotlinx.coroutines.delay

/** How long the switch page stays up after the app has composed again. */
private const val SettleMillis = LanguageSwitchState.SETTLE_MILLIS

/** How long the page takes to fade in or out. */
private const val FadeMillis = 220L

/**
 * The page shown while a language switch is applied: the app's own loader, with
 * its label written in the language being switched to.
 *
 * Two things about what a language switch actually does shape this:
 *
 * - The switch recreates the activity, so this has to *replace* the app content
 *   rather than cover it. The frame the new activity draws first must be this
 *   page, or the work spent composing the rest of the app is spent on a frame
 *   nobody sees.
 * - The label is read from the activity's own resources, which carry the new
 *   language only once the switch has been applied. Until then the page is drawn
 *   with the loader alone - showing the text now would show it in the language
 *   the user is leaving, which is exactly the wrong answer.
 *
 * It fades in and out. The page appears on the frame the new activity draws and disappears when the
 * switch settles, and without a fade both of those are single-frame cuts - the change reads as a
 * flicker rather than as the app having switched language.
 *
 * What this page cannot cover is the system's starting window: the recreation
 * replays the splash screen, and that window belongs to the system. Its colour
 * is themed to the app background (`windowSplashScreenBackground`) so it reads
 * as this page rather than as a black flash.
 */
@Composable
fun FolkLanguageSwitch(tag: String) {
    val context = LocalContext.current
    val configuration = context.resources.configuration
    val currentTag = configuration.locales[0]?.toLanguageTag().orEmpty()
    val switched = tag.isEmpty() || currentTag.equals(tag, ignoreCase = true)

    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(tag) {
        delay(SettleMillis)
        visible = false
        // Only clear the flag once the fade has run, so the page is not pulled out from under its
        // own exit animation.
        delay(FadeMillis)
        LanguageSwitchState.finish()
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                FolkLoadingIndicator(
                    text = if (switched) stringResource(R.string.switching_language) else null,
                )
            }
        }
    }
}
