package com.sukisu.ultra.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.ExpressiveCard
import com.sukisu.ultra.ui.component.chart.ModulePieChart
import com.sukisu.ultra.ui.component.chart.SystemAreaChart
import com.sukisu.ultra.ui.component.chart.SystemLineChart
import com.sukisu.ultra.ui.component.chart.StorageColumnChart
import com.sukisu.ultra.ui.component.chart.StoragePartition
import com.sukisu.ultra.ui.component.chart.rememberPieSliceDataFromCounts
import com.sukisu.ultra.ui.util.HardwareMonitor
import java.util.Locale

/** Formats a byte count for display, in the largest unit that keeps it readable. */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "${bytes}B"
    bytes < 1024L * 1024L -> "${bytes / 1024L}KB"
    bytes < 1024L * 1024L * 1024L ->
        String.format(Locale.US, "%.1fMB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.US, "%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0))
}

/** Formats a frequency given in kHz. */
fun formatFreq(khz: Long): String = when {
    khz >= 1_000_000L -> String.format(Locale.US, "%.2fGHz", khz / 1_000_000.0)
    khz >= 1_000L -> String.format(Locale.US, "%.0fMHz", khz / 1_000.0)
    else -> "${khz}KHz"
}

/**
 * The live hardware readings: CPU and GPU load, temperature, memory and battery.
 *
 * Sections that have nothing to show hide themselves rather than drawing an empty chart - a device
 * that does not publish a GPU counter, or a kernel whose thermal zones cannot be read, simply does
 * not get that section.
 */
@Composable
fun SystemMonitoringSection(
    systemMonitor: SystemMonitorState,
    timeSeries: TimeSeriesData,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    ExpressiveCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_device_status_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SystemLineChart(
                    title = stringResource(R.string.home_device_status_cpu_load),
                    dataPoints = timeSeries.cpuHistory,
                    modifier = Modifier.weight(1f),
                    color = colors.primary,
                )
                SystemLineChart(
                    title = stringResource(R.string.home_device_status_gpu_load),
                    dataPoints = timeSeries.gpuHistory,
                    modifier = Modifier.weight(1f),
                    color = colors.tertiary,
                )
            }

            // Only once a reading has actually arrived; the first sample of a device without a
            // thermal zone is 0, and a permanent "0 °C" chart is worse than none.
            if (timeSeries.cpuTempHistory.lastOrNull()?.let { it > 0f } == true) {
                SystemLineChart(
                    title = stringResource(R.string.home_device_status_cpu_temp),
                    dataPoints = timeSeries.cpuTempHistory,
                    unit = "°C",
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.error,
                )
            }

            if (timeSeries.ramHistory.isNotEmpty()) {
                SystemAreaChart(
                    title = stringResource(R.string.home_device_status_memory_trend),
                    dataPoints = timeSeries.ramHistory,
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.secondary,
                )
            }

            if (systemMonitor.cpuFrequencies.isNotEmpty()) {
                CpuFrequencyBars(systemMonitor.cpuFrequencies)
            }

            systemMonitor.memoryInfo?.let { memory ->
                MemoryRows(memory)
            }

            BatteryRow(systemMonitor)

            if (systemMonitor.networkRxBytes > 0L || systemMonitor.networkTxBytes > 0L) {
                NetworkRows(systemMonitor)
            }

            if (systemMonitor.storagePartitions.isNotEmpty()) {
                // The ported chart, rather than a fourth set of progress bars: it was written for
                // exactly this and had never been given any data.
                StorageColumnChart(
                    partitions = systemMonitor.storagePartitions.map { partition ->
                        val percent = if (partition.totalBytes > 0L) {
                            partition.usedBytes.toFloat() / partition.totalBytes.toFloat() * 100f
                        } else {
                            0f
                        }
                        StoragePartition(
                            label = partition.label,
                            usedPercent = percent,
                            color = when {
                                percent > 90f -> colors.error
                                percent > 75f -> colors.tertiary
                                else -> colors.primary
                            },
                        )
                    },
                )
            }
        }
    }
}

/** RAM, ZRAM and swap, each with a bar coloured by how full it is. */
@Composable
private fun MemoryRows(memory: HardwareMonitor.MemoryInfo) {
    val colors = MaterialTheme.colorScheme
    val ramPercent = if (memory.ramTotal > 0L) {
        memory.ramUsed.toFloat() / memory.ramTotal.toFloat() * 100f
    } else {
        0f
    }

    UsageRow(
        icon = Icons.Outlined.Memory,
        label = stringResource(R.string.home_storage_ram),
        valueText = "${formatBytes(memory.ramUsed)} / ${formatBytes(memory.ramTotal)}",
        percent = ramPercent,
        barColor = when {
            ramPercent > 85f -> colors.error
            ramPercent > 70f -> colors.tertiary
            else -> colors.primary
        },
    )

    if (memory.zramTotal > 0L) {
        UsageRow(
            icon = Icons.Outlined.SdStorage,
            label = stringResource(R.string.home_storage_zram),
            valueText = "${formatBytes(memory.zramUsed)} / ${formatBytes(memory.zramTotal)}",
            percent = memory.zramUsed.toFloat() / memory.zramTotal.toFloat() * 100f,
            barColor = colors.tertiary,
        )
    }

    if (memory.swapTotal > 0L) {
        UsageRow(
            icon = Icons.Outlined.Storage,
            label = stringResource(R.string.home_storage_swap),
            valueText = "${formatBytes(memory.swapUsed)} / ${formatBytes(memory.swapTotal)}",
            percent = memory.swapUsed.toFloat() / memory.swapTotal.toFloat() * 100f,
            barColor = colors.error,
        )
    }
}

@Composable
private fun UsageRow(
    icon: ImageVector,
    label: String,
    valueText: String,
    percent: Float,
    barColor: Color,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { percent.coerceIn(0f, 100f) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = colors.surfaceContainerHighest,
        )
    }
}

@Composable
private fun BatteryRow(systemMonitor: SystemMonitorState) {
    val colors = MaterialTheme.colorScheme
    val charging = systemMonitor.batteryCharging

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (charging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryStd,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (charging) colors.primary else colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.home_device_status_battery_level),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }

        Text(
            text = buildString {
                append("${systemMonitor.batteryLevel}%")
                if (charging) append(" · ${stringResource(R.string.home_device_status_battery_charging)}")
                if (systemMonitor.batteryTemp > 0f) append("  ${systemMonitor.batteryTemp.toInt()}°C")
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )

        LinearProgressIndicator(
            progress = { systemMonitor.batteryLevel.coerceIn(0, 100) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = when {
                charging -> colors.primary
                systemMonitor.batteryLevel <= 20 -> colors.error
                systemMonitor.batteryLevel <= 50 -> colors.tertiary
                else -> colors.primary
            },
            trackColor = colors.surfaceContainerHighest,
        )
    }
}

@Composable
private fun NetworkRows(systemMonitor: SystemMonitorState) {
    val colors = MaterialTheme.colorScheme

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        NetworkCell(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Download,
            label = stringResource(R.string.home_network_rx),
            value = formatBytes(systemMonitor.networkRxBytes),
        )
        NetworkCell(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Upload,
            label = stringResource(R.string.home_network_tx),
            value = formatBytes(systemMonitor.networkTxBytes),
        )
    }
}

@Composable
private fun NetworkCell(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
    }
}

/** One bar per CPU cluster, so an eight-core device shows its big and little cores apart. */
@Composable
fun CpuFrequencyBars(
    cpuFrequencies: List<HardwareMonitor.CpuFreqInfo>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    // Cores sharing a maximum frequency are the same cluster.
    val clusters = cpuFrequencies
        .groupBy { it.maxFreqKhz }
        .map { (maxFreq, cores) ->
            val sorted = cores.sortedBy { it.coreIndex }
            CpuCluster(
                label = if (sorted.size == 1) {
                    "CPU${sorted.first().coreIndex}"
                } else {
                    "CPU${sorted.first().coreIndex}-${sorted.last().coreIndex}"
                },
                avgFreq = sorted.sumOf { it.currentFreqKhz } / sorted.size,
                maxFreq = maxFreq,
            )
        }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.DeveloperBoard,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_device_status_cpu_freq),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }

        clusters.forEach { cluster ->
            val percent = if (cluster.maxFreq > 0L) {
                (cluster.avgFreq.toFloat() / cluster.maxFreq.toFloat() * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = cluster.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.width(52.dp),
                )
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        percent > 80f -> colors.error
                        percent > 50f -> colors.tertiary
                        else -> colors.primary
                    },
                    trackColor = colors.surfaceContainerHighest,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatFreq(cluster.avgFreq),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurface,
                    modifier = Modifier.width(64.dp),
                )
            }
        }
    }
}

private data class CpuCluster(val label: String, val avgFreq: Long, val maxFreq: Long)

/**
 * A pie chart of the module counts, with the totals listed beside it.
 *
 * KernelSU has no kernel modules, so that slice and its row are omitted rather than shown at zero:
 * a permanent zero reads as "this device has none", which is not the same as "this app has no such
 * thing". The layout is left to adapt to the shorter list.
 */
@Composable
fun ModuleStatisticsSection(
    stats: ModuleStatsState,
    modifier: Modifier = Modifier,
) {
    val total = stats.apmModuleCount + stats.superuserCount

    ExpressiveCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModulePieChart(
                data = rememberPieSliceDataFromCounts(
                    kernelModules = null,
                    apmModules = stats.apmModuleCount,
                    superusers = stats.superuserCount,
                    apmLabel = stringResource(R.string.home_stats_apm_modules),
                ),
                centerLabel = if (total > 0) total.toString() else "--",
                modifier = Modifier.size(140.dp),
            )
            Column(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatRow(
                    label = stringResource(R.string.home_stats_apm_modules),
                    value = stats.apmModuleCount.toString(),
                    icon = Icons.Outlined.Extension,
                )
                StatRow(
                    label = stringResource(R.string.home_stats_superusers),
                    value = stats.superuserCount.toString(),
                    icon = Icons.Outlined.Shield,
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, icon: ImageVector) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.primary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
    }
}
