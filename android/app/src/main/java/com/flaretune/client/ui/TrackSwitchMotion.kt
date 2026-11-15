package com.flaretune.client.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.flaretune.client.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val TRACK_HINT_WINDOW_MS = 1500L

internal const val CLASSIC_PLAYING_ARTWORK_SCALE = 0.96f
internal const val CLASSIC_PAUSED_ARTWORK_SCALE = 0.80f
internal const val CLASSIC_PLAYING_ARTWORK_ALPHA = 1f
internal const val CLASSIC_PAUSED_ARTWORK_ALPHA = 0.86f
internal const val CLASSIC_SWITCH_PAUSED_ALPHA = 0.90f

private val CoverShrinkEasing = CubicBezierEasing(0.36f, 0.0f, 0.20f, 1.0f)
private val CoverGracefulGlideEasing = CubicBezierEasing(0.30f, 0.0f, 0.12f, 1.0f)
private val CoverSwipeFinishEasing = CubicBezierEasing(0.18f, 0.0f, 0.14f, 1.0f)

internal data class CoverSlideTiming(
    val shrinkDurationMs: Int,
    val shrinkLeadDelayMs: Int,
    val glideDurationMs: Int,
    val isGestureHandoff: Boolean,
)

internal fun computeCoverSlideTiming(
    startExpanded: Boolean,
    fromOffsetPx: Float,
    spanPx: Float,
    currentPauseHold: Float,
): CoverSlideTiming {
    val isGestureHandoff = abs(fromOffsetPx) > 1f
    if (isGestureHandoff) {
        val travelFraction = (1f - abs(fromOffsetPx) / spanPx.coerceAtLeast(1f)).coerceIn(0.28f, 1f)
        val glideMs = (360f * travelFraction).roundToInt().coerceIn(190, 360)
        return CoverSlideTiming(
            shrinkDurationMs = 0,
            shrinkLeadDelayMs = 0,
            glideDurationMs = glideMs,
            isGestureHandoff = true,
        )
    }
    val remainingShrink = if (startExpanded) {
        (1f - currentPauseHold.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shrinkMs = if (remainingShrink > 0.04f) {
        (145f * remainingShrink).roundToInt().coerceIn(55, 145)
    } else {
        0
    }
    val leadMs = if (shrinkMs > 0) (shrinkMs * 0.68f).roundToInt() else 0
    return CoverSlideTiming(
        shrinkDurationMs = shrinkMs,
        shrinkLeadDelayMs = leadMs,
        glideDurationMs = if (startExpanded) 440 else 410,
        isGestureHandoff = false,
    )
}

internal fun resolveCoverPauseHoldForSlot(
    pausedSwitchMotion: Boolean,
    hasActiveSlide: Boolean,
    isActiveSlot: Boolean,
    isPreviewSlot: Boolean,
    controllerPauseHold: Float,
    dragOffsetPx: Float,
    dragShrinkThresholdPx: Float,
): Float {
    if (!pausedSwitchMotion) return 0f
    if (isPreviewSlot) return 1f
    if (hasActiveSlide && isActiveSlot) return 1f
    val dragPull = if (dragShrinkThresholdPx > 0f) {
        (abs(dragOffsetPx) / dragShrinkThresholdPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    return maxOf(controllerPauseHold.coerceIn(0f, 1f), dragPull)
}

/**
 * Resolves the directional axis for a track switch:
 * - `+1` = forward / next track (outgoing leaves left, incoming enters from right)
 * - `-1` = backward / previous track (outgoing leaves right, incoming enters from left)
 * - `0` = same track
 */
internal fun resolveTrackSwitchDirection(
    previous: PlaybackSnapshot?,
    current: PlaybackSnapshot,
    explicitHint: Int? = null,
): Int {
    if (previous == null) return 0
    val prevId = previous.current?.id
    val curId = current.current?.id
    if (curId == null || prevId == curId) return 0
    if (explicitHint == 1 || explicitHint == -1) return explicitHint

    val prevIdx = previous.currentIndex
    val curIdx = current.currentIndex
    val sameSizedQueue = previous.queue.size == current.queue.size && current.queue.isNotEmpty()
    if (sameSizedQueue && prevIdx >= 0 && curIdx >= 0 && current.queue.size > 1) {
        val lastIdx = current.queue.lastIndex
        if (prevIdx == lastIdx && curIdx == 0 && (current.queue.size > 2 || current.repeatMode == 2)) {
            return 1
        }
        if (prevIdx == 0 && curIdx == lastIdx && (current.queue.size > 2 || current.repeatMode == 2)) {
            return -1
        }
        return if (curIdx < prevIdx) -1 else 1
    }
    if (prevIdx >= 0 && curIdx >= 0 && prevIdx != curIdx) {
        return if (curIdx < prevIdx) -1 else 1
    }
    return 1
}

/**
 * Resolves the immediate previous (`delta = -1`) or next (`delta = +1`) song in playback order.
 * Prefers the real Media3 Timeline adjacent indices (`previousQueueIndex` / `nextQueueIndex`, which
 * reflect active shuffle order and repeat boundaries), falling back cleanly for snapshots without
 * player-provided indices.
 */
internal fun resolveAdjacentQueueSong(playback: PlaybackSnapshot, delta: Int): Song? {
    if (delta == 0 || playback.queue.isEmpty()) return null
    val explicitIndex = if (delta < 0) playback.previousQueueIndex else playback.nextQueueIndex
    if (explicitIndex != null) {
        if (explicitIndex !in playback.queue.indices) return null
        if (playback.queue.size <= 1 && explicitIndex == playback.currentIndex) return null
        return playback.queue[explicitIndex]
    }
    val idx = playback.currentIndex
    if (idx !in playback.queue.indices) return null
    if (playback.shuffle) {
        if (playback.queue.size <= 1) return null
        val wrapped = (idx + delta).mod(playback.queue.size)
        return playback.queue[wrapped]
    }
    val target = idx + delta
    return when {
        target in playback.queue.indices -> playback.queue[target]
        playback.repeatMode == 2 && playback.queue.size > 1 ->
            if (delta > 0) playback.queue.first() else playback.queue.last()
        else -> null
    }
}

@Stable
internal class TrackDirectionTracker(initialPlayback: PlaybackSnapshot) {
    var lastDirection by mutableIntStateOf(1)
        internal set
    var committedSongId: String? = initialPlayback.current?.id
        private set
    private var previousSnapshot: PlaybackSnapshot? = initialPlayback
    private var pendingHint: Int? = null
    private var pendingHintTimeMs: Long = 0L

    fun notifyDirection(direction: Int) {
        if (direction == 1 || direction == -1) {
            pendingHint = direction
            pendingHintTimeMs = SystemClock.uptimeMillis()
        }
    }

    fun peekDirection(current: PlaybackSnapshot): Int {
        val curId = current.current?.id ?: return lastDirection
        if (curId == committedSongId) return lastDirection
        val activeHint = pendingHint?.takeIf {
            SystemClock.uptimeMillis() - pendingHintTimeMs <= TRACK_HINT_WINDOW_MS
        }
        val dir = resolveTrackSwitchDirection(previousSnapshot, current, activeHint)
        return if (dir == 0) lastDirection else dir
    }

    fun commit(current: PlaybackSnapshot) {
        val curId = current.current?.id
        if (curId != null && curId != committedSongId) {
            lastDirection = peekDirection(current)
            pendingHint = null
            committedSongId = curId
        }
        previousSnapshot = current
    }
}

@Composable
internal fun rememberTrackDirectionTracker(playback: PlaybackSnapshot): TrackDirectionTracker {
    val tracker = remember { TrackDirectionTracker(playback) }
    SideEffect {
        tracker.commit(playback)
    }
    return tracker
}

internal data class ActiveTrackSlide<T>(
    val outgoing: T,
    val fromOffsetPx: Float,
    val direction: Int,
)

@Stable
internal class TrackCarouselController<T>(
    initialSongId: String,
    initialSlot: T,
    private val scope: CoroutineScope,
    private val touchSlopPx: Float,
    private val slideGapPx: Float,
    internal val pausedSwitchMotion: Boolean = false,
    private val startExpandedProvider: () -> Boolean = { false },
    internal val dragShrinkThresholdPx: Float = 0f,
) {
    val dragOffset: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val slideProgress: Animatable<Float, AnimationVector1D> = Animatable(1f)
    val coverPauseHold: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var viewportWidthPx by mutableIntStateOf(0)
        internal set
    var isDragging by mutableStateOf(false)
        internal set
    var activeSlide by mutableStateOf<ActiveTrackSlide<T>?>(null)
        internal set

    var committedSongId: String = initialSongId
        private set
    var lastActiveSlot: T = initialSlot
        private set

    private var settleJob: Job? = null
    private var shrinkJob: Job? = null
    private var coverHoldJob: Job? = null
    private var fallbackSwipeResetJob: Job? = null
    private var committedSwipeDirection: Int? = null
    private var committedSwipeTimeMs: Long = 0L

    val spanPx: Float
        get() = (viewportWidthPx + slideGapPx).coerceAtLeast(1f)

    fun currentActiveVisualOffsetPx(): Float {
        val slide = activeSlide ?: return dragOffset.value
        val start = slide.fromOffsetPx + slide.direction * spanPx
        return lerp(start, 0f, slideProgress.value.coerceIn(0f, 1f))
    }

    fun isVisualTransitionActive(currentSongId: String): Boolean =
        currentSongId != committedSongId ||
            activeSlide != null ||
            isDragging ||
            abs(dragOffset.value) > 0.5f

    fun syncCommittedIdleSlot(songId: String, slot: T) {
        if (committedSongId == songId) {
            lastActiveSlot = slot
        }
    }

    fun buildPendingSlideIfNeeded(songId: String, direction: Int): ActiveTrackSlide<T>? {
        if (songId == committedSongId) return null
        val recentSwipe = committedSwipeDirection != null &&
            SystemClock.uptimeMillis() - committedSwipeTimeMs <= TRACK_HINT_WINDOW_MS
        val startOffset = if (recentSwipe || abs(dragOffset.value) > 0.5f) {
            dragOffset.value
        } else if (activeSlide != null) {
            currentActiveVisualOffsetPx()
        } else {
            0f
        }
        return ActiveTrackSlide(
            outgoing = lastActiveSlot,
            fromOffsetPx = startOffset,
            direction = if (direction < 0) -1 else 1,
        )
    }

    suspend fun runTrackSlide(
        newSongId: String,
        newSlot: T,
        direction: Int,
        animatorsEnabled: Boolean,
    ) {
        val slide = buildPendingSlideIfNeeded(newSongId, direction)
        committedSongId = newSongId
        lastActiveSlot = newSlot
        committedSwipeDirection = null
        fallbackSwipeResetJob?.cancel()
        fallbackSwipeResetJob = null
        if (slide == null) return

        coverHoldJob?.cancel()
        shrinkJob?.cancel()
        activeSlide = slide
        dragOffset.snapTo(0f)
        if (!animatorsEnabled || viewportWidthPx <= 0) {
            slideProgress.snapTo(1f)
            coverPauseHold.snapTo(0f)
            activeSlide = null
            return
        }
        if (!pausedSwitchMotion) {
            val travelFraction = (1f - abs(slide.fromOffsetPx) / spanPx).coerceIn(0.32f, 1f)
            val durationMs = if (abs(slide.fromOffsetPx) > 1f) {
                (280f * travelFraction).roundToInt().coerceIn(135, 280)
            } else {
                300
            }
            slideProgress.snapTo(0f)
            slideProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
            )
            if (committedSongId == newSongId) {
                activeSlide = null
            }
            return
        }

        val timing = computeCoverSlideTiming(
            startExpanded = startExpandedProvider(),
            fromOffsetPx = slide.fromOffsetPx,
            spanPx = spanPx,
            currentPauseHold = coverPauseHold.value,
        )
        slideProgress.snapTo(0f)
        try {
            if (timing.isGestureHandoff) {
                coverPauseHold.snapTo(1f)
            } else if (timing.shrinkDurationMs > 0) {
                shrinkJob = scope.launch {
                    coverPauseHold.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = timing.shrinkDurationMs,
                            easing = CoverShrinkEasing,
                        ),
                    )
                }
                if (timing.shrinkLeadDelayMs > 0) {
                    delay(timing.shrinkLeadDelayMs.toLong())
                }
            } else {
                coverPauseHold.snapTo(1f)
            }

            val glideEasing = if (timing.isGestureHandoff) {
                CoverSwipeFinishEasing
            } else {
                CoverGracefulGlideEasing
            }
            slideProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = timing.glideDurationMs,
                    easing = glideEasing,
                ),
            )
            shrinkJob?.join()
            if (committedSongId == newSongId) {
                coverPauseHold.snapTo(1f)
                activeSlide = null
                coverHoldJob = scope.launch {
                    if (startExpandedProvider()) {
                        coverPauseHold.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = 200f,
                            ),
                        )
                    } else {
                        coverPauseHold.snapTo(0f)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            shrinkJob?.cancel()
            throw cancelled
        }
    }

    fun startHorizontalDrag() {
        settleJob?.cancel()
        coverHoldJob?.cancel()
        shrinkJob?.cancel()
        fallbackSwipeResetJob?.cancel()
        fallbackSwipeResetJob = null
        isDragging = true
        if (pausedSwitchMotion) {
            coverHoldJob = scope.launch {
                coverPauseHold.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 180, easing = CoverShrinkEasing),
                )
            }
        }
    }

    fun dragHorizontalBy(
        dx: Float,
        canGoPrevious: Boolean,
        canGoNext: Boolean,
        edgeResistanceLimitPx: Float,
    ) {
        val current = dragOffset.value
        val candidate = current + dx
        val next = when {
            candidate < 0f && canGoNext ->
                candidate.coerceAtLeast(-spanPx * 0.92f)
            candidate > 0f && canGoPrevious ->
                candidate.coerceAtMost(spanPx * 0.92f)
            else -> {
                val damped = current + dx * 0.25f
                damped.coerceIn(-edgeResistanceLimitPx, edgeResistanceLimitPx)
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            dragOffset.snapTo(next)
        }
    }

    fun finishHorizontalDrag(
        velocityX: Float,
        canGoPrevious: Boolean,
        canGoNext: Boolean,
        isPreviousRestartOnly: Boolean,
        commitDistancePx: Float,
        flingVelocityPx: Float,
        directionTracker: TrackDirectionTracker,
        onCommitHaptic: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
    ) {
        isDragging = false
        val current = dragOffset.value
        val shouldGoNext = canGoNext && (
            current <= -commitDistancePx ||
                (velocityX <= -flingVelocityPx && current < -touchSlopPx)
            )
        val shouldGoPrev = canGoPrevious && (
            current >= commitDistancePx ||
                (velocityX >= flingVelocityPx && current > touchSlopPx)
            )

        if (shouldGoNext) {
            onCommitHaptic()
            committedSwipeDirection = 1
            committedSwipeTimeMs = SystemClock.uptimeMillis()
            directionTracker.notifyDirection(1)
            if (pausedSwitchMotion) {
                coverHoldJob?.cancel()
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    coverPauseHold.snapTo(1f)
                }
            }
            onNext()
            scheduleFallbackReset(expectedSongId = committedSongId)
            return
        }
        if (shouldGoPrev) {
            onCommitHaptic()
            if (isPreviousRestartOnly) {
                onPrevious()
                snapBack()
                return
            }
            committedSwipeDirection = -1
            committedSwipeTimeMs = SystemClock.uptimeMillis()
            directionTracker.notifyDirection(-1)
            if (pausedSwitchMotion) {
                coverHoldJob?.cancel()
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    coverPauseHold.snapTo(1f)
                }
            }
            onPrevious()
            scheduleFallbackReset(expectedSongId = committedSongId)
            return
        }
        snapBack()
    }

    fun cancelHorizontalDrag() {
        isDragging = false
        snapBack()
    }

    fun pauseHoldForSlot(
        hasActiveSlide: Boolean,
        isActiveSlot: Boolean,
        isPreviewSlot: Boolean,
    ): Float = resolveCoverPauseHoldForSlot(
        pausedSwitchMotion = pausedSwitchMotion,
        hasActiveSlide = hasActiveSlide,
        isActiveSlot = isActiveSlot,
        isPreviewSlot = isPreviewSlot,
        controllerPauseHold = coverPauseHold.value,
        dragOffsetPx = dragOffset.value,
        dragShrinkThresholdPx = dragShrinkThresholdPx,
    )

    private fun snapBack() {
        settleJob?.cancel()
        settleJob = scope.launch {
            dragOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
            )
        }
        if (pausedSwitchMotion) {
            coverHoldJob?.cancel()
            coverHoldJob = scope.launch {
                delay(70L)
                if (startExpandedProvider()) {
                    coverPauseHold.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 200f),
                    )
                } else {
                    coverPauseHold.snapTo(0f)
                }
            }
        }
    }

    private fun scheduleFallbackReset(expectedSongId: String) {
        fallbackSwipeResetJob?.cancel()
        fallbackSwipeResetJob = scope.launch {
            delay(180L)
            if (committedSongId == expectedSongId && !isDragging && abs(dragOffset.value) > 0.5f) {
                dragOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
                )
                if (pausedSwitchMotion) {
                    coverHoldJob?.cancel()
                    coverHoldJob = scope.launch {
                        if (startExpandedProvider()) {
                            coverPauseHold.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = 0.78f, stiffness = 200f),
                            )
                        } else {
                            coverPauseHold.snapTo(0f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun <T> rememberTrackCarouselController(
    songId: String,
    slot: T,
    slideGapDp: Dp = 16.dp,
    pausedSwitchMotion: Boolean = false,
    isExpandedPlayback: () -> Boolean = { false },
    dragShrinkThresholdDp: Dp = 44.dp,
): TrackCarouselController<T> {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val touchSlopPx = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop
    val slideGapPx = with(density) { slideGapDp.toPx() }
    val dragShrinkThresholdPx = with(density) { dragShrinkThresholdDp.toPx() }
    val latestIsExpanded by rememberUpdatedState(isExpandedPlayback)
    val controller = remember {
        TrackCarouselController(
            initialSongId = songId,
            initialSlot = slot,
            scope = scope,
            touchSlopPx = touchSlopPx,
            slideGapPx = slideGapPx,
            pausedSwitchMotion = pausedSwitchMotion,
            startExpandedProvider = { latestIsExpanded() },
            dragShrinkThresholdPx = dragShrinkThresholdPx,
        )
    }
    SideEffect {
        controller.syncCommittedIdleSlot(songId, slot)
    }
    return controller
}

/**
 * Shared horizontal carousel viewport used by both the MiniPlayer song card and Full-Screen Artwork.
 * Renders only the single active slot when idle, adjacent peek slots during finger scrubbing, and
 * seamless continuity across manual swipe releases or programmatic track changes.
 */
@Composable
internal fun <T> TrackCarouselViewport(
    controller: TrackCarouselController<T>,
    songId: String,
    currentSlot: T,
    direction: Int,
    previousPreviewSlot: T?,
    nextPreviewSlot: T?,
    modifier: Modifier = Modifier,
    clipVerticalAllowance: Dp = 0.dp,
    clipHorizontalAllowance: Dp = 0.dp,
    clipWhenIdle: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    canShowPreviousPreview: () -> Boolean = { true },
    slotContent: @Composable (slot: T, isActiveSlot: Boolean, isPreviewSlot: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val animatorsEnabled = remember(context) { systemAnimatorsEnabled(context) }
    LaunchedEffect(songId) {
        controller.runTrackSlide(
            newSongId = songId,
            newSlot = currentSlot,
            direction = direction,
            animatorsEnabled = animatorsEnabled,
        )
    }

    val pendingSlide = controller.buildPendingSlideIfNeeded(songId, direction)
    val slide = pendingSlide ?: controller.activeSlide
    val progress = if (pendingSlide != null) 0f else controller.slideProgress.value.coerceIn(0f, 1f)
    val shouldClip = clipWhenIdle || slide != null || controller.isDragging || abs(controller.dragOffset.value) > 0.5f

    Box(
        modifier = modifier
            .onSizeChanged { controller.viewportWidthPx = it.width }
            .then(
                if (shouldClip) {
                    Modifier.drawWithContent {
                        val vAllowPx = clipVerticalAllowance.toPx()
                        val hAllowPx = clipHorizontalAllowance.toPx()
                        clipRect(
                            left = -hAllowPx,
                            top = -vAllowPx,
                            right = size.width + hAllowPx,
                            bottom = size.height + vAllowPx,
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = contentAlignment,
    ) {
        val span = controller.spanPx
        if (slide != null) {
            val outX = lerp(slide.fromOffsetPx, -slide.direction * span, progress)
            val inX = lerp(slide.fromOffsetPx + slide.direction * span, 0f, progress)
            Box(
                Modifier.graphicsLayer { translationX = outX },
                contentAlignment = contentAlignment,
            ) {
                slotContent(slide.outgoing, false, false)
            }
            Box(
                Modifier.graphicsLayer { translationX = inX },
                contentAlignment = contentAlignment,
            ) {
                slotContent(currentSlot, true, false)
            }
        } else {
            val dragX = controller.dragOffset.value
            if (dragX < -0.5f && nextPreviewSlot != null) {
                Box(
                    Modifier.graphicsLayer { translationX = dragX + span },
                    contentAlignment = contentAlignment,
                ) {
                    slotContent(nextPreviewSlot, false, true)
                }
            } else if (dragX > 0.5f && previousPreviewSlot != null && canShowPreviousPreview()) {
                Box(
                    Modifier.graphicsLayer { translationX = dragX - span },
                    contentAlignment = contentAlignment,
                ) {
                    slotContent(previousPreviewSlot, false, true)
                }
            }
            Box(
                Modifier.graphicsLayer { translationX = dragX },
                contentAlignment = contentAlignment,
            ) {
                slotContent(currentSlot, true, false)
            }
        }
    }
}

/**
 * Attaches horizontal-only track swipe detection without intercepting taps or vertical drags
 * (so artwork tap-to-lyrics and sheet vertical collapse remain unobstructed).
 */
@Composable
internal fun Modifier.horizontalTrackSwipe(
    enabled: Boolean,
    controller: TrackCarouselController<*>,
    directionTracker: TrackDirectionTracker,
    canGoPrevious: () -> Boolean,
    canGoNext: () -> Boolean,
    isPreviousRestartOnly: () -> Boolean,
    onCommitHaptic: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier {
    val latestCanGoPrev by rememberUpdatedState(canGoPrevious)
    val latestCanGoNext by rememberUpdatedState(canGoNext)
    val latestRestartOnly by rememberUpdatedState(isPreviousRestartOnly)
    val latestHaptic by rememberUpdatedState(onCommitHaptic)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrev by rememberUpdatedState(onPrevious)
    if (!enabled) return this
    return this.pointerInput(controller) {
        val touchSlop = viewConfiguration.touchSlop
        val densityScale = density
        val edgeLimitPx = 42.dp.toPx()
        val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startPos = down.position
            var lastPos = startPos
            var lockedHorizontal = false
            velocityTracker.resetTracking()
            velocityTracker.addPosition(down.uptimeMillis, startPos)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (lockedHorizontal) {
                            change.consume()
                            val vx = velocityTracker.calculateVelocity().x
                            val commitDistPx = minOf(68.dp.toPx(), controller.spanPx * 0.25f)
                            controller.finishHorizontalDrag(
                                velocityX = vx,
                                canGoPrevious = latestCanGoPrev(),
                                canGoNext = latestCanGoNext(),
                                isPreviousRestartOnly = latestRestartOnly(),
                                commitDistancePx = commitDistPx,
                                flingVelocityPx = 620f * densityScale,
                                directionTracker = directionTracker,
                                onCommitHaptic = latestHaptic,
                                onNext = latestOnNext,
                                onPrevious = latestOnPrev,
                            )
                        }
                        break
                    }
                    val curPos = change.position
                    val dx = curPos.x - lastPos.x
                    lastPos = curPos
                    velocityTracker.addPosition(change.uptimeMillis, curPos)
                    if (!lockedHorizontal) {
                        val totalDx = curPos.x - startPos.x
                        val totalDy = curPos.y - startPos.y
                        if (hypot(totalDx, totalDy) > touchSlop) {
                            if (abs(totalDx) > abs(totalDy)) {
                                lockedHorizontal = true
                                change.consume()
                                controller.startHorizontalDrag()
                                controller.dragHorizontalBy(
                                    dx = totalDx,
                                    canGoPrevious = latestCanGoPrev(),
                                    canGoNext = latestCanGoNext(),
                                    edgeResistanceLimitPx = edgeLimitPx,
                                )
                            } else {
                                // Yield vertical gesture immediately to sheet dismiss or parent scroll.
                                break
                            }
                        }
                    } else {
                        change.consume()
                        controller.dragHorizontalBy(
                            dx = dx,
                            canGoPrevious = latestCanGoPrev(),
                            canGoNext = latestCanGoNext(),
                            edgeResistanceLimitPx = edgeLimitPx,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                if (lockedHorizontal) {
                    controller.cancelHorizontalDrag()
                }
                throw cancelled
            }
        }
    }
}

/**
 * Smooth directional slide + crossfade for song title/artist/album metadata when changing tracks.
 */
@Composable
internal fun AnimatedTrackMetadata(
    song: Song,
    direction: Int,
    modifier: Modifier = Modifier,
    slideFraction: Float = 0.28f,
    content: @Composable (Song) -> Unit,
) {
    val context = LocalContext.current
    val animatorsEnabled = remember(context) { systemAnimatorsEnabled(context) }
    val dir = if (direction < 0) -1 else 1
    AnimatedContent(
        targetState = song,
        contentKey = { it.id },
        modifier = modifier,
        transitionSpec = {
            if (!animatorsEnabled) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                val enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = 340, delayMillis = 30, easing = CoverGracefulGlideEasing),
                ) { width -> dir * (width * slideFraction).roundToInt() } +
                    fadeIn(tween(durationMillis = 280, delayMillis = 30))
                val exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 240, easing = CoverShrinkEasing),
                ) { width -> -dir * (width * slideFraction).roundToInt() } +
                    fadeOut(tween(durationMillis = 190))
                (enter togetherWith exit).using(SizeTransform(clip = true))
            }
        },
        label = "track_metadata_slide",
    ) { targetSong ->
        content(targetSong)
    }
}
