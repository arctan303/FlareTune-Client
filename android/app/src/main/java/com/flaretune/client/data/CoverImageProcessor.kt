package com.flaretune.client.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

fun interface CoverImageProcessor {
    /** Transient picker URI in, metadata-free 1024 square WebP out. Never persists the URI. */
    fun prepare(sourceUri: String): ByteArray
}

class AndroidCoverImageProcessor(private val context: Context) : CoverImageProcessor {
    override fun prepare(sourceUri: String): ByteArray {
        try {
            val uri = Uri.parse(sourceUri)
            if (uri.scheme != "content" || uri.authority.isNullOrBlank()) throw ApiFailure(Failure.REQUEST)
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_SOURCE_BYTES) throw ApiFailure(Failure.REQUEST)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw ApiFailure(Failure.REQUEST)
            return encode(bytes)
        } catch (error: ApiFailure) { throw error }
        catch (_: Exception) { throw ApiFailure(Failure.REQUEST) }
    }

    internal fun encode(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty() || bytes.size > MAX_SOURCE_BYTES) throw ApiFailure(Failure.REQUEST)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..32768 || bounds.outHeight !in 1..32768) throw ApiFailure(Failure.REQUEST)
        val bitmaps = linkedSetOf<Bitmap>()
        try {
            var decoded = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = (2048.0 / maxOf(info.size.width, info.size.height)).coerceAtMost(1.0)
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            } else {
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                while (maxOf(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= 2048)
                    options.inSampleSize *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: throw ApiFailure(Failure.REQUEST)
                bitmaps += bitmap
                orient(bitmap, bytes)
            }
            bitmaps += decoded
            val size = minOf(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(decoded, (decoded.width - size) / 2, (decoded.height - size) / 2, size, size)
            bitmaps += square
            decoded = Bitmap.createScaledBitmap(square, 1024, 1024, true)
            bitmaps += decoded
            val output = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            if (!decoded.compress(format, 90, output) || output.size() !in 1..MAX_COVER_BYTES)
                throw ApiFailure(Failure.REQUEST)
            return output.toByteArray()
        } finally { bitmaps.forEach { if (!it.isRecycled) it.recycle() } }
    }

    @Suppress("DEPRECATION")
    private fun orient(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        // API23 has only the filename constructor. The private temporary is always removed.
        val temporary = File.createTempFile("cover-", ".image", context.cacheDir)
        val orientation = try {
            temporary.writeBytes(bytes)
            ExifInterface(temporary.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        finally { temporary.delete() }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val MAX_SOURCE_BYTES = 20 * 1024 * 1024
        const val MAX_COVER_BYTES = 5 * 1024 * 1024
    }
}
