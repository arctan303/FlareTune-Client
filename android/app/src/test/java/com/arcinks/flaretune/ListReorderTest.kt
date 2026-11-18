package com.arcinks.flaretune

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.arcinks.flaretune.model.PlaylistSong
import com.arcinks.flaretune.model.PlaylistSummary
import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.ui.*
import com.arcinks.flaretune.ui.theme.FlareTuneTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class ListReorderPolicyTest {
    @Test fun draggedRowStaysInsideTheVisibleListEvenWhenFingerLeavesIt() {
        assertEquals(36f, clampReorderCenter(-500f, 72f, 400f), .001f)
        assertEquals(364f, clampReorderCenter(900f, 72f, 400f), .001f)
        assertEquals(50f, clampReorderCenter(900f, 500f, 100f), .001f)
    }

    @Test fun edgeScrollRequiresDeliberateTravelAndHasABoundedSmoothSpeed() {
        fun delta(pointer: Float, origin: Float, back: Boolean = true, forward: Boolean = true) =
            reorderScrollDelta(pointer, origin, 400f, 48f, 5f, 12f, back, forward)
        assertEquals(0f, delta(12f, 12f), .001f)
        assertEquals(0f, delta(388f, 388f), .001f)
        assertEquals(-5f, delta(-200f, 160f), .001f)
        assertEquals(5f, delta(700f, 160f), .001f)
        assertEquals(0f, delta(-200f, 160f, back = false), .001f)
        assertEquals(0f, delta(700f, 160f, forward = false), .001f)
        assertEquals(-1.25f, delta(24f, 160f), .001f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListReorderUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val songs = (0..11).map {
        Song("reorder-$it", "Track $it", "Artist", "Album", 180.0, "https://fixture.invalid/$it.mp3")
    }

    private fun queueSheet(moved: MutableList<Pair<Int, Int>>, removed: MutableList<Int>, dismissed: MutableList<Boolean>) {
        rule.setContent {
            FlareTuneTheme {
                var owned by remember { mutableStateOf(false) }
                FlareModalSheet(
                    onDismissRequest = { dismissed += true },
                    sheetGesturesEnabled = !owned,
                ) {
                    QueuePanel(
                        state = PlaybackSnapshot(current = songs[0], queue = songs, currentIndex = 0, playing = true),
                        loader = null,
                        close = { dismissed += true },
                        onRemove = { removed += it },
                        onMove = { from, to -> moved += from to to },
                        onReorderOwnershipChanged = { owned = it },
                        select = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun diagonalHandleGestureInRealQueueSheetCannotDeleteTheSongOrDismissTheSheet() {
        val moved = mutableListOf<Pair<Int, Int>>()
        val removed = mutableListOf<Int>()
        val dismissed = mutableListOf<Boolean>()
        queueSheet(moved, removed, dismissed)
        rule.onNodeWithTag("queue_drag_0", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(-160f, 4f), delayMillis = 32)
            up()
        }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(removed.isEmpty()); assertTrue(moved.isEmpty()); assertTrue(dismissed.isEmpty()) }
        rule.onNodeWithTag("queue_panel").assertIsDisplayed()
    }

    @Test fun downwardHandleDragInRealQueueSheetMovesWithoutPullingTheSheetAway() {
        val moved = mutableListOf<Pair<Int, Int>>()
        val removed = mutableListOf<Int>()
        val dismissed = mutableListOf<Boolean>()
        queueSheet(moved, removed, dismissed)
        val first = rule.onNodeWithTag("queue_item_0").fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("queue_item_1").fetchSemanticsNode().boundsInRoot
        val distance = second.center.y - first.center.y + 20f
        rule.onNodeWithTag("queue_drag_0", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(-4f, distance / 8f), delayMillis = 16) }
            up()
        }
        rule.runOnIdle {
            assertEquals(listOf(0 to 1), moved)
            assertTrue(removed.isEmpty()); assertTrue(dismissed.isEmpty())
        }
        rule.onNodeWithTag("queue_panel").assertIsDisplayed()
    }

    @Test fun cancelledQueueHandleGestureNeverCommitsAPartialMove() {
        val moved = mutableListOf<Pair<Int, Int>>()
        val removed = mutableListOf<Int>()
        val dismissed = mutableListOf<Boolean>()
        queueSheet(moved, removed, dismissed)
        rule.onNodeWithTag("queue_drag_0", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(0f, 18f), delayMillis = 16) }
            cancel()
        }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(moved.isEmpty()); assertTrue(removed.isEmpty()); assertTrue(dismissed.isEmpty()) }
        rule.onNodeWithTag("queue_item_0").assertTextContains("Track 0", substring = true)
    }

    @Test fun handleCanStayBeyondTheBottomWhileTheDraggedRowRemainsVisible() {
        val moved = mutableListOf<Pair<Int, Int>>()
        val removed = mutableListOf<Int>()
        val dismissed = mutableListOf<Boolean>()
        queueSheet(moved, removed, dismissed)
        val list = rule.onNodeWithTag("queue_list").fetchSemanticsNode().boundsInRoot
        val handle = rule.onNodeWithTag("queue_drag_0", useUnmergedTree = true)
        val distance = list.bottom - handle.fetchSemanticsNode().boundsInRoot.center.y + 140f
        rule.mainClock.autoAdvance = false
        try {
            handle.performTouchInput {
                down(center)
                repeat(12) { moveBy(Offset(0f, distance / 12f), delayMillis = 16) }
            }
            rule.mainClock.advanceTimeBy(900L)
            rule.onNodeWithText("Track 0", substring = false).assertIsDisplayed()
            rule.onNodeWithTag("queue_panel").performTouchInput { cancel() }
        } finally {
            rule.mainClock.autoAdvance = true
        }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(moved.isEmpty()); assertTrue(removed.isEmpty()); assertTrue(dismissed.isEmpty()) }
    }

    @Test fun playlistSheetHandleMovesFullMembershipButWritesOnlyAfterExplicitSave() {
        val submissions = mutableListOf<List<String>>()
        val entries = songs.take(3).mapIndexed { index, song ->
            PlaylistSong(if (index == 0) song.copy(audioUrl = "") else song)
        }
        rule.setContent {
            FlareTuneTheme {
                PlaylistOrderSheet(
                    PlaylistSummary("playlist", "Playlist", "member", revision = 1), entries,
                    busy = false, notice = null, conflict = false,
                    onSubmit = { submissions += it }, onDismiss = {},
                )
            }
        }
        rule.waitForIdle()
        val first = rule.onNodeWithTag("playlist_order_entry_reorder-0").fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("playlist_order_entry_reorder-1").fetchSemanticsNode().boundsInRoot
        val distance = second.center.y - first.center.y + 12f
        rule.onNodeWithTag("playlist_order_drag_reorder-0").performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(-2f, distance / 8f), delayMillis = 16) }
            up()
        }
        rule.runOnIdle { assertTrue(submissions.isEmpty()) }
        rule.onNodeWithTag("playlist_order_save").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(listOf(listOf("reorder-1", "reorder-0", "reorder-2")), submissions) }
    }
}
