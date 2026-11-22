package com.sukisu.ultra.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFile

private const val TAG = "SELinuxChecker"

private const val ENFORCE_NODE = "/sys/fs/selinux/enforce"

/**
 * Returns the raw SELinux status string: "Enforcing", "Permissive", "Disabled" or "Unknown".
 *
 * Safe to call from any thread; IO is recommended because it may run a shell.
 *
 * Four readers, in this order, because each one is the only one that works somewhere:
 *
 *  1. `/sys/fs/selinux/enforce` through [SuFile], which falls back to a shell when the app uid may
 *     not open the node itself. Only a successful read decides here - an unreadable node is not a
 *     status.
 *  2. `getenforce` through the manager's root shell. On a device that hides the node from apps,
 *     this is the reader that answers, and the manager holds root already.
 *  3. `getenforce` through a plain `sh`, for the case where root is not usable at all.
 *  4. Inference only as a last resort: a node that does not exist means SELinux is off; one that
 *     exists but cannot be read is what an enforcing kernel looks like from an unprivileged app.
 *
 * The home row used to take the plain-`sh` path only, and stayed "unknown" on a device where the
 * node was hidden and the root shell was the one that could answer.
 */
fun getSELinuxStatusRaw(): String {
    readEnforceNodeValue()?.let { return it }
    readSelinuxViaRootShell()?.let { return it }
    readSelinuxViaShell()?.let { return it }
    return when {
        !SuFile(ENFORCE_NODE).exists() -> "Disabled"
        !SuFile(ENFORCE_NODE).canRead() -> "Enforcing"
        else -> "Unknown"
    }
}

/** Reads the node itself. Null means "this reader could not decide", never "not enforcing". */
private fun readEnforceNodeValue(): String? = runCatching {
    val node = SuFile(ENFORCE_NODE)
    if (!node.exists() || !node.canRead()) return@runCatching null
    val text = node.newInputStream().bufferedReader().use { it.readText() }.trim()
    when (text) {
        "1" -> "Enforcing"
        "0" -> "Permissive"
        else -> null
    }
}.onFailure { Log.w(TAG, "could not read the SELinux enforce node", it) }.getOrNull()

/** `getenforce` through the manager's own root shell. */
private fun readSelinuxViaRootShell(): String? = runCatching {
    toStatus(ShellUtils.fastCmd(getRootShell(), "getenforce"))
}.onFailure { Log.w(TAG, "could not read SELinux through the root shell", it) }.getOrNull()

/** `getenforce` through a plain shell, for a device where no root is available to this app. */
private fun readSelinuxViaShell(): String? {
    val stdout = ArrayList<String>()
    val result = runCatching {
        Shell.Builder.create().build("sh").use { it.newJob().add("getenforce").to(stdout).exec() }
    }.getOrNull() ?: return null
    if (!result.isSuccess) return null
    return toStatus(stdout.joinToString("\n"))
}

private fun toStatus(output: String): String? = when (output.trim()) {
    "Enforcing" -> "Enforcing"
    "Permissive" -> "Permissive"
    "Disabled" -> "Disabled"
    else -> null
}
