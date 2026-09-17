package com.abbeysbite.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.abbeysbite.app.core.config.AppConfig
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidMediaPicker(
    private val context: Context,
) : MediaPicker {

    override suspend fun capturePhoto(): PickedImage? {
        val host = ActivityResultBridge.host ?: return null
        if (!host.ensureCameraPermission()) return null
        val uri = host.capturePhoto() ?: return null
        return compress(uri)
    }

    override suspend fun pickPhoto(): PickedImage? {
        val host = ActivityResultBridge.host ?: return null
        val uri = host.pickPhoto() ?: return null
        return compress(uri)
    }

    override suspend fun cameraPermissionStatus(): PermissionStatus {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return if (granted) PermissionStatus.GRANTED else PermissionStatus.NOT_DETERMINED
    }

    /** Downscales to AppConfig.Ai.IMAGE_MAX_DIMENSION and JPEG-compresses. */
    private suspend fun compress(uri: Uri): PickedImage? = withContext(Dispatchers.IO) {
        runCatching {
            // Bounds pass
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val maxDim = AppConfig.Ai.IMAGE_MAX_DIMENSION
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching null

            // Respect EXIF orientation so the photo isn't sideways.
            val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            var bitmap = decoded
            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }

            // Final exact downscale
            val scale = minOf(
                1f,
                maxDim.toFloat() / bitmap.width,
                maxDim.toFloat() / bitmap.height,
            )
            if (scale < 1f) {
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            }

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, AppConfig.Ai.IMAGE_JPEG_QUALITY, out)
            PickedImage(out.toByteArray(), bitmap.width, bitmap.height)
        }.getOrNull()
    }
}
