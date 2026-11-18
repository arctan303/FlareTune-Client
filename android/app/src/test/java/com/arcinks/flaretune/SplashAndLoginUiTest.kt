package com.arcinks.flaretune

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.arcinks.flaretune.data.SavedAccountSummary
import com.arcinks.flaretune.model.Album
import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.ui.ClientActions
import com.arcinks.flaretune.ui.ClientApp
import com.arcinks.flaretune.ui.ClientScreen
import com.arcinks.flaretune.ui.ClientUiState
import com.arcinks.flaretune.ui.ConnectStep
import com.arcinks.flaretune.ui.LibraryTab
import com.arcinks.flaretune.ui.MainDestination
import com.arcinks.flaretune.ui.theme.FlareTuneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private open class TestActionsAdapter : ClientActions {
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplashAndLoginUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val noopActions: ClientActions = TestActionsAdapter()

    private fun libraryState() = ClientUiState(
        screen = ClientScreen.LIBRARY,
        destination = MainDestination.HOME,
        instanceUrl = "https://music.example.com",
        username = "alice",
        accountName = "Alice",
        songs = listOf(
            Song("s1", "Midnight Stream", "FlareTune", "Signals", 195.0, "https://fixture.invalid/s1.mp3", null),
        ),
    )

    @Test
    @Config(qualifiers = "w390dp-h844dp-night-mdpi")
    fun coldOpenSplash_docksIntoConnectLoginAvatar() {
        var state by mutableStateOf(ClientUiState(screen = ClientScreen.RESTORING))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FlareTuneTheme {
                ClientApp(state = state, actions = noopActions, playColdOpenSplash = true)
            }
        }

        rule.mainClock.advanceTimeBy(180)
        rule.onNodeWithTag("cold_open_splash").assertExists()
        captureScreenshot("splash-act1-dark.png")

        state = ClientUiState(
            screen = ClientScreen.CONNECT,
            instanceUrl = "https://music.example.com",
        )
        rule.mainClock.advanceTimeBy(360)
        captureScreenshot("splash-act2-morph.png")

        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()

        assertTrue(rule.onAllNodesWithTag("cold_open_splash").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("login_avatar_badge").assertIsDisplayed()
        rule.onNodeWithTag("connect_url").assertIsDisplayed()
        rule.onNodeWithTag("connect_check").assertIsDisplayed()
        captureScreenshot("login-phone-dark.png")
    }

    @Test
    fun coldOpenSplash_docksIntoLibraryAccountAvatar() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FlareTuneTheme {
                ClientApp(state = libraryState(), actions = noopActions, playColdOpenSplash = true)
            }
        }

        rule.mainClock.advanceTimeBy(180)
        rule.onNodeWithTag("cold_open_splash").assertExists()

        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()

        assertTrue(rule.onAllNodesWithTag("cold_open_splash").fetchSemanticsNodes().isEmpty())
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("account_menu").assertIsDisplayed()
        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("account_drawer").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp-night-mdpi")
    fun loginSuccess_relaysAvatarIntoHomeAccountAvatar() {
        var state by mutableStateOf(
            ClientUiState(
                screen = ClientScreen.CONNECT,
                instanceUrl = "https://music.example.com",
                username = "alice",
            ),
        )
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FlareTuneTheme {
                ClientApp(state = state, actions = noopActions, playColdOpenSplash = false)
            }
        }

        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("login_avatar_badge").assertIsDisplayed()

        state = libraryState()
        rule.mainClock.advanceTimeBy(80)
        rule.onNodeWithTag("avatar_relay_overlay").assertExists()
        captureScreenshot("avatar-relay-midflight.png")

        rule.mainClock.advanceTimeBy(600)
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()

        assertTrue(rule.onAllNodesWithTag("avatar_relay_overlay").fetchSemanticsNodes().isEmpty())
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("account_menu").assertIsDisplayed()
        rule.onNodeWithTag("account_menu").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("account_drawer").assertIsDisplayed()
    }

    @Test
    fun connectionScreen_twoStepLoginGoogleButtonAndFontScaleWorkCleanly() {
        var checkedOrigin: String? = null
        var loggedInUser: String? = null
        var googleStarted = 0
        var editedInstance = 0
        var state by mutableStateOf(
            ClientUiState(
                screen = ClientScreen.CONNECT,
                connectStep = ConnectStep.INSTANCE,
                savedAccounts = listOf(
                    SavedAccountSummary(
                        accountKey = "acc1",
                        origin = "https://music.example.com",
                        accountId = "u1",
                        username = "mira",
                        displayName = "Mira",
                        active = false,
                        needsReauth = false,
                    ),
                ),
            ),
        )
        val actions = object : TestActionsAdapter() {
            override fun checkInstance(instanceUrl: String) {
                checkedOrigin = instanceUrl
                state = state.copy(
                    instanceUrl = instanceUrl,
                    connectStep = ConnectStep.CREDENTIALS,
                    googleLoginEnabled = true,
                    googleNativeLoginEnabled = true,
                )
            }

            override fun editInstanceAddress() {
                editedInstance++
                state = state.copy(
                    connectStep = ConnectStep.INSTANCE,
                    googleLoginEnabled = false,
                    googleNativeLoginEnabled = false,
                )
            }

            override fun startGoogleLogin() {
                googleStarted++
            }

            override fun login(instanceUrl: String, username: String, password: String) {
                loggedInUser = username
            }
        }
        rule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, fontScale = 2.0f)) {
                FlareTuneTheme {
                    ClientApp(state = state, actions = actions)
                }
            }
        }

        // Step 1: Enter instance address and check
        rule.onNodeWithTag("login_avatar_badge").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("connect_url").performScrollTo().performTextInput("https://music.example.com")
        rule.onNodeWithTag("connect_saved_accounts").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("connect_check").performScrollTo().assertIsEnabled().performClick()
        org.junit.Assert.assertEquals("https://music.example.com", checkedOrigin)

        // Step 2: Credentials + Google login
        rule.onNodeWithTag("connect_instance_bar").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("connect_google").performScrollTo().assertIsEnabled().performClick()
        org.junit.Assert.assertEquals(1, googleStarted)
        rule.onNodeWithTag("connect_username").performScrollTo().performTextInput("flare")
        rule.onNodeWithTag("connect_password").performScrollTo().performTextInput("secret-pass")
        rule.onNodeWithTag("login_avatar_badge").performScrollTo()
        rule.onNodeWithText("F", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("connect_submit").performScrollTo().assertIsEnabled().performClick()
        assertTrue(loggedInUser == "flare")
        captureScreenshot("login-phone-2x.png")

        // Return to Step 1
        rule.onNodeWithTag("connect_change_instance").performScrollTo().performClick()
        org.junit.Assert.assertEquals(1, editedInstance)
        rule.onNodeWithTag("connect_url").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun connectionScreen_showsUpgradeHintWhenServerHasWebGoogleOnly() {
        rule.setContent {
            FlareTuneTheme {
                ClientApp(
                    state = ClientUiState(
                        screen = ClientScreen.CONNECT,
                        connectStep = ConnectStep.CREDENTIALS,
                        instanceUrl = "https://music.example.com",
                        googleLoginEnabled = true,
                        googleNativeLoginEnabled = false,
                    ),
                    actions = noopActions,
                )
            }
        }
        rule.onNodeWithTag("connect_google_upgrade_hint").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("connect_google").fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-night-mdpi")
    fun connectionScreen_tabletWideUsesTwoPaneHeroAndFormSideBySide() {
        rule.setContent {
            FlareTuneTheme {
                ClientApp(
                    state = ClientUiState(
                        screen = ClientScreen.CONNECT,
                        connectStep = ConnectStep.CREDENTIALS,
                        instanceUrl = "https://music.example.com",
                        username = "luna",
                        addingAccount = true,
                        googleLoginEnabled = true,
                        googleNativeLoginEnabled = true,
                        savedAccounts = listOf(
                            SavedAccountSummary(
                                accountKey = "acc1",
                                origin = "https://music.example.com",
                                accountId = "u1",
                                username = "mira",
                                displayName = "Mira",
                                active = false,
                                needsReauth = false,
                            ),
                        ),
                    ),
                    actions = noopActions,
                )
            }
        }

        val avatarBounds = rule.onNodeWithTag("login_avatar_badge").getBoundsInRoot()
        val formBarBounds = rule.onNodeWithTag("connect_instance_bar").getBoundsInRoot()
        // In wide tablet two-pane layout, the hero badge sits cleanly to the left of the form card.
        assertTrue(avatarBounds.right < formBarBounds.left)
        assertTrue((formBarBounds.right - formBarBounds.left) >= 300.dp)
        rule.onNodeWithTag("connect_cancel").assertIsDisplayed()
        rule.onNodeWithTag("connect_submit").assertIsDisplayed()
        rule.onNodeWithTag("connect_google").assertIsDisplayed()
        captureScreenshot("login-tablet-dark.png")
    }

    @Test
    fun accountSwitch_replaysColdOpenSplashAndAllowsEmptyPasswordRetryOnSavedAccount() {
        var switchedTo: String? = null
        val actions = object : TestActionsAdapter() {
            override fun switchAccount(accountKey: String) {
                switchedTo = accountKey
            }
        }
        var state by mutableStateOf(libraryState())
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FlareTuneTheme {
                ClientApp(state = state, actions = actions, playColdOpenSplash = true)
            }
        }

        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("cold_open_splash").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("account_menu").assertIsDisplayed()

        rule.runOnIdle {
            state = ClientUiState(
                screen = ClientScreen.RESTORING,
                splashNonce = 1,
                instanceUrl = "https://music.example.com",
                username = "mira",
                busy = true,
            )
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(180)
        rule.waitForIdle()
        rule.onNodeWithTag("cold_open_splash").assertExists()

        rule.runOnIdle {
            state = ClientUiState(
                screen = ClientScreen.CONNECT,
                connectStep = ConnectStep.CREDENTIALS,
                splashNonce = 1,
                instanceUrl = "https://music.example.com",
                username = "mira",
                savedAccounts = listOf(
                    SavedAccountSummary(
                        accountKey = "acc-mira",
                        origin = "https://music.example.com",
                        accountId = "u2",
                        username = "mira",
                        displayName = "Mira",
                        active = false,
                        needsReauth = false,
                    ),
                ),
            )
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("cold_open_splash").fetchSemanticsNodes().isEmpty())

        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("connect_submit").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        org.junit.Assert.assertEquals("acc-mira", switchedTo)
    }

    private fun captureScreenshot(fileName: String) {
        val dirPath = System.getProperty("flaretune.screenshots.dir") ?: return
        val dir = File(dirPath).apply { mkdirs() }
        rule.runOnIdle {
            val views = WindowInspector.getGlobalWindowViews()
            val view = views.lastOrNull { it.isShown && it.width > 0 && it.height > 0 } ?: rule.activity.window.decorView
            if (view.width <= 0 || view.height <= 0) return@runOnIdle
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(dir, fileName).outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            assertTrue(bitmap.width > 100 && bitmap.height > 100)
        }
    }
}
