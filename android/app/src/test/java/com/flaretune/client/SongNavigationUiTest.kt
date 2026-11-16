package com.flaretune.client

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SongNavigationUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val albumId = "yt:album:MPREb_fixture"
    private val track = Song("yt:abcdefghijk", "Selected recording", "Singer", "Selected release", 212.0,
        "https://fixture.invalid/audio", source = "youtube", albumId = albumId,
        artists = listOf(SongArtist("yt:artist:UCfixture", "Singer")))
    private val album = Album(albumId, "Selected release", "Singer")
    private fun render(song: Song = track, selected: Album? = null, actions: ClientActions) {
        val state = ClientUiState(screen = ClientScreen.LIBRARY, navigationIdentity = "owner",
            selectedAlbum = selected, albumSongs = if (selected == null) emptyList() else listOf(song),
            artists = listOf(Artist("Singer")), albums = listOf(Album("local-album", song.album, "Singer")),
            playback = PlaybackSnapshot(current = song, queue = listOf(song), currentIndex = 0))
        rule.setContent { FlareTuneTheme { ClientApp(state, actions) } }
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_more").performClick()
    }

    @Test fun albumIntentClosesPlayerEvenWhenDestinationIsAlreadyOpen() {
        var opened: Album? = null
        var searches = 0
        val actions = object : ClientActions by noSongNavigationActions {
            override fun openAlbum(album: Album) { opened = album }
            override fun searchAlbums(query: String) { searches++ }
        }
        render(selected = album, actions = actions)
        rule.onNodeWithTag("song_action_album").performScrollTo().performClick()
        rule.onNodeWithTag("player_play").assertDoesNotExist()
        rule.runOnIdle { assertEquals(albumId, opened?.id); assertEquals(0, searches) }
    }

    @Test fun externalArtistKeepsItsSourceReferenceDespiteSameNamedLocalArtist() {
        var opened: Artist? = null
        val actions = object : ClientActions by noSongNavigationActions {
            override fun openArtist(artist: Artist) { opened = artist }
        }
        render(actions = actions)
        rule.onNodeWithTag("song_action_artist").performScrollTo().performClick()
        rule.onNodeWithTag("player_play").assertDoesNotExist()
        rule.runOnIdle { assertEquals("yt:artist:UCfixture", opened?.id) }
    }

    @Test fun realAlbumReferenceIsAvailableWithoutADisplayAlbumName() {
        var opened: Album? = null
        val actions = object : ClientActions by noSongNavigationActions {
            override fun openAlbum(album: Album) { opened = album }
        }
        render(track.copy(album = ""), actions = actions)
        rule.onNodeWithTag("song_action_album").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(albumId, opened?.id) }
        rule.onNodeWithTag("player_play").assertDoesNotExist()
    }

    @Test fun missingExternalReferencesNeverOfferUnrelatedLocalDestinations() {
        render(track.copy(albumId = null, artists = emptyList()), actions = noSongNavigationActions)
        rule.onNodeWithTag("song_action_artist").assertDoesNotExist()
        rule.onNodeWithTag("song_action_album").assertDoesNotExist()
        rule.onNodeWithTag("song_action_favorite").assertExists()
    }

    @Test fun collaboratorChoicePreservesEachRealArtistId() {
        var opened: Artist? = null
        val actions = object : ClientActions by noSongNavigationActions {
            override fun openArtist(artist: Artist) { opened = artist }
        }
        render(track.copy(artists = track.artists + SongArtist("yt:artist:UCguest", "Guest")), actions = actions)
        rule.onNodeWithTag("song_action_artist").performScrollTo().performClick()
        rule.onNodeWithTag("artist_chooser_yt:artist:UCguest").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("yt:artist:UCguest", opened?.id) }
        rule.onNodeWithTag("player_play").assertDoesNotExist()
    }
}

private val noSongNavigationActions = object : ClientActions {
    override fun navigate(destination: MainDestination) {}
    override fun loadMoreAlbums() {}
    override fun openAlbum(album: Album) {}
    override fun closeAlbum() {}
    override fun setVolume(volume: Float) {}
    override fun login(instanceUrl: String, username: String, password: String) {}
    override fun changePassword(currentPassword: String, newPassword: String) {}
    override fun logout() {}
    override fun search(query: String) {}
    override fun selectTab(tab: LibraryTab) {}
    override fun refresh() {}
    override fun loadMore() {}
    override fun playSong(song: Song) {}
    override fun togglePlay() {}
    override fun next() {}
    override fun previous() {}
    override fun seekTo(positionMs: Long) {}
    override fun toggleShuffle() {}
    override fun cycleRepeat() {}
    override fun selectQueueItem(index: Int) {}
    override fun retryPlayback() {}
}
