package com.sukisu.ultra.ui.screen.home

import androidx.compose.runtime.Immutable
import com.sukisu.ultra.ui.util.HardwareMonitor

/**
 * The hardware readings the stats layout shows.
 *
 * Every field has a default so the layout has something to draw before the first sample arrives -
 * collecting them needs a root shell, and on a device where a reading is unavailable it stays at
 * its default rather than the section disappearing.
 */
@Immutable
data class SystemMonitorState(
    val cpuUsage: Int = 0,
    val gpuUsage: Int = 0,
    val cpuTemp: Float = 0f,
    val cpuFrequencies: List<HardwareMonitor.CpuFreqInfo> = emptyList(),
    val memoryInfo: HardwareMonitor.MemoryInfo? = null,
    val batteryLevel: Int = 0,
    val batteryTemp: Float = 0f,
    val batteryCharging: Boolean = false,
    val networkRxBytes: Long = 0L,
    val networkTxBytes: Long = 0L,
    /** Per-partition usage, empty when the partitions could not be read. */
    val storagePartitions: List<HardwareMonitor.StoragePartitionInfo> = emptyList(),
)

/**
 * Rolling history for the charts, newest last.
 *
 * Capped at [MAX_POINTS] so the charts show a moving window rather than growing without bound.
 */
@Immutable
data class TimeSeriesData(
    val cpuHistory: List<Float> = emptyList(),
    val gpuHistory: List<Float> = emptyList(),
    val cpuTempHistory: List<Float> = emptyList(),
    val ramHistory: List<Float> = emptyList(),
) {
    companion object {
        const val MAX_POINTS = 30
    }
}

/**
 * The counts shown beside the pie chart.
 *
 * There is no kernel-module count: KernelSU has no such concept, and a field that is always zero
 * would invite a caller to display it as one.
 */
@Immutable
data class ModuleStatsState(
    val apmModuleCount: Int = 0,
    val superuserCount: Int = 0,
)
