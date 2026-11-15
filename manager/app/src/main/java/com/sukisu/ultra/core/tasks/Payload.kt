package com.sukisu.ultra.core.tasks

import chromeos_update_engine.UpdateMetadata.DeltaArchiveManifest
import chromeos_update_engine.UpdateMetadata.InstallOperation
import chromeos_update_engine.UpdateMetadata.PartitionUpdate
import com.sukisu.ultra.core.utils.DataSourceChannel
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

class Payload(private val channel: DataSourceChannel) {
    private val manifest: DeltaArchiveManifest
    private var dataBase = 0L

    init {
        manifest = readPayloadHeader()
    }

    fun extract(
        outputFile: File,
        partitionName: String,
        console: (String) -> Unit,
        logger: (String) -> Unit,
    ) {
        val partition = findPartition(partitionName)
        console("- Found partition ${partition.partitionName}")

        val actualHash = extractPartition(outputFile, partition, console)

        if (!partition.newPartitionInfo.hasHash()) {
            logger("Hash verification skipped")
            return
        }

        fun toHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

        val expectedHash = partition.newPartitionInfo.hash.toByteArray()
        if (!expectedHash.contentEquals(actualHash)) {
            throw IOException(
                "Hash mismatch, expected ${toHex(expectedHash)}, but got ${toHex(actualHash)}"
            )
        }
        logger("Hash verification passed")
    }

    fun partitionNames(): List<String> = manifest.partitionsList.map { it.partitionName }

    /**
     * Extracts the KMI from the payload's boot partition with minimal reads.
     * A miss means "unknown": the install page asks for a KMI manually and
     * remote downloads pass an explicit --kmi to ksud.
     */
    fun kernelKmi(onProgress: ((String) -> Unit)? = null): String? {
        for (name in BOOT_PARTITION_NAMES) {
            val partition = manifest.partitionsList.find { it.partitionName == name } ?: continue
            kernelKmi(partition, onProgress)?.let { return it }
        }
        return null
    }

    private fun kernelKmi(
        partition: PartitionUpdate,
        onProgress: ((String) -> Unit)? = null,
    ): String? {
        // The banner can sit deep (EFI PE GKI, ~45-55% into the kernel), so
        // probe the middle op first, then fall back to the sequential walk.
        probeKernelMiddle(partition, onProgress)?.let { return it }
        return walkKernelPrefix(partition, onProgress)
    }

    /**
     * Ops map to uncompressed ranges via dst extents, so only the op covering
     * the kernel middle needs to be fetched.
     */
    private fun probeKernelMiddle(
        partition: PartitionUpdate,
        onProgress: ((String) -> Unit)?,
    ): String? {
        val ranges = opRanges(partition)
        val firstIndex = ranges.indexOfFirst { it.first == 0L }
        if (firstIndex < 0) return null
        val first = partition.operationsList[firstIndex]
        if (first.dataLength <= 0 || first.getType() !in KMI_OPERATION_TYPES) return null

        // Locate the kernel block from the first op's header.
        val header = channel.readFully(
            dataBase + first.dataOffset,
            minOf(first.dataLength, HEADER_PROBE_LIMIT),
        ) ?: return null
        val headerChunk = decodeChunk(header, first.getType()) ?: return null
        val (kernelOffset, kernelSize) = BootKernelVersion.bootKernelBlock(headerChunk) ?: return null
        if (kernelSize <= 0) return null

        val target = kernelOffset.toLong() + kernelSize / 2L
        val middleIndex = ranges.indexOfFirst { it.first <= target && target < it.second }
        if (middleIndex < 0) return null
        val count = partition.operationsCount
        // The banner can sit just left of the middle op boundary; also cover the
        // previous op.
        for (index in middleIndex downTo maxOf(0, middleIndex - 1)) {
            val operation = partition.operationsList[index]
            if (operation.dataLength <= 0 || operation.getType() !in KMI_OPERATION_TYPES) continue
            onProgress?.invoke("- Analyzing boot partition ${index + 1}/$count")
            val data = channel.readFully(
                dataBase + operation.dataOffset,
                minOf(operation.dataLength, KMI_PROBE_LIMIT),
            ) ?: return null
            val chunk = decodeChunk(data, operation.getType()) ?: continue
            BootKernelVersion.scanKmi(chunk)?.let { return it }
        }
        return null
    }

    private fun walkKernelPrefix(
        partition: PartitionUpdate,
        onProgress: ((String) -> Unit)?,
    ): String? {
        val prefix = ByteArrayOutputStream()
        var scannedPrefix = 0
        val count = partition.operationsCount
        for ((index, operation) in partition.operationsList.withIndex()) {
            if (onProgress != null && (index % 5 == 0 || index == count - 1)) {
                onProgress("- Analyzing boot partition ${index + 1}/$count")
            }
            if (operation.dataLength <= 0 || operation.dstExtentsCount == 0) continue
            val type = operation.getType()
            if (type !in KMI_OPERATION_TYPES) continue

            val readLength = minOf(operation.dataLength, KMI_PROBE_LIMIT)
            val data = channel.readFully(dataBase + operation.dataOffset, readLength) ?: return null
            val chunk = decodeChunk(data, type) ?: return null
            prefix.write(chunk)
            BootKernelVersion.parseKmiFromBootData(prefix.toByteArray(), scannedPrefix)
                ?.let { return it }
            scannedPrefix = prefix.size()
            if (prefix.size() >= KMI_PROBE_LIMIT) return null
        }
        return null
    }

    private fun decodeChunk(data: ByteArray, type: InstallOperation.Type): ByteArray? {
        return when (type) {
            InstallOperation.Type.REPLACE -> data
            InstallOperation.Type.REPLACE_BZ, InstallOperation.Type.REPLACE_XZ ->
                decompressPrefix(data, type)

            else -> null
        }
    }

    private fun opRanges(partition: PartitionUpdate): List<Pair<Long, Long>> {
        var offset = 0L
        return partition.operationsList.map { operation ->
            val size = operation.dstExtentsList.sumOf { it.numBlocks } * manifest.blockSize
            val range = offset to (offset + size)
            offset += size
            range
        }
    }

    private fun decompressPrefix(data: ByteArray, type: InstallOperation.Type): ByteArray? {
        return try {
            val stream = when (type) {
                InstallOperation.Type.REPLACE_BZ -> BZip2CompressorInputStream(ByteArrayInputStream(data))
                InstallOperation.Type.REPLACE_XZ -> XZCompressorInputStream(ByteArrayInputStream(data))
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
            while (output.size() < KMI_PROBE_LIMIT) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, minOf(read, (KMI_PROBE_LIMIT - output.size()).toInt()))
            }
        } catch (_: IOException) {
            // Truncated stream: keep the prefix decompressed so far.
        }
        return if (output.size() > 0) output.toByteArray() else null
    }

    @Throws(IOException::class)
    private fun readPayloadHeader(): DeltaArchiveManifest {
        // The header of a payload is short and its lengths are 64-bit on the wire: a hostile file
        // can claim a 4 GiB manifest. Everything is range-checked before it is used as a size.
        var position = 0L

        fun readExact(size: Int): ByteArray {
            if (size < 0) throw IOException("Invalid payload: negative field size")
            val buffer = ByteBuffer.allocate(size)
            var total = 0
            while (total < size) {
                val read = channel.read(buffer, position + total)
                if (read < 0) throw IOException("Invalid payload: truncated header")
                if (read == 0) throw IOException("Invalid payload: stalled header read")
                total += read
            }
            position += size
            return buffer.array()
        }

        val magic = String(readExact(4), StandardCharsets.UTF_8)
        if (magic != "CrAU") {
            throw IOException("Invalid payload: invalid magic")
        }

        val version = ByteBuffer.wrap(readExact(8)).order(ByteOrder.BIG_ENDIAN).long
        if (version != 2L) {
            throw IOException("Invalid payload: unsupported version: $version")
        }

        val manifestLen = ByteBuffer.wrap(readExact(8)).order(ByteOrder.BIG_ENDIAN).long
        if (manifestLen <= 0 || manifestLen > MAX_MANIFEST_SIZE) {
            throw IOException("Invalid payload: manifest length $manifestLen out of range")
        }

        val manifestSigLen = ByteBuffer.wrap(readExact(4)).order(ByteOrder.BIG_ENDIAN).int.toLong()
        if (manifestSigLen < 0 || manifestSigLen > MAX_SIGNATURE_SIZE) {
            throw IOException("Invalid payload: signature length $manifestSigLen out of range")
        }

        val manifest = DeltaArchiveManifest.parseFrom(readExact(manifestLen.toInt()))

        position += manifestSigLen
        if (position > channel.size()) {
            throw IOException("Invalid payload: truncated after the manifest")
        }
        dataBase = position

        return manifest
    }

    @Throws(IOException::class)
    private fun findPartition(partitionName: String): PartitionUpdate {
        return manifest.partitionsList.find { it.partitionName == partitionName }
            ?: throw IOException("partition $partitionName not found in payload")
    }

    @Throws(IOException::class)
    private fun extractPartition(
        outputFile: File,
        partition: PartitionUpdate,
        console: (String) -> Unit,
    ): ByteArray {
        FileChannel.open(
            outputFile.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.READ,
            StandardOpenOption.TRUNCATE_EXISTING
        ).use { outChannel ->
            val size = partition.newPartitionInfo.size
            // A boot image is far smaller than this; the bound keeps a bogus manifest from
            // asking for an output file (and a mmap) that cannot exist.
            if (size <= 0 || size > MAX_PARTITION_SIZE) {
                throw IOException("Invalid payload: partition size $size out of range")
            }
            outChannel.write(ByteBuffer.allocate(1), size - 1)

            val count = partition.operationsCount
            partition.operationsList.forEachIndexed { index, operation ->
                if (index % 5 == 0 || index == count - 1) {
                    console("- Downloading ${index + 1}/$count")
                }
                processOperation(outChannel, operation, size)
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = outChannel.map(FileChannel.MapMode.READ_WRITE, 0, size)
            digest.update(buffer)
            return digest.digest()
        }
    }

    @Throws(IOException::class)
    private fun processOperation(
        outChannel: FileChannel,
        operation: InstallOperation,
        partitionSize: Long,
    ) {
        val dataType = operation.getType()
        if (dataType == InstallOperation.Type.ZERO) {
            return
        }

        if (operation.dataLength <= 0 || operation.dataLength > MAX_OPERATION_DATA) {
            throw IOException("Invalid payload: operation data length ${operation.dataLength}")
        }
        if (operation.dataOffset < 0) {
            throw IOException("Invalid payload: negative data offset")
        }

        val dataBuffer = ByteBuffer.allocate(operation.dataLength.toInt())
        channel.read(dataBuffer, dataBase + operation.dataOffset)
        dataBuffer.flip()

        val blockSize = manifest.blockSize
        if (blockSize <= 0) throw IOException("Invalid payload: block size $blockSize")
        val dstExtent = operation.getDstExtents(0)
        val outOffset = try {
            Math.multiplyExact(dstExtent.startBlock, blockSize)
        } catch (_: ArithmeticException) {
            throw IOException("Invalid payload: extent out of range")
        }
        if (outOffset < 0 || outOffset > partitionSize) {
            throw IOException("Invalid payload: extent at $outOffset is outside the partition")
        }

        when (dataType) {
            InstallOperation.Type.REPLACE -> {
                if (outOffset + dataBuffer.remaining() > partitionSize) {
                    throw IOException("Invalid payload: operation writes past the partition")
                }
                outChannel.write(dataBuffer, outOffset)
            }

            InstallOperation.Type.REPLACE_BZ, InstallOperation.Type.REPLACE_XZ -> {
                val inputStream = dataBuffer.array().inputStream()
                if (dataType == InstallOperation.Type.REPLACE_BZ) {
                    BZip2CompressorInputStream(inputStream)
                } else {
                    XZCompressorInputStream(inputStream)
                }.use { decompressor ->
                    val bytes = ByteArray(8192)
                    var bytesRead: Int
                    var bytesWritten = 0
                    while (decompressor.read(bytes).also { bytesRead = it } != -1) {
                        if (outOffset + bytesWritten + bytesRead > partitionSize) {
                            throw IOException("Invalid payload: operation writes past the partition")
                        }
                        val buffer = ByteBuffer.wrap(bytes, 0, bytesRead)
                        bytesWritten += outChannel.write(buffer, outOffset + bytesWritten)
                    }
                }
            }

            else -> throw IOException("Unsupported operation type: $dataType")
        }
    }

    companion object {
        // KMI comes from the kernel block; init_boot only carries the ramdisk.
        private val BOOT_PARTITION_NAMES = listOf("boot", "vendor_kernel_boot")
        private val KMI_OPERATION_TYPES = setOf(
            InstallOperation.Type.REPLACE,
            InstallOperation.Type.REPLACE_BZ,
            InstallOperation.Type.REPLACE_XZ,
        )
        private const val KMI_PROBE_LIMIT = 24L * 1024 * 1024
        private const val HEADER_PROBE_LIMIT = 64L * 1024

        // Payload header and operation sizes are attacker-controlled 64-bit fields; these bounds
        // are generous for a real OTA payload and small enough to allocate.
        private const val MAX_MANIFEST_SIZE = 64L * 1024 * 1024
        private const val MAX_SIGNATURE_SIZE = 1024L * 1024
        private const val MAX_OPERATION_DATA = 512L * 1024 * 1024
        private const val MAX_PARTITION_SIZE = 1024L * 1024 * 1024
    }
}
