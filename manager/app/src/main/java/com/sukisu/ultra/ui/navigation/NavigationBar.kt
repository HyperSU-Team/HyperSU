package com.sukisu.ultra.ui.navigation

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.sukisu.ultra.ui.component.FloatingBottomBar
import com.sukisu.ultra.ui.component.FloatingBottomBarItem
import com.sukisu.ultra.ui.theme.glass.GlassConfig
import com.sukisu.ultra.ui.theme.tokens.ContinuousCornerShape
import com.sukisu.ultra.ui.theme.tokens.FolkShape

/** Height of the docked bar, excluding the system navigation-bar inset. */
private val DockedBarHeight = 68.dp

/**
 * The bottom navigation bar.
 *
 * The floating variant is ReSukiSU's FloatingBottomBar ported verbatim: a blurred pill with
 * its own damped drag gesture, gravity-tracked specular indicator and no chrome around it -
 * no frosted slot, no elevation shadow. It needs the S blur pipeline, so below that the bar
 * falls back to the docked layout, which is the FolkPatch visual port: a surface holding
 * one selectable item per [BottomBarDestination] where the selected item carries a pill
 * behind its icon and both the icon and the label spring into the accent colour.
 *
 * Selection is driven purely by [selectedIndex] and reported back through
 * [onSelectedIndexChange] - there is no NavHostController, no compose-destinations route and no
 * repository behind this composable, so the caller keeps owning navigation.
 *
 * @param selectedIndex index of the active tab, 0..BottomBarDestination.PAGE_COUNT - 1.
 * @param onSelectedIndexChange invoked with the newly tapped tab index.
 * @param badge per-tab badge counts; a zero count renders no badge.
 * @param isFloating true for the floating pill, false for the docked bar.
 */
@Composable
fun FolkBottomBar(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    badge: NavigationBadgeState,
    isFloating: Boolean = false,
    backdrop: LayerBackdrop? = null,
    modifier: Modifier = Modifier,
) {
    val destinations = BottomBarDestination.entries

    if (isFloating && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // The pill samples the page behind it, exactly like ReSukiSU's bar: the caller records
        // its pager through Modifier.layerBackdrop and hands the handle over. Without a handle
        // there is nothing to refract, so the bar keeps its tinted fill instead of pretending.
        // The lens itself is an AGSL shader, which only exists from API 33.
        val effectiveBackdrop = backdrop ?: rememberLayerBackdrop()
        val blurEnabled = backdrop != null &&
            GlassConfig.blurEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        Box(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal),
                )
                .padding(
                    bottom = 12.dp +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            FloatingBottomBar(
                backdrop = effectiveBackdrop,
                selectedIndex = selectedIndex,
                onSelected = onSelectedIndexChange,
                tabsCount = destinations.size,
                isBlurEnabled = blurEnabled,
            ) { activateTab ->
                destinations.forEachIndexed { index, destination ->
                    FloatingBottomBarItem(
                        selected = index == selectedIndex,
                        onClick = { activateTab(index) },
                        modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                    ) {
                        val navBadge = badgeFor(index, badge)
                        val icon: @Composable () -> Unit = {
                            // The destination's own glyph, or the user's image when one is set.
                            NavBarIcon(
                                destination = destination,
                                selected = true,
                            )
                        }
                        if (navBadge != null) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = when (navBadge.tone) {
                                            BadgeTone.Alert -> MaterialTheme.colorScheme.error
                                            BadgeTone.Accent -> MaterialTheme.colorScheme.primary
                                        },
                                        contentColor = when (navBadge.tone) {
                                            BadgeTone.Alert -> MaterialTheme.colorScheme.onError
                                            BadgeTone.Accent -> MaterialTheme.colorScheme.onPrimary
                                        },
                                    ) {
                                        Text(text = navBadge.count.coerceAtMost(99).toString())
                                    }
                                },
                            ) {
                                icon()
                            }
                        } else {
                            icon()
                        }
                        Text(
                            text = stringResource(destination.label),
                            color = LocalContentColor.current,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
        return
    }

    val density = LocalDensity.current
    val barShape = ContinuousCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val haptics = LocalHapticFeedback.current
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentSelect by rememberUpdatedState(onSelectedIndexChange)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Walking the tabs sideways off the bar itself: one tab per 56dp of travel
            // with a haptic on each step. The items keep their clicks because a tap never
            // accumulates enough travel to cross a threshold, and up to the threshold the
            // drag is swallowed rather than forwarded - a deliberate horizontal swipe on
            // the bar changes pages instead of scrolling whatever sits behind it.
            .pointerInput(destinations.size) {
                var target = currentSelected
                var travelled = 0f
                val threshold = with(density) { 56.dp.toPx() }
                detectDragGestures(
                    onDragStart = {
                        target = currentSelected
                        travelled = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        travelled += dragAmount.x
                        if (travelled <= -threshold || travelled >= threshold) {
                            val step = if (travelled < 0f) 1 else -1
                            val next = (target + step).coerceIn(0, destinations.lastIndex)
                            if (next != target) {
                                target = next
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentSelect(target)
                            }
                            travelled = 0f
                        }
                    },
                    onDragEnd = { travelled = 0f },
                    onDragCancel = { travelled = 0f },
                )
            },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            shape = barShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DockedBarHeight)
                .padding(horizontal = 4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = index == selectedIndex

                // One spring drives the pill, the icon scale and the colour, so the whole item
                // settles as a single movement instead of three.
                val selection by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "FolkBottomBarSelection" + index,
                )

                val iconColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "FolkBottomBarIconColor" + index,
                )

                val labelColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "FolkBottomBarLabelColor" + index,
                )

                val pillColor = MaterialTheme.colorScheme.secondaryContainer
                val navBadge = badgeFor(index, badge)
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(FolkShape.CornerFull)
                        .selectable(
                            selected = selected,
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelectedIndexChange(index) },
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BadgedBox(
                        badge = {
                            if (navBadge != null) {
                                Badge(
                                    containerColor = when (navBadge.tone) {
                                        BadgeTone.Alert -> MaterialTheme.colorScheme.error
                                        BadgeTone.Accent -> MaterialTheme.colorScheme.primary
                                    },
                                    contentColor = when (navBadge.tone) {
                                        BadgeTone.Alert -> MaterialTheme.colorScheme.onError
                                        BadgeTone.Accent -> MaterialTheme.colorScheme.onPrimary
                                    },
                                ) {
                                    Text(
                                        text = navBadge.count.coerceAtMost(99).toString(),
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 28.dp)
                                .scale(0.9f + 0.1f * selection)
                                .background(
                                    color = pillColor.copy(alpha = selection),
                                    shape = FolkShape.CornerFull,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            NavBarIcon(
                                destination = destination,
                                selected = selected,
                                tint = iconColor,
                            )
                        }
                    }

                    Text(
                        text = stringResource(destination.label),
                        color = labelColor,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
    }
}
