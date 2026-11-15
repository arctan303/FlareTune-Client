package com.flaretune.client.ui

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.flaretune.client.model.Song
import com.flaretune.client.ui.theme.FlareTokens
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Responsive 300ms standard deceleration curve for compact sheet open/close settle (YT Music style).
 */
internal val PlayerSheetSettleTween = tween<Float>(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
)

/**
 * Returns the exact 300ms [PlayerSheetSettleTween] for tap open/close and proportionally
 * shortens duration for partial drags or high-velocity flings so releases stay responsive.
 */
internal fun compactPlayerSettleTween(
    remainingFraction: Float,
    initialVelocity: Float,
) = if (abs(initialVelocity) <= 0.1f && remainingFraction >= 0.98f) {
    PlayerSheetSettleTween
} else {
    val velocityFactor = 1f + abs(initialVelocity) * 0.14f
    tween<Float>(
        durationMillis = (300f * remainingFraction.coerceIn(0f, 1f) / velocityFactor)
            .roundToInt()
            .coerceIn(110, 300),
        easing = FastOutSlowInEasing,
    )
}

/**
 * Critically damped high-stiffness spring for YT-Music-style compact sheet open/close settle,
 * preserving finger release velocity seamlessly without bounce.
 */
internal val CompactPlayerSettleSpring = spring<Float>(
    dampingRatio = 0.95f,
    stiffness = 460f,
)

/**
 * Smooth bottom-up slide spring for wide tablet full-screen player presentation.
 */
internal val WidePlayerSlideSpring = spring<Float>(
    dampingRatio = 0.94f,
    stiffness = 400f,
)

/**
 * Graceful spatial container spring for wide-screen tablet app-launch expansion & reversible collapse (~300ms).
 */
internal val WidePlayerContainerSpring = spring<Float>(
    dampingRatio = 0.90f,
    stiffness = 380f,
)

/**
 * Reused static rounded-top sheet outline during compact drag/settle.
 */
internal val PlayerSheetDraggingShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

internal const val ThumbArtworkPx = com.flaretune.client.playback.THUMB_ARTWORK_PX
internal const val PlayerPrewarmArtworkPx = com.flaretune.client.playback.PLAYER_PREWARM_ARTWORK_PX

internal fun quantizeArtworkDecodePx(requestedPx: Int): Int =
    com.flaretune.client.playback.quantizeArtworkDecodePx(requestedPx)

internal fun coverMemoryCacheKey(coverUrl: String, targetPx: Int): MemoryCache.Key =
    com.flaretune.client.playback.coverMemoryCacheKey(coverUrl, targetPx)

internal fun findBestCoverMemoryCacheKey(
    imageLoader: ImageLoader?,
    coverUrl: String,
    preferredPx: Int,
): MemoryCache.Key? =
    com.flaretune.client.playback.findBestCoverMemoryCacheKey(imageLoader, coverUrl, preferredPx)

internal fun buildCoverImageRequest(
    context: Context,
    coverUrl: String,
    targetPx: Int,
    imageLoader: ImageLoader?,
): ImageRequest =
    com.flaretune.client.playback.buildCoverImageRequest(context, coverUrl, targetPx, imageLoader)

private fun rectsDiffer(a: Rect?, b: Rect, tolerancePx: Float = 0.75f): Boolean {
    if (a == null) return true
    return abs(a.left - b.left) > tolerancePx ||
        abs(a.top - b.top) > tolerancePx ||
        abs(a.right - b.right) > tolerancePx ||
        abs(a.bottom - b.bottom) > tolerancePx
}

/**
 * Tracks root-relative container and artwork rectangles across both compact (phone) and
 * wide (tablet) layouts. Bounds are frozen during motion and read only inside layout/graphics/draw
 * phases so drag and settle animations trigger zero per-frame recompositions.
 */
@Stable
internal class PlayerTransitionRegistry {
    var rootOrigin: Offset = Offset.Zero
    var rootSize: IntSize = IntSize.Zero

    var miniBarBounds by mutableStateOf<Rect?>(null)
    var miniBarCornerDp: Dp = 10.dp
    var miniBarSurfaceColor: Color = Color(0xFF232428)

    var miniArtworkBounds by mutableStateOf<Rect?>(null)
    var miniArtworkCornerDp: Dp = 8.dp

    var fullArtworkBounds by mutableStateOf<Rect?>(null)
    var fullArtworkCornerDp: Dp = FlareTokens.RadiusLg
    var fullArtworkAlpha: Float = 1f
    var fullArtworkModeKey: String? = null
    var flyingArtworkReady by mutableStateOf(false)
    var activeLibraryScale: Float = 1f

    fun updateRootLayout(origin: Offset, size: IntSize) {
        val sizeChanged = rootSize != IntSize.Zero && rootSize != size
        rootOrigin = origin
        rootSize = size
        if (sizeChanged) {
            miniBarBounds = null
            miniArtworkBounds = null
            fullArtworkBounds = null
            fullArtworkModeKey = null
        }
    }

    fun toRootLocalRect(coords: LayoutCoordinates): Rect? {
        if (!coords.isAttached) return null
        val raw = coords.findRootCoordinates().localBoundingBoxOf(coords, clipBounds = false)
        if (raw.width <= 0f || raw.height <= 0f) return null
        val origin = rootOrigin
        val localRect = Rect(
            left = raw.left - origin.x,
            top = raw.top - origin.y,
            right = raw.right - origin.x,
            bottom = raw.bottom - origin.y,
        )
        return unscaleRectAroundViewportCenter(localRect, rootSize, activeLibraryScale)
    }

    fun updateMiniBar(coords: LayoutCoordinates, cornerDp: Dp, surfaceColor: Color) {
        toRootLocalRect(coords)?.let { rect ->
            miniBarCornerDp = cornerDp
            miniBarSurfaceColor = surfaceColor
            if (rectsDiffer(miniBarBounds, rect)) {
                miniBarBounds = rect
            }
        }
    }

    fun updateMiniArtwork(coords: LayoutCoordinates, cornerDp: Dp) {
        toRootLocalRect(coords)?.let { rect ->
            miniArtworkCornerDp = cornerDp
            if (rectsDiffer(miniArtworkBounds, rect)) {
                miniArtworkBounds = rect
            }
        }
    }

    /**
     * Captures resting (`p == 1f` or pre-open `p == 0f`) full artwork rectangle using unclipped
     * root bounds so clipped sheet edges never distort target bounds, while staying frozen mid-flight
     * unless [modeKey] changes (e.g. Cover <-> Lyrics).
     */
    fun updateFullArtwork(
        coords: LayoutCoordinates,
        cornerDp: Dp,
        scale: Float,
        alpha: Float,
        expandProgress: Float,
        sheetTranslationY: Float,
        modeKey: String = "",
    ) {
        fullArtworkAlpha = alpha
        val modeChanged = fullArtworkModeKey != modeKey
        if (!modeChanged && fullArtworkBounds != null && expandProgress in 0.001f..0.998f) return
        if (!coords.isAttached) return
        val raw = coords.findRootCoordinates().localBoundingBoxOf(coords, clipBounds = false)
        if (raw.width <= 0f || raw.height <= 0f) return
        val origin = rootOrigin
        val restingRect = Rect(
            left = raw.left - origin.x,
            top = raw.top - origin.y - sheetTranslationY,
            right = raw.right - origin.x,
            bottom = raw.bottom - origin.y - sheetTranslationY,
        )
        val scaledRect = scaleRectAroundCenter(restingRect, scale)
        fullArtworkCornerDp = cornerDp * scale
        fullArtworkModeKey = modeKey
        if (rectsDiffer(fullArtworkBounds, scaledRect)) {
            fullArtworkBounds = scaledRect
        }
    }

    fun hasBothArtworkBounds(): Boolean = miniArtworkBounds != null && fullArtworkBounds != null

    fun isHandoffActive(progress: Float, transitionActive: Boolean = false): Boolean =
        hasBothArtworkBounds() && progress < 0.997f && (progress > 0.001f || (transitionActive && flyingArtworkReady))

    fun shouldHideMiniArtwork(progress: Float, transitionActive: Boolean = false): Boolean =
        hasBothArtworkBounds() && (progress > 0.001f || (transitionActive && flyingArtworkReady))
}

internal fun unscaleRectAroundViewportCenter(rect: Rect, rootSize: IntSize, scale: Float): Rect {
    if (abs(scale - 1f) <= 0.0005f || scale <= 0.01f || rootSize.width <= 0 || rootSize.height <= 0) return rect
    val cx = rootSize.width * 0.5f
    val cy = rootSize.height * 0.5f
    return Rect(
        left = cx + (rect.left - cx) / scale,
        top = cy + (rect.top - cy) / scale,
        right = cx + (rect.right - cx) / scale,
        bottom = cy + (rect.bottom - cy) / scale,
    )
}

@Composable
internal fun rememberPlayerTransitionRegistry(): PlayerTransitionRegistry =
    remember { PlayerTransitionRegistry() }

internal fun scaleRectAroundCenter(rect: Rect, scale: Float): Rect {
    if (scale == 1f) return rect
    val halfW = rect.width * scale * 0.5f
    val halfH = rect.height * scale * 0.5f
    val center = rect.center
    return Rect(
        left = center.x - halfW,
        top = center.y - halfH,
        right = center.x + halfW,
        bottom = center.y + halfH,
    )
}

/**
 * Fallback collapsed container bounds at root bottom for compact mode before MiniPlayer reports layout.
 */
internal fun fallbackMiniBarRect(rootSize: IntSize, density: Density): Rect {
    val width = rootSize.width.toFloat().coerceAtLeast(1f)
    val height = rootSize.height.toFloat().coerceAtLeast(1f)
    val barHeight = with(density) { FlareTokens.CompactMiniPlayerHeight.toPx() }
    return Rect(0f, (height - barHeight).coerceAtLeast(0f), width, height)
}

/**
 * Fallback bottom-right floating MiniPlayer bounds for wide tablet layouts.
 */
internal fun fallbackWideMiniBarRect(size: Size, density: Density): Rect {
    val widthDp = with(density) { size.width.toDp() }
    val dockTotalDp = ((widthDp - 40.dp) * 0.86f).coerceIn(760.dp, 1020.dp)
    val gapDp = 12.dp
    val navWidthDp = ((dockTotalDp - gapDp) * 0.58f).coerceIn(440.dp, 580.dp)
    val miniWidthDp = (dockTotalDp - gapDp - navWidthDp).coerceIn(308.dp, 428.dp)
    val miniHeightDp = FlareTokens.WideDockHeight
    val rightMarginPx = with(density) { ((widthDp - dockTotalDp) * 0.5f).coerceAtLeast(20.dp).toPx() }
    val bottomMarginPx = with(density) { 10.dp.toPx() }
    val miniWidthPx = with(density) { miniWidthDp.toPx() }
    val miniHeightPx = with(density) { miniHeightDp.toPx() }
    val right = (size.width - rightMarginPx).coerceAtLeast(miniWidthPx)
    val bottom = (size.height - bottomMarginPx).coerceAtLeast(miniHeightPx)
    return Rect(right - miniWidthPx, bottom - miniHeightPx, right, bottom)
}

/**
 * Responsive frame progress for wide container transform: stays closely coupled to finger drag
 * while giving a slight edge cushion so the cover icon never clips against the frame.
 */
internal fun wideWindowFrameProgress(p: Float): Float {
    val clamped = p.coerceIn(0f, 1f)
    val inv = 1f - clamped
    val quadOut = 1f - inv * inv
    return 0.65f * clamped + 0.35f * quadOut
}

/**
 * Reusable Shape that tracks the compact sheet from the full-width MiniPlayer strip above the bottom
 * navigation bar (p=0) to edge-to-edge full-screen (p=1), keeping the sheet bottom edge 1:1 synced
 * with the downward-translating bottom navigation bar and applying a smooth bell-curve top radius.
 */
internal class CompactMorphingSheetShape(
    private val registry: PlayerTransitionRegistry,
    private val progress: () -> Float,
    private val slideStartY: () -> Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = progress().coerceIn(0f, 1f)
        val startRect = registry.miniBarBounds
            ?: fallbackMiniBarRect(IntSize(size.width.roundToInt(), size.height.roundToInt()), density)
        val sideP = if (startRect.left <= 0.5f && startRect.right >= size.width - 0.5f) {
            1f
        } else {
            (p / 0.35f).coerceIn(0f, 1f)
        }
        val curTranslateY = (1f - p) * slideStartY()

        val left = lerp(startRect.left, 0f, sideP)
        val right = lerp(startRect.right, size.width, sideP)
        val rootBottom = lerp(startRect.bottom, size.height, p)
        val localBottom = (rootBottom - curTranslateY).coerceAtLeast(1f)

        val miniCornerPx = with(density) { registry.miniBarCornerDp.toPx() }
        val sheetCornerPx = with(density) { 20.dp.toPx() }
        val topCornerPx = maxOf(
            miniCornerPx * (1f - p),
            sheetCornerPx * 4f * p * (1f - p),
        )
        val bottomCornerPx = lerp(miniCornerPx, 0f, p)

        return Outline.Rounded(
            RoundRect(
                left = left,
                top = 0f,
                right = right,
                bottom = localBottom,
                topLeftCornerRadius = CornerRadius(topCornerPx, topCornerPx),
                topRightCornerRadius = CornerRadius(topCornerPx, topCornerPx),
                bottomRightCornerRadius = CornerRadius(bottomCornerPx, bottomCornerPx),
                bottomLeftCornerRadius = CornerRadius(bottomCornerPx, bottomCornerPx),
            ),
        )
    }
}

internal fun computeWideWindowRect(
    size: Size,
    density: Density,
    registry: PlayerTransitionRegistry,
    progress: Float,
): Pair<Rect, Float> {
    val frameP = wideWindowFrameProgress(progress)
    val startRect = registry.miniBarBounds ?: fallbackWideMiniBarRect(size, density)
    val left = lerp(startRect.left, 0f, frameP)
    val top = lerp(startRect.top, 0f, frameP)
    val right = lerp(startRect.right, size.width, frameP)
    val bottom = lerp(startRect.bottom, size.height, frameP)
    val startCornerPx = with(density) { registry.miniBarCornerDp.toPx() }
    val cornerPx = lerp(startCornerPx, 0f, frameP)
    return Rect(left, top, right, bottom) to cornerPx
}

/**
 * Reusable Shape that clips the wide full-screen player container smoothly from the bottom-right
 * floating MiniPlayer card into the full tablet viewport (and reversibly back), with zero allocations.
 */
internal class WideMorphingWindowShape(
    private val registry: PlayerTransitionRegistry,
    private val progress: () -> Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val (rect, cornerPx) = computeWideWindowRect(size, density, registry, progress())
        return Outline.Rounded(
            RoundRect(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
                cornerRadius = CornerRadius(cornerPx, cornerPx),
            ),
        )
    }
}

/**
 * Draws a crisp hairline border around the expanding/collapsing tablet window frame while in motion.
 */
internal fun Modifier.wideMorphingFrameBorder(
    registry: PlayerTransitionRegistry,
    progress: () -> Float,
    darkTheme: Boolean,
): Modifier = drawWithContent {
    drawContent()
    val p = progress().coerceIn(0f, 1f)
    if (p in 0.001f..0.998f) {
        val (rect, cornerPx) = computeWideWindowRect(size, this, registry, p)
        val borderColor = if (darkTheme) Color.White else Color.Black
        val borderAlpha = (1f - p) * (if (darkTheme) 0.16f else 0.10f)
        drawRoundRect(
            color = borderColor.copy(alpha = borderAlpha),
            topLeft = Offset(rect.left, rect.top),
            size = Size((rect.width).coerceAtLeast(0f), (rect.height).coerceAtLeast(0f)),
            cornerRadius = CornerRadius(cornerPx, cornerPx),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

/**
 * Pure GPU-layer staggered reveal for player secondary controls and lyrics, driven by
 * [expandProgress] without causing recomposition during drags or container transforms.
 * Starts fading/receding immediately from p=1.0 during close drags on both phone and tablet.
 */
internal fun Modifier.playerContentReveal(
    expandProgress: () -> Float,
    isWideMode: Boolean = false,
): Modifier = if (isWideMode) {
    this
} else {
    graphicsLayer {
        val p = expandProgress().coerceIn(0f, 1f)
        if (p >= 0.999f) {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            translationY = 0f
        } else {
            val contentP = ((p - 0.14f) / 0.72f).coerceIn(0f, 1f)
            alpha = contentP
            val s = 0.985f + 0.015f * contentP
            scaleX = s
            scaleY = s
            translationY = (1f - contentP) * 18.dp.toPx()
        }
    }
}

/**
 * GPU-layer fade-in for the top drag handle on compact sheets; wide sheets slide up as one unit.
 */
internal fun Modifier.playerHeaderReveal(
    expandProgress: () -> Float,
    isWideMode: Boolean = false,
    registry: PlayerTransitionRegistry? = null,
): Modifier = if (isWideMode) {
    this
} else {
    graphicsLayer {
        val p = expandProgress().coerceIn(0f, 1f)
        translationY = 0f
        alpha = if (p >= 0.999f) 1f else ((p - 0.035f) / 0.965f).coerceIn(0f, 1f)
    }
}

/**
 * GPU-only interpolated cover overlay between MiniPlayer artwork (0f) and Full-screen player
 * artwork (1f). Compact mode tracks [progress] 1:1 so the cover stays locked inside the sliding
 * sheet during drags and ease-out settles (instead of lagging behind during close); wide mode
 * tracks [wideWindowFrameProgress] so artwork stays framed inside the expanding tablet container.
 */
@Composable
internal fun FlyingArtworkOverlay(
    song: Song,
    imageLoader: ImageLoader?,
    progress: () -> Float,
    registry: PlayerTransitionRegistry,
    modifier: Modifier = Modifier,
    isWideMode: Boolean = false,
) {
    val context = LocalContext.current
    val coverUrl = song.coverUrl
    val coverRequest = remember(context, coverUrl, imageLoader) {
        if (coverUrl.isNullOrBlank()) null
        else buildCoverImageRequest(
            context = context,
            coverUrl = coverUrl,
            targetPx = PlayerPrewarmArtworkPx,
            imageLoader = imageLoader,
        )
    }
    remember(registry, imageLoader, coverUrl) {
        registry.flyingArtworkReady = imageLoader == null ||
            coverUrl.isNullOrBlank() ||
            findBestCoverMemoryCacheKey(imageLoader, coverUrl, PlayerPrewarmArtworkPx) != null
        true
    }
    DisposableEffect(registry) {
        onDispose {
            registry.flyingArtworkReady = false
        }
    }
    val morphShape = remember(registry, progress, isWideMode) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val p = progress().coerceIn(0f, 1f)
                val geoP = if (isWideMode) wideWindowFrameProgress(p) else p
                val start = registry.miniArtworkBounds
                val end = registry.fullArtworkBounds
                val startCornerPx = with(density) { registry.miniArtworkCornerDp.toPx() }
                val endCornerPx = with(density) { registry.fullArtworkCornerDp.toPx() }
                val curScale = if (start != null && end != null && end.width > 0f) {
                    lerp(start.width / end.width, 1f, geoP).coerceAtLeast(0.05f)
                } else {
                    1f
                }
                val compensatedRadiusPx = lerp(startCornerPx, endCornerPx, geoP) / curScale
                return Outline.Rounded(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        cornerRadius = CornerRadius(compensatedRadiusPx, compensatedRadiusPx),
                    ),
                )
            }
        }
    }

    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Box(
                Modifier
                    .graphicsLayer {
                        val p = progress().coerceIn(0f, 1f)
                        val start = registry.miniArtworkBounds
                        val end = registry.fullArtworkBounds
                        if (start == null || end == null || p >= 0.997f || (!registry.flyingArtworkReady && p <= 0.001f)) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        val geoP = if (isWideMode) wideWindowFrameProgress(p) else p
                        val startCx = start.left + start.width * 0.5f
                        val startCy = start.top + start.height * 0.5f
                        val endCx = end.left + end.width * 0.5f
                        val endCy = end.top + end.height * 0.5f
                        val curCx = startCx + (endCx - startCx) * geoP
                        val curCy = startCy + (endCy - startCy) * geoP
                        val startScaleX = start.width / end.width.coerceAtLeast(1f)
                        val startScaleY = start.height / end.height.coerceAtLeast(1f)
                        scaleX = startScaleX + (1f - startScaleX) * geoP
                        scaleY = startScaleY + (1f - startScaleY) * geoP
                        translationX = curCx - end.width * 0.5f
                        translationY = curCy - end.height * 0.5f
                        alpha = 1f + (registry.fullArtworkAlpha - 1f) * p
                        shape = morphShape
                        clip = true
                    }
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1F2229), Color(0xFF14161B)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    ClientIcons.Music,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.24f),
                    tint = Color.White.copy(alpha = 0.42f),
                )
                if (imageLoader != null && coverRequest != null) {
                    AsyncImage(
                        model = coverRequest,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onSuccess = { registry.flyingArtworkReady = true },
                        onError = { registry.flyingArtworkReady = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val endRect = registry.fullArtworkBounds ?: registry.miniArtworkBounds
        val targetW = (endRect?.width?.roundToInt() ?: 1).coerceIn(1, constraints.maxWidth.coerceAtLeast(1))
        val targetH = (endRect?.height?.roundToInt() ?: 1).coerceIn(1, constraints.maxHeight.coerceAtLeast(1))
        val placeable = measurables.first().measure(Constraints.fixed(targetW, targetH))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(0, 0)
        }
    }
}
