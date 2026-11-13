package com.flaretune.client

import com.flaretune.client.data.*
import com.flaretune.client.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsCoordinatorTest {
    private val song = Song("song-one", "First song", audioUrl = "https://music.example/media/one.mp3")

    @Test fun onlyTheExactInstrumentalLanguageSkipsTheServer() = runTest {
        val api = LyricsFakeApi()
        val connections = LyricsFakeConnections(api)
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(connections.current, song.copy(language = "instrumental"))
        runCurrent()
        assertEquals(LyricStatus.INSTRUMENTAL, coordinator.state.value.status)
        assertEquals(listOf(LyricLine("纯音乐，请欣赏", timeSeconds = 0.0)), coordinator.state.value.lines)
        assertEquals(0, api.reads.size)
        assertEquals(0, coordinator.state.value.activeLyricIndex)
        coordinator.updatePosition(90_000)
        assertEquals(0, coordinator.state.value.activeLyricIndex)

        for ((index, language) in listOf(null, "", "unknown", "Instrumental").withIndex()) {
            coordinator.activate(connections.current, song.copy(id = "unknown-" + index, language = language))
            runCurrent()
            assertEquals(LyricStatus.READY, coordinator.state.value.status)
            assertEquals(index + 1, api.reads.size)
            assertNull(api.reads.last().second)
        }
        coordinator.clear()
    }

    @Test fun aServerInstrumentalUnionUsesTheSameFixedMessage() = runTest {
        val api = LyricsFakeApi().apply {
            read = { _, _ -> LyricDocument(instrumental = true, lines = listOf(LyricLine("ignored"))) }
        }
        val connections = LyricsFakeConnections(api)
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(connections.current, song)
        advanceUntilIdle()
        assertEquals(LyricStatus.INSTRUMENTAL, coordinator.state.value.status)
        assertEquals("纯音乐，请欣赏", coordinator.state.value.lines.single().text)
        assertEquals(LyricTranslationStatus.NOT_NEEDED, coordinator.state.value.translationState)
        assertEquals(0, coordinator.state.value.activeLyricIndex)
        coordinator.requestTranslation()
        coordinator.retry()
        advanceUntilIdle()
        assertEquals(1, api.reads.size)
        assertTrue(api.translations.isEmpty())
    }

    @Test fun activationIgnoresPlaybackAndCosmeticChangesButReactsToSongLanguageAndIdentity() = runTest {
        val api = LyricsFakeApi()
        val connections = LyricsFakeConnections(api)
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(connections.current, song)
        advanceUntilIdle()
        coordinator.updatePosition(4_000)
        repeat(3) { coordinator.activate(connections.current, song.copy(title = "updated metadata", language = "")) }
        advanceUntilIdle()
        assertEquals(1, api.reads.size)

        coordinator.activate(connections.current, song.copy(language = "en"))
        advanceUntilIdle()
        assertEquals(2, api.reads.size)
        val changedSession = connections.current!!.copy(
            auth = connections.current!!.auth.copy(csrfToken = "replacement-session"),
        )
        coordinator.activate(changedSession, song.copy(language = "en"))
        advanceUntilIdle()
        assertEquals(3, api.reads.size)
        coordinator.activate(changedSession, song.copy(id = "song-two", language = "en"))
        advanceUntilIdle()
        assertEquals(4, api.reads.size)
        assertTrue(api.reads.all { it.second == null })
    }

    @Test fun positionUsesSecondsAndKeepsUntimedAndLeadingContentUnselected() = runTest {
        val words = listOf(LyricWord("second", 4.0, 4.7))
        val lines = listOf(
            LyricLine("first", timeSeconds = 2.5, endTimeSeconds = 3.5),
            LyricLine("untimed"),
            LyricLine("second", timeSeconds = 4.0, words = words),
        )
        val api = LyricsFakeApi().apply { read = { _, _ -> lyricDoc(lines = lines, syncMode = "word") } }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(LyricsFakeConnections(api).current, song)
        advanceUntilIdle()
        assertEquals(-1, coordinator.state.value.activeLyricIndex)
        for ((position, index) in listOf(2_499L to -1, 2_500L to 0, 3_999L to 0, 4_000L to 2, 1_000L to -1)) {
            coordinator.updatePosition(position)
            assertEquals(index, coordinator.state.value.activeLyricIndex)
        }
        assertEquals(words, coordinator.state.value.lines.last().words)
        api.read = { _, _ -> lyricDoc(lines = lines, syncMode = "none") }
        coordinator.retry()
        advanceUntilIdle()
        coordinator.updatePosition(9_000)
        assertEquals(-1, coordinator.state.value.activeLyricIndex)
    }

    @Test fun aPositionReceivedDuringTheRequestIsAppliedWhenLyricsArrive() = runTest {
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(LyricsFakeConnections(LyricsFakeApi()).current, song)
        coordinator.updatePosition(3_000)
        advanceUntilIdle()
        assertEquals(0, coordinator.state.value.activeLyricIndex)
    }

    @Test fun pendingPollingUsesTheLatestReferenceAndStopsAtTheBoundWithoutClaimingSuccess() = runTest {
        val api = LyricsFakeApi().apply {
            read = { _, _ ->
                lyricDoc(ref = "pending-" + reads.size, translationState = LyricTranslationStatus.PENDING, supported = true)
            }
        }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler), 1_000, 2)
        coordinator.activate(LyricsFakeConnections(api).current, song)
        runCurrent()
        assertTrue(coordinator.state.value.polling)
        assertEquals(1, api.reads.size)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(2, api.reads.size)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(null, "pending-1", "pending-2"), api.reads.map { it.second })
        assertFalse(coordinator.state.value.polling)
        assertEquals(LyricStatus.READY, coordinator.state.value.status)
        assertEquals(LyricTranslationStatus.PENDING, coordinator.state.value.translationState)
        advanceTimeBy(100_000); runCurrent()
        assertEquals(3, api.reads.size)

        api.read = { _, _ -> lyricDoc(ref = "complete", translationState = LyricTranslationStatus.READY, supported = true) }
        coordinator.retry()
        advanceUntilIdle()
        assertEquals("pending-3", api.reads.last().second)
        assertEquals(LyricTranslationStatus.READY, coordinator.state.value.translationState)
    }

    @Test fun zeroPollBudgetStillReadsOnceAndKeepsPendingForAnExplicitRetry() = runTest {
        val api = LyricsFakeApi().apply {
            read = { _, _ -> lyricDoc(translationState = LyricTranslationStatus.PENDING) }
        }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler), 1_000, 0)
        coordinator.activate(LyricsFakeConnections(api).current, song)
        advanceUntilIdle()
        assertEquals(1, api.reads.size)
        assertFalse(coordinator.state.value.polling)
        assertEquals(LyricTranslationStatus.PENDING, coordinator.state.value.translationState)
        coordinator.retry()
        advanceUntilIdle()
        assertEquals(2, api.reads.size)
    }

    @Test fun aRetiredReferenceReloadsTheChainOnceWithoutReusingTheOldReference() = runTest {
        for (code in listOf("LYRIC_RESULT_RETIRED", "LYRIC_RESULT_CHANGED")) {
            val api = LyricsFakeApi().apply {
                read = { _, reference ->
                    when (reads.size) {
                        1 -> lyricDoc(ref = "retired", translationState = LyricTranslationStatus.PENDING)
                        2 -> throw ApiFailure(Failure.REQUEST, httpStatus = 409, code = code)
                        else -> lyricDoc(text = "replacement", ref = "new")
                    }
                }
            }
            val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler), 1_000, 2)
            coordinator.activate(LyricsFakeConnections(api).current, song)
            advanceUntilIdle()
            assertEquals(listOf(null, "retired", null), api.reads.map { it.second })
            assertEquals("replacement", coordinator.state.value.lines.single().text)
            assertEquals(LyricStatus.READY, coordinator.state.value.status)
        }
    }

    @Test fun repeatedRetirementCannotCreateAnAutomaticReloadLoop() = runTest {
        val api = LyricsFakeApi().apply {
            read = { _, reference ->
                if (reference == null) lyricDoc(ref = "ref-" + reads.size, translationState = LyricTranslationStatus.PENDING)
                else throw ApiFailure(Failure.REQUEST, httpStatus = 409, code = "LYRIC_RESULT_CHANGED")
            }
        }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler), 1_000, 10)
        coordinator.activate(LyricsFakeConnections(api).current, song)
        advanceUntilIdle()
        assertEquals(4, api.reads.size)
        assertEquals(2, api.reads.count { it.second == null })
        assertEquals(LyricStatus.FAILED, coordinator.state.value.status)
        assertFalse(coordinator.state.value.polling)
    }

    @Test fun invalidReferencesAreNotAutomaticallyReplayedAndManualRetryStartsFresh() = runTest {
        val api = LyricsFakeApi().apply {
            read = { _, _ ->
                if (reads.size == 1) lyricDoc(ref = "invalid", translationState = LyricTranslationStatus.PENDING)
                else throw ApiFailure(Failure.REQUEST, httpStatus = 400, code = "LYRIC_RESULT_INVALID")
            }
        }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler), 1_000, 2)
        coordinator.activate(LyricsFakeConnections(api).current, song)
        advanceUntilIdle()
        assertEquals(listOf(null, "invalid"), api.reads.map { it.second })
        assertEquals(LyricStatus.FAILED, coordinator.state.value.status)
        api.read = { _, _ -> lyricDoc() }
        coordinator.retry()
        advanceUntilIdle()
        assertNull(api.reads.last().second)
        assertEquals(LyricStatus.READY, coordinator.state.value.status)
    }

    @Test fun onlyLyricsNotFoundBecomesEmptyWhileOtherErrorsUseSafeNotices() = runTest {
        val api = LyricsFakeApi()
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        val connection = LyricsFakeConnections(api).current
        api.read = { _, _ -> throw ApiFailure(Failure.REQUEST, httpStatus = 404, reason = "lyrics_not_found") }
        coordinator.activate(connection, song)
        advanceUntilIdle()
        assertEquals(LyricStatus.EMPTY, coordinator.state.value.status)
        assertNull(coordinator.state.value.notice)
        api.read = { _, _ -> throw ApiFailure(Failure.REQUEST, httpStatus = 404, reason = "song_not_found") }
        coordinator.retry()
        advanceUntilIdle()
        assertEquals(LyricStatus.FAILED, coordinator.state.value.status)
        assertEquals(ClientNotice.REQUEST_FAILED, coordinator.state.value.notice)
        api.read = { _, _ -> throw ApiFailure(Failure.NETWORK) }
        coordinator.retry()
        advanceUntilIdle()
        assertEquals(ClientNotice.NETWORK, coordinator.state.value.notice)
    }

    @Test fun translationRequiresCapabilityAndAReferenceAndNeverPostsTwiceWhileBusy() = runTest {
        val api = LyricsFakeApi()
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler), 1_000, 2)
        val connection = LyricsFakeConnections(api).current
        coordinator.activate(connection, song)
        advanceUntilIdle()
        coordinator.requestTranslation()
        advanceUntilIdle()
        assertTrue(api.translations.isEmpty())

        api.read = { _, _ -> lyricDoc(ref = null, supported = true, translationState = LyricTranslationStatus.IDLE) }
        coordinator.retry()
        advanceUntilIdle()
        coordinator.requestTranslation()
        advanceUntilIdle()
        assertTrue(api.translations.isEmpty())

        api.read = { _, _ -> lyricDoc(ref = "own-ref", supported = true, translationState = LyricTranslationStatus.IDLE) }
        coordinator.retry()
        advanceUntilIdle()
        api.translate = {
            lyricDoc(ref = "translated-ref", supported = true, translationState = LyricTranslationStatus.PENDING)
        }
        api.read = { _, _ ->
            lyricDoc(ref = "ready-ref", lines = listOf(LyricLine("original", "译文", 1.0)),
                supported = true, translationState = LyricTranslationStatus.READY)
        }
        coordinator.requestTranslation()
        coordinator.requestTranslation()
        runCurrent()
        assertEquals(listOf("own-ref"), api.translations)
        assertTrue(coordinator.state.value.polling)
        advanceUntilIdle()
        assertEquals("translated-ref", api.reads.last().second)
        assertEquals("译文", coordinator.state.value.lines.single().translation)
        assertFalse(coordinator.state.value.polling)
        coordinator.requestTranslation()
        advanceUntilIdle()
        assertEquals(1, api.translations.size)
    }

    @Test fun serverCapabilityControlsGenerationWithoutClientLanguageJudgement() = runTest {
        val original = listOf(LyricLine("original", "existing foreign translation", 1.0))
        val api = LyricsFakeApi().apply {
            read = { _, _ -> lyricDoc(lines = original, supported = true, translationState = LyricTranslationStatus.IDLE,
                translationLanguage = "en", translationTargetLanguage = "zh_CN") }
            translate = { lyricDoc(lines = original, supported = true, translationState = LyricTranslationStatus.READY,
                translationLanguage = "en", translationTargetLanguage = "zh_CN") }
        }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(LyricsFakeConnections(api).current, song.copy(language = "ZH-cn"))
        advanceUntilIdle()
        assertEquals("en", coordinator.state.value.translationLanguage)
        assertEquals("zh_CN", coordinator.state.value.translationTargetLanguage)
        coordinator.requestTranslation(); advanceUntilIdle()
        assertEquals(1, api.translations.size)
        assertEquals(LyricStatus.READY, coordinator.state.value.status)
        assertEquals(original, coordinator.state.value.lines)
        coordinator.clear()
    }

    @Test fun differentOrUnknownTranslationLanguagesStillPermitAnExplicitPost() = runTest {
        for ((source, target) in listOf("zh" to "zh", "zh" to "en", "zh" to "zh-Hant", null to "zh", "zh" to null,
            "unknown" to "unknown", "en" to "future-target")) {
            val api = LyricsFakeApi().apply {
                read = { _, _ -> lyricDoc(ref = "current", supported = true, translationState = LyricTranslationStatus.IDLE,
                    translationTargetLanguage = target) }
            }
            val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
            coordinator.activate(LyricsFakeConnections(api).current, song.copy(language = source))
            advanceUntilIdle()
            assertTrue(api.translations.isEmpty())
            coordinator.requestTranslation(); advanceUntilIdle()
            assertEquals("$source / $target", listOf("current"), api.translations)
            coordinator.clear()
        }
    }

    @Test fun activationAndPendingReadsNeverInitiateTranslationPosts() = runTest {
        val api = LyricsFakeApi().apply {
            read = { _, _ -> lyricDoc(ref = "read-${reads.size}", supported = true,
                translationState = if (reads.size < 3) LyricTranslationStatus.PENDING else LyricTranslationStatus.IDLE,
                translationTargetLanguage = "zh") }
        }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler), 1_000, 3)
        coordinator.activate(LyricsFakeConnections(api).current, song.copy(language = "zh"))
        advanceUntilIdle()
        assertEquals(3, api.reads.size)
        assertTrue(api.translations.isEmpty())
        assertEquals("zh", coordinator.state.value.translationTargetLanguage)
        coordinator.clear()
    }

    @Test fun failedExplicitTranslationKeepsOriginalAndReferenceForARealRetry() = runTest {
        for (error in listOf(ApiFailure(Failure.NETWORK), ApiFailure(Failure.REQUEST, httpStatus = 503))) {
            val original = listOf(LyricLine("first", timeSeconds = 1.0), LyricLine("second", timeSeconds = 5.0))
            val api = LyricsFakeApi().apply {
                read = { _, _ -> lyricDoc(ref = "original-ref", lines = original, supported = true,
                    translationState = LyricTranslationStatus.IDLE, translationTargetLanguage = "zh") }
                translate = { throw error }
            }
            val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
            coordinator.activate(LyricsFakeConnections(api).current, song.copy(language = "en"))
            advanceUntilIdle(); coordinator.updatePosition(6_000)
            coordinator.requestTranslation(); advanceUntilIdle()
            val failed = coordinator.state.value
            assertEquals(LyricStatus.READY, failed.status)
            assertEquals(original, failed.lines)
            assertEquals(1, failed.activeLyricIndex)
            assertEquals(LyricTranslationStatus.FAILED, failed.translationState)
            assertEquals(if (error.failure == Failure.NETWORK) ClientNotice.NETWORK else ClientNotice.REQUEST_FAILED, failed.notice)
            assertEquals("zh", failed.translationTargetLanguage)
            api.translate = { lyricDoc(lines = original.map { it.copy(translation = "translated") }, supported = true,
                translationState = LyricTranslationStatus.READY, translationLanguage = "zh", translationTargetLanguage = "zh") }
            coordinator.requestTranslation(); advanceUntilIdle()
            assertEquals(listOf("original-ref", "original-ref"), api.translations)
            assertEquals(1, api.reads.size)
            assertEquals(LyricTranslationStatus.READY, coordinator.state.value.translationState)
            assertEquals(1, coordinator.state.value.activeLyricIndex)
            assertNull(coordinator.state.value.notice)
            coordinator.clear()
        }
    }

    @Test fun failedTranslationPollingKeepsTheLatestReferenceAndOriginalLines() = runTest {
        val original = listOf(LyricLine("original", timeSeconds = 1.0))
        val api = LyricsFakeApi().apply {
            read = { _, reference -> if (reference != null) throw ApiFailure(Failure.NETWORK)
                else lyricDoc(ref = "initial", lines = original, supported = true, translationState = LyricTranslationStatus.IDLE) }
            translate = { lyricDoc(ref = "pending-ref", lines = original, supported = true, translationState = LyricTranslationStatus.PENDING) }
        }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler), 1_000, 2)
        coordinator.activate(LyricsFakeConnections(api).current, song)
        advanceUntilIdle(); coordinator.updatePosition(2_000)
        coordinator.requestTranslation(); advanceUntilIdle()
        assertEquals(LyricStatus.READY, coordinator.state.value.status)
        assertEquals(original, coordinator.state.value.lines)
        assertEquals(0, coordinator.state.value.activeLyricIndex)
        assertEquals(LyricTranslationStatus.FAILED, coordinator.state.value.translationState)
        assertEquals(ClientNotice.NETWORK, coordinator.state.value.notice)
        assertEquals("pending-ref", api.reads.last().second)
        api.translate = { lyricDoc(lines = original, supported = true, translationState = LyricTranslationStatus.READY) }
        coordinator.requestTranslation(); advanceUntilIdle()
        assertEquals(listOf("initial", "pending-ref"), api.translations)
        coordinator.clear()
    }

    @Test fun invalidTranslationReferencesAndRetiredSessionsKeepTheirExistingProtection() = runTest {
        val api = LyricsFakeApi().apply {
            read = { _, _ -> lyricDoc(ref = "invalid", supported = true, translationState = LyricTranslationStatus.IDLE) }
            translate = { throw ApiFailure(Failure.REQUEST, httpStatus = 400, code = "LYRIC_RESULT_INVALID") }
        }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(LyricsFakeConnections(api).current, song); advanceUntilIdle()
        coordinator.requestTranslation(); advanceUntilIdle()
        assertEquals(LyricStatus.FAILED, coordinator.state.value.status)
        coordinator.requestTranslation(); advanceUntilIdle()
        assertEquals(1, api.translations.size)
        coordinator.retry(); advanceUntilIdle()
        assertNull(api.reads.last().second)
        coordinator.clear()

        for (failure in listOf(Failure.SESSION_EXPIRED, Failure.RETIRED)) {
            val retiredApi = LyricsFakeApi().apply {
                read = { _, _ -> lyricDoc(supported = true, translationState = LyricTranslationStatus.IDLE) }
                translate = { throw ApiFailure(failure) }
            }
            val retired = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
            retired.activate(LyricsFakeConnections(retiredApi).current, song); advanceUntilIdle()
            retired.requestTranslation(); advanceUntilIdle()
            assertEquals(LyricState(), retired.state.value)
            retired.requestTranslation(); advanceUntilIdle()
            assertEquals(1, retiredApi.translations.size)
        }
    }

    @Test fun unavailableCannotGenerateEvenWhenCapabilityAndOldTranslationsRemain() = runTest {
        val original = listOf(LyricLine("original", "returned translation", 1.0))
        val api = LyricsFakeApi().apply { read = { _, _ -> lyricDoc(
            lines=original, supported=true, translationState=LyricTranslationStatus.UNAVAILABLE) } }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(LyricsFakeConnections(api).current, song)
        advanceUntilIdle()
        coordinator.requestTranslation()
        advanceUntilIdle()
        assertTrue("Unavailable generation must not POST", api.translations.isEmpty())
        assertEquals(original, coordinator.state.value.lines)
        coordinator.clear()
    }

    @Test fun notNeededNeverPostsEvenWhenCapabilityIsTrueAndLanguagesDiffer() = runTest {
        val api = LyricsFakeApi().apply { read = { _, _ -> lyricDoc(supported = true,
            translationState = LyricTranslationStatus.NOT_NEEDED, translationTargetLanguage = "en") } }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(LyricsFakeConnections(api).current, song.copy(language = "zh")); advanceUntilIdle()
        coordinator.requestTranslation(); advanceUntilIdle()
        assertTrue(api.translations.isEmpty())
        assertEquals(LyricStatus.READY, coordinator.state.value.status)
        coordinator.clear()
    }

    @Test fun aRetiredTranslationReferenceReloadsRatherThanPostingToAnotherSource() = runTest {
        val api = LyricsFakeApi().apply {
            read = { _, _ -> lyricDoc(ref = "original-ref", supported = true, translationState = LyricTranslationStatus.IDLE) }
            translate = { throw ApiFailure(Failure.REQUEST, httpStatus = 409, code = "LYRIC_RESULT_RETIRED") }
        }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(LyricsFakeConnections(api).current, song)
        advanceUntilIdle()
        coordinator.requestTranslation()
        advanceUntilIdle()
        assertEquals(listOf("original-ref"), api.translations)
        assertEquals(listOf(null, null), api.reads.map { it.second })
        assertEquals(LyricStatus.READY, coordinator.state.value.status)
    }

    @Test fun lateBlockingSongResponseCannotOverwriteTheNewSongOrLeakItsReference() = runTest {
        val api = LyricsFakeApi()
        val connections = LyricsFakeConnections(api)
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        val secondSong = song.copy(id = "song-two")
        api.read = { id, _ ->
            if (id == song.id) {
                // Re-enter while this synchronous fake cannot be cancelled.
                coordinator.activate(connections.current, secondSong)
                lyricDoc(text = "old-private-text", ref = "old-private-ref", supported = true)
            } else lyricDoc(text = "new-text", ref = "new-ref", supported = true, translationState = LyricTranslationStatus.IDLE)
        }
        coordinator.activate(connections.current, song)
        advanceUntilIdle()
        assertEquals(secondSong.id, coordinator.state.value.songId)
        assertEquals("new-text", coordinator.state.value.lines.single().text)
        assertFalse(coordinator.state.value.toString().contains("private"))
        assertFalse(coordinator.state.value.toString().contains("new-ref"))
        coordinator.requestTranslation()
        advanceUntilIdle()
        assertEquals(listOf("new-ref"), api.translations)
    }

    @Test fun aChangedConnectionRejectsAnOldResponseEvenWhenTheSongIdMatches() = runTest {
        val oldApi = LyricsFakeApi()
        val replacementApi = LyricsFakeApi().apply { read = { _, _ -> lyricDoc(text = "new-account") } }
        val old = LyricsFakeConnections(oldApi)
        val replacement = LyricsFakeConnections(replacementApi, accountId = "different-account")
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        oldApi.read = { _, _ ->
            coordinator.activate(replacement.current, song)
            lyricDoc(text = "old-account", ref = "do-not-expose")
        }
        coordinator.activate(old.current, song)
        advanceUntilIdle()
        assertEquals("new-account", coordinator.state.value.lines.single().text)
        assertEquals(1, replacementApi.reads.size)
        assertNull(replacementApi.reads.single().second)
    }

    @Test fun logoutAndExplicitClearRejectLateBlockingResultsAndDiscardReferences() = runTest {
        for (logout in listOf(false, true)) {
            val api = LyricsFakeApi()
            val connections = LyricsFakeConnections(api)
            val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
            api.read = { _, _ ->
                if (logout) {
                    connections.current!!.network.retire()
                    connections.current = null
                    coordinator.activate(connections.current, null)
                } else coordinator.clear()
                lyricDoc(text = "late-private-data", ref = "late-private-ref", supported = true)
            }
            coordinator.activate(connections.current, song)
            advanceUntilIdle()
            assertEquals(LyricState(), coordinator.state.value)
            coordinator.requestTranslation()
            coordinator.retry()
            advanceUntilIdle()
            assertEquals(1, api.reads.size)
            assertTrue(api.translations.isEmpty())
        }
    }

    @Test fun anInactiveNetworkOrLimitedAccountNeverPublishesOrRequestsLyrics() = runTest {
        val api = LyricsFakeApi()
        val connections = LyricsFakeConnections(api)
        val active = connections.current!!
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(active.copy(auth = active.auth.copy(mustChangePassword = true)), song)
        advanceUntilIdle()
        assertTrue(api.reads.isEmpty())
        assertEquals(LyricState(), coordinator.state.value)
        api.read = { _, _ ->
            active.network.retire()
            lyricDoc(text = "retired-data", ref = "retired-ref")
        }
        coordinator.activate(active, song)
        advanceUntilIdle()
        assertEquals(LyricState(), coordinator.state.value)
        coordinator.retry()
        coordinator.requestTranslation()
        advanceUntilIdle()
        assertEquals(1, api.reads.size)
    }

    @Test fun updatePositionDropsExistingLyricsAfterNetworkRetirement() = runTest {
        val api = LyricsFakeApi()
        val connection = LyricsFakeConnections(api).current!!
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(connection, song)
        advanceUntilIdle()
        assertEquals(LyricStatus.READY, coordinator.state.value.status)
        connection.network.retire()
        coordinator.updatePosition(1_000)
        assertEquals(LyricState(), coordinator.state.value)
    }

    @Test fun pendingWithoutAReferenceStopsWithAnExplicitInvalidResponseNotice() = runTest {
        val api = LyricsFakeApi().apply {
            read = { _, _ -> lyricDoc(ref = null, translationState = LyricTranslationStatus.PENDING) }
        }
        val coordinator = LyricsCoordinator(this, StandardTestDispatcher(testScheduler))
        coordinator.activate(LyricsFakeConnections(api).current, song)
        advanceUntilIdle()
        assertEquals(1, api.reads.size)
        assertFalse(coordinator.state.value.polling)
        assertEquals(LyricTranslationStatus.PENDING, coordinator.state.value.translationState)
        assertEquals(ClientNotice.INVALID_RESPONSE, coordinator.state.value.notice)
    }
}

private fun lyricDoc(
    text: String = "resolved",
    ref: String? = "ref-one",
    translationState: LyricTranslationStatus = LyricTranslationStatus.UNAVAILABLE,
    supported: Boolean = false,
    syncMode: String = "line",
    lines: List<LyricLine> = listOf(LyricLine(text, timeSeconds = 1.0)),
    translationLanguage: String? = null,
    translationTargetLanguage: String? = null,
) = LyricDocument(
    lines = lines,
    resultRef = ref,
    translationState = translationState,
    translationSupported = supported,
    sourceId = "file",
    syncMode = syncMode,
    translationLanguage = translationLanguage,
    translationTargetLanguage = translationTargetLanguage,
)

private class LyricsFakeApi : AccountApi {
    val reads = mutableListOf<Pair<String, String?>>()
    val translations = mutableListOf<String>()
    var read: (String, String?) -> LyricDocument = { _, _ -> lyricDoc() }
    var translate: (String) -> LyricDocument = {
        lyricDoc(translationState = LyricTranslationStatus.READY, supported = true)
    }
    override fun lyrics(songId: String, resultRef: String?): LyricDocument {
        reads += songId to resultRef
        return read(songId, resultRef)
    }
    override fun translateLyrics(resultRef: String): LyricDocument {
        translations += resultRef
        return translate(resultRef)
    }
    override fun songs(query: String, offset: Int) = SongPage(emptyList(), offset, false)
    override fun favorites(): List<Song> = emptyList()
    override fun changePassword(current: String, replacement: String) = Unit
    override fun logout() = Unit
}

private class LyricsFakeConnections(
    api: AccountApi,
    accountId: String = "account-one",
) : Connections {
    override var current: ActiveConnection? = ActiveConnection(
        SessionNetwork(InstanceAddress.parse("https://music.example")),
        api,
        AuthSession(Account(accountId, "listener", "Listener"), "csrf-one", false),
    )
    override val lastOrigin = "https://music.example"
    override val invalidations = MutableSharedFlow<ClientNotice>()
    override suspend fun restore() = current
    override suspend fun login(address: String, username: String, password: String) =
        current ?: throw ApiFailure(Failure.SESSION_EXPIRED)
    override suspend fun changePassword(current: String, replacement: String) {
        this.current?.network?.retire()
        this.current = null
    }
    override suspend fun logout() {
        current?.network?.retire()
        current = null
    }
}
