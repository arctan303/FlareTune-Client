package com.arcinks.flaretune

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
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import androidx.compose.ui.unit.width
import com.arcinks.flaretune.data.AppLanguage
import com.arcinks.flaretune.data.AppThemeMode
import com.arcinks.flaretune.data.BluetoothLyricsMode
import com.arcinks.flaretune.data.SavedAccountSummary
import com.arcinks.flaretune.model.Album
import com.arcinks.flaretune.model.PlayStats
import com.arcinks.flaretune.model.RankedSong
import com.arcinks.flaretune.model.Song
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.arcinks.flaretune.playback.SleepTimerMode
import com.arcinks.flaretune.playback.SleepTimerState
import com.arcinks.flaretune.ui.ClientActions
import com.arcinks.flaretune.ui.ClientApp
import com.arcinks.flaretune.ui.ClientScreen
import com.arcinks.flaretune.ui.ClientUiState
import com.arcinks.flaretune.ui.ImageCacheClearResult
import com.arcinks.flaretune.ui.LibraryTab
import com.arcinks.flaretune.ui.MainDestination
import com.arcinks.flaretune.ui.NowPlayingScreen
import com.arcinks.flaretune.ui.PlaybackSnapshot
import com.arcinks.flaretune.ui.theme.FlareTuneTheme
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
        scrollAboveDockAndClick("settings_category_appearance")
        scrollAboveDockAndClick("settings_theme_trigger")
        rule.onNodeWithTag("settings_theme_dark").performClick()
        rule.waitForIdle()
        assertEquals(AppThemeMode.DARK, actions.themeModeSet)
        scrollAboveDockAndClick("settings_theme_trigger")
        rule.onNodeWithTag("settings_theme_light").performClick()
        rule.waitForIdle()
        assertEquals(AppThemeMode.LIGHT, actions.themeModeSet)
        scrollAboveDockAndClick("settings_theme_trigger")
        rule.onNodeWithTag("settings_theme_system").performClick()
        rule.waitForIdle()
        assertEquals(AppThemeMode.SYSTEM, actions.themeModeSet)

        rule.onNodeWithTag("settings_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_screen").assertIsDisplayed()

        scrollAboveDockAndClick("settings_category_general")
        rule.onNodeWithText(rule.activity.getString(R.string.ft_settings_language_title)).assertIsDisplayed()
        scrollAboveDockAndClick("settings_language_trigger")
        rule.onNodeWithTag("settings_language_ja").performClick()
        rule.waitForIdle()
        assertEquals(AppLanguage.JA, actions.appLanguageSet)
        scrollAboveDockAndClick("settings_language_trigger")
        rule.onNodeWithTag("settings_language_zh_hant").performClick()
        rule.waitForIdle()
        assertEquals(AppLanguage.ZH_HANT, actions.appLanguageSet)
        scrollAboveDockAndClick("settings_language_trigger")
        rule.onNodeWithTag("settings_language_system").performClick()
        rule.waitForIdle()
        assertEquals(AppLanguage.SYSTEM, actions.appLanguageSet)

        rule.onNodeWithTag("settings_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_screen").assertIsDisplayed()

        scrollAboveDockAndClick("settings_category_playback")
        scrollAboveDockAndClick("settings_keep_screen_on_switch")
        assertEquals(false, actions.keepScreenOnSet)

        scrollAboveDockAndClick("settings_translation_switch")
        assertEquals(false, actions.translationSet)

        rule.onNodeWithTag("settings_back").performClick()
        rule.waitForIdle()

        scrollAboveDockAndClick("settings_category_storage")
        scrollAboveDockAndClick("settings_clear_cache")
        assertEquals(1, actions.cacheClearCalls)
        uiState = uiState.copy(imageCacheClearResult = ImageCacheClearResult.CLEARED)
        rule.waitForIdle()
        rule.onNodeWithText(rule.activity.getString(R.string.ft_settings_clear_cover_cache_done)).assertIsDisplayed()

        rule.onNodeWithTag("settings_back").performClick()
        rule.waitForIdle()

        scrollAboveDockAndClick("settings_category_profile_accounts")
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
        rule.onNodeWithTag("settings_category_profile_accounts").performScrollTo().performClick()
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
    fun tabletSettingsScreenHidesFloatingDockAndShowsFullHeightDivider() {
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

        assertTrue(rule.onAllNodesWithTag("dock_wide").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("dock_compact").fetchSemanticsNodes().isEmpty())
        val navBounds = rule.onNodeWithTag("settings_split_nav").assertIsDisplayed().getBoundsInRoot()
        val dividerBounds = rule.onNodeWithTag("settings_split_divider").assertIsDisplayed().getBoundsInRoot()
        val detailBounds = rule.onNodeWithTag("settings_scroll_content").assertIsDisplayed().getBoundsInRoot()
        assertTrue("Split divider must sit right of nav pane", dividerBounds.left >= navBounds.right - 1.dp)
        assertTrue("Detail pane must sit right of split divider", detailBounds.left >= dividerBounds.right - 1.dp)
        assertTrue("Split divider must span down toward bottom of screen", dividerBounds.bottom >= 600.dp)
    }

    @Test
    @Config(qualifiers = "w1024dp-h720dp-mdpi")
    fun tabletSettingsUsesBoundedSplitPaneAndSwitchesCategories() {
        val actions = FakeSidebarActions()
        val uiState = ClientUiState(
            screen = ClientScreen.LIBRARY,
            destination = MainDestination.HOME,
            instanceUrl = "https://music.example.com",
            username = "alice",
            accountName = "Alice",
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

        val navBounds = rule.onNodeWithTag("settings_split_nav").assertIsDisplayed().getBoundsInRoot()
        val dividerBounds = rule.onNodeWithTag("settings_split_divider").assertIsDisplayed().getBoundsInRoot()
        val detailBounds = rule.onNodeWithTag("settings_scroll_content").assertIsDisplayed().getBoundsInRoot()
        assertTrue("Detail pane must sit to the right of nav pane and divider", detailBounds.left >= dividerBounds.right - 1.dp)
        assertTrue("Nav pane width must stay bounded (was ${navBounds.right - navBounds.left})", (navBounds.right - navBounds.left) <= 312.dp)
        assertTrue("Detail pane width must stay bounded (was ${detailBounds.right - detailBounds.left})", (detailBounds.right - detailBounds.left) <= 685.dp)

        rule.onNodeWithTag("settings_category_profile_accounts").assertIsSelected()
        rule.onNodeWithTag("settings_edit_nickname").assertIsDisplayed()

        rule.onNodeWithTag("settings_category_general").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_category_general").assertIsSelected()
        rule.onNodeWithTag("settings_language").assertIsDisplayed()

        rule.onNodeWithTag("settings_category_appearance").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_category_appearance").assertIsSelected()
        rule.onNodeWithTag("settings_theme").assertIsDisplayed()

        rule.onNodeWithTag("settings_category_playback").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_category_playback").assertIsSelected()
        rule.onNodeWithTag("settings_bluetooth_lyrics").assertIsDisplayed()

        rule.onNodeWithTag("settings_category_about").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_category_about").assertIsSelected()
        rule.onNodeWithTag("settings_check_updates").assertIsDisplayed()
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
        rule.onNodeWithTag("settings_category_playback").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("ft_settings_section_playback").performScrollTo()
        val btRowBounds = rule.onNodeWithTag("settings_bluetooth_lyrics").performScrollTo().assertIsDisplayed().getBoundsInRoot()
        assertTrue("Bluetooth lyrics row must not overflow 390dp (right=${btRowBounds.right})", btRowBounds.right <= 390.dp)
        val btTitleBounds = rule.onNodeWithTag("settings_bluetooth_lyrics_title").getBoundsInRoot()
        assertTrue("Bluetooth lyrics title must not be squeezed at 200% fontScale (width=${btTitleBounds.width})", btTitleBounds.width >= 220.dp)
        val btTriggerBounds = rule.onNodeWithTag("settings_bluetooth_lyrics_trigger").assertIsDisplayed().getBoundsInRoot()
        assertTrue("Expected trigger height >= 48.dp, was ${btTriggerBounds.height}", btTriggerBounds.height >= 48.dp)
        val res = rule.activity.resources
        rule.onNodeWithText(res.getString(R.string.ft_settings_bluetooth_lyrics_title)).assertIsDisplayed()
        rule.onNodeWithText(res.getString(R.string.ft_settings_bluetooth_lyrics_desc)).assertIsDisplayed()

        rule.onNodeWithTag("settings_bluetooth_lyrics_trigger").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("settings_bluetooth_lyrics_off").assertIsDisplayed().assertIsSelected()
        rule.onNodeWithTag("settings_bluetooth_lyrics_bluetooth_audio").assertIsDisplayed().assertIsNotSelected()
        rule.onNodeWithTag("settings_bluetooth_lyrics_always").assertIsDisplayed().assertIsNotSelected()

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

        rule.onNodeWithTag("settings_bluetooth_lyrics_bluetooth_audio").performClick()
        rule.waitForIdle()
        assertEquals(BluetoothLyricsMode.BLUETOOTH_AUDIO, actions.bluetoothLyricsModeSet)

        rule.onNodeWithTag("settings_bluetooth_lyrics_trigger").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_bluetooth_lyrics_bluetooth_audio").assertIsSelected()
        rule.onNodeWithTag("settings_bluetooth_lyrics_always").performClick()
        rule.waitForIdle()
        assertEquals(BluetoothLyricsMode.ALWAYS, actions.bluetoothLyricsModeSet)

        rule.onNodeWithTag("settings_bluetooth_lyrics_trigger").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_bluetooth_lyrics_always").assertIsSelected().performClick()
        rule.waitForIdle()

        // System back from subpage returns to Settings directory without closing Settings.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("settings_screen").assertIsDisplayed()
        rule.onNodeWithTag("settings_category_general").performScrollTo().performClick()
        rule.waitForIdle()
        val langTitleBounds = rule.onNodeWithTag("settings_language_title").getBoundsInRoot()
        assertTrue("Language title must not be squeezed at 200% fontScale (width=${langTitleBounds.width})", langTitleBounds.width >= 220.dp)
        assertTrue(rule.onNodeWithTag("settings_language_trigger").getBoundsInRoot().height >= 48.dp)
        assertTrue(rule.onNodeWithTag("settings_language").getBoundsInRoot().right <= 390.dp)

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("settings_category_appearance").performScrollTo().performClick()
        rule.waitForIdle()
        val themeTitleBounds = rule.onNodeWithTag("settings_theme_title").getBoundsInRoot()
        assertTrue("Theme title must not be squeezed at 200% fontScale (width=${themeTitleBounds.width})", themeTitleBounds.width >= 220.dp)
        assertTrue(rule.onNodeWithTag("settings_theme_trigger").getBoundsInRoot().height >= 48.dp)
        assertTrue(rule.onNodeWithTag("settings_theme").getBoundsInRoot().right <= 390.dp)
    }

    @Test
    fun compactSettingsHidesBottomDockRestoresOnExitAndPreservesCategoryAcrossRecreation() {
        val restorationTester = StateRestorationTester(rule)
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
        restorationTester.setContent {
            FlareTuneTheme {
                ClientApp(state = uiState, actions = actions)
            }
        }

        rule.onNodeWithTag("dock_compact").assertIsDisplayed()
        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_settings").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("settings_screen").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("dock_compact").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithTag("settings_category_general").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_language").assertIsDisplayed()

        // Simulate Activity recreation (e.g. after switching language): active Settings category persists.
        restorationTester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_screen").assertIsDisplayed()
        rule.onNodeWithTag("settings_language").assertIsDisplayed()

        // Exit subpage -> directory -> exit Settings -> dock_compact restores.
        rule.onNodeWithTag("settings_back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_category_general").assertIsDisplayed()
        rule.onNodeWithTag("settings_back").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("dock_compact").assertIsDisplayed()
    }
}
