package com.flaretune.client

import android.app.Application
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.flaretune.client.data.AppLanguage
import com.flaretune.client.data.AppThemeMode
import com.flaretune.client.data.BluetoothLyricsMode
import com.flaretune.client.data.SavedAccountSummary
import com.flaretune.client.model.Album
import com.flaretune.client.model.PlayStats
import com.flaretune.client.model.RankedSong
import com.flaretune.client.model.Song
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.flaretune.client.playback.SleepTimerMode
import com.flaretune.client.playback.SleepTimerState
import com.flaretune.client.ui.ClientActions
import com.flaretune.client.ui.ClientApp
import com.flaretune.client.ui.ClientScreen
import com.flaretune.client.ui.ClientUiState
import com.flaretune.client.ui.ImageCacheClearResult
import com.flaretune.client.ui.LibraryTab
import com.flaretune.client.ui.MainDestination
import com.flaretune.client.ui.NowPlayingScreen
import com.flaretune.client.ui.PlaybackSnapshot
import com.flaretune.client.ui.theme.FlareTuneTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi", application = Application::class)
class SidebarAndSettingsUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val track = Song(
        id = "track-1",
        title = "Midnight Drift",
        artist = "FlareTune",
        album = "Night Waves",
        durationSeconds = 210.0,
        audioUrl = "https://fixture.invalid/track-1.mp3",
        coverUrl = null,
    )

    private val accounts = listOf(
        SavedAccountSummary(
            accountKey = "acc-main",
            origin = "https://music.example.com",
            accountId = "u1",
            username = "alice",
            displayName = "Alice",
            active = true,
            needsReauth = false,
        ),
        SavedAccountSummary(
            accountKey = "acc-alt",
            origin = "https://alt.example.org",
            accountId = "u2",
            username = "bob",
            displayName = "Bob Studio",
            active = false,
            needsReauth = false,
        ),
    )

    private class FakeSidebarActions : ClientActions {
        var switchedTo: String? = null
        var removedAccount: String? = null
        var addedAccountCalls = 0
        var cancelledAddCalls = 0
        var loggedOutCalls = 0
        var sleepMinutesSet: Int? = null
        var sleepEndOfTrackCalls = 0
        var sleepFinishCurrentTrackSet: Boolean? = null
        var sleepCancelledCalls = 0
        var keepScreenOnSet: Boolean? = null
        var themeModeSet: AppThemeMode? = null
        var appLanguageSet: AppLanguage? = null
        var bluetoothLyricsModeSet: BluetoothLyricsMode? = null
        var translationSet: Boolean? = null
        var cacheClearCalls = 0
        var refreshCalls = 0
        var updatedNickname: String? = null
        var clearedAvatarCalls = 0

        override fun switchAccount(accountKey: String) {
            switchedTo = accountKey
        }

        override fun removeSavedAccount(accountKey: String) {
            removedAccount = accountKey
        }

        override fun beginAddAccount() {
            addedAccountCalls++
        }

        override fun cancelAddAccount() {
            cancelledAddCalls++
        }

        override fun logout() {
            loggedOutCalls++
        }

        override fun setSleepTimer(minutes: Int) {
            sleepMinutesSet = minutes
        }

        override fun setSleepTimerEndOfTrack() {
            sleepEndOfTrackCalls++
        }

        override fun setSleepTimerFinishCurrentTrack(enabled: Boolean) {
            sleepFinishCurrentTrackSet = enabled
        }

        override fun cancelSleepTimer() {
            sleepCancelledCalls++
        }

        override fun setKeepScreenOnEnabled(enabled: Boolean) {
            keepScreenOnSet = enabled
        }

        override fun setThemeMode(mode: AppThemeMode) {
            themeModeSet = mode
        }

        override fun setAppLanguage(language: AppLanguage) {
            appLanguageSet = language
        }

        override fun setBluetoothLyricsMode(mode: BluetoothLyricsMode) {
            bluetoothLyricsModeSet = mode
        }

        override fun setTranslationEnabled(enabled: Boolean) {
            translationSet = enabled
        }

        override fun clearImageCache() {
            cacheClearCalls++
        }

        override fun refresh() {
            refreshCalls++
        }

        override fun updateDisplayName(displayName: String) {
            updatedNickname = displayName
        }

        override fun clearAccountAvatar() {
            clearedAvatarCalls++
        }

        override fun navigate(destination: MainDestination) {}
        override fun loadMoreAlbums() {}
        override fun openAlbum(album: Album) {}
        override fun closeAlbum() {}
        override fun setVolume(volume: Float) {}
        override fun login(instanceUrl: String, username: String, password: String) {}
        override fun changePassword(currentPassword: String, newPassword: String) {}
        override fun search(query: String) {}
        override fun selectTab(tab: LibraryTab) {}
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

    @Test
    @Config(qualifiers = "w1024dp-h720dp-mdpi")
    fun tabletDrawerPushesShellRightwardAndSwitchesOrAddsAccount() {
        val actions = FakeSidebarActions()
        val sleepFlow = MutableStateFlow(SleepTimerState())
        rule.setContent {
            FlareTuneTheme {
                ClientApp(
                    state = ClientUiState(
                        screen = ClientScreen.LIBRARY,
                        destination = MainDestination.HOME,
                        instanceUrl = "https://music.example.com",
                        username = "alice",
                        accountName = "Alice",
                        savedAccounts = accounts,
                        songs = listOf(track),
                    ),
                    actions = actions,
                    sleepTimerFlow = sleepFlow,
                )
            }
        }

        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("account_drawer").assertIsDisplayed()
        rule.onNodeWithTag("drawer_header").assertIsDisplayed()
        rule.onNodeWithTag("drawer_scrim").assertIsDisplayed()

        val drawerBounds = rule.onNodeWithTag("account_drawer").getBoundsInRoot()
        val scrimBounds = rule.onNodeWithTag("drawer_scrim").getBoundsInRoot()
        assertTrue(scrimBounds.left >= drawerBounds.right - 2.dp)

        rule.onNodeWithTag("drawer_account_acc-alt").performClick()
        rule.waitForIdle()
        assertEquals("acc-alt", actions.switchedTo)

        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_add_account").performClick()
        rule.waitForIdle()
        assertEquals(1, actions.addedAccountCalls)
    }

    @Test
    fun sleepTimerSheetSetsDurationEndOfTrackAndCancelsTimer() {
        val actions = FakeSidebarActions()
        val sleepFlow = MutableStateFlow(
            SleepTimerState(
                mode = SleepTimerMode.TIMED,
                endElapsedRealtimeMs = SystemClock.elapsedRealtime() + 20 * 60_000L,
            ),
        )
        rule.setContent {
            FlareTuneTheme {
                ClientApp(
                    state = ClientUiState(
                        screen = ClientScreen.LIBRARY,
                        destination = MainDestination.HOME,
                        instanceUrl = "https://music.example.com",
                        username = "alice",
                        accountName = "Alice",
                        savedAccounts = accounts,
                        songs = listOf(track),
                    ),
                    actions = actions,
                    sleepTimerFlow = sleepFlow,
                )
            }
        }

        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_sleep_timer").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("sleep_timer_sheet").assertIsDisplayed()
        rule.onNodeWithTag("sleep_timer_30").performClick()
        rule.waitForIdle()
        assertEquals(30, actions.sleepMinutesSet)

        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_sleep_timer").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("sleep_timer_custom").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("sleep_timer_custom_input").performTextInput("75")
        rule.onNodeWithTag("sleep_timer_custom_confirm").performClick()
        rule.waitForIdle()
        assertEquals(75, actions.sleepMinutesSet)

        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_sleep_timer").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("sleep_timer_end_of_track").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(true, actions.sleepFinishCurrentTrackSet)

        rule.onNodeWithTag("sleep_timer_off").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, actions.sleepCancelledCalls)
    }

    @Test
    fun sleepTimerDisablesEndOfTrackSwitchWhenIdleAndUnscheduled() {
        val actions = FakeSidebarActions()
        val sleepFlow = MutableStateFlow(SleepTimerState())
        rule.setContent {
            FlareTuneTheme {
                ClientApp(
                    state = ClientUiState(
                        screen = ClientScreen.LIBRARY,
                        destination = MainDestination.HOME,
                        instanceUrl = "https://music.example.com",
                        username = "alice",
                        accountName = "Alice",
                        savedAccounts = accounts,
                        songs = listOf(track),
                    ),
                    actions = actions,
                    sleepTimerFlow = sleepFlow,
                )
            }
        }

        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_sleep_timer").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("sleep_timer_end_of_track").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("sleep_timer_end_of_track").performClick()
        rule.waitForIdle()
        assertNull(actions.sleepFinishCurrentTrackSet)
    }

    @Test
    fun settingsScreenManagesAccountsPreferencesCacheAndSignOut() {
        val actions = FakeSidebarActions()
        var uiState by mutableStateOf(
            ClientUiState(
                screen = ClientScreen.LIBRARY,
                destination = MainDestination.HOME,
                instanceUrl = "https://music.example.com",
                username = "alice",
                accountName = "Alice",
                savedAccounts = accounts,
                translationEnabled = true,
                keepScreenOnEnabled = true,
                songs = listOf(track),
            ),
        )
        rule.setContent {
            FlareTuneTheme {
                ClientApp(state = uiState, actions = actions)
            }
        }

        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_settings").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("settings_screen").assertIsDisplayed()
        fun scrollAboveDockAndClick(tag: String) {
            rule.onNodeWithTag(tag).performScrollTo()
            rule.waitForIdle()
            val dockTop = rule.onAllNodesWithTag("dock_compact")
                .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
                ?: rule.onAllNodesWithTag("dock_wide")
                    .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
            if (dockTop != null) {
                val nodeCenterY = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center.y
                if (nodeCenterY >= dockTop - 8f) {
                    val shift = (nodeCenterY - dockTop + 72f).coerceAtLeast(80f)
                    rule.onNodeWithTag("settings_scroll_content").performTouchInput {
                        down(center)
                        moveBy(androidx.compose.ui.geometry.Offset(0f, -shift))
                        up()
                    }
                    rule.waitForIdle()
                }
            }
            rule.onNodeWithTag(tag).performClick()
            rule.waitForIdle()
        }
        scrollAboveDockAndClick("settings_theme_dark")
        assertEquals(AppThemeMode.DARK, actions.themeModeSet)
        scrollAboveDockAndClick("settings_theme_light")
        assertEquals(AppThemeMode.LIGHT, actions.themeModeSet)
        scrollAboveDockAndClick("settings_theme_system")
        assertEquals(AppThemeMode.SYSTEM, actions.themeModeSet)

        rule.onNodeWithText(rule.activity.getString(R.string.ft_settings_language_title)).assertIsDisplayed()
        scrollAboveDockAndClick("settings_language_ja")
        assertEquals(AppLanguage.JA, actions.appLanguageSet)
        scrollAboveDockAndClick("settings_language_zh_hant")
        assertEquals(AppLanguage.ZH_HANT, actions.appLanguageSet)
        scrollAboveDockAndClick("settings_language_system")
        assertEquals(AppLanguage.SYSTEM, actions.appLanguageSet)

        scrollAboveDockAndClick("settings_keep_screen_on_switch")
        assertEquals(false, actions.keepScreenOnSet)

        scrollAboveDockAndClick("settings_translation_switch")
        assertEquals(false, actions.translationSet)

        scrollAboveDockAndClick("settings_clear_cache")
        assertEquals(1, actions.cacheClearCalls)
        uiState = uiState.copy(imageCacheClearResult = ImageCacheClearResult.CLEARED)
        rule.waitForIdle()
        rule.onNodeWithText(rule.activity.getString(R.string.ft_settings_clear_cover_cache_done)).assertIsDisplayed()

        scrollAboveDockAndClick("settings_remove_account_acc-alt")
        rule.onNodeWithTag("settings_confirm_remove_account").performClick()
        rule.waitForIdle()
        assertEquals("acc-alt", actions.removedAccount)

        scrollAboveDockAndClick("sign_out")
        assertEquals(1, actions.loggedOutCalls)
    }

    @Test
    fun connectScreenSupportsAddingAccountCancelAndQuickAccountSwitch() {
        val actions = FakeSidebarActions()
        var uiState by mutableStateOf(
            ClientUiState(
                screen = ClientScreen.CONNECT,
                instanceUrl = "https://music.example.com",
                addingAccount = true,
                savedAccounts = accounts,
            ),
        )
        rule.setContent {
            FlareTuneTheme {
                ClientApp(state = uiState, actions = actions)
            }
        }

        rule.onNodeWithTag("connect_cancel").assertIsDisplayed().performClick()
        assertEquals(1, actions.cancelledAddCalls)

        uiState = ClientUiState(
            screen = ClientScreen.CONNECT,
            instanceUrl = "https://music.example.com",
            username = "alice",
            addingAccount = false,
            savedAccounts = accounts,
        )
        rule.waitForIdle()
        rule.onNodeWithTag("connect_saved_acc-alt").performScrollTo().performClick()
        assertEquals("acc-alt", actions.switchedTo)
    }

    @Test
    fun nowPlayingRespectsKeepScreenOnEnabledPreference() {
        val actions = FakeSidebarActions()
        var keepEnabled by mutableStateOf(true)
        var capturedView: android.view.View? = null
        rule.setContent {
            FlareTuneTheme {
                capturedView = LocalView.current
                NowPlayingScreen(
                    state = PlaybackSnapshot(current = track, queue = listOf(track), currentIndex = 0, playing = true),
                    keepScreenOnEnabled = keepEnabled,
                    actions = actions,
                    imageLoader = null,
                    onClose = {},
                    onQueue = {},
                )
            }
        }

        rule.waitForIdle()
        assertTrue(capturedView!!.keepScreenOn)

        keepEnabled = false
        rule.waitForIdle()
        assertFalse(capturedView!!.keepScreenOn)
    }

    @Test
    fun listeningStatsOpensAsStandalonePageFromDrawerAndHomeCard() {
        val statSong = Song(
            "stat-1",
            "Midnight Signal",
            "Solo Artist",
            "Night Album",
            210.0,
            "https://fixture.invalid/stat1.mp3",
            null,
        )
        val uiState = ClientUiState(
            screen = ClientScreen.LIBRARY,
            destination = MainDestination.HOME,
            instanceUrl = "https://music.example.com",
            username = "alice",
            accountName = "Alice",
            savedAccounts = accounts,
            songs = listOf(statSong),
            playStats = PlayStats(
                songs = listOf(RankedSong(statSong, playCount = 7, lastPlayedAt = 1_000L)),
                totalPlays = 7,
                totalUniqueSongs = 1,
            ),
        )
        rule.setContent {
            FlareTuneTheme {
                ClientApp(state = uiState, actions = FakeSidebarActions())
            }
        }

        assertTrue(rule.onAllNodesWithTag("listening_stats").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_stats").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("listening_stats").assertIsDisplayed()
        rule.onNodeWithTag("stats_song_stat-1").assertIsDisplayed()
        rule.onNodeWithTag("stats_tab_artists").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("stats_artist_Solo Artist").assertIsDisplayed()

        rule.onNodeWithTag("stats_close").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("listening_stats").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithTag("home_featured_cards").performScrollToNode(hasTestTag("home_stats_card"))
        rule.onNodeWithTag("home_stats_card").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("listening_stats").assertIsDisplayed()
        rule.onNodeWithTag("stats_song_stat-1").assertIsDisplayed()
        rule.onNodeWithTag("stats_close").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("listening_stats").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun settingsProfileEditsNicknameAndOpensFullScreenAvatarViewer() {
        val actions = FakeSidebarActions()
        val uiState = ClientUiState(
            screen = ClientScreen.LIBRARY,
            destination = MainDestination.HOME,
            instanceUrl = "https://music.example.com",
            username = "alice",
            accountName = "Alice",
            avatarUrl = "https://music.example.com/api/account/images/avatar-1",
            savedAccounts = accounts,
            songs = listOf(track),
        )
        rule.setContent {
            FlareTuneTheme {
                ClientApp(state = uiState, actions = actions)
            }
        }

        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_settings").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("settings_edit_nickname").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("edit_nickname_dialog").assertIsDisplayed()
        rule.onNodeWithTag("edit_nickname_input").performTextReplacement("Alice New")
        rule.onNodeWithTag("edit_nickname_confirm").performClick()
        rule.waitForIdle()
        assertEquals("Alice New", actions.updatedNickname)

        assertTrue(rule.onAllNodesWithTag("settings_change_avatar").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("settings_view_avatar").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("settings_remove_avatar").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithTag("settings_profile_avatar").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("avatar_viewer_dialog").assertIsDisplayed()
        rule.onNodeWithTag("avatar_viewer_change").assertIsDisplayed()
        rule.onNodeWithTag("avatar_viewer_save").assertIsDisplayed()
        rule.onNodeWithTag("avatar_viewer_close").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("avatar_viewer_dialog").fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(qualifiers = "w1024dp-h720dp-mdpi")
    fun tabletSettingsScreenKeepsFloatingDockAndScrollsContentBehindDock() {
        val actions = FakeSidebarActions()
        val uiState = ClientUiState(
            screen = ClientScreen.LIBRARY,
            destination = MainDestination.HOME,
            instanceUrl = "https://music.example.com",
            username = "alice",
            accountName = "Alice",
            savedAccounts = accounts,
            songs = listOf(track),
            playback = PlaybackSnapshot(track, listOf(track), 0, playing = true),
        )
        rule.setContent {
            FlareTuneTheme {
                ClientApp(state = uiState, actions = actions)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_settings").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("dock_wide").assertIsDisplayed()
        val dockBounds = rule.onNodeWithTag("dock_wide").fetchSemanticsNode().boundsInRoot
        val scrollBounds = rule.onNodeWithTag("settings_scroll_content").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Settings scroll viewport must extend behind floating dock (scrollBottom=${scrollBounds.bottom}, dockTop=${dockBounds.top})",
            scrollBounds.bottom > dockBounds.top,
        )
    }

    @Test
    fun settingsScreenBluetoothLyricsSelectorSupportsThreeStatesAnd200PercentFontScale() {
        val actions = FakeSidebarActions()
        var currentMode by mutableStateOf(BluetoothLyricsMode.OFF)
        val baseState = ClientUiState(
            screen = ClientScreen.LIBRARY,
            destination = MainDestination.HOME,
            instanceUrl = "https://music.example.com",
            username = "alice",
            accountName = "Alice",
            savedAccounts = accounts,
            songs = listOf(track),
            playback = PlaybackSnapshot(track, listOf(track), 0, playing = true),
        )
        rule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2.0f)) {
                FlareTuneTheme {
                    ClientApp(
                        state = baseState.copy(bluetoothLyricsMode = currentMode),
                        actions = object : ClientActions by actions {
                            override fun setBluetoothLyricsMode(mode: BluetoothLyricsMode) {
                                actions.setBluetoothLyricsMode(mode)
                                currentMode = mode
                            }
                        },
                    )
                }
            }
        }

        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_settings").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("ft_settings_section_playback").performScrollTo()
        rule.onNodeWithTag("settings_bluetooth_lyrics_off").performScrollTo().assertIsDisplayed().assertIsSelected()
        rule.onNodeWithTag("settings_bluetooth_lyrics_bluetooth_audio").performScrollTo().assertIsDisplayed().assertIsNotSelected()
        rule.onNodeWithTag("settings_bluetooth_lyrics_always").performScrollTo().assertIsDisplayed().assertIsNotSelected()

        val res = rule.activity.resources
        rule.onNodeWithText(res.getString(R.string.ft_settings_bluetooth_lyrics_title)).assertIsDisplayed()
        rule.onNodeWithText(res.getString(R.string.ft_settings_bluetooth_lyrics_desc)).assertIsDisplayed()
        rule.onNodeWithText(res.getString(R.string.ft_settings_bluetooth_lyrics_off)).assertIsDisplayed()
        rule.onNodeWithText(res.getString(R.string.ft_settings_bluetooth_lyrics_bluetooth_audio)).assertIsDisplayed()
        rule.onNodeWithText(res.getString(R.string.ft_settings_bluetooth_lyrics_always)).assertIsDisplayed()

        listOf(
            "settings_bluetooth_lyrics_off",
            "settings_bluetooth_lyrics_bluetooth_audio",
            "settings_bluetooth_lyrics_always",
        ).forEach { tag ->
            val bounds = rule.onNodeWithTag(tag).getBoundsInRoot()
            assertTrue("Expected $tag height >= 48.dp at 200% fontScale, was ${bounds.height}", bounds.height >= 48.dp)
        }

        rule.onNodeWithTag("settings_bluetooth_lyrics_bluetooth_audio").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(BluetoothLyricsMode.BLUETOOTH_AUDIO, actions.bluetoothLyricsModeSet)
        rule.onNodeWithTag("settings_bluetooth_lyrics_bluetooth_audio").assertIsSelected()

        rule.onNodeWithTag("settings_bluetooth_lyrics_always").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(BluetoothLyricsMode.ALWAYS, actions.bluetoothLyricsModeSet)
        rule.onNodeWithTag("settings_bluetooth_lyrics_always").assertIsSelected()
    }
}
