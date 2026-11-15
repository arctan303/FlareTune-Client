package com.flaretune.client

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.memory.MemoryCache
import com.flaretune.client.playback.CoilBitmapLoader
import com.flaretune.client.playback.CoverArtworkPrewarmer
import com.flaretune.client.playback.PLAYER_PREWARM_ARTWORK_PX
import com.flaretune.client.playback.THUMB_ARTWORK_PX
import com.flaretune.client.playback.collectNeighborCoverUrls
import com.flaretune.client.playback.coverMemoryCacheKey
import com.flaretune.client.playback.toSafeSoftwareBitmap
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoilBitmapLoaderTest {

    private fun <T> awaitFutureWithMainLooper(future: ListenableFuture<T>, timeoutMs: Long = 5_000L): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!future.isDone && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10L)
        }
        shadowOf(Looper.getMainLooper()).idle()
        if (!future.isDone) throw TimeoutException("Future did not complete within ${timeoutMs}ms")
        return future.get(1, TimeUnit.SECONDS)
    }

    private fun testPngBytes(color: Int = Color.MAGENTA): ByteArray {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
            out.toByteArray()
        }
    }

    @Test
    fun loadBitmap_hitsMemoryCacheSynchronouslyViaImmediateFuture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val memoryCache = MemoryCache.Builder(context).maxSizeBytes(4 * 1024 * 1024).build()
        val imageLoader = ImageLoader.Builder(context).memoryCache { memoryCache }.build()
        val coverUrl = "https://music.example.com/covers/album-42"
        val cachedBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        memoryCache[coverMemoryCacheKey(coverUrl, PLAYER_PREWARM_ARTWORK_PX)] = MemoryCache.Value(cachedBitmap)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val loader = CoilBitmapLoader(context, imageLoaderProvider = { imageLoader }, scope = scope)
            val future = loader.loadBitmap(Uri.parse(coverUrl))
            assertTrue("Memory cache hit must complete synchronously", future.isDone)
            val result = future.get()
            assertNotSame("Returned bitmap must be an independent copy", cachedBitmap, result)
            assertFalse(result.isRecycled)
            assertEquals(Color.CYAN, result.getPixel(0, 0))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                assertFalse(result.config == Bitmap.Config.HARDWARE)
            }
            // Recycling notification copy must not corrupt cachedBitmap
            result.recycle()
            assertFalse(cachedBitmap.isRecycled)
        } finally {
            scope.cancel()
            imageLoader.shutdown()
        }
    }

    @Test
    fun loadBitmap_fallsBackToThumbKeyMatchAndCopiesSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val memoryCache = MemoryCache.Builder(context).maxSizeBytes(4 * 1024 * 1024).build()
        val imageLoader = ImageLoader.Builder(context).memoryCache { memoryCache }.build()
        val coverUrl = "https://music.example.com/covers/thumb-hit"
        val thumbBitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        memoryCache[coverMemoryCacheKey(coverUrl, THUMB_ARTWORK_PX)] = MemoryCache.Value(thumbBitmap)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val loader = CoilBitmapLoader(context, imageLoaderProvider = { imageLoader }, scope = scope)
            val future = loader.loadBitmap(Uri.parse(coverUrl))
            assertTrue(future.isDone)
            assertEquals(Color.GREEN, future.get().getPixel(4, 4))
        } finally {
            scope.cancel()
            imageLoader.shutdown()
        }
    }

    @Test
    fun toSafeSoftwareBitmap_rejectsNullAndRecycled() {
        assertNull(toSafeSoftwareBitmap(null))
        val recycled = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { recycle() }
        assertNull(toSafeSoftwareBitmap(recycled))
    }

    @Test
    fun loadBitmap_usesFallbackLoaderWhenImageLoaderUnavailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fallbackBmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        var called = false
        val fallback = object : BitmapLoader {
            override fun supportsMimeType(mimeType: String): Boolean = mimeType == "image/png"
            override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = Futures.immediateFuture(fallbackBmp)
            override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
                called = true
                return Futures.immediateFuture(fallbackBmp)
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val loader = CoilBitmapLoader(
                context = context,
                imageLoaderProvider = { null },
                fallbackLoaderProvider = { fallback },
                scope = scope,
            )
            assertTrue(loader.supportsMimeType("image/png"))
            val result = loader.loadBitmap(Uri.parse("https://music.example.com/covers/1")).get()
            assertTrue(called)
            assertEquals(fallbackBmp, result)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun loadBitmap_loadsAsyncViaImageLoaderAndPopulatesMemoryCache() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pngBytes = testPngBytes(Color.BLUE)
        val networkCalls = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                networkCalls.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(pngBytes.toResponseBody("image/png".toMediaType()))
                    .build()
            })
            .build()
        val memoryCache = MemoryCache.Builder(context).maxSizeBytes(4 * 1024 * 1024).build()
        val imageLoader = ImageLoader.Builder(context)
            .okHttpClient(client)
            .memoryCache { memoryCache }
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val coverUrl = "https://music.example.com/covers/async-cover"
        try {
            val loader = CoilBitmapLoader(context, imageLoaderProvider = { imageLoader }, scope = scope)
            val bitmap = awaitFutureWithMainLooper(loader.loadBitmap(Uri.parse(coverUrl)))
            assertNotNull(bitmap)
            assertFalse(bitmap.isRecycled)
            assertEquals(1, networkCalls.get())

            // Second load with another lyric line must hit memory cache synchronously with zero network calls
            val secondFuture = loader.loadBitmap(Uri.parse(coverUrl))
            assertTrue(secondFuture.isDone)
            assertEquals(1, networkCalls.get())
        } finally {
            scope.cancel()
            imageLoader.shutdown()
        }
    }

    @Test
    fun collectNeighborCoverUrls_deduplicatesAndHandlesUnsetIndicesAndSharedCovers() {
        val covers = listOf(
            "https://music.example.com/covers/album-a",
            "https://music.example.com/covers/album-a", // shared with index 0
            "  ",
            "https://music.example.com/covers/album-b",
        )
        val collected = collectNeighborCoverUrls(
            currentIndex = 0,
            previousIndex = 3,
            nextIndex = 1,
            mediaItemCount = covers.size,
        ) { covers[it] }
        assertEquals(
            listOf(
                "https://music.example.com/covers/album-a",
                "https://music.example.com/covers/album-b",
            ),
            collected,
        )

        val withUnset = collectNeighborCoverUrls(
            currentIndex = 1,
            previousIndex = C.INDEX_UNSET,
            nextIndex = 2,
            mediaItemCount = covers.size,
        ) { covers[it] }
        assertEquals(listOf("https://music.example.com/covers/album-a"), withUnset)

        assertTrue(collectNeighborCoverUrls(0, C.INDEX_UNSET, C.INDEX_UNSET, 0) { null }.isEmpty())
    }

    @Test
    fun coverArtworkPrewarmer_dedupsInFlightAndSkipsAlreadyCached() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pngBytes = testPngBytes(Color.RED)
        val hits = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                hits.incrementAndGet()
                Thread.sleep(80L)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(pngBytes.toResponseBody("image/png".toMediaType()))
                    .build()
            })
            .build()
        val memoryCache = MemoryCache.Builder(context).maxSizeBytes(4 * 1024 * 1024).build()
        val imageLoader = ImageLoader.Builder(context)
            .okHttpClient(client)
            .memoryCache { memoryCache }
            .build()
        val cachedUrl = "https://music.example.com/covers/warm"
        val warmBmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        memoryCache[coverMemoryCacheKey(cachedUrl, PLAYER_PREWARM_ARTWORK_PX)] = MemoryCache.Value(warmBmp)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prewarmer = CoverArtworkPrewarmer(context, { imageLoader }, scope)
        val coldUrl = "https://music.example.com/covers/cold"
        try {
            prewarmer.prewarmUrls(listOf(cachedUrl, coldUrl))
            prewarmer.prewarmUrls(listOf(cachedUrl, coldUrl)) // duplicate call while in flight
            val deadline = System.currentTimeMillis() + 3_000L
            while (memoryCache[coverMemoryCacheKey(coldUrl, PLAYER_PREWARM_ARTWORK_PX)] == null && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(20L)
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertNotNull(memoryCache[coverMemoryCacheKey(coldUrl, PLAYER_PREWARM_ARTWORK_PX)])
            assertEquals("Only cold URL should trigger one network request", 1, hits.get())
        } finally {
            prewarmer.clear()
            scope.cancel()
            imageLoader.shutdown()
        }
    }
}
