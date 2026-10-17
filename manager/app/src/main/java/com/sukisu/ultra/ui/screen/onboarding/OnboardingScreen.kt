package com.sukisu.ultra.ui.screen.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.ui.component.folk.FolkButtonDefaults
import com.sukisu.ultra.ui.component.folk.FolkFactsGroup
import com.sukisu.ultra.ui.component.folk.FolkPreference
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkSettingsGroup
import com.sukisu.ultra.ui.component.folk.FolkSettingsSection
import com.sukisu.ultra.ui.component.folk.FolkSeverity
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.viewmodel.HomeViewModel

/**
 * The first-run guide, in three steps: what this app is, whether the kernel can actually serve
 * it, and the two optional permissions that keep installs and downloads alive.
 *
 * Deliberately short and skippable - this is a root tool for people who already know what they
 * came for, so the guide exists to surface the one thing that silently breaks everything (a
 * kernel that does not recognise the manager) rather than to hold anyone's hand. The layout is
 * the settings page's: section title, one group surface, facts inside it.
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
            TextButton(
                onClick = finish,
                colors = FolkButtonDefaults.textColors(),
            ) {
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
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
            ) {
                when (step) {
                    0 -> {
                        item {
                            GuideHeader(
                                name = context.getString(R.string.app_name),
                                version = state.systemInfo.managerVersion,
                            )
                        }
                        item {
                            StepCard(
                                title = stringResource(R.string.onboard_welcome_title),
                                body = stringResource(R.string.onboard_welcome_body),
                            )
                        }
                    }

                    1 -> {
                        item {
                            StepCard(
                                title = stringResource(R.string.onboard_env_title),
                                body = stringResource(R.string.onboard_env_body),
                            )
                        }
                        item {
                            FolkSettingsSection(title = stringResource(R.string.onboard_env_title)) {
                                FolkSettingsGroup {
                                    item {
                                        FolkFactsGroup {
                                            fact(
                                                label = stringResource(R.string.onboard_env_root),
                                                value = if (state.isRootAvailable) yes else no,
                                                valueTone = if (state.isRootAvailable) {
                                                    FolkSeverity.Positive
                                                } else {
                                                    FolkSeverity.Critical
                                                },
                                            )
                                            fact(
                                                label = stringResource(R.string.onboard_env_manager),
                                                value = if (state.isManager) yes else no,
                                                valueTone = if (state.isManager) {
                                                    FolkSeverity.Positive
                                                } else {
                                                    FolkSeverity.Critical
                                                },
                                            )
                                            fact(
                                                label = stringResource(R.string.onboard_env_kernel),
                                                value = state.kernelUAPIVersion?.toString() ?: no,
                                                valueTone = if (state.requiresNewKernel) {
                                                    FolkSeverity.Caution
                                                } else {
                                                    FolkSeverity.Neutral
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (!state.isManager) {
                            item {
                                FolkSettingsSection {
                                    Text(
                                        text = stringResource(R.string.onboard_env_bad),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(
                                            horizontal = 20.dp,
                                            vertical = 4.dp,
                                        ),
                                    )
                                }
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
                            FolkSettingsSection(title = stringResource(R.string.onboard_perm_title)) {
                                FolkSettingsGroup {
                                    item {
                                        FolkPreference(
                                            title = stringResource(R.string.onboard_perm_notification),
                                            summary = stringResource(
                                                R.string.onboard_perm_notification_summary,
                                            ),
                                            trailing = {
                                                PermissionAction(
                                                    granted = notificationsGranted,
                                                    label = stringResource(R.string.onboard_perm_grant),
                                                    onAction = {
                                                        if (Build.VERSION.SDK_INT >=
                                                            Build.VERSION_CODES.TIRAMISU
                                                        ) {
                                                            permissionLauncher.launch(
                                                                Manifest.permission
                                                                    .POST_NOTIFICATIONS,
                                                            )
                                                        }
                                                    },
                                                )
                                            },
                                        )
                                    }
                                    item {
                                        FolkPreference(
                                            title = stringResource(R.string.onboard_perm_battery),
                                            summary = stringResource(
                                                R.string.onboard_perm_battery_summary,
                                            ),
                                            trailing = {
                                                PermissionAction(
                                                    granted = false,
                                                    label = stringResource(R.string.onboard_perm_open),
                                                    onAction = {
                                                        runCatching {
                                                            context.startActivity(
                                                                Intent(
                                                                    Settings
                                                                        .ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                                            )
                                                        }
                                                    },
                                                )
                                            },
                                        )
                                    }
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (step > 0) {
                    TextButton(
                        onClick = { step -= 1 },
                        colors = FolkButtonDefaults.textColors(),
                    ) {
                        Text(stringResource(R.string.onboard_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (step == 1) {
                    TextButton(
                        onClick = { viewModel.refresh() },
                        colors = FolkButtonDefaults.textColors(),
                    ) {
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

/**
 * The mark and version above the first step, the same header the about page carries: this is
 * a guide that introduces the app, so it opens with the app rather than with a paragraph.
 */
@Composable
private fun GuideHeader(name: String, version: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 40.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White),
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            modifier = Modifier.padding(top = 14.dp),
            text = name,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = version,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepCard(title: String, body: String) {
    FolkSettingsSection {
        FolkSettingsGroup {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionAction(
    granted: Boolean,
    label: String,
    onAction: () -> Unit,
) {
    TextButton(
        onClick = onAction,
        enabled = !granted,
        colors = FolkButtonDefaults.textColors(),
    ) {
        Text(text = if (granted) stringResource(R.string.device_value_yes) else label)
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
