package com.ludzik.ar.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Zapis zdjęć i filmów do galerii (MediaStore). */
object MediaSaver {
    private const val ALBUM = "LudzikAR"

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Zamienia surowe piksele z glReadPixels (odwrócone w pionie) na bitmapę. */
    fun bitmapFromGl(pixels: ByteBuffer, width: Int, height: Int): Bitmap {
        val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        pixels.position(0)
        raw.copyPixelsFromBuffer(pixels)
        val flip = Matrix().apply { preScale(1f, -1f) }
        val out = Bitmap.createBitmap(raw, 0, 0, width, height, flip, false)
        raw.recycle()
        return out
    }

    fun saveImage(context: Context, bitmap: Bitmap): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "LudzikAR_${stamp()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    fun saveVideo(context: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "LudzikAR_${stamp()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        } finally {
            file.delete()
        }
    }
}
