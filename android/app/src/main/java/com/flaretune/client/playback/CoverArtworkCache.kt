package com.flaretune.client.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.Player
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val THUMB_ARTWORK_PX = 192
internal const val PLAYER_PREWARM_ARTWORK_PX = 512

internal fun quantizeArtworkDecodePx(requestedPx: Int): Int = when {
    requestedPx <= 0 -> PLAYER_PREWARM_ARTWORK_PX
    requestedPx <= THUMB_ARTWORK_PX -> THUMB_ARTWORK_PX
    else -> PLAYER_PREWARM_ARTWORK_PX
}

internal fun coverMemoryCacheKey(coverUrl: String, targetPx: Int): MemoryCache.Key =
    MemoryCache.Key(if (targetPx > 0) "$coverUrl#s=$targetPx" else coverUrl)

internal fun findBestCoverMemoryCacheKey(
    imageLoader: ImageLoader?,
    coverUrl: String,
    preferredPx: Int,
): MemoryCache.Key? {
    val memoryCache = imageLoader?.memoryCache ?: return null
    val candidates = buildList {
        add(coverMemoryCacheKey(coverUrl, preferredPx))
        if (preferredPx != PLAYER_PREWARM_ARTWORK_PX) add(coverMemoryCacheKey(coverUrl, PLAYER_PREWARM_ARTWORK_PX))
        if (preferredPx != THUMB_ARTWORK_PX) add(coverMemoryCacheKey(coverUrl, THUMB_ARTWORK_PX))
        add(MemoryCache.Key(coverUrl))
    }
    return candidates.firstOrNull { memoryCache[it] != null }
        ?: memoryCache.keys.firstOrNull { it.key == coverUrl && memoryCache[it] != null }
}

internal fun buildCoverImageRequest(
    context: Context,
    coverUrl: String,
    targetPx: Int,
    imageLoader: ImageLoader?,
    allowHardware: Boolean = true,
): ImageRequest {
    val cacheKey = coverMemoryCacheKey(coverUrl, targetPx)
    val placeholderKey = findBestCoverMemoryCacheKey(imageLoader, coverUrl, targetPx)
    return ImageRequest.Builder(context)
        .data(coverUrl)
        .apply {
            if (targetPx > 0) size(targetPx, targetPx)
            memoryCacheKey(cacheKey)
            if (placeholderKey != null && placeholderKey != cacheKey) {
                placeholderMemoryCacheKey(placeholderKey)
            }
            if (!allowHardware) allowHardware(false)
        }
        .crossfade(false)
        .build()
}

/**
 * Produces an immutable software [Bitmap] safe for Notification / MediaSessionCompat Binder IPC.
 * Returns null if the source is missing, recycled, or cannot be copied into a software config.
 */
internal fun toSafeSoftwareBitmap(bitmap: Bitmap?): Bitmap? {
    if (bitmap == null || bitmap.isRecycled) return null
    val targetConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        Bitmap.Config.ARGB_8888
    } else {
        bitmap.config ?: Bitmap.Config.ARGB_8888
    }
    return runCatching { bitmap.copy(targetConfig, false) }.getOrNull()
}

internal fun drawableToSoftwareBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable) {
        toSafeSoftwareBitmap(drawable.bitmap)?.let { return it }
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: PLAYER_PREWARM_ARTWORK_PX
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: PLAYER_PREWARM_ARTWORK_PX
    return runCatching {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    }.getOrNull()
}

/**
 * Resolves unique non-blank artwork URLs for the current track, next track, and previous track
 * in priority order while handling invalid/unset indices and shared album covers cleanly.
 */
internal fun collectNeighborCoverUrls(
    currentIndex: Int,
    previousIndex: Int,
    nextIndex: Int,
    mediaItemCount: Int,
    coverUrlAt: (Int) -> String?,
): List<String> {
    if (mediaItemCount <= 0) return emptyList()
    val indices = LinkedHashSet<Int>(3)
    if (currentIndex in 0 until mediaItemCount) indices.add(currentIndex)
    if (nextIndex != C.INDEX_UNSET && nextIndex in 0 until mediaItemCount) indices.add(nextIndex)
    if (previousIndex != C.INDEX_UNSET && previousIndex in 0 until mediaItemCount) indices.add(previousIndex)
    val urls = LinkedHashSet<String>(indices.size)
    for (index in indices) {
        coverUrlAt(index)?.trim()?.takeIf { it.isNotEmpty() }?.let(urls::add)
    }
    return urls.toList()
}

internal class CoverArtworkPrewarmer(
    private val context: Context,
    private val imageLoaderProvider: () -> ImageLoader?,
    private val scope: CoroutineScope,
    private val targetPx: Int = PLAYER_PREWARM_ARTWORK_PX,
) {
    private val lock = Any()
    private var trackedLoader: ImageLoader? = null
    private val inFlight = mutableMapOf<String, Job>()

    fun prewarm(player: Player) {
        val count = player.mediaItemCount
        if (count <= 0) return
        val urls = collectNeighborCoverUrls(
            currentIndex = player.currentMediaItemIndex,
            previousIndex = player.previousMediaItemIndex,
            nextIndex = player.nextMediaItemIndex,
            mediaItemCount = count,
        ) { index ->
            player.getMediaItemAt(index).mediaMetadata.artworkUri?.toString()
        }
        prewarmUrls(urls)
    }

    fun prewarmUrls(urls: List<String>) {
        if (urls.isEmpty()) return
        val loader = imageLoaderProvider() ?: return
        val cacheKeyFor = { url: String -> coverMemoryCacheKey(url, targetPx) }
        val toLaunch = synchronized(lock) {
            if (trackedLoader !== loader) {
                inFlight.values.forEach { it.cancel() }
                inFlight.clear()
                trackedLoader = loader
            }
            urls.filter { url ->
                val cached = loader.memoryCache?.get(cacheKeyFor(url))?.bitmap
                val alreadyWarm = cached != null && !cached.isRecycled
                !alreadyWarm && !inFlight.containsKey(url)
            }
        }
        for (url in toLaunch) {
            val request = buildCoverImageRequest(
                context = context,
                coverUrl = url,
                targetPx = targetPx,
                imageLoader = loader,
                allowHardware = false,
            )
            lateinit var job: Job
            job = scope.launch {
                try {
                    loader.execute(request)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Prewarm is best-effort; foreground loads handle retries/errors.
                } finally {
                    synchronized(lock) {
                        if (trackedLoader === loader && inFlight[url] === job) {
                            inFlight.remove(url)
                        }
                    }
                }
            }
            synchronized(lock) {
                if (trackedLoader === loader && job.isActive) {
                    inFlight[url] = job
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            inFlight.values.forEach { it.cancel() }
            inFlight.clear()
            trackedLoader = null
        }
    }
}
