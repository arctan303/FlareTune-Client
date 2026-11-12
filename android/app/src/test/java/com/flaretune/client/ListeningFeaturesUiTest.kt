package com.flaretune.client

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
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
        render(fixture(MainDestination.SEARCH))
        rule.onNodeWithTag("song_more_first").performClick()
        rule.onNodeWithTag("song_action_favorite").performClick()
        rule.runOnIdle {
            assertEquals("first", controller.favoriteId)
            assertFalse(controller.state.value.favoriteSongIds.contains("first"))
            assertEquals(listOf("first", "last"), controller.state.value.playback.queue.map { it.id })
        }
        rule.onNodeWithTag("song_actions_sheet").assertDoesNotExist()
    }

    @Test fun addFromSongMenuSendsTheSelectedPlaylistAndSongIds() {
        render(fixture(MainDestination.SEARCH))
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
        rule.onNodeWithTag("search_type_albums").performClick()
        rule.onNodeWithTag("browse_album_album-blue").assertExists()
        rule.onNodeWithTag("search_type_artists").performClick()
        rule.onNodeWithTag("browse_artist_The Slow Hours").assertExists()
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
        rule.onNodeWithTag("home_history").performClick()
        rule.onNodeWithTag("history_remove_first").performClick()
        rule.runOnIdle { assertEquals("first", controller.removedHistory); assertEquals(2, controller.state.value.playback.queue.size) }
    }

    @Test fun metadataOnlyHistoryRemainsClickableForFreshResolution() {
        render(fixture(MainDestination.HOME).copy(playHistory = listOf(songs[1])))
        rule.onNodeWithTag("home_history").performClick()
        rule.onNodeWithTag("history_song_unavailable").performClick()
        rule.runOnIdle { assertEquals("unavailable", controller.historyPlayed) }
    }

    @Test fun continueListeningIncludesOnlyTheOnePlayedTrackFromALargeQueue() {
        val queue = listOf(songs.first()) + (0 until 29).map { Song("queued-$it", "Queued $it", audioUrl="https://fixture.invalid/$it.mp3") }
        render(fixture(MainDestination.HOME).copy(playback=PlaybackSnapshot(songs.first(),queue,0,true),playHistory=listOf(songs.first())))
        rule.onNodeWithTag("home_feed").performScrollToNode(hasTestTag("home_history_song_first"))
        rule.onNodeWithTag("home_history_song_queued-0").assertDoesNotExist()
        rule.onNodeWithTag("home_history_song_first").performClick()
        rule.runOnIdle { assertEquals("first", controller.historyPlayed); assertEquals(30,controller.state.value.playback.queue.size) }
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
        render(fixture(MainDestination.SEARCH))
        screenshot("listening-search-wide")
        rule.onNodeWithTag("song_more_first").performClick()
        rule.onNodeWithTag("song_action_favorite").assertIsDisplayed()
        screenshot("listening-song-actions-wide")
    }

    @Test @Config(qualifiers = "zh-rCN-w360dp-h740dp-night-mdpi")
    fun renderLargeTextPlaylistAndAccessibleControls() {
        render(fixture().copy(selectedPlaylist = regular, selectedPlaylistEntries = songs.map { PlaylistSong(it) }, playlistSongs = songs.filter { it.canPlay }), largeText = true)
        rule.onNodeWithTag("playlist_add_songs").performScrollTo().assertIsDisplayed()
        screenshot("listening-playlist-large")
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
    val pickerOwners = mutableListOf<String?>()
    var queueClears = 0
    var query = ""; var artistQuery = ""; var albumQuery = ""; var pickerQuery = ""
    private fun success(update: (ClientUiState)->ClientUiState = { it }) { state.value = update(state.value).copy(libraryMutationVersion = state.value.libraryMutationVersion + 1) }
    override fun createPlaylist(name:String,description:String,songIds:List<String>) {
        created = Triple(name,description,songIds)
        success { it.copy(playlists = it.playlists + PlaylistSummary("created",name,"regular",description)) }
    }
    override fun toggleFavorite(song:Song) { favoriteId=song.id; success { it.copy(favoriteSongIds = it.favoriteSongIds - song.id) } }
    override fun addPlaylistSongs(playlistIds:List<String>,songIds:List<String>) { added=playlistIds to songIds; success() }
    override fun replacePlaylistOrder(songIds:List<String>) { order=songIds; orderWrites++; success { it.copy(selectedPlaylistEntries=songIds.map { id->PlaylistSong(songs.first { it.id==id }) }) } }
    override fun deletePlaylist(playlist:PlaylistSummary) { deleted=playlist.id; success { it.copy(playlists=it.playlists.filterNot { row->row.id==playlist.id },selectedPlaylist=null,selectedPlaylistEntries=emptyList()) } }
    override fun replacePlaylistShelfOrder(ids:List<String>) { shelf=ids; success() }
    override fun searchPlaylistSongs(query:String) { pickerQuery=query; pickerOwners += state.value.selectedPlaylist?.id
        state.value=state.value.copy(pickerQuery=query,pickerSongs=if(state.value.selectedPlaylist!=null) songs.filter { it.canPlay } else emptyList()) }
    override fun playHistorySong(song:Song) { historyPlayed=song.id }
    override fun refreshArtists(query:String) { artistQuery=query }
    override fun searchAlbums(query:String) { albumQuery=query }
    override fun enqueue(songs:List<Song>) { enqueued=songs.map { it.id } }
    override fun removeHistory(songId:String) { removedHistory=songId; state.value=state.value.copy(playHistory=state.value.playHistory.filterNot { it.id==songId }) }
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
