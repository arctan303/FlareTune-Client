package com.flaretune.client

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.flaretune.client.data.AndroidCoverImageProcessor
import com.flaretune.client.data.ApiFailure
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverImageProcessorTest {
    private val processor get() = AndroidCoverImageProcessor(RuntimeEnvironment.getApplication())

    @Test fun realNativeBitmapIsCenterCroppedToStaticSquareWebpWithinHostLimit() {
        val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        val input = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val output = processor.encode(input)
        assertTrue(output.size <= 5 * 1024 * 1024)
        assertEquals("RIFF", output.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WEBP", output.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        val decoded = BitmapFactory.decodeByteArray(output, 0, output.size)
        try { assertEquals(1024, decoded.width); assertEquals(1024, decoded.height) }
        finally { decoded.recycle() }
    }

    @Test fun externalNetworkFileAndMalformedInputsAreRejectedBeforeAnyAuthenticatedRequest() {
        for (uri in listOf("https://other.example/image", "file:///private/image", "data:image/png;base64,AA==")) {
            try { processor.prepare(uri); fail("Expected content URI only") } catch (_: ApiFailure) { }
        }
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2, 3), ByteArray(20 * 1024 * 1024 + 1))) {
            try { processor.encode(bytes); fail("Expected invalid image rejection") } catch (_: ApiFailure) { }
        }
    }

    @Test
    @Config(sdk = [23])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun api23ValidPickedPngTraversesLegacyDecodeCropAndTemporaryExifCleanup() {
        assertEquals(23, Build.VERSION.SDK_INT)
        val context = RuntimeEnvironment.getApplication()
        val input = png(4608, 1024)
        val uri = Uri.parse("content://cover-fixture/api23")
        shadowOf(context.contentResolver).registerInputStream(uri, input.inputStream())
        val priorTemporaryFiles = context.cacheDir.listFiles().orEmpty().map { it.name }.toSet()

        val output = processor.prepare(uri.toString())

        assertTrue(output.isNotEmpty())
        assertTrue(output.size <= 5 * 1024 * 1024)
        val decoded = BitmapFactory.decodeByteArray(output, 0, output.size)
        try { assertEquals(1024, decoded.width); assertEquals(1024, decoded.height) }
        finally { decoded.recycle() }
        assertEquals(priorTemporaryFiles, context.cacheDir.listFiles().orEmpty().map { it.name }.toSet())
        // SDK23 uses Robolectric LEGACY: its ImageUtil substitutes PNG for WEBP.
        // This covers the actual old decode/crop path; the SDK27 NATIVE test verifies WebP bytes.
    }

    @Test
    @Config(sdk = [27])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun api27LegacyDecodeUsesPositiveSamplingAndProducesRealWebp() {
        assertEquals(27, Build.VERSION.SDK_INT)
        val output = processor.encode(png(4608, 1024))
        assertTrue(output.size <= 5 * 1024 * 1024)
        assertEquals("RIFF", output.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WEBP", output.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        val decoded = BitmapFactory.decodeByteArray(output, 0, output.size)
        try { assertEquals(1024, decoded.width); assertEquals(1024, decoded.height) }
        finally { decoded.recycle() }
    }

    private fun png(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.BLUE)
            val output = ByteArrayOutputStream()
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            return output.toByteArray()
        } finally { bitmap.recycle() }
    }
}
