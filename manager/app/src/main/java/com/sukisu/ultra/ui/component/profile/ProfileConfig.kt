package com.sukisu.ultra.ui.component.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.profile.Capabilities
import com.sukisu.ultra.profile.Groups
import com.sukisu.ultra.toRawFlags
import com.sukisu.ultra.toRootProfileFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.sukisu.ultra.ui.component.folk.FolkAlertDialog
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkNavigationPreference
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkSwitchPreference
import com.sukisu.ultra.ui.component.profile.dialogs.MultiSelectDialog
import com.sukisu.ultra.ui.component.profile.dialogs.SingleSelectDialog
import com.sukisu.ultra.ui.theme.tokens.FolkType
import com.sukisu.ultra.ui.util.isSepolicyValid
import com.sukisu.ultra.ui.util.listAppProfileTemplates
import com.sukisu.ultra.ui.util.setSepolicy
import com.sukisu.ultra.ui.viewmodel.getTemplateInfoById

/**
 * The per-app profile editor, in the FolkPatch design.
 *
 * Behaviour is unchanged: the same fields are written back through
 * [onProfileChange], and a disabled field falls back to the kernel's default
 * value exactly as the old variants did. Only the presentation is shared now.
 */
@Composable
fun AppProfileConfig(
    modifier: Modifier = Modifier,
    fixedName: Boolean,
    enabled: Boolean,
    profile: Natives.Profile,
    onProfileChange: (Natives.Profile) -> Unit,
) {
    Column(modifier = modifier) {
        if (!fixedName) {
            OutlinedTextField(
                value = profile.name,
                onValueChange = { onProfileChange(profile.copy(name = it)) },
                label = { Text(stringResource(R.string.profile_name)) },
                readOnly = !enabled,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        FolkSettingsGroup {
            item {
                FolkSwitchPreference(
                    title = stringResource(R.string.profile_umount_modules),
                    summary = stringResource(R.string.profile_umount_modules_summary),
                    checked = if (enabled) {
                        profile.umountModules
                    } else {
                        Natives.isDefaultUmountModules()
                    },
                    enabled = enabled,
                    onCheckedChange = {
                        onProfileChange(
                            profile.copy(
                                umountModules = it,
                                nonRootUseDefault = false,
                            )
                        )
                    },
                )
            }
        }
    }
}

/** The root profile editor: UID/GID, groups, capabilities, namespace, flags and SELinux. */
@Composable
fun RootProfileConfig(
    modifier: Modifier = Modifier,
    fixedName: Boolean,
    enabled: Boolean = true,
    profile: Natives.Profile,
    onProfileChange: (Natives.Profile) -> Unit,
) {
    Column(
        modifier = modifier.padding(bottom = 13.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        if (!fixedName) {
            OutlinedTextField(
                value = profile.name,
                onValueChange = { onProfileChange(profile.copy(name = it)) },
                label = { Text(stringResource(R.string.profile_name)) },
                readOnly = !enabled,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        UidGidPanel(
            enabled = enabled,
            uid = profile.uid,
            gid = profile.gid,
            onUidChange = { onProfileChange(profile.copy(uid = it, rootUseDefault = false)) },
            onGidChange = { onProfileChange(profile.copy(gid = it, rootUseDefault = false)) },
        )

        GroupsPanel(
            enabled = enabled,
            selected = profile.groups.mapNotNull { gid -> Groups.entries.find { it.gid == gid } },
            onSelectionChange = { selection ->
                onProfileChange(
                    profile.copy(
                        groups = selection.map { it.gid },
                        rootUseDefault = false,
                    )
                )
            },
        )

        CapsPanel(
            enabled = enabled,
            selected = profile.capabilities,
            onSelectionChange = { selection ->
                onProfileChange(
                    profile.copy(
                        capabilities = selection.map { it.cap },
                        rootUseDefault = false,
                    )
                )
            },
        )

        MountNameSpacePanel(
            enabled = enabled,
            namespace = profile.namespace,
            onNamespaceChange = {
                onProfileChange(profile.copy(namespace = it, rootUseDefault = false))
            },
        )

        RootProfileFlagPanel(
            enabled = enabled,
            selected = profile.flags.toRootProfileFlags(),
            onSelectionChange = {
                onProfileChange(profile.copy(flags = it.toRawFlags()))
            },
        )

        SELinuxPanel(
            enabled = enabled,
            context = profile.context,
            rules = profile.rules,
            onContextChange = { domain ->
                onProfileChange(profile.copy(context = domain, rootUseDefault = false))
            },
            onRulesChange = { rules ->
                onProfileChange(profile.copy(rules = rules, rootUseDefault = false))
            },
        )
    }
}

/** The template picker plus a shortcut to read the selected template's rules. */
@Composable
fun TemplateConfig(
    modifier: Modifier = Modifier,
    profile: Natives.Profile,
    onViewTemplate: (id: String) -> Unit = {},
    onManageTemplate: () -> Unit = {},
    onProfileChange: (Natives.Profile) -> Unit,
) {
    val showDialog = remember { mutableStateOf(false) }
    val template = profile.rootTemplate ?: ""
    // `listAppProfileTemplates` is a root shell round trip; it must not run during composition,
    // where every recomposition of the picker blocked the main thread on ksud.
    val profileTemplates by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) { listAppProfileTemplates() }
    }
    val noTemplates = profileTemplates.isEmpty()

    val templateOptions = remember(profileTemplates) {
        profileTemplates.map { TemplateOption(it, it) }
    }
    val selectedTemplate = remember(template, templateOptions) {
        templateOptions.find { it.id == template } ?: templateOptions.firstOrNull()
    }

    if (showDialog.value && !noTemplates) {
        SingleSelectDialog(
            title = stringResource(R.string.profile_template),
            items = templateOptions,
            selectedItem = selectedTemplate ?: templateOptions.first(),
            itemTitle = { it.name },
            onConfirm = { selected ->
                val tid = selected.id
                val templateInfo = getTemplateInfoById(tid)
                if (templateInfo != null &&
                    setSepolicy(tid, templateInfo.rules.joinToString("\n"))
                ) {
                    onProfileChange(
                        profile.copy(
                            rootTemplate = tid,
                            rootUseDefault = false,
                            uid = templateInfo.uid,
                            gid = templateInfo.gid,
                            groups = templateInfo.groups,
                            capabilities = templateInfo.capabilities,
                            context = templateInfo.context,
                            namespace = templateInfo.namespace,
                        )
                    )
                }
                showDialog.value = false
            },
            onDismiss = { showDialog.value = false },
        )
    }

    val selectedTemplateName = template.ifEmpty { "None" }

    FolkSettingsGroup(modifier = modifier) {
        item {
            FolkNavigationPreference(
                title = stringResource(R.string.profile_template),
                summary = selectedTemplateName,
                enabled = !noTemplates,
                onClick = { showDialog.value = true },
            )
        }
        if (noTemplates) {
            item {
                FolkNavigationPreference(
                    title = stringResource(R.string.app_profile_template_create),
                    onClick = onManageTemplate,
                )
            }
        }
        if (template.isNotEmpty()) {
            item {
                FolkNavigationPreference(
                    title = stringResource(R.string.app_profile_template_view),
                    onClick = { onViewTemplate(template) },
                )
            }
        }
    }
}

private data class TemplateOption(
    val id: String,
    val name: String,
)

// ---------------------------------------------------------------------------
// Root profile panels
// ---------------------------------------------------------------------------

@Composable
private fun UidGidPanel(
    enabled: Boolean,
    uid: Int,
    gid: Int,
    onUidChange: (Int) -> Unit,
    onGidChange: (Int) -> Unit,
) {
    var uidText by remember(uid) { mutableStateOf(uid.toString()) }
    var gidText by remember(gid) { mutableStateOf(gid.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            enabled = enabled,
            value = uidText,
            onValueChange = {
                uidText = it
                it.toIntOrNull()?.let(onUidChange)
            },
            label = { Text("UID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            enabled = enabled,
            value = gidText,
            onValueChange = {
                gidText = it
                it.toIntOrNull()?.let(onGidChange)
            },
            label = { Text("GID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GroupsPanel(
    enabled: Boolean,
    selected: List<Groups>,
    onSelectionChange: (Set<Groups>) -> Unit,
) {
    val showDialog = remember { mutableStateOf(false) }

    val groups = remember {
        Groups.entries.sortedWith(
            compareBy<Groups> {
                when (it) {
                    Groups.ROOT -> 0
                    Groups.SYSTEM -> 1
                    Groups.SHELL -> 2
                    else -> Int.MAX_VALUE
                }
            }.then(compareBy { it.name })
        )
    }

    if (showDialog.value) {
        MultiSelectDialog(
            title = "Groups",
            subtitle = "${selected.size} / 32",
            items = groups,
            selectedItems = selected.toSet(),
            itemTitle = { it.display },
            itemSubtitle = { it.desc },
            maxSelection = 32,
            onSelectionChange = onSelectionChange,
            onDismiss = { showDialog.value = false },
        )
    }

    val tag = if (selected.isEmpty()) {
        "None"
    } else {
        selected.joinToString(", ") { it.display }
    }

    FolkSettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            FolkNavigationPreference(
                title = stringResource(R.string.profile_groups),
                summary = tag,
                enabled = enabled,
                onClick = { showDialog.value = true },
            )
        }
    }
}

@Composable
private fun MountNameSpacePanel(
    enabled: Boolean,
    namespace: Int,
    onNamespaceChange: (Int) -> Unit,
) {
    data class NamespaceOption(val value: Int, val label: String)

    val showDialog = remember { mutableStateOf(false) }

    val inheritedLabel = stringResource(R.string.profile_namespace_inherited)
    val globalLabel = stringResource(R.string.profile_namespace_global)
    val individualLabel = stringResource(R.string.profile_namespace_individual)

    val options = remember(inheritedLabel, globalLabel, individualLabel) {
        listOf(
            NamespaceOption(0, inheritedLabel),
            NamespaceOption(1, globalLabel),
            NamespaceOption(2, individualLabel),
        )
    }

    val selectedOption = options.find { it.value == namespace } ?: options[0]

    if (showDialog.value) {
        SingleSelectDialog(
            title = stringResource(R.string.profile_namespace),
            items = options,
            selectedItem = selectedOption,
            itemTitle = { it.label },
            onConfirm = {
                onNamespaceChange(it.value)
                showDialog.value = false
            },
            onDismiss = { showDialog.value = false },
        )
    }

    FolkSettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            FolkNavigationPreference(
                title = stringResource(R.string.profile_namespace),
                summary = selectedOption.label,
                enabled = enabled,
                onClick = { showDialog.value = true },
            )
        }
    }
}

@Composable
private fun RootProfileFlagPanel(
    enabled: Boolean,
    selected: List<Natives.Profile.RootProfileFlag>,
    onSelectionChange: (flags: List<Natives.Profile.RootProfileFlag>) -> Unit,
) {
    val showDialog = remember { mutableStateOf(false) }

    val selectedFlags = remember(selected) {
        selected.mapNotNull { flag ->
            Natives.Profile.RootProfileFlag.entries.find { it.ordinal == flag.ordinal }
        }
    }
    val flags = remember { Natives.Profile.RootProfileFlag.entries.sortedBy { it.display } }

    if (showDialog.value) {
        MultiSelectDialog(
            title = stringResource(R.string.profile_flags),
            subtitle = "${selectedFlags.size} / ${Natives.Profile.RootProfileFlag.entries.size}",
            items = flags,
            selectedItems = selectedFlags.toSet(),
            itemTitle = { it.display },
            itemSubtitle = { null },
            maxSelection = Int.MAX_VALUE,
            onSelectionChange = { onSelectionChange(it.toList()) },
            onDismiss = { showDialog.value = false },
        )
    }

    val tag = if (selectedFlags.isEmpty()) {
        "None"
    } else {
        selectedFlags.joinToString(", ") { it.display }
    }

    FolkSettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            FolkNavigationPreference(
                title = stringResource(R.string.profile_flags),
                summary = tag,
                enabled = enabled,
                onClick = { showDialog.value = true },
            )
        }
    }
}

@Composable
private fun CapsPanel(
    enabled: Boolean,
    selected: List<Int>,
    onSelectionChange: (Set<Capabilities>) -> Unit,
) {
    val showDialog = remember { mutableStateOf(false) }

    val selectedCaps = remember(selected) {
        selected.mapNotNull { cap -> Capabilities.entries.find { it.cap == cap } }
    }
    val capabilities = remember { Capabilities.entries.sortedBy { it.display } }

    if (showDialog.value) {
        MultiSelectDialog(
            title = "Capabilities",
            subtitle = "${selectedCaps.size} / ${Capabilities.entries.size}",
            items = capabilities,
            selectedItems = selectedCaps.toSet(),
            itemTitle = { it.display },
            itemSubtitle = { null },
            maxSelection = Int.MAX_VALUE,
            onSelectionChange = onSelectionChange,
            onDismiss = { showDialog.value = false },
        )
    }

    val tag = if (selectedCaps.isEmpty()) {
        "None"
    } else {
        selectedCaps.joinToString(", ") { it.display }
    }

    FolkSettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            FolkNavigationPreference(
                title = stringResource(R.string.profile_capabilities),
                summary = tag,
                enabled = enabled,
                onClick = { showDialog.value = true },
            )
        }
    }
}

@Composable
private fun SELinuxPanel(
    enabled: Boolean,
    context: String,
    rules: String,
    onContextChange: (String) -> Unit,
    onRulesChange: (String) -> Unit,
) {
    val showDialog = remember { mutableStateOf(false) }

    if (showDialog.value) {
        SELinuxDialog(
            domain = context,
            rules = rules,
            onConfirm = { domain, r ->
                onContextChange(domain)
                onRulesChange(r)
                showDialog.value = false
            },
            onDismiss = { showDialog.value = false },
        )
    }

    FolkSettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            FolkNavigationPreference(
                title = stringResource(R.string.profile_selinux_context),
                summary = context.ifEmpty { "-" },
                enabled = enabled,
                onClick = { showDialog.value = true },
            )
        }
    }
}

@Composable
private fun SELinuxDialog(
    domain: String,
    rules: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var currentDomain by remember { mutableStateOf(domain) }
    var currentRules by remember { mutableStateOf(rules) }

    val isDomainValid = remember(currentDomain) {
        currentDomain.matches(Regex("^[a-z_]+:[a-z0-9_]+:[a-z0-9_]+(:[a-z0-9_]+)?$"))
    }
    // `ksud sepolicy check` is a root shell job: running it inside composition fired one exec
    // per keystroke. Debounce instead, and validate off the main thread.
    var isRulesValid by remember { mutableStateOf(true) }
    LaunchedEffect(currentRules) {
        delay(400)
        isRulesValid = withContext(Dispatchers.IO) { isSepolicyValid(currentRules) }
    }

    FolkAlertDialog(onDismissRequest = onDismiss, width = 340.dp) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.profile_selinux_context),
                style = FolkType.Title,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = currentDomain,
                    onValueChange = { currentDomain = it },
                    label = { Text(stringResource(R.string.profile_selinux_domain)) },
                    isError = !isDomainValid,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = currentRules,
                    onValueChange = { currentRules = it },
                    label = { Text(stringResource(R.string.profile_selinux_rules)) },
                    isError = !isRulesValid,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    minLines = 3,
                    maxLines = 10,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = FolkButtonDefaults.textColors(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = { onConfirm(currentDomain, currentRules) },
                    enabled = isDomainValid && isRulesValid,
                    colors = FolkButtonDefaults.filledColors(),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}
