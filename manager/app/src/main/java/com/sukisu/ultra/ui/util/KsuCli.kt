package com.sukisu.ultra.ui.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import android.os.SystemClock
import android.provider.OpenableColumns
import android.system.Os
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import com.sukisu.ultra.BuildConfig
import com.sukisu.ultra.Natives
import com.sukisu.ultra.core.tasks.BootKernelVersion
import com.sukisu.ultra.core.tasks.ExtractImage
import com.sukisu.ultra.core.tasks.ProbeResult
import com.sukisu.ultra.core.utils.DataSourceChannel
import com.sukisu.ultra.ksuApp
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * @author weishu
 * @date 2023/1/1.
 */
private const val TAG = "KsuCli"

fun getKsuDaemonPath(): String {
    return ksuApp.applicationInfo.nativeLibraryDir + File.separator + "libksud.so"
}

data class FlashResult(val code: Int, val err: String, val showReboot: Boolean) {
    constructor(result: Shell.Result, showReboot: Boolean) : this(result.code, result.err.joinToString("\n"), showReboot)
    constructor(result: Shell.Result) : this(result, result.isSuccess)
}

/**
 * The process-wide root shell.
 *
 * Both were `val`s built when the object was first touched, which on a cold start is the very
 * first read of the home page - often before the daemon has authorised this manager. When that
 * request lost the race, `createRootShell` fell back to a plain `sh` and the *result was cached
 * for the life of the process*: every later `ksud`, `getenforce` and module read ran unrooted, so
 * the home facts stayed blank until something restarted the process (switching the language did,
 * which is why that appeared to fix it). A shell that is not root is now never cached, and the
 * next call asks again.
 */
object KsuCli {
    @Volatile
    private var shell: Shell? = null

    @Volatile
    private var globalMntShell: Shell? = null

    /** The un-rooted attempt and when it happened, so a rootless device is not asked every call. */
    @Volatile
    private var lastFailed: Shell? = null

    @Volatile
    private var lastFailedAt = 0L

    fun shell(globalMnt: Boolean): Shell {
        val cached = if (globalMnt) globalMntShell else shell
        if (cached != null && worksAsRoot(cached)) return cached

        val now = SystemClock.elapsedRealtime()
        val failed = lastFailed
        if (failed != null && now - lastFailedAt < FailureRetryMillis) return failed

        val fresh = createRootShell(globalMnt)
        if (worksAsRoot(fresh)) {
            if (globalMnt) globalMntShell = fresh else shell = fresh
            lastFailed = null
        } else {
            // Kept only as a short-lived fallback: the daemon may simply not be up yet, and the
            // next call a moment later can succeed.
            lastFailed = fresh
            lastFailedAt = now
        }
        return fresh
    }

    /**
     * A shell that *can* execute a command as uid 0.
     *
     * `Shell.isRoot` is what the builder was asked for, not what it got: a `su` request that loses
     * the race with the daemon still reports the flag, and caching such a shell made every later
     * `cat`, `getenforce` and `ksud` run unprivileged for the rest of the process - the SELinux
     * row then stayed "unknown" until the activity was recreated and something else happened to
     * build a working one. Asking the shell itself settles it.
     */
    private fun worksAsRoot(candidate: Shell): Boolean = runCatching {
        candidate.isRoot && ShellUtils.fastCmd(candidate, "id -u").trim() == "0"
    }.getOrDefault(false)
}

fun getRootShell(globalMnt: Boolean = false): Shell = KsuCli.shell(globalMnt)

/** How long an un-rooted shell is reused before the daemon is asked again. */
private const val FailureRetryMillis = 1_000L

inline fun <T> withNewRootShell(
    globalMnt: Boolean = false,
    block: Shell.() -> T
): T {
    return createRootShell(globalMnt).use(block)
}

fun Uri.getFileName(context: Context): String? {
    val contentResolver: ContentResolver = context.contentResolver
    return runCatching {
        contentResolver.query(this, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}

fun createRootShell(globalMnt: Boolean = false): Shell {
    Shell.enableVerboseLogging = BuildConfig.DEBUG
    val builder = Shell.Builder.create()
    return try {
        if (globalMnt) {
            builder.build(getKsuDaemonPath(), "debug", "su", "-g")
        } else {
            builder.build(getKsuDaemonPath(), "debug", "su")
        }
    } catch (e: Throwable) {
        Log.w(TAG, "ksu failed: ", e)
        try {
            if (globalMnt) {
                builder.build("su", "-mm")
            } else {
                builder.build("su")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "su failed: ", e)
            builder.build("sh")
        }
    }
}

fun execKsud(args: String, newShell: Boolean = false, globalMnt: Boolean = false): Boolean {
    return if (newShell) {
        withNewRootShell(globalMnt = globalMnt) {
            ShellUtils.fastCmdResult(this, "${getKsuDaemonPath()} $args")
        }
    } else {
        ShellUtils.fastCmdResult(getRootShell(globalMnt), "${getKsuDaemonPath()} $args")
    }
}

suspend fun getFeatureStatus(feature: String): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val out = shell.newJob()
        .add("${getKsuDaemonPath()} feature check ${feature.shellArg()}").to(ArrayList<String>(), null).exec().out
    out.firstOrNull()?.trim().orEmpty()
}

suspend fun getFeaturePersistValue(feature: String): Long? = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val out = shell.newJob()
        .add("${getKsuDaemonPath()} feature get --config ${feature.shellArg()}").to(ArrayList<String>(), null).exec().out
    val valueLine = out.firstOrNull { it.trim().startsWith("Value:") } ?: return@withContext null
    valueLine.substringAfter("Value:").trim().toLongOrNull()
}

fun install() {
    val start = SystemClock.elapsedRealtime()
    val libadbroot = File(ksuApp.applicationInfo.nativeLibraryDir, "libadbroot.so").absolutePath
    val result = execKsud(
        "install --libadbroot ${libadbroot.shellArg()} --data-path ${ksuApp.applicationInfo.deviceProtectedDataDir.shellArg()}",
        true,
    )
    Log.w(TAG, "install result: $result, cost: ${SystemClock.elapsedRealtime() - start}ms")
}

fun listModules(): String {
    val shell = getRootShell()

    val out = shell.newJob()
        .add("${getKsuDaemonPath()} module list").to(ArrayList(), null).exec().out
    return out.joinToString("\n").ifBlank { "[]" }
}

fun getModuleCount(): Int {
    val result = listModules()
    runCatching {
        val array = JSONArray(result)
        return array.length()
    }.getOrElse { return 0 }
}

fun getSuperuserCount(): Int {
    return Natives.getSuperuserCount()
}

fun toggleModule(id: String, enable: Boolean): Boolean {
    val cmd = if (enable) {
        "module enable ${id.shellArg()}"
    } else {
        "module disable ${id.shellArg()}"
    }
    val result = execKsud(cmd, true)
    Log.i(TAG, "$cmd result: $result")
    return result
}

fun undoUninstallModule(id: String): Boolean {
    val cmd = "module undo-uninstall ${id.shellArg()}"
    val result = execKsud(cmd, true)
    Log.i(TAG, "undo uninstall module $id result: $result")
    return result
}

fun uninstallModule(id: String): Boolean {
    val cmd = "module uninstall ${id.shellArg()}"
    val result = execKsud(cmd, true)
    Log.i(TAG, "uninstall module $id result: $result")
    return result
}

private fun flashWithIO(
    cmd: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): Shell.Result {

    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }

    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    return withNewRootShell {
        newJob().add(cmd).to(stdoutCallback, stderrCallback).exec()
    }
}

fun flashModule(
    uri: Uri,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit
): FlashResult {
    var file: File? = null
    return try {
        val input = ksuApp.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open module archive")
        val archive = input.use {
            File.createTempFile("module-", ".zip", ksuApp.cacheDir).also { target ->
                file = target
                target.outputStream().use { output -> it.copyTo(output) }
            }
        }
        val cmd = "module install ${archive.absolutePath.shellArg()}"
        val result = flashWithIO("${getKsuDaemonPath()} $cmd", onStdout, onStderr)
        Log.i("KernelSU", "install module $uri result: $result")
        FlashResult(result)
    } catch (e: Exception) {
        FlashResult(-1, e.message ?: "Module installation failed", false)
    } finally {
        file?.delete()
    }
}

fun runModuleAction(
    moduleId: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit
): Boolean {
    val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStdout(s ?: "")
        }
    }

    val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
        override fun onAddElement(s: String?) {
            onStderr(s ?: "")
        }
    }

    val result = withNewRootShell(true) {
        newJob().add("${getKsuDaemonPath()} module action ${moduleId.shellArg()}")
            .to(stdoutCallback, stderrCallback).exec()
    }

    Log.i("KernelSU", "Module runAction result: $result")

    return result.isSuccess
}

fun restoreBoot(
    onStdout: (String) -> Unit, onStderr: (String) -> Unit
): FlashResult {
    val result = flashWithIO("${getKsuDaemonPath()} boot-restore -f", onStdout, onStderr)
    return FlashResult(result)
}

fun uninstallPermanently(
    onStdout: (String) -> Unit, onStderr: (String) -> Unit
): FlashResult {
    val result = flashWithIO("${getKsuDaemonPath()} uninstall --package-name ${BuildConfig.APPLICATION_ID.shellArg()}", onStdout, onStderr)
    return FlashResult(result)
}

@Parcelize
sealed class LkmSelection : Parcelable {
    @Parcelize
    data class LkmUri(val uri: Uri) : LkmSelection()

    @Parcelize
    data class KmiString(val value: String) : LkmSelection()

    @Parcelize
    data object KmiNone : LkmSelection()
}

private fun writeLkmFile(lkm: LkmSelection): File? {
    if (lkm !is LkmSelection.LkmUri) return null
    val input = ksuApp.contentResolver.openInputStream(lkm.uri)
        ?: throw IOException("Unable to open kernel module")
    return input.use {
        val file = File.createTempFile("kernelsu-lkm-", ".ko", ksuApp.cacheDir)
        try {
            file.outputStream().use { output -> it.copyTo(output) }
            file
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }
}

private fun bootPatchFlags(
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
): String = buildString {
    if (allowShell) append(" --allow-shell")
    if (enableAdb) append(" --enable-adbd")
    if (forceBackup) append(" --backup")
}

fun installBoot(
    bootUri: Uri?,
    lkm: LkmSelection,
    ota: Boolean,
    partition: String?,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    spoofRelease: String,
    spoofVersion: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    var bootFile: File? = null
    var lkmFile: File? = null
    try {
        bootFile = bootUri?.let { uri ->
            val input = ksuApp.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open boot image")
            input.use {
                File.createTempFile("boot-", ".img", ksuApp.cacheDir).also { target ->
                    bootFile = target
                    target.outputStream().use { output -> it.copyTo(output) }
                }
            }
        }

        var cmd = "boot-patch"
        cmd += bootFile?.let { " -b ${it.absolutePath.shellArg()}" } ?: " -f"
        cmd += bootPatchFlags(allowShell, enableAdb, forceBackup)

        if (spoofRelease.isNotBlank()) {
            cmd += " --spoof-release ${spoofRelease.shellArg()}"
        }
        if (spoofVersion.isNotBlank()) {
            cmd += " --spoof-version ${spoofVersion.shellArg()}"
        }
        if (ota) {
            cmd += " -u"
        }

        lkmFile = writeLkmFile(lkm)
        if (lkmFile != null) {
            cmd += " -m ${lkmFile.absolutePath.shellArg()}"
        } else if (lkm is LkmSelection.KmiString) {
            cmd += " --kmi ${lkm.value.shellArg()}"
        }

        if (bootFile != null) {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            cmd += " -o ${downloadsDir.absolutePath.shellArg()}"
        }
        partition?.let { part -> cmd += " --partition ${part.shellArg()}" }

        val result = flashWithIO("${getKsuDaemonPath()} $cmd", onStdout, onStderr)
        Log.i("KernelSU", "install boot result: ${result.isSuccess}")
        val showReboot = bootUri == null && result.isSuccess
        if (showReboot) install()
        return FlashResult(result, showReboot)
    } catch (e: Exception) {
        return FlashResult(-1, e.message ?: "Boot installation failed", false)
    } finally {
        bootFile?.delete()
        lkmFile?.delete()
    }
}

fun downloadBoot(
    url: String,
    partition: String,
    lkm: LkmSelection,
    allowShell: Boolean,
    enableAdb: Boolean,
    forceBackup: Boolean,
    spoofRelease: String,
    spoofVersion: String,
    onStdout: (String) -> Unit,
    onStderr: (String) -> Unit,
): FlashResult {
    // A fixed name made two downloads overwrite each other's image.
    val bootFile = try {
        File.createTempFile("download-boot-", ".img", ksuApp.cacheDir)
    } catch (e: Exception) {
        return FlashResult(-1, e.message ?: "Unable to create a temporary boot image", false)
    }
    var lkmFile: File? = null
    try {
        onStdout("- Downloading and extracting boot image")
        val channel = DataSourceChannel(newDownloadClient(), url)
        val magic: String
        val probedKmi: String?
        try {
            magic = readMagic(channel)
            val image = ExtractImage(bootFile, onStdout)
            // Extract the KMI here while the payload is open. ZipFile closes the
            // channel it is built on, so probe on a separate channel.
            val probeChannel = DataSourceChannel(newDownloadClient(), url)
            probedKmi = try {
                if (magic == "CrAU") {
                    ExtractImage.probePayload(
                        probeChannel,
                        withKmi = lkm is LkmSelection.KmiNone,
                        onProgress = onStdout,
                    ).kmi
                } else {
                    ExtractImage.probe(
                        probeChannel,
                        withKmi = lkm is LkmSelection.KmiNone,
                        onProgress = onStdout,
                    ).kmi
                }
            } finally {
                probeChannel.close()
            }
            if (magic == "CrAU") {
                image.consumePayload(channel, partition)
            } else {
                image.consume(channel, partition)
            }
        } finally {
            channel.close()
        }

        // init_boot/vendor_boot carry no kernel, so their KMI comes from the
        // payload's boot probe and must be passed explicitly. A remote download
        // is unrelated to this device, so ksud must not use the local kernel.
        val autoKmi = if (lkm is LkmSelection.KmiNone) {
            (probedKmi ?: BootKernelVersion.parseKmiFromBoot(bootFile))?.also {
                onStdout("- Auto detected KMI: $it")
            }
        } else {
            null
        }
        if (autoKmi == null && lkm is LkmSelection.KmiNone) {
            throw IOException("Failed to determine KMI from the package")
        }

        var cmd = "${getKsuDaemonPath()} boot-patch -b ${bootFile.absolutePath.shellArg()}"
        cmd += bootPatchFlags(allowShell, enableAdb, forceBackup)

        if (spoofRelease.isNotBlank()) {
            cmd += " --spoof-release ${spoofRelease.shellArg()}"
        }

        if (spoofVersion.isNotBlank()) {
            cmd += " --spoof-version ${spoofVersion.shellArg()}"
        }

        lkmFile = writeLkmFile(lkm)
        if (lkmFile != null) {
            cmd += " -m ${lkmFile.absolutePath.shellArg()}"
        } else if (lkm is LkmSelection.KmiString) {
            cmd += " --kmi ${lkm.value.shellArg()}"
        }
        if (autoKmi != null) cmd += " --kmi ${autoKmi.shellArg()}"
        cmd += " --partition ${partition.shellArg()}"
        // ksud defaults to cwd, which is read-only in the su session; use Downloads.
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        cmd += " -o ${downloadsDir.absolutePath.shellArg()}"

        val stdoutCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                onStdout(s ?: "")
            }
        }
        val stderrCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                onStderr(s ?: "")
            }
        }

        val result = withNewRootShell {
            newJob().add(cmd).to(stdoutCallback, stderrCallback).exec()
        }
        return FlashResult(result, false)
    } catch (e: Exception) {
        return FlashResult(-1, e.message ?: "Download failed", false)
    } finally {
        lkmFile?.delete()
        bootFile.delete()
    }
}

suspend fun probeRemoteBootPartitions(url: String): ProbeResult = withContext(Dispatchers.IO) {
    Log.d(TAG, "probe start: $url")
    val channel = DataSourceChannel(newDownloadClient(), url)
    try {
        Log.d(TAG, "probe connected, size=${channel.size()}")
        val magic = readMagic(channel)
        Log.d(TAG, "probe magic: $magic")
        // Only list the partitions here; the KMI is extracted later when the
        // payload is downloaded for patching.
        val result = if (magic == "CrAU") {
            ExtractImage.probePayload(channel, withKmi = false)
        } else {
            ExtractImage.probe(channel, withKmi = false)
        }
        Log.d(TAG, "probe partitions: ${result.partitions}")
        result
    } finally {
        channel.close()
    }
}

private fun newDownloadClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
}

private fun readMagic(channel: DataSourceChannel): String {
    val buffer = ByteBuffer.allocate(4)
    var read = 0
    while (read < 4) {
        val count = channel.read(ByteBuffer.wrap(buffer.array(), read, 4 - read), read.toLong())
        if (count <= 0) break
        read += count
    }
    channel.position(0)
    return String(buffer.array(), 0, read, StandardCharsets.ISO_8859_1)
}

internal fun String.shellArg(): String {
    require('\u0000' !in this) { "Shell arguments cannot contain null bytes" }
    return "'${replace("'", "'\\''")}'"
}

fun reboot(reason: String = "") {
    if (reason == "soft_reboot") {
        execKsud("soft-reboot", true, true)
        return
    }
    val shell = getRootShell()
    if (reason == "recovery") {
        // KEYCODE_POWER = 26, hide incorrect "Factory data reset" message
        ShellUtils.fastCmd(shell, "/system/bin/input keyevent 26")
    }
    // The target comes from a fixed menu, but it still reaches a shell: quote it so a future
    // caller cannot turn it into a second command.
    val target = if (reason.isBlank()) "" else " ${reason.shellArg()}"
    ShellUtils.fastCmd(shell, "/system/bin/svc power reboot$target || /system/bin/reboot$target")
}

fun rootAvailable(): Boolean {
    val shell = getRootShell()
    return shell.isRoot
}

suspend fun getCurrentKmi(): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info current-kmi"
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd")
}

suspend fun getSupportedKmis(): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info supported-kmis"
    val out = shell.newJob().add("${getKsuDaemonPath()} $cmd").to(ArrayList(), null).exec().out
    out.filter { it.isNotBlank() }.map { it.trim() }
}

suspend fun isAbDevice(): Boolean = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info is-ab-device"
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim().toBoolean()
}

suspend fun getDefaultPartition(): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    if (shell.isRoot) {
        val cmd = "boot-info default-partition"
        ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim()
    } else {
        // init_boot exists from Android 13 on; Android 12 and older (including non-GKI kernels)
        // keep the ramdisk in boot.
        if (ANDROID_13_OR_NEWER.containsMatchIn(Os.uname().release)) "init_boot" else "boot"
    }
}

private val ANDROID_13_OR_NEWER = Regex("android1[3-9]-")

suspend fun getSlotSuffix(ota: Boolean): String = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = if (ota) {
        "boot-info slot-suffix --ota"
    } else {
        "boot-info slot-suffix"
    }
    ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} $cmd").trim()
}

suspend fun getAvailablePartitions(): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "boot-info available-partitions"
    val out = shell.newJob().add("${getKsuDaemonPath()} $cmd").to(ArrayList(), null).exec().out
    out.filter { it.isNotBlank() }.map { it.trim() }
}

fun hasMagisk(): Boolean {
    val shell = getRootShell(true)
    val result = shell.newJob().add("which magisk").exec()
    Log.i(TAG, "has magisk: ${result.isSuccess}")
    return result.isSuccess
}

fun isSepolicyValid(rules: String?): Boolean {
    if (rules == null) {
        return true
    }
    val shell = getRootShell()
    val result =
        shell.newJob().add("${getKsuDaemonPath()} sepolicy check ${rules.shellArg()}").to(ArrayList(), null)
            .exec()
    return result.isSuccess
}

fun getSepolicy(pkg: String): String {
    val shell = getRootShell()
    val result =
        shell.newJob().add("${getKsuDaemonPath()} profile get-sepolicy ${pkg.shellArg()}").to(ArrayList(), null)
            .exec()
    Log.i(TAG, "code: ${result.code}, out: ${result.out}, err: ${result.err}")
    return result.out.joinToString("\n")
}

fun setSepolicy(pkg: String, rules: String): Boolean {
    val shell = getRootShell()
    val result = shell.newJob().add("${getKsuDaemonPath()} profile set-sepolicy ${pkg.shellArg()} ${rules.shellArg()}")
        .to(ArrayList(), null).exec()
    Log.i(TAG, "set sepolicy result: ${result.code}")
    return result.isSuccess
}

fun listAppProfileTemplates(): List<String> {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile list-templates").to(ArrayList(), null)
        .exec().out
}

fun getAppProfileTemplate(id: String): String {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile get-template ${id.shellArg()}")
        .to(ArrayList(), null).exec().out.joinToString("\n")
}

fun setAppProfileTemplate(id: String, template: String): Boolean {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} profile set-template ${id.shellArg()} ${template.shellArg()}"
    return shell.newJob().add(cmd)
        .to(ArrayList(), null).exec().isSuccess
}

fun deleteAppProfileTemplate(id: String): Boolean {
    val shell = getRootShell()
    return shell.newJob().add("${getKsuDaemonPath()} profile delete-template ${id.shellArg()}")
        .to(ArrayList(), null).exec().isSuccess
}

fun forceStopApp(packageName: String, userId: Int? = null) {
    val shell = getRootShell()
    val userArg = userId?.let { " --user $it" } ?: ""
    val result = shell.newJob().add("am force-stop$userArg ${packageName.shellArg()}").exec()
    Log.i(TAG, "force stop $packageName result: $result")
}

fun launchApp(packageName: String, userId: Int? = null) {
    val shell = getRootShell()
    val userArg = userId?.let { " --user $it" } ?: ""
    val result =
        shell.newJob()
            .add("cmd package resolve-activity --brief$userArg ${packageName.shellArg()} | tail -n 1 | xargs cmd activity start-activity$userArg -n")
            .exec()
    Log.i(TAG, "launch $packageName result: $result")
}

fun restartApp(packageName: String, userId: Int? = null) {
    forceStopApp(packageName, userId)
    launchApp(packageName, userId)
}

fun runCmd(shell: Shell, cmd: String): String {
    return shell.newJob()
        .add(cmd)
        .to(mutableListOf<String>(), null)
        .exec().out
        .joinToString("\n")
}

suspend fun streamFile(path: String): List<String> = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val outLines = mutableListOf<String>()

    // A default CallbackList posts to the main thread, so the list could still be empty here.
    shell.newJob().add("cat ${path.shellArg()} || true").to(outLines, null).exec()
    outLines
}

fun getSuSFSStatus(): String {
    val shell = getRootShell()
    return ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} susfs status").trim()
}

fun getSuSFSVersion(): String {
    val shell = getRootShell()
    val result = ShellUtils.fastCmd(shell, "${getKsuDaemonPath()} susfs version")
    return result
}

fun getSuSFSFeatures(): String {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} susfs features"
    return runCmd(shell, cmd)
}

fun spoofKernelUname(release: String, version: String): Boolean {
    fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} kernel spoof-uname --release ${shellQuote(release)} --version ${shellQuote(version)}"
    val result = ShellUtils.fastCmdResult(shell, cmd)
    Log.i(TAG, "kernel spoof-uname result: $result")
    return result
}

fun addUmountPath(path: String, flags: Int): Boolean {
    val shell = getRootShell()
    val flagsArg = if (flags >= 0) "--flags $flags" else ""
    val cmd = "${getKsuDaemonPath()} umount add ${path.shellArg()} $flagsArg"
    val result = ShellUtils.fastCmdResult(shell, cmd)
    Log.i(TAG, "add umount path $path result: $result")
    return result
}

fun removeUmountPath(path: String): Boolean {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} umount remove ${path.shellArg()}"
    val result = ShellUtils.fastCmdResult(shell, cmd)
    Log.i(TAG, "remove umount path $path result: $result")
    return result
}

fun listUmountPaths(): String {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} umount list"
    return try {
        runCmd(shell, cmd).trim()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to list umount paths", e)
        ""
    }
}

fun clearCustomUmountPaths(): Boolean {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} umount clear-custom"
    val result = ShellUtils.fastCmdResult(shell, cmd)
    Log.i(TAG, "clear custom umount paths result: $result")
    return result
}

fun saveUmountConfig(): Boolean {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} umount save"
    val result = ShellUtils.fastCmdResult(shell, cmd)
    Log.i(TAG, "save umount config result: $result")
    return result
}

fun applyUmountConfigToKernel(): Boolean {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} umount apply"
    val result = ShellUtils.fastCmdResult(shell, cmd)
    Log.i(TAG, "apply umount config to kernel result: $result")
    return result
}

/**
 * 内核是否支持 SuSFS（`ksud susfs status` 打印 true/false）。
 *
 * 在 IO 线程执行并用 rememberSaveable 缓存：早前这里是在
 * 组合期同步调 shell，每次重组都会阻塞主线程一次 root 命令。
 */
@Composable
fun rememberSuSFSStatus(): Boolean {
    var cached by rememberSaveable { mutableStateOf(false) }
    val supported by produceState(initialValue = cached) {
        val ok = withContext(Dispatchers.IO) {
            var found: Boolean? = null
            // ksud can be unreachable on the very first frame (root still coming up); an empty
            // answer must not leave the entry hidden for the rest of the session, so retry a
            // few times instead of settling on "unsupported".
            for (attempt in 0 until 4) {
                val out = runCatching { getSuSFSStatus().trim() }.getOrElse { "" }
                if (out.isNotEmpty()) {
                    found = out.equals("true", ignoreCase = true)
                    break
                }
                if (attempt < 3) Thread.sleep(300L)
            }
            found ?: false
        }
        cached = ok
        value = ok
    }
    return supported
}

data class BootConfig(
    val allowShell: Boolean = false,
    val spoofRelease: String = "",
    val spoofVersion: String = "",
)

// 读取镜像中的 ksu_config 参数
suspend fun getBootConfig(): BootConfig = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val cmd = "${getKsuDaemonPath()} boot-info read-config"
    val out = shell.newJob().add(cmd).to(ArrayList(), null).exec().out

    var allowShell = false
    var spoofRelease = ""
    var spoofVersion = ""

    for (line in out) {
        when {
            line.startsWith("allow_shell=") -> allowShell = line.substringAfter("=").trim() == "1"
            line.startsWith("spoof_release=") -> {
                spoofRelease = parseQuotedValue(line.substringAfter("spoof_release="))
            }
            line.startsWith("spoof_version=") -> {
                spoofVersion = parseQuotedValue(line.substringAfter("spoof_version="))
            }
        }
    }

    BootConfig(allowShell, spoofRelease, spoofVersion)
}

private fun parseQuotedValue(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length < 2) return trimmed
    return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
        trimmed.substring(1, trimmed.length - 1)
    } else if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
        trimmed.substring(1, trimmed.length - 1)
    } else {
        trimmed
    }
}

/** Signature the kernel currently accepts as the dynamic (multi-manager) one. */
data class DynamicManagerConfig(val size: Int, val hash: String)

/**
 * Read the dynamic manager signature from the kernel.
 *
 * The kernel keeps it in memory only; `ksud kernel dynamic-manager get` prints it as JSON and
 * answers with a non-zero exit code when nothing is set.
 */
suspend fun getDynamicManagerConfig(): DynamicManagerConfig? = withContext(Dispatchers.IO) {
    val shell = getRootShell()
    val result = shell.newJob()
        .add("${getKsuDaemonPath()} kernel dynamic-manager get --internal true")
        .to(mutableListOf<String>(), null)
        .exec()
    if (!result.isSuccess) return@withContext null
    runCatching {
        val obj = org.json.JSONObject(result.out.joinToString("\n"))
        val size = obj.optInt("size", 0)
        val hash = obj.optString("hash", "")
        if (size > 0 && hash.length == 64) DynamicManagerConfig(size, hash) else null
    }.getOrNull()
}

/** Accept the signature of the manager installed at [apkPath]; `ksud` reads it out of the APK. */
fun setDynamicManagerApk(apkPath: String): Boolean {
    val quoted = "'${apkPath.replace("'", "'\\''")}'"
    return execKsud("kernel dynamic-manager set-apk $quoted", true)
}

/** Forget the dynamic signature. */
fun clearDynamicManager(): Boolean = execKsud("kernel dynamic-manager clear", true)

