package com.sukisu.ultra.ui.kernelFlash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.KeyEventBlocker
import com.sukisu.ultra.ui.component.folk.FolkLogCard
import com.sukisu.ultra.ui.component.folk.FolkLogText
import com.sukisu.ultra.ui.component.folk.FolkScaffold
import com.sukisu.ultra.ui.component.folk.FolkTitleStyle
import com.sukisu.ultra.ui.kernelFlash.state.FlashState
import com.sukisu.ultra.ui.theme.tokens.FolkShape
import com.sukisu.ultra.ui.theme.tokens.FolkType

/**
 * The kernel flashing progress screen, in the FolkPatch design.
 *
 * The progress card keeps its status line, the current step,
 * the bar and the error block; the streamed log lives in the shared monospace
 * card. The volume keys stay blocked while flashing, back is routed through
 * [KernelFlashActions.onBack] so an in-flight flash is not abandoned, and the
 * completed state offers the reboot action.
 */
@Composable
fun KernelFlashFolk(
    state: FlashState,
    actions: KernelFlashActions,
    logText: String,
) {
    val scrollState = rememberScrollState()

    BackHandler {
        actions.onBack()
    }

    KeyEventBlocker {
        it.key == Key.VolumeDown || it.key == Key.VolumeUp
    }

    DisposableEffect(Unit) {
        onDispose {
            if (state.isCompleted || state.error.isNotEmpty()) {
                KernelFlashStateHolder.clear()
            }
        }
    }

    FolkScaffold(
        title = stringResource(
            when {
                state.error.isNotEmpty() -> R.string.flash_failed
                state.isCompleted -> R.string.flash_success
                else -> R.string.kernel_flashing
            }
        ),
        titleStyle = FolkTitleStyle.Inline,
        onBack = actions.onBack,
        actions = {
            IconButton(onClick = { actions.onSaveLog(logText) }) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = stringResource(R.string.save_log),
                )
            }
        },
        floatingActionButton = {
            if (state.isCompleted) {
                SmallFloatingActionButton(
                    onClick = actions.onReboot,
                    modifier = Modifier.padding(bottom = 20.dp, end = 20.dp),
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.reboot),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            FlashProgressCard(state)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
            ) {
                LaunchedEffect(logText) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                FolkLogCard(modifier = Modifier.padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FolkLogText(text = logText)
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashProgressCard(
    flashState: FlashState,
) {
    val statusColor = when {
        flashState.error.isNotEmpty() -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val progress = animateFloatAsState(
        targetValue = flashState.progress.coerceIn(0f, 1f),
        label = "FlashProgress",
    )

    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = FolkShape.Corner20,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = when {
                        flashState.error.isNotEmpty() -> stringResource(R.string.flash_failed)
                        flashState.isCompleted -> stringResource(R.string.flash_success)
                        else -> stringResource(R.string.flashing)
                    },
                    style = FolkType.Title,
                    fontWeight = FontWeight.Medium,
                    color = statusColor,
                )

                when {
                    flashState.error.isNotEmpty() -> Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )

                    flashState.isCompleted -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (flashState.currentStep.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = flashState.currentStep,
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainer,
            )

            if (flashState.error.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = flashState.error,
                    style = FolkType.Summary,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            FolkShape.Corner12,
                        )
                        .padding(12.dp),
                )
            }
        }
    }
}
