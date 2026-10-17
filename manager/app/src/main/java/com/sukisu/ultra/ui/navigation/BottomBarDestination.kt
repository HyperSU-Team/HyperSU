package com.sukisu.ultra.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Cottage
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.sukisu.ultra.R

/**
 * Main-tab destinations of the bottom navigation, ported from FolkPatch
 * `ui/navigation/BottomBarDestination.kt` and mapped onto HyperSU's four
 * pager tabs (Home / SuperUser / Module / Settings).
 *
 * [pageIndex] is the tab index inside the main HorizontalPager.
 */
enum class BottomBarDestination(
    @param:StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    val pageIndex: Int,
) {
    Home(
        R.string.home,
        Icons.Rounded.Cottage,
        Icons.Outlined.Cottage,
        0,
    ),
    SuperUser(
        R.string.superuser,
        Icons.Rounded.AdminPanelSettings,
        Icons.Outlined.AdminPanelSettings,
        1,
    ),
    Module(
        R.string.module,
        Icons.Rounded.Extension,
        Icons.Outlined.Extension,
        2,
    ),
    Settings(
        R.string.settings,
        Icons.Rounded.Tune,
        Icons.Outlined.Tune,
        3,
    );

    companion object {
        /** Tab count of the main pager. */
        const val PAGE_COUNT = 4

        /**
         * The tabs that can actually do something on this device.
         *
         * Superuser and Module are only meaningful when a driver answers: the first would list no
         * apps and the second no modules, and both open a root shell that cannot start, so on a
         * device without HyperSU in the kernel they are two entries that lead to an empty screen.
         * They are dropped from the bar instead, leaving Home and Settings.
         *
         * Both are judged from `Natives.isManager`, which asks the driver whether it recognises
         * this manager. A kernel that has the driver but not this manager's signature answers
         * false as well, which is correct here: the superuser and module lists are served by
         * `ksud`, and without recognition it cannot run.
         */
        fun available(hasKernelSupport: Boolean): List<BottomBarDestination> =
            if (hasKernelSupport) entries.toList() else listOf(Home, Settings)
    }
}
