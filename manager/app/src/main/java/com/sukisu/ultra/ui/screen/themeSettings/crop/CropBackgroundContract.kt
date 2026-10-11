package com.sukisu.ultra.ui.screen.themeSettings.crop

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import java.io.File

/**
 * Runs the wallpaper cropper and hands back the cropped file.
 *
 * The picker returns whatever the user chose, at whatever size and aspect; this is what lets
 * them decide the framing themselves instead of the app choosing a centre crop for them.
 *
 * The result is written to a cache file owned by this app, so the caller can read it back
 * without needing a persisted permission for the original document. The file is deleted on
 * failure and left for the caller to delete once it has been consumed.
 */
class CropBackgroundContract : ActivityResultContract<CropBackgroundContract.Input, Uri?>() {

    /**
     * @param source the image to crop.
     * @param aspectRatioX width of the target frame, or 0 for a free crop.
     * @param aspectRatioY height of the target frame, or 0 for a free crop.
     */
    data class Input(
        val source: Uri,
        val aspectRatioX: Float = 0f,
        val aspectRatioY: Float = 0f,
    )

    override fun createIntent(context: Context, input: Input): Intent {
        val output = cropOutputFile(context)
        return Intent(context, BackgroundCropActivity::class.java)
            .putExtra(UCrop.EXTRA_INPUT_URI, input.source)
            .putExtra(UCrop.EXTRA_OUTPUT_URI, FileProvider.getUriForFile(context, authority(context), output))
            .putExtra(UCrop.EXTRA_ASPECT_RATIO_X, input.aspectRatioX)
            .putExtra(UCrop.EXTRA_ASPECT_RATIO_Y, input.aspectRatioY)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        val extra = intent ?: return null
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            extra.getParcelableExtra(UCrop.EXTRA_OUTPUT_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            extra.getParcelableExtra(UCrop.EXTRA_OUTPUT_URI)
        }
    }

    private fun cropOutputFile(context: Context): File {
        val dir = cropDir(context).apply { mkdirs() }
        return File(dir, "crop_${System.currentTimeMillis()}.png")
    }

    private fun authority(context: Context): String = "${context.packageName}.fileprovider"

    companion object {
        const val CROP_DIR = "wallpaper_crop"

        /** The cache directory holding cropped output. */
        fun cropDir(context: Context): File = File(context.cacheDir, CROP_DIR)

        /**
         * Deletes every scratch crop.
         *
         * Called once the result has been taken into internal storage, and when a crop is
         * cancelled. A crop is only ever read immediately after it is produced, so nothing
         * here needs to survive.
         */
        fun clearCache(context: Context) {
            cropDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
