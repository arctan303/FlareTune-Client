package com.flaretune.client.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.memory.MemoryCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayerTransitionTest {

    @Test
    fun wideMorphingWindowShapeInterpolatesFromMiniBarToFullScreenReversibly() {
        val registry = PlayerTransitionRegistry().apply {
            miniBarBounds = Rect(760f, 700f, 1180f, 778f)
            miniBarCornerDp = 22.dp
        }
        var progress = 0f
        val shape = WideMorphingWindowShape(registry) { progress }
        val fullSize = Size(1280f, 800f)
        val density = Density(1f)

        val startOutline = shape.createOutline(fullSize, LayoutDirection.Ltr, density) as Outline.Rounded
        assertEquals(760f, startOutline.roundRect.left, 0.5f)
        assertEquals(700f, startOutline.roundRect.top, 0.5f)
        assertEquals(1180f, startOutline.roundRect.right, 0.5f)
        assertEquals(778f, startOutline.roundRect.bottom, 0.5f)
        assertEquals(22f, startOutline.roundRect.topLeftCornerRadius.x, 0.5f)

        progress = 0.5f
        val midOutline = shape.createOutline(fullSize, LayoutDirection.Ltr, density) as Outline.Rounded
        assertTrue(midOutline.roundRect.left in 1f..759f)
        assertTrue(midOutline.roundRect.top in 1f..699f)
        assertTrue(midOutline.roundRect.right in 1181f..1279f)
        assertTrue(midOutline.roundRect.bottom in 779f..799f)
        assertTrue(midOutline.roundRect.topLeftCornerRadius.x in 1f..21f)

        progress = 1f
        val endOutline = shape.createOutline(fullSize, LayoutDirection.Ltr, density) as Outline.Rounded
        assertEquals(0f, endOutline.roundRect.left, 0.5f)
        assertEquals(0f, endOutline.roundRect.top, 0.5f)
        assertEquals(1280f, endOutline.roundRect.right, 0.5f)
        assertEquals(800f, endOutline.roundRect.bottom, 0.5f)
        assertEquals(0f, endOutline.roundRect.topLeftCornerRadius.x, 0.5f)

        // Reversible return back to miniBarBounds
        progress = 0f
        val reversedOutline = shape.createOutline(fullSize, LayoutDirection.Ltr, density) as Outline.Rounded
        assertEquals(startOutline.roundRect, reversedOutline.roundRect)
    }

    @Test
    fun compactMorphingSheetShapeTracksFullWidthStripAndBottomNav1To1WithBellTopRadius() {
        val registry = PlayerTransitionRegistry().apply {
            miniBarBounds = Rect(0f, 680f, 412f, 744f)
            miniBarCornerDp = 0.dp
        }
        var progress = 0f
        val slideStartY = 680f
        val shape = CompactMorphingSheetShape(registry, { progress }) { slideStartY }
        val fullSize = Size(412f, 820f)
        val density = Density(1f)

        val atZero = shape.createOutline(fullSize, LayoutDirection.Ltr, density) as Outline.Rounded
        assertEquals(0f, atZero.roundRect.left, 0.1f)
        assertEquals(412f, atZero.roundRect.right, 0.1f)
        assertEquals(64f, atZero.roundRect.bottom, 0.5f)
        assertEquals(0f, atZero.roundRect.topLeftCornerRadius.x, 0.1f)
        assertEquals(0f, atZero.roundRect.bottomLeftCornerRadius.x, 0.1f)

        progress = 0.5f
        val atHalf = shape.createOutline(fullSize, LayoutDirection.Ltr, density) as Outline.Rounded
        val rootBottomHalf = (1f - progress) * slideStartY + atHalf.roundRect.bottom
        assertEquals(0f, atHalf.roundRect.left, 0.1f)
        assertEquals(412f, atHalf.roundRect.right, 0.1f)
        assertEquals(782f, rootBottomHalf, 0.5f) // 744 + (820 - 744) * 0.5 = 782 -> strict 1:1 with bottom nav slide
        assertEquals(20f, atHalf.roundRect.topLeftCornerRadius.x, 0.2f)

        progress = 1f
        val atFull = shape.createOutline(fullSize, LayoutDirection.Ltr, density) as Outline.Rounded
        assertEquals(820f, atFull.roundRect.bottom, 0.5f)
        assertEquals(0f, atFull.roundRect.topLeftCornerRadius.x, 0.1f)
    }

    @Test
    fun wideWindowFrameProgressLeadsLinearProgressAtMidpoint() {
        assertEquals(0f, wideWindowFrameProgress(0f), 0.0001f)
        assertEquals(0.5875f, wideWindowFrameProgress(0.5f), 0.0001f)
        assertEquals(1f, wideWindowFrameProgress(1f), 0.0001f)
    }

    @Test
    fun compactMorphingSheetShapeUncoversBottomNavigationAndMatchesMiniBarAtZero() {
        val registry = PlayerTransitionRegistry().apply {
            miniBarBounds = Rect(8f, 680f, 404f, 744f)
            miniBarCornerDp = 16.dp
        }
        var progress = 0f
        val slideStartY = 680f
        val shape = CompactMorphingSheetShape(registry, { progress }) { slideStartY }
        val fullSize = Size(412f, 820f)
        val density = Density(1f)

        val atZero = shape.createOutline(fullSize, LayoutDirection.Ltr, density) as Outline.Rounded
        assertEquals(8f, atZero.roundRect.left, 0.5f)
        assertEquals(0f, atZero.roundRect.top, 0.5f)
        assertEquals(404f, atZero.roundRect.right, 0.5f)
        assertEquals(64f, atZero.roundRect.bottom, 0.5f)
        assertEquals(16f, atZero.roundRect.topLeftCornerRadius.x, 0.5f)
        assertEquals(16f, atZero.roundRect.bottomLeftCornerRadius.x, 0.5f)

        progress = 0.25f
        val curTranslateY = (1f - progress) * slideStartY
        val atQuarter = shape.createOutline(fullSize, LayoutDirection.Ltr, density) as Outline.Rounded
        val rootBottomQuarter = curTranslateY + atQuarter.roundRect.bottom
        assertTrue(atQuarter.roundRect.left in 0.1f..7.9f)
        assertTrue(atQuarter.roundRect.right in 404.1f..411.9f)
        assertTrue(rootBottomQuarter in 745f..819f)

        progress = 1f
        val atFull = shape.createOutline(fullSize, LayoutDirection.Ltr, density) as Outline.Rounded
        assertEquals(0f, atFull.roundRect.left, 0.5f)
        assertEquals(412f, atFull.roundRect.right, 0.5f)
        assertEquals(820f, atFull.roundRect.bottom, 0.5f)
        assertEquals(0f, atFull.roundRect.topLeftCornerRadius.x, 0.5f)
        assertEquals(0f, atFull.roundRect.bottomLeftCornerRadius.x, 0.5f)
    }

    @Test
    fun handoffAndMiniArtworkHidingPreventFrameZeroFlash() {
        val registry = PlayerTransitionRegistry().apply {
            miniArtworkBounds = Rect(16f, 700f, 60f, 744f)
            fullArtworkBounds = Rect(16f, 80f, 60f, 124f)
        }
        // Frame 0 before FlyingArtworkOverlay image decodes: mini artwork stays visible and destination hides symmetrically only once ready or moving.
        assertFalse(registry.isHandoffActive(0f, transitionActive = true))
        assertFalse(registry.shouldHideMiniArtwork(0f, transitionActive = true))

        registry.flyingArtworkReady = true
        assertTrue(registry.isHandoffActive(0f, transitionActive = true))
        assertTrue(registry.shouldHideMiniArtwork(0f, transitionActive = true))
        assertFalse(registry.shouldHideMiniArtwork(0f, transitionActive = false))

        registry.flyingArtworkReady = false
        assertTrue(registry.isHandoffActive(0.05f, transitionActive = true))
        assertTrue(registry.shouldHideMiniArtwork(0.05f, transitionActive = true))
        assertFalse(registry.isHandoffActive(1f, transitionActive = true))
    }

    @Test
    fun unscaleRectAroundViewportCenterCompensatesLibraryDepthScale() {
        val rootSize = androidx.compose.ui.unit.IntSize(400, 800)
        val original = Rect(8f, 680f, 392f, 744f)
        val scale = 0.976f
        val cx = 200f
        val cy = 400f
        val scaled = Rect(
            left = cx + (original.left - cx) * scale,
            top = cy + (original.top - cy) * scale,
            right = cx + (original.right - cx) * scale,
            bottom = cy + (original.bottom - cy) * scale,
        )
        val unscaled = unscaleRectAroundViewportCenter(scaled, rootSize, scale)
        assertEquals(original.left, unscaled.left, 0.05f)
        assertEquals(original.top, unscaled.top, 0.05f)
        assertEquals(original.right, unscaled.right, 0.05f)
        assertEquals(original.bottom, unscaled.bottom, 0.05f)
    }

    @Test
    fun compactPlayerSettleTweenPreserves300msTapAndShortensGestureReleases() {
        val tapSpec = compactPlayerSettleTween(remainingFraction = 1f, initialVelocity = 0f)
        assertEquals(300, tapSpec.durationMillis)

        val partialSpec = compactPlayerSettleTween(remainingFraction = 0.5f, initialVelocity = 0f)
        assertTrue(partialSpec.durationMillis in 140..160)

        val fastFlingSpec = compactPlayerSettleTween(remainingFraction = 0.6f, initialVelocity = -6f)
        assertTrue(fastFlingSpec.durationMillis in 110 until partialSpec.durationMillis)
    }

    @Test
    fun quantizeArtworkDecodePxNormalizesSmallThumbnailsToUnifiedBucket() {
        assertEquals(ThumbArtworkPx, quantizeArtworkDecodePx(88))
        assertEquals(ThumbArtworkPx, quantizeArtworkDecodePx(96))
        assertEquals(ThumbArtworkPx, quantizeArtworkDecodePx(ThumbArtworkPx))
        assertEquals(PlayerPrewarmArtworkPx, quantizeArtworkDecodePx(320))
        assertEquals(PlayerPrewarmArtworkPx, quantizeArtworkDecodePx(0))
    }

    @Test
    fun coverRequestSharesCanonicalTiersAndSuppliesMemoryPlaceholderAcrossSizes() {
        val context = RuntimeEnvironment.getApplication()
        val loader = ImageLoader.Builder(context).build()
        val coverUrl = "https://music.example/media/cover/song-1"
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)

        // When only a MiniPlayer thumbnail (ThumbArtworkPx) is in RAM, opening FullScreen reuses it as placeholder.
        val thumbKey = coverMemoryCacheKey(coverUrl, ThumbArtworkPx)
        loader.memoryCache!!.set(thumbKey, MemoryCache.Value(bmp))
        val fullRequest = buildCoverImageRequest(context, coverUrl, PlayerPrewarmArtworkPx, loader)
        assertEquals(coverMemoryCacheKey(coverUrl, PlayerPrewarmArtworkPx), fullRequest.memoryCacheKey)
        assertEquals(thumbKey, fullRequest.placeholderMemoryCacheKey)

        // If a Home list card loaded the cover at an arbitrary Coil size extra key, findBestCoverMemoryCacheKey still hits it.
        loader.memoryCache!!.clear()
        val arbitraryKey = MemoryCache.Key(coverUrl, mapOf("coil#size" to "96x96"))
        loader.memoryCache!!.set(arbitraryKey, MemoryCache.Value(bmp))
        assertEquals(arbitraryKey, findBestCoverMemoryCacheKey(loader, coverUrl, PlayerPrewarmArtworkPx))
        assertNull(findBestCoverMemoryCacheKey(loader, "https://music.example/media/cover/other", PlayerPrewarmArtworkPx))
    }
}
