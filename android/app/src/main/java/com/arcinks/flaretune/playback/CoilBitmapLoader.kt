package com.arcinks.flaretune.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.SuccessResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Media3 [BitmapLoader] backed by the active account's [ImageLoader] (memory + account-isolated disk cache).
 * Memory cache hits return synchronously via [Futures.immediateFuture] so system media notifications
 * render artwork on the first frame without flashing the small default icon.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal class CoilBitmapLoader(
    private val context: Context,
    private val imageLoaderProvider: () -> ImageLoader?,
    private val fallbackLoaderProvider: () -> BitmapLoader? = { null },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val targetPx: Int = PLAYER_PREWARM_ARTWORK_PX,
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean =
        fallbackLoaderProvider()?.supportsMimeType(mimeType) ?: Util.isBitmapFactorySupportedMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        fallbackLoaderProvider()?.let { return it.decodeBitmap(data) }
        val future = SettableFuture.create<Bitmap>()
        val job = scope.launch {
            try {
                val decoded = BitmapFactory.decodeByteArray(data, 0, data.size)
                val safe = toSafeSoftwareBitmap(decoded)
                if (safe != null) {
                    future.set(safe)
                } else {
                    future.setException(IllegalArgumentException("Could not decode bitmap bytes"))
                }
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    future.cancel(false)
                } else {
                    future.setException(t)
                }
            }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, { it.run() })
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val coverUrl = uri.toString().trim()
        if (coverUrl.isEmpty()) {
            return Futures.immediateFailedFuture(IllegalArgumentException("Cover URI is empty"))
        }
        val loader = imageLoaderProvider()
        if (loader == null) {
            return fallbackLoaderProvider()?.loadBitmap(uri)
                ?: Futures.immediateFailedFuture(IllegalStateException("ImageLoader is unavailable"))
        }

        val cachedKey = findBestCoverMemoryCacheKey(loader, coverUrl, targetPx)
        if (cachedKey != null) {
            val cachedBitmap = toSafeSoftwareBitmap(loader.memoryCache?.get(cachedKey)?.bitmap)
            if (cachedBitmap != null) {
                return Futures.immediateFuture(cachedBitmap)
            }
        }

        val future = SettableFuture.create<Bitmap>()
        val request = buildCoverImageRequest(
            context = context,
            coverUrl = coverUrl,
            targetPx = targetPx,
            imageLoader = loader,
            allowHardware = false,
        )
        val job = scope.launch {
            try {
                when (val result = loader.execute(request)) {
                    is SuccessResult -> {
                        val safe = drawableToSoftwareBitmap(result.drawable)
                        if (safe != null) {
                            future.set(safe)
                        } else {
                            future.setException(IllegalStateException("Decoded cover could not be converted to software bitmap"))
                        }
                    }
                    is ErrorResult -> future.setException(result.throwable)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    future.cancel(false)
                } else {
                    future.setException(t)
                }
            }
        }
        future.addListener({ if (future.isCancelled) job.cancel() }, { it.run() })
        return future
    }
}
