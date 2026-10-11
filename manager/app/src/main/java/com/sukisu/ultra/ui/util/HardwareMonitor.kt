package com.sukisu.ultra.ui.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the hardware counters the stats layout displays.
 *
 * Everything here goes through a root shell rather than reading the files directly: several of the
 * paths are readable only by root, and the ones that are not are cheap either way. The stateful
 * parts - CPU load and GPU load are both differences between two readings - are kept in this
 * object, so a caller must poll it rather than call once.
 */
object HardwareMonitor {

    private const val TAG = "HardwareMonitor"

    // CPU load is the difference between two /proc/stat readings.
    private var prevTotalCpu = 0L
    private var prevIdleCpu = 0L

    // Adreno reports cumulative busy/total counters, so load is a difference too. The result is
    // smoothed because a single sample is noisy enough to make the bar jump.
    private var prevGpuUsage = 0L
    private var prevGpuTotal = 0L
    private var lastGpuUpdateTime = 0L
    private var smoothedGpuUsage = -1f
    private const val GPU_EMA_ALPHA = 0.3f

    private const val ADRENO_PERCENT_PATH = "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"
    private const val ADRENO_PATH = "/sys/class/kgsl/kgsl-3d0/gpubusy"
    private const val MALI_PATH = "/sys/class/misc/mali0/device/utilization"
    private const val GENERIC_GPU_PATH = "/sys/kernel/gpu/gpu_busy"

    data class MemoryInfo(
        val ramTotal: Long,
        val ramUsed: Long,
        val swapTotal: Long,
        val swapUsed: Long,
        val zramTotal: Long,
        val zramUsed: Long,
    )

    data class CpuFreqInfo(
        val coreIndex: Int,
        val currentFreqKhz: Long,
        val maxFreqKhz: Long,
    )

    data class StoragePartitionInfo(
        val label: String,
        val totalBytes: Long,
        val usedBytes: Long,
    )

    /** Runs a command in the shared root shell and returns its output lines, empty on failure. */
    private suspend fun runShell(command: String): List<String> =
        withContext(Dispatchers.IO) {
            runCatching { getRootShell().newJob().add(command).exec().out }
                .getOrElse { failure ->
                    Log.w(TAG, "shell command failed: $command", failure)
                    emptyList()
                }
        }

    /**
     * CPU load as a percentage, from the difference between two `/proc/stat` readings.
     *
     * The first call has nothing to compare against and reports 0; a counter that went backwards -
     * the kernel restarted its counters, or the shell returned a stale line - resets the baseline
     * rather than producing a nonsense figure.
     */
    suspend fun getCpuUsage(): Int {
        val line = runShell("cat /proc/stat").firstOrNull { it.startsWith("cpu ") } ?: return 0
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 8) return 0

        return runCatching {
            val currentTotal = (1..7).sumOf { parts[it].toLong() }
            val currentIdle = parts[4].toLong() + parts[5].toLong()

            if (prevTotalCpu == 0L || currentTotal < prevTotalCpu) {
                prevTotalCpu = currentTotal
                prevIdleCpu = currentIdle
                return 0
            }

            val deltaTotal = currentTotal - prevTotalCpu
            val deltaIdle = currentIdle - prevIdleCpu
            prevTotalCpu = currentTotal
            prevIdleCpu = currentIdle

            if (deltaTotal <= 0) return 0
            ((deltaTotal - deltaIdle) * 100 / deltaTotal).toInt().coerceIn(0, 100)
        }.getOrElse {
            Log.w(TAG, "could not parse /proc/stat", it)
            0
        }
    }

    /**
     * GPU load as a percentage, or 0 when the device exposes no counter.
     *
     * Three families are tried in turn: Adreno's direct percentage, Adreno's cumulative counters,
     * then Mali's utilisation. A device with none of them reports 0 rather than an error, because
     * "this GPU does not publish its load" is a normal answer.
     */
    suspend fun getGpuUsage(): Int {
        directPercentage()?.let { return it }
        cumulative()?.let { return it }
        return 0
    }

    private suspend fun directPercentage(): Int? {
        val out = runShell("cat $ADRENO_PERCENT_PATH")
        if (out.isEmpty()) return null
        val value = out[0].trim().replace("%", "").toIntOrNull() ?: return null
        smoothedGpuUsage = value.toFloat()
        return value.coerceIn(0, 100)
    }

    private suspend fun cumulative(): Int? {
        // Adreno: two cumulative counters, busy then total.
        val adreno = runShell("cat $ADRENO_PATH")
        if (adreno.isNotEmpty()) {
            val parts = adreno[0].trim().split(Regex("\\s+"))
            if (parts.size >= 2) {
                val currentUsage = parts[0].toLongOrNull() ?: 0L
                val currentTotal = parts[1].toLongOrNull() ?: 0L
                val sampled = sample(currentUsage, currentTotal)
                if (sampled >= 0f) return sampled.toInt().coerceIn(0, 100)
            }
        }

        // Mali: a single utilisation figure.
        val mali = runShell("cat $MALI_PATH")
        if (mali.isNotEmpty()) {
            val value = mali[0].trim().toIntOrNull() ?: return null
            smoothedGpuUsage = value.toFloat()
            return value.coerceIn(0, 100)
        }

        // Generic kernel GPU node.
        val generic = runShell("cat $GENERIC_GPU_PATH")
        if (generic.isNotEmpty()) {
            val value = generic[0].trim().toIntOrNull() ?: return null
            smoothedGpuUsage = value.toFloat()
            return value.coerceIn(0, 100)
        }

        return null
    }

    /**
     * Turns two cumulative counters into a smoothed percentage.
     *
     * Returns -1 when there is nothing to compute yet: the first reading only establishes the
     * baseline, and a counter that went backwards means the driver reset it.
     */
    private fun sample(currentUsage: Long, currentTotal: Long): Float {
        val now = System.currentTimeMillis()
        if (lastGpuUpdateTime == 0L || currentTotal < prevGpuTotal) {
            prevGpuUsage = currentUsage
            prevGpuTotal = currentTotal
            lastGpuUpdateTime = now
            return -1f
        }

        val deltaTotal = currentTotal - prevGpuTotal
        val deltaUsage = currentUsage - prevGpuUsage
        prevGpuUsage = currentUsage
        prevGpuTotal = currentTotal
        lastGpuUpdateTime = now

        if (deltaTotal <= 0) return -1f
        val instant = (deltaUsage.toFloat() / deltaTotal.toFloat() * 100f).coerceIn(0f, 100f)
        smoothedGpuUsage = if (smoothedGpuUsage < 0f) {
            instant
        } else {
            smoothedGpuUsage + GPU_EMA_ALPHA * (instant - smoothedGpuUsage)
        }
        return smoothedGpuUsage
    }

    /**
     * Memory, split into RAM, swap file and zram.
     *
     * `/proc/meminfo` gives the RAM figures; `/proc/swaps` distinguishes zram from a swap file by
     * name. `MemAvailable` is missing on old kernels, and is approximated from free + cached +
     * buffers there - an over-estimate, but the alternative is reporting no memory at all.
     */
    suspend fun getMemoryInfo(): MemoryInfo {
        var ramTotal = 0L
        var ramUsed = 0L
        var swapTotal = 0L
        var swapUsed = 0L
        var zramTotal = 0L
        var zramUsed = 0L

        val memInfo = runShell("cat /proc/meminfo")
        if (memInfo.isNotEmpty()) {
            var memFree = 0L
            var memAvailable = 0L
            var buffers = 0L
            var cached = 0L

            memInfo.forEach { line ->
                val parts = line.split(Regex(":\\s+"))
                if (parts.size < 2) return@forEach
                val value = (parts[1].trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L) * 1024
                when (parts[0].trim()) {
                    "MemTotal" -> ramTotal = value
                    "MemFree" -> memFree = value
                    "MemAvailable" -> memAvailable = value
                    "Buffers" -> buffers = value
                    "Cached" -> cached = value
                }
            }

            if (memAvailable == 0L && memFree > 0L) memAvailable = memFree + cached + buffers
            if (ramTotal > 0L) ramUsed = (ramTotal - memAvailable).coerceAtLeast(0L)
        }

        val swaps = runShell("cat /proc/swaps")
        if (swaps.isNotEmpty()) {
            swaps.forEach { line ->
                if (line.trim().startsWith("Filename")) return@forEach
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 4) return@forEach
                val size = (parts[2].toLongOrNull() ?: 0L) * 1024
                val used = (parts[3].toLongOrNull() ?: 0L) * 1024
                if (parts[0].contains("zram")) {
                    zramTotal += size
                    zramUsed += used
                } else {
                    swapTotal += size
                    swapUsed += used
                }
            }
        }

        return MemoryInfo(ramTotal, ramUsed, swapTotal, swapUsed, zramTotal, zramUsed)
    }

    /** The CPU thermal zone's temperature in Celsius, or 0 when none can be read. */
    suspend fun getCpuTemperature(): Float {
        // Zones whose type mentions cpu are preferred; the first of them is taken.
        readTemperature(
            runShell(
                "grep -rl 'cpu' /sys/class/thermal/thermal_zone*/type 2>/dev/null | " +
                    "sed 's|/type$|/temp|' | xargs cat 2>/dev/null | head -1",
            ),
        )?.let { return it }

        // Otherwise any zone that will answer.
        for (zone in runShell("ls /sys/class/thermal/ 2>/dev/null")) {
            val name = zone.trim()
            if (!name.startsWith("thermal_zone")) continue
            val type = runShell("cat /sys/class/thermal/$name/type 2>/dev/null")
            if (type.isEmpty()) continue
            if (!type[0].trim().lowercase().contains("cpu")) continue
            readTemperature(runShell("cat /sys/class/thermal/$name/temp 2>/dev/null"))?.let { return it }
        }
        return 0f
    }

    /** Millidegrees are common; anything above 1000 is taken to be one. */
    private fun readTemperature(out: List<String>): Float? {
        val value = out.firstOrNull()?.trim()?.toFloatOrNull() ?: return null
        if (value <= 0f) return null
        return if (value > 1000f) value / 1000f else value
    }

    /** One entry per online CPU core, with its current and maximum frequency. */
    suspend fun getCpuFrequencies(): List<CpuFreqInfo> {
        val infos = mutableListOf<CpuFreqInfo>()
        for (line in runShell("ls /sys/devices/system/cpu/ 2>/dev/null | grep -E '^cpu[0-9]+$'")) {
            val core = line.trim()
            val index = Regex("cpu(\\d+)").find(core)?.groupValues?.get(1)?.toIntOrNull() ?: continue

            val online = runShell("cat /sys/devices/system/cpu/$core/online 2>/dev/null")
            if (online.firstOrNull()?.trim() == "0") continue

            val current = runShell("cat /sys/devices/system/cpu/$core/cpufreq/scaling_cur_freq 2>/dev/null")
            val max = runShell("cat /sys/devices/system/cpu/$core/cpufreq/cpuinfo_max_freq 2>/dev/null")

            infos += CpuFreqInfo(
                coreIndex = index,
                currentFreqKhz = current.firstOrNull()?.trim()?.toLongOrNull() ?: 0L,
                maxFreqKhz = max.firstOrNull()?.trim()?.toLongOrNull() ?: 0L,
            )
        }
        return infos
    }

    /** Usage for the partitions that are worth showing, skipping any that cannot be read. */
    suspend fun getStoragePartitions(): List<StoragePartitionInfo> {
        val partitions = listOf(
            "/data" to "Data",
            "/system" to "System",
            "/cache" to "Cache",
            "/vendor" to "Vendor",
            "/product" to "Product",
        )

        val infos = mutableListOf<StoragePartitionInfo>()
        for ((path, label) in partitions) {
            val out = runShell("df -k $path 2>/dev/null")
            if (out.size < 2) continue
            val parts = out.last().trim().split(Regex("\\s+"))
            if (parts.size < 3) continue
            val total = (parts[1].toLongOrNull() ?: continue) * 1024
            val used = (parts[2].toLongOrNull() ?: continue) * 1024
            if (total > 0L) infos += StoragePartitionInfo(label, total, used)
        }
        return infos
    }
}
