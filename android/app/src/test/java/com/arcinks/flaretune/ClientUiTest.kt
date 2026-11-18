package com.arcinks.flaretune

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.arcinks.flaretune.model.*
import com.arcinks.flaretune.ui.*
import com.arcinks.flaretune.ui.theme.FlareTuneTheme
import kotlinx.coroutines.flow.MutableStateFlow
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
class ClientUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val actions = RecordingActions()
    private val titles = listOf("After the rain", "Blue hour", "落日回声", "Along the river", "Wake slowly", "深夜电台")
    private val songs = (0 until 12).map { index ->
        Song("track-" + index, titles[index / 2] + if (index % 2 == 1) " / II" else "",
            listOf("FlareTune", "The Slow Hours", "林间来信")[index / 4], titles[index / 2],
            180.0 + index * 7, "https://fixture.invalid/track-" + index + ".mp3",
            "https://fixture.invalid/art/" + index / 2)
    }
    private val albums = (0 until 6).map { index ->
        Album("album" + index, titles[index], songs[index * 2].artist, songs[index * 2].coverUrl, 2)
    }
    private fun fixture(destination: MainDestination = MainDestination.HOME) = ClientUiState(
        screen = ClientScreen.LIBRARY, instanceUrl = "https://music.example", accountName = "Listener",
        songs = songs, albums = albums, hasMore = true, destination = destination,
        playback = PlaybackSnapshot(songs[0], songs, 0, true, positionMs = 30_000, durationMs = 180_000, volume = .7f))

    private fun render(initial: ClientUiState, artwork: Boolean = true, largeText: Boolean = false) {
        val state = mutableStateOf(initial)
        actions.onNavigate = { state.value = state.value.copy(destination = it, selectedAlbum = null, albumSongs = emptyList()) }
        actions.onTab = { state.value = state.value.copy(tab = it) }
        actions.onAlbum = { album ->
            state.value = state.value.copy(selectedAlbum = album, albumSongs = songs.filter { it.album == album.title })
        }
        actions.onCloseAlbum = { state.value = state.value.copy(selectedAlbum = null, albumSongs = emptyList()) }
        actions.onVolume = { volume -> state.value = state.value.copy(playback = state.value.playback.copy(volume = volume)) }
        actions.onCheckInstance = { origin ->
            state.value = state.value.copy(
                instanceUrl = origin,
                connectStep = ConnectStep.CREDENTIALS,
            )
        }
        val loader = if (artwork) fixtureLoader() else null
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 2f else density.fontScale)) {
                FlareTuneTheme { ClientApp(state.value, actions, loader) }
            }
        }
    }

    @Test fun connectSubmitsTheExactInputAndClearsPassword() {
        render(ClientUiState(screen = ClientScreen.CONNECT), artwork = false)
        rule.onNodeWithTag("connect_url").performTextInput("https://music.example")
        rule.onNodeWithTag("connect_check").performClick()
        rule.onNodeWithTag("connect_username").performTextInput("listener")
        rule.onNodeWithTag("connect_password").performTextInput("ephemeral-password")
        rule.onNodeWithTag("connect_submit").performClick()
        rule.runOnIdle { assertEquals(listOf("https://music.example", "listener", "ephemeral-password"), actions.credentials) }
        rule.onNodeWithTag("connect_password").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        screenshot("v2-connect")
    }

    @Test fun libraryNavigationSearchAndRowsDispatchActualActions() {
        render(fixture(MainDestination.HOME))
        rule.onNodeWithTag("home_all_songs").performScrollTo().performClick()
        rule.onNodeWithTag("song_track-0").performClick()
        rule.runOnIdle { assertEquals("track-0", actions.song?.id) }
        rule.onNodeWithTag("nav_search").performClick()
        rule.onNodeWithTag("account_menu").performClick()
        rule.onNodeWithTag("account_refresh").performClick()
        rule.onNodeWithTag("search_field").performTextInput("rain")
        rule.onNodeWithTag("search_submit").performClick()
        rule.runOnIdle {
            assertEquals(MainDestination.SEARCH, actions.destination)
            assertEquals("rain", actions.query)
            assertEquals(1, actions.refreshes)
        }
        screenshot("v2-search")
    }

    @Test fun homeAlbumCardsOpenARealDetailAndPlayItsFirstTrack() {
        render(fixture())
        rule.onNodeWithTag("home_page").assertExists()
        rule.onNodeWithTag("nav_discover").performClick()
        rule.onNodeWithTag("discover_page").assertExists()
        rule.onNodeWithTag("album_album0").performScrollTo().performClick()
        rule.onNodeWithTag("album_detail").assertExists()
        rule.onNodeWithTag("album_play").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals("album0", actions.album?.id)
            assertEquals("track-0", actions.song?.id)
        }
        screenshot("v2-album")
        rule.onNodeWithTag("album_back").assertIsDisplayed().performClick()
        rule.onNodeWithTag("discover_page").assertExists()
    }

    @Test fun playbackQueueSeekVolumeAndBackgroundRemainFunctional() {
        render(fixture())
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_play").performScrollTo().performClick()
        rule.onNodeWithTag("player_next").performClick()
        rule.onNodeWithTag("player_mode").performClick()
        rule.onNodeWithTag("player_seek").performScrollTo().performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(60_000f) }
        rule.runOnIdle {
            assertEquals(1, actions.toggles); assertEquals(1, actions.nexts)
            assertEquals(60_000L, actions.seekPosition)
        }
        screenshot("v2-player-light")
        rule.onNodeWithTag("player_more").performScrollTo().performClick()
        rule.onNodeWithTag("song_actions_sheet").assertExists()
        rule.onNodeWithTag("song_action_next").assertExists()
        rule.onNodeWithTag("song_action_playlist").assertExists()
        rule.onNodeWithTag("song_action_favorite").assertExists()
        screenshot("v2-player-song-actions")
        rule.onNodeWithTag("song_actions_close").performScrollTo().performClick()
        rule.onNodeWithTag("song_actions_sheet").assertDoesNotExist()
        rule.onNodeWithTag("open_queue").performScrollTo().performClick()
        rule.onNodeWithTag("queue_panel").assertExists()
        rule.onNodeWithTag("queue_item_1").performClick()
        rule.runOnIdle { assertEquals(1, actions.queueIndex) }
        rule.onNodeWithTag("queue_panel").assertIsDisplayed()
    }

    @Test fun emptySongSearchFailureRetriesOnlySongs() {
        render(fixture(MainDestination.SEARCH).copy(
            query = "Come Into My World (Extended Version) - Alexandra Stan / NERVO",
            songs = emptyList(),
            notice = ClientNotice.REQUEST_FAILED,
        ))
        rule.onNodeWithTag("search_songs_retry").performScrollTo().performClick()
        rule.onNodeWithTag("search_artists_retry").assertDoesNotExist()
        rule.onNodeWithTag("search_albums_retry").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(1, actions.songSearchRetries)
            assertEquals(0, actions.refreshes)
        }
        screenshot("v2-catalog-error")
    }

    @Test fun passwordMismatchDoesNotSubmitCredentials() {
        render(ClientUiState(screen = ClientScreen.CHANGE_PASSWORD), artwork = false)
        rule.onNodeWithTag("change_current").performTextInput("old")
        rule.onNodeWithTag("change_new").performTextInput("one")
        rule.onNodeWithTag("change_confirm").performTextInput("different")
        rule.onNodeWithTag("change_submit").performClick()
        rule.runOnIdle { assertEquals(0, actions.passwordChanges) }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w390dp-h844dp-night-mdpi")
    fun compactHomeShowsRichContentAboveSeparateNavigationAndPlayer() {
        render(fixture().copy(
            artists = listOf(Artist("FlareTune", 4, 2)),
            languageCounts = mapOf("all" to 12, "zh" to 3, "en" to 5),
        ))
        rule.onNodeWithTag("dock_compact").assertExists()
        val dockBounds = rule.onNodeWithTag("dock_compact").fetchSemanticsNode().boundsInRoot
        val rootBounds = rule.onNodeWithTag("home_page").fetchSemanticsNode().boundsInRoot
        val density = rule.density.density
        assertTrue("Compact dock should flush to left/right/bottom edges", dockBounds.left <= 1f && kotlin.math.abs(dockBounds.right - rootBounds.right) <= 1f && kotlin.math.abs(dockBounds.bottom - rootBounds.bottom) <= 1f)
        assertTrue("Compact dock should keep enlarged balanced height (134..146dp), was ${dockBounds.height / density}dp", dockBounds.height / density in 134f..146f)
        rule.onNodeWithTag("home_featured_cards").assertExists()
        rule.onNodeWithTag("home_favorites").assertExists()
        rule.onNodeWithTag("home_history").assertDoesNotExist()
        rule.onNodeWithTag("home_featured_cards").performScrollToIndex(2)
        rule.onNodeWithTag("home_history").assertIsDisplayed()
        rule.onNodeWithTag("home_featured_cards").performScrollToIndex(0)
        rule.onNodeWithTag("nav_discover").assertExists()
        rule.onNodeWithTag("nav_home").assertIsSelected()
        screenshot("v2-home-compact")
        rule.onNodeWithTag("nav_discover").performClick()
        rule.onNodeWithTag("discover_page").assertExists()
        rule.onNodeWithTag("discover_lang_zh").assertExists()
        rule.onNodeWithTag("discover_artist_shelf").assertExists()
        rule.onNodeWithTag("album_album0").assertExists()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w1280dp-h800dp-night-mdpi")
    fun wideHomePlacesNavigationAndMiniPlayerSideBySide() {
        render(fixture())
        rule.onNodeWithTag("dock_wide").assertExists()
        rule.onNodeWithTag("nav_discover").assertExists()
        val nav = rule.onNodeWithTag("nav_library").fetchSemanticsNode().boundsInRoot
        val mini = rule.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
        assertTrue("Wide dock overlaps", nav.right <= mini.left)
        screenshot("v2-home-wide")
        // Wide MiniPlayer disables vertical swipe-up to avoid conflicting with tablet system gestures.
        rule.onNodeWithTag("mini_player").performTouchInput {
            swipeUp(startY = bottom - 6f, endY = top - 80f)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("player_mode").assertDoesNotExist()
        rule.onNodeWithTag("mini_player").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("player_mode").assertIsDisplayed()
        screenshot("v2-player-wide")
        rule.onNodeWithTag("player_close").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("player_mode").assertDoesNotExist()
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_mode").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w390dp-h844dp-mdpi")
    fun miniPlayerUnifiedVerticalDragExpandsAndCollapsesPlayer() {
        render(fixture())
        rule.onNodeWithTag("mini_player").assertIsDisplayed()
        // Small upward drag below open threshold settles cleanly back to MiniPlayer.
        rule.onNodeWithTag("mini_player").performTouchInput {
            down(center)
            moveBy(Offset(0f, -36f))
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("player_mode").assertDoesNotExist()

        // Full vertical swipe expands via the unified sheet progress.
        rule.onNodeWithTag("mini_player").performTouchInput {
            swipeUp(startY = bottom - 6f, endY = top - 220f)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("player_mode").assertIsDisplayed()

        // Dragging down on the top collapse handle dismisses back into MiniPlayer.
        rule.onNodeWithTag("player_close", useUnmergedTree = true).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 520f)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("player_mode").assertDoesNotExist()
        rule.onNodeWithTag("mini_player").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w393dp-h851dp-night-xxhdpi")
    fun compactLyricsModePersistsAcrossPlayerCloseAndReopen() {
        render(fixture())
        rule.onNodeWithTag("mini_player").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("player_artwork").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("player_lyrics_top_row").assertIsDisplayed()

        rule.onNodeWithTag("player_close").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("player_lyrics_top_row").assertDoesNotExist()

        rule.onNodeWithTag("mini_player").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("player_lyrics_top_row").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h640dp-night-mdpi")
    fun chineseDarkLibrarySupportsLargeTextAndVisibleControls() {
        render(fixture(MainDestination.LIBRARY), largeText = true)
        val density = rule.density.density
        val miniPlayerBounds = rule.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
        assertTrue("Compact mini player should expand for 200% fontScale (>=74dp), was ${miniPlayerBounds.height / density}dp", miniPlayerBounds.height / density >= 74f)
        rule.onNodeWithTag("library_tab_favorites").performClick()
        rule.runOnIdle { assertEquals(LibraryTab.FAVORITES, actions.tab) }
        screenshot("v2-library-large-zh")
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_mode").performScrollTo().assertIsDisplayed()
        screenshot("v2-player-large-zh")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h760dp-night-mdpi")
    fun narrowFloatingWindowKeepsAllFourNavigationDestinationsReachable() {
        render(fixture())
        rule.onNodeWithTag("dock_compact").assertExists()
        val dockBounds = rule.onNodeWithTag("dock_compact").fetchSemanticsNode().boundsInRoot
        val rootBounds = rule.onNodeWithTag("home_page").fetchSemanticsNode().boundsInRoot
        val density = rule.density.density
        assertTrue("Compact dock should flush to left/right/bottom edges", dockBounds.left <= 1f && kotlin.math.abs(dockBounds.right - rootBounds.right) <= 1f && kotlin.math.abs(dockBounds.bottom - rootBounds.bottom) <= 1f)
        assertTrue("Compact dock should keep enlarged balanced height (134..146dp), was ${dockBounds.height / density}dp", dockBounds.height / density in 134f..146f)
        rule.onNodeWithTag("nav_discover").performClick()
        rule.onNodeWithTag("discover_page").assertExists()
        rule.onNodeWithTag("nav_search").performClick()
        rule.onNodeWithTag("search_field").assertIsDisplayed()
        rule.onNodeWithTag("nav_library").performClick()
        rule.onNodeWithTag("account_menu").assertIsEnabled()
        rule.onNodeWithTag("library_tab_favorites").performClick()
        rule.runOnIdle { assertEquals(LibraryTab.FAVORITES, actions.tab) }
        rule.onNodeWithTag("library_tab_catalog").assertIsEnabled()
        screenshot("v2-floating-library")
    }


    @Test fun androidBackClosesThePlayerBeforeTheUnderlyingAlbum() {
        render(fixture())
        rule.onNodeWithTag("nav_discover").performClick()
        rule.onNodeWithTag("album_album0").performScrollTo().performClick()
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("full_player").assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("full_player").assertDoesNotExist()
        rule.onNodeWithTag("album_detail").assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("discover_page").assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("home_page").assertExists()
    }

    @Test fun discoverShowsLanguageCountsWithoutHomeAlbumsAndDispatchesFilter() {
        render(
            fixture().copy(
                discoverySongs = listOf(songs[2], songs[3]),
                artists = listOf(Artist("FlareTune", 4, 2)),
                languageCounts = mapOf("all" to 12, "zh" to 3, "en" to 5),
            ),
        )
        rule.onNodeWithTag("home_page").assertExists()
        rule.onNodeWithTag("home_discovery").assertExists()
        rule.onNodeWithTag("home_album_shelf").assertDoesNotExist()
        rule.onNodeWithTag("nav_discover").performClick()
        rule.onNodeWithTag("discover_page").assertExists()
        rule.onNodeWithText("Chinese").assertExists()
        rule.onNodeWithText("Chinese (3)").assertDoesNotExist()
        rule.onNodeWithTag("discover_lang_zh").performClick()
        rule.runOnIdle { assertEquals("zh", actions.discoverLanguageSelected) }
    }

    @Test fun mobileLyricsBackReturnsToArtworkAndTrackSkipPreservesLyricsPane() {
        val lyrics = LyricState(
            songId = songs[0].id,
            status = LyricStatus.READY,
            lines = listOf(LyricLine("Line 1", timeSeconds = 0.0)),
            activeLyricIndex = 0,
        )
        val state = mutableStateOf(fixture().copy(lyricState = lyrics))
        actions.onNavigate = { state.value = state.value.copy(destination = it) }
        rule.setContent { FlareTuneTheme { ClientApp(state.value, actions, null) } }
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_lyrics_toggle").assertIsDisplayed()
        rule.onNodeWithTag("player_lyrics_toggle").performClick()
        rule.onNodeWithTag("player_lyrics_list").assertExists()
        rule.runOnIdle {
            state.value = state.value.copy(
                playback = state.value.playback.copy(current = songs[1], currentIndex = 1),
                lyricState = lyrics.copy(songId = songs[1].id),
            )
        }
        rule.onNodeWithTag("player_lyrics_list").assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("full_player").assertExists()
        rule.onNodeWithTag("player_lyrics_list").assertDoesNotExist()
    }

    @Test fun translationSwitchAndGenerationRemainSeparateAndMiniPlayerObeysSwitch() {
        val state = mutableStateOf(
            fixture().copy(
                lyricState = LyricState(
                    songId = songs[0].id,
                    status = LyricStatus.READY,
                    lines = listOf(LyricLine("Hello world", translation = "你好世界", timeSeconds = 0.0)),
                    activeLyricIndex = 0,
                    translationState = LyricTranslationStatus.IDLE,
                    translationSupported = true,
                ),
                translationEnabled = false,
            ),
        )
        actions.onSetTranslationEnabled = { enabled -> state.value = state.value.copy(translationEnabled = enabled) }
        val loader = fixtureLoader()
        rule.setContent { FlareTuneTheme { ClientApp(state.value, actions, loader) } }

        // Mini player hides translation while translationEnabled == false.
        rule.onNodeWithTag("mini_player").assertIsDisplayed()
        rule.onAllNodesWithText("你好世界").assertCountEquals(0)

        // Open full player, tap lyrics toggle beside more to open lyrics, toggle display without triggering generation.
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_lyrics_toggle").assertIsDisplayed()
        rule.onNodeWithTag("player_lyrics_toggle").performClick()
        rule.onAllNodesWithText("你好世界").assertCountEquals(0)
        rule.onNodeWithTag("player_translate_button").performClick()
        rule.runOnIdle {
            assertEquals(true, actions.lastTranslationEnabled)
            assertEquals(0, actions.lyricTranslationRequests)
        }
        rule.onNodeWithText("你好世界").assertIsDisplayed()

        // Without inline translations in IDLE mode, the unified translation button requests generation.
        rule.runOnIdle {
            state.value = state.value.copy(
                translationEnabled = false,
                lyricState = state.value.lyricState.copy(
                    lines = listOf(LyricLine("Hello world", translation = null, timeSeconds = 0.0)),
                    translationState = LyricTranslationStatus.IDLE,
                    translationSupported = true,
                ),
            )
        }
        rule.onNodeWithTag("player_translate_button").performClick()
        rule.runOnIdle {
            assertEquals(true, actions.lastTranslationEnabled)
            assertEquals(1, actions.lyricTranslationRequests)
        }

        // Restore inline translation and close full player; mini player reflects translationEnabled == true.
        rule.runOnIdle {
            state.value = state.value.copy(
                lyricState = state.value.lyricState.copy(
                    lines = listOf(LyricLine("Hello world", translation = "你好世界", timeSeconds = 0.0)),
                ),
            )
        }
        rule.onNodeWithTag("player_close").performClick()
        rule.onNodeWithText("你好世界").assertIsDisplayed()
    }

    @Test fun queuePanelTriggersRemoveQueueItemAction() {
        render(fixture())
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("open_queue").performScrollTo().performClick()
        rule.onNodeWithTag("queue_item_0").performTouchInput { swipeLeft() }
        rule.runOnIdle { assertEquals(listOf(0), actions.removedQueueIndices) }
    }

    @Test fun queueRowMenuRemovesOnlyItsOwnQueueEntry() {
        render(fixture())
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("open_queue").performScrollTo().performClick()
        rule.onNodeWithTag("queue_more_1").performClick()
        rule.onNodeWithTag("song_actions_sheet").assertExists()
        rule.onNodeWithTag("song_action_remove").performClick()
        rule.runOnIdle { assertEquals(listOf(1), actions.removedQueueIndices) }
        rule.onNodeWithTag("queue_panel").assertIsDisplayed()
    }

    @Test fun queuePanelPartialSwipeReversesWithoutRemoving() {
        render(fixture())
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("open_queue").performScrollTo().performClick()
        rule.onNodeWithTag("queue_item_0").performTouchInput {
            down(center)
            moveBy(Offset(-36f, 0f))
            moveBy(Offset(28f, 0f))
            up()
        }
        rule.runOnIdle { assertTrue(actions.removedQueueIndices.isEmpty()) }
    }

    @Test fun queuePanelDragHandleReordersQueueItem() {
        render(fixture())
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("open_queue").performScrollTo().performClick()

        // Slight accidental vertical brush does not activate reorder.
        rule.onNodeWithTag("queue_drag_0", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, 8f))
            up()
        }
        rule.runOnIdle { assertTrue(actions.movedQueuePairs.isEmpty()) }

        // Current track relies on color distinction rather than visible status text.
        val playingLabel = rule.activity.getString(R.string.ft_playing)
        val pausedLabel = rule.activity.getString(R.string.ft_paused)
        assertTrue(rule.onAllNodesWithText(playingLabel).fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText(pausedLabel).fetchSemanticsNodes().isEmpty())

        val firstBounds = rule.onNodeWithTag("queue_item_0").fetchSemanticsNode().boundsInRoot
        val secondBounds = rule.onNodeWithTag("queue_item_1").fetchSemanticsNode().boundsInRoot
        val distance = (secondBounds.center.y - firstBounds.center.y) + 40f
        rule.onNodeWithTag("queue_drag_0", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, distance / 2f))
            moveBy(Offset(0f, distance / 2f))
            up()
        }
        rule.runOnIdle { assertTrue(actions.movedQueuePairs.contains(0 to 1)) }
    }

    @Test fun queuePanelDragHandleReordersDuplicateEntriesAndScrollsNearEdge() {
        val duplicateQueue = listOf(songs[0], songs[0]) + songs.drop(1)
        render(
            fixture().copy(
                playback = PlaybackSnapshot(duplicateQueue[0], duplicateQueue, 0, true, positionMs = 30_000, durationMs = 180_000, volume = .7f),
            ),
        )
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("open_queue").performScrollTo().performClick()

        val firstBounds = rule.onNodeWithTag("queue_item_0").fetchSemanticsNode().boundsInRoot
        val secondBounds = rule.onNodeWithTag("queue_item_1").fetchSemanticsNode().boundsInRoot
        val stepDistance = (secondBounds.center.y - firstBounds.center.y) + 40f
        rule.onNodeWithTag("queue_drag_0", useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, stepDistance / 2f))
            moveBy(Offset(0f, stepDistance / 2f))
            up()
        }
        rule.runOnIdle { assertTrue(actions.movedQueuePairs.contains(0 to 1)) }

        actions.movedQueuePairs.clear()
        val lastVisibleIdx = (11 downTo 1).first { idx ->
            rule.onAllNodesWithTag("queue_drag_$idx", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val listBottom = rule.onNodeWithTag("queue_panel").fetchSemanticsNode().boundsInRoot.bottom
        val startCenterY = rule.onNodeWithTag("queue_drag_$lastVisibleIdx", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.y
        val overscrollDy = (listBottom - startCenterY + 60f).coerceAtLeast(80f)
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("queue_drag_$lastVisibleIdx", useUnmergedTree = true).performTouchInput {
                down(center)
                moveBy(Offset(0f, overscrollDy / 2f))
                moveBy(Offset(0f, overscrollDy / 2f))
            }
            repeat(30) {
                rule.mainClock.advanceTimeByFrame()
            }
            rule.onNodeWithTag("queue_panel").performTouchInput { up() }
        } finally {
            rule.mainClock.autoAdvance = true
        }
        rule.runOnIdle {
            assertTrue(
                "Expected drag from index $lastVisibleIdx past bottom edge to move downward, got ${actions.movedQueuePairs}",
                actions.movedQueuePairs.any { (from, to) -> from == lastVisibleIdx && to > from },
            )
        }
    }

    @Test fun subpageBackButtonReturnsToDiscoverWithoutClosingApp() {
        render(fixture(MainDestination.DISCOVER))
        rule.onNodeWithTag("discover_page").assertExists()
        rule.onNodeWithTag("discover_all_songs").performScrollTo().performClick()
        rule.onNodeWithTag("home_all_songs_page").assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("discover_page").assertExists()
        rule.onNodeWithTag("home_all_songs_page").assertDoesNotExist()
    }

    @Test fun discoverDoesNotRenderSpotlightArtistCard() {
        render(fixture(MainDestination.DISCOVER).copy(
            artists = listOf(Artist("FlareTune", 4, 2)),
            spotlightArtist = SpotlightArtist("Hidden Spotlight", 5, songs.take(3)),
        ))
        rule.onNodeWithTag("discover_page").assertExists()
        rule.onNodeWithTag("discover_artist_shelf").assertExists()
        rule.onNodeWithText("Hidden Spotlight").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w390dp-h844dp-night-mdpi")
    fun narrowLibraryUsesVerticalListRowsAndClearsTopSafeInset() {
        val playlists = listOf(
            PlaylistSummary("fav", "我的收藏", "favorite", songCount = 13),
            PlaylistSummary("p1", "夜间流行", "regular", songCount = 8),
        )
        render(fixture(MainDestination.LIBRARY).copy(
            playlists = playlists,
            playlistShelf = PlaylistShelf(2, items = playlists.map { ShelfItem(it.id) }),
        ))
        val density = rule.density.density
        val accountBounds = rule.onNodeWithTag("account_menu").fetchSemanticsNode().boundsInRoot
        assertTrue("Top header must clear >=24dp safe inset, was ${accountBounds.top / density}dp", accountBounds.top / density >= 23.9f)
        val rootBounds = rule.onNodeWithTag("library_page").fetchSemanticsNode().boundsInRoot
        val favBounds = rule.onNodeWithTag("library_tab_favorites").fetchSemanticsNode().boundsInRoot
        val secondBounds = rule.onNodeWithTag("library_playlist_p1").fetchSemanticsNode().boundsInRoot
        assertTrue("Narrow library rows should fill list width, was ${favBounds.width / rootBounds.width}", favBounds.width >= rootBounds.width * 0.85f)
        assertTrue("Narrow library rows should stack vertically", secondBounds.top >= favBounds.bottom - 1f && kotlin.math.abs(secondBounds.left - favBounds.left) <= 2f)
        screenshot("v2-library-narrow-list")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w1280dp-h800dp-night-mdpi")
    fun wideLibraryPreservesMultiColumnGridCards() {
        val playlists = listOf(
            PlaylistSummary("fav", "我的收藏", "favorite", songCount = 13),
            PlaylistSummary("p1", "夜间流行", "regular", songCount = 8),
        )
        render(fixture(MainDestination.LIBRARY).copy(
            playlists = playlists,
            playlistShelf = PlaylistShelf(2, items = playlists.map { ShelfItem(it.id) }),
        ))
        val favBounds = rule.onNodeWithTag("library_tab_favorites").fetchSemanticsNode().boundsInRoot
        val secondBounds = rule.onNodeWithTag("library_playlist_p1").fetchSemanticsNode().boundsInRoot
        assertTrue("Wide library should keep multi-column grid row", kotlin.math.abs(secondBounds.top - favBounds.top) <= 2f && secondBounds.left >= favBounds.right - 1f)
    }

    private fun fixtureLoader(): coil.ImageLoader {
        val palettes = listOf(0xFF174C59.toInt(), 0xFF614676.toInt(), 0xFFAF7047.toInt(),
            0xFF335B72.toInt(), 0xFF9D3F54.toInt(), 0xFF8D7D48.toInt())
        val images = palettes.mapIndexed { index, color ->
            val bitmap = Bitmap.createBitmap(360, 360, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).apply {
                drawPaint(android.graphics.Paint().apply {
                    shader = android.graphics.LinearGradient(0f, 0f, 360f, 360f,
                        color, 0xFF17222D.toInt(), android.graphics.Shader.TileMode.CLAMP)
                })
                drawCircle(260f, 90f, 66f, android.graphics.Paint(3).apply { this.color = 0xFFE6BD83.toInt() })
                drawRect(0f, 230f, 360f, 360f, android.graphics.Paint().apply { this.color = color })
                drawText("FLARE / " + (index + 1), 24f, 295f, android.graphics.Paint(3).apply {
                    this.color = 0xFFF4EBDD.toInt(); textSize = 32f; typeface = android.graphics.Typeface.DEFAULT_BOLD
                })
            }
            android.graphics.drawable.BitmapDrawable(rule.activity.resources, bitmap)
        }
        return coil.ImageLoader.Builder(rule.activity).components {
            add(object : coil.intercept.Interceptor {
                override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
                    val index = chain.request.data.toString().substringAfterLast('/').toIntOrNull() ?: 0
                    return coil.request.SuccessResult(images[index.mod(images.size)], chain.request, coil.decode.DataSource.MEMORY)
                }
            })
        }.build()
    }

    private fun screenshot(name: String) {
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        val directory = File(checkNotNull(System.getProperty("flaretune.screenshots.dir"))).apply { mkdirs() }
        rule.runOnIdle {
            val views = android.view.inspector.WindowInspector.getGlobalWindowViews()
            val view = views.lastOrNull { it.isShown && it.width > 0 && it.height > 0 } ?: rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(bitmap.width > 100 && bitmap.height > 100)
        }
    }

    @Test fun mobileLyricsTopRowReturnsToArtworkAndTranslationSharesTopRow() {
        val lyrics = LyricState(
            songId = songs[0].id,
            status = LyricStatus.READY,
            lines = listOf(LyricLine("Line 1", translation = "第一行", timeSeconds = 0.0)),
            activeLyricIndex = 0,
            translationState = LyricTranslationStatus.READY,
        )
        val state = mutableStateOf(fixture().copy(lyricState = lyrics, translationEnabled = true))
        actions.onNavigate = { state.value = state.value.copy(destination = it) }
        rule.setContent { FlareTuneTheme { ClientApp(state.value, actions, fixtureLoader()) } }
        rule.onNodeWithTag("mini_player").performClick()
        val artBounds = rule.onNodeWithTag("player_artwork").fetchSemanticsNode().boundsInRoot
        val seekBounds = rule.onNodeWithTag("player_seek").fetchSemanticsNode().boundsInRoot
        val moreSongBounds = rule.onNodeWithTag("player_more").fetchSemanticsNode().boundsInRoot
        val rootWidth = rule.onNodeWithTag("full_player").fetchSemanticsNode().boundsInRoot.width
        assertEquals(rootWidth / 2f, artBounds.center.x, 3f)
        assertTrue(seekBounds.left >= 18f)
        assertTrue(moreSongBounds.right <= rootWidth - 8f)
        val lyricsToggleBounds = rule.onNodeWithTag("player_lyrics_toggle").fetchSemanticsNode().boundsInRoot
        assertEquals(moreSongBounds.center.y, lyricsToggleBounds.center.y, 2f)
        assertTrue(lyricsToggleBounds.center.x < moreSongBounds.center.x)
        assertTrue(lyricsToggleBounds.right <= moreSongBounds.center.x + 2f)
        rule.onNodeWithTag("player_lyric_preview").assertDoesNotExist()
        val seekBefore = rule.onNodeWithTag("player_seek").fetchSemanticsNode().boundsInRoot
        val modeBefore = rule.onNodeWithTag("player_mode").fetchSemanticsNode().boundsInRoot
        val playBefore = rule.onNodeWithTag("player_play").fetchSemanticsNode().boundsInRoot
        screenshot("v2-player-mobile-song")
        rule.onNodeWithTag("player_lyrics_toggle").performClick()
        rule.onNodeWithTag("player_lyrics_list").assertExists()
        screenshot("v2-player-mobile-lyrics")
        val seekLyrics = rule.onNodeWithTag("player_seek").fetchSemanticsNode().boundsInRoot
        val modeLyrics = rule.onNodeWithTag("player_mode").fetchSemanticsNode().boundsInRoot
        val playLyrics = rule.onNodeWithTag("player_play").fetchSemanticsNode().boundsInRoot
        assertEquals(seekBefore.top, seekLyrics.top, 0.5f)
        assertEquals(seekBefore.bottom, seekLyrics.bottom, 0.5f)
        assertEquals(modeBefore.top, modeLyrics.top, 0.5f)
        assertEquals(playBefore.top, playLyrics.top, 0.5f)
        assertEquals(playBefore.height, playLyrics.height, 0.5f)
        val topRowBounds = rule.onNodeWithTag("player_lyrics_top_row").fetchSemanticsNode().boundsInRoot
        val translateBounds = rule.onNodeWithTag("player_translate_button").fetchSemanticsNode().boundsInRoot
        val moreBounds = rule.onNodeWithTag("player_more").fetchSemanticsNode().boundsInRoot
        assertEquals(moreBounds.center.y, translateBounds.center.y, 2f)
        assertTrue(translateBounds.top >= topRowBounds.top - 4f && translateBounds.bottom <= topRowBounds.bottom + 4f)
        rule.onNodeWithTag("player_lyrics_top_row").performClick()
        rule.onNodeWithTag("player_lyrics_list").assertDoesNotExist()
        rule.onNodeWithTag("player_artwork").assertExists()
        val seekAfter = rule.onNodeWithTag("player_seek").fetchSemanticsNode().boundsInRoot
        val playAfter = rule.onNodeWithTag("player_play").fetchSemanticsNode().boundsInRoot
        assertEquals(seekBefore.top, seekAfter.top, 0.5f)
        assertEquals(playBefore.top, playAfter.top, 0.5f)
    }

    @Test fun compactFloatingPlayerHidesClippedPreviewAndExpandsLyricsViewport() {
        val lyrics = LyricState(
            songId = songs[0].id,
            status = LyricStatus.READY,
            lines = listOf(
                LyricLine("Short line", translation = "短句", timeSeconds = 0.0),
                LyricLine("Second line", translation = "第二句", timeSeconds = 3.0),
            ),
            activeLyricIndex = 0,
            translationState = LyricTranslationStatus.READY,
        )
        rule.setContent {
            FlareTuneTheme {
                Box(Modifier.size(320.dp, 470.dp)) {
                    NowPlayingScreen(
                        state = PlaybackSnapshot(songs[0], songs, 0, true, positionMs = 30_000, durationMs = 180_000),
                        lyricState = lyrics,
                        translationEnabled = true,
                        actions = actions,
                        imageLoader = null,
                        onClose = {},
                        onQueue = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("player_artwork").assertIsDisplayed()
        rule.onNodeWithTag("player_lyrics_toggle").assertIsDisplayed()
        rule.onNodeWithTag("player_play").assertIsDisplayed()
        rule.onNodeWithTag("player_lyric_preview").assertDoesNotExist()
        val seekSong = rule.onNodeWithTag("player_seek").fetchSemanticsNode().boundsInRoot
        val playSong = rule.onNodeWithTag("player_play").fetchSemanticsNode().boundsInRoot
        screenshot("v2-player-floating-song")
        rule.onNodeWithTag("player_artwork").performClick()
        rule.onNodeWithTag("player_lyrics_list").assertExists()
        screenshot("v2-player-floating-lyrics")
        val lyricsBounds = rule.onNodeWithTag("player_lyrics_list").fetchSemanticsNode().boundsInRoot
        val topRowBounds = rule.onNodeWithTag("player_lyrics_top_row").fetchSemanticsNode().boundsInRoot
        val seekBounds = rule.onNodeWithTag("player_seek").fetchSemanticsNode().boundsInRoot
        val playLyrics = rule.onNodeWithTag("player_play").fetchSemanticsNode().boundsInRoot
        assertEquals(seekSong.top, seekBounds.top, 0.5f)
        assertEquals(playSong.top, playLyrics.top, 0.5f)
        assertEquals(playSong.height, playLyrics.height, 0.5f)
        assertTrue(lyricsBounds.height >= 240f)
        assertTrue(lyricsBounds.top >= topRowBounds.bottom - 2f)
        assertTrue(lyricsBounds.bottom <= seekBounds.top + 2f)
    }

    @Test fun karaokeFocusCharOffsetTracksWordProgressAccurately() {
        val words = listOf(
            LyricWord("One ", 1.0, 2.0),
            LyricWord("Two", 2.0, 4.0),
        )
        assertEquals(0f, computeKaraokeFocusCharOffset(words, 0.5), 0.01f)
        assertEquals(2f, computeKaraokeFocusCharOffset(words, 1.5), 0.01f)
        assertEquals(5.5f, computeKaraokeFocusCharOffset(words, 3.0), 0.01f)
        assertEquals(7f, computeKaraokeFocusCharOffset(words, 5.0), 0.01f)
    }

    @Test fun wordSyncedMiniPlayerAndPreviewStripRenderWordLyricsWithoutBreakingLayout() {
        val longLineText = "This is a very long karaoke lyric line tracking smoothly across the viewport"
        val words = listOf(
            LyricWord("This is a very ", 1.0, 2.0),
            LyricWord("long karaoke lyric ", 2.0, 3.0),
            LyricWord("line tracking smoothly across the viewport", 3.0, 6.0),
        )
        val wordLyrics = LyricState(
            songId = songs[0].id,
            status = LyricStatus.READY,
            lines = listOf(
                LyricLine(longLineText, translation = "这是一句超长卡拉OK歌词", timeSeconds = 1.0, endTimeSeconds = 6.0, words = words),
            ),
            activeLyricIndex = 0,
            syncMode = "word",
            translationState = LyricTranslationStatus.READY,
        )
        val state = mutableStateOf(
            fixture(MainDestination.HOME).copy(
                playback = PlaybackSnapshot(songs[0], songs, 0, playing = false, positionMs = 1_100, durationMs = 180_000),
                lyricState = wordLyrics,
            ),
        )
        rule.setContent {
            FlareTuneTheme {
                ClientApp(state.value, actions, imageLoader = null)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("mini_player").assertIsDisplayed()
        val miniLyricNode = rule.onNode(
            hasText(longLineText) and hasAnyAncestor(hasTestTag("mini_player_card_current")),
            useUnmergedTree = true,
        )
        miniLyricNode.assertExists()
        val earlyLeft = miniLyricNode.fetchSemanticsNode().positionInRoot.x
        val lyricLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        miniLyricNode.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(lyricLayouts)
        assertTrue(lyricLayouts.isNotEmpty())
        assertFalse("Word-synced mini player lyric must scroll horizontally instead of ellipsizing", lyricLayouts.first().hasVisualOverflow)

        state.value = state.value.copy(
            playback = PlaybackSnapshot(songs[0], songs, 0, playing = false, positionMs = 5_200, durationMs = 180_000),
        )
        rule.waitForIdle()
        val lateLeft = rule.onNode(
            hasText(longLineText) and hasAnyAncestor(hasTestTag("mini_player_card_current")),
            useUnmergedTree = true,
        ).fetchSemanticsNode().positionInRoot.x
        assertTrue("Expected mini player horizontal focus scroll to shift left ($lateLeft < $earlyLeft)", lateLeft < earlyLeft - 10f)

        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_lyric_preview").assertDoesNotExist()
        rule.onNodeWithTag("player_lyrics_toggle").assertIsDisplayed().performClick()
        rule.onNodeWithTag("player_lyrics_list").assertExists()
        rule.onNodeWithText(longLineText).assertExists()
    }

    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    @Test fun portraitTabletPlayerScalesProportionallyAndKeepsControlsAligned() {
        val lyrics = LyricState(
            songId = songs[0].id,
            status = LyricStatus.READY,
            lines = listOf(
                LyricLine("First line of portrait tablet lyrics", translation = "竖屏平板第一句歌词", timeSeconds = 0.0),
                LyricLine("Second line scales comfortably", translation = "字号与宽度等比放大", timeSeconds = 3.0),
            ),
            activeLyricIndex = 0,
            translationState = LyricTranslationStatus.READY,
        )
        rule.setContent {
            FlareTuneTheme {
                NowPlayingScreen(
                    state = PlaybackSnapshot(songs[0], songs, 0, true, positionMs = 30_000, durationMs = 180_000),
                    lyricState = lyrics,
                    translationEnabled = true,
                    actions = actions,
                    imageLoader = null,
                    onClose = {},
                    onQueue = {},
                )
            }
        }
        val density = rule.density.density
        val artBounds = rule.onNodeWithTag("player_artwork").fetchSemanticsNode().boundsInRoot
        val moreBounds = rule.onNodeWithTag("player_more").fetchSemanticsNode().boundsInRoot
        val lyricsBtnBounds = rule.onNodeWithTag("player_lyrics_toggle").fetchSemanticsNode().boundsInRoot
        val queueBounds = rule.onNodeWithTag("open_queue").fetchSemanticsNode().boundsInRoot
        val artWidthDp = artBounds.width / density
        assertTrue("Portrait tablet artwork should expand past 440dp cap, was ${artWidthDp}dp", artWidthDp >= 600f)
        assertEquals(moreBounds.center.x, queueBounds.center.x, 2f)
        assertEquals(moreBounds.center.y, lyricsBtnBounds.center.y, 2f)
        assertTrue(lyricsBtnBounds.center.x < moreBounds.center.x)
        assertTrue(lyricsBtnBounds.right <= moreBounds.center.x + 2f)
        screenshot("v2-player-tablet-portrait-song")

        rule.onNodeWithTag("player_lyrics_toggle").performClick()
        rule.onNodeWithTag("player_lyrics_list").assertExists()
        val lyricsBounds = rule.onNodeWithTag("player_lyrics_list").fetchSemanticsNode().boundsInRoot
        assertTrue("Portrait tablet lyrics viewport should scale wide, was ${lyricsBounds.width / density}dp", lyricsBounds.width / density >= 600f)
        screenshot("v2-player-tablet-portrait-lyrics")
    }

    @Test fun positionMsFlowDrivesMiniPlayerPreviousSwipeAndTransportTime() {
        val positionFlow = MutableStateFlow(1_000L)
        val uiState = fixture(MainDestination.HOME).copy(
            playback = PlaybackSnapshot(
                current = songs[0],
                queue = listOf(songs[0], songs[1]),
                currentIndex = 0,
                playing = true,
                positionMs = 0L,
                durationMs = 180_000L,
            ),
        )
        rule.setContent {
            FlareTuneTheme {
                ClientApp(uiState, actions, imageLoader = null, positionMsFlow = positionFlow)
            }
        }
        rule.onNodeWithTag("mini_player").performTouchInput { swipeRight(startX = centerLeft.x + 20f, endX = centerRight.x - 20f) }
        rule.waitForIdle()
        assertEquals(0, actions.previousCalls)

        positionFlow.value = 4_500L
        rule.waitForIdle()
        rule.onNodeWithTag("mini_player").performTouchInput { swipeRight(startX = centerLeft.x + 20f, endX = centerRight.x - 20f) }
        rule.waitForIdle()
        assertEquals(1, actions.previousCalls)

        rule.onNodeWithTag("mini_player").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("0:04").assertExists()
        assertTrue(rule.onNodeWithTag("player_previous").fetchSemanticsNode().let { true })
    }

    @Test fun tabKeepAlivePreservesHomeScrollAndActiveTapScrollsToTop() {
        val longCatalog = (0 until 24).map { index ->
            Song(
                "keepalive-song-$index",
                "Keepalive Track $index",
                "FlareTune",
                "Echoes",
                180.0,
                "https://fixture.invalid/keepalive-$index.mp3",
                "https://fixture.invalid/art/${index % 4}",
            )
        }
        val currentState = mutableStateOf(
            fixture(MainDestination.HOME).copy(
                songs = longCatalog,
                continueListeningSongs = longCatalog.take(3),
                discoverySongs = longCatalog.drop(3).take(3),
            ),
        )
        actions.onNavigate = { destination ->
            currentState.value = currentState.value.copy(
                destination = destination,
                selectedAlbum = null,
                selectedArtist = null,
                selectedPlaylist = null,
            )
        }
        rule.setContent {
            FlareTuneTheme {
                ClientApp(currentState.value, actions, imageLoader = null)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("home_page").assertExists()
        rule.onNodeWithTag("home_featured_cards").assertIsDisplayed()
        rule.onNodeWithTag("home_filter_all").assertDoesNotExist()

        // Drive the vertical feed directly. The matching song's nearest scroller is its horizontal shelf.
        repeat(2) {
            rule.onNodeWithTag("home_feed").performTouchInput {
                swipeUp(startY = height * 0.70f, endY = height * 0.25f, durationMillis = 500)
            }
            rule.waitForIdle()
        }
        rule.onNodeWithTag("home_song_keepalive-song-0").assertIsDisplayed()

        // Switch to Discover tab: Home semantics must disappear immediately while keeping scroll state alive.
        rule.onNodeWithTag("nav_discover").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("discover_page").assertExists()
        rule.onNodeWithTag("home_page").assertDoesNotExist()

        // Switch back to Home tab: preserved scrolled section must remain active.
        rule.onNodeWithTag("nav_home").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_page").assertExists()
        rule.onNodeWithTag("discover_page").assertDoesNotExist()
        rule.onNodeWithTag("home_song_keepalive-song-0").assertIsDisplayed()

        // Tap active Home tab again while at root: must scroll smoothly back to top.
        rule.onNodeWithTag("nav_home").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_featured_cards").assertIsDisplayed()
    }

    @Test fun miniPlayerSwipeUpOpensFullScreenPlayer() {
        render(fixture(MainDestination.HOME))
        rule.onNodeWithTag("full_player").assertDoesNotExist()
        rule.onNodeWithTag("mini_player").performTouchInput {
            swipeUp(startY = bottom - 6f, endY = top - 80f)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("full_player").assertExists()
    }

    @Test fun searchSubviewTransitionAndUnifiedShelfClickWorkWithPressScale() {
        render(fixture(MainDestination.SEARCH).copy(query = "After", songs = songs, hasMore = true))
        rule.onNodeWithTag("song_track-0").performClick()
        rule.waitForIdle()
        assertEquals("track-0", actions.insertedNextSong?.id)
        assertNull(actions.playedList)

        val seeAllLabel = rule.activity.getString(R.string.ft_see_all)
        val backLabel = rule.activity.getString(R.string.ft_back)
        rule.onNodeWithText(seeAllLabel).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(backLabel).assertExists()

        rule.onNodeWithText(backLabel).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("search_play_all").assertExists()
    }

    @Test fun closingSheetIgnoresGhostClicksOnArtworkAndLyricsToggle() {
        var lyricsMode by mutableStateOf(false)
        rule.setContent {
            FlareTuneTheme {
                NowPlayingScreen(
                    state = PlaybackSnapshot(songs[0], songs, 0, playing = true, positionMs = 45_000, durationMs = 180_000),
                    lyricState = LyricState(
                        songId = songs[0].id,
                        status = LyricStatus.READY,
                        lines = listOf(LyricLine("Line one", translation = "第一行", timeSeconds = 1.0)),
                        translationState = LyricTranslationStatus.READY,
                    ),
                    actions = actions,
                    imageLoader = null,
                    onClose = {},
                    onQueue = {},
                    expandProgress = { 0.4f },
                    mobileLyricsMode = lyricsMode,
                    onMobileLyricsModeChange = { lyricsMode = it },
                    sheetVisible = false,
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("player_artwork").performClick()
        rule.onNodeWithTag("player_lyrics_toggle").performClick()
        rule.waitForIdle()
        assertFalse(lyricsMode)

        var translationEnabled = false
        actions.onSetTranslationEnabled = { translationEnabled = it }
        lyricsMode = true
        rule.waitForIdle()
        rule.onNodeWithTag("player_lyrics_top_row").performClick()
        rule.onNodeWithTag("player_translate_button").performClick()
        rule.waitForIdle()
        assertTrue(lyricsMode)
        assertFalse(translationEnabled)
    }

    @Config(qualifiers = "w800dp-h1280dp-mdpi", fontScale = 2.0f)
    @Test fun portraitTabletWith200FontScaleKeepsControlsUnclippedAndNonOverlapping() {
        val state = fixture(MainDestination.HOME).copy(
            playback = PlaybackSnapshot(songs[0], songs, 0, playing = true, positionMs = 45_000, durationMs = 180_000),
            lyricState = LyricState(songId = songs[0].id, status = LyricStatus.READY, lines = listOf(LyricLine("Line one", timeSeconds = 1.0))),
        )
        rule.setContent {
            FlareTuneTheme {
                NowPlayingScreen(
                    state = state.playback,
                    lyricState = state.lyricState,
                    actions = actions,
                    imageLoader = null,
                    onClose = {},
                    onQueue = {},
                )
            }
        }
        rule.waitForIdle()
        val lyricsBounds = rule.onNodeWithTag("player_lyrics_toggle").fetchSemanticsNode().boundsInRoot
        val moreBounds = rule.onNodeWithTag("player_more").fetchSemanticsNode().boundsInRoot
        val queueBounds = rule.onNodeWithTag("open_queue").fetchSemanticsNode().boundsInRoot
        assertTrue(lyricsBounds.width >= 48f && lyricsBounds.height >= 48f)
        assertTrue(lyricsBounds.right <= moreBounds.left + 0.5f)
        assertEquals(moreBounds.center.x, queueBounds.center.x, 1.5f)
    }

    @Test fun unifiedExpandTransitionStripRemainsPopulatedWhileHoldingMidDragNearMiniPlayer() {
        val snap = fixture()
        val progress = mutableFloatStateOf(0.08f)
        val registry = PlayerTransitionRegistry().apply {
            miniBarBounds = androidx.compose.ui.geometry.Rect(0f, 1800f, 1080f, 1992f)
            miniArtworkBounds = androidx.compose.ui.geometry.Rect(36f, 1830f, 168f, 1962f)
            miniBarSurfaceColor = androidx.compose.ui.graphics.Color(0xFFF3F4F7)
        }
        rule.setContent {
            FlareTuneTheme {
                NowPlayingScreen(
                    state = snap.playback,
                    lyricState = snap.lyricState,
                    translationEnabled = false,
                    actions = actions,
                    imageLoader = null,
                    onClose = {},
                    onQueue = {},
                    expandProgress = { progress.floatValue },
                    isWideTransition = false,
                    onCollapseDragStart = {},
                    onCollapseDragDelta = { 0f },
                    onCollapseDragEnd = {},
                    transitionRegistry = registry,
                    sheetVisible = false,
                )
            }
        }
        rule.onNodeWithTag("player_mini_transition_strip").assertExists()
        rule.onNode(
            hasText(songs[0].title) and hasAnyAncestor(hasTestTag("player_mini_transition_strip")),
            useUnmergedTree = true,
        ).assertExists()

        rule.runOnIdle { progress.floatValue = 0.55f }
        rule.onNodeWithTag("player_mini_transition_strip").assertDoesNotExist()
    }

    @Test fun queuePanelCentersCurrentTrackOnOpen() {
        val longQueue = (0 until 24).map { idx ->
            Song(
                id = "center-track-$idx",
                title = "Center Track $idx",
                artist = "FlareTune",
                album = "Echoes",
                durationSeconds = 180.0,
                audioUrl = "https://fixture.invalid/center-$idx.mp3",
                coverUrl = null,
            )
        }
        render(
            fixture().copy(
                playback = PlaybackSnapshot(
                    current = longQueue[12],
                    queue = longQueue,
                    currentIndex = 12,
                    playing = true,
                    positionMs = 20_000L,
                    durationMs = 180_000L,
                ),
            ),
        )
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("open_queue").performScrollTo().performClick()
        rule.waitForIdle()

        val listBounds = rule.onNodeWithTag("queue_list").fetchSemanticsNode().boundsInRoot
        val currentBounds = rule.onNodeWithTag("queue_item_12").fetchSemanticsNode().boundsInRoot
        val viewportCenter = (listBounds.top + listBounds.bottom) / 2f
        val rowHeight = (currentBounds.bottom - currentBounds.top).coerceAtLeast(40f)
        assertTrue(
            "Expected current queue item 12 near viewport center ($viewportCenter), got center ${currentBounds.center.y}",
            kotlin.math.abs(currentBounds.center.y - viewportCenter) <= rowHeight,
        )
    }

    @Test fun queuePanelDragPinsCurrentHighlightAndOptimisticOrderWithoutFlicker() {
        val qSongs = listOf(songs[0], songs[1], songs[2], songs[3])
        var snapshot by mutableStateOf(
            PlaybackSnapshot(
                current = qSongs[0],
                queue = qSongs,
                currentIndex = 0,
                playing = true,
                positionMs = 10_000L,
                durationMs = 180_000L,
            ),
        )
        val playingLabel = "Playing"
        rule.setContent {
            FlareTuneTheme {
                QueuePanel(
                    state = snapshot,
                    loader = null,
                    close = {},
                    onMove = { from, to -> actions.movedQueuePairs += from to to },
                    select = { actions.queueIndex = it },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("queue_item_0").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, playingLabel),
        )

        val firstBounds = rule.onNodeWithTag("queue_item_0").fetchSemanticsNode().boundsInRoot
        val secondBounds = rule.onNodeWithTag("queue_item_1").fetchSemanticsNode().boundsInRoot
        val dragDistance = (secondBounds.center.y - firstBounds.center.y) + 40f
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("queue_drag_0", useUnmergedTree = true).performTouchInput {
                down(center)
                moveBy(Offset(0f, dragDistance / 2f))
                moveBy(Offset(0f, dragDistance / 2f))
                up()
            }
            rule.mainClock.advanceTimeBy(150L)
            assertEquals(listOf(0 to 1), actions.movedQueuePairs)
            // Before player emits async updated queue/currentIndex (and before 650ms fallback),
            // optimistic order and pinned current highlight move to index 1.
            rule.onNodeWithTag("queue_item_0").assertTextContains(qSongs[1].title, substring = true)
            rule.onNodeWithTag("queue_item_1").assertTextContains(qSongs[0].title, substring = true)
            rule.onNodeWithTag("queue_item_1").assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, playingLabel),
            )

            // When player confirms async commit, list reconciles cleanly without resetting order or losing current highlight.
            snapshot = snapshot.copy(
                queue = listOf(qSongs[1], qSongs[0], qSongs[2], qSongs[3]),
                currentIndex = 1,
            )
            rule.mainClock.advanceTimeBy(150L)
        } finally {
            rule.mainClock.autoAdvance = true
        }
        rule.waitForIdle()
        rule.onNodeWithTag("queue_item_0").assertTextContains(qSongs[1].title, substring = true)
        rule.onNodeWithTag("queue_item_1").assertTextContains(qSongs[0].title, substring = true)
        rule.onNodeWithTag("queue_item_1").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, playingLabel),
        )
    }

    @Test fun miniPlayerSwipesToPreviousCardAfterThreeSecondsAndInShuffleMode() {
        val state = mutableStateOf(
            fixture(MainDestination.HOME).copy(
                playback = PlaybackSnapshot(
                    current = songs[1],
                    queue = listOf(songs[0], songs[1], songs[2]),
                    currentIndex = 1,
                    previousQueueIndex = 2,
                    nextQueueIndex = 0,
                    shuffle = true,
                    playing = true,
                    positionMs = 45_000L,
                    durationMs = 180_000L,
                ),
            ),
        )
        rule.setContent {
            FlareTuneTheme {
                ClientApp(state.value, actions, imageLoader = null)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("mini_player_card_current", useUnmergedTree = true).assertIsDisplayed()

        // Drag right at 45s (> 3000ms) in shuffle mode: adjacent shuffle previous card (songs[2]) enters immediately.
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithTag("mini_player").performTouchInput {
                down(center)
                moveBy(Offset(140f, 0f))
            }
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag("mini_player_card_preview_${songs[2].id}", useUnmergedTree = true).assertExists()
            rule.onNodeWithTag("mini_player").performTouchInput {
                moveBy(Offset(120f, 0f))
                up()
            }
        } finally {
            rule.mainClock.autoAdvance = true
        }
        rule.waitForIdle()
        assertEquals(1, actions.previousCalls)
    }

    @Test fun continueListeningTileScalesCleanlyAcrossCompactAndExpandedWidths() {
        rule.setContent {
            FlareTuneTheme {
                androidx.compose.foundation.layout.Row {
                    ContinueListeningGridTile(
                        title = songs[0].title,
                        showChevron = true,
                        highlighted = false,
                        size = 96.dp,
                        onClick = {},
                    ) { Box(Modifier.size(96.dp)) }
                    ContinueListeningGridTile(
                        title = songs[1].title,
                        showChevron = true,
                        highlighted = true,
                        size = 196.dp,
                        onClick = {},
                    ) { Box(Modifier.size(196.dp)) }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(songs[0].title).assertIsDisplayed()
        rule.onNodeWithText(songs[1].title).assertIsDisplayed()
    }

    private fun assertMiniPlayerCardTextNotClippedAtDoubleFontScale() {
        rule.waitForIdle()
        val cardNode = rule.onNodeWithTag("mini_player_card_current", useUnmergedTree = true)
        cardNode.assertIsDisplayed()
        val cardBounds = cardNode.fetchSemanticsNode().boundsInRoot

        val titleNode = rule.onNode(
            hasText(songs[0].title) and hasAnyAncestor(hasTestTag("mini_player_card_current")),
            useUnmergedTree = true,
        )
        val artistNode = rule.onNode(
            hasText(songs[0].artist) and hasAnyAncestor(hasTestTag("mini_player_card_current")),
            useUnmergedTree = true,
        )
        titleNode.assertIsDisplayed()
        artistNode.assertIsDisplayed()

        val titleBounds = titleNode.fetchSemanticsNode().boundsInRoot
        val artistBounds = artistNode.fetchSemanticsNode().boundsInRoot
        assertTrue("Expected positive title height", titleBounds.height > 0f)
        assertTrue("Expected positive artist height", artistBounds.height > 0f)
        assertTrue("Title top clipped (${titleBounds.top} < ${cardBounds.top})", titleBounds.top >= cardBounds.top - 0.5f)
        assertTrue("Artist bottom clipped (${artistBounds.bottom} > ${cardBounds.bottom})", artistBounds.bottom <= cardBounds.bottom + 0.5f)
        assertTrue("Title overlaps artist vertically", titleBounds.bottom <= artistBounds.top + 0.5f)

        val titleLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        titleNode.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(titleLayouts)
        assertTrue(titleLayouts.isNotEmpty())
        assertFalse("Title overflowed vertically at 2x font scale", titleLayouts.first().didOverflowHeight)

        val artistLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        artistNode.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(artistLayouts)
        assertTrue(artistLayouts.isNotEmpty())
        assertFalse("Artist overflowed vertically at 2x font scale", artistLayouts.first().didOverflowHeight)
    }

    @Test fun miniPlayerFitsDualLineCardTextWithoutVerticalClipAtDoubleFontScaleCompact() {
        render(fixture(), largeText = true)
        assertMiniPlayerCardTextNotClippedAtDoubleFontScale()
    }

    @Config(qualifiers = "w1024dp-h720dp-mdpi")
    @Test fun miniPlayerFitsDualLineCardTextWithoutVerticalClipAtDoubleFontScaleWide() {
        render(fixture(), largeText = true)
        assertMiniPlayerCardTextNotClippedAtDoubleFontScale()
    }

    @Test fun queuePanelClampsBoundaryCenteringAndReCentersOnReopenAfterTrackChange() {
        val longQueue = List(24) { idx ->
            Song("q-$idx", "Queue Track $idx", "Artist $idx", "Album $idx", 180.0, "https://fixture.invalid/$idx.mp3")
        }
        var queueOpen by mutableStateOf(true)
        var currentIndex by mutableIntStateOf(0)
        rule.setContent {
            FlareTuneTheme {
                if (queueOpen) {
                    QueuePanel(
                        state = PlaybackSnapshot(
                            current = longQueue[currentIndex],
                            queue = longQueue,
                            currentIndex = currentIndex,
                            playing = true,
                            positionMs = 10_000L,
                            durationMs = 180_000L,
                        ),
                        loader = null,
                        close = { queueOpen = false },
                        select = {},
                    )
                }
            }
        }
        rule.waitForIdle()

        val listBoundsTop = rule.onNodeWithTag("queue_list").fetchSemanticsNode().boundsInRoot
        val firstBounds = rule.onNodeWithTag("queue_item_0").fetchSemanticsNode().boundsInRoot
        assertTrue("First item should anchor at list top without blank gap", firstBounds.top <= listBoundsTop.top + 8f)
        rule.onNodeWithTag("queue_item_0").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Playing"),
        )

        // Close, switch to last track, reopen -> clamped cleanly at bottom without trailing whitespace gap.
        queueOpen = false
        rule.waitForIdle()
        currentIndex = longQueue.lastIndex
        queueOpen = true
        rule.waitForIdle()

        val listBoundsBottom = rule.onNodeWithTag("queue_list").fetchSemanticsNode().boundsInRoot
        val lastTag = "queue_item_${longQueue.lastIndex}"
        rule.onNodeWithTag(lastTag).assertIsDisplayed()
        val lastBounds = rule.onNodeWithTag(lastTag).fetchSemanticsNode().boundsInRoot
        val bottomPadPx = with(rule.density) { 18.dp.toPx() }
        assertTrue(
            "Last item should clamp near list bottom without trailing blank gap (${lastBounds.bottom} vs ${listBoundsBottom.bottom})",
            lastBounds.bottom >= listBoundsBottom.bottom - bottomPadPx,
        )
        rule.onNodeWithTag(lastTag).assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Playing"),
        )

        // Close, switch to middle track, reopen -> re-centers around middle track.
        queueOpen = false
        rule.waitForIdle()
        currentIndex = 12
        queueOpen = true
        rule.waitForIdle()

        val listBoundsMid = rule.onNodeWithTag("queue_list").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("queue_item_12").assertIsDisplayed()
        val midBounds = rule.onNodeWithTag("queue_item_12").fetchSemanticsNode().boundsInRoot
        val listCenterY = (listBoundsMid.top + listBoundsMid.bottom) / 2f
        val midCenterY = (midBounds.top + midBounds.bottom) / 2f
        assertTrue(
            "Middle item should re-center near viewport midpoint ($midCenterY vs $listCenterY)",
            kotlin.math.abs(midCenterY - listCenterY) <= midBounds.height,
        )
        rule.onNodeWithTag("queue_item_0").assertDoesNotExist()
        rule.onNodeWithTag("queue_item_12").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Playing"),
        )
    }

    @Test fun queuePanelRoundTripDragInsideCommitWindowRetainsOptimisticOrderWithoutPrematureReset() {
        val dupSongs = listOf(songs[0], songs[1], songs[2], songs[0])
        val movedPairs = mutableListOf<Pair<Int, Int>>()
        var snapshot by mutableStateOf(
            PlaybackSnapshot(
                current = dupSongs[0],
                queue = dupSongs,
                currentIndex = 0,
                playing = true,
                positionMs = 10_000L,
                durationMs = 180_000L,
            ),
        )
        rule.setContent {
            FlareTuneTheme {
                QueuePanel(
                    state = snapshot,
                    loader = null,
                    close = {},
                    onMove = { from, to -> movedPairs += from to to },
                    select = {},
                )
            }
        }
        rule.waitForIdle()

        val row0Bounds = rule.onNodeWithTag("queue_item_0").fetchSemanticsNode().boundsInRoot
        val row1Bounds = rule.onNodeWithTag("queue_item_1").fetchSemanticsNode().boundsInRoot
        val dragStep = (row1Bounds.center.y - row0Bounds.center.y) + 40f

        rule.mainClock.autoAdvance = false
        try {
            // Drag 0 -> 1 (moves current duplicate songs[0] instance to index 1).
            rule.onNodeWithTag("queue_drag_0", useUnmergedTree = true).performTouchInput {
                down(center)
                moveBy(Offset(0f, dragStep / 2f))
                moveBy(Offset(0f, dragStep / 2f))
                up()
            }
            rule.mainClock.advanceTimeBy(100L)
            assertEquals(listOf(0 to 1), movedPairs)
            rule.onNodeWithTag("queue_item_0").assertTextContains(dupSongs[1].title, substring = true)
            rule.onNodeWithTag("queue_item_1").assertTextContains(dupSongs[0].title, substring = true)
            rule.onNodeWithTag("queue_item_1").assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Playing"),
            )
            // The second duplicate instance at index 3 must NOT steal Playing highlight.
            rule.onNodeWithTag("queue_item_3").assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Playing").not(),
            )

            // Allow row placement spring to settle while remaining within the 650ms commit window.
            rule.mainClock.advanceTimeBy(260L)

            // Rapidly drag 1 -> 0 back inside the 650ms commit window while player snapshot is still stale [0, 1, 2, 0].
            rule.onNodeWithTag("queue_drag_1", useUnmergedTree = true).performTouchInput {
                down(center)
                repeat(12) { moveBy(Offset(0f, -dragStep / 12f)) }
                up()
            }
            rule.mainClock.advanceTimeBy(100L)
            assertEquals(listOf(0 to 1, 1 to 0), movedPairs)
            rule.onNodeWithTag("queue_item_0").assertTextContains(dupSongs[0].title, substring = true)
            rule.onNodeWithTag("queue_item_0").assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Playing"),
            )

            // Delayed intermediate snapshot [1, 0, 2, 0] from move #1 arrives: must NOT revert optimistic [0, 1, 2, 0].
            snapshot = snapshot.copy(
                current = dupSongs[0],
                queue = listOf(dupSongs[1], dupSongs[0], dupSongs[2], dupSongs[0]),
                currentIndex = 1,
            )
            rule.mainClock.advanceTimeBy(120L)
            rule.onNodeWithTag("queue_item_0").assertTextContains(dupSongs[0].title, substring = true)
            rule.onNodeWithTag("queue_item_1").assertTextContains(dupSongs[1].title, substring = true)
            rule.onNodeWithTag("queue_item_0").assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Playing"),
            )

            // Final confirmation snapshot [0, 1, 2, 0] arrives and settles cleanly.
            snapshot = snapshot.copy(
                current = dupSongs[0],
                queue = dupSongs,
                currentIndex = 0,
            )
        } finally {
            rule.mainClock.autoAdvance = true
        }
        rule.waitForIdle()
        rule.onNodeWithTag("queue_item_0").assertTextContains(dupSongs[0].title, substring = true)
        rule.onNodeWithTag("queue_item_1").assertTextContains(dupSongs[1].title, substring = true)
        rule.onNodeWithTag("queue_item_0").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Playing"),
        )
    }

    @Test
    @Config(qualifiers = "zh-rCN-w390dp-h844dp-mdpi")
    fun compactDragDownClosesPlayerWithoutBlockingBackgroundOrBlankingMiniPlayer() {
        render(fixture(MainDestination.HOME))
        rule.onNodeWithTag("mini_player").assertIsDisplayed()
        // Open from mini player after prewarmed composition (sheetVisible was false initially).
        rule.onNodeWithTag("mini_player").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("player_mode").assertIsDisplayed()

        // Drag down anywhere on the player body (sheetNestedScroll) to close.
        rule.onNodeWithTag("player_scroll").performTouchInput {
            swipeDown(startY = centerY, endY = bottom - 12f)
        }
        rule.waitForIdle()

        // Sheet should be fully closed: player_mode unmounted, mini player info & controls visible, background clickable.
        rule.onNodeWithTag("player_mode").assertDoesNotExist()
        rule.onNodeWithTag("mini_player").assertIsDisplayed()
        rule.onAllNodesWithText(songs[0].title).onFirst().assertIsDisplayed()
        rule.onNodeWithTag("mini_play").performClick()
        rule.runOnIdle { assertEquals(1, actions.toggles) }
        rule.onNodeWithTag("nav_discover").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("discover_page").assertIsDisplayed()

        // Reopen and drag down via ClassicDragHandleHeader (player_close) as well.
        rule.onNodeWithTag("mini_player").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("player_mode").assertIsDisplayed()
        rule.onNodeWithTag("player_close", useUnmergedTree = true).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 520f)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("player_mode").assertDoesNotExist()
        rule.onNodeWithTag("mini_play").performClick()
        rule.runOnIdle { assertEquals(2, actions.toggles) }
        rule.onNodeWithTag("nav_home").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_page").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w390dp-h844dp-mdpi")
    fun prewarmedTransitionStripAppearsWhenSheetBecomesVisibleAtZeroProgress() {
        val snap = fixture()
        var sheetVisible by mutableStateOf(false)
        val progress = mutableFloatStateOf(0f)
        val registry = PlayerTransitionRegistry().apply {
            miniBarBounds = androidx.compose.ui.geometry.Rect(0f, 1800f, 1080f, 1992f)
            miniArtworkBounds = androidx.compose.ui.geometry.Rect(36f, 1830f, 168f, 1962f)
            miniBarSurfaceColor = androidx.compose.ui.graphics.Color(0xFFF3F4F7)
        }
        rule.setContent {
            FlareTuneTheme {
                NowPlayingScreen(
                    state = snap.playback,
                    lyricState = snap.lyricState,
                    translationEnabled = false,
                    actions = actions,
                    imageLoader = null,
                    onClose = {},
                    onQueue = {},
                    expandProgress = { progress.floatValue },
                    isWideTransition = false,
                    onCollapseDragStart = {},
                    onCollapseDragDelta = { 0f },
                    onCollapseDragEnd = {},
                    transitionRegistry = registry,
                    sheetVisible = sheetVisible,
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("player_mini_transition_strip").assertDoesNotExist()

        sheetVisible = true
        rule.waitForIdle()
        rule.onNodeWithTag("player_mini_transition_strip").assertExists()
    }
}

private class RecordingActions : ClientActions {
    var credentials: List<String>? = null
    var song: Song? = null
    var insertedNextSong: Song? = null
    var playedList: Pair<List<Song>, Int>? = null
    var album: Album? = null
    var refreshes = 0
    var songSearchRetries = 0
    var query = ""
    var toggles = 0
    var nexts = 0
    var previousCalls = 0
    var seekPosition = -1L
    var lastVolume = -1f
    var queueIndex = -1
    val removedQueueIndices = mutableListOf<Int>()
    val movedQueuePairs = mutableListOf<Pair<Int, Int>>()
    var lastTranslationEnabled: Boolean? = null
    var lyricTranslationRequests = 0
    var passwordChanges = 0
    var tab = LibraryTab.CATALOG
    var destination = MainDestination.HOME
    var onNavigate: (MainDestination) -> Unit = {}
    var onTab: (LibraryTab) -> Unit = {}
    var onAlbum: (Album) -> Unit = {}
    var onCloseAlbum: () -> Unit = {}
    var onVolume: (Float) -> Unit = {}
    var onCheckInstance: (String) -> Unit = {}
    var onSetTranslationEnabled: (Boolean) -> Unit = {}
    var discoverLanguageSelected: String? = null
    override fun navigate(destination: MainDestination) { this.destination = destination; onNavigate(destination) }
    override fun selectDiscoverLanguage(language: String) { discoverLanguageSelected = language }
    override fun loadMoreAlbums() {}
    override fun openAlbum(album: Album) { this.album = album; onAlbum(album) }
    override fun closeAlbum() { onCloseAlbum() }
    override fun setVolume(volume: Float) { this.lastVolume = volume; onVolume(volume) }
    override fun checkInstance(instanceUrl: String) { onCheckInstance(instanceUrl) }
    override fun login(instanceUrl: String, username: String, password: String) { credentials = listOf(instanceUrl, username, password) }
    override fun changePassword(currentPassword: String, newPassword: String) { passwordChanges++ }
    override fun logout() {}
    override fun search(query: String) { this.query = query }
    override fun selectTab(tab: LibraryTab) { this.tab = tab; onTab(tab) }
    override fun refresh() { refreshes++ }
    override fun retrySearchSongs() { songSearchRetries++ }
    override fun loadMore() {}
    override fun playSong(song: Song) { this.song = song }
    override fun playSingleAsNext(song: Song, fromSearch: Boolean) { this.insertedNextSong = song }
    override fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean) {
        this.playedList = songs to startIndex
        songs.getOrNull(startIndex)?.let { this.song = it }
    }
    override fun togglePlay() { toggles++ }
    override fun next() { nexts++ }
    override fun previous() { previousCalls++ }
    override fun seekTo(positionMs: Long) { seekPosition = positionMs }
    override fun toggleShuffle() {}
    override fun cycleRepeat() {}
    override fun selectQueueItem(index: Int) { queueIndex = index }
    override fun retryPlayback() {}
    override fun removeQueueItem(index: Int) { removedQueueIndices += index }
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) { movedQueuePairs += fromIndex to toIndex }
    override fun setTranslationEnabled(enabled: Boolean) { lastTranslationEnabled = enabled; onSetTranslationEnabled(enabled) }
    override fun requestLyricTranslation() { lyricTranslationRequests++ }
}
