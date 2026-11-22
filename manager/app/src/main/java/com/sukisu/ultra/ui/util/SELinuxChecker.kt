package com.sukisu.ultra.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import java.io.File

private const val TAG = "SELinuxChecker"

private const val ENFORCE_NODE = "/sys/fs/selinux/enforce"

/**
 * Returns the raw SELinux status string: "Enforcing", "Permissive", "Disabled" or "Unknown".
 *
 * Safe to call from any thread; IO is recommended because it may run a shell.
 *
 * This is 3a8dff100's reader - the node first, because it needs no shell and no root, then a plain
 * `sh` - with one step inserted between them: on a device that hides the node from an app, the
 * manager's own root shell can still `cat` it, and that is the only reader left.
 */
fun getSELinuxStatusRaw(): String {
    readSelinuxEnforceNode()?.let {
        Log.i(TAG, "SELinux from the node: $it")
        return it
    }
    readSelinuxEnforceNodeAsRoot()?.let {
        Log.i(TAG, "SELinux from the root shell: $it")
        return it
    }
    // `ro.boot.selinux` is what the kernel was booted with. It needs no shell and no root, so it
    // is the answer that is available on the very first frame - which is the case that used to
    // stay "unknown" until the activity was recreated.
    readBootSelinuxProperty()?.let {
        Log.i(TAG, "SELinux from ro.boot.selinux: $it")
        return it
    }
    val viaShell = readSelinuxViaShell()
    Log.w(TAG, "SELinux unknown; node, root cat and ro.boot.selinux all failed, sh said $viaShell")
    return viaShell
}

/**
 * `ro.boot.selinux` - "enforcing" or "permissive" as recorded at boot.
 *
 * This is the boot mode, not the live one, so it is used before any inference but after the node
 * reads: a device that switched to permissive at runtime still reports the live value from them.
 */
private fun readBootSelinuxProperty(): String? = runCatching {
    val clazz = Class.forName("android.os.SystemProperties")
    val get = clazz.getMethod("get", String::class.java, String::class.java)
    val raw = (get.invoke(null, "ro.boot.selinux", "") as? String)?.trim().orEmpty()
    Log.i(TAG, "ro.boot.selinux = '$raw'")
    when (raw.lowercase()) {
        "enforcing" -> "Enforcing"
        "permissive" -> "Permissive"
        "disabled" -> "Disabled"
        else -> null
    }
}.onFailure { Log.w(TAG, "could not read ro.boot.selinux", it) }.getOrNull()

/**
 * Reads `/sys/fs/selinux/enforce`, which holds "1" for enforcing and "0" for permissive.
 *
 * Returns null when the node is missing or unreadable, so the caller can fall back rather than
 * reporting "Disabled" for a file it could not open - those are different answers.
 */
private fun readSelinuxEnforceNode(): String? = runCatching {
    val node = File(ENFORCE_NODE)
    if (!node.exists() || !node.canRead()) return@runCatching null
    toStatus(node.readText())
}.onFailure { Log.w(TAG, "could not read the SELinux enforce node", it) }.getOrNull()

/**
 * The same node through the manager's root shell, for the case where the app uid is not allowed to
 * open it (SELinux hiding, an enforcing kernel). `cat` answers the same question as the file read.
 */
private fun readSelinuxEnforceNodeAsRoot(): String? = runCatching {
    val out = ShellUtils.fastCmd(getRootShell(), "cat $ENFORCE_NODE")
    toStatus(out)
}.onFailure { Log.w(TAG, "could not read the SELinux enforce node as root", it) }.getOrNull()

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
        return toStatus(stdout.joinToString("\n")) ?: "Unknown"
    }

    // A permission error means SELinux is on and refusing the read, which is what an enforcing
    // kernel does. It is an inference rather than a reading, so it is the last resort.
    return if (stderr.joinToString("\n").trim().endsWith("Permission denied")) {
        "Enforcing"
    } else {
        "Unknown"
    }
}

/** "1"/"0"/`getenforce` output to the raw status string. */
private fun toStatus(output: String): String? = when (output.trim()) {
    "1", "Enforcing" -> "Enforcing"
    "0", "Permissive" -> "Permissive"
    "Disabled" -> "Disabled"
    else -> null
}
