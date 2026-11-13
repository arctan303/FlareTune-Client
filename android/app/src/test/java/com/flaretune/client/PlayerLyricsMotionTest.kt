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
    private var translationRequests = 0
    private lateinit var contentView: android.view.View
    private val actions = object : ClientActions {
        override fun seekTo(positionMs: Long) { lastSeek = positionMs }
        override fun setTranslationEnabled(enabled: Boolean) { translated = enabled }
        override fun requestLyricTranslation() { translationRequests++ }
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
    private fun settle(ms: Long = 1200) {
        // Manual Compose frames and Android layout/draw are separate under Robolectric.
        // Drain layout between frames so stagger delays (~50-150ms) + ~600ms springs reach equilibrium.
        repeat(((ms+15)/16).toInt()) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle() }
    }
    private fun activate(index: Int) { rule.runOnIdle { lyrics = lyrics.copy(activeLyricIndex=index) }; settle() }
    private fun assertFocused(index: Int) {
        val viewport = rule.onNodeWithTag("player_lyrics_list").fetchSemanticsNode().boundsInRoot
        val line = rule.onNodeWithTag("player_lyric_$index").fetchSemanticsNode().boundsInRoot
        val targetCenter = viewport.top + viewport.height * LyricMotionSpecs.ALIGN_POSITION
        assertTrue(
            "Line $index center ${line.center.y} must align at 35% focus $targetCenter promptly",
            abs(line.center.y - targetCenter) < 3f,
        )
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
        // Keep stale returned translations: NOT_NEEDED must still suppress both controls.
        lyrics=lyrics.copy(translationTargetLanguage="zh-CN",
            translationState=LyricTranslationStatus.NOT_NEEDED)
        render()
        rule.onNodeWithTag("player_translate_button").assertDoesNotExist()
        rule.onNodeWithTag("player_generate_translation").assertDoesNotExist()
    }
    @Test fun returnedLegacyTranslationRemainsReadableWithoutGenerationCapability() {
        lyrics=lyrics.copy(translationSupported=false, translationState=LyricTranslationStatus.UNAVAILABLE)
        render()
        rule.onNodeWithTag("player_translate_button").assertExists().performClick()
        settle()
        rule.onNodeWithText(lines.first().translation!!).assertExists()
        rule.onNodeWithTag("player_generate_translation").assertDoesNotExist()
        assertEquals(0, translationRequests)
        screenshot("translation-returned-legacy")
    }
    @Test fun changingToNotNeededRemovesThePreviousSongsTranslationControls() {
        render()
        rule.onNodeWithTag("player_translate_button").assertExists()
        rule.runOnIdle {
            current=song.copy(id="not-needed", language="unknown")
            lyrics=lyrics.copy(songId=current.id, translationState=LyricTranslationStatus.NOT_NEEDED)
        }
        settle()
        rule.onNodeWithTag("player_translate_button").assertDoesNotExist()
        rule.onNodeWithTag("player_generate_translation").assertDoesNotExist()
        assertEquals(0, translationRequests)
        screenshot("translation-not-needed")
    }
    @Test fun instrumentalStatusCannotExposeStaleTranslationControls() {
        lyrics=lyrics.copy(status=LyricStatus.INSTRUMENTAL)
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
    @Test fun fullScreenPlayerKeepsScreenAwakeWhileMountedAndRestoresOnDispose() {
        var visible by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent { FlareTuneTheme {
            val hostView = LocalView.current
            SideEffect { contentView = hostView }
            if (visible) {
                NowPlayingScreen(
                    state = PlaybackSnapshot(current, listOf(current), 0, false, positionMs = 0, durationMs = 180000),
                    lyricState = lyrics, translationEnabled = translated, actions = actions,
                    imageLoader = null, onClose = {}, onQueue = {},
                )
            }
        } }
        settle()
        rule.runOnIdle { assertTrue(contentView.keepScreenOn) }
        rule.runOnIdle { visible = false }
        settle()
        rule.runOnIdle { assertFalse(contentView.keepScreenOn) }
    }
    @Test fun longMetadataStaysSingleLineWithoutShiftingControlsOrCollapsingEdges() {
        render()
        val shortPlayTop = rule.onNodeWithTag("player_play").fetchSemanticsNode().boundsInRoot.top
        val shortSeekTop = rule.onNodeWithTag("player_seek").fetchSemanticsNode().boundsInRoot.top
        val modeBounds = rule.onNodeWithTag("player_mode").fetchSemanticsNode().boundsInRoot
        val queueBounds = rule.onNodeWithTag("open_queue").fetchSemanticsNode().boundsInRoot
        val moreBounds = rule.onNodeWithTag("player_more").fetchSemanticsNode().boundsInRoot
        assertEquals("Right edge icons must align vertically", queueBounds.center.x, moreBounds.center.x, 3f)
        assertTrue("Edge buttons must preserve >=48px touch target", modeBounds.width >= 48f && queueBounds.width >= 48f)

        rule.runOnIdle {
            current = song.copy(
                id = "very-long",
                title = "An Extraordinarily Long Song Title That Would Have Wrapped Across Multiple Lines And Shifted Everything Before",
                artist = "An Exceptionally Verbose Artist Ensemble Name",
                album = "Deluxe Remastered Special Collectors Edition Soundtrack Volume Two",
            )
        }
        settle()
        val longPlayTop = rule.onNodeWithTag("player_play").fetchSemanticsNode().boundsInRoot.top
        val longSeekTop = rule.onNodeWithTag("player_seek").fetchSemanticsNode().boundsInRoot.top
        assertEquals("Single-line marquee must keep play button top fixed", shortPlayTop, longPlayTop, 1f)
        assertEquals("Single-line marquee must keep seek bar top fixed", shortSeekTop, longSeekTop, 1f)
    }
    @Test fun interludeDotsFollowWebTimingContractAndSeekToStageOnTap() {
        val gapLines = listOf(
            LyricLine(
                text = "Opening verse",
                timeSeconds = 2.0,
                endTimeSeconds = 5.0,
                words = listOf(LyricWord("Opening", 2.0, 3.5), LyricWord("verse", 3.6, 5.0)),
            ),
            LyricLine(
                text = "After interlude",
                timeSeconds = 17.0,
                endTimeSeconds = 20.0,
                words = listOf(LyricWord("After", 17.0, 18.4), LyricWord("interlude", 18.5, 20.0)),
            ),
        )
        // Short line-only intro (<7.5s) stays hidden; >=7.5s lowered threshold activates cleanly.
        val shortLineIntro = computeInterludeUiState(
            currentTimeSeconds = 1.0,
            lines = listOf(LyricLine("Short intro", timeSeconds = 6.0)),
            activeIndex = -1,
            lineIndex = 0,
            userScrolling = false,
            syncMode = "line",
        )
        assertFalse(shortLineIntro.isInterlude)

        val mediumLineIntro = computeInterludeUiState(
            currentTimeSeconds = 1.0,
            lines = listOf(LyricLine("Medium intro", timeSeconds = 8.5)),
            activeIndex = -1,
            lineIndex = 0,
            userScrolling = false,
            syncMode = "line",
        )
        assertTrue(mediumLineIntro.isInterlude)

        val mediumBetweenLines = computeInterludeUiState(
            currentTimeSeconds = 5.5,
            lines = listOf(
                LyricLine("First", timeSeconds = 1.0),
                LyricLine("Second", timeSeconds = 9.0),
            ),
            activeIndex = 0,
            lineIndex = 0,
            userScrolling = false,
            syncMode = "line",
        )
        assertTrue(mediumBetweenLines.isInterlude)

        val midBetween = computeInterludeUiState(
            currentTimeSeconds = 10.1,
            lines = gapLines,
            activeIndex = 0,
            lineIndex = 0,
            userScrolling = false,
            syncMode = "word",
        )
        assertTrue(midBetween.isInterlude)
        assertEquals(InterludeDotFillState.COMPLETE, midBetween.dotStates[0])
        assertEquals(InterludeDotFillState.FILLING, midBetween.dotStates[1])
        assertEquals(InterludeDotFillState.PENDING, midBetween.dotStates[2])

        lyrics = LyricState(
            songId = song.id,
            status = LyricStatus.READY,
            lines = gapLines,
            activeLyricIndex = 0,
            syncMode = "word",
        )
        var posMs by mutableStateOf(2500L)
        rule.mainClock.autoAdvance = false
        rule.setContent { FlareTuneTheme {
            val hostView = LocalView.current
            SideEffect { contentView = hostView }
            NowPlayingScreen(
                state = PlaybackSnapshot(current, listOf(current), 0, false, positionMs = posMs, durationMs = 180000),
                lyricState = lyrics, translationEnabled = false, actions = actions,
                imageLoader = null, onClose = {}, onQueue = {},
            )
        } }
        settle()
        val line0BoundsBefore = rule.onNodeWithTag("player_lyric_0").fetchSemanticsNode().boundsInRoot
        val line1TopBefore = rule.onNodeWithTag("player_lyric_1").fetchSemanticsNode().boundsInRoot.top

        // Enter BETWEEN_LINES interlude in-place inside line 0: dots must stay inside line 0 without pushing line 1 down.
        rule.runOnIdle { posMs = 10100L }
        settle(1200)
        val dotsBounds = rule.onNodeWithTag("player_interlude_dots").fetchSemanticsNode().boundsInRoot
        val line0BoundsInterlude = rule.onNodeWithTag("player_lyric_0").fetchSemanticsNode().boundsInRoot
        val line1TopInterlude = rule.onNodeWithTag("player_lyric_1").fetchSemanticsNode().boundsInRoot.top
        assertEquals("Interlude must transform line 0 in-place without shifting line 1", line1TopBefore, line1TopInterlude, 1.5f)
        assertTrue(
            "Interlude dots must live inside active line 0 bounds (${dotsBounds} inside $line0BoundsInterlude)",
            dotsBounds.top >= line0BoundsInterlude.top - 1f && dotsBounds.bottom <= line0BoundsInterlude.bottom + 1f,
        )
        assertEquals("Line 0 height stays identical during in-place interlude", line0BoundsBefore.height, line0BoundsInterlude.height, 1.5f)

        // Verify dot 0 (COMPLETE) renders crisp pure-white core pixels instead of gray.
        val dot0Bounds = rule.onNodeWithTag("interlude_dot_0").fetchSemanticsNode().boundsInRoot
        screenshot("interlude-dots-in-place")
        lateinit var dot0Crop: Bitmap
        rule.runOnIdle {
            val frame = Bitmap.createBitmap(contentView.width, contentView.height, Bitmap.Config.ARGB_8888)
            contentView.draw(android.graphics.Canvas(frame))
            val left = dot0Bounds.left.roundToInt().coerceIn(0, frame.width - 1)
            val top = dot0Bounds.top.roundToInt().coerceIn(0, frame.height - 1)
            dot0Crop = Bitmap.createBitmap(
                frame,
                left,
                top,
                dot0Bounds.width.roundToInt().coerceAtLeast(1).coerceAtMost(frame.width - left),
                dot0Bounds.height.roundToInt().coerceAtLeast(1).coerceAtMost(frame.height - top),
            )
        }
        val dot0Pixels = dot0Crop.asImageBitmap().toPixelMap()
        var pureWhiteCount = 0
        for (y in 0 until dot0Pixels.height) for (x in 0 until dot0Pixels.width) {
            val px = dot0Pixels[x, y]
            if (px.red > 0.90f && px.green > 0.90f && px.blue > 0.90f) pureWhiteCount++
        }
        assertTrue("Complete interlude dot must render bright pure white center (pixels=$pureWhiteCount)", pureWhiteCount > 25)

        rule.onNodeWithTag("interlude_dot_1").performClick()
        rule.runOnIdle {
            val expectedMs = (midBetween.dotTimesSeconds!![1] * 1000.0).toLong()
            assertEquals(expectedMs, lastSeek)
        }

        // Verify INTRO interlude also transforms line 0 in-place when activeLyricIndex = -1.
        val introGapLines = listOf(
            LyricLine(
                text = "Late opening verse",
                timeSeconds = 12.0,
                endTimeSeconds = 15.0,
                words = listOf(LyricWord("Late", 12.0, 13.5), LyricWord("opening", 13.6, 15.0)),
            ),
            LyricLine(
                text = "Second verse",
                timeSeconds = 16.0,
                endTimeSeconds = 19.0,
                words = listOf(LyricWord("Second", 16.0, 17.4), LyricWord("verse", 17.5, 19.0)),
            ),
        )
        rule.runOnIdle {
            lyrics = LyricState(
                songId = "intro-song",
                status = LyricStatus.READY,
                lines = introGapLines,
                activeLyricIndex = -1,
                syncMode = "word",
            )
            posMs = 4500L
        }
        settle(1200)
        val introDotsBounds = rule.onNodeWithTag("player_interlude_dots").fetchSemanticsNode().boundsInRoot
        val introLine0Bounds = rule.onNodeWithTag("player_lyric_0").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Intro interlude dots must transform inside line 0 slot ($introDotsBounds inside $introLine0Bounds)",
            introDotsBounds.top >= introLine0Bounds.top - 1f && introDotsBounds.bottom <= introLine0Bounds.bottom + 1f,
        )
    }
    @Test fun musiverPresentationTiersAndDecayingStaggerFollowMotionContract() {
        val active = resolveLyricPresentation(index = 4, activeIndex = 4, lineCount = 12, userScrolling = false)
        assertEquals(1.00f, active.opacity, 1e-4f)
        assertEquals(1.00f, active.scale, 1e-4f)
        assertEquals(0f, active.blurRadius.value, 1e-4f)
        assertEquals(0f, active.verticalOffset.value, 1e-4f)

        val above1 = resolveLyricPresentation(index = 3, activeIndex = 4, lineCount = 12, userScrolling = false)
        assertEquals(0.34f, above1.opacity, 1e-4f)
        assertEquals(0.985f, above1.scale, 1e-4f)
        assertEquals(0.5f, above1.blurRadius.value, 1e-4f)
        assertEquals(-2f, above1.verticalOffset.value, 1e-4f)

        val below1 = resolveLyricPresentation(index = 5, activeIndex = 4, lineCount = 12, userScrolling = false)
        assertEquals(0.44f, below1.opacity, 1e-4f)
        assertTrue("Active line opacity must strongly exceed adjacent line", active.opacity - below1.opacity >= 0.50f)

        val below3 = resolveLyricPresentation(index = 8, activeIndex = 4, lineCount = 12, userScrolling = false)
        assertEquals(0.22f, below3.opacity, 1e-4f)
        assertEquals(0.935f, below3.scale, 1e-4f)
        assertEquals(2.2f, below3.blurRadius.value, 1e-4f)
        assertEquals(16f, below3.verticalOffset.value, 1e-4f)

        val d0 = computeStaggerDelayMs(4, 4)
        val d1 = computeStaggerDelayMs(5, 4)
        val d2 = computeStaggerDelayMs(6, 4)
        val d3 = computeStaggerDelayMs(7, 4)
        assertEquals(0, d0)
        assertEquals(50, d1)
        assertEquals(98, d2)
        assertTrue("Downstream stagger step must decay gently", (d3 - d2) < (d2 - d1))

        val solverItems = (0 until 10).map { i ->
            LyricsSpringLayoutItem(
                index = i,
                height = 60f,
                presentation = resolveLyricPresentation(i, activeIndex = 3, lineCount = 10, userScrolling = false),
            )
        }
        val solved = solveLyricsSpringLayout(
            items = solverItems,
            containerHeight = 600f,
            alignOffset = 3,
            scrollOffset = 0f,
            lineSpacing = 20f,
            staggerEnabled = true,
        )
        assertEquals(600f * 0.35f, solved[3].y + solved[3].height / 2f, 0.01f)
        assertTrue("Lines with y >= 0 stagger smoothly and decay past anchor", solved[4].delaySeconds > solved[3].delaySeconds)
        val limits = computeLyricsScrollLimits(solverItems, containerHeight = 600f, alignOffset = 3, lineSpacing = 20f)
        assertTrue("Can scroll up to first line (min <= 0) and down to last line (max >= 0)", limits.min < 0f && limits.max > 0f)
    }
    @Test fun lineAdvanceStaysUncutAndSmoothMidFlightWithoutWindowClipping() {
        render()
        // At index 0, line 3 (|d|=3, +16dp verticalOffset, inside unmasked center-bottom area) must render its bottom glyphs unclipped.
        val restLine3Bounds = rule.onNodeWithTag("player_lyric_3").fetchSemanticsNode().boundsInRoot
        lateinit var restCrop: Bitmap
        rule.runOnIdle {
            val frame = Bitmap.createBitmap(contentView.width, contentView.height, Bitmap.Config.ARGB_8888)
            contentView.draw(android.graphics.Canvas(frame))
            val top = (restLine3Bounds.bottom - 18f).roundToInt().coerceIn(0, frame.height - 1)
            val bottom = (restLine3Bounds.bottom - 5f).roundToInt().coerceIn(top + 1, frame.height)
            val left = restLine3Bounds.left.roundToInt().coerceIn(0, frame.width - 1)
            restCrop = Bitmap.createBitmap(
                frame,
                left,
                top,
                restLine3Bounds.width.roundToInt().coerceAtLeast(1).coerceAtMost(frame.width - left),
                bottom - top,
            )
        }
        val restPixels = restCrop.asImageBitmap().toPixelMap()
        var litPixelsAtBottom = 0
        for (y in 0 until restPixels.height) for (x in 0 until restPixels.width) {
            if (restPixels[x, y].red > 0.18f) litPixelsAtBottom++
        }
        assertTrue("Line 3 with +16dp offset must not clip bottom text glyphs (lit=$litPixelsAtBottom)", litPixelsAtBottom > 12)

        // Advance 0 -> 1 and verify mid-flight smoothness + visible unclipped rendering + settled focus.
        val beforeTop = rule.onNodeWithTag("player_lyric_1").fetchSemanticsNode().boundsInRoot.top
        rule.runOnIdle { lyrics = lyrics.copy(activeLyricIndex = 1) }
        settle(180)
        val midBounds = rule.onNodeWithTag("player_lyric_1").fetchSemanticsNode().boundsInRoot
        assertTrue("Line 1 must move upward smoothly mid-flight ($beforeTop -> ${midBounds.top})", midBounds.top < beforeTop)
        lateinit var midCrop: Bitmap
        rule.runOnIdle {
            val frame = Bitmap.createBitmap(contentView.width, contentView.height, Bitmap.Config.ARGB_8888)
            contentView.draw(android.graphics.Canvas(frame))
            val left = midBounds.left.roundToInt().coerceIn(0, frame.width - 1)
            val top = midBounds.top.roundToInt().coerceIn(0, frame.height - 1)
            midCrop = Bitmap.createBitmap(
                frame,
                left,
                top,
                midBounds.width.roundToInt().coerceAtLeast(1).coerceAtMost(frame.width - left),
                midBounds.height.roundToInt().coerceAtLeast(1).coerceAtMost(frame.height - top),
            )
        }
        val midPixels = midCrop.asImageBitmap().toPixelMap()
        var midLit = 0
        for (y in 0 until midPixels.height) for (x in 0 until midPixels.width) {
            if (midPixels[x, y].red > 0.45f) midLit++
        }
        assertTrue("Active advancing line must remain visibly drawn mid-flight without clip vanishing (lit=$midLit)", midLit > 50)
        settle(1200)
        assertFocused(1)
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
