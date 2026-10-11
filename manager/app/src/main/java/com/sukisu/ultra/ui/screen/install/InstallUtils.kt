package com.sukisu.ultra.ui.screen.install

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import com.sukisu.ultra.R

@Parcelize
sealed class InstallMethod : Parcelable {
    data class SelectFile(
        val uri: Uri? = null,
        @get:StringRes override val label: Int = R.string.select_file,
        override val summary: String?
    ) : InstallMethod()

    data class DownloadFile(
        val url: String? = null,
        val partition: String? = null,
        @get:StringRes override val label: Int = R.string.download_file,
        override val summary: String?
    ) : InstallMethod()

    data object DirectInstall : InstallMethod() {
        override val label: Int
            get() = R.string.direct_install
    }

    data object DirectInstallToInactiveSlot : InstallMethod() {
        override val label: Int
            get() = R.string.install_inactive_slot
    }

    /**
     * An AnyKernel3 archive the user picks.
     *
     * The row is labelled "AnyKernel3 Kernel" and runs the whole AnyKernel3 flow - slot
     * selection, then confirmation, then anykernel.sh against the boot image. A second row
     * named AnyKernel3 was added alongside it and did the same thing, so the install list
     * offered the same entry twice; this one is kept because the intent dispatcher and the
     * kernel-flash screen are both built around it.
     */
    data class HorizonKernel(
        val uri: Uri? = null,
        val slot: String? = null,
        @get:StringRes override val label: Int = R.string.horizon_kernel,
        override val summary: String? = null
    ) : InstallMethod()

    abstract val label: Int

    @IgnoredOnParcel
    open val summary: String? = null
}

/*
 * Declared on the nullable receiver so a call site holding an `InstallMethod?` needs no `?.`.
 * A null method is simply not an archive.
 */

/** The uri of the archive row, or null for the methods that carry no archive. */
val InstallMethod?.archiveUri: Uri?
    get() = (this as? InstallMethod.HorizonKernel)?.uri

/** The slot of the archive row, or null when none is recorded. */
val InstallMethod?.archiveSlot: String?
    get() = (this as? InstallMethod.HorizonKernel)?.slot

/** True for the archive row, which owns the slot, KPM and confirmation steps. */
val InstallMethod?.isKernelArchive: Boolean
    get() = this is InstallMethod.HorizonKernel

/**
 * The same archive with a slot recorded.
 *
 * The slot dialog hands back only a slot string, so the archive has to be rebuilt. Rebuilding
 * it as a fixed type is what once made the install list lose its selection mark: the rebuilt
 * value no longer matched the row the user had picked.
 */
fun InstallMethod.withArchiveSlot(slot: String?): InstallMethod = when (this) {
    is InstallMethod.HorizonKernel -> copy(slot = slot)
    else -> this
}

fun isKoFile(context: Context, uri: Uri): Boolean {
    val seg = uri.lastPathSegment ?: ""
    if (seg.endsWith(".ko", ignoreCase = true)) return true

    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(idx)
                name?.endsWith(".ko", ignoreCase = true) == true
            } else {
                false
            }
        } ?: false
    } catch (_: Throwable) {
        false
    }
}
