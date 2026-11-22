package com.sukisu.ultra.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell

private const val TAG = "SELinuxChecker"

/**
 * Returns the raw SELinux status string: "Enforcing", "Permissive", "Disabled" or "Unknown".
 *
 * Safe to call from any thread; IO is recommended because it may run a shell.
 *
 * The sysfs node is read first, because it needs no shell and no root: `/sys/fs/selinux/enforce`
 * is world-readable on essentially every Android device, and it answers the question directly
 * rather than by inference. The shell is the fallback for the devices where it is not, and it is a
 * plain `sh` rather than a root shell - a status read should not raise a root prompt, and on a
 * device with no root at all the root shell would either fail or hang.
 *
 * This mattered for the home screen: the status row was empty whenever the shell path failed, even
 * though the answer was sitting in a file the app can always read.
 */
fun getSELinuxStatusRaw(): String {
    readSelinuxEnforceNode()?.let { return it }
    return readSelinuxViaShell()
}

/**
 * Reads `/sys/fs/selinux/enforce`, which holds "1" for enforcing and "0" for permissive.
 *
 * Returns null when the node is missing or unreadable, so the caller can fall back rather than
 * reporting "Disabled" for a file it could not open - those are different answers.
 */
private fun readSelinuxEnforceNode(): String? = runCatching {
    // SuFile, not File: it retries through a shell when the app uid is not allowed to open the
    // node, which is the common case when SELinux hiding is on. A plain File read returned null
    // there and the row fell back to "unknown" even though the mode was readable.
    val node = com.topjohnwu.superuser.io.SuFile("/sys/fs/selinux/enforce")
    when {
        !node.exists() -> "Disabled"
        !node.isFile -> null
        // Present but unreadable is what an enforcing kernel looks like from an unprivileged app.
        !node.canRead() -> "Enforcing"
        else -> when (
            runCatching { node.newInputStream() }.getOrNull()?.bufferedReader()
                ?.use { it.readText().trim() }
        ) {
            "1" -> "Enforcing"
            "0" -> "Permissive"
            else -> null
        }
    }
}.onFailure { Log.w(TAG, "could not read the SELinux enforce node", it) }.getOrNull()

/** The shell fallback, for devices that do not expose the sysfs node to an app. */
private fun readSelinuxViaShell(): String {
    val stdout = ArrayList<String>()
    val stderr = ArrayList<String>()
    val result = runCatching {
        Shell.Builder.create().build("sh").use {
            it.newJob().add("getenforce").to(stdout, stderr).exec()
        }
    }.getOrNull() ?: return "Unknown"

    if (result.isSuccess) {
        return when (val out = stdout.joinToString("\n").trim()) {
            "Enforcing", "Permissive", "Disabled" -> out
            else -> "Unknown"
        }
    }

    // A permission error means SELinux is on and refusing the read, which is what an enforcing
    // kernel does. It is an inference rather than a reading, so it is the last resort.
    return if (stderr.joinToString("\n").trim().endsWith("Permission denied")) {
        "Enforcing"
    } else {
        "Unknown"
    }
}
