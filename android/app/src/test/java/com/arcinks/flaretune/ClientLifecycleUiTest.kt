package com.arcinks.flaretune

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.arcinks.flaretune.model.*
import com.arcinks.flaretune.ui.*
import com.arcinks.flaretune.ui.theme.FlareTuneTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClientLifecycleUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val song = Song("track", "Track", audioUrl = "https://fixture.invalid/song.mp3")
    private val fixture = ClientUiState(screen = ClientScreen.LIBRARY, navigationIdentity = "owner-one",
        playback = PlaybackSnapshot(current = song, queue = listOf(song), currentIndex = 0))

    @Test fun savedFullScreenPlayerSurvivesTheIntermediateRestoringScreen() {
        val tester = StateRestorationTester(rule)
        var restoreNextComposition = false
        var complete by mutableStateOf(false)
        tester.setContent {
            val current = if (restoreNextComposition && !complete) ClientUiState() else fixture
            FlareTuneTheme { ClientApp(current, lifecycleActions) }
        }
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_play").assertExists()
        restoreNextComposition = true
        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("restoring").assertExists()
        rule.runOnIdle { complete = true }
        rule.onNodeWithTag("player_play").assertExists()
    }

    @Test fun restoredEmptyQueueClosesHiddenPanelBeforeANewSongIsPlayed() {
        val tester = StateRestorationTester(rule)
        var restoreNext = false
        var complete by mutableStateOf(false)
        var state by mutableStateOf(fixture)
        tester.setContent { FlareTuneTheme {
            ClientApp(if (restoreNext && !complete) ClientUiState() else state, lifecycleActions)
        } }
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_play").assertExists()
        restoreNext = true
        tester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            state = fixture.copy(playback = PlaybackSnapshot(), playbackReady = false, queueRestoring = true)
            complete = true
        }
        rule.runOnIdle { state = state.copy(playbackReady = true, queueRestoring = false) }
        rule.runOnIdle { state = fixture }
        rule.onNodeWithTag("mini_player").assertExists()
        rule.onNodeWithTag("player_play").assertDoesNotExist()
    }

    @Test fun anEmptyIntermediatePlayerDoesNotEraseTheSavedPanelWhileQueueRestores() {
        val tester = StateRestorationTester(rule)
        var restoreNext = false
        var complete by mutableStateOf(false)
        var state by mutableStateOf(fixture)
        tester.setContent { FlareTuneTheme {
            ClientApp(if (restoreNext && !complete) ClientUiState() else state, lifecycleActions)
        } }
        rule.onNodeWithTag("mini_player").performClick()
        restoreNext = true
        tester.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            state = fixture.copy(playback = PlaybackSnapshot(), playbackReady = false)
            complete = true
        }
        rule.runOnIdle { state = state.copy(playbackReady = true, queueRestoring = true) }
        rule.runOnIdle { state = fixture }
        rule.onNodeWithTag("player_play").assertExists()
    }

    @Test fun mixedSameNameArtistCardsKeepTheirRealIdentity() {
        var opened: Artist? = null
        val actions = object : ClientActions by lifecycleActions {
            override fun openArtist(artist: Artist) { opened = artist }
        }
        val local = Artist("Same name", songCount = 4, albumCount = 2)
        val external = Artist("Same name", id = "yt:artist:UCfixture", knownSongCount = null, knownAlbumCount = null)
        val state = fixture.copy(destination = MainDestination.SEARCH, query = "same", artists = listOf(local, external))
        rule.setContent { FlareTuneTheme { ClientApp(state, actions) } }
        rule.onAllNodesWithTag("browse_artist_Same name").assertCountEquals(2)
        rule.onAllNodesWithTag("browse_artist_Same name")[1].performClick()
        rule.runOnIdle { org.junit.Assert.assertEquals("yt:artist:UCfixture", opened?.id) }
    }

    @Test fun aDifferentAccountAndAnActuallyClearedQueueCloseTheOldPlayer() {
        val state = mutableStateOf(fixture)
        rule.setContent { FlareTuneTheme { ClientApp(state.value, lifecycleActions) } }
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_play").assertExists()
        rule.runOnIdle { state.value = fixture.copy(navigationIdentity = "owner-two") }
        rule.onNodeWithTag("mini_player").assertExists()
        rule.onNodeWithTag("mini_player").performClick()
        rule.onNodeWithTag("player_play").assertExists()
        rule.runOnIdle { state.value = state.value.copy(playback = PlaybackSnapshot()) }
        rule.onNodeWithTag("player_play").assertDoesNotExist()
    }
}

private val lifecycleActions = object : ClientActions {
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
