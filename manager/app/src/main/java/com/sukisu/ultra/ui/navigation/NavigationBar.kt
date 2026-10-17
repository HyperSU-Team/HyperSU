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
import com.sukisu.ultra.ui.component.FloatingBottomBar
import com.sukisu.ultra.Natives
import com.sukisu.ultra.ui.component.FloatingBottomBarItem
import com.sukisu.ultra.ui.theme.tokens.ContinuousCornerShape
import com.sukisu.ultra.ui.theme.tokens.FolkShape

/** Height of the docked bar, excluding the system navigation-bar inset. */
private val DockedBarHeight = 68.dp

/**
 * Height of the floating pill, excluding the 12dp gap below it and the system inset.
 *
 * Declared here so the bar and the Scaffold slot that reserves room for it agree: the pill itself
 * is 56dp with 4dp of padding around it.
 */
private val FloatingBarHeight = 64.dp

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
    modifier: Modifier = Modifier,
) {
    // Without a working driver there is nothing behind Superuser or Module, so the bar offers only
    // the pages that work. `Natives.isManager` is read once per composition rather than per item.
    val hasKernelSupport = remember { runCatching { Natives.isManager }.getOrDefault(false) }
    val destinations = remember(hasKernelSupport) {
        BottomBarDestination.available(hasKernelSupport)
    }

    if (isFloating && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // A plain tinted pill. The backdrop and the AGSL lens were removed along with the blur
        // switch: the refraction is a per-frame shader over the whole page, which is the main
        // reason a tab change took a moment to settle on a mid-range device.
        //
        // The height is declared rather than left to the content. Scaffold measures this slot to
        // decide the inner padding it hands the page, and a Box whose only child is centred
        // reports the child's height - but the pill sits inside 12dp of bottom padding and the
        // system navigation-bar inset, so the slot came out shorter than the space the pill
        // actually covers. Pages then padded themselves by too little and their last row ended up
        // underneath the pill.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal),
                )
                .padding(
                    bottom = 12.dp +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                )
                .height(FloatingBarHeight),
            contentAlignment = Alignment.Center,
        ) {
            FloatingBottomBar(
                selectedIndex = selectedIndex,
                onSelected = onSelectedIndexChange,
                tabsCount = destinations.size,
                pageForTab = { position -> destinations[position].pageIndex },
            ) { activateTab ->
                destinations.forEachIndexed { index, destination ->
                    // The tab's position in the bar and the page it opens are not the same number
                    // once entries are dropped: with Superuser and Module hidden, Settings is the
                    // second tab but still page 3. Selection goes by `pageIndex`; `activateTab`
                    // takes both, because the pill travels in tab positions.
                    FloatingBottomBarItem(
                        selected = destination.pageIndex == selectedIndex,
                        onClick = { activateTab(index, destination.pageIndex) },
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
                // By page, not by position: the two differ once entries are dropped.
                val selected = destination.pageIndex == selectedIndex

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
                            onClick = { onSelectedIndexChange(destination.pageIndex) },
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
