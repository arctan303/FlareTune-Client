package com.flaretune.client

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import com.flaretune.client.data.AppUpdateError
import com.flaretune.client.data.AppUpdateRelease
import com.flaretune.client.data.SavedAccountSummary
import com.flaretune.client.model.Album
import com.flaretune.client.model.Song
import com.flaretune.client.ui.AppUpdateState
import com.flaretune.client.ui.AppUpdateStatus
import com.flaretune.client.ui.ClientActions
import com.flaretune.client.ui.ClientApp
import com.flaretune.client.ui.ClientScreen
import com.flaretune.client.ui.ClientUiState
import com.flaretune.client.ui.LibraryTab
import com.flaretune.client.ui.MainDestination
import com.flaretune.client.ui.theme.FlareTuneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi", application = Application::class)
class AppUpdateUiTest {
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
            avatarUrl = null,
        ),
    )

    private open class BaseFakeUpdateUiActions : ClientActions {
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

    private val release = AppUpdateRelease(
        tagName = "v0.1.0-dev.10",
        versionName = "0.1.0-dev.10",
        releaseName = "FlareTune 0.1.0-dev.10",
        body = "1. Two-step login flow\n2. Google Sign-In\n3. In-app updates",
        publishedAt = "2026-11-17T00:00:00Z",
        isPrerelease = true,
        htmlUrl = "https://github.com/arctan303/FlareTune-Client/releases/tag/v0.1.0-dev.10",
        apkDownloadUrl = "https://github.com/arctan303/FlareTune-Client/releases/download/v0.1.0-dev.10/app.apk",
        apkFileName = "app.apk",
        apkSizeBytes = 12_582_912L,
        apkSha256 = "0".repeat(64),
    )

    private fun openSettingsFromHome() {
        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    private fun scrollAboveDockAndClick(tag: String) {
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

    @Test
    fun settingsAboutSectionTogglesPrereleasesAndTriggersManualCheck() {
        var prereleaseSet: Boolean? = null
        var checkCalls = 0
        var uiState by mutableStateOf(
            ClientUiState(
                screen = ClientScreen.LIBRARY,
                destination = MainDestination.HOME,
                instanceUrl = "https://music.example.com",
                username = "alice",
                accountName = "Alice",
                savedAccounts = accounts,
                songs = listOf(track),
                appUpdate = AppUpdateState(
                    currentVersion = "0.1.0-dev.9",
                    status = AppUpdateStatus.IDLE,
                    includePrereleases = false,
                ),
            ),
        )
        val actions = object : BaseFakeUpdateUiActions() {
            override fun setIncludePrereleaseUpdates(enabled: Boolean) {
                prereleaseSet = enabled
                uiState = uiState.copy(appUpdate = uiState.appUpdate.copy(includePrereleases = enabled))
            }
            override fun checkForAppUpdate() {
                checkCalls++
                uiState = uiState.copy(appUpdate = uiState.appUpdate.copy(status = AppUpdateStatus.UP_TO_DATE))
            }
        }

        rule.setContent {
            FlareTuneTheme {
                ClientApp(state = uiState, actions = actions)
            }
        }

        openSettingsFromHome()
        rule.onNodeWithTag("ft_settings_section_about").performScrollTo()
        rule.onNodeWithTag("settings_include_prereleases_switch").performScrollTo().assertIsOff()
        scrollAboveDockAndClick("settings_include_prereleases_switch")
        assertEquals(true, prereleaseSet)
        rule.onNodeWithTag("settings_include_prereleases_switch").assertIsOn()

        scrollAboveDockAndClick("settings_check_updates")
        assertEquals(1, checkCalls)
        val upToDateText = rule.activity.resources.getString(R.string.ft_settings_update_up_to_date)
        rule.onNodeWithText(upToDateText).assertIsDisplayed()
    }

    @Test
    fun appUpdateDialogRendersComparisonDownloadInstallPermissionErrorAndHighFontScale() {
        var downloadCalls = 0
        var cancelCalls = 0
        var installCalls = 0
        var dismissCalls = 0

        var uiState by mutableStateOf(
            ClientUiState(
                screen = ClientScreen.LIBRARY,
                destination = MainDestination.HOME,
                instanceUrl = "https://music.example.com",
                username = "alice",
                accountName = "Alice",
                savedAccounts = accounts,
                songs = listOf(track),
                appUpdate = AppUpdateState(
                    currentVersion = "0.1.0-dev.9",
                    status = AppUpdateStatus.AVAILABLE,
                    includePrereleases = true,
                    dialogVisible = true,
                    availableRelease = release,
                ),
            ),
        )
        val actions = object : BaseFakeUpdateUiActions() {
            override fun startAppUpdateDownload() { downloadCalls++ }
            override fun cancelAppUpdateDownload() { cancelCalls++ }
            override fun installDownloadedAppUpdate() { installCalls++ }
            override fun dismissAppUpdateDialog() { dismissCalls++ }
        }

        rule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2.0f)) {
                FlareTuneTheme {
                    ClientApp(state = uiState, actions = actions)
                }
            }
        }

        openSettingsFromHome()
        rule.onNodeWithTag("app_update_dialog").assertIsDisplayed()
        rule.onNodeWithTag("app_update_prerelease_badge").assertIsDisplayed()
        rule.onNodeWithTag("app_update_current_version").assertIsDisplayed()
        rule.onNodeWithTag("app_update_latest_version").assertIsDisplayed()
        rule.onNodeWithTag("app_update_notes").assertIsDisplayed()
        rule.onNodeWithTag("app_update_open_release_button").assertIsDisplayed()

        rule.onNodeWithTag("app_update_download_button").performClick()
        assertEquals(1, downloadCalls)

        // Downloading state shows progress bar and cancel button.
        uiState = uiState.copy(
            appUpdate = uiState.appUpdate.copy(
                status = AppUpdateStatus.DOWNLOADING,
                downloadedBytes = 6_291_456L,
                totalBytes = 12_582_912L,
            ),
        )
        rule.waitForIdle()
        rule.onNodeWithTag("app_update_progress").assertIsDisplayed()
        rule.onNodeWithTag("app_update_cancel_button").performClick()
        assertEquals(1, cancelCalls)

        // Install permission required state shows hint and grant button.
        uiState = uiState.copy(
            appUpdate = uiState.appUpdate.copy(
                status = AppUpdateStatus.INSTALL_PERMISSION_REQUIRED,
            ),
        )
        rule.waitForIdle()
        rule.onNodeWithTag("app_update_permission_hint").assertIsDisplayed()
        rule.onNodeWithTag("app_update_grant_permission_button").performClick()
        assertEquals(1, installCalls)

        // Ready to install state shows Install now button.
        uiState = uiState.copy(
            appUpdate = uiState.appUpdate.copy(
                status = AppUpdateStatus.READY_TO_INSTALL,
            ),
        )
        rule.waitForIdle()
        rule.onNodeWithTag("app_update_install_button").performClick()
        assertEquals(2, installCalls)

        // Error state shows error text and retry button.
        uiState = uiState.copy(
            appUpdate = uiState.appUpdate.copy(
                status = AppUpdateStatus.FAILED,
                errorKind = AppUpdateError.INTEGRITY_MISMATCH,
            ),
        )
        rule.waitForIdle()
        rule.onNodeWithTag("app_update_error").assertIsDisplayed()
        rule.onNodeWithTag("app_update_download_button").performClick()
        assertEquals(2, downloadCalls)

        rule.onNodeWithTag("app_update_dismiss_button").performClick()
        assertTrue(dismissCalls >= 1)
    }
}
