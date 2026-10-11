package com.sukisu.ultra.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File

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
    val node = File("/sys/fs/selinux/enforce")
    if (!node.exists() || !node.canRead()) return@runCatching null
    when (node.readText().trim()) {
        "1" -> "Enforcing"
        "0" -> "Permissive"
        else -> null
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
