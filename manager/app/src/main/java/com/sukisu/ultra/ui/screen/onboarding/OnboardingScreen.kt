package com.sukisu.ultra.ui.screen.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsSectionGroup
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.viewmodel.HomeViewModel

/**
 * The first-run guide, in three steps: what this app is, whether the kernel can actually serve
 * it, and the two optional permissions that keep installs and downloads alive.
 *
 * Deliberately short and skippable - this is a root tool for people who already know what they
 * came for, so the guide exists to surface the one thing that silently breaks everything (a
 * kernel that does not recognise the manager) rather than to hold anyone's hand.
 */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val viewModel = viewModel<HomeViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var step by rememberSaveable { mutableStateOf(0) }
    var notificationsGranted by remember { mutableStateOf(context.hasNotificationPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val finish: () -> Unit = {
        SettingsRepositoryImpl().onboardingDone = true
        navigator.pop()
    }

    val yes = stringResource(R.string.device_value_yes)
    val no = stringResource(R.string.device_value_no)

    FolkScaffold(
        title = stringResource(R.string.onboard_title),
        onBack = {
            if (step > 0) step -= 1 else finish()
        },
        actions = {
            TextButton(onClick = finish) {
                Text(stringResource(R.string.onboard_skip))
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> item {
                        StepCard(
                            title = stringResource(R.string.onboard_welcome_title),
                            body = stringResource(R.string.onboard_welcome_body),
                        )
                    }

                    1 -> {
                        item {
                            StepCard(
                                title = stringResource(R.string.onboard_env_title),
                                body = stringResource(R.string.onboard_env_body),
                            )
                        }
                        item {
                            FolkSettingsSectionGroup(
                                title = stringResource(R.string.onboard_env_title),
                            ) {
                                item {
                                    FolkPreference(
                                        title = stringResource(R.string.onboard_env_root),
                                        summary = if (state.isRootAvailable) yes else no,
                                    )
                                }
                                item {
                                    FolkPreference(
                                        title = stringResource(R.string.onboard_env_manager),
                                        summary = if (state.isManager) yes else no,
                                    )
                                }
                                item {
                                    FolkPreference(
                                        title = stringResource(R.string.onboard_env_kernel),
                                        summary = state.kernelUAPIVersion?.toString() ?: no,
                                    )
                                }
                            }
                        }
                        if (!state.isManager) {
                            item {
                                Text(
                                    text = stringResource(R.string.onboard_env_bad),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }
                    }

                    else -> {
                        item {
                            StepCard(
                                title = stringResource(R.string.onboard_perm_title),
                                body = stringResource(R.string.onboard_perm_body),
                            )
                        }
                        item {
                            FolkSettingsSectionGroup(
                                title = stringResource(R.string.onboard_perm_title),
                            ) {
                                item {
                                    PermissionRow(
                                        title = stringResource(R.string.onboard_perm_notification),
                                        summary = stringResource(
                                            R.string.onboard_perm_notification_summary,
                                        ),
                                        granted = notificationsGranted,
                                        actionLabel = stringResource(R.string.onboard_perm_grant),
                                        onAction = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                permissionLauncher.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                )
                                            }
                                        },
                                    )
                                }
                                item {
                                    PermissionRow(
                                        title = stringResource(R.string.onboard_perm_battery),
                                        summary = stringResource(
                                            R.string.onboard_perm_battery_summary,
                                        ),
                                        granted = false,
                                        actionLabel = stringResource(R.string.onboard_perm_open),
                                        onAction = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 0) {
                    TextButton(onClick = { step -= 1 }) {
                        Text(stringResource(R.string.onboard_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (step == 1) {
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text(stringResource(R.string.onboard_refresh))
                    }
                }
                Button(
                    onClick = {
                        if (step < LAST_STEP) step += 1 else finish()
                    },
                    colors = FolkButtonDefaults.filledColors(),
                ) {
                    Text(
                        text = when (step) {
                            0 -> stringResource(R.string.onboard_start)
                            LAST_STEP -> stringResource(R.string.onboard_done)
                            else -> stringResource(R.string.onboard_next)
                        },
                    )
                }
            }
        }
    }
}

private const val LAST_STEP = 2

@Composable
private fun StepCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    summary: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAction, enabled = !granted) {
            Text(text = if (granted) stringResource(R.string.device_value_yes) else actionLabel)
        }
    }
}

private fun Context.hasNotificationPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
