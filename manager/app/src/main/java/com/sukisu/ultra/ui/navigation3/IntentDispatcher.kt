package com.sukisu.ultra.ui.navigation3

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.ui.component.dialog.ConfirmResult
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.screen.flash.FlashIt
import com.sukisu.ultra.ui.util.getFileName
import com.sukisu.ultra.ui.webui.WebUIActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream

private const val SCHEME_KSU = "ksu"
private const val HOST_ACTION = "action"
private const val HOST_WEBUI = "webui"
private const val PARAM_ID = "id"
private const val PARAM_TOKEN = "token"

/**
 * Resolved intent action to execute after validation.
 */
private sealed interface PendingAction {
    /** Execute a module's action script — triggered by shortcut or deep link. */
    data class ExecuteAction(val moduleId: String) : PendingAction

    /** Open a module's WebUI — triggered by shortcut. */
    data class OpenWebUI(val moduleId: String) : PendingAction
}

private sealed interface KsuDeepLink {
    data class Action(val moduleId: String) : KsuDeepLink
    data class WebUi(val moduleId: String) : KsuDeepLink
}

private fun buildInternalWebUiUri(moduleId: String): Uri {
    return Uri.Builder()
        .scheme(SCHEME_KSU)
        .authority(HOST_WEBUI)
        .appendQueryParameter(PARAM_ID, moduleId)
        .build()
}

/**
 * Resolve an intent snapshot into a [PendingAction].
 * Returns null if the intent carries no recognized action.
 */
private fun resolveIntent(intent: Intent): PendingAction? {
    // Check deep links
    return when (val deepLink = parseValidatedDeepLink(intent.data)) {
        is KsuDeepLink.Action -> PendingAction.ExecuteAction(deepLink.moduleId)
        is KsuDeepLink.WebUi -> PendingAction.OpenWebUI(deepLink.moduleId)
        null -> null
    }
}

private fun parseValidatedDeepLink(uri: Uri?): KsuDeepLink? {
    if (uri?.scheme != SCHEME_KSU) return null

    val moduleId = uri.getQueryParameter(PARAM_ID)?.takeIf { it.isNotBlank() } ?: return null
    val token = uri.getQueryParameter(PARAM_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
    if (token != SettingsRepositoryImpl().intentToken) return null

    return when (uri.host) {
        HOST_ACTION -> KsuDeepLink.Action(moduleId)
        HOST_WEBUI -> KsuDeepLink.WebUi(moduleId)
        else -> null
    }
}

@SuppressLint("StringFormatInvalid")
@Composable
fun IntentDispatcher(intentChannel: ReceiveChannel<Intent>) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val isManager = Natives.isManager

    CollectIntentChannel(intentChannel) { intent ->
        if (!isManager) return@CollectIntentChannel
        val action = resolveIntent(intent) ?: return@CollectIntentChannel

        when (action) {
            is PendingAction.ExecuteAction -> {
                navigator.push(Route.ExecuteModuleAction(action.moduleId, fromShortcut = true))
            }

            is PendingAction.OpenWebUI -> {
                val webIntent = Intent(context, WebUIActivity::class.java)
                    .setData(buildInternalWebUiUri(action.moduleId))
                context.startActivity(webIntent)
            }
        }
    }
}

/**
 * Receive intents inside a [LaunchedEffect] tied to the channel identity.
 * Each emitted intent is processed exactly once; no activity.intent mutation needed.
 */
@Composable
private fun CollectIntentChannel(intentChannel: ReceiveChannel<Intent>, onIntent: suspend (Intent) -> Unit) {
    LaunchedEffect(intentChannel) {
        for (intent in intentChannel) {
            onIntent(intent)
        }
    }
}

@Composable
fun HandleZipFileIntent(intentChannel: ReceiveChannel<Intent>) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val confirmDialog = rememberConfirmDialog()

    val strings = Strings(
        safeMode = stringResource(R.string.safe_mode_module_disabled),
        zipTypeModule = stringResource(R.string.zip_type_module),
        zipTypeKernel = stringResource(R.string.zip_type_kernel),
        zipTypeUnknown = stringResource(R.string.zip_type_unknown),
        zipFileUnknown = stringResource(R.string.zip_file_unknown),
        mixedInstallPrompt = stringResource(R.string.mixed_install_prompt_with_name),
        kernelInstallPrompt = stringResource(R.string.kernel_install_prompt_with_name),
        moduleInstallPrompt = stringResource(R.string.module_install_prompt_with_name),
        horizonKernel = stringResource(R.string.horizon_kernel),
        module = stringResource(R.string.module),
    )

    // Every zip/apk intent is handled, not just the one that launched the activity: when the
    // manager is already running, tapping a downloaded module only calls onNewIntent, and the old
    // one-shot read of activity.intent silently dropped it - the app opened with no prompt.
    LaunchedEffect(intentChannel) {
        for (intent in intentChannel) {
            val zipUris = withContext(Dispatchers.IO) { extractZipUris(context, intent) }
            if (zipUris.isEmpty()) continue

            val zipTypes = withContext(Dispatchers.IO) {
                zipUris.map { uri -> detectZipType(context, uri) }
            }

            val (moduleUris, kernelUris, unknownUris) = categorizeUris(zipUris, zipTypes)
            val finalModuleUris = moduleUris + unknownUris

            val fileNames = buildFileNames(zipUris, zipTypes, context, strings)
            val (confirmTitle, confirmContent) = buildConfirmInfo(
                moduleUris, kernelUris, fileNames, strings
            )

            if (confirmDialog.awaitConfirm(title = confirmTitle, content = confirmContent) != ConfirmResult.Confirmed) {
                continue
            }

            handleConfirmed(finalModuleUris, kernelUris, context, navigator, strings)
        }
    }
}

private data class Strings(
    val safeMode: String,
    val zipTypeModule: String,
    val zipTypeKernel: String,
    val zipTypeUnknown: String,
    val zipFileUnknown: String,
    val mixedInstallPrompt: String,
    val kernelInstallPrompt: String,
    val moduleInstallPrompt: String,
    val horizonKernel: String,
    val module: String,
)

private fun extractZipUris(context: Context, intent: Intent): List<Uri> {
    val uris = mutableSetOf<Uri>()

    // A Downloads or browser uri carries no file name - it is "content://.../document/msf%3A42" -
    // so a suffix test alone silently dropped the very intents these filters exist for. Trust the
    // MIME type the chooser matched on, and fall back to the resolved display name.
    val mime = intent.type?.lowercase(Locale.ROOT)
    val mimeIsArchive = mime == "application/zip" ||
        mime == "application/x-zip-compressed" ||
        mime == "application/vnd.android.package-archive"

    fun isZipFile(uri: Uri?): Boolean {
        if (uri == null) return false
        if (mimeIsArchive) return true
        val uriString = uri.toString()
        if (uriString.endsWith(".zip", ignoreCase = true) ||
            uriString.endsWith(".apk", ignoreCase = true)
        ) {
            return true
        }
        val name = runCatching { uri.getFileName(context) }.getOrNull() ?: return false
        return name.endsWith(".zip", ignoreCase = true) ||
            name.endsWith(".apk", ignoreCase = true)
    }

    when (intent.action) {
        Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
            val data = intent.data
            val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (isZipFile(data)) uris.add(data!!)
            if (isZipFile(stream)) uris.add(stream!!)
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            val streamList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            streamList?.filter { isZipFile(it) }?.let { uris.addAll(it) }
        }
    }

    intent.clipData?.let { clipData ->
        for (i in 0 until clipData.itemCount) {
            clipData.getItemAt(i)?.uri?.let { if (isZipFile(it)) uris.add(it) }
        }
    }

    return uris.toList()
}

private fun categorizeUris(uris: List<Uri>, types: List<ZipType>): Triple<List<Uri>, List<Uri>, List<Uri>> {
    val moduleUris = mutableListOf<Uri>()
    val kernelUris = mutableListOf<Uri>()
    val unknownUris = mutableListOf<Uri>()

    uris.forEachIndexed { index, uri ->
        when (types[index]) {
            ZipType.MODULE -> moduleUris.add(uri)
            ZipType.KERNEL -> kernelUris.add(uri)
            ZipType.UNKNOWN -> unknownUris.add(uri)
        }
    }
    return Triple(moduleUris, kernelUris, unknownUris)
}

private fun buildFileNames(
    uris: List<Uri>,
    types: List<ZipType>,
    context: Context,
    strings: Strings
): String {
    return uris.mapIndexed { index, uri ->
        val fileName = uri.getFileName(context) ?: strings.zipFileUnknown
        val typeStr = when (types[index]) {
            ZipType.MODULE -> strings.zipTypeModule
            ZipType.KERNEL -> strings.zipTypeKernel
            // The unknown type has its own label: reusing the file-name fallback printed
            // "1. foo.zipunknown.zip" in the confirmation.
            ZipType.UNKNOWN -> strings.zipTypeUnknown
        }
        "\n${index + 1}. $fileName$typeStr"
    }.joinToString("")
}

private fun buildConfirmInfo(
    moduleUris: List<Uri>,
    kernelUris: List<Uri>,
    fileNames: String,
    strings: Strings
): Pair<String, String> {
    val title = if (kernelUris.isNotEmpty() && moduleUris.isEmpty()) {
        strings.horizonKernel
    } else {
        strings.module
    }

    val content = when {
        moduleUris.isNotEmpty() && kernelUris.isNotEmpty() -> strings.mixedInstallPrompt.format(fileNames)
        kernelUris.isNotEmpty() -> strings.kernelInstallPrompt.format(fileNames)
        else -> strings.moduleInstallPrompt.format(fileNames)
    }

    return title to content
}

private suspend fun handleConfirmed(
    moduleUris: List<Uri>,
    kernelUris: List<Uri>,
    context: Context,
    navigator: Navigator,
    strings: Strings
) {
    if (moduleUris.isNotEmpty()) {
        if (Natives.isSafeMode) {
            Toast.makeText(context, strings.safeMode, Toast.LENGTH_SHORT).show()
        } else {
            val cachedUris = withContext(Dispatchers.IO) {
                moduleUris.mapNotNull { copyUriToCache(context, it) }
            }

            if (cachedUris.isNotEmpty()) {
                navigator.pushFlash(Route.Flash(FlashIt.FlashModules(cachedUris)))
            } else {
                // "unknown file" was the wrong message for this: the archive was recognised, it just
                // could not be copied out of the uri the chooser handed over.
                Toast.makeText(context, R.string.zip_file_unreadable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (kernelUris.isNotEmpty()) {
        // Straight to the flash, not through the install screen. Tapping a downloaded kernel in the
        // browser is a request to flash that file; the install screen is the place to *choose* what
        // to flash, and sending the intent there meant the user confirmed twice and had to find the
        // continue button on a page about a choice they had already made. The slot is left to the
        // AnyKernel3 script, which defaults to the running slot - the archive path on that screen
        // does the same when no slot was picked.
        navigator.pushFlash(
            Route.KernelFlash(
                kernelUri = kernelUris.first(),
                selectedSlot = null,
                kpmPatchEnabled = false,
                kpmUndoPatch = false,
            ),
        )
    }
}

private fun copyUriToCache(context: Context, uri: Uri): Uri? {
    return try {
        val fileName = uri.getFileName(context) ?: "module_${System.currentTimeMillis()}.zip"
        val destFile = File(context.cacheDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }

        Uri.fromFile(destFile)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

enum class ZipType {
    MODULE,
    KERNEL,
    UNKNOWN
}

private fun detectZipType(context: Context, uri: Uri): ZipType {
    val isApk = uri.toString().lowercase().endsWith(".apk", ignoreCase = true)

    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipStream ->
                detectFromZipStream(zipStream, isApk)
            }
        } ?: defaultType(isApk)
    } catch (e: Exception) {
        e.printStackTrace()
        defaultType(isApk)
    }
}

private fun detectFromZipStream(zipStream: ZipInputStream, isApk: Boolean): ZipType {
    var hasModuleProp = false
    var hasToolsFolder = false
    var hasAnykernelSh = false

    var entry = zipStream.nextEntry
    while (entry != null) {
        val entryName = entry.name.lowercase()
        when {
            entryName == "module.prop" || entryName.endsWith("/module.prop") -> hasModuleProp = true
            entryName.startsWith("tools/") || entryName == "tools" -> hasToolsFolder = true
            entryName == "anykernel.sh" || entryName.endsWith("/anykernel.sh") -> hasAnykernelSh = true
        }
        zipStream.closeEntry()
        entry = zipStream.nextEntry
    }

    return when {
        // An AnyKernel3 script wins over a `module.prop`. Plenty of kernel packages ship both - the
        // prop is there for the kernel's own KernelSU integration - and checking the prop first
        // classified those as modules: tapping a downloaded kernel in the browser installed it as a
        // module and landed on the module flash screen, which is the one with the reboot button,
        // instead of flashing the boot image. `tools/` is not required: the script is the marker,
        // and an AnyKernel3 archive without tools only means it expects the phone's own binaries.
        hasAnykernelSh -> ZipType.KERNEL
        hasModuleProp -> ZipType.MODULE
        isApk -> ZipType.MODULE
        else -> ZipType.UNKNOWN
    }
}

private fun defaultType(isApk: Boolean) = if (isApk) ZipType.MODULE else ZipType.UNKNOWN
