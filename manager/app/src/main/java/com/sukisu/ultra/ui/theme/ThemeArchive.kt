package com.sukisu.ultra.ui.theme

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The on-disk format of a `.fpt` theme archive.
 *
 * FolkPatch wraps the ZIP in AES/CBC and puts the 16-byte IV in front of the ciphertext, so a
 * theme exported there is not a readable ZIP at all. HyperSU keeps that container verbatim -
 * the same passphrase, the same layout, `theme.json` inside - so a theme travels between the
 * two managers in either direction.
 *
 * [LegacyPlainZip] exists because the first HyperSU release wrote a plain ZIP named
 * `theme_config.json`. Reading it keeps archives exported by that build importable; writing it
 * is never done.
 */
internal object ThemeArchive {
    private const val TAG = "ThemeArchive"

    /** FolkPatch's passphrase; the AES key is its SHA-256 digest. */
    private const val KEY_STR = "FolkPatchThemeSecretKey2025"

    private const val IV_SIZE = 16

    /**
     * Caps for one extracted entry and the whole archive.
     *
     * A theme carries wallpapers, a font and short audio files; these limits are far above any
     * real theme and keep a crafted archive from filling the partition or the heap.
     */
    private const val MAX_ENTRY_BYTES = 256L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024

    /** The entry holding the theme JSON, named to match FolkPatch. */
    const val CONFIG_ENTRY = "theme.json"

    /** Entry name used by the first HyperSU release, still read for compatibility. */
    const val LEGACY_CONFIG_ENTRY = "theme_config.json"

    /** Which container an archive turned out to use. */
    enum class Format { Encrypted, LegacyPlainZip, Unknown }

    private fun secretKey(): SecretKey =
        SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(KEY_STR.toByteArray()), "AES")

    /** Writes [entries] into [uri] as an encrypted archive. */
    fun write(uri: OutputStream, entries: (ZipOutputStream) -> Unit) {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(), IvParameterSpec(iv))

        // The IV is written in the clear; the reader needs it before it can decrypt anything.
        uri.write(iv)
        CipherOutputStream(uri, cipher).use { encrypted ->
            ZipOutputStream(BufferedOutputStream(encrypted)).use { zip -> entries(zip) }
        }
    }

    /**
     * Sniffs the container without decrypting the whole archive.
     *
     * A ZIP always starts with "PK"; anything else is either an encrypted archive or not a theme
     * at all. Cheap enough to run before the user is asked to confirm an import.
     */
    fun detect(context: Context, uri: Uri): Format {
        val header = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(4)
                val read = input.read(buf)
                if (read < 2) ByteArray(0) else buf.copyOf(read)
            } ?: return Format.Unknown
        } catch (e: Exception) {
            Log.w(TAG, "failed to read archive header: ${e.message}")
            return Format.Unknown
        }

        return when {
            header.size >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte() ->
                Format.LegacyPlainZip
            header.size >= 2 -> Format.Encrypted
            else -> Format.Unknown
        }
    }

    /**
     * Opens [uri] for reading as a ZIP stream, transparently decrypting when needed.
     *
     * The caller closes the returned stream. A wrong key or a truncated file surfaces as an
     * exception from the stream, which the caller reports as "not a valid theme".
     */
    fun openZipStream(
        context: Context,
        uri: Uri,
        format: Format,
    ): InputStream? {
        val raw = context.contentResolver.openInputStream(uri) ?: return null

        if (format != Format.Encrypted) return BufferedInputStream(raw)

        return try {
            val iv = ByteArray(IV_SIZE)
            var read = 0
            while (read < IV_SIZE) {
                val n = raw.read(iv, read, IV_SIZE - read)
                if (n <= 0) {
                    raw.close()
                    return null
                }
                read += n
            }

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), IvParameterSpec(iv))
            BufferedInputStream(CipherInputStream(raw, cipher))
        } catch (e: Exception) {
            Log.w(TAG, "failed to open encrypted archive: ${e.message}")
            runCatching { raw.close() }
            null
        }
    }

    /** Copies every entry of the archive into [targetDir], returning the entry names written. */
    fun extractTo(context: Context, uri: Uri, targetDir: File): List<String> {
        val format = detect(context, uri)
        if (format == Format.Unknown) return emptyList()

        val written = mutableListOf<String>()
        val stream = openZipStream(context, uri, format) ?: return emptyList()

        var totalBytes = 0L
        stream.use { input ->
            ZipInputStream(input).use { zip ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!isSafeEntryName(name)) continue

                    val target = File(targetDir, name)
                    target.parentFile?.mkdirs()
                    var entryBytes = 0L
                    FileOutputStream(target).use { out ->
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            if (read == 0) throw java.io.IOException("stalled theme entry: $name")
                            entryBytes += read
                            totalBytes += read
                            if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_TOTAL_BYTES) {
                                throw java.io.IOException("theme archive is too large")
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                    written += name
                }
            }
        }
        return written
    }

    /**
     * Rejects anything that could escape [targetDir] or that we never write ourselves.
     *
     * A leading slash, a backslash, a `..` segment or a doubled slash would all place a file
     * outside the intended directory on some platforms.
     */
    fun isSafeFileName(name: String): Boolean =
        isSafeEntryName(name) && '/' !in name && name != "."

    fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank() || '\u0000' in name) return false
        if (name.startsWith("/") || name.startsWith("\\")) return false
        if (name.contains("\\")) return false
        if (name.contains("//")) return false
        return name.split('/').none { it == ".." || it.isEmpty() }
    }
}
