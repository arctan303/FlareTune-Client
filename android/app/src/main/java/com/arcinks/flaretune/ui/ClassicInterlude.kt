package com.arcinks.flaretune.ui

import com.arcinks.flaretune.model.LyricLine
import kotlin.math.max

/**
 * Lyric interlude window & three-stage dot progression modeled after Web `interludeState.js`.
 */
internal object InterludeConfig {
    const val MIN_GAP_SECONDS = 7.5
    const val MIN_ACTIVE_SECONDS = 3.2
    const val INTRO_PREPARE_SECONDS = 2.0
    const val BASE_CHAR_TIME = 0.28
    const val LINE_TAIL_HOLD = 1.2
    const val MIN_SING_DURATION = 2.5
    const val RETURN_LEAD_TIME = 1.6
    const val STAGE_3_LEAD_SECONDS = 0.6
}

internal enum class InterludeKind { INTRO, BETWEEN_LINES }

internal enum class InterludeDotFillState { PENDING, FILLING, COMPLETE }

internal data class InterludeWindow(
    val kind: InterludeKind? = null,
    val startSeconds: Double? = null,
    val endSeconds: Double? = null,
    val durationSeconds: Double = 0.0,
) {
    val active: Boolean
        get() = kind != null && startSeconds != null && endSeconds != null && durationSeconds > 0.0
}

internal data class InterludeUiState(
    val isInterlude: Boolean = false,
    val stage: Int = 0,
    val progress: Float = 0f,
    val dotFills: List<Float> = listOf(0f, 0f, 0f),
    val dotStates: List<InterludeDotFillState> = listOf(
        InterludeDotFillState.PENDING,
        InterludeDotFillState.PENDING,
        InterludeDotFillState.PENDING,
    ),
    val dotTimesSeconds: List<Double>? = null,
    val window: InterludeWindow = InterludeWindow(),
)

private fun finiteNonNegative(value: Double?): Double? =
    value?.takeIf { it.isFinite() && it >= 0.0 }

private fun reliableWordBounds(line: LyricLine): Pair<Double, Double>? {
    if (line.words.isEmpty()) return null
    var prevStart = Double.NEGATIVE_INFINITY
    for (word in line.words) {
        if (!word.startTimeSeconds.isFinite() || !word.endTimeSeconds.isFinite()) return null
        if (word.startTimeSeconds < 0.0 || word.endTimeSeconds < word.startTimeSeconds) return null
        if (word.startTimeSeconds < prevStart) return null
        prevStart = word.startTimeSeconds
    }
    val vocalWords = line.words.filter { it.text.isNotBlank() }
    if (vocalWords.isEmpty()) return null
    val start = vocalWords.first().startTimeSeconds
    val end = vocalWords.maxOf { it.endTimeSeconds }
    return start to end
}

private fun firstVocalStart(line: LyricLine): Double? =
    reliableWordBounds(line)?.first ?: finiteNonNegative(line.timeSeconds)

private fun reliableLineEnd(line: LyricLine): Double? {
    val start = finiteNonNegative(line.timeSeconds)
    val end = finiteNonNegative(line.endTimeSeconds)
    return if (end != null && (start == null || end >= start)) end else null
}

internal fun estimateLyricSingDuration(text: String, gapSeconds: Double): Double {
    val charCount = text.trim().count { !it.isWhitespace() }
    val estimated = InterludeConfig.LINE_TAIL_HOLD + charCount * InterludeConfig.BASE_CHAR_TIME
    val safeGap = if (gapSeconds.isFinite() && gapSeconds > 0.0) gapSeconds else InterludeConfig.MIN_GAP_SECONDS
    val maxAllowed = max(InterludeConfig.MIN_SING_DURATION, safeGap * 0.55)
    return estimated.coerceIn(InterludeConfig.MIN_SING_DURATION, maxAllowed)
}

private fun resolveIntroWindow(firstLine: LyricLine, syncMode: String): InterludeWindow {
    val wordStart = reliableWordBounds(firstLine)?.first
    if (syncMode == "word" && wordStart == null) return InterludeWindow()
    val vocalStart = wordStart ?: finiteNonNegative(firstLine.timeSeconds) ?: return InterludeWindow()
    if (syncMode == "line" && wordStart == null && vocalStart < InterludeConfig.MIN_GAP_SECONDS) {
        return InterludeWindow()
    }
    val end = vocalStart - InterludeConfig.INTRO_PREPARE_SECONDS
    if (end < InterludeConfig.MIN_ACTIVE_SECONDS) return InterludeWindow()
    return InterludeWindow(
        kind = InterludeKind.INTRO,
        startSeconds = 0.0,
        endSeconds = end,
        durationSeconds = end,
    )
}

private fun resolveBetweenLinesWindow(
    currentLine: LyricLine,
    nextLine: LyricLine,
    syncMode: String,
): InterludeWindow {
    val currentStart = finiteNonNegative(currentLine.timeSeconds)
    val nextLineStart = finiteNonNegative(nextLine.timeSeconds)
    val vocalEnd = reliableWordBounds(currentLine)?.second ?: reliableLineEnd(currentLine)
    val nextVocalStart = reliableWordBounds(nextLine)?.first
        ?: if (syncMode == "word") null else nextLineStart

    if (vocalEnd != null && nextVocalStart != null) {
        if (nextVocalStart < vocalEnd) return InterludeWindow()
        val end = nextVocalStart - InterludeConfig.RETURN_LEAD_TIME
        if (end - vocalEnd < InterludeConfig.MIN_ACTIVE_SECONDS) return InterludeWindow()
        return InterludeWindow(
            kind = InterludeKind.BETWEEN_LINES,
            startSeconds = vocalEnd,
            endSeconds = end,
            durationSeconds = end - vocalEnd,
        )
    }

    if (syncMode != "line") return InterludeWindow()
    if (currentStart == null || nextLineStart == null || nextLineStart <= currentStart) return InterludeWindow()
    val gap = nextLineStart - currentStart
    if (gap < InterludeConfig.MIN_GAP_SECONDS) return InterludeWindow()
    val start = currentStart + estimateLyricSingDuration(currentLine.text, gap)
    val end = nextLineStart - InterludeConfig.RETURN_LEAD_TIME
    if (end - start < InterludeConfig.MIN_ACTIVE_SECONDS) return InterludeWindow()
    return InterludeWindow(
        kind = InterludeKind.BETWEEN_LINES,
        startSeconds = start,
        endSeconds = end,
        durationSeconds = end - start,
    )
}

internal fun resolveInterludeWindow(
    currentTimeSeconds: Double,
    lines: List<LyricLine>,
    activeIndex: Int,
    lineIndex: Int,
    syncMode: String,
): InterludeWindow {
    if (lines.isEmpty()) return InterludeWindow()
    val normalizedSync = if (syncMode in setOf("word", "line", "none")) syncMode else "line"
    if (normalizedSync == "none") return InterludeWindow()

    val firstLine = lines.first()
    val introBoundary = firstVocalStart(firstLine)
    val hasValidTime = currentTimeSeconds.isFinite() && currentTimeSeconds >= 0.0
    val isBeforeFirstVocal = (activeIndex < 0 || (lineIndex == 0 && activeIndex == 0)) &&
        hasValidTime && introBoundary != null && currentTimeSeconds < introBoundary
    if (isBeforeFirstVocal && lineIndex == 0) {
        return resolveIntroWindow(firstLine, normalizedSync)
    }

    if (lineIndex == activeIndex && lineIndex in 0 until lines.lastIndex) {
        return resolveBetweenLinesWindow(lines[lineIndex], lines[lineIndex + 1], normalizedSync)
    }
    return InterludeWindow()
}

private fun stageTimesSeconds(window: InterludeWindow): List<Double>? {
    val start = window.startSeconds ?: return null
    val duration = window.durationSeconds
    if (duration <= 0.0) return null
    val stage3Threshold = max(0.75, 1.0 - (InterludeConfig.STAGE_3_LEAD_SECONDS / duration))
    return listOf(
        start + duration / 3.0,
        start + duration * 2.0 / 3.0,
        start + duration * stage3Threshold,
    )
}

internal fun interludeDotProgress(currentTimeSeconds: Double, window: InterludeWindow): List<Float> {
    val start = window.startSeconds
    val duration = window.durationSeconds
    val ends = stageTimesSeconds(window)
    if (!currentTimeSeconds.isFinite() || start == null || duration <= 0.0 || ends == null) {
        return listOf(0f, 0f, 0f)
    }
    val starts = listOf(start, ends[0], ends[1])
    return ends.mapIndexed { index, end ->
        val span = (end - starts[index]).coerceAtLeast(1e-4)
        ((currentTimeSeconds - starts[index]) / span).toFloat().coerceIn(0f, 1f)
    }
}

internal fun interludeUiFromWindow(
    currentTimeSeconds: Double,
    window: InterludeWindow,
): InterludeUiState {
    val start = window.startSeconds
    val end = window.endSeconds
    if (!window.active || start == null || end == null || !currentTimeSeconds.isFinite() ||
        currentTimeSeconds < start || currentTimeSeconds >= end
    ) {
        return InterludeUiState()
    }
    val ends = stageTimesSeconds(window) ?: return InterludeUiState()
    val fills = interludeDotProgress(currentTimeSeconds, window)
    val stage = when {
        currentTimeSeconds >= ends[2] -> 3
        currentTimeSeconds >= ends[1] -> 2
        currentTimeSeconds >= ends[0] -> 1
        else -> 0
    }
    val progress = ((currentTimeSeconds - start) / window.durationSeconds).toFloat().coerceIn(0f, 1f)
    val dotStates = fills.mapIndexed { index, fill ->
        when {
            fill >= 1f -> InterludeDotFillState.COMPLETE
            fills.take(index).all { it >= 1f } -> InterludeDotFillState.FILLING
            else -> InterludeDotFillState.PENDING
        }
    }
    val seekTargets = ends.map { it + 0.05 }
    return InterludeUiState(
        isInterlude = true,
        stage = stage,
        progress = progress,
        dotFills = fills,
        dotStates = dotStates,
        dotTimesSeconds = seekTargets,
        window = window,
    )
}

internal fun computeInterludeUiState(
    currentTimeSeconds: Double,
    lines: List<LyricLine>,
    activeIndex: Int,
    lineIndex: Int,
    userScrolling: Boolean,
    syncMode: String,
): InterludeUiState {
    if (userScrolling) return InterludeUiState()
    val validTime = if (currentTimeSeconds.isFinite() && currentTimeSeconds >= 0.0) currentTimeSeconds else 0.0
    val window = resolveInterludeWindow(validTime, lines, activeIndex, lineIndex, syncMode)
    return interludeUiFromWindow(validTime, window)
}
