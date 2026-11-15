package com.flaretune.client.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.os.ConfigurationCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.flaretune.client.R
import com.flaretune.client.model.ClientNotice
import com.flaretune.client.model.LyricLine
import com.flaretune.client.model.LyricState
import com.flaretune.client.model.LyricStatus
import com.flaretune.client.model.LyricTranslationStatus
import com.flaretune.client.model.LyricWord
import com.flaretune.client.model.Song
import com.flaretune.client.ui.theme.FlareTokens
import com.flaretune.client.ui.theme.isFlareDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ClassicMobilePane { SONG, LYRICS }
private val ClassicTransportHeight = 68.dp
private val ClassicCompactTransportHeight = 56.dp
private val ClassicDragHeaderHeight = 36.dp

private data class ClassicPlayerMetrics(
    val contentWidth: Dp,
    val pageWidth: Dp,
    val lyricScale: Float,
    val controlScale: Float,
    val wideArtSize: Dp,
)

private fun resolveClassicPlayerMetrics(
    screenMaxWidth: Dp,
    screenMaxHeight: Dp,
    bodyHeight: Dp,
    twoColumns: Boolean,
    fontScale: Float,
): ClassicPlayerMetrics {
    val safeFontScale = fontScale.coerceAtLeast(1f)
    val fontHeadroom = if (safeFontScale > 1f) {
        (1f / safeFontScale.pow(0.45f)).coerceIn(0.68f, 1f)
    } else {
        1f
    }
    if (twoColumns) {
        val pageWidth = screenMaxWidth.coerceAtMost(1680.dp)
        val rawSpaceFactor = minOf(screenMaxWidth.value / 1200f, bodyHeight.value / 740f)
        val rawLyricScale = (1.15f + (rawSpaceFactor - 1f) * 0.55f).coerceIn(1.12f, 1.48f)
        val lyricScale = 1f + (rawLyricScale - 1f) * fontHeadroom
        val controlScale = (1f + (rawLyricScale - 1f) * 0.36f).coerceIn(1f, 1.22f)
        val controlsAllowance = (204.dp * controlScale) * safeFontScale
        val wideArtSize = minOf(
            screenMaxWidth * 0.34f,
            (bodyHeight - controlsAllowance).coerceAtLeast(160.dp),
        ).coerceIn(160.dp, 620.dp)
        return ClassicPlayerMetrics(
            contentWidth = pageWidth,
            pageWidth = pageWidth,
            lyricScale = lyricScale,
            controlScale = controlScale,
            wideArtSize = wideArtSize,
        )
    }

    // Preserve phone horizontal gutters (<= 484.dp) while proportionally expanding content on portrait tablets.
    val contentWidth = if (screenMaxWidth <= 484.dp) {
        (screenMaxWidth - 44.dp).coerceAtLeast(0.dp)
    } else {
        (screenMaxWidth * 0.84f).coerceIn(440.dp, 760.dp).coerceAtMost(screenMaxWidth - 48.dp)
    }
    val widthRatio = contentWidth.value / 344f
    val heightRatio = bodyHeight.value / 720f
    val rawFactor = minOf(widthRatio, heightRatio)
    val dampedFactor = if (rawFactor >= 1f) {
        (1f + (rawFactor - 1f) * 0.58f).coerceAtMost(1.45f)
    } else {
        (1f - (1f - rawFactor) * 0.35f).coerceAtLeast(0.86f)
    }
    val lyricScale = if (dampedFactor > 1f) {
        1f + (dampedFactor - 1f) * fontHeadroom
    } else {
        dampedFactor
    }
    val controlScale = if (dampedFactor >= 1f) {
        (1f + (dampedFactor - 1f) * 0.45f).coerceIn(1f, 1.22f)
    } else {
        (1f - (1f - dampedFactor) * 0.50f).coerceIn(0.90f, 1f)
    }
    return ClassicPlayerMetrics(
        contentWidth = contentWidth,
        pageWidth = contentWidth,
        lyricScale = lyricScale,
        controlScale = controlScale,
        wideArtSize = 320.dp,
    )
}

internal enum class UnifiedTranslationButtonMode {
    HIDDEN,
    LOADING,
    FAILED_RETRY,
    FAILED_READONLY,
    REQUEST,
    TOGGLE,
}

internal fun resolveUnifiedTranslationButtonMode(lyricState: LyricState): UnifiedTranslationButtonMode {
    if (lyricState.status != LyricStatus.READY ||
        lyricState.translationState == LyricTranslationStatus.NOT_NEEDED
    ) {
        return UnifiedTranslationButtonMode.HIDDEN
    }
    if (lyricState.translationState == LyricTranslationStatus.PENDING || lyricState.polling) {
        return UnifiedTranslationButtonMode.LOADING
    }
    val hasInlineTranslations = lyricState.lines.any { !it.translation.isNullOrBlank() }
    if (lyricState.translationState == LyricTranslationStatus.FAILED) {
        return if (lyricState.translationSupported || hasInlineTranslations) {
            UnifiedTranslationButtonMode.FAILED_RETRY
        } else {
            UnifiedTranslationButtonMode.FAILED_READONLY
        }
    }
    if (hasInlineTranslations) {
        return UnifiedTranslationButtonMode.TOGGLE
    }
    if (lyricState.translationSupported && lyricState.translationState == LyricTranslationStatus.IDLE) {
        return UnifiedTranslationButtonMode.REQUEST
    }
    return UnifiedTranslationButtonMode.HIDDEN
}

/**
 * Full-screen player modeled after the web classic player (`MobileClassicPlayer` / `ClassicDesktopLayout`):
 * - Always calm dark-ambient cover wash to preserve soft-white typography contrast
 * - Animated playing/paused cover scale & phone Song <-> Lyrics view toggle
 * - Streamlined dotless progress/volume tracks and 5-button transport row
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun NowPlayingScreen(
    state: PlaybackSnapshot,
    lyricState: LyricState = LyricState(),
    translationEnabled: Boolean = false,
    translationNotice: ClientNotice? = null,
    keepScreenOnEnabled: Boolean = true,
    actions: ClientActions,
    imageLoader: ImageLoader?,
    onClose: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
    expandProgress: () -> Float = { 1f },
    sheetTranslationY: () -> Float = { 0f },
    isWideTransition: Boolean = false,
    onCollapseDragStart: (() -> Unit)? = null,
    onCollapseDragDelta: ((Float) -> Float)? = null,
    onCollapseDragEnd: ((Float) -> Unit)? = null,
    transitionRegistry: PlayerTransitionRegistry? = null,
    mobileLyricsMode: Boolean = false,
    onMobileLyricsModeChange: ((Boolean) -> Unit)? = null,
    sheetVisible: Boolean = true,
) {
    val song = state.current ?: return
    var fallbackMobilePane by rememberSaveable { mutableStateOf(ClassicMobilePane.SONG) }
    val mobilePane = if (onMobileLyricsModeChange != null) {
        if (mobileLyricsMode) ClassicMobilePane.LYRICS else ClassicMobilePane.SONG
    } else {
        fallbackMobilePane
    }
    val setMobilePane: (ClassicMobilePane) -> Unit = { pane ->
        if (onMobileLyricsModeChange != null) {
            onMobileLyricsModeChange(pane == ClassicMobilePane.LYRICS)
        } else {
            fallbackMobilePane = pane
        }
    }
    val artworkAvailable = imageLoader != null && !song.coverUrl.isNullOrBlank()
    val atmospheric = artworkAvailable
    val scope = rememberCoroutineScope()
    val hostView = LocalView.current
    val unifiedMode = onCollapseDragDelta != null && onCollapseDragEnd != null
    val isMotionActive by remember(unifiedMode, sheetVisible, expandProgress) {
        derivedStateOf { !unifiedMode || sheetVisible || expandProgress() > 0.001f }
    }
    val keepAwake = sheetVisible && state.playing && keepScreenOnEnabled
    DisposableEffect(hostView, keepAwake) {
        val previous = hostView.keepScreenOn
        hostView.keepScreenOn = keepAwake
        onDispose {
            hostView.keepScreenOn = previous
        }
    }
    val dismissDragY = remember { Animatable(0f) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var draggingDismiss by remember { mutableStateOf(false) }
    var nestedCollapseActive by remember { mutableStateOf(false) }
    var closingSheet by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val maxDragThresholdPx = with(LocalDensity.current) { 148.dp.toPx() }
    val settleSpring = remember { spring<Float>(dampingRatio = 0.8f, stiffness = 308f) }
    val directionTracker = rememberTrackDirectionTracker(state)
    val trackDirection = directionTracker.peekDirection(state)
    val revealModifier = Modifier.playerContentReveal(expandProgress, isWideMode = isWideTransition)
    val headerRevealModifier = Modifier.playerHeaderReveal(expandProgress, isWideMode = isWideTransition, registry = transitionRegistry)

    val closeAnimated: () -> Unit = {
        if (unifiedMode) {
            onClose()
        } else if (!closingSheet) {
            closingSheet = true
            scope.launch {
                val target = containerHeightPx
                if (target > 1f) {
                    dismissDragY.animateTo(target, settleSpring)
                }
                onClose()
            }
        }
    }

    val settleSheet: (Float) -> Unit = { velocityY ->
        draggingDismiss = false
        nestedCollapseActive = false
        if (unifiedMode) {
            onCollapseDragEnd.invoke(velocityY)
        } else if (!closingSheet) {
            val height = containerHeightPx.coerceAtLeast(1f)
            val posThreshold = minOf(height * 0.26f, maxDragThresholdPx)
            val shouldClose = when {
                velocityY <= -1400f -> false
                velocityY >= 1500f -> true
                else -> dismissDragY.value >= posThreshold
            }
            if (shouldClose) {
                closeAnimated()
            } else {
                scope.launch { dismissDragY.animateTo(0f, settleSpring) }
            }
        }
    }

    // Page-level vertical drag-to-dismiss hook (unified with single sheet expand progress when provided).
    val sheetNestedScroll = remember(unifiedMode) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || (!unifiedMode && closingSheet)) return Offset.Zero
                if (unifiedMode) {
                    if (nestedCollapseActive || expandProgress() < 0.999f) {
                        if (!nestedCollapseActive) {
                            nestedCollapseActive = true
                            draggingDismiss = true
                            onCollapseDragStart?.invoke()
                        }
                        val consumedY = onCollapseDragDelta.invoke(available.y)
                        return Offset(0f, consumedY)
                    }
                    return Offset.Zero
                }
                val currentY = dismissDragY.value
                if (currentY > 0f) {
                    draggingDismiss = true
                    val maxH = containerHeightPx.coerceAtLeast(1f)
                    val next = (currentY + available.y).coerceIn(0f, maxH)
                    val consumedY = next - currentY
                    scope.launch { dismissDragY.snapTo(next) }
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || (!unifiedMode && closingSheet)) return Offset.Zero
                if (scrollState.value == 0 && available.y > 0f) {
                    if (unifiedMode) {
                        if (!nestedCollapseActive) {
                            nestedCollapseActive = true
                            draggingDismiss = true
                            onCollapseDragStart?.invoke()
                        }
                        val consumedY = onCollapseDragDelta.invoke(available.y)
                        return Offset(0f, consumedY)
                    }
                    draggingDismiss = true
                    val maxH = containerHeightPx.coerceAtLeast(1f)
                    val next = (dismissDragY.value + available.y).coerceIn(0f, maxH)
                    scope.launch { dismissDragY.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (unifiedMode) {
                    if (nestedCollapseActive || expandProgress() < 0.999f) {
                        settleSheet(available.y)
                        return available
                    }
                    return Velocity.Zero
                }
                if (!closingSheet && dismissDragY.value > 0f) {
                    settleSheet(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val collapseFlingVelocityPx = with(density) { 520.dp.toPx() }
                if (unifiedMode) {
                    if (scrollState.value == 0 && (nestedCollapseActive || available.y >= collapseFlingVelocityPx)) {
                        settleSheet(available.y)
                        return available
                    }
                    return Velocity.Zero
                }
                if (!closingSheet && scrollState.value == 0 && available.y > 220f) {
                    settleSheet(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier.fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height.toFloat() }
            .then(if (unifiedMode) Modifier else Modifier.graphicsLayer { translationY = dismissDragY.value })
            .then(if (isMotionActive) Modifier.testTag("full_player") else Modifier),
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = if (!unifiedMode || isWideTransition) 1f else ((expandProgress() - 0.04f) / 0.24f).coerceIn(0f, 1f)
            },
        ) {
            ClassicPlayerBackdrop(song, imageLoader, atmospheric)
        }
        if (unifiedMode && !isWideTransition) {
            val startSurface = transitionRegistry?.miniBarSurfaceColor ?: Color(0xFF232428)
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val p = expandProgress().coerceIn(0f, 1f)
                        alpha = if (p <= 0.06f) {
                            1f
                        } else {
                            (1f - (p - 0.06f) / 0.30f).coerceIn(0f, 1f)
                        }
                    }
                    .background(startSurface),
            )
            val peekVisible by remember { derivedStateOf { isMotionActive && expandProgress() < 0.32f } }
            if (peekVisible) {
                MiniPlayerTransitionStrip(
                    song = song,
                    state = state,
                    lyricState = lyricState,
                    translationEnabled = translationEnabled,
                    isWideTransition = false,
                    transitionRegistry = transitionRegistry,
                    expandProgress = expandProgress,
                )
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
        ) {
            val screenMaxWidth = maxWidth
            val screenMaxHeight = maxHeight
            val twoColumns = screenMaxWidth >= 1000.dp || (screenMaxWidth >= 600.dp && screenMaxWidth > screenMaxHeight * 1.1f)
            BackHandler(enabled = sheetVisible && !twoColumns && mobilePane == ClassicMobilePane.LYRICS) {
                setMobilePane(ClassicMobilePane.SONG)
            }
            val bodyHeight = (screenMaxHeight - ClassicDragHeaderHeight).coerceAtLeast(0.dp)
            val metrics = resolveClassicPlayerMetrics(
                screenMaxWidth = screenMaxWidth,
                screenMaxHeight = screenMaxHeight,
                bodyHeight = bodyHeight,
                twoColumns = twoColumns,
                fontScale = density.fontScale,
            )

            Column(
                Modifier.fillMaxSize()
                    .nestedScroll(sheetNestedScroll)
                    .verticalScroll(scrollState)
                    .testTag("player_scroll"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ClassicDragHandleHeader(
                    dragging = draggingDismiss,
                    closeClickable = sheetVisible,
                    onClose = closeAnimated,
                    onDragStart = {
                        draggingDismiss = true
                        if (unifiedMode) onCollapseDragStart?.invoke()
                    },
                    onDragDelta = { delta ->
                        if (unifiedMode) {
                            onCollapseDragDelta.invoke(delta)
                        } else if (!closingSheet) {
                            val maxH = containerHeightPx.coerceAtLeast(1f)
                            scope.launch {
                                val next = (dismissDragY.value + delta).coerceIn(0f, maxH)
                                dismissDragY.snapTo(next)
                            }
                        }
                    },
                    onDragEnd = settleSheet,
                    modifier = headerRevealModifier,
                )
                if (twoColumns) {
                    val stagePaddingH = (screenMaxWidth * 0.045f).coerceIn(28.dp, 68.dp)
                    val columnGap = (screenMaxWidth * 0.04f).coerceIn(28.dp, 56.dp)
                    val artSize = metrics.wideArtSize
                    val innerWidth = (metrics.pageWidth - stagePaddingH * 2 - columnGap).coerceAtLeast(0.dp)
                    val leftRailWidth = maxOf(
                        artSize,
                        (240.dp * metrics.controlScale).coerceAtMost(innerWidth * 0.42f),
                    )
                    // Single-pass layout measures the left cover+controls rail first so the right lyrics rail
                    // matches its exact height and bottom baseline with zero deferred resize pass.
                    Layout(
                        modifier = Modifier.width(metrics.pageWidth).height(bodyHeight)
                            .padding(start = stagePaddingH, end = stagePaddingH, bottom = 12.dp),
                        contents = listOf(
                            {
                                Column(
                                    Modifier.width(leftRailWidth)
                                        .heightIn(max = bodyHeight),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    ClassicArtworkCarousel(
                                        state = state,
                                        actions = actions,
                                        directionTracker = directionTracker,
                                        trackDirection = trackDirection,
                                        artSize = artSize,
                                        imageLoader = imageLoader,
                                        onClick = null,
                                        transitionRegistry = transitionRegistry,
                                        expandProgress = expandProgress,
                                        sheetTranslationY = sheetTranslationY,
                                        unifiedMode = unifiedMode,
                                        modeKey = "wide",
                                        swipeEnabled = sheetVisible && (!unifiedMode || expandProgress() >= 0.999f),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(20.dp * metrics.controlScale))
                                    ClassicPlayerControls(
                                        state = state,
                                        actions = actions,
                                        onQueue = onQueue,
                                        showLyricsButton = false,
                                        onToggleLyrics = {},
                                        hideMetadata = false,
                                        controlScale = metrics.controlScale,
                                        interactionEnabled = sheetVisible,
                                        trackDirection = trackDirection,
                                        onTrackDirectionHint = directionTracker::notifyDirection,
                                        modifier = Modifier.fillMaxWidth().then(revealModifier),
                                    )
                                }
                            },
                            {
                                ClassicLyricsViewport(
                                    song = song,
                                    lyricState = lyricState,
                                    translationEnabled = translationEnabled,
                                    translationNotice = translationNotice,
                                    playbackPositionMs = state.positionMs,
                                    playing = state.playing && isMotionActive,
                                    buffering = state.buffering,
                                    twoColumns = true,
                                    onSeekTo = actions::seekTo,
                                    onRetry = actions::retryLyrics,
                                    onSetTranslationEnabled = actions::setTranslationEnabled,
                                    onTranslate = actions::requestLyricTranslation,
                                    trackDirection = trackDirection,
                                    lyricScale = metrics.lyricScale,
                                    controlScale = metrics.controlScale,
                                    modifier = Modifier
                                        .widthIn(max = (artSize * 1.38f).coerceAtLeast(440.dp))
                                        .fillMaxWidth()
                                        .fillMaxHeight(),
                                )
                            },
                        ),
                    ) { measurables, constraints ->
                        val gapPx = columnGap.roundToPx()
                        val availableWidth = (constraints.maxWidth - gapPx).coerceAtLeast(0)
                        val leftSlotWidth = (availableWidth * 0.42f).roundToInt()
                        val rightSlotWidth = (availableWidth - leftSlotWidth).coerceAtLeast(0)
                        val leftPlaceable = measurables[0].first().measure(
                            Constraints(
                                minWidth = 0,
                                maxWidth = leftSlotWidth,
                                minHeight = 0,
                                maxHeight = constraints.maxHeight,
                            ),
                        )
                        val railHeight = leftPlaceable.height.coerceAtLeast(0)
                        val rightPlaceable = measurables[1].first().measure(
                            Constraints(
                                minWidth = 0,
                                maxWidth = rightSlotWidth,
                                minHeight = railHeight,
                                maxHeight = railHeight,
                            ),
                        )
                        val layoutWidth = constraints.maxWidth
                        val layoutHeight = constraints.maxHeight
                        val topY = ((layoutHeight - railHeight) / 2).coerceAtLeast(0)
                        val leftX = ((leftSlotWidth - leftPlaceable.width) / 2).coerceAtLeast(0)
                        layout(layoutWidth, layoutHeight) {
                            leftPlaceable.placeRelative(leftX, topY)
                            rightPlaceable.placeRelative(leftSlotWidth + gapPx, topY)
                        }
                    }
                } else {
                    val translationMode = resolveUnifiedTranslationButtonMode(lyricState)
                    val useCompactTransport = bodyHeight < 400.dp
                    val stageMinCompactHeight = 260.dp
                    Layout(
                        modifier = Modifier.width(metrics.contentWidth),
                        contents = listOf(
                            {
                                SharedTransitionLayout(Modifier.fillMaxWidth()) {
                                    AnimatedContent(
                                        targetState = mobilePane,
                                        transitionSpec = {
                                            (fadeIn(tween(240)) togetherWith fadeOut(tween(180)))
                                                .using(SizeTransform(clip = false))
                                        },
                                        label = "classic_mobile_pane",
                                    ) { pane ->
                                        val sharedScope = this@SharedTransitionLayout
                                        val animatedScope = this@AnimatedContent
                                        if (pane == ClassicMobilePane.LYRICS) {
                                            ClassicMobileLyricsStage(
                                                song = song,
                                                lyricState = lyricState,
                                                translationEnabled = translationEnabled,
                                                translationNotice = translationNotice,
                                                translationMode = translationMode,
                                                playbackPositionMs = state.positionMs,
                                                playing = state.playing && isMotionActive,
                                                buffering = state.buffering,
                                                imageLoader = imageLoader,
                                                actions = actions,
                                                onBackToArtwork = { setMobilePane(ClassicMobilePane.SONG) },
                                                sharedTransitionScope = sharedScope,
                                                animatedVisibilityScope = animatedScope,
                                                revealModifier = revealModifier,
                                                transitionRegistry = transitionRegistry,
                                                expandProgress = expandProgress,
                                                sheetTranslationY = sheetTranslationY,
                                                unifiedMode = unifiedMode,
                                                trackDirection = trackDirection,
                                                lyricScale = metrics.lyricScale,
                                                controlScale = metrics.controlScale,
                                                interactionEnabled = sheetVisible,
                                            )
                                        } else {
                                            ClassicMobileSongStage(
                                                state = state,
                                                actions = actions,
                                                directionTracker = directionTracker,
                                                trackDirection = trackDirection,
                                                imageLoader = imageLoader,
                                                onOpenLyrics = { setMobilePane(ClassicMobilePane.LYRICS) },
                                                sharedTransitionScope = sharedScope,
                                                animatedVisibilityScope = animatedScope,
                                                revealModifier = revealModifier,
                                                transitionRegistry = transitionRegistry,
                                                expandProgress = expandProgress,
                                                sheetTranslationY = sheetTranslationY,
                                                unifiedMode = unifiedMode,
                                                controlScale = metrics.controlScale,
                                                interactionEnabled = sheetVisible,
                                            )
                                        }
                                    }
                                }
                            },
                            {
                                ClassicPlayerTransportPanel(
                                    state = state,
                                    actions = actions,
                                    onQueue = onQueue,
                                    compactVertical = useCompactTransport,
                                    controlScale = metrics.controlScale,
                                    onTrackDirectionHint = directionTracker::notifyDirection,
                                    modifier = Modifier.fillMaxWidth().then(revealModifier),
                                )
                            },
                        ),
                    ) { measurables, constraints ->
                        val widthPx = constraints.maxWidth
                        val bodyPx = bodyHeight.roundToPx()
                        val bottomPadPx = 12.dp.roundToPx()
                        val widthConstraints = Constraints.fixedWidth(widthPx)
                        val transportPlaceable = measurables[1].first().measure(widthConstraints)
                        val minCompactPx = stageMinCompactHeight.roundToPx()
                        val remainingStagePx = (bodyPx - bottomPadPx - transportPlaceable.height)
                            .coerceAtLeast(minCompactPx)
                        val stagePlaceable = measurables[0].first().measure(
                            Constraints.fixed(widthPx, remainingStagePx),
                        )
                        val totalHeight = maxOf(bodyPx, stagePlaceable.height + transportPlaceable.height + bottomPadPx)
                        layout(widthPx, totalHeight) {
                            stagePlaceable.placeRelative(0, 0)
                            transportPlaceable.placeRelative(0, totalHeight - bottomPadPx - transportPlaceable.height)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ClassicMobileLyricsStage(
    song: Song,
    lyricState: LyricState,
    translationEnabled: Boolean,
    translationNotice: ClientNotice?,
    translationMode: UnifiedTranslationButtonMode,
    playbackPositionMs: Long,
    playing: Boolean,
    buffering: Boolean,
    imageLoader: ImageLoader?,
    actions: ClientActions,
    onBackToArtwork: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    revealModifier: Modifier = Modifier,
    transitionRegistry: PlayerTransitionRegistry? = null,
    expandProgress: () -> Float = { 1f },
    sheetTranslationY: () -> Float = { 0f },
    unifiedMode: Boolean = false,
    trackDirection: Int = 1,
    lyricScale: Float = 1f,
    controlScale: Float = 1f,
    interactionEnabled: Boolean = true,
) {
    Column(Modifier.fillMaxSize()) {
        ClassicLyricsTopRow(
            song = song,
            imageLoader = imageLoader,
            onBackToArtwork = onBackToArtwork,
            translationMode = translationMode,
            translationEnabled = translationEnabled,
            targetLanguage = lyricState.translationTargetLanguage,
            onSetTranslationEnabled = actions::setTranslationEnabled,
            onTranslate = actions::requestLyricTranslation,
            trackDirection = trackDirection,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            revealModifier = revealModifier,
            transitionRegistry = transitionRegistry,
            expandProgress = expandProgress,
            sheetTranslationY = sheetTranslationY,
            unifiedMode = unifiedMode,
            controlScale = controlScale,
            interactionEnabled = interactionEnabled,
        )
        ClassicLyricsViewport(
            song = song,
            lyricState = lyricState,
            translationEnabled = translationEnabled,
            translationNotice = translationNotice,
            playbackPositionMs = playbackPositionMs,
            playing = playing,
            buffering = buffering,
            twoColumns = false,
            onSeekTo = actions::seekTo,
            onRetry = actions::retryLyrics,
            onSetTranslationEnabled = actions::setTranslationEnabled,
            onTranslate = actions::requestLyricTranslation,
            showInlineTranslationToggle = false,
            trackDirection = trackDirection,
            lyricScale = lyricScale,
            controlScale = controlScale,
            modifier = Modifier.weight(1f).fillMaxWidth().then(revealModifier),
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ClassicMobileSongStage(
    state: PlaybackSnapshot,
    actions: ClientActions,
    directionTracker: TrackDirectionTracker,
    trackDirection: Int,
    imageLoader: ImageLoader?,
    onOpenLyrics: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    revealModifier: Modifier = Modifier,
    transitionRegistry: PlayerTransitionRegistry? = null,
    expandProgress: () -> Float = { 1f },
    sheetTranslationY: () -> Float = { 0f },
    unifiedMode: Boolean = false,
    controlScale: Float = 1f,
    interactionEnabled: Boolean = true,
) {
    val song = state.current ?: return
    Column(Modifier.fillMaxSize().padding(top = 4.dp)) {
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val minArt = minOf(maxWidth, 136.dp)
            val artSize = minOf(maxWidth, maxHeight.coerceAtLeast(minArt))
            ClassicArtworkCarousel(
                state = state,
                actions = actions,
                directionTracker = directionTracker,
                trackDirection = trackDirection,
                artSize = artSize,
                imageLoader = imageLoader,
                onClick = if (interactionEnabled) onOpenLyrics else null,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                transitionRegistry = transitionRegistry,
                expandProgress = expandProgress,
                sheetTranslationY = sheetTranslationY,
                unifiedMode = unifiedMode,
                modeKey = "mobile_song",
                swipeEnabled = interactionEnabled && (!unifiedMode || expandProgress() >= 0.999f),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(10.dp * controlScale))
        Box(revealModifier) {
            ClassicSongMetadataRow(
                song = song,
                showLyricsButton = true,
                onToggleLyrics = onOpenLyrics,
                trackDirection = trackDirection,
                controlScale = controlScale,
                interactionEnabled = interactionEnabled,
            )
        }
        Spacer(Modifier.height(10.dp * controlScale))
    }
}

@Composable
private fun ClassicDragHandleHeader(
    dragging: Boolean,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    closeClickable: Boolean = true,
) {
    val closeDescription = stringResource(R.string.ft_close)
    val velocityTracker = remember { VelocityTracker() }
    Box(
        modifier.fillMaxWidth()
            .height(ClassicDragHeaderHeight)
            .pointerInput(Unit) {
                var trackedY = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        trackedY = 0f
                        velocityTracker.resetTracking()
                        onDragStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        trackedY += dragAmount
                        velocityTracker.addPosition(change.uptimeMillis, Offset(0f, trackedY))
                        onDragDelta(dragAmount)
                    },
                    onDragEnd = {
                        val velocityY = velocityTracker.calculateVelocity().y
                        onDragEnd(velocityY)
                    },
                    onDragCancel = { onDragEnd(0f) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Compact 48dp x 36dp collapse target with crisp 36dp x 4dp capsule pill (modeled after Musiver).
        Box(
            Modifier.size(width = 48.dp, height = ClassicDragHeaderHeight)
                .clip(CircleShape)
                .then(if (closeClickable) Modifier.clickable(onClick = onClose) else Modifier)
                .testTag("player_close")
                .semantics { contentDescription = closeDescription },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (dragging) 0.72f else 0.42f)),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ClassicArtworkCarousel(
    state: PlaybackSnapshot,
    actions: ClientActions,
    directionTracker: TrackDirectionTracker,
    trackDirection: Int,
    artSize: Dp,
    imageLoader: ImageLoader?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    transitionRegistry: PlayerTransitionRegistry? = null,
    expandProgress: () -> Float = { 1f },
    sheetTranslationY: () -> Float = { 0f },
    unifiedMode: Boolean = false,
    modeKey: String = "classic_artwork",
    swipeEnabled: Boolean = true,
) {
    val song = state.current ?: return
    val haptic = LocalHapticFeedback.current
    val livePositionMsState = rememberPlaybackPositionMsState(state.positionMs)
    val prevPreview = remember(state.queue, state.currentIndex, state.previousQueueIndex, state.repeatMode, state.shuffle) {
        resolveAdjacentQueueSong(state, -1)
    }
    val nextPreview = remember(state.queue, state.currentIndex, state.nextQueueIndex, state.repeatMode, state.shuffle) {
        resolveAdjacentQueueSong(state, +1)
    }
    val hasPreviousTrack = prevPreview != null
    fun canGoPreviousNow(): Boolean = hasPreviousTrack || livePositionMsState.value > 3000L
    val canGoNext = nextPreview != null || (
        state.nextQueueIndex == null && (
            state.currentIndex < state.queue.lastIndex ||
                state.repeatMode == 2 ||
                (state.shuffle && state.queue.size > 1)
            )
        )
    val activePlayback = state.playing || state.buffering
    val carouselController = rememberTrackCarouselController(
        songId = song.id,
        slot = song,
        slideGapDp = 12.dp,
        pausedSwitchMotion = true,
        isExpandedPlayback = { activePlayback },
        dragShrinkThresholdDp = 44.dp,
    )
    TrackCarouselViewport(
        controller = carouselController,
        songId = song.id,
        currentSlot = song,
        direction = trackDirection,
        previousPreviewSlot = prevPreview,
        nextPreviewSlot = nextPreview,
        modifier = modifier.horizontalTrackSwipe(
            enabled = swipeEnabled,
            controller = carouselController,
            directionTracker = directionTracker,
            canGoPrevious = { canGoPreviousNow() },
            canGoNext = { canGoNext },
            isPreviousRestartOnly = { !hasPreviousTrack && livePositionMsState.value > 3000L },
            onCommitHaptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            onNext = actions::next,
            onPrevious = actions::previous,
        ),
        clipVerticalAllowance = 36.dp,
        clipHorizontalAllowance = if (modeKey == "mobile_song") 32.dp else 12.dp,
        clipWhenIdle = false,
        contentAlignment = Alignment.Center,
    ) { slotSong, isActiveSlot, isPreviewSlot ->
        val hasActiveSlide = carouselController.activeSlide != null ||
            carouselController.committedSongId != song.id
        val pausedSwitchHold = carouselController.pauseHoldForSlot(
            hasActiveSlide = hasActiveSlide,
            isActiveSlot = isActiveSlot,
            isPreviewSlot = isPreviewSlot,
        )
        key(slotSong.id) {
            ClassicArtwork(
                song = slotSong,
                imageLoader = imageLoader,
                playing = state.playing,
                buffering = state.buffering,
                modifier = Modifier.size(artSize),
                pausedSwitchHold = pausedSwitchHold,
                onClick = if (isActiveSlot) onClick else null,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                transitionRegistry = transitionRegistry,
                expandProgress = expandProgress,
                sheetTranslationY = sheetTranslationY,
                unifiedMode = unifiedMode,
                modeKey = modeKey,
                isPrimarySlot = isActiveSlot,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ClassicArtwork(
    song: Song,
    imageLoader: ImageLoader?,
    playing: Boolean,
    buffering: Boolean,
    modifier: Modifier = Modifier,
    pausedSwitchHold: Float = 0f,
    onClick: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    transitionRegistry: PlayerTransitionRegistry? = null,
    expandProgress: () -> Float = { 1f },
    sheetTranslationY: () -> Float = { 0f },
    unifiedMode: Boolean = false,
    modeKey: String = "classic_artwork",
    isPrimarySlot: Boolean = true,
) {
    val activePlaying = playing || buffering
    val restingScale by animateFloatAsState(
        targetValue = if (activePlaying) CLASSIC_PLAYING_ARTWORK_SCALE else CLASSIC_PAUSED_ARTWORK_SCALE,
        animationSpec = if (activePlaying) {
            spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessLow)
        } else {
            tween(durationMillis = 320, easing = FastOutSlowInEasing)
        },
        label = "classic_cover_scale",
    )
    val restingAlpha by animateFloatAsState(
        targetValue = if (activePlaying) CLASSIC_PLAYING_ARTWORK_ALPHA else CLASSIC_PAUSED_ARTWORK_ALPHA,
        animationSpec = tween(durationMillis = 300),
        label = "classic_cover_alpha",
    )
    val clampedHold = pausedSwitchHold.coerceIn(0f, 1f)
    val scale = lerp(restingScale, CLASSIC_PAUSED_ARTWORK_SCALE, clampedHold)
    val alpha = lerp(restingAlpha, minOf(restingAlpha, CLASSIC_SWITCH_PAUSED_ALPHA), clampedHold)
    val shape = RoundedCornerShape(FlareTokens.RadiusLg)
    val context = LocalContext.current
    val coverUrl = song.coverUrl
    val artworkRequest = remember(context, coverUrl, imageLoader) {
        if (coverUrl.isNullOrBlank()) null
        else buildCoverImageRequest(
            context = context,
            coverUrl = coverUrl,
            targetPx = PlayerPrewarmArtworkPx,
            imageLoader = imageLoader,
        )
    }
    val sharedModifier = if (isPrimarySlot && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "classic_album_artwork_${song.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 360f) },
            )
        }
    } else {
        Modifier
    }
    Box(
        modifier
            .then(sharedModifier)
            .then(if (isPrimarySlot) Modifier.testTag("player_artwork") else Modifier)
            .then(
                if (isPrimarySlot) {
                    Modifier.onGloballyPositioned { coords ->
                        transitionRegistry?.updateFullArtwork(
                            coords = coords,
                            cornerDp = FlareTokens.RadiusLg,
                            scale = scale,
                            alpha = alpha,
                            expandProgress = expandProgress(),
                            sheetTranslationY = sheetTranslationY(),
                            modeKey = modeKey,
                        )
                    }
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = if (
                    modeKey != "wide" &&
                    isPrimarySlot &&
                    transitionRegistry?.isHandoffActive(expandProgress(), transitionActive = unifiedMode) == true
                ) {
                    0f
                } else {
                    alpha
                }
            }
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.6f), spotColor = Color.Black.copy(alpha = 0.7f))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1F2229), Color(0xFF14161B)),
                ),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            ClientIcons.Music, null, Modifier.fillMaxSize(0.24f),
            tint = Color.White.copy(alpha = 0.42f),
        )
        if (imageLoader != null && artworkRequest != null) {
            AsyncImage(
                model = artworkRequest,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ClassicLyricsTopRow(
    song: Song,
    imageLoader: ImageLoader?,
    onBackToArtwork: () -> Unit,
    translationMode: UnifiedTranslationButtonMode = UnifiedTranslationButtonMode.HIDDEN,
    translationEnabled: Boolean = false,
    targetLanguage: String? = null,
    onSetTranslationEnabled: (Boolean) -> Unit = {},
    onTranslate: () -> Unit = {},
    trackDirection: Int = 1,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    revealModifier: Modifier = Modifier,
    transitionRegistry: PlayerTransitionRegistry? = null,
    expandProgress: () -> Float = { 1f },
    sheetTranslationY: () -> Float = { 0f },
    unifiedMode: Boolean = false,
    controlScale: Float = 1f,
    interactionEnabled: Boolean = true,
) {
    val thumbShape = RoundedCornerShape(10.dp * controlScale)
    val thumbSize = 44.dp * controlScale
    val minRowHeight = (48.dp * controlScale).coerceAtLeast(48.dp)
    val context = LocalContext.current
    val animatorsEnabled = remember(context) { systemAnimatorsEnabled(context) }
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "classic_album_artwork_${song.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 360f) },
            )
        }
    } else {
        Modifier
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp * controlScale),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp * controlScale),
    ) {
        Row(
            Modifier.weight(1f)
                .heightIn(min = minRowHeight)
                .clip(RoundedCornerShape(12.dp * controlScale))
                .then(if (interactionEnabled) Modifier.clickable(onClick = onBackToArtwork) else Modifier)
                .testTag("player_lyrics_top_row")
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp * controlScale),
        ) {
            Box(
                Modifier.size(thumbSize)
                    .then(sharedModifier)
                    .onGloballyPositioned { coords ->
                        transitionRegistry?.updateFullArtwork(
                            coords = coords,
                            cornerDp = 10.dp * controlScale,
                            scale = 1f,
                            alpha = 1f,
                            expandProgress = expandProgress(),
                            sheetTranslationY = sheetTranslationY(),
                            modeKey = "mobile_lyrics",
                        )
                    }
                    .graphicsLayer {
                        alpha = if (transitionRegistry?.isHandoffActive(expandProgress(), transitionActive = unifiedMode) == true) 0f else 1f
                    }
                    .clip(thumbShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.16f), thumbShape),
                contentAlignment = Alignment.Center,
            ) {
                val dir = if (trackDirection < 0) -1 else 1
                AnimatedContent(
                    targetState = song,
                    contentKey = { it.id },
                    transitionSpec = {
                        if (!animatorsEnabled) {
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        } else {
                            (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { dir * it } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -dir * it } + fadeOut(tween(180)))
                        }
                    },
                    label = "lyrics_top_thumb",
                ) { thumbSong ->
                    val thumbCover = thumbSong.coverUrl
                    val thumbRequest = remember(context, thumbCover, imageLoader) {
                        if (thumbCover.isNullOrBlank()) null
                        else buildCoverImageRequest(
                            context = context,
                            coverUrl = thumbCover,
                            targetPx = ThumbArtworkPx,
                            imageLoader = imageLoader,
                        )
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(ClientIcons.Music, null, Modifier.size(20.dp * controlScale), tint = FlareTokens.ClassicSubtitle)
                        if (imageLoader != null && thumbRequest != null) {
                            AsyncImage(
                                model = thumbRequest,
                                imageLoader = imageLoader,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
            AnimatedTrackMetadata(
                song = song,
                direction = trackDirection,
                modifier = Modifier.weight(1f).clipToBounds().then(revealModifier),
            ) { displaySong ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        displaySong.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp * controlScale,
                            lineHeight = 22.sp * controlScale,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = FlareTokens.ClassicTitle,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    val meta = listOf(displaySong.artist.ifBlank { stringResource(R.string.ft_unknown_artist) }, displaySong.album)
                        .filter { it.isNotBlank() }
                        .joinToString(" — ")
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp * controlScale,
                            lineHeight = 20.sp * controlScale,
                        ),
                        color = FlareTokens.ClassicSubtitle,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
            }
        }
        if (translationMode != UnifiedTranslationButtonMode.HIDDEN) {
            Box(revealModifier) {
                ClassicTranslationButton(
                    mode = translationMode,
                    translationEnabled = translationEnabled,
                    targetLanguage = targetLanguage,
                    onToggleDisplay = { onSetTranslationEnabled(!translationEnabled) },
                    onRequestOrRetry = {
                        if (!translationEnabled) onSetTranslationEnabled(true)
                        onTranslate()
                    },
                    controlScale = controlScale,
                    interactionEnabled = interactionEnabled,
                )
            }
        }
        Box(revealModifier) {
            PlayerMoreMenu(song = song, controlScale = controlScale, interactionEnabled = interactionEnabled)
        }
    }
}

private data class LyricsViewportScene(
    val trackKey: String,
    val song: Song,
    val lyricState: LyricState,
)

@Composable
private fun ClassicLyricsViewport(
    song: Song,
    lyricState: LyricState,
    translationEnabled: Boolean,
    translationNotice: ClientNotice?,
    playbackPositionMs: Long,
    playing: Boolean,
    buffering: Boolean,
    twoColumns: Boolean,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    onSetTranslationEnabled: (Boolean) -> Unit,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier,
    showInlineTranslationToggle: Boolean = true,
    trackDirection: Int = 1,
    lyricScale: Float = 1f,
    controlScale: Float = 1f,
) {
    val context = LocalContext.current
    val animatorsEnabled = remember(context) { systemAnimatorsEnabled(context) }
    val density = LocalDensity.current
    val primarySize = 26.sp * lyricScale
    val primaryLineHeight = 34.sp * lyricScale
    val secondarySize = 16.sp * lyricScale
    val secondaryLineHeight = 23.sp * lyricScale
    val lineSpacingPx = with(density) { (22.dp * lyricScale).toPx() }
    val fallbackHeightPx = with(density) { (46.dp * lyricScale).toPx() }

    val currentTrackKey = lyricState.songId?.takeIf { it.isNotBlank() } ?: song.id
    val currentScene = LyricsViewportScene(trackKey = currentTrackKey, song = song, lyricState = lyricState)
    val dir = if (trackDirection < 0) -1 else 1
    val translationMode = resolveUnifiedTranslationButtonMode(lyricState)
    val showBottomTranslationButton = showInlineTranslationToggle && translationMode != UnifiedTranslationButtonMode.HIDDEN

    // Direct floating lyrics viewport (borderless & cardless, solved via Musiver spring layout).
    Column(modifier = modifier) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            contentAlignment = if (twoColumns) Alignment.CenterStart else Alignment.Center,
        ) {
            AnimatedContent(
                targetState = currentScene,
                contentKey = { it.trackKey },
                transitionSpec = {
                    if (!animatorsEnabled) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        val enter = slideInHorizontally(
                            animationSpec = tween(durationMillis = 280, delayMillis = 20, easing = FastOutSlowInEasing),
                        ) { width -> dir * (width * 0.22f).roundToInt() } +
                            fadeIn(tween(durationMillis = 240, delayMillis = 20))
                        val exit = slideOutHorizontally(
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        ) { width -> -dir * (width * 0.22f).roundToInt() } +
                            fadeOut(tween(durationMillis = 160))
                        enter togetherWith exit
                    }
                },
                label = "lyrics_viewport_track_slide",
            ) { scene ->
                ClassicLyricsSceneContent(
                    scene = scene,
                    isPrimaryScene = scene.trackKey == currentTrackKey,
                    translationEnabled = translationEnabled,
                    playbackPositionMs = playbackPositionMs,
                    playing = playing,
                    buffering = buffering,
                    twoColumns = twoColumns,
                    onSeekTo = onSeekTo,
                    onRetry = onRetry,
                    animatorsEnabled = animatorsEnabled,
                    primarySize = primarySize,
                    primaryLineHeight = primaryLineHeight,
                    secondarySize = secondarySize,
                    secondaryLineHeight = secondaryLineHeight,
                    lineSpacingPx = lineSpacingPx,
                    fallbackHeightPx = fallbackHeightPx,
                    lyricScale = lyricScale,
                )
            }
        }

        // Unified bottom-left lyric translation control (for two-column view) or storage error notice.
        if (showBottomTranslationButton || translationNotice != null) {
            val stripModifier = if (twoColumns) {
                Modifier.fillMaxWidth().height(ClassicTransportHeight * controlScale).padding(start = 4.dp)
            } else {
                Modifier.fillMaxWidth().heightIn(min = (48.dp * controlScale).coerceAtLeast(48.dp))
                    .padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
            }
            Box(stripModifier, contentAlignment = Alignment.CenterStart) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBottomTranslationButton) {
                        ClassicTranslationButton(
                            mode = translationMode,
                            translationEnabled = translationEnabled,
                            targetLanguage = lyricState.translationTargetLanguage,
                            onToggleDisplay = { onSetTranslationEnabled(!translationEnabled) },
                            onRequestOrRetry = {
                                if (!translationEnabled) onSetTranslationEnabled(true)
                                onTranslate()
                            },
                            controlScale = controlScale,
                        )
                    }
                    if (translationNotice != null) {
                        Text(
                            text = stringResource(R.string.ft_notice_storage_failed),
                            style = MaterialTheme.typography.labelSmall,
                            color = FlareTokens.ClassicVibrantSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicLyricsSceneContent(
    scene: LyricsViewportScene,
    isPrimaryScene: Boolean,
    translationEnabled: Boolean,
    playbackPositionMs: Long,
    playing: Boolean,
    buffering: Boolean,
    twoColumns: Boolean,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    animatorsEnabled: Boolean,
    primarySize: androidx.compose.ui.unit.TextUnit,
    primaryLineHeight: androidx.compose.ui.unit.TextUnit,
    secondarySize: androidx.compose.ui.unit.TextUnit,
    secondaryLineHeight: androidx.compose.ui.unit.TextUnit,
    lineSpacingPx: Float,
    fallbackHeightPx: Float,
    lyricScale: Float,
) {
    val song = scene.song
    val lyricState = scene.lyricState
    val controller = remember(scene.trackKey) { LyricsSpringLayoutController() }
    val density = LocalDensity.current
    var userScrolling by remember(scene.trackKey) { mutableStateOf(false) }
    val interactionSource = remember(scene.trackKey) { MutableInteractionSource() }
    var scrollGeneration by remember(scene.trackKey) { mutableIntStateOf(0) }
    val livePositionMsState = rememberPlaybackPositionMsState(playbackPositionMs)

    class AnchorSnapshot(var key: Any? = null, var baselineY: Float? = null, var slotIdx: Int = 0)
    class SceneTracker(
        var activeIdx: Int = Int.MIN_VALUE,
        var hadIntro: Boolean = false,
        var translation: Boolean = false,
        var widthPx: Int = 0,
        var heightPx: Int = 0,
        var userScrolling: Boolean = false,
        var dragAnchorIdx: Int = -1,
    )

    val anchorSnapshot = remember(scene.trackKey) { AnchorSnapshot() }
    val sceneTracker = remember(scene.trackKey) {
        SceneTracker(translation = translationEnabled)
    }

    LaunchedEffect(controller) {
        controller.runAnimationLoop()
    }

    val scrollableState = rememberScrollableState { delta ->
        controller.applyScrollDelta(delta)
    }

    // Programmatic layout solves must never start the user's browsing pause or cancel themselves.
    LaunchedEffect(interactionSource, scrollableState) {
        var resume: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    resume?.cancel()
                    scrollGeneration++
                    if (!userScrolling) {
                        sceneTracker.dragAnchorIdx = anchorSnapshot.slotIdx
                        controller.snapScrollToCurrentAnchor(
                            anchorKey = anchorSnapshot.key,
                            baselineAnchorY = anchorSnapshot.baselineY,
                        )
                        userScrolling = true
                    }
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    val gen = ++scrollGeneration
                    resume?.cancel()
                    resume = launch {
                        snapshotFlow { scrollableState.isScrollInProgress }.first { !it }
                        delay(LyricMotionSpecs.USER_SCROLL_DEBOUNCE_MS)
                        if (scrollGeneration == gen) {
                            sceneTracker.dragAnchorIdx = -1
                            controller.resetScrollOffset()
                            userScrolling = false
                        }
                    }
                }
            }
        }
    }

    val activeIdx = lyricState.activeLyricIndex
    val hasIntroInterlude by remember(
        lyricState.status,
        lyricState.lines,
        activeIdx,
        userScrolling,
        lyricState.syncMode,
        livePositionMsState,
    ) {
        derivedStateOf {
            if (lyricState.status == LyricStatus.READY && lyricState.lines.isNotEmpty() && activeIdx <= 0) {
                computeInterludeUiState(
                    currentTimeSeconds = livePositionMsState.value / 1000.0,
                    lines = lyricState.lines,
                    activeIndex = activeIdx,
                    lineIndex = 0,
                    userScrolling = userScrolling,
                    syncMode = lyricState.syncMode,
                ).let { it.isInterlude && it.window.kind == InterludeKind.INTRO }
            } else {
                false
            }
        }
    }

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = if (twoColumns) Alignment.CenterStart else Alignment.Center,
    ) {
        when (lyricState.status) {
                LyricStatus.LOADING -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = FlareTokens.ClassicVibrantPrimary)
                    }
                }
                LyricStatus.INSTRUMENTAL -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.ft_instrumental_lyrics),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = if (twoColumns) 24.sp else 20.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = FlareTokens.ClassicVibrantPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                LyricStatus.FAILED -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                stringResource(R.string.ft_lyrics_failed),
                                style = MaterialTheme.typography.bodyLarge,
                                color = FlareTokens.ClassicVibrantSecondary,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(
                                onClick = onRetry,
                                colors = ButtonDefaults.textButtonColors(contentColor = FlareTokens.ClassicIconActive),
                            ) {
                                Text(stringResource(R.string.ft_retry))
                            }
                        }
                    }
                }
                LyricStatus.READY -> if (lyricState.lines.isNotEmpty()) {
                    // Keep lyrics scroll gestures self-contained so scrolling lyrics does not pull the sheet.
                    val absorbLyricsOverscroll = remember {
                        object : NestedScrollConnection {
                            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                                Offset(0f, available.y)

                            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                                Velocity(0f, available.y)
                        }
                    }
                    val lines = lyricState.lines
                    val totalSlots = lines.size
                    val slotKeys = remember(lyricState.songId, lines) {
                        ArrayList<Any>(totalSlots).apply {
                            lines.forEachIndexed { index, line ->
                                add("line:$index:${line.timeSeconds}:${line.text}")
                            }
                        }
                    }
                    val presentations = remember(activeIdx, lines.size, userScrolling, hasIntroInterlude) {
                        ArrayList<LyricLinePresentation>(totalSlots).apply {
                            val effectiveActiveIndex = if (activeIdx < 0 && hasIntroInterlude) 0 else activeIdx
                            for (index in lines.indices) {
                                add(
                                    resolveLyricPresentation(
                                        index = index,
                                        activeIndex = effectiveActiveIndex,
                                        lineCount = lines.size,
                                        userScrolling = userScrolling,
                                    ),
                                )
                            }
                        }
                    }
                    val pushYPxList = remember(presentations, density) {
                        presentations.map { with(density) { it.verticalOffset.toPx() } }
                    }
                    val blurPxList = remember(presentations, density) {
                        presentations.map { with(density) { it.blurRadius.toPx() } }
                    }

                    SubcomposeLayout(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (isPrimaryScene) Modifier.testTag("player_lyrics_list") else Modifier)
                            .clipToBounds()
                            .nestedScroll(absorbLyricsOverscroll)
                            .scrollable(
                                state = scrollableState,
                                orientation = Orientation.Vertical,
                                reverseDirection = false,
                                flingBehavior = ScrollableDefaults.flingBehavior(),
                                interactionSource = interactionSource,
                            ),
                    ) { constraints ->
                        // Subscribe to manual drag/fling offset changes inside layout phase.
                        controller.readScrollInvalidation()

                        val viewportWidth = constraints.maxWidth
                        val viewportHeight = constraints.maxHeight
                        val containerHeight = viewportHeight.toFloat()

                        if (
                            sceneTracker.widthPx != 0 &&
                            (sceneTracker.widthPx != viewportWidth || sceneTracker.translation != translationEnabled)
                        ) {
                            controller.invalidateHeights()
                        }

                        val playbackAnchorIdx = if (activeIdx >= 0) {
                            activeIdx.coerceIn(0, totalSlots - 1)
                        } else {
                            0
                        }
                        val anchorIdx = if (userScrolling && sceneTracker.dragAnchorIdx in 0 until totalSlots) {
                            sceneTracker.dragAnchorIdx
                        } else {
                            playbackAnchorIdx
                        }
                        val itemConstraints = Constraints(
                            minWidth = viewportWidth,
                            maxWidth = viewportWidth,
                            minHeight = 0,
                            maxHeight = Constraints.Infinity,
                        )
                        val overscanPx = max(320f, containerHeight * 0.6f)
                        val measuredPlaceables = HashMap<Int, Placeable>()

                        fun buildItems(): List<LyricsSpringLayoutItem> =
                            ArrayList<LyricsSpringLayoutItem>(totalSlots).apply {
                                for (i in 0 until totalSlots) {
                                    add(
                                        LyricsSpringLayoutItem(
                                            index = i,
                                            height = controller.measuredHeightFor(slotKeys[i]) ?: fallbackHeightPx,
                                            presentation = presentations[i],
                                        ),
                                    )
                                }
                            }

                        fun measureSlot(slotIndex: Int) {
                            if (slotIndex in measuredPlaceables) return
                            val key = slotKeys[slotIndex]
                            val hadCachedHeight = controller.measuredHeightFor(key) != null
                            val measurables = subcompose(key) {
                                val lineIndex = slotIndex
                                val line = lines[lineIndex]
                                val isActive = lineIndex == activeIdx
                                val isInterludeCandidate = isActive || (lineIndex == 0 && activeIdx <= 0)
                                val interludeWindow by remember(isInterludeCandidate, lines, activeIdx, lineIndex, userScrolling, lyricState.syncMode) {
                                    derivedStateOf {
                                        if (!isInterludeCandidate || userScrolling) {
                                            null
                                        } else {
                                            val sec = livePositionMsState.value / 1000.0
                                            val validTime = if (sec.isFinite() && sec >= 0.0) sec else 0.0
                                            val w = resolveInterludeWindow(validTime, lines, activeIdx, lineIndex, lyricState.syncMode)
                                            val start = w.startSeconds
                                            val end = w.endSeconds
                                            if (w.active && start != null && end != null && validTime >= start && validTime < end) {
                                                w
                                            } else {
                                                null
                                            }
                                        }
                                    }
                                }
                                val isInterlude = interludeWindow != null
                                val interludeTransition = updateTransition(
                                    targetState = isInterlude,
                                    label = "interlude_host",
                                )
                                val dotsAlpha by interludeTransition.animateFloat(
                                    transitionSpec = {
                                        when {
                                            !animatorsEnabled -> snap()
                                            targetState -> tween(durationMillis = 450, easing = InterludeHostEasing)
                                            else -> keyframes {
                                                durationMillis = 850
                                                1f at 0
                                                1f at 580
                                                0f at 850 using InterludeExitCollapseEasing
                                            }
                                        }
                                    },
                                    label = "interlude_dots_alpha",
                                ) { active -> if (active) 1f else 0f }
                                val dotsScale by interludeTransition.animateFloat(
                                    transitionSpec = {
                                        when {
                                            !animatorsEnabled -> snap()
                                            targetState -> keyframes {
                                                durationMillis = 450
                                                0.94f at 0
                                                1f at 450 using InterludeHostEasing
                                            }
                                            else -> keyframes {
                                                durationMillis = 850
                                                1f at 0
                                                1.20f at 580 using InterludeExitSwellEasing
                                                0.50f at 850 using InterludeExitCollapseEasing
                                            }
                                        }
                                    },
                                    label = "interlude_dots_scale",
                                ) { active -> if (active) 1f else 0.50f }
                                val lyricRevealProgress by interludeTransition.animateFloat(
                                    transitionSpec = {
                                        when {
                                            !animatorsEnabled -> snap()
                                            targetState -> tween(durationMillis = 450, easing = InterludeHostEasing)
                                            else -> keyframes {
                                                durationMillis = 850
                                                0f at 0
                                                0f at 500
                                                1f at 850 using InterludeHostEasing
                                            }
                                        }
                                    },
                                    label = "interlude_lyric_reveal",
                                ) { active -> if (active) 0f else 1f }
                                var retainedWindow by remember(key) { mutableStateOf<InterludeWindow?>(null) }
                                if (interludeWindow != null) {
                                    retainedWindow = interludeWindow
                                } else if (dotsAlpha <= 0.01f) {
                                    retainedWindow = null
                                }

                                val clickableModifier = if (!isInterlude) {
                                    line.timeSeconds?.let { sec ->
                                        Modifier.clickable {
                                            scrollGeneration++
                                            sceneTracker.dragAnchorIdx = -1
                                            controller.resetScrollOffset()
                                            userScrolling = false
                                            onSeekTo((sec * 1000.0).toLong().coerceAtLeast(0L))
                                        }
                                    } ?: Modifier
                                } else {
                                    Modifier
                                }

                                Box(
                                    Modifier.fillMaxWidth()
                                        .padding(start = 4.dp, end = 8.dp)
                                        .then(if (isPrimaryScene) Modifier.testTag("player_lyric_$lineIndex") else Modifier)
                                        .semantics { selected = isActive || (lineIndex == 0 && activeIdx < 0 && isInterlude) }
                                        .then(clickableModifier),
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth()
                                            .graphicsLayer {
                                                val reveal = lyricRevealProgress.coerceIn(0f, 1f)
                                                val hidden = 1f - reveal
                                                alpha = reveal
                                                val scale = 1f - 0.03f * hidden
                                                scaleX = scale
                                                scaleY = scale
                                                transformOrigin = TransformOrigin(0f, 0.5f)
                                            }
                                            .padding(vertical = 4.dp),
                                    ) {
                                        val wordSynced = isActive && lyricState.syncMode == "word" && line.words.isNotEmpty()
                                        val primaryColor = if (isActive) FlareTokens.ClassicVibrantPrimary else Color.White
                                        val primaryShadow: Shadow? = if (isActive) {
                                            Shadow(color = Color(0x66000000), offset = Offset(0f, 2f), blurRadius = 10f)
                                        } else {
                                            null
                                        }

                                        if (wordSynced) {
                                            KaraokeLyricText(
                                                line = line,
                                                basePositionMs = playbackPositionMs,
                                                playing = playing && !buffering,
                                                fontSize = primarySize,
                                                lineHeight = primaryLineHeight,
                                                shadow = primaryShadow,
                                                positionMsState = livePositionMsState,
                                            )
                                        } else {
                                            Text(
                                                text = line.text.ifBlank { "…" },
                                                style = MaterialTheme.typography.headlineSmall.copy(
                                                    fontSize = primarySize,
                                                    lineHeight = primaryLineHeight,
                                                    fontWeight = FontWeight.Bold,
                                                    shadow = primaryShadow,
                                                ),
                                                color = primaryColor,
                                            )
                                        }

                                        val translation = if (translationEnabled) line.translation?.trim().orEmpty() else ""
                                        if (translation.isNotBlank()) {
                                            Text(
                                                text = translation,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontSize = secondarySize,
                                                    lineHeight = secondaryLineHeight,
                                                    fontWeight = FontWeight.Normal,
                                                    shadow = primaryShadow,
                                                ),
                                                color = if (isActive) Color(0xCCFFFFFF) else Color(0x8CFFFFFF),
                                                modifier = Modifier.padding(top = 5.dp),
                                            )
                                        }
                                    }

                                    retainedWindow?.let { activeWindow ->
                                        if (isInterlude || dotsAlpha > 0.01f) {
                                            val displayInterlude = remember(activeWindow, isInterlude) {
                                                if (isInterlude) {
                                                    InterludeUiState(isInterlude = true, window = activeWindow)
                                                } else {
                                                    InterludeUiState(
                                                        isInterlude = true,
                                                        dotFills = listOf(1f, 1f, 1f),
                                                        dotStates = List(3) { InterludeDotFillState.COMPLETE },
                                                        window = activeWindow,
                                                    )
                                                }
                                            }
                                            Box(
                                                Modifier.matchParentSize()
                                                    .graphicsLayer {
                                                        alpha = dotsAlpha.coerceIn(0f, 1f)
                                                        scaleX = dotsScale
                                                        scaleY = dotsScale
                                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                                    },
                                                contentAlignment = Alignment.CenterStart,
                                            ) {
                                                InterludeDotsRow(
                                                    interludeState = displayInterlude,
                                                    playbackPositionMs = playbackPositionMs,
                                                    active = isInterlude && playing && !buffering,
                                                    lyricScale = lyricScale,
                                                    interactive = isInterlude,
                                                    onSeekTo = onSeekTo,
                                                    positionMsState = livePositionMsState,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            val placeable = measurables.first().measure(itemConstraints)
                            measuredPlaceables[slotIndex] = placeable
                            val measuredH = placeable.height.toFloat()
                            val prevRecorded = controller.recordMeasuredHeight(key, measuredH)
                            if (userScrolling && !hadCachedHeight && prevRecorded == null && slotIndex < anchorIdx) {
                                controller.compensateOffscreenHeightDelta(measuredH - fallbackHeightPx)
                            }
                        }

                        // Pass 1: estimate visible range and measure those rows + anchor.
                        if (containerHeight > 0f) {
                            val roughItems = buildItems()
                            val roughTargets = solveLyricsSpringLayout(
                                items = roughItems,
                                containerHeight = containerHeight,
                                alignOffset = anchorIdx,
                                scrollOffset = controller.currentScrollOffset(),
                                lineSpacing = lineSpacingPx,
                                staggerEnabled = false,
                            )
                            measureSlot(anchorIdx)
                            for (i in 0 until totalSlots) {
                                val t = roughTargets[i]
                                val bounds = controller.estimateRenderBoundsY(slotKeys[i], t.y, t.height)
                                if (bounds.endInclusive >= -overscanPx && bounds.start <= containerHeight + overscanPx) {
                                    measureSlot(i)
                                }
                            }

                            // Pass 2: if measured heights revealed additional rows inside overscan, measure them too.
                            val refinedItems = buildItems()
                            val refinedTargets = solveLyricsSpringLayout(
                                items = refinedItems,
                                containerHeight = containerHeight,
                                alignOffset = anchorIdx,
                                scrollOffset = controller.currentScrollOffset(),
                                lineSpacing = lineSpacingPx,
                                staggerEnabled = false,
                            )
                            var measuredExtra = false
                            for (i in 0 until totalSlots) {
                                if (i in measuredPlaceables) continue
                                val t = refinedTargets[i]
                                val bounds = controller.estimateRenderBoundsY(slotKeys[i], t.y, t.height)
                                if (bounds.endInclusive >= -overscanPx && bounds.start <= containerHeight + overscanPx) {
                                    measureSlot(i)
                                    measuredExtra = true
                                }
                            }

                            val finalItems = if (measuredExtra) buildItems() else refinedItems
                            val limits = computeLyricsScrollLimits(
                                items = finalItems,
                                containerHeight = containerHeight,
                                alignOffset = anchorIdx,
                                lineSpacing = lineSpacingPx,
                            )
                            controller.updateScrollLimits(limits)

                            val scene = when {
                                userScrolling -> LyricsMotionScene.INSTANT
                                sceneTracker.widthPx != 0 && (
                                    sceneTracker.widthPx != viewportWidth ||
                                        sceneTracker.heightPx != viewportHeight ||
                                        sceneTracker.translation != translationEnabled
                                    ) -> LyricsMotionScene.CONTAINER_RESIZE
                                sceneTracker.userScrolling && !userScrolling -> LyricsMotionScene.CONTAINER_RESIZE
                                sceneTracker.activeIdx == Int.MIN_VALUE -> LyricsMotionScene.INSTANT
                                sceneTracker.activeIdx != activeIdx -> {
                                    val jump = if (
                                        sceneTracker.activeIdx < 0 &&
                                        activeIdx in 0..2 &&
                                        (sceneTracker.hadIntro || activeIdx <= 1)
                                    ) {
                                        activeIdx - sceneTracker.activeIdx
                                    } else if (sceneTracker.activeIdx < 0 || activeIdx < 0) {
                                        Int.MAX_VALUE
                                    } else {
                                        kotlin.math.abs(activeIdx - sceneTracker.activeIdx)
                                    }
                                    if (jump in 1..4) LyricsMotionScene.LINE_ADVANCE else LyricsMotionScene.INSTANT
                                }
                                else -> LyricsMotionScene.SPRING
                            }

                            val staggerEnabled = scene == LyricsMotionScene.LINE_ADVANCE && animatorsEnabled
                            val finalTargets = solveLyricsSpringLayout(
                                items = finalItems,
                                containerHeight = containerHeight,
                                alignOffset = anchorIdx,
                                scrollOffset = controller.currentScrollOffset(),
                                lineSpacing = lineSpacingPx,
                                staggerEnabled = staggerEnabled,
                            )

                            anchorSnapshot.key = slotKeys[anchorIdx]
                            anchorSnapshot.baselineY =
                                containerHeight * LyricMotionSpecs.ALIGN_POSITION - finalItems[anchorIdx].height / 2f
                            anchorSnapshot.slotIdx = anchorIdx

                            sceneTracker.activeIdx = activeIdx
                            sceneTracker.hadIntro = hasIntroInterlude
                            sceneTracker.translation = translationEnabled
                            sceneTracker.widthPx = viewportWidth
                            sceneTracker.heightPx = viewportHeight
                            sceneTracker.userScrolling = userScrolling

                            controller.commitTargets(
                                keys = slotKeys,
                                targets = finalTargets,
                                presentations = presentations,
                                pushYPxList = pushYPxList,
                                blurPxList = blurPxList,
                                scene = scene,
                                animatorsEnabled = animatorsEnabled,
                                visibleIndices = measuredPlaceables.keys,
                            )
                        }

                        layout(viewportWidth, viewportHeight) {
                            measuredPlaceables.forEach { (slotIndex, placeable) ->
                                val key = slotKeys[slotIndex]
                                val rowState = controller.visualStateFor(key)
                                val targetY = (rowState?.displayY ?: 0f) + (rowState?.displayPushYPx ?: 0f)
                                val yInt = targetY.roundToInt()
                                val subPixelY = targetY - yInt
                                placeable.placeRelativeWithLayer(0, yInt) {
                                    val liveState = controller.visualStateFor(key)
                                    if (liveState != null) {
                                        val liveY = liveState.displayY + liveState.displayPushYPx
                                        translationY = liveY - yInt
                                        scaleX = liveState.displayScale
                                        scaleY = liveState.displayScale
                                        alpha = liveState.displayOpacity
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    } else {
                                        translationY = subPixelY
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ClassicLyricsEmptyPlaceholder(song)
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ClassicLyricsEmptyPlaceholder(song)
                }
            }
        }
}

internal fun computeKaraokeFocusCharOffset(words: List<LyricWord>, currentSec: Double): Float {
    var cursor = 0
    for (word in words) {
        val len = word.text.length
        if (currentSec <= word.startTimeSeconds) return cursor.toFloat()
        if (currentSec < word.endTimeSeconds) {
            val span = (word.endTimeSeconds - word.startTimeSeconds).coerceAtLeast(0.05)
            val fraction = ((currentSec - word.startTimeSeconds) / span).toFloat().coerceIn(0f, 1f)
            return cursor + fraction * len
        }
        cursor += len
    }
    return cursor.toFloat()
}

@Composable
internal fun KaraokeLyricText(
    line: LyricLine,
    basePositionMs: Long,
    playing: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    shadow: Shadow? = null,
    sungColor: Color = FlareTokens.ClassicVibrantPrimary,
    unsungColor: Color = Color(0x99FFFFFF),
    fontWeight: FontWeight = FontWeight.Bold,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    trackHorizontalFocus: Boolean = false,
    positionMsState: State<Long>? = null,
    modifier: Modifier = Modifier,
) {
    val fullText = remember(line) {
        if (line.words.isNotEmpty()) line.words.joinToString(separator = "") { it.text } else line.text
    }
    val displayPosMsState = remember(line) { mutableLongStateOf(positionMsState?.value ?: basePositionMs) }
    val endMs = remember(line) {
        ((line.endTimeSeconds ?: line.words.lastOrNull()?.endTimeSeconds ?: 0.0) * 1000.0).toLong() + 300L
    }
    LaunchedEffect(line, basePositionMs, playing, positionMsState) {
        var anchorPos = positionMsState?.value ?: basePositionMs
        displayPosMsState.longValue = anchorPos
        if (!playing) {
            if (positionMsState != null) {
                snapshotFlow { positionMsState.value }.collect { latest ->
                    displayPosMsState.longValue = latest
                }
            }
            return@LaunchedEffect
        }
        var startWall = SystemClock.uptimeMillis()
        while (isActive && displayPosMsState.longValue < endMs) {
            withFrameNanos {
                val external = positionMsState?.value
                if (external != null && kotlin.math.abs(external - displayPosMsState.longValue) > 350L) {
                    anchorPos = external
                    startWall = SystemClock.uptimeMillis()
                }
                displayPosMsState.longValue = anchorPos + (SystemClock.uptimeMillis() - startWall)
            }
        }
    }

    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        shadow = shadow,
    )
    var textLayout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }

    // Draw-layer word-by-word sweep: overlays a static sung Text above a static unsung Text
    // and clips per visual line using TextLayoutResult, eliminating AnnotatedString rebuilds.
    val sungSweepModifier = Modifier.drawWithContent {
        val layout = textLayout
        val totalChars = fullText.length
        if (layout == null || totalChars == 0 || line.words.isEmpty()) {
            drawContent()
            return@drawWithContent
        }
        val focusChar = computeKaraokeFocusCharOffset(line.words, displayPosMsState.longValue / 1000.0)
            .coerceIn(0f, totalChars.toFloat())
        if (focusChar <= 0.001f) return@drawWithContent
        if (focusChar >= totalChars - 0.001f) {
            drawContent()
            return@drawWithContent
        }
        val lowIdx = floor(focusChar).toInt().coerceIn(0, totalChars)
        val highIdx = ceil(focusChar).toInt().coerceIn(0, totalChars)
        val activeVisualLine = layout.getLineForOffset(lowIdx).coerceIn(0, layout.lineCount - 1)
        for (l in 0 until activeVisualLine) {
            clipRect(
                left = -4f,
                top = layout.getLineTop(l),
                right = size.width + 4f,
                bottom = layout.getLineBottom(l),
            ) {
                this@drawWithContent.drawContent()
            }
        }
        val isRtl = layout.getParagraphDirection(activeVisualLine) == ResolvedTextDirection.Rtl
        val lineLeft = layout.getLineLeft(activeVisualLine)
        val lineRight = layout.getLineRight(activeVisualLine)
        val lowX = layout.getHorizontalPosition(lowIdx, usePrimaryDirection = true)
        val highX = if (layout.getLineForOffset(highIdx) == activeVisualLine) {
            layout.getHorizontalPosition(highIdx, usePrimaryDirection = true)
        } else if (isRtl) {
            lineLeft
        } else {
            lineRight
        }
        val sweepX = lowX + (highX - lowX) * (focusChar - lowIdx)
        val lineTop = layout.getLineTop(activeVisualLine)
        val lineBottom = layout.getLineBottom(activeVisualLine)
        if (isRtl) {
            clipRect(left = sweepX, top = lineTop, right = size.width + 4f, bottom = lineBottom) {
                this@drawWithContent.drawContent()
            }
        } else {
            clipRect(left = -4f, top = lineTop, right = sweepX, bottom = lineBottom) {
                this@drawWithContent.drawContent()
            }
        }
    }

    if (!trackHorizontalFocus) {
        Box(modifier) {
            Text(
                text = fullText,
                style = textStyle,
                color = unsungColor,
                maxLines = maxLines,
                overflow = overflow,
                textAlign = textAlign,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                text = fullText,
                style = textStyle,
                color = sungColor,
                maxLines = maxLines,
                overflow = overflow,
                textAlign = textAlign,
                onTextLayout = { textLayout = it },
                modifier = sungSweepModifier,
            )
        }
        return
    }

    var overflowMetrics by remember(line) { mutableStateOf(0 to 0) }
    val containerW = overflowMetrics.first
    val textW = overflowMetrics.second
    val maxScrollPx = (textW - containerW).coerceAtLeast(0)
    val scrollOffsetState = remember(line) { mutableFloatStateOf(0f) }

    fun resolveHorizontalScroll(): Float {
        val layout = textLayout
        if (layout == null || maxScrollPx <= 0) return 0f
        val totalChars = fullText.length
        if (totalChars <= 0) return 0f
        val focusChar = computeKaraokeFocusCharOffset(line.words, displayPosMsState.longValue / 1000.0)
            .coerceIn(0f, totalChars.toFloat())
        val lowIdx = floor(focusChar).toInt().coerceIn(0, totalChars)
        val highIdx = ceil(focusChar).toInt().coerceIn(0, totalChars)
        val lowX = layout.getHorizontalPosition(lowIdx, usePrimaryDirection = true)
        val highX = layout.getHorizontalPosition(highIdx, usePrimaryDirection = true)
        val focusX = lowX + (highX - lowX) * (focusChar - lowIdx)
        return (focusX - containerW / 2f).coerceIn(0f, maxScrollPx.toFloat())
    }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .then(
                if (maxScrollPx > 0) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val edgePx = 14.dp.toPx().coerceAtMost(size.width * 0.16f)
                            val edgeFrac = (edgePx / size.width.coerceAtLeast(1f)).coerceIn(0.01f, 0.25f)
                            val currentScroll = scrollOffsetState.floatValue
                            val leftFaded = currentScroll > 1f
                            val rightFaded = currentScroll < maxScrollPx - 1f
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black.copy(alpha = if (leftFaded) 0f else 1f),
                                    edgeFrac to Color.Black,
                                    (1f - edgeFrac) to Color.Black,
                                    1f to Color.Black.copy(alpha = if (rightFaded) 0f else 1f),
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                } else {
                    Modifier
                },
            ),
        content = {
            Box(
                Modifier.graphicsLayer {
                    if (maxScrollPx > 0) {
                        val targetScroll = resolveHorizontalScroll()
                        scrollOffsetState.floatValue = targetScroll
                        translationX = -targetScroll
                    } else {
                        scrollOffsetState.floatValue = 0f
                    }
                },
            ) {
                Text(
                    text = fullText,
                    style = textStyle,
                    color = unsungColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Text(
                    text = fullText,
                    style = textStyle,
                    color = sungColor,
                    maxLines = 1,
                    softWrap = false,
                    onTextLayout = { textLayout = it },
                    modifier = sungSweepModifier,
                )
            }
        },
    ) { measurables, constraints ->
        val textPlaceable = measurables.first().measure(
            constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity),
        )
        val viewportW = if (constraints.maxWidth == Constraints.Infinity) {
            textPlaceable.width
        } else {
            constraints.maxWidth
        }
        if (overflowMetrics.first != viewportW || overflowMetrics.second != textPlaceable.width) {
            overflowMetrics = viewportW to textPlaceable.width
        }
        layout(viewportW, textPlaceable.height) {
            val xOffset = if (textPlaceable.width <= viewportW && textAlign == TextAlign.Center) {
                (viewportW - textPlaceable.width) / 2
            } else {
                0
            }
            textPlaceable.place(xOffset, 0)
        }
    }
}

internal fun systemAnimatorsEnabled(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 26) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }

private val InterludeHostEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val InterludeDotFillEasing = CubicBezierEasing(0f, 0.5f, 1f, 0.6f)
private val InterludeExitSwellEasing = CubicBezierEasing(0.5f, 0.1f, 0.5f, 1.0f)
private val InterludeExitCollapseEasing = CubicBezierEasing(0.5f, 0f, 1f, 0.2f)

@Composable
private fun InterludeDotsRow(
    interludeState: InterludeUiState,
    playbackPositionMs: Long,
    active: Boolean,
    lyricScale: Float = 1f,
    interactive: Boolean = true,
    onSeekTo: (Long) -> Unit,
    positionMsState: State<Long>? = null,
) {
    val context = LocalContext.current
    val animatorsEnabled = remember(context) { systemAnimatorsEnabled(context) }
    val window = interludeState.window
    val startMs = remember(window) { ((window.startSeconds ?: 0.0) * 1000.0).toLong() }
    val endMs = remember(window) { ((window.endSeconds ?: 0.0) * 1000.0).toLong() }
    var displayPosMs by remember(window) { mutableLongStateOf(positionMsState?.value ?: playbackPositionMs) }

    LaunchedEffect(window, playbackPositionMs, active, animatorsEnabled, positionMsState) {
        var anchorPos = positionMsState?.value ?: playbackPositionMs
        displayPosMs = anchorPos
        if (!active || !animatorsEnabled || !window.active || anchorPos >= endMs) {
            if (positionMsState != null && window.active) {
                snapshotFlow { positionMsState.value }.collect { latest ->
                    displayPosMs = latest.coerceAtMost(endMs)
                }
            }
            return@LaunchedEffect
        }
        var startWall = SystemClock.uptimeMillis()
        while (isActive && displayPosMs < endMs) {
            withFrameNanos {
                val external = positionMsState?.value
                if (external != null && kotlin.math.abs(external - displayPosMs) > 350L) {
                    anchorPos = external
                    startWall = SystemClock.uptimeMillis()
                }
                displayPosMs = (anchorPos + (SystemClock.uptimeMillis() - startWall)).coerceAtMost(endMs)
            }
        }
    }

    val liveState = remember(displayPosMs, interludeState, active) {
        if (!active && interludeState.dotStates.all { it == InterludeDotFillState.COMPLETE }) {
            interludeState
        } else {
            val computed = interludeUiFromWindow(displayPosMs / 1000.0, window)
            if (computed.isInterlude) computed else interludeState
        }
    }

    // Apple-inspired whole-row ~4s breath aligned cleanly before the final stage-3 completion tail.
    val totalWindowMs = remember(window, startMs, endMs) { (endMs - startMs).coerceAtLeast(1L) }
    val breatheSpanMs = remember(totalWindowMs) {
        val tailLeadMs = (InterludeConfig.STAGE_3_LEAD_SECONDS * 1000.0).toLong()
        (totalWindowMs - tailLeadMs).coerceAtLeast(totalWindowMs / 2L).coerceAtLeast(1L)
    }
    val breatheCycles = remember(breatheSpanMs) {
        max(1, (breatheSpanMs / 4000.0).roundToInt())
    }
    val rowBreathScale = if (active && animatorsEnabled && window.active) {
        val elapsedMs = (displayPosMs - startMs).coerceAtLeast(0L)
        if (elapsedMs < breatheSpanMs) {
            val cycleMs = breatheSpanMs.toFloat() / breatheCycles
            val u = ((elapsedMs % cycleMs) / cycleMs).coerceIn(0f, 1f)
            val swell = sin(PI * u).toFloat().coerceIn(0f, 1f).pow(1.6f)
            1f + 0.16f * swell
        } else {
            1f
        }
    } else {
        1f
    }

    val dotSize = 14.dp * lyricScale
    val containerLabel = stringResource(R.string.ft_interlude_progress, liveState.stage)
    val dotLabels = listOf(
        stringResource(R.string.ft_interlude_dot_stage_1),
        stringResource(R.string.ft_interlude_dot_stage_2),
        stringResource(R.string.ft_interlude_dot_stage_3),
    )

    Row(
        Modifier.padding(vertical = 6.dp)
            .graphicsLayer {
                scaleX = rowBreathScale
                scaleY = rowBreathScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .then(if (interactive) Modifier.testTag("player_interlude_dots") else Modifier)
            .semantics { if (interactive) contentDescription = containerLabel },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val dotState = liveState.dotStates.getOrElse(index) { InterludeDotFillState.PENDING }
            val dotFill = liveState.dotFills.getOrElse(index) { 0f }.coerceIn(0f, 1f)
            val easedFill = InterludeDotFillEasing.transform(dotFill)
            val targetSec = liveState.dotTimesSeconds?.getOrNull(index)

            // Keep ringed dot aesthetic while adopting Apple's smooth fill curve without 1s per-dot flicker.
            val fillAlpha = when (dotState) {
                InterludeDotFillState.PENDING -> 0.06f
                InterludeDotFillState.FILLING -> 0.24f + 0.76f * easedFill
                InterludeDotFillState.COMPLETE -> 1.0f
            }
            val borderAlpha = when (dotState) {
                InterludeDotFillState.PENDING -> 0.30f
                InterludeDotFillState.FILLING -> 0.50f + 0.46f * easedFill
                InterludeDotFillState.COMPLETE -> 0.96f
            }
            val glowAlpha = when (dotState) {
                InterludeDotFillState.PENDING -> 0f
                InterludeDotFillState.FILLING -> 0.26f * easedFill
                InterludeDotFillState.COMPLETE -> 0.32f
            }

            Box(
                Modifier.size(dotSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.requiredSize(38.dp)
                        .then(if (interactive) Modifier.testTag("interlude_dot_$index") else Modifier)
                        .drawBehind {
                            if (glowAlpha > 0.01f) {
                                val dotRadiusPx = dotSize.toPx() * 0.5f
                                val haloRadius = dotRadiusPx * if (dotState == InterludeDotFillState.COMPLETE) {
                                    1.58f
                                } else {
                                    1.36f + 0.22f * easedFill
                                }
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = glowAlpha),
                                            Color.White.copy(alpha = glowAlpha * 0.35f),
                                            Color.White.copy(alpha = 0f),
                                        ),
                                        radius = haloRadius,
                                    ),
                                    radius = haloRadius,
                                )
                            }
                        }
                        .clip(CircleShape)
                        .clickable(
                            enabled = interactive && targetSec != null,
                            role = Role.Button,
                            onClickLabel = dotLabels[index],
                        ) {
                            targetSec?.let { onSeekTo((it * 1000.0).toLong().coerceAtLeast(0L)) }
                        }
                        .semantics {
                            if (interactive) {
                                contentDescription = dotLabels[index]
                                selected = dotState == InterludeDotFillState.COMPLETE
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(dotSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = fillAlpha))
                            .border(1.25.dp, Color.White.copy(alpha = borderAlpha), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassicTranslationButton(
    mode: UnifiedTranslationButtonMode,
    translationEnabled: Boolean,
    targetLanguage: String?,
    onToggleDisplay: () -> Unit,
    onRequestOrRetry: () -> Unit,
    controlScale: Float = 1f,
    interactionEnabled: Boolean = true,
) {
    if (mode == UnifiedTranslationButtonMode.HIDDEN) return
    val buttonSize = (48.dp * controlScale).coerceAtLeast(48.dp)
    val displayLocale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: java.util.Locale.ROOT
    val targetName = remember(targetLanguage, displayLocale) {
        targetLanguage?.replace('_', '-')?.let(java.util.Locale::forLanguageTag)
            ?.takeIf { it.language in java.util.Locale.getISOLanguages() }
            ?.getDisplayName(displayLocale)
            ?.takeIf(String::isNotBlank)
    }
    val description = when (mode) {
        UnifiedTranslationButtonMode.LOADING -> stringResource(R.string.ft_translating_lyrics)
        UnifiedTranslationButtonMode.FAILED_RETRY,
        UnifiedTranslationButtonMode.FAILED_READONLY -> stringResource(R.string.ft_lyric_translation_failed)
        UnifiedTranslationButtonMode.REQUEST -> if (targetName != null) {
            stringResource(R.string.ft_translate_to_language, targetName)
        } else {
            stringResource(R.string.ft_request_translation)
        }
        UnifiedTranslationButtonMode.TOGGLE -> stringResource(R.string.ft_translate_lyrics)
        UnifiedTranslationButtonMode.HIDDEN -> ""
    }
    val breatheProgress = if (mode == UnifiedTranslationButtonMode.LOADING) {
        val transition = rememberInfiniteTransition(label = "translationLoadingBreathe")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathePhase",
        ).value
    } else {
        1f
    }
    val iconModifier = if (mode == UnifiedTranslationButtonMode.LOADING) {
        val breatheAlpha = 0.36f + 0.58f * breatheProgress
        val breatheScale = 0.94f + 0.06f * breatheProgress
        Modifier
            .graphicsLayer {
                alpha = breatheAlpha
                scaleX = breatheScale
                scaleY = breatheScale
            }
            .testTag("player_translate_button")
    } else {
        Modifier.testTag("player_translate_button")
    }
    val selected = mode == UnifiedTranslationButtonMode.TOGGLE && translationEnabled
    val clickable = when (mode) {
        UnifiedTranslationButtonMode.LOADING,
        UnifiedTranslationButtonMode.FAILED_READONLY,
        UnifiedTranslationButtonMode.HIDDEN -> false
        UnifiedTranslationButtonMode.FAILED_RETRY,
        UnifiedTranslationButtonMode.REQUEST,
        UnifiedTranslationButtonMode.TOGGLE -> interactionEnabled
    }
    val onClick: () -> Unit = when (mode) {
        UnifiedTranslationButtonMode.TOGGLE -> onToggleDisplay
        UnifiedTranslationButtonMode.FAILED_RETRY,
        UnifiedTranslationButtonMode.REQUEST -> onRequestOrRetry
        UnifiedTranslationButtonMode.LOADING,
        UnifiedTranslationButtonMode.FAILED_READONLY,
        UnifiedTranslationButtonMode.HIDDEN -> ({})
    }

    Box(Modifier.size(buttonSize), contentAlignment = Alignment.Center) {
        ClassicGlyphButton(
            icon = ClientIcons.Translate,
            description = description,
            onClick = onClick,
            interactive = clickable,
            selected = selected,
            iconSize = 22.dp * controlScale,
            buttonSize = buttonSize,
            modifier = iconModifier,
        )
        if (mode == UnifiedTranslationButtonMode.FAILED_RETRY || mode == UnifiedTranslationButtonMode.FAILED_READONLY) {
            TranslationFailedExclamationBadge(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .testTag("player_translate_failed_badge"),
            )
        }
    }
}

@Composable
private fun TranslationFailedExclamationBadge(modifier: Modifier = Modifier) {
    Canvas(modifier.size(10.dp)) {
        val radius = size.minDimension * 0.5f
        val cx = size.width * 0.5f
        drawCircle(
            color = Color.Black.copy(alpha = 0.34f),
            radius = radius,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = radius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.7.dp.toPx()),
        )
        val markColor = Color(0xFFFCA5A5).copy(alpha = 0.94f)
        drawLine(
            color = markColor,
            start = Offset(cx, size.height * 0.26f),
            end = Offset(cx, size.height * 0.56f),
            strokeWidth = (size.minDimension * 0.14f).coerceAtLeast(1.2.dp.toPx()),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = markColor,
            radius = (size.minDimension * 0.085f).coerceAtLeast(0.75.dp.toPx()),
            center = Offset(cx, size.height * 0.75f),
        )
    }
}

@Composable
private fun ClassicLyricsEmptyPlaceholder(song: Song) {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(ClientIcons.Lyrics, null, Modifier.size(28.dp), tint = FlareTokens.ClassicVibrantSecondary)
        Text(
            stringResource(R.string.ft_lyrics),
            style = MaterialTheme.typography.titleMedium,
            color = FlareTokens.ClassicVibrantPrimary,
        )
        Text(
            stringResource(R.string.ft_lyrics_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = FlareTokens.ClassicVibrantSecondary,
            textAlign = TextAlign.Center,
        )
        if (song.album.isNotBlank()) {
            Text(
                "${song.title} · ${song.album}",
                style = MaterialTheme.typography.labelMedium,
                color = FlareTokens.ClassicVibrantTertiary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ClassicSongMetadataRow(
    song: Song,
    showLyricsButton: Boolean,
    onToggleLyrics: () -> Unit,
    lyricsActive: Boolean = false,
    controlScale: Float = 1f,
    interactionEnabled: Boolean = true,
    trackDirection: Int = 1,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AnimatedTrackMetadata(
            song = song,
            direction = trackDirection,
            modifier = Modifier.weight(1f).clipToBounds().padding(end = 10.dp * controlScale),
        ) { displaySong ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    displaySong.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 21.sp * controlScale,
                        lineHeight = 27.sp * controlScale,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = FlareTokens.ClassicTitle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                )
                val meta = listOf(
                    displaySong.artist.ifBlank { stringResource(R.string.ft_unknown_artist) },
                    displaySong.album,
                ).filter { it.isNotBlank() }.joinToString(" — ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp * controlScale,
                        lineHeight = 20.sp * controlScale,
                    ),
                    color = FlareTokens.ClassicSubtitle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp * controlScale).basicMarquee(iterations = Int.MAX_VALUE),
                )
            }
        }
        if (showLyricsButton) {
            val touchSize = (48.dp * controlScale).coerceAtLeast(48.dp)
            ClassicGlyphButton(
                icon = ClientIcons.Lyrics,
                description = stringResource(R.string.ft_lyrics),
                onClick = onToggleLyrics,
                interactive = interactionEnabled,
                selected = lyricsActive,
                iconSize = 22.dp * controlScale,
                buttonSize = touchSize,
                modifier = Modifier.testTag("player_lyrics_toggle"),
            )
            Spacer(Modifier.width(maxOf(12.dp * controlScale, (touchSize - 24.dp * controlScale) / 2)))
        }
        PlayerMoreMenu(song = song, controlScale = controlScale, interactionEnabled = interactionEnabled)
    }
}

@Composable
private fun ClassicPlayerTransportPanel(
    state: PlaybackSnapshot,
    actions: ClientActions,
    onQueue: () -> Unit,
    compactVertical: Boolean = false,
    controlScale: Float = 1f,
    onTrackDirectionHint: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val song = state.current ?: return
    val duration = state.durationMs.coerceAtLeast(0)

    Column(modifier) {
        if (state.failed) {
            Text(
                stringResource(R.string.ft_playback_error),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFCA5A5),
                modifier = Modifier.padding(top = 10.dp),
            )
            TextButton(
                onClick = actions::retryPlayback,
                colors = ButtonDefaults.textButtonColors(contentColor = FlareTokens.ClassicIconActive),
                modifier = Modifier.heightIn(min = 48.dp).testTag("playback_retry"),
            ) { Text(stringResource(R.string.ft_retry)) }
        }

        ClassicPlayerTimelineRow(
            songId = song.id,
            positionMs = state.positionMs,
            duration = duration,
            controlScale = controlScale,
            onSeekTo = actions::seekTo,
        )

        if (!compactVertical) {
            Spacer(Modifier.height(6.dp * controlScale))
        }
        ClassicTransportRow(
            state = state,
            actions = actions,
            onQueue = onQueue,
            compact = compactVertical,
            controlScale = controlScale,
            onTrackDirectionHint = onTrackDirectionHint,
        )
    }
}

@Composable
private fun ClassicPlayerTimelineRow(
    songId: String,
    positionMs: Long,
    duration: Long,
    controlScale: Float,
    onSeekTo: (Long) -> Unit,
) {
    var dragging by remember(songId) { mutableStateOf<Float?>(null) }
    var pendingSeekMs by remember(songId) { mutableStateOf<Long?>(null) }
    val livePositionMs by rememberPlaybackPositionMsState(positionMs)
    LaunchedEffect(duration) {
        if (duration <= 0) {
            dragging = null
            pendingSeekMs = null
        }
    }
    LaunchedEffect(pendingSeekMs) {
        val target = pendingSeekMs ?: return@LaunchedEffect
        withTimeoutOrNull(1200L) {
            snapshotFlow { livePositionMs }.first { kotlin.math.abs(it - target) <= 750L }
        }
        if (pendingSeekMs == target) {
            pendingSeekMs = null
        }
    }
    val position = (dragging?.toLong() ?: pendingSeekMs ?: livePositionMs).coerceIn(0, duration)
    // Inline left-time / streamlined dotless track / right-time matching web classic-controls__timeline
    val timeStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = 12.sp * controlScale,
        lineHeight = 16.sp * controlScale,
        fontFeatureSettings = "tnum",
    )
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp * controlScale),
    ) {
        Text(playerTime(position), style = timeStyle, color = FlareTokens.ClassicTime)
        StreamlinedTrackBar(
            value = position.toFloat(),
            range = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = duration > 0,
            description = stringResource(R.string.ft_seek_position),
            onChange = { dragging = it },
            onFinished = { target ->
                val clamped = target.toLong().coerceIn(0, duration)
                pendingSeekMs = clamped
                dragging = null
                onSeekTo(clamped)
            },
            controlScale = controlScale,
            modifier = Modifier.weight(1f).testTag("player_seek"),
        )
        Text(playerTime(duration), style = timeStyle, color = FlareTokens.ClassicTime)
    }
}

@Composable
private fun ClassicPlayerControls(
    state: PlaybackSnapshot,
    actions: ClientActions,
    onQueue: () -> Unit,
    showLyricsButton: Boolean,
    onToggleLyrics: () -> Unit,
    hideMetadata: Boolean,
    modifier: Modifier = Modifier,
    lyricsActive: Boolean = false,
    compactVertical: Boolean = false,
    controlScale: Float = 1f,
    interactionEnabled: Boolean = true,
    trackDirection: Int = 1,
    onTrackDirectionHint: (Int) -> Unit = {},
) {
    val song = state.current ?: return
    Column(modifier) {
        if (!hideMetadata) {
            ClassicSongMetadataRow(
                song = song,
                showLyricsButton = showLyricsButton,
                onToggleLyrics = onToggleLyrics,
                lyricsActive = lyricsActive,
                controlScale = controlScale,
                interactionEnabled = interactionEnabled,
                trackDirection = trackDirection,
            )
            Spacer(Modifier.height(10.dp * controlScale))
        }
        ClassicPlayerTransportPanel(
            state = state,
            actions = actions,
            onQueue = onQueue,
            compactVertical = compactVertical,
            controlScale = controlScale,
            onTrackDirectionHint = onTrackDirectionHint,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class ClassicPlaybackMode { SEQUENCE, LOOP, SINGLE, RANDOM }

private fun currentPlaybackMode(state: PlaybackSnapshot): ClassicPlaybackMode = when {
    state.shuffle -> ClassicPlaybackMode.RANDOM
    state.repeatMode == 2 -> ClassicPlaybackMode.LOOP
    state.repeatMode == 1 -> ClassicPlaybackMode.SINGLE
    else -> ClassicPlaybackMode.SEQUENCE
}

private fun nextPlaybackMode(mode: ClassicPlaybackMode): ClassicPlaybackMode = when (mode) {
    ClassicPlaybackMode.SEQUENCE -> ClassicPlaybackMode.LOOP
    ClassicPlaybackMode.LOOP -> ClassicPlaybackMode.SINGLE
    ClassicPlaybackMode.SINGLE -> ClassicPlaybackMode.RANDOM
    ClassicPlaybackMode.RANDOM -> ClassicPlaybackMode.SEQUENCE
}

private fun playbackModeLabelRes(mode: ClassicPlaybackMode): Int = when (mode) {
    ClassicPlaybackMode.SEQUENCE -> R.string.ft_mode_sequence
    ClassicPlaybackMode.LOOP -> R.string.ft_repeat_all
    ClassicPlaybackMode.SINGLE -> R.string.ft_repeat_one
    ClassicPlaybackMode.RANDOM -> R.string.ft_shuffle_on
}

private fun advancePlaybackMode(state: PlaybackSnapshot, actions: ClientActions): ClassicPlaybackMode {
    val next = nextPlaybackMode(currentPlaybackMode(state))
    val (targetShuffle, targetRepeat) = when (next) {
        ClassicPlaybackMode.SEQUENCE -> false to 0
        ClassicPlaybackMode.LOOP -> false to 2
        ClassicPlaybackMode.SINGLE -> false to 1
        ClassicPlaybackMode.RANDOM -> true to 0
    }
    actions.setPlaybackMode(
        shuffle = targetShuffle,
        repeatMode = targetRepeat,
        currentShuffle = state.shuffle,
        currentRepeatMode = state.repeatMode,
    )
    return next
}

@Composable
private fun ClassicTransportRow(
    state: PlaybackSnapshot,
    actions: ClientActions,
    onQueue: () -> Unit,
    compact: Boolean = false,
    controlScale: Float = 1f,
    onTrackDirectionHint: (Int) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val listeningInteractions = LocalListeningInteractions.current
    val playing = state.playing || state.buffering
    val playState = stringResource(
        when {
            state.buffering -> R.string.ft_buffering
            state.playing -> R.string.ft_playing
            else -> R.string.ft_paused
        },
    )
    val mode = currentPlaybackMode(state)
    val nextMode = nextPlaybackMode(mode)
    val modeDescription = stringResource(playbackModeLabelRes(mode))
    val nextModeDescription = stringResource(playbackModeLabelRes(nextMode))
    val modeIcon = when (mode) {
        ClassicPlaybackMode.SEQUENCE -> ClientIcons.Sequence
        ClassicPlaybackMode.LOOP -> ClientIcons.Repeat
        ClassicPlaybackMode.SINGLE -> ClientIcons.RepeatOne
        ClassicPlaybackMode.RANDOM -> ClientIcons.Shuffle
    }

    val rowHeight = if (compact) ClassicCompactTransportHeight else ClassicTransportHeight * controlScale
    val playButtonSize = if (compact) 56.dp else (64.dp * controlScale).coerceAtLeast(56.dp)
    val playIconSize = if (compact) 32.dp else 38.dp * controlScale
    val skipButtonSize = (48.dp * controlScale).coerceAtLeast(48.dp)
    val skipIconSize = if (compact) 28.dp else 30.dp * controlScale
    val edgeSlotWidth = 24.dp * controlScale
    val edgeTouchSize = (48.dp * controlScale).coerceAtLeast(48.dp)
    val sideIconSize = 24.dp * controlScale

    Row(
        Modifier.fillMaxWidth().heightIn(min = rowHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EdgeSlotIconButton(
            slotWidth = edgeSlotWidth,
            slotHeight = edgeTouchSize,
            touchSize = edgeTouchSize,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                advancePlaybackMode(state, actions)
                listeningInteractions?.feedback?.invoke(nextModeDescription)
            },
            contentDescription = modeDescription,
            selected = mode != ClassicPlaybackMode.SEQUENCE,
            modifier = Modifier.testTag("player_mode")
                .semantics {
                    stateDescription = modeDescription
                    contentDescription = modeDescription
                },
        ) {
            Icon(
                modeIcon, null, Modifier.size(sideIconSize),
                tint = if (mode != ClassicPlaybackMode.SEQUENCE) FlareTokens.ClassicIconActive else FlareTokens.ClassicIcon,
            )
        }
        val livePositionMsState = rememberPlaybackPositionMsState(state.positionMs)
        val pastRestartThreshold by remember(livePositionMsState) {
            derivedStateOf { livePositionMsState.value > 3000L }
        }
        ClassicGlyphButton(
            icon = ClientIcons.Previous,
            description = stringResource(R.string.ft_previous),
            onClick = {
                onTrackDirectionHint(-1)
                actions.previous()
            },
            enabled = state.currentIndex > 0 || pastRestartThreshold ||
                state.repeatMode == 2 || (state.shuffle && state.queue.size > 1),
            iconSize = skipIconSize,
            buttonSize = skipButtonSize,
            modifier = Modifier.testTag("player_previous"),
        )
        // Web classic player: pure solid rounded play/pause glyph without heavy circular background
        IconButton(
            onClick = actions::togglePlay,
            modifier = Modifier.size(playButtonSize).testTag("player_play")
                .semantics { stateDescription = playState },
        ) {
            if (state.buffering) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        Modifier.size(44.dp * controlScale), strokeWidth = 2.dp,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                    Icon(
                        ClientIcons.Pause, stringResource(R.string.ft_pause),
                        modifier = Modifier.size(28.dp * controlScale), tint = FlareTokens.ClassicPlayGlyph,
                    )
                }
            } else {
                Icon(
                    if (playing) ClientIcons.Pause else ClientIcons.Play,
                    stringResource(if (playing) R.string.ft_pause else R.string.ft_play),
                    modifier = Modifier.size(playIconSize), tint = FlareTokens.ClassicPlayGlyph,
                )
            }
        }
        ClassicGlyphButton(
            icon = ClientIcons.Next,
            description = stringResource(R.string.ft_next),
            onClick = {
                onTrackDirectionHint(1)
                actions.next()
            },
            enabled = state.currentIndex < state.queue.lastIndex || state.repeatMode == 2 ||
                (state.shuffle && state.queue.size > 1),
            iconSize = skipIconSize,
            buttonSize = skipButtonSize,
            modifier = Modifier.testTag("player_next"),
        )
        EdgeSlotIconButton(
            slotWidth = edgeSlotWidth,
            slotHeight = edgeTouchSize,
            touchSize = edgeTouchSize,
            onClick = onQueue,
            contentDescription = stringResource(R.string.ft_queue),
            modifier = Modifier.testTag("open_queue"),
        ) {
            Icon(
                ClientIcons.Queue, stringResource(R.string.ft_queue),
                Modifier.size(sideIconSize), tint = FlareTokens.ClassicIcon,
            )
        }
    }
}

@Composable
private fun PlayerMoreMenu(
    song: Song,
    controlScale: Float = 1f,
    interactionEnabled: Boolean = true,
) {
    val listeningInteractions = LocalListeningInteractions.current
    val touchSize = (48.dp * controlScale).coerceAtLeast(48.dp)
    EdgeSlotIconButton(
        slotWidth = 24.dp * controlScale,
        slotHeight = touchSize,
        touchSize = touchSize,
        enabled = interactionEnabled && listeningInteractions != null,
        onClick = { listeningInteractions?.song(song) },
        contentDescription = stringResource(R.string.ft_listening_song_actions),
        modifier = Modifier.testTag("player_more"),
    ) {
        Icon(
            ClientIcons.More,
            stringResource(R.string.ft_listening_song_actions),
            modifier = Modifier.size(24.dp * controlScale),
            tint = FlareTokens.ClassicIcon,
        )
    }
}

/**
 * Keeps a 48dp circular touch target while aligning the visible icon slot flush with outer column edges.
 */
@Composable
private fun EdgeSlotIconButton(
    slotWidth: Dp,
    slotHeight: Dp,
    touchSize: Dp,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier.size(width = slotWidth, height = slotHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = modifier
                .requiredSize(touchSize)
                .clip(CircleShape)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = contentDescription,
                    onClick = onClick,
                )
                .semantics {
                    this.selected = selected
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun ClassicGlyphButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactive: Boolean = true,
    selected: Boolean = false,
    iconSize: Dp = 22.dp,
    buttonSize: Dp = 48.dp,
) {
    val contentTint = when {
        !enabled -> Color.White.copy(alpha = 0.25f)
        selected -> FlareTokens.ClassicIconActive
        else -> FlareTokens.ClassicIcon
    }
    if (!interactive) {
        Box(
            modifier = modifier.size(buttonSize).semantics { this.selected = selected },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, description, Modifier.size(iconSize), tint = contentTint)
            if (selected) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
                        .size(4.dp).background(FlareTokens.ClassicIconActive, CircleShape),
                )
            }
        }
        return
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(buttonSize).semantics { this.selected = selected },
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = if (selected) FlareTokens.ClassicIconActive else FlareTokens.ClassicIcon,
            disabledContentColor = Color.White.copy(alpha = 0.25f),
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(iconSize))
            if (selected) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
                        .size(4.dp).background(FlareTokens.ClassicIconActive, CircleShape),
                )
            }
        }
    }
}

/**
 * Streamlined dotless track bar modeled after Apple Music classic controls:
 * 6dp resting height -> 9dp while scrubbing (proportionally scaled on larger screens),
 * 48dp touch height, full semantics SetProgress support.
 */
@Composable
private fun StreamlinedTrackBar(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    description: String,
    onChange: (Float) -> Unit,
    onFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    controlScale: Float = 1f,
) {
    val span = (range.endInclusive - range.start).coerceAtLeast(1e-4f)
    val clamped = value.coerceIn(range.start, range.endInclusive)
    val fraction = ((clamped - range.start) / span).coerceIn(0f, 1f)
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var activeDrag by remember { mutableStateOf(false) }
    var latestValue by remember { mutableFloatStateOf(clamped) }
    val haptic = LocalHapticFeedback.current
    val restingHeight = 6.dp * controlScale
    val dragHeight = 9.dp * controlScale
    val trackHeight by animateDpAsState(if (activeDrag) dragHeight else restingHeight, tween(160), label = "track_height")

    fun valueForOffset(x: Float): Float {
        val ratio = (x / trackWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return range.start + ratio * span
    }

    Box(
        modifier
            .heightIn(min = 48.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                contentDescription = description
                setProgress { target ->
                    if (!enabled) return@setProgress false
                    val next = target.coerceIn(range.start, range.endInclusive)
                    onChange(next)
                    onFinished(next)
                    true
                }
            }
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(range) {
                            detectTapGestures { offset ->
                                val next = valueForOffset(offset.x)
                                onChange(next)
                                onFinished(next)
                            }
                        }
                        .pointerInput(range) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    activeDrag = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    latestValue = valueForOffset(offset.x)
                                    onChange(latestValue)
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    latestValue = valueForOffset(change.position.x)
                                    onChange(latestValue)
                                },
                                onDragEnd = {
                                    activeDrag = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onFinished(latestValue)
                                },
                                onDragCancel = {
                                    activeDrag = false
                                    onFinished(latestValue)
                                },
                            )
                        }
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val activeColor = if (enabled) FlareTokens.ClassicTrackActive else FlareTokens.ClassicTrackActive.copy(alpha = 0.35f)
        Box(
            Modifier.fillMaxWidth().height(trackHeight).drawBehind {
                val radius = size.height / 2f
                val corner = CornerRadius(radius, radius)
                drawRoundRect(
                    color = FlareTokens.ClassicTrackInactive,
                    cornerRadius = corner,
                )
                if (fraction > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        size = Size(size.width * fraction, size.height),
                        cornerRadius = corner,
                    )
                }
            },
        )
    }
}

private data class ClassicBackdropPalette(
    val dominant: Color,
    val secondary: Color,
    val deep: Color,
) {
    companion object {
        val Default = ClassicBackdropPalette(
            dominant = Color(0xFF343238),
            secondary = Color(0xFF4A464C),
            deep = FlareTokens.ClassicBase,
        )
    }
}

private suspend fun extractBackdropPalette(bitmap: Bitmap): ClassicBackdropPalette =
    withContext(Dispatchers.Default) {
        val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        if (scaled !== bitmap) scaled.recycle()

        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var weight = 0f
        var bestR = -1f
        var bestG = -1f
        var bestB = -1f
        var maxVibrance = -1f

        for (px in pixels) {
            val a = android.graphics.Color.alpha(px)
            if (a < 128) continue
            val r = android.graphics.Color.red(px).toFloat()
            val g = android.graphics.Color.green(px).toFloat()
            val b = android.graphics.Color.blue(px).toFloat()
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val delta = maxC - minC
            val lightness = (maxC + minC) / 510f
            val chromaWeight = 0.55f + (delta / 255f) * 0.45f
            sumR += r * chromaWeight
            sumG += g * chromaWeight
            sumB += b * chromaWeight
            weight += chromaWeight

            if (lightness < 0.12f || lightness > 0.88f || delta < 35f) continue
            val saturation = delta / (1f - kotlin.math.abs(2f * lightness - 1f) + 0.001f)
            val vibrance = saturation * 2f + (maxC / 255f)
            if (vibrance > maxVibrance) {
                maxVibrance = vibrance
                bestR = r
                bestG = g
                bestB = b
            }
        }
        if (weight <= 0f) return@withContext ClassicBackdropPalette.Default
        val avgR = (sumR / weight).coerceIn(0f, 255f)
        val avgG = (sumG / weight).coerceIn(0f, 255f)
        val avgB = (sumB / weight).coerceIn(0f, 255f)
        val domR = (if (bestR >= 0f) bestR else avgR).roundToInt().coerceIn(0, 255)
        val domG = (if (bestG >= 0f) bestG else avgG).roundToInt().coerceIn(0, 255)
        val domB = (if (bestB >= 0f) bestB else avgB).roundToInt().coerceIn(0, 255)
        val secR = (avgR * 1.18f).roundToInt().coerceIn(0, 255)
        val secG = (avgG * 1.18f).roundToInt().coerceIn(0, 255)
        val secB = (avgB * 1.18f).roundToInt().coerceIn(0, 255)
        val deepR = (avgR * 0.14f).roundToInt().coerceIn(8, 255)
        val deepG = (avgG * 0.14f).roundToInt().coerceIn(8, 255)
        val deepB = (avgB * 0.14f).roundToInt().coerceIn(8, 255)
        ClassicBackdropPalette(
            dominant = Color(domR, domG, domB),
            secondary = Color(secR, secG, secB),
            deep = Color(deepR, deepG, deepB),
        )
    }

private data class BackdropArtworkState(
    val coverUrl: String,
    val imageBitmap: ImageBitmap?,
)

internal fun stackBlurArgb(pixels: IntArray, width: Int, height: Int, radius: Int) {
    if (radius < 1 || width <= 0 || height <= 0) return
    val wm = width - 1
    val hm = height - 1
    val wh = width * height
    val div = radius + radius + 1
    val r = IntArray(wh)
    val g = IntArray(wh)
    val b = IntArray(wh)
    val vmin = IntArray(max(width, height))
    var divsum = (div + 1) shr 1
    divsum *= divsum
    val dv = IntArray(256 * divsum) { it / divsum }
    val stack = Array(div) { IntArray(3) }
    val r1 = radius + 1

    var yw = 0
    var yi = 0
    for (y in 0 until height) {
        var rsum = 0; var gsum = 0; var bsum = 0
        var rinsum = 0; var ginsum = 0; var binsum = 0
        var routsum = 0; var goutsum = 0; var boutsum = 0
        for (i in -radius..radius) {
            val p = pixels[yi + min(wm, max(i, 0))]
            val sir = stack[i + radius]
            sir[0] = (p and 0xff0000) shr 16
            sir[1] = (p and 0x00ff00) shr 8
            sir[2] = p and 0x0000ff
            val rbs = r1 - kotlin.math.abs(i)
            rsum += sir[0] * rbs; gsum += sir[1] * rbs; bsum += sir[2] * rbs
            if (i > 0) {
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            } else {
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            }
        }
        var stackpointer = radius
        for (x in 0 until width) {
            r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]
            rsum -= routsum; gsum -= goutsum; bsum -= boutsum
            val stackstart = stackpointer - radius + div
            var sir = stack[stackstart % div]
            routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
            if (y == 0) vmin[x] = min(x + radius + 1, wm)
            val p = pixels[yw + vmin[x]]
            sir[0] = (p and 0xff0000) shr 16
            sir[1] = (p and 0x00ff00) shr 8
            sir[2] = p and 0x0000ff
            rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            rsum += rinsum; gsum += ginsum; bsum += binsum
            stackpointer = (stackpointer + 1) % div
            sir = stack[stackpointer % div]
            routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
            yi++
        }
        yw += width
    }

    for (x in 0 until width) {
        var rsum = 0; var gsum = 0; var bsum = 0
        var rinsum = 0; var ginsum = 0; var binsum = 0
        var routsum = 0; var goutsum = 0; var boutsum = 0
        var yp = -radius * width
        for (i in -radius..radius) {
            val idx = max(0, yp) + x
            val sir = stack[i + radius]
            sir[0] = r[idx]; sir[1] = g[idx]; sir[2] = b[idx]
            val rbs = r1 - kotlin.math.abs(i)
            rsum += r[idx] * rbs; gsum += g[idx] * rbs; bsum += b[idx] * rbs
            if (i > 0) {
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            } else {
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            }
            if (i < hm) yp += width
        }
        yi = x
        var stackpointer = radius
        for (y in 0 until height) {
            pixels[yi] = (0xff shl 24) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
            rsum -= routsum; gsum -= goutsum; bsum -= boutsum
            val stackstart = stackpointer - radius + div
            var sir = stack[stackstart % div]
            routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
            if (x == 0) vmin[y] = min(y + r1, hm) * width
            val p = x + vmin[y]
            sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]
            rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            rsum += rinsum; gsum += ginsum; bsum += binsum
            stackpointer = (stackpointer + 1) % div
            sir = stack[stackpointer]
            routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
            yi += width
        }
    }
}

private fun stackBlurScaledCover(bitmap: Bitmap, targetSize: Int, radius: Int): Bitmap {
    val scaled = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
    val pixels = IntArray(targetSize * targetSize)
    scaled.getPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
    if (scaled !== bitmap) scaled.recycle()
    stackBlurArgb(pixels, targetSize, targetSize, radius = radius)
    return Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, targetSize, 0, 0, targetSize, targetSize)
    }
}

private suspend fun createBlurredBackdropBitmap(
    bitmap: Bitmap,
    palette: ClassicBackdropPalette,
): ImageBitmap =
    withContext(Dispatchers.Default) {
        val targetSize = 96
        val blurredCover = stackBlurScaledCover(bitmap, targetSize = targetSize, radius = 14)
        val composite = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(composite)
        val sizeF = targetSize.toFloat()

        // Bake the multi-layer backdrop (base, palette sweep, saturated 1.18x blurred cover,
        // readability scrim, and vertical vignette) once on Dispatchers.Default so runtime renders 1 layer.
        canvas.drawColor(FlareTokens.ClassicFallbackBase.toArgb())
        val basePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f,
                0f,
                sizeF,
                sizeF,
                intArrayOf(
                    palette.dominant.copy(alpha = 0.85f).toArgb(),
                    palette.secondary.copy(alpha = 0.65f).toArgb(),
                    palette.deep.toArgb(),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, sizeF, sizeF, basePaint)

        val satMatrix = android.graphics.ColorMatrix().apply { setSaturation(2.0f) }
        val coverPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
            alpha = (0.66f * 255f).roundToInt()
            colorFilter = android.graphics.ColorMatrixColorFilter(satMatrix)
        }
        canvas.save()
        canvas.scale(1.18f, 1.18f, sizeF * 0.5f, sizeF * 0.5f)
        canvas.drawBitmap(blurredCover, 0f, 0f, coverPaint)
        canvas.restore()
        blurredCover.recycle()

        canvas.drawColor(FlareTokens.ClassicLegibilityScrim.copy(alpha = 0.42f).toArgb())
        val vignettePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f,
                0f,
                0f,
                sizeF,
                intArrayOf(
                    palette.deep.copy(alpha = 0.34f).toArgb(),
                    android.graphics.Color.argb((0.28f * 255f).roundToInt(), 0, 0, 0),
                    palette.deep.copy(alpha = 0.72f).toArgb(),
                ),
                floatArrayOf(0f, 0.45f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, sizeF, sizeF, vignettePaint)
        composite.asImageBitmap()
    }

private object PreblurredCoverCache {
    private val cache = LruCache<String, ImageBitmap>(24)
    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }
    fun put(key: String, bmp: ImageBitmap) = synchronized(cache) { cache.put(key, bmp) }
}

internal suspend fun loadPreblurredCoverBitmap(
    context: Context,
    coverUrl: String?,
    imageLoader: ImageLoader?,
    targetSize: Int = 64,
    radius: Int = 10,
): ImageBitmap? {
    val cover = coverUrl?.trim().orEmpty()
    if (cover.isBlank() || imageLoader == null) return null
    val cacheKey = "$cover|$targetSize|$radius"
    PreblurredCoverCache.get(cacheKey)?.let { return it }
    return try {
        val request = ImageRequest.Builder(context)
            .data(cover)
            .size(targetSize)
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request)
        val bmp = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return null
        val out = withContext(Dispatchers.Default) {
            stackBlurScaledCover(bmp, targetSize = targetSize, radius = radius).asImageBitmap()
        }
        PreblurredCoverCache.put(cacheKey, out)
        out
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
}

@Composable
internal fun PreblurredCoverBackdrop(
    coverUrl: String?,
    imageLoader: ImageLoader?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    targetSize: Int = 64,
    radius: Int = 10,
) {
    val context = LocalContext.current
    val blurred by produceState<ImageBitmap?>(null, coverUrl, imageLoader, targetSize, radius) {
        value = loadPreblurredCoverBitmap(
            context = context,
            coverUrl = coverUrl,
            imageLoader = imageLoader,
            targetSize = targetSize,
            radius = radius,
        )
    }
    blurred?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
            alpha = alpha,
            modifier = modifier,
        )
    }
}

private data class CachedClassicBackdrop(
    val palette: ClassicBackdropPalette,
    val artwork: BackdropArtworkState,
)

private object ClassicBackdropCache {
    private val cache = LruCache<String, CachedClassicBackdrop>(8)
    fun get(coverUrl: String): CachedClassicBackdrop? =
        if (coverUrl.isBlank()) null else synchronized(cache) { cache.get(coverUrl) }

    fun put(coverUrl: String, entry: CachedClassicBackdrop) {
        if (coverUrl.isNotBlank()) synchronized(cache) { cache.put(coverUrl, entry) }
    }
}

internal suspend fun prewarmClassicPlayerBackdrop(
    context: Context,
    coverUrl: String?,
    imageLoader: ImageLoader?,
) {
    val cover = coverUrl.orEmpty()
    if (cover.isBlank() || imageLoader == null) return
    imageLoader.enqueue(
        buildCoverImageRequest(
            context = context,
            coverUrl = cover,
            targetPx = PlayerPrewarmArtworkPx,
            imageLoader = imageLoader,
        ),
    )
    if (ClassicBackdropCache.get(cover) != null) return
    try {
        val request = ImageRequest.Builder(context)
            .data(cover)
            .size(96)
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request)
        val bmp = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return
        val extracted = extractBackdropPalette(bmp)
        val blurred = createBlurredBackdropBitmap(bmp, extracted)
        ClassicBackdropCache.put(
            cover,
            CachedClassicBackdrop(extracted, BackdropArtworkState(cover, blurred)),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
    }
}

@Composable
private fun ClassicPlayerBackdrop(song: Song, imageLoader: ImageLoader?, atmospheric: Boolean) {
    val context = LocalContext.current
    val coverKey = song.coverUrl.orEmpty()
    val cachedInitial = remember(coverKey, atmospheric, imageLoader) {
        if (atmospheric && imageLoader != null) ClassicBackdropCache.get(coverKey) else null
    }
    var palette by remember(coverKey) { mutableStateOf(cachedInitial?.palette ?: ClassicBackdropPalette.Default) }
    var backdropArtwork by remember(coverKey) {
        mutableStateOf(cachedInitial?.artwork ?: BackdropArtworkState("", null))
    }

    LaunchedEffect(song.id, coverKey, atmospheric, imageLoader) {
        if (!atmospheric || imageLoader == null || coverKey.isBlank()) {
            palette = ClassicBackdropPalette.Default
            backdropArtwork = BackdropArtworkState("", null)
            return@LaunchedEffect
        }
        ClassicBackdropCache.get(coverKey)?.let { cached ->
            palette = cached.palette
            backdropArtwork = cached.artwork
            return@LaunchedEffect
        }
        prewarmClassicPlayerBackdrop(context, coverKey, imageLoader)
        val loaded = ClassicBackdropCache.get(coverKey)
        if (loaded != null) {
            palette = loaded.palette
            backdropArtwork = loaded.artwork
        } else {
            palette = ClassicBackdropPalette.Default
            backdropArtwork = BackdropArtworkState("", null)
        }
    }

    val base = if (atmospheric) FlareTokens.ClassicFallbackBase else FlareTokens.ClassicSolidBase
    Box(Modifier.fillMaxSize().clipToBounds().background(base)) {
        if (atmospheric) {
            if (backdropArtwork.imageBitmap == null) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            listOf(
                                palette.dominant.copy(alpha = 0.85f),
                                palette.secondary.copy(alpha = 0.65f),
                                palette.deep,
                            ),
                        ),
                    ),
                )
            }
            if (imageLoader != null) {
                Crossfade(
                    targetState = backdropArtwork,
                    animationSpec = tween(700),
                    label = "backdrop_cover_crossfade",
                ) { art ->
                    if (art.imageBitmap != null) {
                        Image(
                            bitmap = art.imageBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerTransitionStrip(
    song: Song,
    state: PlaybackSnapshot,
    lyricState: LyricState,
    translationEnabled: Boolean,
    isWideTransition: Boolean,
    transitionRegistry: PlayerTransitionRegistry?,
    expandProgress: () -> Float,
) {
    val density = LocalDensity.current
    val dark = isFlareDarkTheme()
    val barBounds = transitionRegistry?.miniBarBounds
    val barHeightDp = with(density) {
        (barBounds?.height ?: (if (isWideTransition) FlareTokens.WideDockHeight.toPx() else FlareTokens.CompactMiniPlayerHeight.toPx()))
            .coerceAtLeast(48.dp.toPx())
            .toDp()
    }
    val barWidthModifier = if (isWideTransition && barBounds != null) {
        Modifier.width(with(density) { barBounds.width.coerceAtLeast(1f).toDp() })
    } else {
        Modifier.fillMaxWidth()
    }
    val positionedModifier = if (isWideTransition && transitionRegistry != null) {
        val rootIntSize = transitionRegistry.rootSize
        Modifier.offset {
            val p = expandProgress().coerceIn(0f, 1f)
            val rootSize = Size(
                rootIntSize.width.toFloat().coerceAtLeast(1f),
                rootIntSize.height.toFloat().coerceAtLeast(1f),
            )
            val (rect, _) = computeWideWindowRect(rootSize, density, transitionRegistry, p)
            IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
        }
    } else {
        Modifier
    }
    val lyricTarget = resolveMiniPlayerLyricTarget(
        song = song,
        buffering = state.buffering,
        lyricState = lyricState,
        translationEnabled = translationEnabled,
    )
    val startGapDp = if (isWideTransition) 50.dp + 14.dp else 44.dp + 10.dp
    val playTouchSize = if (isWideTransition) 52.dp else 48.dp

    Box(
        positionedModifier
            .then(barWidthModifier)
            .height(barHeightDp)
            .clipToBounds()
            .testTag("player_mini_transition_strip"),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 12.dp, end = if (isWideTransition) 8.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(startGapDp))
            MiniPlayerLyricSlotContent(
                lineSlot = lyricTarget,
                positionMs = state.positionMs,
                playing = state.playing,
                buffering = state.buffering,
                miniPlayerActive = false,
                wideStyle = isWideTransition,
                dark = dark,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        val p = expandProgress().coerceIn(0f, 1f)
                        val fadeP = (p / 0.30f).coerceIn(0f, 1f)
                        alpha = 1f - fadeP
                        translationY = 22.dp.toPx() * fadeP
                    },
            )
            Box(
                Modifier
                    .size(playTouchSize)
                    .graphicsLayer {
                        val p = expandProgress().coerceIn(0f, 1f)
                        val fadeP = (p / 0.30f).coerceIn(0f, 1f)
                        alpha = 1f - fadeP
                        translationY = 22.dp.toPx() * fadeP
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.playing || state.buffering) ClientIcons.Pause else ClientIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        MiniPlayerProgressBar(
            fallbackPositionMs = state.positionMs,
            durationMs = state.durationMs,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            fillColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .graphicsLayer {
                    val p = expandProgress().coerceIn(0f, 1f)
                    alpha = (1f - (p / 0.24f).coerceIn(0f, 1f))
                },
        )
    }
}

private fun playerTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    val minutes = seconds / 60
    return "${minutes}:${(seconds % 60).toString().padStart(2, '0')}"
}
