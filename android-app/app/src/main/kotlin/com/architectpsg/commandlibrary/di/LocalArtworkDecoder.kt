package com.architectpsg.commandlibrary.di

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.feature.shell.CardArtworkAccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Memory-only, bounds-first platform decode. No resource density or resampling/transforms. */
internal object LocalArtworkDecoder {
    suspend fun decode(bytes: ByteArray, variant: String, cancel: CancelToken): Bitmap {
        checkCancellation(cancel)
        require(signature(bytes, variant))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        checkCancellation(cancel)
        require(dimensions(bounds.outWidth, bounds.outHeight))
        require(bounds.outMimeType == if (variant == "normal") "image/jpeg" else "image/webp")
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false; inSampleSize = 1; inDensity = 0; inTargetDensity = 0
        }
        var owned: Bitmap? = null
        try {
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
            owned = bitmap
            checkCancellation(cancel)
            require(dimensions(bitmap.width, bitmap.height) && bitmap.width == bounds.outWidth && bitmap.height == bounds.outHeight)
            require(bitmap.config == Bitmap.Config.ARGB_8888 && bitmap.allocationByteCount.toLong() in 1..8L * 1024 * 1024)
            owned = null
            return bitmap
        } finally { owned?.recycle() }
    }

    private suspend fun checkCancellation(cancel: CancelToken) {
        currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
    }

    private fun dimensions(width: Int, height: Int) = width in 1..CardArtworkAccess.MAX_EDGE &&
        height in 1..CardArtworkAccess.MAX_EDGE && width.toLong() * height <= CardArtworkAccess.MAX_PIXELS

    fun signature(bytes: ByteArray, variant: String): Boolean {
        if (bytes.isEmpty() || bytes.size.toLong() > CardArtworkAccess.MAX_COMPRESSED_BYTES) return false
        if (variant == "normal") return bytes.size >= 4 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
            bytes[2] == 0xff.toByte() && bytes[bytes.size - 2] == 0xff.toByte() && bytes.last() == 0xd9.toByte()
        if (variant != "grid" || bytes.size < 20 || tag(bytes, 0) != "RIFF" || tag(bytes, 8) != "WEBP" ||
            unsigned32(bytes, 4) + 8 != bytes.size.toLong()) return false
        var offset = 12
        var image = false
        var extended = false
        while (offset < bytes.size) {
            if (offset + 8 > bytes.size) return false
            val chunk = tag(bytes, offset)
            val length = unsigned32(bytes, offset + 4)
            val end = offset.toLong() + 8 + length
            if (length == 0L || end > bytes.size || end + (length and 1L) > bytes.size) return false
            when (chunk) {
                "VP8X" -> {
                    // Strict static profile: no animation, alpha, metadata or unknown flag/chunk.
                    if (offset != 12 || extended || length != 10L || (0..3).any { bytes[offset + 8 + it] != 0.toByte() }) return false
                    extended = true
                }
                "VP8 ", "VP8L" -> { if (image) return false; image = true }
                else -> return false
            }
            if ((length and 1L) == 1L && bytes[end.toInt()] != 0.toByte()) return false
            offset = (end + (length and 1L)).toInt()
        }
        return image && offset == bytes.size
    }

    private fun tag(bytes: ByteArray, offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
    private fun unsigned32(bytes: ByteArray, offset: Int): Long = (0..3).fold(0L) { value, i ->
        value or ((bytes[offset + i].toLong() and 255) shl (i * 8))
    }
}
