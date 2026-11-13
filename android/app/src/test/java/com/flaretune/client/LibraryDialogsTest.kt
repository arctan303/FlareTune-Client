package com.flaretune.client

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.flaretune.client.model.*
import com.flaretune.client.ui.*
import com.flaretune.client.ui.theme.FlareTuneTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryDialogsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val track = Song("a", "After the rain", "The Slow Hours", "Blue hour", 180.0, "https://fixture.invalid/a.mp3")
    private fun playlist(id: String = "p", revision: Long = 1) = PlaylistSummary(id, "Playlist $id", "member", "A collection", revision = revision)
    private fun playlists(count: Int) = (1..count).map { playlist("p$it") }

    @Test fun songActionsDispatchEveryCallbackWithoutClosingTheOwner() {
        val calls = mutableListOf<String>()
        rule.setContent {
            FlareTuneTheme {
                SongActionsSheet(track, false, false, null,
                    { calls += "favorite" }, { calls += "next" }, { calls += "queue" }, { calls += "playlist" },
                    { calls += "artist" }, { calls += "album" }, { calls += "dismiss" }, { calls += "remove" })
            }
        }
        listOf("favorite", "next", "enqueue", "playlist", "artist", "album", "remove").forEach {
            rule.onNodeWithTag("song_action_$it").performScrollTo().performClick()
        }
        rule.runOnIdle { assertEquals(listOf("favorite", "next", "queue", "playlist", "artist", "album", "remove"), calls) }
        rule.onNodeWithTag("song_actions_sheet").assertExists()
        rule.onNodeWithTag("song_actions_close").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("dismiss", calls.last()) }
    }

    @Test fun songActionsHeaderShowsArtistDurationAndFavoriteSelectionState() {
        var favorite by mutableStateOf(false)
        rule.setContent {
            FlareTuneTheme {
                SongActionsSheet(track, favorite, false, null,
                    { favorite = !favorite }, {}, {}, {}, {}, {}, {})
            }
        }
        rule.onNodeWithText("The Slow Hours · 3:00").assertExists()
        rule.onNodeWithTag("song_action_favorite").assertIsNotSelected().performClick()
        rule.onNodeWithTag("song_action_favorite").assertIsSelected()
    }

    @Test fun busyAndUnavailableSongActionsDoNotExposeFakePlayback() {
        var busy by mutableStateOf(false)
        var calls = 0
        rule.setContent {
            FlareTuneTheme {
                SongActionsSheet(track.copy(audioUrl = ""), true, busy, ClientNotice.NETWORK,
                    { calls++ }, { calls++ }, { calls++ }, { calls++ }, { calls++ }, { calls++ }, { calls++ })
            }
        }
        rule.onNodeWithTag("song_action_next").assertIsNotEnabled()
        rule.onNodeWithTag("song_action_enqueue").assertIsNotEnabled()
        rule.onNodeWithTag("song_action_favorite").assertIsEnabled()
        rule.onNodeWithTag("song_action_remove").assertDoesNotExist()
        rule.runOnIdle { busy = true }
        rule.onNodeWithTag("song_action_favorite").assertIsNotEnabled()
        rule.onNodeWithTag("song_actions_close").performScrollTo().assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun playlistFormCountsUnicodeCodePointsAndTrimsSubmittedValues() {
        val submissions = mutableListOf<Pair<String, String>>()
        rule.setContent { FlareTuneTheme { PlaylistDraftDialog(busy = false, notice = null, onSubmit = { a, b -> submissions += a to b }, onDismiss = {}) } }
        rule.onNodeWithTag("playlist_draft_save").assertIsNotEnabled()
        val name = "\uD83C\uDFB5".repeat(40)
        rule.onNodeWithTag("playlist_name").performTextReplacement(name)
        rule.onNodeWithTag("playlist_draft_save").assertIsEnabled()
        rule.onNodeWithTag("playlist_name").performTextReplacement(name + "\uD83C\uDFB5")
        rule.onNodeWithTag("playlist_draft_save").assertIsNotEnabled()
        rule.onNodeWithTag("playlist_name").performTextReplacement("  Evening  ")
        rule.onNodeWithTag("playlist_description").performTextReplacement("x".repeat(301))
        rule.onNodeWithTag("playlist_draft_save").assertIsNotEnabled()
        rule.onNodeWithTag("playlist_description").performTextReplacement("  Quiet songs  ")
        rule.onNodeWithTag("playlist_draft_save").performClick()
        rule.runOnIdle { assertEquals(listOf("Evening" to "Quiet songs"), submissions) }
        rule.onNodeWithTag("playlist_draft_dialog").assertExists()
    }

    @Test fun failedSaveRetainsDraftAndCancelDoesNotWriteAgain() {
        var notice by mutableStateOf<ClientNotice?>(null)
        var busy by mutableStateOf(false)
        var saves = 0
        var dismisses = 0
        rule.setContent { FlareTuneTheme { PlaylistDraftDialog(playlist(), busy, notice, onSubmit = { _, _ -> saves++; busy = true }, onDismiss = { dismisses++ }) } }
        rule.onNodeWithTag("playlist_name").performTextReplacement("My draft")
        rule.onNodeWithTag("playlist_draft_save").performClick()
        rule.onNodeWithTag("playlist_draft_save").assertIsNotEnabled()
        rule.onNodeWithTag("playlist_draft_cancel").assertIsNotEnabled()
        rule.runOnIdle { busy = false; notice = ClientNotice.NETWORK }
        rule.onNodeWithTag("playlist_name").assertTextContains("My draft")
        rule.onNodeWithTag("notice_NETWORK").assertExists()
        rule.onNodeWithTag("playlist_draft_cancel").performClick()
        rule.runOnIdle { assertEquals(1, saves); assertEquals(1, dismisses) }
    }

    @Test fun revisionChangeCannotSaveAnOldMetadataDraft() {
        var current by mutableStateOf(playlist())
        var saves = 0
        rule.setContent { FlareTuneTheme { PlaylistDraftDialog(current, false, null, onSubmit = { _, _ -> saves++ }, onDismiss = {}) } }
        rule.onNodeWithTag("playlist_name").performTextReplacement("An unsaved name")
        rule.runOnIdle { current = current.copy(revision = 2, name = "Remote name") }
        rule.onNodeWithTag("library_conflict").assertExists()
        rule.onNodeWithTag("playlist_name").assertTextContains("An unsaved name")
        rule.onNodeWithTag("playlist_draft_save").assertIsNotEnabled()
        rule.runOnIdle { assertEquals(0, saves) }
    }

    @Test fun savedMetadataDraftKeepsItsConflictBaselineAcrossStateRestoration() {
        var current by mutableStateOf(playlist())
        val restoration = StateRestorationTester(rule)
        restoration.setContent { FlareTuneTheme { PlaylistDraftDialog(current, false, null, onSubmit = { _, _ -> }, onDismiss = {}) } }
        rule.onNodeWithTag("playlist_name").performTextReplacement("Unsaved before rotation")
        rule.runOnIdle { current = current.copy(revision = 2, name = "Remote name") }
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("playlist_name").assertTextContains("Unsaved before rotation")
        rule.onNodeWithTag("library_conflict").assertExists()
        rule.onNodeWithTag("playlist_draft_save").assertIsNotEnabled()
    }
    @Test fun targetSelectionEnforcesTheBatchProductAndSupportsDeselection() {
        var selected: List<String>? = null
        rule.setContent { FlareTuneTheme { PlaylistTargetSheet(100, playlists(6), false, null, false, { selected = it }, {}, {}) } }
        rule.onNodeWithTag("playlist_target_submit").assertIsNotEnabled()
        (1..5).forEach { rule.onNodeWithTag("playlist_target_p$it").performScrollTo().performClick() }
        rule.onNodeWithTag("playlist_target_p6").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("playlist_target_p1").performScrollTo().performClick()
        rule.onNodeWithTag("playlist_target_p6").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag("playlist_target_submit").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("p2", "p3", "p4", "p5", "p6"), selected) }
    }

    @Test fun targetSelectionUsesTheRealFiftyTargetLimitAndRejectsInvalidSongCount() {
        var songCount by mutableIntStateOf(1)
        rule.setContent { FlareTuneTheme { PlaylistTargetSheet(songCount, playlists(2), false, null, false, {}, {}, {}) } }
        rule.onNodeWithText("0 selected · up to 50 playlists").assertExists()
        rule.runOnIdle { songCount = 101 }
        rule.onNodeWithTag("playlist_target_p1").assertIsNotEnabled()
        rule.onNodeWithTag("playlist_target_submit").performScrollTo().assertIsNotEnabled()
        rule.runOnIdle { songCount = 0 }
        rule.onNodeWithTag("playlist_target_submit").assertIsNotEnabled()
    }

    @Test fun partialResultsRemainVisibleAndRetryExcludesSuccessfulTargets() {
        var results by mutableStateOf(emptyList<PlaylistTargetResult>())
        val submissions = mutableListOf<List<String>>()
        rule.setContent { FlareTuneTheme { PlaylistTargetSheet(1, playlists(3), false, null, false, { submissions += it }, {}, {}, results) } }
        (1..3).forEach { rule.onNodeWithTag("playlist_target_p$it").performScrollTo().performClick() }
        rule.onNodeWithTag("playlist_target_submit").performScrollTo().performClick()
        rule.runOnIdle {
            results = listOf(PlaylistTargetResult("p1", "applied", listOf("a")), PlaylistTargetResult("p2", "noop", noopSongIds = listOf("a")),
                PlaylistTargetResult("p3", "failed", error = "untrusted upstream text secret-marker"))
        }
        rule.onNodeWithTag("playlist_target_result_p1").assertExists()
        rule.onNodeWithTag("playlist_target_result_p2").assertExists()
        rule.onNodeWithTag("playlist_target_result_p3").assertExists()
        rule.onNodeWithText("secret-marker", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("playlist_target_p1").assertIsNotEnabled().assertIsOn()
        rule.onNodeWithTag("playlist_target_submit").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf(listOf("p1", "p2", "p3"), listOf("p3")), submissions) }
        screenshot("library-target-partial")
    }

    @Test fun partialRefreshAllowsOnlyTheFailedTargetToRetryAndRetainsAcknowledgements() {
        var choices by mutableStateOf(playlists(3))
        var results by mutableStateOf(emptyList<PlaylistTargetResult>())
        var busy by mutableStateOf(false)
        val submissions = mutableListOf<List<String>>()
        rule.setContent { FlareTuneTheme { PlaylistTargetSheet(1, choices, busy, null, false, { submissions += it }, {}, {}, results) } }
        rule.onNodeWithTag("playlist_target_p1").performScrollTo().performClick()
        rule.onNodeWithTag("playlist_target_p2").performScrollTo().performClick()
        rule.onNodeWithTag("playlist_target_submit").performScrollTo().performClick()
        rule.runOnIdle {
            results = listOf(PlaylistTargetResult("p1", "applied", listOf("a"), revision = 2),
                PlaylistTargetResult("p2", "failed", error = "TEMPORARY_FAILURE"))
            // The VM refreshes summaries after partial: p1 changed because our write succeeded;
            // an unselected playlist also changed and the returned shelf order is different.
            choices = listOf(playlist("p3", 9), playlist("p1", 2), playlist("p2", 1))
        }
        rule.onNodeWithTag("library_conflict").assertDoesNotExist()
        rule.onNodeWithTag("playlist_target_p1").assertIsOn().assertIsNotEnabled()
        rule.onNodeWithTag("playlist_target_submit").performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { busy = true; results = emptyList() }
        rule.onNodeWithTag("playlist_target_p1").assertIsNotEnabled()
        rule.runOnIdle {
            busy = false
            results = listOf(PlaylistTargetResult("p2", "failed", error = "TEMPORARY_FAILURE"))
        }
        rule.onNodeWithTag("library_conflict").assertDoesNotExist()
        rule.onNodeWithTag("playlist_target_result_p1").assertExists()
        rule.onNodeWithTag("playlist_target_p1").assertIsOn().assertIsNotEnabled()
        rule.onNodeWithTag("playlist_target_submit").performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(listOf(listOf("p1", "p2"), listOf("p2"), listOf("p2")), submissions) }
    }

    @Test fun missingPendingTargetAfterPartialStillBlocksAnOldSelection() {
        var choices by mutableStateOf(playlists(2))
        var results by mutableStateOf(emptyList<PlaylistTargetResult>())
        rule.setContent { FlareTuneTheme { PlaylistTargetSheet(1, choices, false, null, false, {}, {}, {}, results) } }
        rule.onNodeWithTag("playlist_target_p1").performScrollTo().performClick()
        rule.onNodeWithTag("playlist_target_p2").performScrollTo().performClick()
        rule.runOnIdle {
            results = listOf(PlaylistTargetResult("p1", "applied", listOf("a"), revision = 2),
                PlaylistTargetResult("p2", "failed", error = "PLAYLIST_NOT_FOUND"))
            choices = listOf(playlist("p1", 2))
        }
        rule.onNodeWithTag("library_conflict").assertExists()
        rule.onNodeWithTag("playlist_target_submit").performScrollTo().assertIsNotEnabled()
    }
    @Test fun targetConflictOrRefreshCannotBlindlyReplayOldSelection() {
        var choices by mutableStateOf(playlists(2))
        var results by mutableStateOf(emptyList<PlaylistTargetResult>())
        rule.setContent { FlareTuneTheme { PlaylistTargetSheet(1, choices, false, null, false, {}, {}, {}, results) } }
        rule.onNodeWithTag("playlist_target_p1").performScrollTo().performClick()
        rule.runOnIdle { results = listOf(PlaylistTargetResult("p1", "failed", error = "REVISION_CONFLICT")) }
        rule.onNodeWithTag("library_conflict").assertExists()
        rule.onNodeWithTag("playlist_target_submit").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("playlist_target_result_p1").assertExists()
        rule.runOnIdle { results = emptyList(); choices = choices.map { it.copy(revision = 2) } }
        rule.onNodeWithTag("library_conflict").assertExists()
        rule.onNodeWithTag("playlist_target_submit").assertIsNotEnabled()
    }

    @Test fun reorderPreservesUnavailableMembersAndCancelHasNoMutation() {
        val unavailable = track.copy(id = "missing", title = "Unavailable song", audioUrl = "")
        val entries = listOf(PlaylistSong(track), PlaylistSong(unavailable), PlaylistSong(track.copy(id = "b", title = "Blue hour")))
        val submissions = mutableListOf<List<String>>()
        var dismisses = 0
        rule.setContent { FlareTuneTheme { PlaylistOrderDialog(playlist(), entries, false, null, false, { submissions += it }, { dismisses++ }) } }
        rule.onNodeWithTag("playlist_order_up_a").assertIsNotEnabled()
        rule.onNodeWithTag("playlist_order_down_b").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("playlist_order_up_missing").performScrollTo().performClick()
        rule.onNodeWithTag("playlist_order_save").performClick()
        rule.runOnIdle { assertEquals(listOf(listOf("missing", "a", "b")), submissions) }
        rule.onNodeWithTag("playlist_order_dialog").assertExists()
        rule.onNodeWithTag("playlist_order_cancel").performClick()
        rule.runOnIdle { assertEquals(1, submissions.size); assertEquals(1, dismisses) }
    }

    @Test fun longPressDragReordersCompleteMembershipBeforeExplicitSave() {
        val unavailable = track.copy(id = "missing", title = "Unavailable song", audioUrl = "")
        val entries = listOf(PlaylistSong(track), PlaylistSong(unavailable), PlaylistSong(track.copy(id = "b", title = "Blue hour")))
        val submissions = mutableListOf<List<String>>()
        rule.setContent { FlareTuneTheme { PlaylistOrderDialog(playlist(), entries, false, null, false, { submissions += it }, {}) } }
        dragMember("missing", "a")
        rule.runOnIdle { assertTrue(submissions.isEmpty()) }
        rule.onNodeWithTag("playlist_order_up_missing").assertIsNotEnabled()
        rule.onNodeWithTag("playlist_order_save").performClick()
        rule.runOnIdle { assertEquals(listOf(listOf("missing", "a", "b")), submissions) }
    }

    @Test fun cancellingAfterATouchDragDoesNotSubmitAnyMembership() {
        val entries = listOf(PlaylistSong(track), PlaylistSong(track.copy(id = "b", title = "Blue hour")))
        val submissions = mutableListOf<List<String>>()
        var dismisses = 0
        rule.setContent { FlareTuneTheme { PlaylistOrderDialog(playlist(), entries, false, null, false, { submissions += it }, { dismisses++ }) } }
        dragMember("a", "b")
        rule.onNodeWithTag("playlist_order_up_b").assertIsNotEnabled()
        rule.onNodeWithTag("playlist_order_cancel").performClick()
        rule.runOnIdle { assertEquals(1, dismisses); assertTrue(submissions.isEmpty()) }
    }

    @Test fun revisionChangeDuringALongPressDisablesDraggingAndSaving() {
        var current by mutableStateOf(playlist())
        val entries = listOf(PlaylistSong(track), PlaylistSong(track.copy(id = "b", title = "Blue hour")))
        val submissions = mutableListOf<List<String>>()
        rule.setContent { FlareTuneTheme { PlaylistOrderDialog(current, entries, false, null, false, { submissions += it }, {}) } }
        val handle = rule.onNodeWithTag("playlist_order_drag_a")
        val distance = rule.onNodeWithTag("playlist_order_entry_b").fetchSemanticsNode().boundsInRoot.center.y -
            rule.onNodeWithTag("playlist_order_entry_a").fetchSemanticsNode().boundsInRoot.center.y + 12f
        handle.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.runOnIdle { current = current.copy(revision = 2) }
        handle.assertIsNotEnabled()
        handle.performTouchInput { moveBy(Offset(0f, distance)); up() }
        rule.onNodeWithTag("library_conflict").assertExists()
        rule.onNodeWithTag("playlist_order_save").assertIsNotEnabled()
        val a = rule.onNodeWithTag("playlist_order_entry_a").fetchSemanticsNode().boundsInRoot.center.y
        val b = rule.onNodeWithTag("playlist_order_entry_b").fetchSemanticsNode().boundsInRoot.center.y
        rule.runOnIdle { assertTrue(a < b); assertTrue(submissions.isEmpty()) }
    }
    @Test fun changedFullMembershipBlocksSavingEvenIfRevisionDidNotChange() {
        var entries by mutableStateOf(listOf(PlaylistSong(track), PlaylistSong(track.copy(id = "b"))))
        rule.setContent { FlareTuneTheme { PlaylistOrderDialog(playlist(), entries, false, null, false, {}, {}) } }
        rule.onNodeWithTag("playlist_order_down_a").performScrollTo().performClick()
        rule.runOnIdle { entries = entries + PlaylistSong(track.copy(id = "new")) }
        rule.onNodeWithTag("library_conflict").assertExists()
        rule.onNodeWithTag("playlist_order_save").assertIsNotEnabled()
        rule.onNodeWithTag("playlist_order_up_b").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp-night-mdpi")
    fun narrowChineseFormSupportsLargeTextAndAccessibleSaveCancel() {
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        assertEquals(2f, rule.activity.resources.configuration.fontScale, 0.001f)
        rule.setContent {
            FlareTuneTheme { PlaylistDraftDialog(playlist().copy(name = "夜晚的音乐与长名称歌单"), false, ClientNotice.NETWORK, onSubmit = { _, _ -> }, onDismiss = {}) }
        }
        rule.onNodeWithTag("playlist_name").performScrollTo().assertIsDisplayed()
        // Read the actual editable text layout in the separate Dialog window, rather than
        // assuming that a parent CompositionLocal density reached that window.
        val textLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        rule.onNodeWithTag("playlist_name").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult,
        ) { action -> assertTrue(action(textLayouts)) }
        rule.runOnIdle {
            assertTrue(textLayouts.any { it.layoutInput.text.text == "夜晚的音乐与长名称歌单" })
            textLayouts.forEach { assertEquals(2f, it.layoutInput.density.fontScale, 0.001f) }
        }
        rule.onNodeWithTag("playlist_description").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("playlist_draft_cancel").assertIsDisplayed()
        rule.onNodeWithTag("playlist_draft_save").assertIsDisplayed().assertIsEnabled()
        screenshot("library-form-large-zh")
    }

    private fun dragMember(from: String, to: String) {
        val source = rule.onNodeWithTag("playlist_order_entry_$from").fetchSemanticsNode().boundsInRoot
        val target = rule.onNodeWithTag("playlist_order_entry_$to").fetchSemanticsNode().boundsInRoot
        val delta = target.center.y - source.center.y
        val handle = rule.onNodeWithTag("playlist_order_drag_$from")
        handle.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        handle.performTouchInput {
            moveBy(Offset(0f, delta + if (delta > 0f) 12f else -12f), delayMillis = 48)
            up()
        }
        rule.waitForIdle()
    }
    private fun screenshot(name: String) {
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        val directory = File(checkNotNull(System.getProperty("flaretune.screenshots.dir"))).apply { mkdirs() }
        rule.runOnIdle {
            val windows = android.view.inspector.WindowInspector.getGlobalWindowViews()
            val view = windows.lastOrNull { it.isShown && it.width > 0 && it.height > 0 } ?: rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(bitmap.width > 100 && bitmap.height > 100)
        }
    }
}