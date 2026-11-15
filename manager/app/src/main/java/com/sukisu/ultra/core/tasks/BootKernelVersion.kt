package com.sukisu.ultra.core.tasks

import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.io.IOUtils
import com.sukisu.ultra.core.utils.DataSourceChannel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.util.zip.GZIPInputStream

/**
 * Mirrors ksud's parse_kmi_from_boot: touches only the boot header plus the
 * kernel block to find the KMI (e.g. "android15-6.6").
 */
object BootKernelVersion {
    private const val BOOT_MAGIC = "ANDROID!"
    private const val HEADER_READ_SIZE = 64
    private const val INITIAL_KERNEL_PROBE = 1L shl 20
    private const val INITIAL_REMOTE_PROBE = 256L * 1024
    private const val REMOTE_PROBE_GROWTH = 4L
    private const val KMI_SCAN_OVERLAP = 64

    // Android boot headers use 2048/4096 (v0-v2) or a fixed 4096 (v3+).
    // Anything outside this window is a corrupt header, not a real image.
    private const val MIN_PAGE_SIZE = 512L
    private const val MAX_PAGE_SIZE = 64L * 1024L

    // A compressed kernel is expanded only far enough to find the banner; the
    // cap keeps a decompression bomb from exhausting the heap.
    private const val MAX_DECOMPRESSED_SIZE = 32 * 1024 * 1024

    // The banner lives in the first few megabytes of a real kernel. The header's kernel_size is
    // attacker-controlled and can claim gigabytes, so every probe walk stops here instead of
    // allocating what the file asked for.
    private const val MAX_KERNEL_PROBE = 32L * 1024 * 1024

    // Boot header total sizes per header version (android_bootimg layouts)
    private val HEADER_TOTAL_SIZE = mapOf(
        0L to 1632L,
        1L to 1648L,
        2L to 1660L,
        3L to 1580L,
        4L to 1584L,
    )

    private val kmiRegex = Regex("""(\d+\.\d+)(?:\S+)?(android\d+)""")

    private val gzipMagic = byteArrayOf(0x1F, 0x8B.toByte())
    private val xzMagic = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)
    private val lz4FrameMagic = byteArrayOf(0x04, 0x22, 0x4D, 0x18)

    fun parseKmiFromBoot(file: File): String? {
        FileChannel.open(file.toPath(), StandardOpenOption.READ).use { channel ->
            val header = readRange(channel, 0, HEADER_READ_SIZE.toLong()) ?: return null
            val (kernelOffset, kernelSize) = bootKernelBlock(header) ?: return null

            var probeSize = minOf(INITIAL_KERNEL_PROBE, kernelSize.toLong())
            while (true) {
                val data = readRange(channel, kernelOffset.toLong(), probeSize) ?: return null
                parseKmi(data)?.let { return it }
                if (probeSize >= kernelSize || probeSize >= MAX_KERNEL_PROBE) {
                    decompressPartial(data)?.let { parseKmi(it) }?.let { return it }
                    return null
                }
                val grown = minOf(probeSize * 4, kernelSize.toLong(), MAX_KERNEL_PROBE)
                if (grown <= probeSize) return null
                probeSize = grown
            }
        }
    }

    /**
     * Reads a boot image stream, including a decompressed ZIP entry.
     *
     * A truncated or malformed entry simply has no KMI; the probe reports
     * "unknown" instead of throwing into the caller's coroutine.
     */
    fun parseKmiFromBoot(input: InputStream): String? {
        return try {
            val stream = DataInputStream(input)
            val header = ByteArray(HEADER_READ_SIZE)
            if (IOUtils.read(stream, header) < header.size) return null
            val (kernelOffset, kernelSize) = bootKernelBlock(header) ?: return null
            val skip = kernelOffset.toLong() - header.size
            if (skip > 0) IOUtils.skipFully(stream, skip)

            var data = ByteArray(minOf(INITIAL_KERNEL_PROBE, kernelSize.toLong()).toInt())
            var previousSize = 0
            while (true) {
                stream.readFully(data, previousSize, data.size - previousSize)
                parseKmi(data)?.let { return it }
                decompressPartial(data)?.let { parseKmi(it) }?.let { return it }
                if (data.size >= kernelSize || data.size >= MAX_KERNEL_PROBE) return null
                previousSize = data.size
                data = data.copyOf(
                    minOf(data.size.toLong() * 4, kernelSize.toLong(), MAX_KERNEL_PROBE).toInt()
                )
            }
            @Suppress("UNREACHABLE_CODE")
            null
        } catch (_: IOException) {
            null
        }
    }

    /**
     * KMI from a range-readable channel (HTTP Range or local file), touching
     * only the boot header plus a small banner probe.
     */
    fun parseKmiFromBoot(channel: DataSourceChannel): String? {
        val maxSize = channel.size()
        var probeSize = minOf(INITIAL_REMOTE_PROBE, maxSize)
        while (true) {
            val data = channel.readFully(0, probeSize) ?: return null
            parseKmiFromBootData(data)?.let { return it }
            if (probeSize >= maxSize || probeSize >= MAX_KERNEL_PROBE) return null
            val grown = minOf(probeSize * REMOTE_PROBE_GROWTH, maxSize, MAX_KERNEL_PROBE)
            if (grown <= probeSize) return null
            probeSize = grown
        }
    }

    /**
     * Parses a partial boot image prefix; [data] must start at offset 0.
     * [fromPrefixOffset] skips already-scanned bytes so the op-walking probe
     * stays cheap when the banner is deep.
     */
    fun parseKmiFromBootData(data: ByteArray, fromPrefixOffset: Int = 0): String? {
        val (kernelOffset, kernelSize) = bootKernelBlock(data) ?: return null
        if (data.size <= kernelOffset.toLong() + 4L) return null

        val kernelEnd = minOf(data.size.toLong(), kernelOffset.toLong() + kernelSize.toLong()).toInt()
        if (kernelEnd <= kernelOffset) return null

        // Scan only the new bytes, with overlap for a banner straddling the
        // probe boundary.
        val scanFrom = maxOf(kernelOffset, fromPrefixOffset - KMI_SCAN_OVERLAP)
        if (scanFrom < kernelEnd) {
            parseKmi(data, scanFrom, kernelEnd)?.let { return it }
        }

        // Compressed kernel: decompress the prefix so far; the banner usually
        // sits early in the output.
        if (isCompressedKernel(data, kernelOffset)) {
            val kernelData = data.copyOfRange(kernelOffset, kernelEnd)
            decompressPartial(kernelData)?.let { parseKmi(it) }?.let { return it }
        }
        return null
    }

    /**
     * Scans arbitrary bytes, e.g. a single op's uncompressed chunk.
     */
    fun scanKmi(buffer: ByteArray): String? = parseKmi(buffer)

    /**
     * @return (kernel block offset, size), or null without a valid boot header.
     */
    fun bootKernelBlock(data: ByteArray): Pair<Int, Int>? {
        if (data.size < HEADER_READ_SIZE) return null
        if (!startsWith(data, BOOT_MAGIC.toByteArray())) return null
        val kernelSize = data.u32le(8)
        val headerVersion = data.u32le(40)
        if (kernelSize <= 0) return null
        val headerTotal = HEADER_TOTAL_SIZE[headerVersion] ?: return null
        val pageSize = if (headerVersion >= 3) 4096L else data.u32le(36)
        if (pageSize < MIN_PAGE_SIZE || pageSize > MAX_PAGE_SIZE) return null
        val kernelOffset = align(headerTotal, pageSize)
        if (kernelOffset > Int.MAX_VALUE || kernelSize > Int.MAX_VALUE) return null
        return kernelOffset.toInt() to kernelSize.toInt()
    }

    private fun isCompressedKernel(data: ByteArray, offset: Int): Boolean {
        return startsWithAt(data, offset, gzipMagic) ||
            startsWithAt(data, offset, xzMagic) ||
            startsWithAt(data, offset, lz4FrameMagic)
    }

    private fun readRange(channel: FileChannel, position: Long, length: Long): ByteArray? {
        if (length <= 0 || length > Int.MAX_VALUE) return null
        val buffer = ByteBuffer.allocate(length.toInt())
        var total = 0
        while (total < buffer.capacity()) {
            val read = channel.read(buffer, position + total)
            if (read < 0) break
            if (read == 0) return null
            total += read
        }
        if (total == 0) return null
        buffer.flip()
        val data = ByteArray(total)
        buffer.get(data)
        return data
    }

    private fun parseKmi(buffer: ByteArray, from: Int = 0, to: Int = buffer.size): String? {
        val end = minOf(to, buffer.size)
        for (i in maxOf(0, from) until end - 3) {
            val b0 = buffer[i].toInt() and 0xFF
            val b1 = buffer[i + 1].toInt() and 0xFF
            val b2 = buffer[i + 2].toInt() and 0xFF
            val b3 = buffer[i + 3].toInt() and 0xFF
            if (b1 != '.'.code || b2 < '0'.code || b2 > '9'.code) continue
            if (b0 != '5'.code || b3 < '0'.code || b3 > '9'.code) {
                if (b0 < '6'.code || b0 > '9'.code) continue
            }
            val end = minOf(buffer.size, i + 100)
            val nul = (i until end).firstOrNull { buffer[it] == 0.toByte() } ?: end
            val text = String(buffer, i, nul - i, StandardCharsets.UTF_8)
            kmiRegex.find(text)?.let { match ->
                return "${match.groupValues[2]}-${match.groupValues[1]}"
            }
        }
        return null
    }

    private fun decompressPartial(data: ByteArray): ByteArray? {
        return try {
            val stream = when {
                startsWith(data, gzipMagic) -> GZIPInputStream(ByteArrayInputStream(data))
                startsWith(data, xzMagic) -> XZCompressorInputStream(ByteArrayInputStream(data))
                startsWith(data, lz4FrameMagic) -> FramedLZ4CompressorInputStream(ByteArrayInputStream(data))
                else -> return null
            }
            stream.use { decompressPrefix(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun decompressPrefix(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        try {
            while (output.size() < MAX_DECOMPRESSED_SIZE) {
                val read = input.read(
                    buffer,
                    0,
                    minOf(buffer.size, MAX_DECOMPRESSED_SIZE - output.size()),
                )
                if (read < 0) break
                output.write(buffer, 0, read)
            }
        } catch (_: IOException) {
            // Truncated stream: keep the prefix decompressed so far.
        }
        return if (output.size() > 0) output.toByteArray() else null
    }

    private fun align(value: Long, alignment: Long): Long {
        return (value + alignment - 1) / alignment * alignment
    }

    private fun startsWith(data: ByteArray, magic: ByteArray): Boolean {
        if (data.size < magic.size) return false
        for (i in magic.indices) {
            if (data[i] != magic[i]) return false
        }
        return true
    }

    private fun startsWithAt(data: ByteArray, offset: Int, magic: ByteArray): Boolean {
        if (data.size < offset + magic.size) return false
        for (i in magic.indices) {
            if (data[offset + i] != magic[i]) return false
        }
        return true
    }

    private fun ByteArray.u32le(pos: Int): Long {
        return (this[pos].toLong() and 0xFF) or
            ((this[pos + 1].toLong() and 0xFF) shl 8) or
            ((this[pos + 2].toLong() and 0xFF) shl 16) or
            ((this[pos + 3].toLong() and 0xFF) shl 24)
    }
}
