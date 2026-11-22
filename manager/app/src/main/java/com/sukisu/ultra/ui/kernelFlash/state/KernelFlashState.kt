package com.sukisu.ultra.ui.kernelFlash.state

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.kernelFlash.util.AssetsUtil
import com.sukisu.ultra.ui.util.getRootShell
import com.sukisu.ultra.ui.util.install
import com.sukisu.ultra.ui.util.rootAvailable
import com.sukisu.ultra.ui.util.shellArg
import java.nio.file.Files
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */
data class FlashState(
    val isFlashing: Boolean = false,
    val isCompleted: Boolean = false,
    val progress: Float = 0f,
    val currentStep: String = "",
    val logs: List<String> = emptyList(),
    val error: String = ""
)

class HorizonKernelState {
    private val _state = MutableStateFlow(FlashState())
    val state: StateFlow<FlashState> = _state.asStateFlow()

    fun updateProgress(progress: Float) {
        _state.update { it.copy(progress = progress) }
    }

    fun updateStep(step: String) {
        _state.update { it.copy(currentStep = step) }
    }

    fun addLog(log: String) {
        _state.update {
            it.copy(logs = it.logs + log)
        }
    }

    fun setError(error: String) {
        // A failed run is no longer flashing: leaving the flag set kept the screen locked and made
        // a re-entry look like a live job.
        _state.update { it.copy(error = error, isFlashing = false) }
    }

    fun startFlashing() {
        _state.update {
            it.copy(
                isFlashing = true,
                isCompleted = false,
                progress = 0f,
                currentStep = "under preparation...",
                logs = emptyList(),
                error = ""
            )
        }
    }

    fun completeFlashing() {
        _state.update { it.copy(isCompleted = true, progress = 1f) }
    }
}

class HorizonKernelWorker(
    private val context: Context,
    private val state: HorizonKernelState,
    private val slot: String? = null,
) : Thread() {
    var uri: Uri? = null
    private lateinit var filePath: String
    private lateinit var binaryPath: String
    private lateinit var workDir: String

    private var onFlashComplete: (() -> Unit)? = null
    private var originalSlot: String? = null

    fun setOnFlashCompleteListener(listener: () -> Unit) {
        onFlashComplete = listener
    }

    override fun run() {
        state.startFlashing()
        state.updateStep(context.getString(R.string.horizon_preparing))

        try {
            workDir = Files.createTempDirectory(context.cacheDir.toPath(), "kernel-flash-").toString()
            filePath = "$workDir/kernel.zip"
            binaryPath = "$workDir/META-INF/com/google/android/update-binary"
            state.updateStep(context.getString(R.string.horizon_cleaning_files))
            state.updateProgress(0.1f)

            if (!rootAvailable()) {
                throw IOException(context.getString(R.string.root_required))
            }

            state.updateStep(context.getString(R.string.horizon_copying_files))
            state.updateProgress(0.2f)
            copy()

            if (!File(filePath).exists()) {
                state.setError(context.getString(R.string.horizon_copy_failed))
                return
            }

            state.updateStep(context.getString(R.string.horizon_extracting_tool))
            state.updateProgress(0.4f)
            getBinary()

            state.updateStep(context.getString(R.string.horizon_patching_script))
            state.updateProgress(0.6f)
            patch()

            state.updateStep(context.getString(R.string.horizon_flashing))
            state.updateProgress(0.7f)

            val isAbDevice = isAbDevice()

            if (isAbDevice && slot != null) {
                state.updateStep(context.getString(R.string.horizon_getting_original_slot))
                state.updateProgress(0.72f)
                originalSlot = try {
                    val shell = getRootShell()
                    ShellUtils.fastCmd(shell, "getprop ro.boot.slot_suffix").trim()
                } catch (_: Exception) {
                    null
                }

                state.updateStep(context.getString(R.string.horizon_setting_target_slot))
                state.updateProgress(0.74f)
                if (originalSlot.isNullOrEmpty()) throw IOException("Unable to determine the original boot slot")
                if (runCommand(true, "resetprop -n ro.boot.slot_suffix ${"_$slot".shellArg()}") != 0) {
                    throw IOException("Unable to select the target boot slot")
                }
            }

            flash()

            if (isAbDevice && !originalSlot.isNullOrEmpty()) {
                state.updateStep(context.getString(R.string.horizon_restoring_original_slot))
                state.updateProgress(0.8f)
                if (runCommand(true, "resetprop ro.boot.slot_suffix ${originalSlot!!.shellArg()}") != 0) {
                    throw IOException("Unable to restore the original boot slot")
                }
                originalSlot = null
            }

            try {
                install()
            } catch (e: Exception) {
                state.updateStep("ksud update skipped: ${e.message}")
            }

            state.updateStep(context.getString(R.string.horizon_flash_complete_status))
            state.completeFlashing()

            (context as? Activity)?.runOnUiThread {
                onFlashComplete?.invoke()
            }
        } catch (e: Exception) {
            state.setError(e.message ?: context.getString(R.string.horizon_unknown_error))
        } finally {
            originalSlot?.takeIf { it.isNotEmpty() }?.let { suffix ->
                runCatching { runCommand(true, "resetprop ro.boot.slot_suffix ${suffix.shellArg()}") }
                    .onFailure { state.addLog(context.getString(R.string.flash_log_restore_slot_failed, it.message.orEmpty())) }
            }
            if (::workDir.isInitialized) {
                runCatching { runCommand(true, "rm -rf ${workDir.shellArg()}") }
                    .onFailure { state.addLog(context.getString(R.string.flash_log_cleanup_failed, it.message.orEmpty())) }
            }
        }
    }

    // 检查设备是否为AB分区设备
    private fun isAbDevice(): Boolean {
        return try {
            val shell = getRootShell()
            val abUpdate = ShellUtils.fastCmd(shell, "getprop ro.build.ab_update").trim()
            if (!abUpdate.toBoolean()) return false

            val slotSuffix = ShellUtils.fastCmd(shell, "getprop ro.boot.slot_suffix").trim()
            slotSuffix.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun copy() {
        val sourceUri = uri ?: throw IOException("No kernel archive selected")
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Unable to open kernel archive")
        input.use {
            FileOutputStream(File(filePath)).use { output -> it.copyTo(output) }
        }
    }

    private fun getBinary() {
        // Quiet: busybox unzip prints "Archive:" and one "inflating:" line per entry, and that raw
        // output used to land in the flash log the user is watching.
        val result = runCommand(
            false,
            "unzip -q -o ${filePath.shellArg()} 'META-INF/com/google/android/update-binary' -d ${workDir.shellArg()}",
            log = false,
        )
        if (result != 0) throw IOException("Failed to extract update-binary")
        if (!File(binaryPath).exists()) {
            throw IOException("Failed to extract update-binary")
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun patch() {
        val kernelVersion = try {
            val shell = getRootShell()
            ShellUtils.fastCmd(shell, "cat /proc/version")
        } catch (_: Exception) {
            ""
        }
        val versionRegex = """\d+\.\d+\.\d+""".toRegex()
        val version = kernelVersion.let { versionRegex.find(it) }?.value ?: ""
        val toolName = if (version.isNotEmpty()) {
            val parts = version.split('.')
            if (parts.size >= 2) {
                val major = parts[0].toIntOrNull() ?: 0
                val minor = parts[1].toIntOrNull() ?: 0
                if (major < 5 || (major == 5 && minor <= 10)) "5_10" else "5_15+"
            } else {
                "5_15+"
            }
        } else {
            "5_15+"
        }
        val toolPath = "$workDir/mkbootfs"
        AssetsUtil.exportFiles(context, "$toolName-mkbootfs", toolPath)
        // Only the kernel version is logged. The tool name ("5_10" / "5_15+") is an internal detail
        // of which mkbootfs variant was unpacked - it is not a patch tool, and printing it next to
        // the kernel version read as one, which is what made the line confusing.
        state.addLog(context.getString(R.string.kernel_version_log, version))
        val patchCommand = "sed -i ${"/chmod -R 755 tools bin;/i cp -f ${toolPath.shellArg()} \"\$AKHOME/tools\";".shellArg()} ${binaryPath.shellArg()}"
        if (runCommand(false, patchCommand, log = false) != 0) {
            throw IOException("Failed to patch update-binary")
        }
        // sed exits 0 even when the anchor line is absent, in which case the archive would be
        // flashed with the stock mkbootfs and the ramdisk the patch is for would be missing.
        if (runCommand(false, "grep -qF ${toolPath.shellArg()} ${binaryPath.shellArg()}", log = false) != 0) {
            throw IOException("Unsupported update-binary layout")
        }
    }

    private fun flash() {
        val process = ProcessBuilder("su")
            .redirectErrorStream(true)
            .start()

        try {
            process.outputStream.bufferedWriter().use { writer ->
                writer.write("export POSTINSTALL=${workDir.shellArg()}\n")

                // 写入槽位信息到临时文件
                slot?.let { selectedSlot ->
                    writer.write("printf '%s' ${selectedSlot.shellArg()} > ${"$workDir/bootslot".shellArg()}\n")
                }

                // 构建刷写命令
                val flashCommand = buildString {
                    append("sh ${binaryPath.shellArg()} 3 1 ${filePath.shellArg()}")
                    if (slot != null) {
                        append(" ${slot.shellArg()}")
                    }
                    append(" && touch ${"$workDir/done".shellArg()}\n")
                }

                writer.write(flashCommand)
                writer.write("exit\n")
                writer.flush()
            }

            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    if (line.startsWith("ui_print")) {
                        val logMessage = line.removePrefix("ui_print").trim()
                        state.addLog(logMessage)

                        when {
                            logMessage.contains("extracting", ignoreCase = true) -> {
                                state.updateProgress(0.75f)
                            }
                            logMessage.contains("installing", ignoreCase = true) -> {
                                state.updateProgress(0.85f)
                            }
                            logMessage.contains("complete", ignoreCase = true) -> {
                                state.updateProgress(0.95f)
                            }
                        }
                    }
                }
            }
            if (process.waitFor() != 0) {
                throw IOException(context.getString(R.string.flash_failed_message))
            }
        } finally {
            process.destroy()
        }

        if (!File("$workDir/done").exists()) {
            throw IOException(context.getString(R.string.flash_failed_message))
        }
    }

    private fun runCommand(su: Boolean, cmd: String, log: Boolean = true): Int {
        val shell = if (su) "su" else "sh"
        val process = ProcessBuilder(shell, "-c", cmd).redirectErrorStream(true).start()

        return try {
            process.inputStream.bufferedReader().use { reader ->
                if (log) {
                    reader.lineSequence().forEach(state::addLog)
                } else {
                    // Drain the pipe all the same: a full buffer would block the child.
                    reader.lineSequence().forEach { /* discarded on purpose */ }
                }
            }
            process.waitFor()
        } finally {
            process.destroy()
        }
    }
}