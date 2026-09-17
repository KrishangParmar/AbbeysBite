package com.abbeysbite.app.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Encodes a composed [ImageBitmap] to PNG bytes for the share sheet. */
expect fun ImageBitmap.encodeToPng(): ByteArray
