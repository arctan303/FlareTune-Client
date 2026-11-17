package com.flaretune.client

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.flaretune.client.data.PlayedCollectionType
import com.flaretune.client.data.SearchEntity
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
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListeningFeaturesUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val songs = listOf(
        Song("first", "After the rain", "The Slow Hours", "Blue hour", 180.0, "https://fixture.invalid/a.mp3", "https://fixture.invalid/art/0"),
        Song("unavailable", "A saved unavailable track", "The Slow Hours", "Blue hour", 180.0, ""),
        Song("last", "沿途的光", "林间来信", "远行", 204.0, "https://fixture.invalid/b.mp3", "https://fixture.invalid/art/1"),
    )
    private val favorites = PlaylistSummary("liked-real", "Liked songs", "favorite", songCount = 1, revision = 2)
    private val regular = PlaylistSummary("commute", "On the way", "regular", "Music for the journey", 3, 7,
        previewCovers = listOf("https://fixture.invalid/art/0"))
    private val album = Album("album-blue", "Blue hour", "The Slow Hours", "https://fixture.invalid/art/0", 2)
    private val artist = Artist("The Slow Hours", 2, 1, coverUrl = "https://fixture.invalid/art/0")
    private fun fixture(destination: MainDestination = MainDestination.LIBRARY) = ClientUiState(
        screen = ClientScreen.LIBRARY, instanceUrl = "https://music.example", username = "listener", accountName = "Listener",
        destination = destination, songs = songs.filter { it.canPlay }, albums = listOf(album), searchAlbums = listOf(album), artists = listOf(artist),
        playlists = listOf(favorites, regular), playlistShelf = PlaylistShelf(4, items = listOf(ShelfItem(favorites.id), ShelfItem(regular.id))),
        favoriteSongIds = setOf(songs.first().id), favoriteSongs = listOf(songs.first()),
        playHistory = listOf(songs.first()), playHistoryPlayedAt = mapOf(songs.first().id to 1_700_000_000_000L),
        playback = PlaybackSnapshot(songs.first(), listOf(songs.first(), songs.last()), 0, true, positionMs = 30_000, durationMs = 180_000),
    )
    private lateinit var controller: FeatureActions
    private fun render(initial: ClientUiState = fixture(), largeText: Boolean = false) {
        controller = FeatureActions(mutableStateOf(initial), songs, regular)
        // Resolve the activity before composition. Reading rule.activity from Coil's
        // main-thread interceptor drains Robolectric's looper inside an active layout.
        val resources = rule.activity.resources
        val loader = coil.ImageLoader.Builder(rule.activity).components {
            add(object : coil.intercept.Interceptor {
                override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
                    val index = chain.request.data.toString().substringAfterLast('/').toIntOrNull() ?: 0
                    val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(if (index % 2 == 0) 0xFF31596B.toInt() else 0xFF86533E.toInt())
                    canvas.drawCircle(170f, 70f, 44f, android.graphics.Paint(3).apply { color = 0xFFE4C59B.toInt() })
                    canvas.drawText("FLARE", 22f, 190f, android.graphics.Paint(3).apply { color = android.graphics.Color.WHITE; textSize = 27f })
                    return coil.request.SuccessResult(android.graphics.drawable.BitmapDrawable(resources, bitmap), chain.request, coil.decode.DataSource.MEMORY)
                }
            })
        }.build()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 2f else density.fontScale)) {
                FlareTuneTheme { ClientApp(controller.state.value, controller, loader) }
            }
        }
    }

    @Test fun createPlaylistSubmitsNameAndDescriptionAndClosesOnlyAfterAcknowledgement() {
        render()
        rule.onNodeWithTag("create_playlist").performClick()
        rule.onNodeWithTag("playlist_name").performTextInput("Morning music")
        rule.onNodeWithTag("playlist_description").performTextInput("A quiet start")
        rule.onNodeWithTag("playlist_draft_save").performClick()
        rule.runOnIdle { assertEquals(Triple("Morning music", "A quiet start", emptyList<String>()), controller.created) }
        rule.onNodeWithTag("playlist_name").assertDoesNotExist()
        rule.onNodeWithTag("library_playlist_created").assertExists()
    }

    @Test fun currentSongMenuTogglesRealFavoriteActionWithoutReplacingQueue() {
        render(fixture(MainDestination.SEARCH).copy(query = "first"))
        rule.onNodeWithTag("song_more_first").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("song_actions_sheet").assertIsDisplayed()
        rule.onNodeWithTag("song_action_favorite").assertIsSelected()

        rule.onNodeWithTag("song_action_favorite").performClick()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals("first", controller.favoriteId)
            assertFalse(controller.state.value.favoriteSongIds.contains("first"))
            assertEquals(listOf("first", "last"), controller.state.value.playback.queue.map { it.id })
        }
        // #13: Toggling favorite refreshes state in-place without closing SongActionsSheet.
        rule.onNodeWithTag("song_actions_sheet").assertExists()
        rule.onNodeWithTag("song_action_favorite").assertIsNotSelected()

        // Toggling again re-favorites without closing and updates label & selection.
        rule.onNodeWithTag("song_action_favorite").performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(controller.state.value.favoriteSongIds.contains("first")) }
        rule.onNodeWithTag("song_actions_sheet").assertExists()
        rule.onNodeWithTag("song_action_favorite").assertIsSelected()
    }

    @Test fun songMenuAndPlaylistMenuActionsDismissSmoothlyViaSheetHandle() {
        render(fixture(MainDestination.SEARCH).copy(query = "first"))
        rule.onNodeWithTag("song_more_first").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("song_actions_sheet").assertExists()

        rule.onNodeWithTag("song_action_enqueue").performClick()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(listOf("first"), controller.enqueued) }
        rule.onNodeWithTag("song_actions_sheet").assertDoesNotExist()
    }

    @Test fun failedFavoriteFollowedByPlaylistRemoveDoesNotReplayFavoriteToast() {
        render(
            fixture().copy(
                selectedPlaylist = regular,
                selectedPlaylistEntries = songs.mapIndexed { i, s -> PlaylistSong(s, i) },
                playlistSongs = songs.filter { it.canPlay },
            ),
        )
        rule.onNodeWithTag("song_more_first").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("song_actions_sheet").assertIsDisplayed()

        controller.failFavoriteToggle = true
        rule.onNodeWithTag("song_action_favorite").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("song_actions_sheet").assertExists()
        rule.onNode(
            hasText("The request could not be completed. Try again.") and hasAnyAncestor(hasTestTag("song_actions_sheet")),
        ).assertIsDisplayed()
        rule.onNodeWithTag("action_feedback_toast").assertDoesNotExist()

        rule.onNodeWithTag("song_action_remove").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("song_actions_sheet").assertDoesNotExist()
        rule.onNodeWithTag("listening_confirm").assertIsDisplayed()
        rule.onAllNodesWithText("The request could not be completed. Try again.").assertCountEquals(0)

        rule.onNodeWithTag("listening_confirm").performClick()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(listOf("first"), controller.removedPlaylistSongs)
            assertFalse(controller.state.value.selectedPlaylistEntries.any { it.song.id == "first" })
            assertTrue(controller.state.value.favoriteSongIds.contains("first"))
        }
        rule.onNodeWithTag("listening_confirm").assertDoesNotExist()
        rule.onNodeWithTag("action_feedback_toast").assertDoesNotExist()
        rule.onNodeWithText("Removed from favorites").assertDoesNotExist()
        rule.onNodeWithText("Added to favorites").assertDoesNotExist()
    }

    @Test fun addFromSongMenuSendsTheSelectedPlaylistAndSongIds() {
        render(fixture(MainDestination.SEARCH).copy(query = "first"))
        rule.onNodeWithTag("song_more_first").performClick()
        rule.onNodeWithTag("song_action_playlist").performClick()
        rule.onNodeWithTag("playlist_target_commute").performClick()
        rule.onNodeWithTag("playlist_target_submit").performClick()
        rule.runOnIdle { assertEquals(listOf("commute") to listOf("first"), controller.added) }
    }

    @Test fun playlistPickerHasIndependentSearchAndAddsMultipleSelectedSongs() {
        render(fixture().copy(selectedPlaylist = regular, selectedPlaylistEntries = songs.mapIndexed { i, s -> PlaylistSong(s, i) }, playlistSongs = songs.filter { it.canPlay }))
        rule.onNodeWithTag("playlist_add_songs").performClick()
        rule.onNodeWithTag("playlist_song_picker_query").performTextInput("light")
        rule.onNodeWithTag("picker_song_first").performClick()
        rule.onNodeWithTag("picker_song_last").performClick()
        rule.onNodeWithTag("playlist_picker_submit").performClick()
        rule.runOnIdle {
            assertEquals("light", controller.pickerQuery)
            assertEquals(listOf("commute") to listOf("first", "last"), controller.added)
            assertEquals("commute", controller.state.value.selectedPlaylist?.id)
        }
    }

    @Test fun libraryMenuBindsTheChosenPlaylistBeforeLoadingItsPicker() {
        render()
        rule.onNodeWithTag("playlist_more_commute").performClick()
        rule.onNodeWithTag("playlist_menu_add").performClick()
        rule.onNodeWithTag("playlist_song_picker").assertExists()
        rule.onNodeWithTag("picker_song_first").assertExists()
        rule.runOnIdle {
            assertEquals("commute", controller.state.value.selectedPlaylist?.id)
            assertEquals(listOf("commute"), controller.pickerOwners)
        }
    }

    @Test fun playlistOrderIncludesUnavailableMembershipAndCancelDoesNotWrite() {
        render(fixture().copy(selectedPlaylist = regular, selectedPlaylistEntries = songs.mapIndexed { i, s -> PlaylistSong(s, i) }, playlistSongs = songs.filter { it.canPlay }))
        rule.onNodeWithTag("playlist_more_commute").performClick()
        rule.onNodeWithTag("playlist_menu_order").performClick()
        rule.onNodeWithTag("playlist_order_down_first").performScrollTo().performClick()
        rule.onNodeWithTag("playlist_order_save").performClick()
        rule.runOnIdle { assertEquals(listOf("unavailable", "first", "last"), controller.order) }
        rule.onNodeWithTag("playlist_more_commute").performClick()
        rule.onNodeWithTag("playlist_menu_order").performClick()
        rule.onNodeWithTag("playlist_order_down_unavailable").performScrollTo().performClick()
        rule.onNodeWithTag("playlist_order_cancel").performClick()
        rule.runOnIdle { assertEquals(1, controller.orderWrites) }
    }

    @Test fun playlistDetailEditOrderButtonOpensOrderSheetDirectly() {
        render(fixture().copy(selectedPlaylist = regular, selectedPlaylistEntries = songs.mapIndexed { i, s -> PlaylistSong(s, i) }, playlistSongs = songs.filter { it.canPlay }))
        rule.onNodeWithTag("playlist_edit_order").performClick()
        rule.onNodeWithTag("playlist_order_dialog").assertExists()
        rule.onNodeWithTag("playlist_order_save").assertIsNotEnabled()
    }

    @Test fun songActionFeedbackToastAppearsOnPlayNext() {
        render(fixture(MainDestination.SEARCH).copy(query = "first"))
        rule.onNodeWithTag("song_more_first").performClick()
        rule.onNodeWithTag("song_action_next").performClick()
        rule.onNodeWithTag("action_feedback_toast").assertExists()
    }

    @Test fun deletingAPlaylistRequiresConfirmationAndDoesNotClearAudioQueue() {
        render(fixture().copy(selectedPlaylist = regular, selectedPlaylistEntries = songs.map { PlaylistSong(it) }, playlistSongs = songs.filter { it.canPlay }))
        rule.onNodeWithTag("playlist_more_commute").performClick()
        rule.onNodeWithTag("playlist_menu_delete").performClick()
        rule.runOnIdle { assertNull(controller.deleted) }
        rule.onNodeWithTag("listening_confirm").performClick()
        rule.runOnIdle { assertEquals("commute", controller.deleted); assertEquals(2, controller.state.value.playback.queue.size); assertEquals(0, controller.queueClears) }
    }

    @Test fun shelfReorderingSendsACompleteMixedFavoriteAndRegularSequence() {
        render()
        rule.onNodeWithTag("edit_shelf").performScrollTo().performClick()
        rule.onNodeWithTag("shelf_down_liked-real").performClick()
        rule.onNodeWithTag("shelf_order_save").performClick()
        rule.runOnIdle { assertEquals(listOf("commute", "liked-real"), controller.shelf) }
    }

    @Test fun threeSearchTypesDispatchTheSameQueryToTheirOwnActions() {
        render(fixture(MainDestination.SEARCH))
        rule.onNodeWithTag("search_field").performTextInput("blue")
        rule.onNodeWithTag("search_submit").performClick()
        rule.runOnIdle { assertEquals("blue", controller.query); assertEquals("blue", controller.artistQuery); assertEquals("blue", controller.albumQuery) }
        rule.onNodeWithTag("browse_artist_The Slow Hours").performScrollTo().assertExists()
        rule.onNodeWithTag("browse_album_album-blue").performScrollTo().assertExists()
    }

    @Test fun preSearchShowsSplitRecentTermsAndDirectPicksWithoutQuickAccess() {
        render(
            fixture(MainDestination.SEARCH).copy(
                recentSearchTerms = listOf("Rain"),
                recentSearches = listOf(com.flaretune.client.data.SearchEntity.fromSong(songs.first())),
            ),
        )
        rule.onNodeWithTag("recent_searches").assertExists()
        rule.onNodeWithText("Rain").performClick()
        rule.runOnIdle { assertEquals("Rain", controller.query) }
    }

    @Test fun artistCollectionAppendAndAndroidBackKeepExistingPlayer() {
        render(fixture().copy(selectedArtist = artist, artistSongs = songs.filter { it.canPlay }, artistAlbums = listOf(album)))
        rule.onNodeWithTag("artist_enqueue").performClick()
        rule.runOnIdle { assertEquals(listOf("first", "last"), controller.enqueued) }
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("artist_detail").assertDoesNotExist()
        rule.onNodeWithTag("mini_player").assertExists()
    }

    @Test fun historyHasIndependentRemoveAndClearActions() {
        render(fixture(MainDestination.HOME))
        rule.onNodeWithTag("home_featured_cards").performScrollToNode(hasTestTag("home_history"))
        rule.onNodeWithTag("home_history").performClick()
        rule.onNodeWithTag("history_panel").assertExists()
        rule.onNodeWithTag("history_remove_first").performClick()
        rule.runOnIdle { assertEquals("first", controller.removedHistory); assertEquals(2, controller.state.value.playback.queue.size) }
        rule.onNodeWithTag("history_back").performClick()
        rule.onNodeWithTag("history_panel").assertDoesNotExist()
    }

    @Test fun metadataOnlyHistoryRemainsClickableForFreshResolution() {
        render(fixture(MainDestination.HOME).copy(playHistory = listOf(songs[1])))
        rule.onNodeWithTag("home_featured_cards").performScrollToNode(hasTestTag("home_history"))
        rule.onNodeWithTag("home_history").performClick()
        rule.onNodeWithTag("history_song_unavailable").performClick()
        rule.runOnIdle { assertEquals("unavailable", controller.historyPlayed) }
    }

    @Test fun continueListeningIncludesOnlyTheOnePlayedTrackFromALargeQueue() {
        val queue = listOf(songs.first()) + (0 until 29).map { Song("queued-$it", "Queued $it", audioUrl="https://fixture.invalid/$it.mp3") }
        render(fixture(MainDestination.HOME).copy(
            playback = PlaybackSnapshot(songs.first(), queue, 0, true),
            playHistory = listOf(songs.first()),
            continueListeningSongs = listOf(songs.first()),
        ))
        rule.onNodeWithTag("home_feed").performScrollToNode(hasTestTag("home_continue_song_first"))
        rule.onNodeWithTag("home_continue_song_queued-0").assertDoesNotExist()
        rule.onNodeWithTag("home_continue_song_first").performClick()
        rule.runOnIdle { assertEquals(songs.first().id, controller.state.value.playback.current?.id) }
        rule.onNodeWithTag("home_feed").performScrollToNode(hasTestTag("home_featured_cards"))
        rule.onNodeWithTag("home_featured_cards").performScrollToNode(hasTestTag("home_history"))
        rule.onNodeWithTag("home_history").performClick()
        rule.onNodeWithTag("history_song_first").assertExists()
        rule.onNodeWithTag("history_song_queued-0").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "zh-rCN-w390dp-h844dp-night-mdpi")
    fun renderCompactLibraryAndPlaylistEditor() {
        render()
        screenshot("listening-library-compact")
        rule.onNodeWithTag("create_playlist").performClick()
        screenshot("listening-playlist-create")
    }

    @Test @Config(qualifiers = "zh-rCN-w1280dp-h800dp-night-mdpi")
    fun renderWideLibraryAndRealSongActions() {
        render(fixture(MainDestination.SEARCH).copy(query = "blue"))
        screenshot("listening-search-wide")
        rule.onNodeWithTag("song_more_first").performClick()
        rule.onNodeWithTag("song_action_favorite").assertIsDisplayed()
        screenshot("listening-song-actions-wide")
    }

    @Test @Config(qualifiers = "zh-rCN-w1280dp-h800dp-night-mdpi")
    fun favoritePlaylistDrawsWhiteBackgroundRedHeartInsteadOfCollage() {
        val collageFav = favorites.copy(
            name = "我喜欢",
            songCount = 4,
            previewCovers = listOf(
                "https://fixture.invalid/art/0",
                "https://fixture.invalid/art/1",
                "https://fixture.invalid/art/2",
                "https://fixture.invalid/art/3",
            ),
        )
        render(
            fixture().copy(
                playlists = listOf(collageFav, regular),
                selectedPlaylist = collageFav,
                selectedPlaylistEntries = songs.map { PlaylistSong(it) },
                playlistSongs = songs.filter { it.canPlay },
            ),
        )
        val heartBounds = rule.onNodeWithTag("favorite_heart_cover").fetchSemanticsNode().boundsInRoot
        screenshot("favorite-heart-cover-detail")
        lateinit var crop: Bitmap
        rule.runOnIdle {
            val view = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            val left = heartBounds.left.roundToInt().coerceIn(0, bmp.width - 1)
            val top = heartBounds.top.roundToInt().coerceIn(0, bmp.height - 1)
            crop = Bitmap.createBitmap(
                bmp,
                left,
                top,
                heartBounds.width.roundToInt().coerceAtLeast(1).coerceAtMost(bmp.width - left),
                heartBounds.height.roundToInt().coerceAtLeast(1).coerceAtMost(bmp.height - top),
            )
        }
        val pixels = crop.asImageBitmap().toPixelMap()
        var whitePixels = 0
        var redPixels = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val px = pixels[x, y]
            if (px.red > 0.90f && px.green > 0.90f && px.blue > 0.90f) whitePixels++
            if (px.red > 0.70f && (px.red - px.green) > 0.35f && (px.red - px.blue) > 0.35f) redPixels++
        }
        assertTrue("Favorites cover must have clean white background (white=$whitePixels)", whitePixels > 1000)
        assertTrue("Favorites cover must draw vibrant red heart (red=$redPixels)", redPixels > 400)
    }

    @Test @Config(qualifiers = "zh-rCN-w1280dp-h800dp-mdpi")
    fun homeFavoritesUsesLatestCoverAndWideSongShelvesKeepRowHeightAcrossColumns() {
        val sampleSongs = (0 until 12).map { index ->
            Song("song-$index", "Track $index", "Artist $index", audioUrl = "https://fixture.invalid/$index.mp3", coverUrl = "https://fixture.invalid/cover/$index.jpg")
        }
        render(
            fixture(MainDestination.HOME).copy(
                latestFavoriteCoverUrl = "https://fixture.invalid/cover/latest-fav.jpg",
                continueListeningSongs = sampleSongs,
                songs = sampleSongs,
            ),
        )
        val favBounds = rule.onNodeWithTag("home_favorites").fetchSemanticsNode().boundsInRoot
        val queueBounds = rule.onNodeWithTag("home_queue_card").fetchSemanticsNode().boundsInRoot
        val historyBounds = rule.onNodeWithTag("home_history").fetchSemanticsNode().boundsInRoot
        val dockBounds = rule.onNodeWithTag("dock_wide").fetchSemanticsNode().boundsInRoot
        val navHomeBounds = rule.onNodeWithTag("nav_home").fetchSemanticsNode().boundsInRoot
        val navLibBounds = rule.onNodeWithTag("nav_library").fetchSemanticsNode().boundsInRoot
        val miniBounds = rule.onNodeWithTag("mini_player").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("favorite_heart_cover").assertDoesNotExist()
        rule.onNodeWithTag("home_feed").performScrollToNode(hasTestTag("home_continue_song_song-0"))
        val continueCardBounds = rule.onNodeWithTag("home_continue_song_song-0").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("home_feed").performScrollToNode(hasTestTag("home_song_song-3"))
        rule.onNodeWithTag("home_feed").performTouchInput { swipeUp() }
        rule.waitForIdle()
        val rootWidth = rule.onNodeWithTag("home_page").fetchSemanticsNode().boundsInRoot.width
        val col1Row0 = rule.onNodeWithTag("home_song_song-0").fetchSemanticsNode().boundsInRoot
        val col1Row3 = rule.onNodeWithTag("home_song_song-3").fetchSemanticsNode().boundsInRoot
        val col2Row0 = rule.onNodeWithTag("home_song_song-4").fetchSemanticsNode().boundsInRoot
        val col3Row0 = rule.onNodeWithTag("home_song_song-8").fetchSemanticsNode().boundsInRoot
        val density = rule.density.density
        assertTrue("Featured card order should place Queue after Favorites", favBounds.right < queueBounds.left && queueBounds.right < historyBounds.left)
        assertTrue("Wide hero card should be enlarged (>=320dp wide, >=190dp tall), got ${favBounds.width / density}x${favBounds.height / density}dp", favBounds.width / density >= 320f && favBounds.height / density >= 190f)
        assertTrue("Continue listening card should be 1:1 square tile (>=140dp wide on 1280dp), got ${continueCardBounds.width / density}x${continueCardBounds.height / density}dp", continueCardBounds.width / density >= 140f && kotlin.math.abs(continueCardBounds.width - continueCardBounds.height) / density <= 2f)
        assertTrue("Wide dock should be enlarged (~78dp tall, >=880dp wide on 1280dp), got ${dockBounds.width / density}x${dockBounds.height / density}dp", dockBounds.height / density >= 76f && dockBounds.width / density >= 880f)
        val navWidth = navLibBounds.right - navHomeBounds.left
        assertTrue("Navigation bar ($navWidth) must be wider than mini player (${miniBounds.width})", navWidth > miniBounds.width)
        assertTrue("Row height must remain >= 78dp (${col1Row0.height / density}dp)", col1Row0.height / density >= 78f)
        assertTrue("Row 4 of col 1 sits below row 0", col1Row3.top > col1Row0.bottom)
        assertTrue("Column 2 sits to the right of Column 1 at same vertical level", col2Row0.left > col1Row0.right && kotlin.math.abs(col2Row0.top - col1Row0.top) < 2f)
        val colWidthFraction = col1Row0.width / rootWidth
        assertTrue("Column width (~2/5 of screen) should be 0.35..0.44, was $colWidthFraction", colWidthFraction in 0.35f..0.44f)
        val col3VisibleFraction = (rootWidth - col3Row0.left) / rootWidth
        assertTrue("Column 3 should peek ~1/5 of screen (0.14..0.24), was $col3VisibleFraction", col3VisibleFraction in 0.14f..0.24f)
    }

    @Test @Config(qualifiers = "zh-rCN-w412dp-h915dp-mdpi")
    fun phoneHomeEnlargesHeroCardsSupportsTopFiltersAndClearsBottomGestureBar() {
        render(
            fixture(MainDestination.HOME).copy(
                continueListeningSongs = songs.filter { it.canPlay },
                discoverySongs = songs.filter { it.canPlay },
            ),
        )
        val density = rule.density.density
        val favBounds = rule.onNodeWithTag("home_favorites").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Phone hero card should be enlarged (>=290dp wide, >=170dp tall), got ${favBounds.width / density}x${favBounds.height / density}dp",
            favBounds.width / density >= 290f && favBounds.height / density >= 170f,
        )
        rule.onNodeWithTag("home_featured_dots").assertIsDisplayed()
        rule.onNodeWithTag("home_featured_dot_0").assertIsDisplayed()
        rule.onNodeWithTag("home_featured_dot_3").assertIsDisplayed()

        rule.onNodeWithTag("home_continue_shelf").assertExists()
        rule.onNodeWithTag("home_continue_song_first").assertExists()
        rule.onNodeWithTag("home_continue_shelf").performScrollToNode(hasTestTag("home_continue_playlist_commute"))
        rule.onNodeWithTag("home_continue_playlist_commute").assertIsDisplayed()
        rule.onNodeWithTag("home_continue_shelf").performScrollToNode(hasTestTag("home_continue_artist_The Slow Hours"))
        rule.onNodeWithTag("home_continue_artist_The Slow Hours").assertIsDisplayed()

        val dockBounds = rule.onNodeWithTag("dock_compact").fetchSemanticsNode().boundsInRoot
        val navBounds = rule.onNodeWithTag("nav_discover").fetchSemanticsNode().boundsInRoot
        val bottomClearanceDp = (dockBounds.bottom - navBounds.bottom) / density
        assertTrue("Compact nav should clear floating gesture pill (>=12dp), got ${bottomClearanceDp}dp", bottomClearanceDp >= 12f)

        rule.onNodeWithTag("home_filter_all").assertExists()
        rule.onNodeWithTag("home_filter_continue").assertExists()
        rule.onNodeWithTag("home_filter_discovery").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_discovery").assertIsDisplayed()
        rule.onNodeWithTag("home_filter_all").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_favorites").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "zh-rCN-w360dp-h740dp-night-mdpi")
    fun renderLargeTextPlaylistAndAccessibleControls() {
        render(fixture().copy(selectedPlaylist = regular, selectedPlaylistEntries = songs.map { PlaylistSong(it) }, playlistSongs = songs.filter { it.canPlay }), largeText = true)
        rule.onNodeWithTag("playlist_add_songs").performScrollTo().assertIsDisplayed()
        screenshot("listening-playlist-large")
    }

    @Test
    fun searchDebouncePreservesTrailingSpaceWhileTypingAndSyncsExternalReset() {
        render(fixture(MainDestination.SEARCH))
        rule.onNodeWithTag("search_field").performTextInput("rain ")
        rule.mainClock.advanceTimeBy(450)
        rule.waitForIdle()
        rule.runOnIdle { assertEquals("rain", controller.query) }
        rule.onNodeWithTag("search_field").assert(hasText("rain "))
        rule.runOnIdle { controller.search("") }
        rule.waitForIdle()
        rule.onNodeWithTag("search_field").assert(hasText(""))
    }

    @Test @Config(qualifiers = "zh-rCN-w412dp-h915dp-mdpi")
    fun homeHidesDiscoveryPillWhenEmptyAndHighlightsLibraryAtBottom() {
        val sampleSongs = (0 until 12).map { index ->
            Song("song-$index", "Track $index", "Artist $index", audioUrl = "https://fixture.invalid/$index.mp3")
        }
        render(
            fixture(MainDestination.HOME).copy(
                continueListeningSongs = sampleSongs,
                discoverySongs = emptyList(),
                discoveryLoading = false,
                discoveryNotice = null,
                songs = sampleSongs,
            ),
        )
        rule.onNodeWithTag("home_filter_discovery").assertDoesNotExist()
        rule.onNodeWithTag("home_filter_all").assertIsSelected()
        rule.onNodeWithTag("home_feed").performTouchInput { repeat(4) { swipeUp() } }
        rule.waitForIdle()
        rule.onNodeWithTag("home_filter_library").assertIsSelected()
    }

    @Test @Config(qualifiers = "zh-rCN-w412dp-h915dp-mdpi")
    fun mainTabsOmitTopRightRefreshAndLibraryEmptyPromptMatchesButtonLabel() {
        render(
            fixture(MainDestination.LIBRARY).copy(
                playlists = emptyList(),
                playlistShelf = PlaylistShelf(1, items = emptyList()),
            ),
        )
        rule.onNodeWithTag("library_refresh").assertDoesNotExist()
        rule.onNodeWithTag("library_heading_pill").assertExists()
        rule.onNodeWithText("还没有自建歌单，点击“创建歌单”创建你的第一个歌单吧。").assertIsDisplayed()
        rule.onNodeWithTag("nav_discover").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("discover_refresh").assertDoesNotExist()
        rule.onNodeWithTag("nav_search").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("search_refresh").assertDoesNotExist()
        rule.onNodeWithTag("nav_home").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_refresh").assertDoesNotExist()
    }

    @Test fun singleSongDiscoveryEntriesInsertAsNextWhilePlayAllReplacesQueue() {
        render(
            fixture(MainDestination.HOME).copy(
                playback = PlaybackSnapshot(songs.first(), listOf(songs.first(), songs.last()), 0, true),
                recentSearchTerms = emptyList(),
                recentSearches = listOf(SearchEntity.fromSong(songs[2])),
            ),
        )
        // 1. Home "From your library": clicking single song uses playSingleAsNext; Play All replaces queue.
        rule.onNodeWithTag("home_feed").performScrollToNode(hasTestTag("home_library_play_all"))
        rule.onNodeWithTag("home_song_last").performClick()
        rule.runOnIdle {
            assertEquals("last", controller.insertedNext?.id)
            assertNull(controller.playedList)
        }
        rule.onNodeWithTag("home_library_play_all").performClick()
        rule.runOnIdle {
            assertEquals(listOf("first", "last"), controller.playedList?.first?.map { it.id })
            assertEquals(0, controller.playedList?.second)
        }

        // 2. Search pre-search recent song & search overview: clicking single song uses playSingleAsNext; Play All replaces queue.
        controller.insertedNext = null
        controller.playedList = null
        rule.onNodeWithTag("nav_search").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("沿途的光").performClick()
        rule.runOnIdle { assertEquals("last", controller.openedRecentSearch?.id) }

        controller.state.value = controller.state.value.copy(query = "rain")
        rule.waitForIdle()
        rule.onNodeWithTag("catalog_list").performScrollToNode(hasTestTag("search_play_all"))
        rule.onNodeWithTag("song_first").performClick()
        rule.runOnIdle {
            assertEquals("first", controller.insertedNext?.id)
            assertNull(controller.playedList)
        }
        rule.onNodeWithTag("search_play_all").performClick()
        rule.runOnIdle {
            assertEquals(listOf("first", "last"), controller.playedList?.first?.map { it.id })
        }
    }

    @Test fun playHistoryFourTabsRenderSwitchRestoreQueueAndSupportBackStack() {
        val artist = VisitedArtist(id = "art-1", name = "The Slow Hours", source = "local", visitedAtMs = 500L)
        val collection = PlayedCollection(
            type = PlayedCollectionType.ALBUM,
            id = "alb-1",
            title = "Blue hour",
            subtitle = "The Slow Hours",
            trackCount = 8,
            source = "local",
            kind = "regular",
            playedAtMs = 600L,
        )
        val snapshot = QueueSnapshot(
            id = "snap-1",
            capturedAtMs = 700L,
            sourceLabel = "Morning Mix",
            sourceType = "playlist",
            songs = songs,
            currentIndex = 2,
        )
        render(
            fixture(MainDestination.HOME).copy(
                playHistory = listOf(songs.first(), songs.last()),
                visitedArtists = listOf(artist),
                playedCollections = listOf(collection),
                queueSnapshots = listOf(snapshot),
            ),
        )
        rule.onNodeWithTag("home_featured_cards").performScrollToNode(hasTestTag("home_history"))
        rule.onNodeWithTag("home_history").performClick()
        rule.onNodeWithTag("history_panel").assertExists()

        // Songs tab: Play All dispatches playSongs, row click dispatches playHistorySong.
        rule.onNodeWithTag("history_play_all").performClick()
        rule.runOnIdle {
            assertEquals(listOf("first", "last"), controller.playedList?.first?.map { it.id })
        }

        // Artists tab
        rule.onNodeWithTag("history_tab_artists").performClick()
        rule.onNodeWithTag("history_artists_list").assertExists()
        rule.onNodeWithTag("history_artist_id:art-1").performClick()
        rule.runOnIdle { assertEquals("The Slow Hours", controller.state.value.selectedArtist?.name) }
        rule.runOnIdle { controller.closeArtist() }
        rule.onNodeWithTag("history_remove_artist_id:art-1").performClick()
        rule.runOnIdle { assertEquals("id:art-1", controller.removedArtistKey) }

        // Collections tab
        rule.onNodeWithTag("history_tab_collections").performClick()
        rule.onNodeWithTag("history_collections_list").assertExists()
        rule.onNodeWithTag("history_play_collection_ALBUM:alb-1").performClick()
        rule.runOnIdle { assertEquals("alb-1", controller.playedCollection?.id) }
        rule.onNodeWithTag("history_remove_collection_ALBUM:alb-1").performClick()
        rule.runOnIdle { assertEquals("ALBUM:alb-1", controller.removedCollectionKey) }

        // Queues tab
        rule.onNodeWithTag("history_tab_queues").performClick()
        rule.onNodeWithTag("history_queues_list").assertExists()
        rule.onNodeWithTag("history_current_queue_card").assertExists()
        rule.onNodeWithTag("history_restore_queue_snap-1").performClick()
        rule.runOnIdle { assertEquals("snap-1", controller.restoredSnapshotId) }
        rule.onNodeWithTag("history_remove_queue_snap-1").performClick()
        rule.runOnIdle { assertEquals("snap-1", controller.removedSnapshotId) }

        // System Back closes PlayHistoryPage.
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("history_panel").assertDoesNotExist()
    }

    @Test fun playHistoryArtistsCollectionsAndQueuesClearRequireConfirmation() {
        val artist = VisitedArtist(id = "art-1", name = "The Slow Hours", source = "local", visitedAtMs = 500L)
        val collection = PlayedCollection(
            type = PlayedCollectionType.ALBUM,
            id = "alb-1",
            title = "Blue hour",
            subtitle = "The Slow Hours",
            trackCount = 8,
            source = "local",
            kind = "regular",
            playedAtMs = 600L,
        )
        val snapshot = QueueSnapshot(
            id = "snap-1",
            capturedAtMs = 700L,
            sourceLabel = "Morning Mix",
            sourceType = "playlist",
            songs = songs,
            currentIndex = 1,
        )
        render(
            fixture(MainDestination.HOME).copy(
                visitedArtists = listOf(artist),
                playedCollections = listOf(collection),
                queueSnapshots = listOf(snapshot),
            ),
        )
        rule.onNodeWithTag("home_featured_cards").performScrollToNode(hasTestTag("home_history"))
        rule.onNodeWithTag("home_history").performClick()

        // Artists clear confirmation: Cancel retains data, Confirm executes clear.
        rule.onNodeWithTag("history_tab_artists").performClick()
        rule.onNodeWithTag("history_clear_artists").performClick()
        rule.onNodeWithTag("history_clear_dialog").assertIsDisplayed()
        rule.onNodeWithTag("history_clear_cancel").performClick()
        rule.onNodeWithTag("history_clear_dialog").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(0, controller.clearedVisitedArtistsCount)
            assertEquals(1, controller.state.value.visitedArtists.size)
        }
        rule.onNodeWithTag("history_clear_artists").performClick()
        rule.onNodeWithTag("history_clear_confirm").performClick()
        rule.onNodeWithTag("history_clear_dialog").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(1, controller.clearedVisitedArtistsCount)
            assertTrue(controller.state.value.visitedArtists.isEmpty())
        }

        // Collections clear confirmation: Cancel retains data, Confirm executes clear.
        rule.onNodeWithTag("history_tab_collections").performClick()
        rule.onNodeWithTag("history_clear_collections").performClick()
        rule.onNodeWithTag("history_clear_dialog").assertIsDisplayed()
        rule.onNodeWithTag("history_clear_cancel").performClick()
        rule.runOnIdle {
            assertEquals(0, controller.clearedPlayedCollectionsCount)
            assertEquals(1, controller.state.value.playedCollections.size)
        }
        rule.onNodeWithTag("history_clear_collections").performClick()
        rule.onNodeWithTag("history_clear_confirm").performClick()
        rule.runOnIdle {
            assertEquals(1, controller.clearedPlayedCollectionsCount)
            assertTrue(controller.state.value.playedCollections.isEmpty())
        }

        // Queues clear confirmation: Cancel retains data, Confirm executes clear.
        rule.onNodeWithTag("history_tab_queues").performClick()
        rule.onNodeWithTag("history_clear_queues").performClick()
        rule.onNodeWithTag("history_clear_dialog").assertIsDisplayed()
        rule.onNodeWithTag("history_clear_cancel").performClick()
        rule.runOnIdle {
            assertEquals(0, controller.clearedQueueSnapshotsCount)
            assertEquals(1, controller.state.value.queueSnapshots.size)
        }
        rule.onNodeWithTag("history_clear_queues").performClick()
        rule.onNodeWithTag("history_clear_confirm").performClick()
        rule.runOnIdle {
            assertEquals(1, controller.clearedQueueSnapshotsCount)
            assertTrue(controller.state.value.queueSnapshots.isEmpty())
        }
    }

    @Test fun playbackFeedbackNeverFiresOptimisticallyOnClickAndRendersVerifiedEvents() {
        val snapshot = QueueSnapshot(
            id = "snap-1",
            capturedAtMs = 700L,
            sourceLabel = "Morning Mix",
            sourceType = "playlist",
            songs = songs,
            currentIndex = 1,
        )
        render(
            fixture(MainDestination.HOME).copy(
                query = "first",
                recentSearches = listOf(SearchEntity.fromSong(songs.first())),
                playHistory = listOf(songs.first(), songs.last()),
                queueSnapshots = listOf(snapshot),
            ),
        )

        // Clicking Home song, Search song, History song, and Restore Queue must not show optimistic feedback.
        rule.onNodeWithTag("home_song_last").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("action_feedback_toast").assertDoesNotExist()

        rule.onNodeWithTag("nav_search").performClick()
        rule.onNodeWithTag("song_first").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("action_feedback_toast").assertDoesNotExist()

        rule.onNodeWithTag("nav_home").performClick()
        rule.onNodeWithTag("home_featured_cards").performScrollToNode(hasTestTag("home_history"))
        rule.onNodeWithTag("home_history").performClick()
        rule.onNodeWithTag("history_song_first").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("action_feedback_toast").assertDoesNotExist()

        rule.onNodeWithTag("history_tab_queues").performClick()
        rule.onNodeWithTag("history_restore_queue_snap-1").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("action_feedback_toast").assertDoesNotExist()

        // Verified feedback events from ViewModel render localized toast and are dismissed cleanly.
        rule.runOnIdle {
            controller.state.value = controller.state.value.copy(
                playbackFeedback = PlaybackFeedbackEvent(101L, PlaybackFeedbackKind.TRACK_UNAVAILABLE),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("action_feedback_toast").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(com.flaretune.client.R.string.ft_feedback_track_unavailable)).assertIsDisplayed()
        rule.runOnIdle { assertNull(controller.state.value.playbackFeedback) }

        rule.runOnIdle {
            controller.state.value = controller.state.value.copy(
                playbackFeedback = PlaybackFeedbackEvent(102L, PlaybackFeedbackKind.QUEUE_RESTORED_PARTIAL, omittedCount = 2),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("action_feedback_toast").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.resources.getQuantityString(com.flaretune.client.R.plurals.ft_history_queue_restored_partial, 2, 2)).assertIsDisplayed()
        rule.runOnIdle { assertNull(controller.state.value.playbackFeedback) }
    }

    @Test @Config(qualifiers = "zh-rCN-w390dp-h844dp-mdpi")
    fun shelfHeadingAndPlayHistoryPageLargeTextNarrowViewportDoesNotClipOrOverlap() {
        assertLargeFontAccessibleLayout(viewportMaxWidth = 390.dp)
    }

    @Test @Config(qualifiers = "zh-rCN-w1024dp-h768dp-mdpi")
    fun shelfHeadingAndPlayHistoryPageTabletLargeTextLayoutDoesNotClip() {
        assertLargeFontAccessibleLayout(viewportMaxWidth = 1024.dp)
    }

    private fun assertLargeFontAccessibleLayout(viewportMaxWidth: androidx.compose.ui.unit.Dp) {
        val snapshot = QueueSnapshot(
            id = "snap-1",
            capturedAtMs = 700L,
            sourceLabel = "Morning Mix",
            sourceType = "playlist",
            songs = songs,
            currentIndex = 1,
        )
        render(
            fixture(MainDestination.HOME).copy(
                playHistory = listOf(songs.first(), songs.last()),
                queueSnapshots = listOf(snapshot),
            ),
            largeText = true,
        )

        val playAllHome = rule.onNodeWithTag("home_library_play_all").performScrollTo().assertIsDisplayed()
        playAllHome.assertHeightIsAtLeast(48.dp)
        val allHome = rule.onNodeWithTag("home_all_songs").performScrollTo().assertIsDisplayed()
        allHome.assertHeightIsAtLeast(48.dp)
        val playAllHomeRect = playAllHome.getUnclippedBoundsInRoot()
        val allHomeRect = allHome.getUnclippedBoundsInRoot()
        assertTrue(playAllHomeRect.right <= allHomeRect.left + 1.dp || playAllHomeRect.bottom <= allHomeRect.top + 1.dp)
        assertTrue(allHomeRect.right <= viewportMaxWidth + 1.dp)

        rule.onNodeWithTag("home_featured_cards").performScrollToNode(hasTestTag("home_history"))
        rule.onNodeWithTag("home_history").performClick()

        val tabTags = listOf("history_tab_songs", "history_tab_artists", "history_tab_collections", "history_tab_queues")
        val tabRects = tabTags.map { tag ->
            val node = rule.onNodeWithTag(tag).assertIsDisplayed()
            node.assertHeightIsAtLeast(48.dp)
            node.getUnclippedBoundsInRoot().also { rect ->
                assertTrue("$tag right=${rect.right} exceeds $viewportMaxWidth", rect.right <= viewportMaxWidth + 1.dp)
            }
        }
        for (i in tabRects.indices) {
            for (j in i + 1 until tabRects.size) {
                val a = tabRects[i]
                val b = tabRects[j]
                val disjoint = a.right <= b.left + 1.dp || b.right <= a.left + 1.dp ||
                    a.bottom <= b.top + 1.dp || b.bottom <= a.top + 1.dp
                assertTrue("History tabs $i and $j overlap: $a vs $b", disjoint)
            }
        }

        rule.onNodeWithTag("history_play_all").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)

        rule.onNodeWithTag("history_tab_queues").performClick()
        rule.onNodeWithTag("history_open_current_queue").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("history_restore_queue_snap-1").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }

    private fun screenshot(name: String) {
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        val directory = File(checkNotNull(System.getProperty("flaretune.screenshots.dir"))).apply { mkdirs() }
        rule.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull { it.isShown && it.width > 0 && it.height > 0 } ?: rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(bitmap.width > 100 && bitmap.height > 100)
        }
    }
}

private class FeatureActions(
    val state: MutableState<ClientUiState>, private val songs: List<Song>, private val playlist: PlaylistSummary,
) : ClientActions {
    var created: Triple<String,String,List<String>>? = null
    var favoriteId: String? = null
    var added: Pair<List<String>,List<String>>? = null
    var order: List<String>? = null
    var orderWrites = 0
    var deleted: String? = null
    var shelf: List<String>? = null
    var enqueued: List<String>? = null
    var removedHistory: String? = null
    var historyPlayed: String? = null
    var insertedNext: Song? = null
    var playedList: Pair<List<Song>, Int>? = null
    var openedRecentSearch: SearchEntity? = null
    var removedArtistKey: String? = null
    var playedCollection: PlayedCollection? = null
    var removedCollectionKey: String? = null
    var restoredSnapshotId: String? = null
    var removedSnapshotId: String? = null
    val pickerOwners = mutableListOf<String?>()
    var queueClears = 0
    var clearedVisitedArtistsCount = 0
    var clearedPlayedCollectionsCount = 0
    var clearedQueueSnapshotsCount = 0
    var failFavoriteToggle = false
    var removedPlaylistSongs: List<String>? = null
    var query = ""; var artistQuery = ""; var albumQuery = ""; var pickerQuery = ""
    private fun success(update: (ClientUiState)->ClientUiState = { it }) { state.value = update(state.value).copy(libraryMutationVersion = state.value.libraryMutationVersion + 1) }
    override fun dismissLibraryMutationNotice() { state.value = state.value.copy(libraryMutationNotice = null) }
    override fun createPlaylist(name:String,description:String,songIds:List<String>) {
        created = Triple(name,description,songIds)
        success { it.copy(playlists = it.playlists + PlaylistSummary("created",name,"regular",description)) }
    }
    override fun toggleFavorite(song:Song) {
        favoriteId=song.id
        if (failFavoriteToggle) {
            failFavoriteToggle = false
            state.value = state.value.copy(libraryMutationNotice = ClientNotice.REQUEST_FAILED)
            return
        }
        success { it.copy(favoriteSongIds = if (song.id in it.favoriteSongIds) it.favoriteSongIds - song.id else it.favoriteSongIds + song.id) }
    }
    override fun removePlaylistSongs(songIds:List<String>) {
        removedPlaylistSongs = songIds
        success { current -> current.copy(selectedPlaylistEntries = current.selectedPlaylistEntries.filterNot { it.song.id in songIds }) }
    }
    override fun addPlaylistSongs(playlistIds:List<String>,songIds:List<String>) { added=playlistIds to songIds; success() }
    override fun replacePlaylistOrder(songIds:List<String>) { order=songIds; orderWrites++; success { it.copy(selectedPlaylistEntries=songIds.map { id->PlaylistSong(songs.first { it.id==id }) }) } }
    override fun deletePlaylist(playlist:PlaylistSummary) { deleted=playlist.id; success { it.copy(playlists=it.playlists.filterNot { row->row.id==playlist.id },selectedPlaylist=null,selectedPlaylistEntries=emptyList()) } }
    override fun replacePlaylistShelfOrder(ids:List<String>) { shelf=ids; success() }
    override fun searchPlaylistSongs(query:String) { pickerQuery=query; pickerOwners += state.value.selectedPlaylist?.id
        state.value=state.value.copy(pickerQuery=query,pickerSongs=if(state.value.selectedPlaylist!=null) songs.filter { it.canPlay } else emptyList()) }
    override fun playHistorySong(song:Song) { historyPlayed=song.id }
    override fun playSingleAsNext(song:Song, fromSearch: Boolean) { insertedNext = song }
    override fun playSongs(songs:List<Song>, startIndex:Int, shuffle:Boolean) { playedList = songs to startIndex }
    override fun openRecentSearch(entry:SearchEntity) { openedRecentSearch = entry }
    override fun removeVisitedArtist(artistKey:String) { removedArtistKey = artistKey }
    override fun playPlayedCollection(collection:PlayedCollection) { playedCollection = collection }
    override fun removePlayedCollection(collectionKey:String) { removedCollectionKey = collectionKey }
    override fun restoreQueueSnapshot(snapshotId:String) { restoredSnapshotId = snapshotId }
    override fun removeQueueSnapshot(snapshotId:String) { removedSnapshotId = snapshotId }
    override fun refreshArtists(query:String) { artistQuery=query }
    override fun searchAlbums(query:String) { albumQuery=query }
    override fun enqueue(songs:List<Song>) { enqueued=songs.map { it.id } }
    override fun removeHistory(songId:String) { removedHistory=songId; state.value=state.value.copy(playHistory=state.value.playHistory.filterNot { it.id==songId }) }
    override fun clearVisitedArtists() { clearedVisitedArtistsCount++; state.value=state.value.copy(visitedArtists=emptyList()) }
    override fun clearPlayedCollections() { clearedPlayedCollectionsCount++; state.value=state.value.copy(playedCollections=emptyList()) }
    override fun clearQueueSnapshots() { clearedQueueSnapshotsCount++; state.value=state.value.copy(queueSnapshots=emptyList()) }
    override fun dismissPlaybackFeedback(eventId:Long) {
        if (state.value.playbackFeedback?.id == eventId) state.value=state.value.copy(playbackFeedback=null)
    }
    override fun clearQueue() { queueClears++; state.value=state.value.copy(playback=PlaybackSnapshot()) }
    override fun navigate(destination:MainDestination) { state.value=state.value.copy(destination=destination) }
    override fun search(query:String) { this.query=query; state.value=state.value.copy(query=query) }
    override fun openArtist(artist:Artist) { state.value=state.value.copy(selectedArtist=artist,artistSongs=songs.filter { it.canPlay }) }
    override fun closeArtist() { state.value=state.value.copy(selectedArtist=null) }
    override fun openPlaylist(playlist:PlaylistSummary) { state.value=state.value.copy(selectedPlaylist=playlist,selectedPlaylistEntries=songs.map { PlaylistSong(it) },playlistSongs=songs.filter { it.canPlay }) }
    override fun closePlaylist() { state.value=state.value.copy(selectedPlaylist=null,selectedPlaylistEntries=emptyList()) }
    override fun openAlbum(album:Album) { state.value=state.value.copy(selectedAlbum=album,albumSongs=songs.filter { it.canPlay }) }
    override fun closeAlbum() { state.value=state.value.copy(selectedAlbum=null) }
    override fun loadMoreAlbums() {}; override fun setVolume(volume:Float) {}
    override fun login(instanceUrl:String,username:String,password:String) {}; override fun changePassword(currentPassword:String,newPassword:String) {}; override fun logout() {}
    override fun selectTab(tab:LibraryTab) { state.value=state.value.copy(tab=tab) }
    override fun refresh() {}; override fun loadMore() {}; override fun playSong(song:Song) {}; override fun togglePlay() {}; override fun next() {}; override fun previous() {}
    override fun seekTo(positionMs:Long) {}; override fun toggleShuffle() {}; override fun cycleRepeat() {}; override fun selectQueueItem(index:Int) {}; override fun retryPlayback() {}
}
