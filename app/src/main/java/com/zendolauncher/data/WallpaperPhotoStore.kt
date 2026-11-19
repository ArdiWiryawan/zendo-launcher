package com.zendolauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Persists a user-picked Home background photo inside app-private storage.
 *
 * Copying and downsampling into [filesDir] means the launcher never holds a stale
 * content URI grant and never decodes a multi-megapixel camera original on the UI thread.
 */
class WallpaperPhotoStore(private val context: Context) {

    private val photoFile: File
        get() = File(context.filesDir, FILE_NAME)

    fun hasPhoto(): Boolean = photoFile.exists() && photoFile.length() > 0L

    fun loadBitmap(): Bitmap? {
        val file = photoFile
        if (!file.exists()) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /**
     * Imports [uri], downsampling to at most [MAX_EDGE_PX] on the longest side and storing
     * it atomically as JPEG. Returns the decoded bitmap on success, or null on failure.
     */
    fun saveFromUri(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        }.getOrNull() ?: return null

        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        val saved = runCatching {
            FileOutputStream(tmp).use { out ->
                decoded.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (photoFile.exists()) photoFile.delete()
            tmp.renameTo(photoFile)
        }.getOrDefault(false)

        return if (saved) decoded else {
            tmp.delete()
            decoded.recycle()
            null
        }
    }

    fun clear() {
        runCatching { photoFile.delete() }
    }

    companion object {
        private const val FILE_NAME = "home_wallpaper.jpg"
        private const val MAX_EDGE_PX = 1600

        internal fun computeInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
            var sample = 1
            val longest = maxOf(width, height)
            while (longest / sample > maxEdge) {
                sample *= 2
            }
            return sample.coerceAtLeast(1)
        }
    }
}
