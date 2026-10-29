package com.fouad.cuttingnotebook.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Model photos live in the app's own files directory (spec 4): a picked image is copied in
 * rather than referenced, so the model keeps its photo when the gallery changes or the
 * original is deleted.
 */
class ImageStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, DIR_NAME)

    /** Copies and downscales [source], returning the stored file's name, or null on failure. */
    suspend fun importImage(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            val bitmap = decodeScaled(source) ?: return@runCatching null
            val name = "${UUID.randomUUID()}.jpg"
            FileOutputStream(File(dir, name)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            name
        }.getOrNull()
    }

    /** Absolute file for a stored name, whether or not it still exists on disk. */
    fun fileFor(name: String): File = File(dir, name)

    /** A missing photo must not break the screen, so callers check before rendering. */
    fun exists(name: String?): Boolean = name != null && fileFor(name).exists()

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        runCatching { fileFor(name).delete() }
        Unit
    }

    /** Every stored photo, used when a backup is written. */
    fun listAll(): List<File> = dir.listFiles()?.toList().orEmpty()

    // ---- restore support ----------------------------------------------------------------
    //
    // A restore writes the incoming photos to a staging directory first, then swaps it into
    // place only once the database transaction has committed. If anything fails before that,
    // the staging directory is dropped and the operator's existing photos are untouched.

    private val stagingDir = File(appContext.filesDir, "$DIR_NAME-staging")
    private val retiredDir = File(appContext.filesDir, "$DIR_NAME-retired")

    suspend fun beginStaging() = withContext(Dispatchers.IO) {
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        Unit
    }

    suspend fun stage(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        stagingDir.mkdirs()
        File(stagingDir, name).writeBytes(bytes)
    }

    suspend fun discardStaging() = withContext(Dispatchers.IO) {
        stagingDir.deleteRecursively()
        Unit
    }

    /** Swaps the staged photos in. Returns false and leaves the old set in place on failure. */
    suspend fun commitStaging(): Boolean = withContext(Dispatchers.IO) {
        retiredDir.deleteRecursively()
        val hadExisting = dir.exists()
        if (hadExisting && !dir.renameTo(retiredDir)) return@withContext false
        if (!stagingDir.renameTo(dir)) {
            // Put the operator's photos back exactly as they were.
            if (hadExisting) retiredDir.renameTo(dir)
            return@withContext false
        }
        retiredDir.deleteRecursively()
        true
    }

    private fun decodeScaled(source: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        if (longestEdge <= 0) return null
        var sample = 1
        while (longestEdge / sample > MAX_EDGE_PX) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = appContext.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return applyExifRotation(source, decoded)
    }

    /** Phone cameras record orientation in EXIF rather than in the pixels. */
    private fun applyExifRotation(source: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            appContext.contentResolver.openInputStream(source)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private companion object {
        const val DIR_NAME = "model_images"
        const val MAX_EDGE_PX = 1600
    }
}
