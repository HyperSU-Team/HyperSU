package com.sukisu.ultra.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukisu.ultra.ui.theme.SoundEffectConfig
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.util.SoundEffectManager

/** Width of the rail. */
private val RailWidth = 84.dp

/**
 * The FolkPatch navigation rail, used by the split-pane (landscape / large-screen) layout.
 *
 * Same visual language as [FolkBottomBar] - a surface, one selectable item per destination, a pill
 * behind the selected icon and a spring on selection - but laid out vertically with the items
 * grouped in the middle of the rail. Like the bottom bar it is driven only by an index, so the
 * caller keeps owning navigation.
 *
 * @param selectedIndex index of the active tab, `0..BottomBarDestination.PAGE_COUNT - 1`.
 * @param onSelectedIndexChange invoked with the newly tapped tab index.
 * @param badge per-tab badge counts; a zero count renders no badge.
 */
@Composable
fun FolkNavigationRail(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    badge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(RailWidth),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))

            BottomBarDestination.entries.forEachIndexed { index, destination ->
                val selected = index == selectedIndex

                val selection by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "FolkNavigationRailSelection$index",
                )

                val iconColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "FolkNavigationRailIconColor$index",
                )

                val labelColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "FolkNavigationRailLabelColor$index",
                )

                val pillColor = MaterialTheme.colorScheme.secondaryContainer
                val navBadge = badgeFor(index, badge)
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(FolkShape.CornerFull)
                        .selectable(
                            selected = selected,
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                onSelectedIndexChange(index)
                                // The rail is this layout's tab bar, so it uses the same scope
                                // as the bottom bar.
                                SoundEffectManager.playScoped(
                                    context,
                                    SoundEffectConfig.SCOPE_BOTTOM_BAR,
                                )
                            },
                        )
                        .padding(vertical = 8.dp)
                        .width(RailWidth - 16.dp),
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
                                .size(width = 52.dp, height = 30.dp)
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
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}
