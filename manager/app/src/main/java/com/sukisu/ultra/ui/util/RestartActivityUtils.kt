package com.sukisu.ultra.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.sukisu.ultra.ui.MainActivity

/**
 * Makes the launcher point at [MainActivity] again.
 *
 * The alternate-icon row is gone from the settings screen, but a device that switched it on in an
 * older build left the real activity disabled and the alias enabled. Re-enabling the activity and
 * retiring the alias keeps that device launchable after the update; on every other device both
 * calls are no-ops.
 */
fun restoreLauncherComponent(context: Context) {
    val pm = context.packageManager
    val main = ComponentName(context, MainActivity::class.java)
    runCatching {
        pm.setComponentEnabledSetting(
            main,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        val alias = ComponentName(context, "${MainActivity::class.java.name}Alias")
        pm.setComponentEnabledSetting(
            alias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
