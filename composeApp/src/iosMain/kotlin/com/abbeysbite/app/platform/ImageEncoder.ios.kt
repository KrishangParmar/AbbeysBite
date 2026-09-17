package com.abbeysbite.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

actual fun ImageBitmap.encodeToPng(): ByteArray {
    val image = Image.makeFromBitmap(asSkiaBitmap())
    val data = image.encodeToData(EncodedImageFormat.PNG)
        ?: return ByteArray(0)
    return data.bytes
}
