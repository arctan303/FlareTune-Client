package com.flaretune.client

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
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
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-night-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerLyricsMotionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val song = Song("fixture", "Blue hour", "The Slow Hours", "After the rain", 180.0,
        "https://fixture.invalid/audio", "https://fixture.invalid/art")
    private val lines = (0 until 36).map { index -> LyricLine(
        text = "Line $index — a quiet moment along the way",
        translation = "第 $index 行，在音乐里慢慢前行，留住沿途的光。",
        timeSeconds = index * 3.0, endTimeSeconds = index * 3.0 + 2.8,
    ) }
    private var lyrics by mutableStateOf(LyricState(songId = song.id, status = LyricStatus.READY,
        lines = lines, activeLyricIndex = 0, translationSupported = true,
        translationState = LyricTranslationStatus.READY))
    private var translated by mutableStateOf(false)
    private var current by mutableStateOf(song)
    private var lastSeek: Long? = null
    private lateinit var contentView: android.view.View
    private val actions = object : ClientActions {
        override fun seekTo(positionMs: Long) { lastSeek = positionMs }
        override fun setTranslationEnabled(enabled: Boolean) { translated = enabled }
        override fun login(instanceUrl:String,username:String,password:String) {}
        override fun changePassword(currentPassword:String,newPassword:String) {}
        override fun logout() {}; override fun selectTab(tab:LibraryTab) {}
        override fun refresh() {}; override fun search(query:String) {}; override fun loadMore() {}
        override fun playSong(song:Song) {}; override fun retryPlayback() {}
        override fun togglePlay() {}; override fun next() {}; override fun previous() {}
        override fun toggleShuffle() {}; override fun cycleRepeat() {}
        override fun removeQueueItem(index:Int) {}; override fun moveQueueItem(fromIndex:Int,toIndex:Int) {}
        override fun loadMoreAlbums() {}; override fun setVolume(volume:Float) {}
        override fun navigate(destination:MainDestination) {}
        override fun openAlbum(album:Album) {}; override fun closeAlbum() {}
        override fun selectQueueItem(index:Int) {}
    }
    private fun render(artwork: Boolean = false) {
        val resources = rule.activity.resources
        val loader = if (!artwork) null else coil.ImageLoader.Builder(rule.activity).components {
            add(object : coil.intercept.Interceptor {
                override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
                    val bmp = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(0xFF31596B.toInt())
                    canvas.drawCircle(40f,20f,18f,android.graphics.Paint(3).apply { color=0xFFB98B60.toInt() })
                    return coil.request.SuccessResult(android.graphics.drawable.BitmapDrawable(resources,bmp),chain.request,coil.decode.DataSource.MEMORY)
                }
            })
        }.build()
        rule.mainClock.autoAdvance = false
        rule.setContent { FlareTuneTheme {
            val hostView=LocalView.current
            SideEffect { contentView=hostView }
            NowPlayingScreen(
                state = PlaybackSnapshot(current, listOf(current),0,false,positionMs=(lyrics.activeLyricIndex * 3000L).coerceAtLeast(0),durationMs=180000),
                lyricState = lyrics, translationEnabled = translated, actions = actions,
                imageLoader = loader, onClose = {}, onQueue = {},
            )
        } }
        settle()
    }
    private fun settle(ms: Long = 360) {
        // Manual Compose frames and Android layout/draw are separate under Robolectric.
        // Drain layout between frames so coroutine scrolls see the latest measured items.
        repeat(((ms+15)/16).toInt()) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
    }
    private fun activate(index: Int) { rule.runOnIdle { lyrics = lyrics.copy(activeLyricIndex=index) }; settle() }
    private fun assertFocused(index: Int) {
        val viewport = rule.onNodeWithTag("player_lyrics_list").fetchSemanticsNode().boundsInRoot
        val line = rule.onNodeWithTag("player_lyric_$index").fetchSemanticsNode().boundsInRoot
        val target = viewport.top + maxOf(viewport.height * .30f,72f)
        assertTrue("Line $index top ${line.top} must follow focus $target promptly",abs(line.top-target)<3f)
    }
    @Test fun automaticScrollingDoesNotCancelItselfAndContinuesWithinTheOldPauseWindow() {
        render()
        activate(3); assertFocused(3)
        activate(4); assertFocused(4)
        activate(5); assertFocused(5)
    }
    @Test fun distantSeekMovesToCurrentLineWithoutTraversingTheOldPage() {
        render(); activate(28); assertFocused(28)
        activate(lines.lastIndex); assertFocused(lines.lastIndex)
        activate(1); assertFocused(1)
    }
    @Test fun translationHeightChangesKeepTheActiveLineAtItsFocus() {
        render(); activate(3); assertFocused(3)
        rule.onNodeWithTag("player_translate_button").performClick(); settle()
        assertFocused(3)
        activate(4); assertFocused(4)
        rule.onNodeWithTag("player_translate_button").performClick(); settle()
        assertFocused(4)
    }
    private fun dragLyrics() {
        rule.onNodeWithTag("player_lyrics_list").performTouchInput {
            down(center); moveBy(Offset(0f,-150f),delayMillis=700); up()
        }
        settle(350)
    }
    @Test fun realDraggingPausesFollowingAndResumesAfterBrowsingEnds() {
        render(); dragLyrics()
        val before=rule.onNodeWithTag("player_lyric_2").fetchSemanticsNode().boundsInRoot.top
        activate(1)
        val after=rule.onNodeWithTag("player_lyric_2").fetchSemanticsNode().boundsInRoot.top
        assertEquals("A real gesture must not be overridden by the next line",before,after,2f)
        settle(3000); assertFocused(1)
    }
    @Test fun changingSongsClearsTheOldManualBrowsingPause() {
        render(); dragLyrics()
        rule.runOnIdle { current=song.copy(id="next"); lyrics=lyrics.copy(songId="next",activeLyricIndex=2) }
        settle(); assertFocused(2)
    }
    @Test fun timedLyricTapStillSeeksToTheSourceTimestamp() {
        render(); rule.onNodeWithTag("player_lyric_1").performClick()
        rule.runOnIdle { assertEquals(3000L,lastSeek) }
    }
    private fun assertToolbarAligned() {
        val transport=rule.onNodeWithTag("player_play").fetchSemanticsNode().boundsInRoot
        val translate=rule.onNodeWithTag("player_translate_button").fetchSemanticsNode().boundsInRoot
        assertEquals(transport.center.y,translate.center.y,1f)
        assertTrue("Translation touch height",translate.height>=48f)
    }
    @Test fun wideTranslationControlSharesTransportBaselineAndNeutralPalette() {
        render(); assertToolbarAligned()
        rule.onNodeWithTag("player_translate_button").assertIsNotSelected()
        assertNeutralButton(active=false)
        rule.onNodeWithTag("player_translate_button").performClick(); settle()
        rule.onNodeWithTag("player_translate_button").assertIsSelected()
        assertToolbarAligned(); assertNeutralButton(active=true)
        screenshot("player-lyrics-wide-translation-on")
    }
    private fun assertNeutralButton(active: Boolean) {
        val bounds=rule.onNodeWithTag("player_translate_button").fetchSemanticsNode().boundsInRoot
        lateinit var button: Bitmap
        rule.runOnIdle {
            val image=Bitmap.createBitmap(contentView.width,contentView.height,Bitmap.Config.ARGB_8888)
            contentView.draw(android.graphics.Canvas(image))
            button=Bitmap.createBitmap(image,bounds.left.roundToInt(),bounds.top.roundToInt(),bounds.width.roundToInt(),bounds.height.roundToInt())
        }
        val pixels=button.asImageBitmap().toPixelMap()
        var peak=0f
        for(y in 0 until pixels.height)for(x in 0 until pixels.width){val c=pixels[x,y]
            assertTrue("No independent blue button fill or icon", c.blue-c.red<.08f && c.blue-c.green<.08f)
            peak=maxOf(peak,c.red)
        }
        assertTrue("Button corner should use the common player background",pixels[1,1].red<.12f)
        if(active)assertTrue("Enabled icon must be bright white",peak>.95f)
        else assertTrue("Disabled display preference must use gray",peak in .60f.. .90f)
    }
    @Test fun systemLargeTextKeepsToolbarBaselineAndReadableScrollableLyrics() {
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        render(); assertToolbarAligned(); activate(2); assertFocused(2)
        screenshot("player-lyrics-wide-large")
    }
    @Test fun coverAtmosphereAndNeutralControlsRenderTogether() {
        render(artwork=true); activate(2); assertToolbarAligned()
        screenshot("player-lyrics-cover-atmosphere")
    }
    @Test fun aServerNotNeededResultHidesActionsEvenWhenCapabilityIsTrue() {
        current=song.copy(language="zh")
        lyrics=lyrics.copy(lines=lines.map { it.copy(translation=null) },translationTargetLanguage="zh-CN",
            translationState=LyricTranslationStatus.NOT_NEEDED)
        render()
        rule.onNodeWithTag("player_translate_button").assertDoesNotExist()
        rule.onNodeWithTag("player_generate_translation").assertDoesNotExist()
    }
    @Test fun translationEligibilityComesFromTheApiAndNotTheSongLanguage() {
        current=song.copy(language="zh")
        lyrics=lyrics.copy(lines=lines.map { it.copy(translation=null) },translationTargetLanguage="zh",
            translationState=LyricTranslationStatus.IDLE)
        render()
        rule.onNodeWithTag("player_translate_button").assertDoesNotExist()
        rule.onNodeWithTag("player_generate_translation").assertExists()
    }
    @Test fun existingDifferentTranslationRemainsUsableWhenGenerationIsNotNeeded() {
        current=song.copy(language="zh")
        lyrics=lyrics.copy(translationTargetLanguage="zh",translationLanguage="en")
        render()
        rule.onNodeWithTag("player_translate_button").assertExists()
        rule.onNodeWithTag("player_generate_translation").assertDoesNotExist()
        rule.onNodeWithTag("player_translate_button").performClick(); settle()
        rule.onNodeWithText(lines.first().translation!!).assertExists()
    }
    @Test fun unknownLanguageKeepsExplicitGenerationButNeverPretendsAnEmptyTranslationExists() {
        lyrics=lyrics.copy(lines=lines.map { it.copy(translation=null) },translationTargetLanguage="en",
            translationState=LyricTranslationStatus.IDLE)
        render()
        rule.onNodeWithTag("player_translate_button").assertDoesNotExist()
        rule.onNodeWithTag("player_generate_translation").assertExists()
        rule.onNodeWithText("Translate to English").assertExists()
    }
    @Test fun aTranslationFailureKeepsOriginalTextAndShowsItsOwnError() {
        lyrics=lyrics.copy(lines=lines.map { it.copy(translation=null) },translationTargetLanguage="en",
            translationState=LyricTranslationStatus.FAILED,notice=ClientNotice.NETWORK)
        render()
        rule.onNodeWithTag("player_lyric_0").assertExists()
        rule.onNodeWithTag("player_generate_translation").assertExists()
        rule.onNodeWithText("Translation could not be loaded. The original lyrics are still available.").assertExists()
        rule.onNodeWithText("Secure storage is unavailable. Please try again.").assertDoesNotExist()
    }
    private fun screenshot(name: String) {
        settle(800)
        val dir=File(checkNotNull(System.getProperty("flaretune.screenshots.dir"))).apply { mkdirs() }
        rule.runOnIdle {
            val view=rule.activity.window.decorView
            val bmp=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            File(dir,"$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
