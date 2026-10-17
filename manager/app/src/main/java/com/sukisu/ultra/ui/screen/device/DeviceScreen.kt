package com.sukisu.ultra.ui.screen.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkFactsGroup
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkSettingsSection
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.util.getSuperuserCount
import com.sukisu.ultra.ui.util.rememberSuSFSStatus
import com.sukisu.ultra.ui.viewmodel.HomeViewModel
import com.sukisu.ultra.ui.viewmodel.ModuleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything about this device in one place: the phone itself, the kernel underneath it, the
 * manager running on top, and what each of them is currently doing.
 *
 * The rows are FolkFactsGroup facts, the same label/value shape the home page uses, so a value
 * that is long (a kernel string, a fingerprint-sized version) wraps under its label instead of
 * squeezing it. The groups and the section titles are the settings page's, so the page reads as
 * part of the app rather than as a table bolted onto it.
 */
@Composable
fun DeviceScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = viewModel<HomeViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val moduleViewModel = viewModel<ModuleViewModel>()
    val moduleState by moduleViewModel.uiState.collectAsStateWithLifecycle()
    val susfs = rememberSuSFSStatus()

    var superuserCount by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.refresh()
        superuserCount = withContext(Dispatchers.IO) {
            runCatching { getSuperuserCount() }.getOrDefault(0)
        }
    }

    val props = remember { DeviceProperties.read() }
    val yes = context.getString(R.string.device_value_yes)
    val no = context.getString(R.string.device_value_no)

    val deviceRows = buildList {
        if (props.deviceName.isNotBlank()) {
            add(context.getString(R.string.device_row_device_name) to props.deviceName)
        }
        add(context.getString(R.string.device_row_market_name) to props.marketName)
        add(
            context.getString(R.string.device_row_manufacturer) to
                (Build.MANUFACTURER + " " + Build.BRAND),
        )
        add(
            context.getString(R.string.device_row_android_version) to
                (Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"),
        )
        add(context.getString(R.string.device_row_system_version) to props.systemVersion)
        add(
            context.getString(R.string.device_row_abi) to
                Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        )
    }

    val kernelRows = buildList {
        add(
            context.getString(R.string.device_row_kernel_version) to
                (state.kernelVersion.major.toString() + "." +
                    state.kernelVersion.patchLevel + "." + state.kernelVersion.subLevel),
        )
        state.systemInfo.kernelFullVersion?.takeIf { it.isNotBlank() }?.let {
            add(context.getString(R.string.device_row_kernel_full) to it)
        }
        add(
            context.getString(R.string.device_row_kernel_uapi) to
                (state.kernelUAPIVersion?.toString() ?: no),
        )
        add(
            context.getString(R.string.device_row_kmi) to
                (if (state.kernelVersion.isGKI()) yes else no),
        )
        add(context.getString(R.string.device_row_selinux) to state.systemInfo.selinuxStatus)
        add(
            context.getString(R.string.device_row_seccomp) to
                state.systemInfo.seccompStatus.toString(),
        )
        add(context.getString(R.string.device_row_susfs) to (if (susfs) yes else no))
        add(
            context.getString(R.string.device_row_lkm) to
                when (state.lkmMode) {
                    true -> yes
                    false -> no
                    null -> no
                },
        )
    }

    val managerRows = buildList {
        add(context.getString(R.string.device_row_manager_version) to state.systemInfo.managerVersion)
        add(context.getString(R.string.device_row_package) to context.packageName)
        add(
            context.getString(R.string.device_row_manager_uapi) to
                state.managerUAPIVersion.toString(),
        )
        add(
            context.getString(R.string.device_row_manager_channel) to
                context.getString(
                    if (state.isManagerPrBuild) R.string.device_channel_pr
                    else R.string.device_channel_release,
                ),
        )
        add(context.getString(R.string.device_row_recognized) to (if (state.isManager) yes else no))
        add(context.getString(R.string.device_row_root) to (if (state.isRootAvailable) yes else no))
    }

    val statsRows = buildList {
        add(
            context.getString(R.string.device_row_modules) to
                moduleState.modules.count { it.enabled && !it.remove }.toString(),
        )
        add(context.getString(R.string.device_row_superuser) to superuserCount.toString())
    }

    FolkScaffold(
        title = stringResource(R.string.device_info_title),
        onBack = { navigator.pop() },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { DeviceSection(stringResource(R.string.device_section_device), deviceRows) }
            item { DeviceSection(stringResource(R.string.device_section_kernel), kernelRows) }
            item { DeviceSection(stringResource(R.string.device_section_manager), managerRows) }
            item { DeviceSection(stringResource(R.string.device_section_stats), statsRows) }
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = FolkButtonDefaults.filledColors(),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as? ClipboardManager
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText(
                                context.getString(R.string.device_info_title),
                                buildCopyText(
                                    listOf(
                                        context.getString(R.string.device_section_device) to deviceRows,
                                        context.getString(R.string.device_section_kernel) to kernelRows,
                                        context.getString(R.string.device_section_manager) to managerRows,
                                        context.getString(R.string.device_section_stats) to statsRows,
                                    ),
                                ),
                            ),
                        )
                        copied = true
                    },
                ) {
                    Text(
                        text = context.getString(
                            if (copied) R.string.device_copied else R.string.device_copy_all,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceSection(title: String, rows: List<Pair<String, String>>) {
    if (rows.isEmpty()) return
    FolkSettingsSection(title = title) {
        FolkSettingsGroup {
            item {
                FolkFactsGroup {
                    rows.forEach { row -> fact(row.first, row.second) }
                }
            }
        }
    }
}

private fun buildCopyText(sections: List<Pair<String, List<Pair<String, String>>>>): String {
    val builder = StringBuilder()
    sections.forEach { (title, rows) ->
        if (rows.isEmpty()) return@forEach
        if (builder.isNotEmpty()) builder.append('\n')
        builder.append('[').append(title).append(']').append('\n')
        rows.forEach { (label, value) ->
            builder.append(label).append(": ").append(value).append('\n')
        }
    }
    return builder.toString().trim()
}

/** The few values only readable as system properties, plus their fallbacks. */
private data class DeviceProperties(
    val deviceName: String,
    val marketName: String,
    val systemVersion: String,
) {
    companion object {
        fun read(): DeviceProperties = DeviceProperties(
            deviceName = getProp("persist.sys.device_name"),
            marketName = getProp("ro.product.marketname").ifBlank { Build.MODEL },
            systemVersion = getProp("ro.system.build.version.incremental")
                .ifBlank { getProp("ro.build.version.incremental") }
                .ifBlank { Build.DISPLAY.orEmpty() },
        )

        /**
         * android.os.SystemProperties is hidden from the SDK, but every one of these keys is
         * public, read-only device metadata - the same values HyperCeiler's device card shows.
         */
        private fun getProp(key: String): String = runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            (get.invoke(null, key) as? String).orEmpty()
        }.getOrDefault("")
    }
}
