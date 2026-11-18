package com.arcinks.flaretune

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.arcinks.flaretune.model.Album
import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.ui.ClientActions
import com.arcinks.flaretune.ui.ClientApp
import com.arcinks.flaretune.ui.ClientScreen
import com.arcinks.flaretune.ui.ClientUiState
import com.arcinks.flaretune.ui.LibraryTab
import com.arcinks.flaretune.ui.MainDestination
import com.arcinks.flaretune.ui.theme.FlareTuneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the settings page in every shipped interface language and proves the language selector
 * uses that language's resources instead of falling back to English.
 *
 * Visual review on a real device is not replaced by this test; Robolectric cannot render Compose
 * vector icons in this project's graphics configuration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi", application = Application::class)
class LanguageLayoutTest {
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

    private fun openLanguageSettings() {
        rule.setContent {
            FlareTuneTheme {
                ClientApp(
                    state = ClientUiState(
                        screen = ClientScreen.LIBRARY,
                        destination = MainDestination.HOME,
                        instanceUrl = "https://music.example.com",
                        username = "alice",
                        accountName = "Alice",
                        songs = listOf(track),
                    ),
                    actions = NoopActions,
                )
            }
        }
        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_category_general").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings_language").performScrollTo()
        rule.waitForIdle()
    }

    private fun assertLanguageSelectorShowsAllFiveLanguages() {
        assertTrue(
            "the language selector row must fit the 390dp viewport",
            rule.onNodeWithTag("settings_language").fetchSemanticsNode().boundsInRoot.right <= 390f,
        )
        rule.onNodeWithTag("settings_language_trigger").assertIsDisplayed().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("简体中文").assertIsDisplayed()
        rule.onNodeWithText("繁體中文").assertIsDisplayed()
        rule.onNodeWithText("English").assertIsDisplayed()
        rule.onNodeWithText("日本語").assertIsDisplayed()
        rule.onNodeWithText("한국어").assertIsDisplayed()
        for (tag in listOf("settings_language_system", "settings_language_zh", "settings_language_zh_hant", "settings_language_en", "settings_language_ja", "settings_language_ko")) {
            rule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "en-w390dp-h844dp-mdpi")
    fun englishSettingsShowTheLanguageSelector() {
        openLanguageSettings()
        rule.onNodeWithText("Follow system language").assertIsDisplayed()
        assertLanguageSelectorShowsAllFiveLanguages()
    }

    @Test
    @Config(qualifiers = "ja-w390dp-h844dp-mdpi")
    fun japaneseSettingsUseTranslatedCopy() {
        openLanguageSettings()
        rule.onAllNodesWithText("Follow system language").assertCountEquals(0)
        assertLanguageSelectorShowsAllFiveLanguages()
    }

    @Test
    @Config(qualifiers = "ko-w390dp-h844dp-mdpi")
    fun koreanSettingsUseTranslatedCopy() {
        openLanguageSettings()
        rule.onAllNodesWithText("Follow system language").assertCountEquals(0)
        assertLanguageSelectorShowsAllFiveLanguages()
    }

    @Test
    @Config(qualifiers = "b+zh+Hant-w390dp-h844dp-mdpi")
    fun traditionalChineseSettingsUseTranslatedCopy() {
        openLanguageSettings()
        rule.onAllNodesWithText("Follow system language").assertCountEquals(0)
        assertLanguageSelectorShowsAllFiveLanguages()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w390dp-h844dp-mdpi")
    fun simplifiedChineseSettingsUseTranslatedCopy() {
        openLanguageSettings()
        rule.onAllNodesWithText("Follow system language").assertCountEquals(0)
        assertLanguageSelectorShowsAllFiveLanguages()
    }

    private object NoopActions : ClientActions {
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
}
