package com.flaretune.client.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatAnimationSpec
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class LyricLinePresentation(
    val opacity: Float,
    val scale: Float,
    val blurRadius: Dp,
    val verticalOffset: Dp,
)

internal data class LyricsSpringLayoutItem(
    val index: Int,
    val height: Float,
    val presentation: LyricLinePresentation,
)

internal data class LyricsSpringLayoutTarget(
    val index: Int,
    val y: Float,
    val height: Float,
    val scale: Float,
    val delaySeconds: Float,
)

internal data class LyricsScrollOffsetLimits(
    val min: Float,
    val max: Float,
)

internal enum class LyricsMotionScene {
    INSTANT,
    SPRING,
    LINE_ADVANCE,
    CONTAINER_RESIZE,
}

internal object LyricMotionSpecs {
    const val ALIGN_POSITION = 0.35f
    const val SCROLL_DEADZONE_PX = 0.5f
    const val USER_SCROLL_DEBOUNCE_MS = 2000L
    const val POSITION_DAMPING_RATIO = 0.833333f
    const val POSITION_STIFFNESS = 100f
    const val SCALE_DAMPING_RATIO = 0.883883f
    const val SCALE_STIFFNESS = 50f

    val ScaleSpring = spring<Float>(
        dampingRatio = SCALE_DAMPING_RATIO,
        stiffness = SCALE_STIFFNESS,
    )
    val PushSpring = spring<Dp>(
        dampingRatio = POSITION_DAMPING_RATIO,
        stiffness = POSITION_STIFFNESS,
    )
    val ResizeEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val ResizeTween = tween<Float>(durationMillis = 300, easing = ResizeEasing)

    val PositionFloatSpring = FloatSpringSpec(
        dampingRatio = POSITION_DAMPING_RATIO,
        stiffness = POSITION_STIFFNESS,
        visibilityThreshold = 0.25f,
    )
    val ScaleFloatSpring = FloatSpringSpec(
        dampingRatio = SCALE_DAMPING_RATIO,
        stiffness = SCALE_STIFFNESS,
        visibilityThreshold = 0.001f,
    )
    val ResizeFloatTween = FloatTweenSpec(
        duration = 300,
        delay = 0,
        easing = ResizeEasing,
    )
    val OpacityFloatTween = FloatTweenSpec(
        duration = 280,
        delay = 0,
        easing = FastOutSlowInEasing,
    )
    val BlurFloatTween = FloatTweenSpec(
        duration = 300,
        delay = 0,
        easing = FastOutSlowInEasing,
    )
}

/**
 * Four-tier lyric depth presentation (`opacity`, `scale`, `verticalOffset`), keeping blur disabled
 * so scrolling and player transitions avoid per-line offscreen Gaussian blur shaders.
 */
internal fun resolveLyricPresentation(
    index: Int,
    activeIndex: Int,
    lineCount: Int,
    userScrolling: Boolean,
): LyricLinePresentation {
    if (activeIndex < 0) {
        return LyricLinePresentation(
            opacity = 0.72f,
            scale = 1.0f,
            blurRadius = 0.dp,
            verticalOffset = 0.dp,
        )
    }
    if (userScrolling || lineCount <= 2) {
        return LyricLinePresentation(
            opacity = if (index == activeIndex) 1.00f else 0.50f,
            scale = 1.0f,
            blurRadius = 0.dp,
            verticalOffset = 0.dp,
        )
    }
    val diff = index - activeIndex
    val sign = if (diff < 0) -1f else 1f
    val isPast = diff < 0
    return when (abs(diff)) {
        0 -> LyricLinePresentation(
            opacity = 1.00f,
            scale = 1.000f,
            blurRadius = 0.dp,
            verticalOffset = 0.dp,
        )
        1 -> LyricLinePresentation(
            opacity = if (isPast) 0.34f else 0.44f,
            scale = 0.985f,
            blurRadius = 0.dp,
            verticalOffset = (2f * sign).dp,
        )
        2 -> LyricLinePresentation(
            opacity = if (isPast) 0.25f else 0.32f,
            scale = 0.965f,
            blurRadius = 0.dp,
            verticalOffset = (5f * sign).dp,
        )
        else -> LyricLinePresentation(
            opacity = if (isPast) 0.18f else 0.22f,
            scale = 0.935f,
            blurRadius = 0.dp,
            verticalOffset = (16f * sign).dp,
        )
    }
}

/**
 * Decaying ripple stagger delay starting at 0ms on/above anchor and +50ms/1.05^k below.
 */
internal fun computeStaggerDelayMs(lineIndex: Int, anchorIndex: Int): Int {
    if (lineIndex <= anchorIndex) return 0
    var step = 0.05f
    var delaySeconds = 0f
    repeat(lineIndex - anchorIndex) {
        delaySeconds += step
        step /= 1.05f
    }
    return (delaySeconds * 1000f).roundToInt().coerceIn(0, 360)
}

/**
 * Pure Musiver-style spring layout solver:
 * - Centers [alignOffset] at [alignPosition] (0.35 of viewport height)
 * - Solves absolute target Y for all items with instant [scrollOffset]
 * - Accumulates decaying stagger delay starting when lines reach `y >= 0f`
 */
internal fun solveLyricsSpringLayout(
    items: List<LyricsSpringLayoutItem>,
    containerHeight: Float,
    alignOffset: Int,
    alignPosition: Float = LyricMotionSpecs.ALIGN_POSITION,
    scrollOffset: Float = 0f,
    lineSpacing: Float,
    staggerEnabled: Boolean,
): List<LyricsSpringLayoutTarget> {
    val n = items.size
    if (n == 0 || containerHeight <= 0f) return emptyList()

    val anchor = alignOffset.coerceIn(0, n - 1)
    var acc = -scrollOffset
    for (i in 0 until anchor) {
        acc -= items[i].height + lineSpacing
    }
    var y = containerHeight * alignPosition + acc - items[anchor].height / 2f

    var step = if (staggerEnabled) 0.05f else 0f
    var delay = 0f
    val out = ArrayList<LyricsSpringLayoutTarget>(n)
    for (i in 0 until n) {
        val item = items[i]
        out.add(
            LyricsSpringLayoutTarget(
                index = item.index,
                y = y,
                height = item.height,
                scale = item.presentation.scale,
                delaySeconds = delay,
            ),
        )
        y += item.height + lineSpacing
        if (staggerEnabled && y >= 0f) {
            delay += step
            if (i >= anchor) step /= 1.05f
        }
    }
    return out
}

internal fun computeLyricsScrollLimits(
    items: List<LyricsSpringLayoutItem>,
    containerHeight: Float,
    alignOffset: Int,
    alignPosition: Float = LyricMotionSpecs.ALIGN_POSITION,
    lineSpacing: Float,
): LyricsScrollOffsetLimits {
    if (items.isEmpty() || containerHeight <= 0f) return LyricsScrollOffsetLimits(0f, 0f)
    val baseline = solveLyricsSpringLayout(
        items = items,
        containerHeight = containerHeight,
        alignOffset = alignOffset,
        alignPosition = alignPosition,
        scrollOffset = 0f,
        lineSpacing = lineSpacing,
        staggerEnabled = false,
    )
    val first = baseline.first()
    val last = baseline.last()
    val focusCenterY = containerHeight * alignPosition
    val minScroll = min(0f, (first.y + first.height / 2f) - focusCenterY)
    val maxScroll = max(0f, (last.y + last.height / 2f) - focusCenterY)
    return LyricsScrollOffsetLimits(min = minScroll, max = maxScroll)
}

@Stable
internal class LyricsSpringLayoutController {
    internal class ScalarTrack(
        initial: Float,
        private val deadzone: Float,
    ) {
        var current by mutableFloatStateOf(initial)
            private set

        private var velocity = 0f
        private var startValue = initial
        private var startVelocity = 0f
        private var activeTarget = initial
        private var animStartNanos = -1L
        private var animIsTween = false
        private var animRunning = false

        private var hasPending = false
        private var pendingTarget = initial
        private var pendingDelayNanos = 0L
        private var pendingQueuedNanos = -1L
        private var pendingIsTween = false

        val isActive: Boolean get() = animRunning || hasPending
        val destination: Float get() = if (hasPending) pendingTarget else activeTarget

        fun snapTo(value: Float) {
            current = value
            velocity = 0f
            startValue = value
            startVelocity = 0f
            activeTarget = value
            animStartNanos = -1L
            animRunning = false
            hasPending = false
            pendingTarget = value
            pendingQueuedNanos = -1L
        }

        fun setTarget(
            newTarget: Float,
            delayNanos: Long,
            instant: Boolean,
            tween: Boolean,
        ) {
            if (instant) {
                snapTo(newTarget)
                return
            }
            val scheduled = if (hasPending) pendingTarget else activeTarget
            if (abs(newTarget - scheduled) < deadzone && (isActive || abs(newTarget - current) < deadzone)) {
                return
            }
            if (!isActive && abs(newTarget - current) < deadzone) {
                snapTo(newTarget)
                return
            }
            if (delayNanos <= 0L) {
                hasPending = false
                startSegment(newTarget, tween, -1L)
            } else {
                // Keep any active in-flight motion running seamlessly until the stagger delay elapses.
                hasPending = true
                pendingTarget = newTarget
                pendingDelayNanos = delayNanos
                pendingQueuedNanos = -1L
                pendingIsTween = tween
            }
        }

        private fun startSegment(target: Float, tween: Boolean, nowNanos: Long) {
            startValue = current
            startVelocity = velocity
            activeTarget = target
            animIsTween = tween
            animStartNanos = nowNanos
            animRunning = true
        }

        fun step(
            frameNanos: Long,
            springSpec: FloatAnimationSpec,
            tweenSpec: FloatAnimationSpec,
        ): Boolean {
            if (hasPending) {
                if (pendingQueuedNanos < 0L) pendingQueuedNanos = frameNanos
                if (frameNanos - pendingQueuedNanos >= pendingDelayNanos) {
                    val nextTarget = pendingTarget
                    val nextTween = pendingIsTween
                    hasPending = false
                    pendingQueuedNanos = -1L
                    startSegment(nextTarget, nextTween, frameNanos)
                }
            }
            if (animRunning) {
                if (animStartNanos < 0L) animStartNanos = frameNanos
                val elapsed = (frameNanos - animStartNanos).coerceAtLeast(0L)
                val spec = if (animIsTween) tweenSpec else springSpec
                val durationNanos = spec.getDurationNanos(startValue, activeTarget, startVelocity)
                if (elapsed >= durationNanos) {
                    current = activeTarget
                    velocity = 0f
                    animRunning = false
                } else {
                    current = spec.getValueFromNanos(elapsed, startValue, activeTarget, startVelocity)
                    velocity = spec.getVelocityFromNanos(elapsed, startValue, activeTarget, startVelocity)
                }
            }
            return animRunning || hasPending
        }
    }

    internal class RowVisualState(
        initialY: Float,
        initialPushYPx: Float,
        initialScale: Float,
        initialOpacity: Float,
        initialBlurPx: Float,
    ) {
        val yTrack = ScalarTrack(initialY, LyricMotionSpecs.SCROLL_DEADZONE_PX)
        val pushYTrack = ScalarTrack(initialPushYPx, LyricMotionSpecs.SCROLL_DEADZONE_PX)
        val scaleTrack = ScalarTrack(initialScale, 0.001f)
        val opacityTrack = ScalarTrack(initialOpacity, 0.004f)
        val blurTrack = ScalarTrack(initialBlurPx, 0.1f)

        val displayY: Float get() = yTrack.current
        val displayPushYPx: Float get() = pushYTrack.current
        val displayScale: Float get() = scaleTrack.current
        val displayOpacity: Float get() = opacityTrack.current
        val displayBlurPx: Float get() = blurTrack.current

        val isActive: Boolean
            get() = yTrack.isActive || pushYTrack.isActive || scaleTrack.isActive ||
                opacityTrack.isActive || blurTrack.isActive
    }

    private val measuredHeights = HashMap<Any, Float>()
    private val rowStates = HashMap<Any, RowVisualState>()
    private val lastSolvedY = HashMap<Any, Float>()
    private val lastSolvedScale = HashMap<Any, Float>()
    private val lastSolvedOpacity = HashMap<Any, Float>()
    private val lastSolvedBlurPx = HashMap<Any, Float>()
    private val lastSolvedPushYPx = HashMap<Any, Float>()

    private var scrollOffsetPx = 0f
    private var scrollInvalidation by mutableIntStateOf(0)
    private var limits = LyricsScrollOffsetLimits(0f, 0f)
    private var hasCommittedFirstFrame = false

    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    fun readScrollInvalidation(): Int = scrollInvalidation

    fun currentScrollOffset(): Float = scrollOffsetPx

    fun measuredHeightFor(key: Any): Float? = measuredHeights[key]

    /**
     * Records a newly measured item height inside layout; returns the old height if one existed.
     */
    fun recordMeasuredHeight(key: Any, heightPx: Float): Float? = measuredHeights.put(key, heightPx)

    fun invalidateHeights() {
        measuredHeights.clear()
    }

    /**
     * Adjusts manual scroll offset silently inside the same layout pass when an offscreen line
     * above the anchor reveals a different actual height than the fallback estimate.
     */
    fun compensateOffscreenHeightDelta(deltaHeightPx: Float) {
        scrollOffsetPx -= deltaHeightPx
    }

    fun updateScrollLimits(newLimits: LyricsScrollOffsetLimits) {
        limits = newLimits
        scrollOffsetPx = scrollOffsetPx.coerceIn(newLimits.min, newLimits.max)
    }

    fun applyScrollDelta(deltaPx: Float): Float {
        val prev = scrollOffsetPx
        val next = (prev - deltaPx).coerceIn(limits.min, limits.max)
        if (next != prev) {
            scrollOffsetPx = next
            scrollInvalidation++
        }
        // Return the consumed Lazy/Scrollable delta (opposite sign of scrollOffset).
        return -(next - prev)
    }

    fun snapScrollToCurrentAnchor(anchorKey: Any?, baselineAnchorY: Float?) {
        if (anchorKey == null || baselineAnchorY == null) return
        val liveY = rowStates[anchorKey]?.displayY ?: return
        val inferredOffset = (baselineAnchorY - liveY).coerceIn(limits.min, limits.max)
        if (inferredOffset != scrollOffsetPx) {
            scrollOffsetPx = inferredOffset
            scrollInvalidation++
        }
    }

    fun resetScrollOffset() {
        if (scrollOffsetPx != 0f) {
            scrollOffsetPx = 0f
            scrollInvalidation++
        }
    }

    fun visualStateFor(key: Any): RowVisualState? = rowStates[key]

    fun estimateRenderBoundsY(key: Any, targetY: Float, height: Float): ClosedFloatingPointRange<Float> {
        val state = rowStates[key]
        val curY = state?.displayY ?: lastSolvedY[key] ?: targetY
        val curPush = state?.displayPushYPx ?: 0f
        val top = min(curY + curPush, targetY)
        val bottom = max(curY + curPush, targetY) + height
        return top..bottom
    }

    fun commitTargets(
        keys: List<Any>,
        targets: List<LyricsSpringLayoutTarget>,
        presentations: List<LyricLinePresentation>,
        pushYPxList: List<Float>,
        blurPxList: List<Float>,
        scene: LyricsMotionScene,
        animatorsEnabled: Boolean,
        visibleIndices: Set<Int>,
    ) {
        val effectiveScene = if (!animatorsEnabled || !hasCommittedFirstFrame) {
            LyricsMotionScene.INSTANT
        } else {
            scene
        }
        val instantPosition = effectiveScene == LyricsMotionScene.INSTANT
        val tweenPosition = effectiveScene == LyricsMotionScene.CONTAINER_RESIZE

        for (idx in visibleIndices) {
            if (idx !in targets.indices) continue
            val key = keys[idx]
            val target = targets[idx]
            val presentation = presentations[idx]
            val pushYPx = pushYPxList[idx]
            val blurPx = blurPxList[idx]
            val delayNanos = if (effectiveScene == LyricsMotionScene.LINE_ADVANCE) {
                (target.delaySeconds * 1_000_000_000f).toLong().coerceAtLeast(0L)
            } else {
                0L
            }

            val existing = rowStates[key]
            val state = if (existing != null) {
                existing
            } else {
                val initY = if (instantPosition) target.y else (lastSolvedY[key] ?: target.y)
                val initPush = if (instantPosition) pushYPx else (lastSolvedPushYPx[key] ?: pushYPx)
                val initScale = if (instantPosition) target.scale else (lastSolvedScale[key] ?: target.scale)
                val initOpacity = if (instantPosition) presentation.opacity else (lastSolvedOpacity[key] ?: presentation.opacity)
                val initBlur = if (instantPosition) blurPx else (lastSolvedBlurPx[key] ?: blurPx)
                RowVisualState(initY, initPush, initScale, initOpacity, initBlur).also {
                    rowStates[key] = it
                }
            }

            state.yTrack.setTarget(
                newTarget = target.y,
                delayNanos = delayNanos,
                instant = instantPosition,
                tween = tweenPosition,
            )
            state.pushYTrack.setTarget(
                newTarget = pushYPx,
                delayNanos = delayNanos,
                instant = instantPosition,
                tween = tweenPosition,
            )
            state.scaleTrack.setTarget(
                newTarget = target.scale,
                delayNanos = delayNanos,
                instant = instantPosition,
                tween = tweenPosition,
            )
            state.opacityTrack.setTarget(
                newTarget = presentation.opacity,
                delayNanos = delayNanos,
                instant = instantPosition,
                tween = true,
            )
            state.blurTrack.setTarget(
                newTarget = blurPx,
                delayNanos = delayNanos,
                instant = instantPosition,
                tween = true,
            )
        }

        // Remove stale settled row states that are no longer needed.
        val validKeys = keys.toHashSet()
        val iterator = rowStates.entries.iterator()
        while (iterator.hasNext()) {
            val (key, state) = iterator.next()
            if (key !in validKeys && !state.isActive) {
                iterator.remove()
            }
        }

        lastSolvedY.clear()
        lastSolvedScale.clear()
        lastSolvedOpacity.clear()
        lastSolvedBlurPx.clear()
        lastSolvedPushYPx.clear()
        for (i in targets.indices) {
            val k = keys[i]
            lastSolvedY[k] = targets[i].y
            lastSolvedScale[k] = targets[i].scale
            lastSolvedOpacity[k] = presentations[i].opacity
            lastSolvedBlurPx[k] = blurPxList[i]
            lastSolvedPushYPx[k] = pushYPxList[i]
        }

        hasCommittedFirstFrame = true
        if (hasAnyActiveTrack()) {
            wakeSignal.trySend(Unit)
        }
    }

    private fun hasAnyActiveTrack(): Boolean = rowStates.values.any { it.isActive }

    private fun stepAllTracks(frameNanos: Long) {
        for (state in rowStates.values) {
            if (!state.isActive) continue
            state.yTrack.step(frameNanos, LyricMotionSpecs.PositionFloatSpring, LyricMotionSpecs.ResizeFloatTween)
            state.pushYTrack.step(frameNanos, LyricMotionSpecs.PositionFloatSpring, LyricMotionSpecs.ResizeFloatTween)
            state.scaleTrack.step(frameNanos, LyricMotionSpecs.ScaleFloatSpring, LyricMotionSpecs.ResizeFloatTween)
            state.opacityTrack.step(frameNanos, LyricMotionSpecs.OpacityFloatTween, LyricMotionSpecs.OpacityFloatTween)
            state.blurTrack.step(frameNanos, LyricMotionSpecs.BlurFloatTween, LyricMotionSpecs.BlurFloatTween)
        }
    }

    suspend fun runAnimationLoop() {
        val ctx = currentCoroutineContext()
        while (ctx.isActive) {
            while (ctx.isActive && hasAnyActiveTrack()) {
                withFrameNanos { frameNanos ->
                    stepAllTracks(frameNanos)
                }
            }
            wakeSignal.tryReceive()
            if (!hasAnyActiveTrack()) {
                wakeSignal.receive()
            }
        }
    }

    fun clear() {
        measuredHeights.clear()
        rowStates.clear()
        lastSolvedY.clear()
        lastSolvedScale.clear()
        lastSolvedOpacity.clear()
        lastSolvedBlurPx.clear()
        lastSolvedPushYPx.clear()
        scrollOffsetPx = 0f
        limits = LyricsScrollOffsetLimits(0f, 0f)
        hasCommittedFirstFrame = false
    }
}

internal const val LYRICS_EDGE_FADE_MAX_FRACTION = 0.14f

/**
 * Returns `true` strictly when a visible row intersects either the top or bottom viewport edge
 * fade band (without masking a giant focused active row on ultra-compact split windows).
 */
internal fun shouldLyricsRowApplyEdgeFade(
    viewTop: Float,
    scaledHeight: Float,
    containerHeight: Float,
    topFadePx: Float,
    bottomFadePx: Float,
    isFocusedActiveLine: Boolean = false,
): Boolean {
    if (containerHeight <= 0f || scaledHeight <= 0f) return false
    val viewBottom = viewTop + scaledHeight
    if (viewBottom <= 0f || viewTop >= containerHeight) return false
    val effTop = kotlin.math.min(topFadePx, containerHeight * LYRICS_EDGE_FADE_MAX_FRACTION).coerceAtLeast(0f)
    val effBottom = kotlin.math.min(bottomFadePx, containerHeight * LYRICS_EDGE_FADE_MAX_FRACTION).coerceAtLeast(0f)
    if (isFocusedActiveLine && viewTop < effTop && viewBottom > containerHeight - effBottom) {
        return false
    }
    return (effTop > 0f && viewTop < effTop) || (effBottom > 0f && viewBottom > containerHeight - effBottom)
}

/**
 * Computes piecewise-linear local `[fraction -> alpha]` vertical stops (`0f..1f` along the row)
 * so only pixels inside the thin top/bottom viewport edge bands feather softly to zero, keeping
 * all inner lyrics rows at full brightness and zero offscreen overhead.
 */
internal fun computeLyricsRowEdgeFadeStops(
    viewTop: Float,
    scaledHeight: Float,
    containerHeight: Float,
    topFadePx: Float,
    bottomFadePx: Float,
    isFocusedActiveLine: Boolean = false,
): List<Pair<Float, Float>>? {
    if (!shouldLyricsRowApplyEdgeFade(viewTop, scaledHeight, containerHeight, topFadePx, bottomFadePx, isFocusedActiveLine)) {
        return null
    }
    val effTop = kotlin.math.min(topFadePx, containerHeight * LYRICS_EDGE_FADE_MAX_FRACTION).coerceAtLeast(0f)
    val effBottom = kotlin.math.min(bottomFadePx, containerHeight * LYRICS_EDGE_FADE_MAX_FRACTION).coerceAtLeast(0f)

    fun linearAlphaAt(viewY: Float): Float {
        val topA = if (effTop > 0f) (viewY / effTop).coerceIn(0f, 1f) else 1f
        val botA = if (effBottom > 0f) ((containerHeight - viewY) / effBottom).coerceIn(0f, 1f) else 1f
        return (topA * botA).coerceIn(0f, 1f)
    }

    val rawKnots = ArrayList<Float>(6).apply {
        add(0f)
        if (effTop > 0f) {
            val f0 = (0f - viewTop) / scaledHeight
            val f1 = (effTop - viewTop) / scaledHeight
            if (f0 > 0.002f && f0 < 0.998f) add(f0)
            if (f1 > 0.002f && f1 < 0.998f) add(f1)
        }
        if (effBottom > 0f) {
            val f2 = (containerHeight - effBottom - viewTop) / scaledHeight
            val f3 = (containerHeight - viewTop) / scaledHeight
            if (f2 > 0.002f && f2 < 0.998f) add(f2)
            if (f3 > 0.002f && f3 < 0.998f) add(f3)
        }
        add(1f)
    }
    rawKnots.sort()
    val stops = ArrayList<Pair<Float, Float>>(rawKnots.size)
    for (f in rawKnots) {
        val clampedF = f.coerceIn(0f, 1f)
        if (stops.isEmpty()) {
            stops.add(clampedF to linearAlphaAt(viewTop + clampedF * scaledHeight))
        } else if (clampedF == 1f || clampedF - stops.last().first >= 0.002f) {
            if (clampedF <= stops.last().first) {
                stops[stops.lastIndex] = clampedF to linearAlphaAt(viewTop + clampedF * scaledHeight)
            } else {
                stops.add(clampedF to linearAlphaAt(viewTop + clampedF * scaledHeight))
            }
        }
    }
    return if (stops.size >= 2) stops else null
}
