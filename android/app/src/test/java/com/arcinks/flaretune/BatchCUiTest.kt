package com.arcinks.flaretune

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.arcinks.flaretune.model.*
import com.arcinks.flaretune.ui.*
import com.arcinks.flaretune.ui.theme.FlareTuneTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchCUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val songs = listOf(
        Song("s1", "Zebra Rain", "Bob", "Blue Hour", 120.0, "https://fixture.invalid/1.mp3", "https://fixture.invalid/art/0", language = "zh"),
        Song("s2", "Apple Glow", "Alice", "Blue Hour", 300.0, "https://fixture.invalid/2.mp3", "https://fixture.invalid/art/1", language = "en"),
        Song("s3", "Mango Mist", "Charlie", "Blue Hour", 200.0, "https://fixture.invalid/3.mp3", "https://fixture.invalid/art/2", language = ""),
        Song("s4", "Offline Track", "Bob", "Blue Hour", 150.0, "", language = "zh"),
    )
    private val favPlaylist = PlaylistSummary("fav-1", "Favorites", "favorite", songCount = 1, revision = 1, createdAt = 100, updatedAt = 100)
    private val commute = PlaylistSummary("p-commute", "Commute", "regular", "Daily mix", songCount = 4, revision = 5, customCoverUrl = "https://fixture.invalid/art/custom.webp", createdAt = 200, updatedAt = 400)
    private val alpha = PlaylistSummary("p-alpha", "Alpha", "regular", "Chill", songCount = 2, revision = 2, createdAt = 300, updatedAt = 200)
    private val artist = Artist("The Slow Hours", songCount = 4, albumCount = 2, coverUrl = "https://fixture.invalid/art/0")
    private val album = Album("alb-1", "Blue Hour", "The Slow Hours", "https://fixture.invalid/art/0", 3)

    private lateinit var actions: BatchCTestActions

    private fun baseState(destination: MainDestination = MainDestination.LIBRARY) = ClientUiState(
        screen = ClientScreen.LIBRARY,
        instanceUrl = "https://music.example",
        username = "listener",
        accountName = "Listener",
        destination = destination,
        songs = songs.filter { it.canPlay },
        homeLibrarySongs = listOf(songs[2], songs[0], songs[1]),
        albums = listOf(album),
        searchAlbums = listOf(album),
        artists = listOf(artist),
        playlists = listOf(favPlaylist, commute, alpha),
        playlistShelf = PlaylistShelf(1, items = listOf(ShelfItem(favPlaylist.id), ShelfItem(commute.id), ShelfItem(alpha.id))),
        favoriteSongIds = setOf("s1"),
        favoriteSongs = listOf(songs[0]),
        playback = PlaybackSnapshot(songs[0], listOf(songs[0], songs[1]), 0, true, positionMs = 20_000, durationMs = 120_000),
    )

    private fun render(initial: ClientUiState = baseState(), fontScale: Float? = null) {
        actions = BatchCTestActions(mutableStateOf(initial), songs)
        val resources = rule.activity.resources
        val loader = ImageLoader.Builder(rule.activity).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                    return SuccessResult(android.graphics.drawable.BitmapDrawable(resources, bitmap), chain.request, DataSource.MEMORY)
                }
            })
        }.build()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                FlareTuneTheme { ClientApp(actions.state.value, actions, loader) }
            }
        }
    }

    @Test fun homeLibraryUsesIndependentHomeMixAndPreservesNextVsPlayAll() {
        val homeOnlySong = Song("home-untagged", "Untagged Latest", "Indie", "Single", 190.0, "https://fixture.invalid/u.mp3")
        render(
            baseState(MainDestination.HOME).copy(
                homeLibrarySongs = listOf(homeOnlySong, songs[0]),
                songs = listOf(songs[1]),
            ),
        )
        rule.onNodeWithTag("home_feed").performScrollToNode(hasTestTag("home_library_play_all"))
        rule.onNodeWithTag("home_song_home-untagged").performClick()
        rule.runOnIdle {
            assertEquals("home-untagged", actions.insertedNext?.id)
            assertNull(actions.playedList)
        }
        rule.onNodeWithTag("home_library_play_all").performClick()
        rule.runOnIdle {
            assertEquals(listOf("home-untagged", "s1"), actions.playedList?.first?.map { it.id })
            assertEquals(0, actions.playedList?.second)
        }
    }

    @Test fun librarySortDropdownAndQuickShelfSortRespectDraftAndPersistence() {
        render(baseState(MainDestination.LIBRARY))
        rule.onNodeWithTag("library_header_pills").performScrollToNode(hasTestTag("library_sort_button"))
        rule.onNodeWithTag("library_sort_button").performClick()
        rule.onNodeWithTag("library_sort_option_TRACK_COUNT").performClick()
        rule.runOnIdle { assertEquals(PlaylistSortMode.TRACK_COUNT, actions.state.value.playlistSortMode) }

        rule.onNodeWithTag("library_header_pills").performScrollToNode(hasTestTag("edit_shelf"))
        rule.onNodeWithTag("edit_shelf").performClick()
        rule.onNodeWithTag("shelf_order_save").assertIsNotEnabled()
        rule.onNodeWithTag("shelf_quick_name").performScrollTo().performClick()
        rule.runOnIdle { assertNull(actions.shelfSaved) }
        rule.onNodeWithTag("shelf_order_save").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(listOf("p-alpha", "p-commute", "fav-1"), actions.shelfSaved) }
    }

    @Test fun playlistSongSortPlaysCurrentViewOrderAndOrderSheetQuickSortStaysDraftUntilSaved() {
        val entries = listOf(
            PlaylistSong(songs[0], 0), // Zebra, Bob, 120s
            PlaylistSong(songs[1], 1), // Apple, Alice, 300s
            PlaylistSong(songs[2], 2), // Mango, Charlie, 200s
        )
        render(
            baseState(MainDestination.LIBRARY).copy(
                selectedPlaylist = commute,
                selectedPlaylistEntries = entries,
                playlistSongs = entries.map { it.song },
            ),
        )
        rule.onNodeWithTag("playlist_sort_button").performScrollTo().performClick()
        rule.onNodeWithTag("playlist_sort_option_TITLE").performClick()
        rule.onNodeWithTag("playlist_play").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(PlaylistSongSortMode.TITLE, actions.state.value.playlistSongSort)
            assertEquals(listOf("s2", "s3", "s1"), actions.playedList?.first?.map { it.id })
        }

        rule.onNodeWithTag("playlist_edit_order").performScrollTo().performClick()
        rule.onNodeWithTag("playlist_order_save").assertIsNotEnabled()
        rule.onNodeWithTag("playlist_order_quick_duration").performScrollTo().performClick()
        rule.runOnIdle { assertNull(actions.orderSaved) }
        rule.onNodeWithTag("playlist_order_save").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(listOf("s2", "s3", "s1"), actions.orderSaved) }
    }

    @Test fun playlistMultiSelectLongPressBackHandlerAndRealFeedbackRules() {
        val entries = songs.mapIndexed { i, s -> PlaylistSong(s, i) }
        render(
            baseState(MainDestination.LIBRARY).copy(
                selectedPlaylist = commute,
                selectedPlaylistEntries = entries,
                playlistSongs = entries.map { it.song }.filter { it.canPlay },
            ),
        )
        // Long press song row enters multi-select with s2 selected.
        rule.onNodeWithTag("playlist_detail_tracks").performScrollToNode(hasTestTag("playlist_song_s2"))
        rule.onNodeWithTag("playlist_song_s2").performTouchInput { longClick() }
        rule.onNodeWithTag("playlist_multiselect_bar").assertIsDisplayed()
        rule.onNodeWithTag("batch_song_action_bar").assertIsDisplayed()
        rule.onNodeWithTag("batch_action_remove").assertIsDisplayed()

        // System back exits multi-select WITHOUT closing the playlist detail.
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_multiselect_bar").assertDoesNotExist()
        rule.runOnIdle { assertEquals("p-commute", actions.state.value.selectedPlaylist?.id) }

        // Enter via button, select all, test batch favorite failure keeps selection until real success.
        rule.onNodeWithTag("playlist_multiselect").performScrollTo().performClick()
        rule.onNodeWithTag("playlist_multiselect_select_all").performClick()
        actions.failNextFavoriteBatch = true
        rule.onNodeWithTag("batch_action_favorite").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_multiselect_bar").assertIsDisplayed()

        rule.onNodeWithTag("batch_action_favorite").performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(listOf("s1", "s2", "s3", "s4"), actions.batchFavorited) }
        rule.onNodeWithTag("playlist_multiselect_bar").assertDoesNotExist()

        // [Minor-2]: Unplayable track s4 has short-click disabled when not multi-selecting,
        // yet long-press still enters multi-select mode with s4 selected.
        rule.onNodeWithTag("playlist_detail_tracks").performScrollToNode(hasTestTag("playlist_song_s4"))
        rule.onNodeWithTag("playlist_detail_tracks").performTouchInput {
            swipeUp(startY = centerY, endY = top)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_song_s4").assertIsNotEnabled()
        rule.onNodeWithTag("playlist_song_s4").performTouchInput { longClick() }
        rule.onNodeWithTag("playlist_multiselect_bar").assertIsDisplayed()
        rule.onNodeWithTag("batch_song_action_bar").assertIsDisplayed()

        // [Minor-3]: Canceling add-to-playlist sheet clears pending callback without spurious exit.
        rule.onNodeWithTag("batch_action_playlist").performClick()
        rule.onNodeWithTag("playlist_target_sheet").assertIsDisplayed()
        rule.onNodeWithTag("playlist_target_cancel").performClick()
        rule.onNodeWithTag("playlist_target_sheet").assertDoesNotExist()
        rule.runOnIdle {
            actions.state.value = actions.state.value.copy(libraryMutationVersion = actions.state.value.libraryMutationVersion + 1)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_multiselect_bar").assertIsDisplayed()
        rule.onNodeWithTag("batch_action_favorite").performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(listOf("s4"), actions.batchFavorited) }
        rule.onNodeWithTag("playlist_multiselect_bar").assertDoesNotExist()

        // Favorite playlist must NOT show remove-from-playlist button.
        rule.runOnIdle {
            actions.state.value = actions.state.value.copy(selectedPlaylist = favPlaylist)
        }
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_multiselect").performScrollTo().performClick()
        rule.onNodeWithTag("batch_action_remove").assertDoesNotExist()
    }

    @Test fun batchFavoriteFailureShowsErrorAndKeepsSelectionAcrossSearchAllSongsAndAlbum() {
        // 1. Search overview batch favorite failure
        render(baseState(MainDestination.SEARCH).copy(query = "hour"))
        rule.onNodeWithTag("catalog_list").performScrollToNode(hasTestTag("search_overview_multiselect"))
        rule.onNodeWithTag("search_overview_multiselect").performClick()
        rule.onNodeWithTag("search_multiselect_select_all").performClick()
        actions.failNextFavoriteBatch = true
        rule.onNodeWithTag("batch_action_favorite").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("action_feedback_toast").assertIsDisplayed()
        rule.onNodeWithText("The request could not be completed. Try again.").assertIsDisplayed()
        rule.onAllNodesWithText("Added to favorites").assertCountEquals(0)
        rule.runOnIdle { assertNull(actions.state.value.libraryMutationNotice) }
        rule.onNodeWithTag("search_multiselect_bar").assertIsDisplayed()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        // 2. All Songs batch favorite failure
        rule.onNodeWithTag("nav_discover").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("discover_feed").performScrollToNode(hasTestTag("discover_all_songs"))
        rule.onNodeWithTag("discover_all_songs").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("all_songs_multiselect").performClick()
        rule.onNodeWithTag("all_songs_multiselect_select_all").performClick()
        actions.failNextFavoriteBatch = true
        rule.onNodeWithTag("batch_action_favorite").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("action_feedback_toast").assertIsDisplayed()
        rule.onNodeWithText("The request could not be completed. Try again.").assertIsDisplayed()
        rule.onAllNodesWithText("Added to favorites").assertCountEquals(0)
        rule.runOnIdle { assertNull(actions.state.value.libraryMutationNotice) }
        rule.onNodeWithTag("all_songs_multiselect_bar").assertIsDisplayed()

        // 3. Album detail batch favorite failure
        rule.runOnIdle {
            actions.state.value = actions.state.value.copy(
                selectedAlbum = album,
                albumSongs = songs.filter { it.canPlay },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("album_multiselect").performScrollTo().performClick()
        rule.onNodeWithTag("album_multiselect_select_all").performClick()
        actions.failNextFavoriteBatch = true
        rule.onNodeWithTag("batch_action_favorite").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("action_feedback_toast").assertIsDisplayed()
        rule.onNodeWithText("The request could not be completed. Try again.").assertIsDisplayed()
        rule.onAllNodesWithText("Added to favorites").assertCountEquals(0)
        rule.runOnIdle { assertNull(actions.state.value.libraryMutationNotice) }
        rule.onNodeWithTag("album_multiselect_bar").assertIsDisplayed()
    }

    @Test fun searchAllSongsAndAlbumMultiSelectWithQuickActionsAndBackPriority() {
        // 1. Search overview & songs subview multi-select
        render(baseState(MainDestination.SEARCH).copy(query = "hour"))
        rule.onNodeWithTag("catalog_list").performScrollToNode(hasTestTag("search_overview_multiselect"))
        rule.onNodeWithTag("search_overview_multiselect").performClick()
        rule.onNodeWithTag("search_multiselect_bar").assertIsDisplayed()
        rule.onNodeWithTag("search_multiselect_select_all").performClick()
        rule.onNodeWithTag("batch_action_play_next").performClick()
        rule.runOnIdle { assertEquals(listOf("s1", "s2", "s3"), actions.playedNextBatch) }
        rule.onNodeWithTag("search_multiselect_bar").assertDoesNotExist()

        // 2. Discover -> All Songs multi-select
        rule.onNodeWithTag("nav_discover").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("discover_feed").performScrollToNode(hasTestTag("discover_all_songs"))
        rule.onNodeWithTag("discover_all_songs").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("all_songs_multiselect").performClick()
        rule.onNodeWithTag("all_songs_multiselect_bar").assertIsDisplayed()
        rule.onNodeWithTag("all_songs_multiselect_select_all").performClick()
        rule.onNodeWithTag("batch_action_enqueue").performClick()
        rule.runOnIdle { assertEquals(listOf("s1", "s2", "s3"), actions.enqueued) }
        rule.onNodeWithTag("all_songs_multiselect_bar").assertDoesNotExist()

        // 3. Album detail quick actions + multi-select + back priority
        rule.runOnIdle {
            actions.state.value = actions.state.value.copy(
                selectedAlbum = album,
                albumSongs = songs.filter { it.canPlay },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("album_shuffle").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("s1", "s2", "s3"), actions.playedList?.first?.map { it.id }) }
        rule.onNodeWithTag("album_enqueue").performScrollTo().assertExists()
        rule.onNodeWithTag("album_add_to_playlist").performScrollTo().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("album_multiselect").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("album_multiselect_bar").assertIsDisplayed()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("album_multiselect_bar").assertDoesNotExist()
        rule.runOnIdle { assertEquals("alb-1", actions.state.value.selectedAlbum?.id) }
    }

    @Test fun playlistActionsSheetUpgradeAndArtistCountsHiddenAcrossCardsAndDetailPage() {
        render(baseState(MainDestination.LIBRARY))
        rule.onNodeWithTag("playlist_more_p-commute").performClick()
        rule.onNodeWithTag("playlist_actions_sheet").assertIsDisplayed()
        rule.onNodeWithTag("playlist_menu_play").assertIsDisplayed()
        rule.onNodeWithTag("playlist_menu_shuffle").assertIsDisplayed()
        rule.onNodeWithTag("playlist_menu_enqueue").assertIsDisplayed()
        rule.onNodeWithTag("playlist_menu_add").assertExists()
        rule.onNodeWithTag("playlist_menu_multiselect").performScrollTo().assertExists()
        rule.onNodeWithTag("playlist_menu_order").performScrollTo().assertExists()
        rule.onNodeWithTag("playlist_menu_edit").performScrollTo().assertExists()
        rule.onNodeWithTag("playlist_menu_cover").performScrollTo().assertExists()
        rule.onNodeWithTag("playlist_menu_reset_cover").performScrollTo().assertExists()
        rule.onNodeWithTag("playlist_menu_delete").performScrollTo().assertExists()

        // Clicking Multi-select Manage switches straight into that playlist's multi-select mode.
        rule.onNodeWithTag("playlist_menu_multiselect").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("playlist_actions_sheet").assertDoesNotExist()
        rule.onNodeWithTag("playlist_multiselect_bar").assertIsDisplayed()
        rule.runOnIdle {
            actions.state.value = actions.state.value.copy(selectedPlaylist = null)
        }
        rule.waitForIdle()

        // Favorite playlist sheet hides destructive/edit rows.
        rule.onNodeWithTag("playlist_more_fav-1").performClick()
        rule.onNodeWithTag("playlist_actions_sheet").assertIsDisplayed()
        rule.onNodeWithTag("playlist_menu_edit").assertDoesNotExist()
        rule.onNodeWithTag("playlist_menu_delete").assertDoesNotExist()
        rule.onNodeWithTag("playlist_actions_close").performClick()
        rule.waitForIdle()

        // #8: Artist cards on Discover + Artist detail page never display song/album counts.
        rule.onNodeWithTag("nav_discover").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("4", substring = true).assertCountEquals(0)
        rule.onNodeWithTag("browse_artist_The Slow Hours").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("artist_shuffle").assertExists()
        rule.onNodeWithTag("artist_add_to_playlist").assertHeightIsAtLeast(48.dp)
        rule.onAllNodesWithText("4 songs", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("2 albums", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("4 首歌曲", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("2 张专辑", substring = true).assertCountEquals(0)
    }

    @Test fun largeFontScale390dpMultiSelectBarAndButtonsDoNotClipOrShrinkTargets() {
        val entries = songs.mapIndexed { i, s -> PlaylistSong(s, i) }
        render(
            baseState(MainDestination.LIBRARY).copy(
                selectedPlaylist = commute,
                selectedPlaylistEntries = entries,
                playlistSongs = entries.map { it.song }.filter { it.canPlay },
            ),
            fontScale = 2.0f,
        )
        rule.onNodeWithTag("playlist_multiselect").performClick()
        rule.onNodeWithTag("playlist_multiselect_bar").assertIsDisplayed()
        rule.onNodeWithTag("batch_song_action_bar").assertIsDisplayed()
        rule.onNodeWithTag("batch_action_play_next").assertHeightIsAtLeast(48.dp).assertIsDisplayed()
        rule.onNodeWithTag("batch_action_favorite").assertHeightIsAtLeast(48.dp).assertIsDisplayed()

        // [Minor-4]: Scroll to the last item under fontScale=2.0f and verify dynamic bottom padding
        // leaves the final song row completely clear of the wrapped BatchSongActionBar.
        rule.onNodeWithTag("playlist_detail_tracks").performScrollToNode(hasTestTag("playlist_song_s4"))
        repeat(2) {
            rule.onNodeWithTag("playlist_detail_tracks").performTouchInput { swipeUp() }
            rule.waitForIdle()
        }
        val lastRowBounds = rule.onNodeWithTag("playlist_song_s4").fetchSemanticsNode().boundsInRoot
        val barBounds = rule.onNodeWithTag("batch_song_action_bar").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected last song row bottom (${lastRowBounds.bottom}) <= bar top (${barBounds.top})",
            lastRowBounds.bottom <= barBounds.top + 1f,
        )
    }

    @Test
    @Config(qualifiers = "w840dp-h1000dp-mdpi")
    fun tabletWideLayoutRendersPlaylistSortAndBatchActionBarWithoutClipping() {
        val entries = songs.mapIndexed { i, s -> PlaylistSong(s, i) }
        render(
            baseState(MainDestination.LIBRARY).copy(
                selectedPlaylist = commute,
                selectedPlaylistEntries = entries,
                playlistSongs = entries.map { it.song }.filter { it.canPlay },
            ),
        )
        rule.onNodeWithTag("playlist_sort_button").assertIsDisplayed()
        rule.onNodeWithTag("playlist_multiselect").assertIsDisplayed().performClick()
        rule.onNodeWithTag("playlist_multiselect_bar").assertIsDisplayed()
        rule.onNodeWithTag("batch_song_action_bar").assertIsDisplayed()
        rule.onNodeWithTag("batch_action_play_next").assertHeightIsAtLeast(48.dp).assertIsDisplayed()
        rule.onNodeWithTag("batch_action_remove").assertHeightIsAtLeast(48.dp).assertIsDisplayed()
    }
}

private class BatchCTestActions(
    val state: MutableState<ClientUiState>,
    private val catalogSongs: List<Song>,
) : ClientActions {
    var insertedNext: Song? = null
    var playedList: Pair<List<Song>, Int>? = null
    var playedNextBatch: List<String>? = null
    var enqueued: List<String>? = null
    var shelfSaved: List<String>? = null
    var orderSaved: List<String>? = null
    var batchFavorited: List<String>? = null
    var failNextFavoriteBatch = false

    private fun bumpMutation(update: (ClientUiState) -> ClientUiState = { it }) {
        state.value = update(state.value).copy(
            libraryMutationVersion = state.value.libraryMutationVersion + 1,
            libraryMutationNotice = null,
        )
    }

    override fun navigate(destination: MainDestination) { state.value = state.value.copy(destination = destination) }
    override fun selectTab(tab: LibraryTab) { state.value = state.value.copy(tab = tab) }
    override fun login(instanceUrl: String, username: String, password: String) {}
    override fun changePassword(currentPassword: String, newPassword: String) {}
    override fun logout() {}
    override fun search(query: String) { state.value = state.value.copy(query = query) }
    override fun refresh() {}
    override fun loadMore() {}
    override fun loadMoreAlbums() {}
    override fun setVolume(volume: Float) {}
    override fun playSong(song: Song) {}
    override fun togglePlay() {}
    override fun next() {}
    override fun previous() {}
    override fun seekTo(positionMs: Long) {}
    override fun toggleShuffle() {}
    override fun cycleRepeat() {}
    override fun selectQueueItem(index: Int) {}
    override fun retryPlayback() {}

    override fun selectPlaylistSort(mode: PlaylistSortMode) {
        state.value = state.value.copy(playlistSortMode = mode)
    }

    override fun selectPlaylistSongSort(mode: PlaylistSongSortMode) {
        state.value = state.value.copy(playlistSongSort = mode)
    }

    override fun replacePlaylistShelfOrder(ids: List<String>) {
        shelfSaved = ids
        bumpMutation()
    }

    override fun replacePlaylistOrder(songIds: List<String>) {
        orderSaved = songIds
        bumpMutation { cur ->
            cur.copy(selectedPlaylistEntries = songIds.mapIndexed { idx, id -> PlaylistSong(catalogSongs.first { it.id == id }, idx) })
        }
    }

    override fun openPlaylist(playlist: PlaylistSummary) {
        val entries = catalogSongs.mapIndexed { idx, s -> PlaylistSong(s, idx) }
        state.value = state.value.copy(
            selectedPlaylist = playlist,
            selectedPlaylistEntries = entries,
            playlistSongs = entries.mapNotNull { it.song.takeIf { s -> s.canPlay } },
        )
    }

    override fun closePlaylist() {
        state.value = state.value.copy(selectedPlaylist = null, selectedPlaylistEntries = emptyList())
    }

    override fun openArtist(artist: Artist) {
        state.value = state.value.copy(selectedArtist = artist, artistSongs = catalogSongs.filter { it.canPlay })
    }

    override fun closeArtist() { state.value = state.value.copy(selectedArtist = null) }
    override fun openAlbum(album: Album) { state.value = state.value.copy(selectedAlbum = album, albumSongs = catalogSongs.filter { it.canPlay }) }
    override fun closeAlbum() { state.value = state.value.copy(selectedAlbum = null) }
    override fun playSingleAsNext(song: Song, fromSearch: Boolean) { insertedNext = song }
    override fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean) { playedList = songs to startIndex }
    override fun playNextSongs(songs: List<Song>) { playedNextBatch = songs.map { it.id } }
    override fun enqueue(songs: List<Song>) { enqueued = songs.map { it.id } }

    override fun addFavorites(songs: List<Song>) {
        if (failNextFavoriteBatch) {
            failNextFavoriteBatch = false
            state.value = state.value.copy(libraryMutationNotice = ClientNotice.REQUEST_FAILED)
            return
        }
        batchFavorited = songs.map { it.id }
        bumpMutation { cur -> cur.copy(favoriteSongIds = cur.favoriteSongIds + songs.map { it.id }) }
    }

    override fun dismissLibraryMutationNotice() {
        state.value = state.value.copy(libraryMutationNotice = null)
    }
}
