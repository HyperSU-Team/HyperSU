package com.sukisu.ultra.ui.screen.home

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukisu.ultra.ui.util.HardwareMonitor
import com.sukisu.ultra.ui.util.getModuleCount
import com.sukisu.ultra.ui.util.getSuperuserCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Samples the hardware counters the stats layout draws.
 *
 * Polling starts when the screen becomes visible and stops when it leaves, rather than running for
 * the whole life of the process: every sample costs a root shell command, and there is nothing to
 * show while the user is on another tab.
 *
 * The counters are kept in separate jobs with their own intervals because they change at very
 * different rates - CPU load is worth reading every few seconds, the partition list is not.
 */
class DashboardViewModel : ViewModel() {

    private companion object {
        const val TAG = "DashboardViewModel"
        const val CPU_TEMP_INTERVAL_MS = 3_000L
        const val GPU_INTERVAL_MS = 2_000L
        const val MEMORY_INTERVAL_MS = 5_000L
        const val BATTERY_NETWORK_INTERVAL_MS = 10_000L
        const val STORAGE_INTERVAL_MS = 30_000L
    }

    private val _systemMonitor = MutableStateFlow(SystemMonitorState())
    val systemMonitor: StateFlow<SystemMonitorState> = _systemMonitor.asStateFlow()

    private val _timeSeries = MutableStateFlow(TimeSeriesData())
    val timeSeries: StateFlow<TimeSeriesData> = _timeSeries.asStateFlow()

    private val _moduleStats = MutableStateFlow(ModuleStatsState())
    val moduleStats: StateFlow<ModuleStatsState> = _moduleStats.asStateFlow()

    private val jobs = mutableListOf<Job>()

    /**
     * Begins sampling, or does nothing if sampling is already running.
     *
     * The guard matters: the screen calls this from a lifecycle effect, and without it a
     * recomposition that restarted the effect would leave the previous jobs running and overwrite
     * the references to them, so they could never be cancelled.
     */
    fun startPolling(context: Context) {
        if (jobs.any { it.isActive }) return
        val appContext = context.applicationContext

        jobs += viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                runCatching {
                    val cpu = HardwareMonitor.getCpuUsage()
                    val frequencies = HardwareMonitor.getCpuFrequencies()
                    val temp = HardwareMonitor.getCpuTemperature()
                    _systemMonitor.update {
                        it.copy(cpuUsage = cpu, cpuFrequencies = frequencies, cpuTemp = temp)
                    }
                    _timeSeries.update {
                        it.copy(
                            cpuHistory = (it.cpuHistory + cpu.toFloat()).takeLast(TimeSeriesData.MAX_POINTS),
                            cpuTempHistory = (it.cpuTempHistory + temp).takeLast(TimeSeriesData.MAX_POINTS),
                        )
                    }
                }.onFailure { Log.w(TAG, "CPU sample failed", it) }
                delay(CPU_TEMP_INTERVAL_MS)
            }
        }

        jobs += viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                runCatching {
                    val gpu = HardwareMonitor.getGpuUsage()
                    _systemMonitor.update { it.copy(gpuUsage = gpu) }
                    _timeSeries.update {
                        it.copy(gpuHistory = (it.gpuHistory + gpu.toFloat()).takeLast(TimeSeriesData.MAX_POINTS))
                    }
                }.onFailure { Log.w(TAG, "GPU sample failed", it) }
                delay(GPU_INTERVAL_MS)
            }
        }

        jobs += viewModelScope.launch(Dispatchers.IO) {
            delay(2_000L)
            while (true) {
                runCatching {
                    val memory = HardwareMonitor.getMemoryInfo()
                    _systemMonitor.update { it.copy(memoryInfo = memory) }
                    val percent = if (memory.ramTotal > 0L) {
                        memory.ramUsed.toFloat() / memory.ramTotal.toFloat() * 100f
                    } else {
                        0f
                    }
                    _timeSeries.update {
                        it.copy(ramHistory = (it.ramHistory + percent).takeLast(TimeSeriesData.MAX_POINTS))
                    }
                }.onFailure { Log.w(TAG, "memory sample failed", it) }
                delay(MEMORY_INTERVAL_MS)
            }
        }

        jobs += viewModelScope.launch(Dispatchers.IO) {
            delay(5_000L)
            while (true) {
                runCatching {
                    val battery = readBattery(appContext)
                    // TrafficStats reports -1 when the device does not support the counter, which
                    // would otherwise be formatted as a negative number of bytes.
                    val rx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
                    val tx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
                    _systemMonitor.update {
                        it.copy(
                            batteryLevel = battery.level,
                            batteryTemp = battery.temp,
                            batteryCharging = battery.charging,
                            networkRxBytes = rx,
                            networkTxBytes = tx,
                        )
                    }
                }.onFailure { Log.w(TAG, "battery sample failed", it) }
                delay(BATTERY_NETWORK_INTERVAL_MS)
            }
        }

        // Partition usage changes far more slowly than anything else here, and reading it costs
        // five `df` calls, so it is sampled on its own long interval.
        jobs += viewModelScope.launch(Dispatchers.IO) {
            delay(8_000L)
            while (true) {
                runCatching {
                    val partitions = HardwareMonitor.getStoragePartitions()
                    _systemMonitor.update { it.copy(storagePartitions = partitions) }
                }.onFailure { Log.w(TAG, "storage sample failed", it) }
                delay(STORAGE_INTERVAL_MS)
            }
        }
    }

    /** Stops sampling. Safe to call when nothing is running. */
    fun stopPolling() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    /**
     * Reads the three module counts once.
     *
     * Called when the stats layout appears rather than polled: the counts only change when the user
     * installs or removes something, and each read costs a root shell command.
     */
    fun loadModuleStats() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val superusers = getSuperuserCount()
                val modules = getModuleCount()
                _moduleStats.value = ModuleStatsState(
                    apmModuleCount = modules,
                    superuserCount = superusers,
                )
            }.onFailure { Log.w(TAG, "module counts failed", it) }
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private data class BatteryReading(val level: Int, val temp: Float, val charging: Boolean)

    private fun readBattery(context: Context): BatteryReading {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0

        var temp = 0f
        var charging = false
        // A null receiver asks for the sticky broadcast's current value without registering
        // anything. It is deprecated on recent releases but is still the only way to read the
        // battery temperature without holding a receiver open.
        @Suppress("UnspecifiedRegisterReceiverFlag", "DEPRECATION")
        val intent: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        if (intent != null) {
            val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            if (tenths > 0) temp = tenths / 10f
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }
        return BatteryReading(level.coerceIn(0, 100), temp, charging)
    }
}
