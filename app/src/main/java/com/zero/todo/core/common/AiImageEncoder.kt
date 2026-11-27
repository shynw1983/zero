package com.zero.todo.core.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.zero.todo.data.ai.AiImagePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object AiImageEncoder {

    private const val CAPTURE_DIR_NAME = "ai_captures"

    fun createTempCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, CAPTURE_DIR_NAME).apply {
            if (!exists()) mkdirs()
            listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
        }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun encodeUriToJpegPayload(
        context: Context,
        uri: Uri,
        maxEdgePx: Int = 2048,
        quality: Int = 90
    ): AiImagePayload? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sampleSize = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sampleSize > maxEdgePx * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@runCatching null

            val scaled = scaleDownIfNeeded(decoded, maxEdgePx)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 95), baos)
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()

            val bytes = baos.toByteArray()
            AiImagePayload(
                base64Jpeg = Base64.encodeToString(bytes, Base64.NO_WRAP),
                mimeType = "image/jpeg"
            )
        }.getOrNull()
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxEdgePx: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = maxOf(w, h)
        if (longest <= maxEdgePx) return bitmap
        val ratio = maxEdgePx.toFloat() / longest.toFloat()
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
