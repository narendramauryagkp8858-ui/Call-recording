package com.example.billing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

object ImageStampHelper {

    suspend fun uriToCompactBase64(context: Context, uri: Uri, maxDim: Int = 320): String? =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val original = inputStream.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
                bitmapToCompactBase64(original, maxDim)
            } catch (_: Exception) {
                null
            }
        }

    fun bitmapToCompactBase64(bitmap: Bitmap, maxDim: Int = 320): String {
        val scale = min(
            maxDim.toFloat() / bitmap.width.coerceAtLeast(1),
            maxDim.toFloat() / bitmap.height.coerceAtLeast(1)
        ).coerceAtMost(1f)
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (targetW != bitmap.width || targetH != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 95, out)
        var bytes = out.toByteArray()
        if (bytes.size > 110_000) {
            val smaller = Bitmap.createScaledBitmap(
                scaled,
                (targetW * 0.65f).toInt().coerceAtLeast(64),
                (targetH * 0.65f).toInt().coerceAtLeast(64),
                true
            )
            out.reset()
            smaller.compress(Bitmap.CompressFormat.PNG, 90, out)
            bytes = out.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
