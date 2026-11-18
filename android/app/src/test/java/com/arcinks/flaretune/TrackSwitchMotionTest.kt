package com.arcinks.flaretune

import androidx.compose.ui.unit.dp
import com.arcinks.flaretune.model.LyricLine
import com.arcinks.flaretune.model.LyricState
import com.arcinks.flaretune.model.LyricStatus
import com.arcinks.flaretune.model.LyricTranslationStatus
import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.ui.PlaybackSnapshot
import com.arcinks.flaretune.ui.UiQueueEntryKeyGenerator
import com.arcinks.flaretune.ui.UnifiedTranslationButtonMode
import com.arcinks.flaretune.ui.computeCoverSlideTiming
import com.arcinks.flaretune.ui.computeQueueCenterScrollOffsetPx
import com.arcinks.flaretune.ui.reconcileUiQueueEntries
import com.arcinks.flaretune.ui.resolveAdjacentQueueSong
import com.arcinks.flaretune.ui.resolveContinueListeningTileMetrics
import com.arcinks.flaretune.ui.resolveCoverPauseHoldForSlot
import com.arcinks.flaretune.ui.resolveTrackSwitchDirection
import com.arcinks.flaretune.ui.resolveUnifiedTranslationButtonMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSwitchMotionTest {
    private val queue = listOf(
        Song("s1", "Track 1", "Artist", "Album", 120.0, "https://a/1", null),
        Song("s2", "Track 2", "Artist", "Album", 120.0, "https://a/2", null),
        Song("s3", "Track 3", "Artist", "Album", 120.0, "https://a/3", null),
    )

    @Test fun resolveTrackSwitchDirectionRespectsHintAndQueueOrderAndWrapAround() {
        val snap0 = PlaybackSnapshot(current = queue[0], queue = queue, currentIndex = 0)
        val snap1 = PlaybackSnapshot(current = queue[1], queue = queue, currentIndex = 1)
        val snap2 = PlaybackSnapshot(current = queue[2], queue = queue, currentIndex = 2)

        assertEquals(0, resolveTrackSwitchDirection(snap0, snap0))
        assertEquals(1, resolveTrackSwitchDirection(snap0, snap1))
        assertEquals(-1, resolveTrackSwitchDirection(snap1, snap0))

        // Repeat all wrap-around: last -> first is forward (+1), first -> last is backward (-1).
        val snap2Repeat = snap2.copy(repeatMode = 2)
        val snap0Repeat = snap0.copy(repeatMode = 2)
        assertEquals(1, resolveTrackSwitchDirection(snap2Repeat, snap0Repeat))
        assertEquals(-1, resolveTrackSwitchDirection(snap0Repeat, snap2Repeat))

        // Explicit recent gesture/transport hint takes priority (e.g. shuffle next/prev).
        assertEquals(-1, resolveTrackSwitchDirection(snap0.copy(shuffle = true), snap2.copy(shuffle = true), explicitHint = -1))
        assertEquals(1, resolveTrackSwitchDirection(snap2.copy(shuffle = true), snap0.copy(shuffle = true), explicitHint = 1))
    }

    @Test fun resolveAdjacentQueueSongHandlesBoundsWrapAndShuffle() {
        val middle = PlaybackSnapshot(current = queue[1], queue = queue, currentIndex = 1, repeatMode = 0, shuffle = false)
        assertEquals(queue[0], resolveAdjacentQueueSong(middle, -1))
        assertEquals(queue[2], resolveAdjacentQueueSong(middle, 1))

        val firstNoWrap = middle.copy(current = queue[0], currentIndex = 0, repeatMode = 0)
        assertNull(resolveAdjacentQueueSong(firstNoWrap, -1))
        assertEquals(queue[1], resolveAdjacentQueueSong(firstNoWrap, 1))

        val firstRepeatAll = firstNoWrap.copy(repeatMode = 2)
        assertEquals(queue[2], resolveAdjacentQueueSong(firstRepeatAll, -1))

        val lastRepeatAll = middle.copy(current = queue[2], currentIndex = 2, repeatMode = 2)
        assertEquals(queue[0], resolveAdjacentQueueSong(lastRepeatAll, 1))

        val shuffledFallback = middle.copy(shuffle = true)
        assertEquals(queue[0], resolveAdjacentQueueSong(shuffledFallback, -1))
        assertEquals(queue[2], resolveAdjacentQueueSong(shuffledFallback, 1))

        val shuffledExplicit = middle.copy(shuffle = true, previousQueueIndex = 2, nextQueueIndex = 0)
        assertEquals(queue[2], resolveAdjacentQueueSong(shuffledExplicit, -1))
        assertEquals(queue[0], resolveAdjacentQueueSong(shuffledExplicit, 1))

        val shuffledBoundaryUnset = middle.copy(shuffle = true, previousQueueIndex = -1, nextQueueIndex = 2)
        assertNull(resolveAdjacentQueueSong(shuffledBoundaryUnset, -1))
        assertEquals(queue[2], resolveAdjacentQueueSong(shuffledBoundaryUnset, 1))
    }

    @Test fun resolveUnifiedTranslationButtonModeMapsAllTranslationStates() {
        val readyOriginal = LyricState(
            songId = "s1",
            status = LyricStatus.READY,
            lines = listOf(LyricLine("hello", timeSeconds = 1.0)),
            translationSupported = true,
            translationState = LyricTranslationStatus.IDLE,
        )
        assertEquals(UnifiedTranslationButtonMode.REQUEST, resolveUnifiedTranslationButtonMode(readyOriginal))

        assertEquals(
            UnifiedTranslationButtonMode.LOADING,
            resolveUnifiedTranslationButtonMode(readyOriginal.copy(translationState = LyricTranslationStatus.PENDING)),
        )
        assertEquals(
            UnifiedTranslationButtonMode.LOADING,
            resolveUnifiedTranslationButtonMode(readyOriginal.copy(translationState = LyricTranslationStatus.FAILED, polling = true)),
        )
        assertEquals(
            UnifiedTranslationButtonMode.FAILED_RETRY,
            resolveUnifiedTranslationButtonMode(readyOriginal.copy(translationState = LyricTranslationStatus.FAILED, polling = false)),
        )
        assertEquals(
            UnifiedTranslationButtonMode.FAILED_READONLY,
            resolveUnifiedTranslationButtonMode(
                readyOriginal.copy(translationSupported = false, translationState = LyricTranslationStatus.FAILED, polling = false),
            ),
        )
        assertEquals(
            UnifiedTranslationButtonMode.TOGGLE,
            resolveUnifiedTranslationButtonMode(
                readyOriginal.copy(
                    lines = listOf(LyricLine("hello", translation = "你好", timeSeconds = 1.0)),
                    translationState = LyricTranslationStatus.READY,
                ),
            ),
        )
        assertEquals(
            UnifiedTranslationButtonMode.HIDDEN,
            resolveUnifiedTranslationButtonMode(readyOriginal.copy(translationState = LyricTranslationStatus.NOT_NEEDED)),
        )
    }

    @Test fun computeCoverSlideTimingShrinksFirstWhenPlayingAndSkipsShrinkWhenPausedOrSwiped() {
        val playingTiming = computeCoverSlideTiming(
            startExpanded = true,
            fromOffsetPx = 0f,
            spanPx = 400f,
            currentPauseHold = 0f,
        )
        assertFalse(playingTiming.isGestureHandoff)
        assertTrue(playingTiming.shrinkDurationMs > 0)
        assertTrue(playingTiming.shrinkLeadDelayMs in 1 until playingTiming.shrinkDurationMs)
        assertEquals(440, playingTiming.glideDurationMs)

        val pausedTiming = computeCoverSlideTiming(
            startExpanded = false,
            fromOffsetPx = 0f,
            spanPx = 400f,
            currentPauseHold = 0f,
        )
        assertFalse(pausedTiming.isGestureHandoff)
        assertEquals(0, pausedTiming.shrinkDurationMs)
        assertEquals(0, pausedTiming.shrinkLeadDelayMs)
        assertEquals(410, pausedTiming.glideDurationMs)

        val handoffTiming = computeCoverSlideTiming(
            startExpanded = true,
            fromOffsetPx = -200f,
            spanPx = 400f,
            currentPauseHold = 1f,
        )
        assertTrue(handoffTiming.isGestureHandoff)
        assertEquals(0, handoffTiming.shrinkDurationMs)
        assertEquals(0, handoffTiming.shrinkLeadDelayMs)
        assertTrue(handoffTiming.glideDurationMs in 190..360)
    }

    @Test fun resolveCoverPauseHoldForSlotKeepsIncomingAndPreviewInPausedSizeAndTracksDrag() {
        // MiniPlayer opts out of paused-size choreography.
        assertEquals(
            0f,
            resolveCoverPauseHoldForSlot(
                pausedSwitchMotion = false,
                hasActiveSlide = true,
                isActiveSlot = true,
                isPreviewSlot = true,
                controllerPauseHold = 1f,
                dragOffsetPx = 100f,
                dragShrinkThresholdPx = 50f,
            ),
            0.001f,
        )

        // Incoming slot and adjacent preview slot always enter at full paused-state hold (1f).
        assertEquals(
            1f,
            resolveCoverPauseHoldForSlot(
                pausedSwitchMotion = true,
                hasActiveSlide = true,
                isActiveSlot = true,
                isPreviewSlot = false,
                controllerPauseHold = 0f,
                dragOffsetPx = 0f,
                dragShrinkThresholdPx = 80f,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            resolveCoverPauseHoldForSlot(
                pausedSwitchMotion = true,
                hasActiveSlide = false,
                isActiveSlot = false,
                isPreviewSlot = true,
                controllerPauseHold = 0f,
                dragOffsetPx = 10f,
                dragShrinkThresholdPx = 80f,
            ),
            0.001f,
        )

        // Outgoing / dragged active slot takes max of controller shrink hold and horizontal drag ratio.
        assertEquals(
            0.5f,
            resolveCoverPauseHoldForSlot(
                pausedSwitchMotion = true,
                hasActiveSlide = false,
                isActiveSlot = true,
                isPreviewSlot = false,
                controllerPauseHold = 0.25f,
                dragOffsetPx = -40f,
                dragShrinkThresholdPx = 80f,
            ),
            0.001f,
        )
    }

    @Test fun reconcileQueueEntriesPreservesInstanceKeysAcrossDuplicateIdsAndEdits() {
        val generator = UiQueueEntryKeyGenerator()
        val initial = generator.build(listOf(queue[0], queue[1], queue[0]))
        assertEquals(3, initial.map { it.key }.distinct().size)

        // Pure reorder preserves existing instance keys cleanly without regeneration.
        val reordered = reconcileUiQueueEntries(
            currentEntries = listOf(initial[2], initial[1], initial[0]),
            incomingQueue = listOf(queue[0], queue[1], queue[0]),
            keyGenerator = generator,
        )
        assertEquals(listOf(initial[2].key, initial[1].key, initial[0].key), reordered.map { it.key })

        // Adding a fourth song keeps the three existing instance keys and allocates one fresh key.
        val expanded = reconcileUiQueueEntries(
            currentEntries = reordered,
            incomingQueue = listOf(queue[0], queue[1], queue[2], queue[0]),
            keyGenerator = generator,
        )
        assertEquals(reordered[0].key, expanded[0].key)
        assertEquals(reordered[1].key, expanded[1].key)
        assertEquals(reordered[2].key, expanded[3].key)
        assertNotEquals(reordered[0].key, expanded[2].key)
        assertTrue(expanded[2].key.contains("s3"))
    }

    @Test fun computeQueueCenterScrollOffsetPxCentersItemOrClampsNonNegative() {
        assertEquals(268, computeQueueCenterScrollOffsetPx(viewportHeightPx = 600, itemHeightPx = 64))
        assertEquals(0, computeQueueCenterScrollOffsetPx(viewportHeightPx = 60, itemHeightPx = 64))
        assertEquals(0, computeQueueCenterScrollOffsetPx(viewportHeightPx = 0, itemHeightPx = 64))
    }

    @Test fun continueListeningTileMetricsScaleMonotonicallyWithTileSize() {
        val compact = resolveContinueListeningTileMetrics(96.dp)
        val medium = resolveContinueListeningTileMetrics(140.dp)
        val expanded = resolveContinueListeningTileMetrics(196.dp)

        assertTrue(compact.fontSize.value < medium.fontSize.value)
        assertTrue(medium.fontSize.value < expanded.fontSize.value)
        assertTrue(compact.chevronSize < medium.chevronSize)
        assertTrue(medium.chevronSize < expanded.chevronSize)
        assertTrue(compact.horizontalPadding < expanded.horizontalPadding)
        assertEquals(2, expanded.maxLines)
    }
}
