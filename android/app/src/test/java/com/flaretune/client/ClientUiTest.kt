package com.flaretune.client

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
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
        rule.onNodeWithTag("library_refresh").performClick()
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
        rule.onNodeWithTag("album_album0").performScrollTo().performClick()
        rule.onNodeWithTag("album_detail").assertExists()
        rule.onNodeWithTag("album_play").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals("album0", actions.album?.id)
            assertEquals("track-0", actions.song?.id)
        }
        screenshot("v2-album")
        rule.onNodeWithTag("album_back").assertIsDisplayed().performClick()
        rule.onNodeWithTag("home_page").assertExists()
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
    }

    @Test fun emptyFailureHasAnActualRetryAction() {
        render(fixture(MainDestination.SEARCH).copy(songs = emptyList(), notice = ClientNotice.NETWORK))
        rule.onNodeWithTag("catalog_retry").performClick()
        rule.runOnIdle { assertEquals(1, actions.refreshes) }
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
        render(fixture())
        rule.onNodeWithTag("dock_compact").assertExists()
        rule.onNodeWithTag("album_album0").assertExists()
        rule.onNodeWithTag("nav_home").assertIsSelected()
        screenshot("v2-home-compact")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w1280dp-h800dp-night-mdpi")
    fun wideHomePlacesNavigationAndMiniPlayerSideBySide() {
        render(fixture())
        rule.onNodeWithTag("dock_wide").assertExists()
        val nav = rule.onNodeWithTag("nav_library").fetchSemanticsNode().boundsInRoot
        val mini = rule.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
        assertTrue("Wide dock overlaps", nav.right <= mini.left)
        screenshot("v2-home-wide")
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_mode").assertIsDisplayed()
        screenshot("v2-player-wide")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w360dp-h640dp-night-mdpi")
    fun chineseDarkLibrarySupportsLargeTextAndVisibleControls() {
        render(fixture(MainDestination.LIBRARY), largeText = true)
        rule.onNodeWithTag("library_tab_favorites").performClick()
        rule.runOnIdle { assertEquals(LibraryTab.FAVORITES, actions.tab) }
        screenshot("v2-library-large-zh")
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_mode").performScrollTo().assertIsDisplayed()
        screenshot("v2-player-large-zh")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h760dp-night-mdpi")
    fun narrowFloatingWindowKeepsAllThreeNavigationDestinationsReachable() {
        render(fixture())
        rule.onNodeWithTag("dock_compact").assertExists()
        rule.onNodeWithTag("nav_search").performClick()
        rule.onNodeWithTag("search_field").assertIsDisplayed()
        rule.onNodeWithTag("nav_library").performClick()
        rule.onNodeWithTag("library_tab_favorites").performClick()
        rule.runOnIdle { assertEquals(LibraryTab.FAVORITES, actions.tab) }
        rule.onNodeWithTag("library_refresh").assertIsEnabled()
        screenshot("v2-floating-library")
    }


    @Test fun androidBackClosesThePlayerBeforeTheUnderlyingAlbum() {
        render(fixture())
        rule.onNodeWithTag("album_album0").performClick()
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("full_player").assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("full_player").assertDoesNotExist()
        rule.onNodeWithTag("album_detail").assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("home_page").assertExists()
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

        // Open full player, toggle display via shared action without triggering generation.
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_lyrics_toggle").performScrollTo().performClick()
        rule.onAllNodesWithText("你好世界").assertCountEquals(0)
        rule.onNodeWithTag("player_translate_button").performClick()
        rule.runOnIdle {
            assertEquals(true, actions.lastTranslationEnabled)
            assertEquals(0, actions.lyricTranslationRequests)
        }
        rule.onNodeWithText("你好世界").assertIsDisplayed()

        // Generate button invokes requestLyricTranslation independently.
        rule.onNodeWithTag("player_generate_translation").performClick()
        rule.runOnIdle { assertEquals(1, actions.lyricTranslationRequests) }

        // Close full player; mini player now reflects translationEnabled == true.
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
}

private class RecordingActions : ClientActions {
    var credentials: List<String>? = null
    var song: Song? = null
    var album: Album? = null
    var refreshes = 0
    var query = ""
    var toggles = 0
    var nexts = 0
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
    var onSetTranslationEnabled: (Boolean) -> Unit = {}
    override fun navigate(destination: MainDestination) { this.destination = destination; onNavigate(destination) }
    override fun loadMoreAlbums() {}
    override fun openAlbum(album: Album) { this.album = album; onAlbum(album) }
    override fun closeAlbum() { onCloseAlbum() }
    override fun setVolume(volume: Float) { this.lastVolume = volume; onVolume(volume) }
    override fun login(instanceUrl: String, username: String, password: String) { credentials = listOf(instanceUrl, username, password) }
    override fun changePassword(currentPassword: String, newPassword: String) { passwordChanges++ }
    override fun logout() {}
    override fun search(query: String) { this.query = query }
    override fun selectTab(tab: LibraryTab) { this.tab = tab; onTab(tab) }
    override fun refresh() { refreshes++ }
    override fun loadMore() {}
    override fun playSong(song: Song) { this.song = song }
    override fun togglePlay() { toggles++ }
    override fun next() { nexts++ }
    override fun previous() {}
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
