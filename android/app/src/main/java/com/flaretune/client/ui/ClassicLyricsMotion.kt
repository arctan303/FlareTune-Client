package com.flaretune.client.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class LyricLinePresentation(
    val opacity: Float,
    val scale: Float,
    val blurRadius: Dp,
    val verticalOffset: Dp,
)

internal object LyricMotionSpecs {
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
}

/**
 * Four-tier lyric depth presentation modeled after Musiver (`opacity`, `scale`, `blur`, `verticalOffset`).
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
            opacity = if (index == activeIndex) 0.96f else 0.72f,
            scale = 1.0f,
            blurRadius = 0.dp,
            verticalOffset = 0.dp,
        )
    }
    val diff = index - activeIndex
    val sign = if (diff < 0) -1f else 1f
    return when (abs(diff)) {
        0 -> LyricLinePresentation(
            opacity = 1.00f,
            scale = 1.000f,
            blurRadius = 0.dp,
            verticalOffset = 0.dp,
        )
        1 -> LyricLinePresentation(
            opacity = 0.78f,
            scale = 0.985f,
            blurRadius = 0.5.dp,
            verticalOffset = (2f * sign).dp,
        )
        2 -> LyricLinePresentation(
            opacity = 0.56f,
            scale = 0.965f,
            blurRadius = 1.2.dp,
            verticalOffset = (5f * sign).dp,
        )
        else -> LyricLinePresentation(
            opacity = 0.34f,
            scale = 0.935f,
            blurRadius = 2.2.dp,
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

@Stable
internal class LyricsWaveSpringController {
    private val offsets = mutableStateMapOf<Int, Float>()
    private val velocities = mutableMapOf<Int, Float>()
    private val springSpec = FloatSpringSpec(
        dampingRatio = LyricMotionSpecs.POSITION_DAMPING_RATIO,
        stiffness = LyricMotionSpecs.POSITION_STIFFNESS,
        visibilityThreshold = LyricMotionSpecs.SCROLL_DEADZONE_PX,
    )

    private data class RowTrack(
        val listIndex: Int,
        val startValue: Float,
        val startVelocity: Float,
        val delayNanos: Long,
        var settled: Boolean = false,
    )

    fun offsetFor(listIndex: Int): Float = offsets[listIndex] ?: 0f

    fun clear() {
        offsets.clear()
        velocities.clear()
    }

    suspend fun animateLineAdvance(
        deltaPx: Float,
        anchorListIndex: Int,
        visibleIndices: IntRange,
    ) {
        if (abs(deltaPx) < LyricMotionSpecs.SCROLL_DEADZONE_PX || visibleIndices.isEmpty()) {
            clear()
            return
        }
        val tracks = visibleIndices.map { index ->
            val startValue = (offsets[index] ?: 0f) + deltaPx
            val startVelocity = velocities[index] ?: 0f
            val delayNanos = computeStaggerDelayMs(index, anchorListIndex) * 1_000_000L
            offsets[index] = startValue
            RowTrack(
                listIndex = index,
                startValue = startValue,
                startVelocity = startVelocity,
                delayNanos = delayNanos,
            )
        }
        // Remove stale entries outside the active ripple span.
        offsets.keys.toList().forEach { key ->
            if (key !in visibleIndices) {
                offsets.remove(key)
                velocities.remove(key)
            }
        }

        var waveStartNanos = -1L
        val ctx = currentCoroutineContext()
        while (ctx.isActive) {
            var allSettled = true
            withFrameNanos { frameNanos ->
                if (waveStartNanos < 0L) waveStartNanos = frameNanos
                val elapsedNanos = (frameNanos - waveStartNanos).coerceAtLeast(0L)
                for (track in tracks) {
                    if (track.settled) continue
                    if (elapsedNanos < track.delayNanos) {
                        offsets[track.listIndex] = track.startValue
                        allSettled = false
                        continue
                    }
                    val playNanos = elapsedNanos - track.delayNanos
                    val value = springSpec.getValueFromNanos(
                        playTimeNanos = playNanos,
                        initialValue = track.startValue,
                        targetValue = 0f,
                        initialVelocity = track.startVelocity,
                    )
                    val velocity = springSpec.getVelocityFromNanos(
                        playTimeNanos = playNanos,
                        initialValue = track.startValue,
                        targetValue = 0f,
                        initialVelocity = track.startVelocity,
                    )
                    if (abs(value) < LyricMotionSpecs.SCROLL_DEADZONE_PX && abs(velocity) < 8f) {
                        offsets.remove(track.listIndex)
                        velocities.remove(track.listIndex)
                        track.settled = true
                    } else {
                        offsets[track.listIndex] = value
                        velocities[track.listIndex] = velocity
                        allSettled = false
                    }
                }
            }
            if (allSettled) break
        }
    }
}
