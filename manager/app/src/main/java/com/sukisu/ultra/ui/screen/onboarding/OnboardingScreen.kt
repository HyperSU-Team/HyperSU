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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.ui.component.hyperos.HyperHero
import com.sukisu.ultra.ui.component.hyperos.HyperOsPage
import com.sukisu.ultra.ui.component.hyperos.HyperPillButton
import com.sukisu.ultra.ui.component.hyperos.HyperRow
import com.sukisu.ultra.ui.component.hyperos.HyperSection
import com.sukisu.ultra.ui.component.hyperos.HyperTextAction
import com.sukisu.ultra.ui.component.hyperos.hyperSecondaryColor
import com.sukisu.ultra.ui.component.hyperos.hyperTextColor
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.viewmodel.HomeViewModel

/**
 * The first-run guide as a HyperOS task page: the app's own header, then one card per idea, and
 * a pill at the bottom to move on.
 *
 * Three steps - welcome, whether the kernel can actually serve this manager, and the two
 * optional permissions. Every step is skippable; the point is to put the one failure that
 * silently breaks everything (an unrecognised manager) in front of someone once, not to gate
 * the app behind a tour.
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
    val okColor = Color(0xFF2BC56A)
    val badColor = Color(0xFFFF3B30)
    val warnColor = Color(0xFFFF9500)

    HyperOsPage(
        title = stringResource(R.string.onboard_title),
        onBack = {
            if (step > 0) step -= 1 else finish()
        },
        actions = {
            HyperTextAction(
                text = stringResource(R.string.onboard_skip),
                onClick = finish,
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) {
                    HyperTextAction(
                        text = stringResource(R.string.onboard_back),
                        onClick = { step -= 1 },
                    )
                }
                Spacer(Modifier.weight(1f))
                HyperPillButton(
                    text = when (step) {
                        0 -> stringResource(R.string.onboard_start)
                        LAST_STEP -> stringResource(R.string.onboard_done)
                        else -> stringResource(R.string.onboard_next)
                    },
                    onClick = {
                        if (step < LAST_STEP) step += 1 else finish()
                    },
                    fillWidth = false,
                    modifier = Modifier.width(150.dp),
                )
            }
        },
    ) {
        when (step) {
            0 -> {
                HyperHero(
                    icon = painterResource(id = R.drawable.ic_launcher_foreground),
                    name = context.getString(R.string.app_name),
                    version = state.systemInfo.managerVersion,
                )
                HyperSection {
                    TextCard(
                        title = stringResource(R.string.onboard_welcome_title),
                        body = stringResource(R.string.onboard_welcome_body),
                    )
                }
            }

            1 -> {
                HyperSection(title = stringResource(R.string.onboard_env_title)) {
                    HyperRow(
                        label = stringResource(R.string.onboard_env_root),
                        value = if (state.isRootAvailable) yes else no,
                        valueColor = if (state.isRootAvailable) okColor else badColor,
                    )
                    HyperRow(
                        label = stringResource(R.string.onboard_env_manager),
                        value = if (state.isManager) yes else no,
                        valueColor = if (state.isManager) okColor else badColor,
                    )
                    HyperRow(
                        label = stringResource(R.string.onboard_env_kernel),
                        value = state.kernelUAPIVersion?.toString() ?: no,
                        showDivider = false,
                        valueColor = if (state.requiresNewKernel) warnColor else null,
                    )
                }
                HyperSection {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.onboard_env_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = hyperSecondaryColor(),
                        )
                        if (!state.isManager) {
                            Text(
                                text = stringResource(R.string.onboard_env_bad),
                                style = MaterialTheme.typography.bodyMedium,
                                color = badColor,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                HyperTextAction(
                    text = stringResource(R.string.onboard_refresh),
                    onClick = { viewModel.refresh() },
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            else -> {
                HyperSection(title = stringResource(R.string.onboard_perm_title)) {
                    HyperRow(
                        label = stringResource(R.string.onboard_perm_notification),
                        value = if (notificationsGranted) yes else stringResource(
                            R.string.onboard_perm_grant,
                        ),
                        valueColor = if (notificationsGranted) okColor else null,
                    )
                    Text(
                        text = stringResource(R.string.onboard_perm_notification_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = hyperSecondaryColor(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    HyperRow(
                        label = stringResource(R.string.onboard_perm_battery),
                        value = stringResource(R.string.onboard_perm_open),
                        showDivider = false,
                    )
                    Text(
                        text = stringResource(R.string.onboard_perm_battery_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = hyperSecondaryColor(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                HyperTextAction(
                    text = stringResource(R.string.onboard_perm_grant),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    modifier = Modifier.padding(start = 12.dp),
                    enabled = !notificationsGranted,
                )
                HyperTextAction(
                    text = stringResource(R.string.onboard_perm_open),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    modifier = Modifier.padding(start = 12.dp),
                )
                HyperSection {
                    Text(
                        text = stringResource(R.string.onboard_perm_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = hyperSecondaryColor(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

private const val LAST_STEP = 2

@Composable
private fun TextCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = hyperTextColor(),
        )
        Text(
            text = body,
            fontSize = 14.sp,
            color = hyperSecondaryColor(),
        )
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
