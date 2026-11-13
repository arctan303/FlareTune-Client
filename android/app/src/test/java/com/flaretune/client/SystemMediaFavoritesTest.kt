package com.flaretune.client

import com.flaretune.client.data.*
import com.flaretune.client.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemMediaFavoritesTest {
    @Test fun completeMembershipIncludesUnavailableSongsAndReadsOnlyOncePerTrack() = runTest {
        val access = FakeAccess(detail(5, "one"))
        val coordinator = SystemMediaFavorites(backgroundScope, access, { true }, {})
        coordinator.observeSong("one"); runCurrent()
        assertEquals(true, coordinator.state.value.saved)
        repeat(10) { coordinator.observeSong("one") }; runCurrent()
        assertEquals(1, access.loads)
        assertFalse(coordinator.state.value.loading)
        coordinator.close()
    }

    @Test fun oldNotificationActionCannotMutateTheNextTrackOrAnAtoBtoAReturn() = runTest {
        val access = FakeAccess(detail(1))
        val coordinator = SystemMediaFavorites(backgroundScope, access, { true }, {})
        coordinator.observeSong("one"); runCurrent()
        val old = coordinator.state.value.commandId
        coordinator.observeSong("two"); runCurrent()
        assertEquals(SystemMediaFavoriteResult.REJECTED, coordinator.perform(old))
        coordinator.observeSong("one"); runCurrent()
        assertEquals(SystemMediaFavoriteResult.REJECTED, coordinator.perform(old))
        assertEquals(0, access.writes.size)
        coordinator.close()
    }

    @Test fun delayedPreviousTrackReadCannotReplaceTheCurrentTrack() = runTest {
        val first = CompletableDeferred<PlaylistDetail>()
        val access = FakeAccess(detail(2, "two"))
        access.loadAction = { if (access.loads == 1) first.await() else access.detail }
        val coordinator = SystemMediaFavorites(backgroundScope, access, { true }, {})
        coordinator.observeSong("one"); runCurrent()
        coordinator.observeSong("two"); runCurrent()
        first.complete(detail(1, "one")); runCurrent()
        assertEquals("two", coordinator.state.value.songId)
        assertEquals(true, coordinator.state.value.saved)
        assertEquals(0, access.writes.size)
        coordinator.close()
    }

    @Test fun retiredConnectionAndLateMutationAcknowledgementNeverPublishSuccess() = runTest {
        val waiting = CompletableDeferred<PlaylistDetail>()
        val access = FakeAccess(detail(1))
        access.setAction = { _, _, _ -> waiting.await() }
        var current = true
        val published = mutableListOf<PlaylistDetail>()
        val coordinator = SystemMediaFavorites(backgroundScope, access, { current }, published::add)
        coordinator.observeSong("one"); runCurrent()
        val work = async { coordinator.perform(coordinator.state.value.commandId) }; runCurrent()
        assertTrue(coordinator.state.value.busy)
        current = false
        waiting.complete(detail(2, "one")); runCurrent()
        assertEquals(SystemMediaFavoriteResult.FAILED, work.await())
        assertEquals(0, published.size)
        assertEquals(false, coordinator.state.value.saved)
        assertEquals(SystemMediaFavoriteResult.REJECTED, coordinator.perform(coordinator.state.value.commandId))
        coordinator.close()
    }

    @Test fun duplicateTapIsRejectedAndSelectionChangesOnlyAfterConfirmedSuccess() = runTest {
        val waiting = CompletableDeferred<PlaylistDetail>()
        val access = FakeAccess(detail(1))
        access.setAction = { _, _, _ -> waiting.await() }
        val published = mutableListOf<PlaylistDetail>()
        val coordinator = SystemMediaFavorites(backgroundScope, access, { true }, published::add)
        coordinator.observeSong("one"); runCurrent()
        val command = coordinator.state.value.commandId
        val work = async { coordinator.perform(command) }; runCurrent()
        assertEquals(false, coordinator.state.value.saved)
        assertEquals(SystemMediaFavoriteResult.REJECTED, coordinator.perform(command))
        waiting.complete(detail(2, "one")); runCurrent()
        assertEquals(SystemMediaFavoriteResult.SUCCESS, work.await())
        assertEquals(true, coordinator.state.value.saved)
        assertEquals(1, access.writes.size)
        assertEquals(1, published.size)
        coordinator.close()
    }

    @Test fun failedMutationKeepsConfirmedSelectionAndCanBeExplicitlyRetried() = runTest {
        val access = FakeAccess(detail(1))
        access.setAction = { _, _, _ -> throw ApiFailure(Failure.NETWORK) }
        val published = mutableListOf<PlaylistDetail>()
        val coordinator = SystemMediaFavorites(backgroundScope, access, { true }, published::add)
        coordinator.observeSong("one"); runCurrent()
        assertEquals(SystemMediaFavoriteResult.FAILED, coordinator.perform(coordinator.state.value.commandId))
        assertFalse(coordinator.state.value.saved!!)
        assertTrue(coordinator.state.value.failed)
        assertTrue(published.isEmpty())
        access.setAction = { _, _, _ -> detail(2, "one") }
        assertEquals(SystemMediaFavoriteResult.SUCCESS, coordinator.perform(coordinator.state.value.commandId))
        assertTrue(coordinator.state.value.saved!!)
        assertFalse(coordinator.state.value.failed)
        coordinator.close()
    }

    @Test fun failedStatusRetryOnlyReadsAndNeverBlindlyChangesFavorites() = runTest {
        val access = FakeAccess(detail(1, "one"))
        access.loadAction = { throw ApiFailure(Failure.NETWORK) }
        val coordinator = SystemMediaFavorites(backgroundScope, access, { true }, {})
        coordinator.observeSong("one"); runCurrent()
        assertNull(coordinator.state.value.saved)
        assertTrue(coordinator.state.value.failed)
        access.loadAction = { access.detail }
        assertEquals(SystemMediaFavoriteResult.SUCCESS, coordinator.perform(coordinator.state.value.commandId))
        assertEquals(true, coordinator.state.value.saved)
        assertTrue(access.writes.isEmpty())
        coordinator.close()
    }

    @Test fun newerUiAcknowledgementInvalidatesOlderReadsAndOlderRevisionsAreIgnored() = runTest {
        val waiting = CompletableDeferred<PlaylistDetail>()
        val access = FakeAccess(detail(1))
        access.loadAction = { waiting.await() }
        val coordinator = SystemMediaFavorites(backgroundScope, access, { true }, {})
        coordinator.observeSong("one"); runCurrent()
        coordinator.adopt(detail(9, "one"))
        waiting.complete(detail(2)); runCurrent()
        coordinator.adopt(detail(8))
        assertEquals(true, coordinator.state.value.saved)
        assertFalse(coordinator.state.value.loading)
        coordinator.adopt(PlaylistDetail(PlaylistSummary("other", "Not favorite", "member", revision = 99), emptyList()))
        assertEquals(true, coordinator.state.value.saved)
        coordinator.close()
    }

    @Test fun newerUiStateWinsOverAnOlderMutationReplyWithoutPublishingTheOldReply() = runTest {
        val waiting = CompletableDeferred<PlaylistDetail>()
        val access = FakeAccess(detail(1))
        access.setAction = { _, _, _ -> waiting.await() }
        val published = mutableListOf<PlaylistDetail>()
        val coordinator = SystemMediaFavorites(backgroundScope, access, { true }, published::add)
        coordinator.observeSong("one"); runCurrent()
        val work = async { coordinator.perform(coordinator.state.value.commandId) }; runCurrent()
        coordinator.adopt(detail(10))
        waiting.complete(detail(9, "one")); runCurrent()
        assertEquals(SystemMediaFavoriteResult.SUCCESS, work.await())
        assertEquals(false, coordinator.state.value.saved)
        assertTrue(published.isEmpty())
        assertFalse(coordinator.state.value.busy)
        coordinator.close()
    }

    @Test fun staleReadForANewTrackIsAnExplicitReadFailureNeverAnUnconfirmedAddButton() = runTest {
        val access = FakeAccess(detail(9, "one"))
        val coordinator = SystemMediaFavorites(backgroundScope, access, { true }, {})
        coordinator.observeSong("one"); runCurrent()
        access.detail = detail(8)
        coordinator.observeSong("two"); runCurrent()
        assertNull(coordinator.state.value.saved)
        assertTrue(coordinator.state.value.failed)
        assertEquals(SystemMediaFavoriteResult.FAILED, coordinator.perform(coordinator.state.value.commandId))
        assertTrue(access.writes.isEmpty())
        access.detail = detail(10, "two")
        assertEquals(SystemMediaFavoriteResult.SUCCESS, coordinator.perform(coordinator.state.value.commandId))
        assertEquals(true, coordinator.state.value.saved)
        assertTrue(access.writes.isEmpty())
        coordinator.close()
    }

    @Test fun productionAdapterUsesModernIdFreshDetailRevisionAndConfirmedReadback() = runTest {
        val api = FakeAccountApi()
        api.current = detail(8, "unavailable")
        val connection = connection(api)
        try {
            val access = SystemMediaFavoriteApi(connection, StandardTestDispatcher(testScheduler))
            val work = async { access.set("one", true) {} }; runCurrent()
            assertEquals(listOf(PlaylistTarget("favorite-real-id", 8)), api.addTargets)
            assertEquals(listOf("one"), api.addIds)
            assertEquals(listOf("unavailable", "one"), work.await().songIds)
            assertEquals(0, api.legacyReads)
            assertEquals(2, api.detailReads)
        } finally { connection.network.retire() }
    }

    @Test fun productionAdapterRemovesOnlyTheRequestedMemberAndDoesNotReplaceTheCollection() = runTest {
        val api = FakeAccountApi()
        api.current = detail(4, "one", "unavailable", "third")
        val connection = connection(api)
        try {
            val work = async { SystemMediaFavoriteApi(connection, StandardTestDispatcher(testScheduler)).set("one", false) {} }; runCurrent()
            assertEquals(Triple("favorite-real-id", "one", 4L), api.remove)
            assertEquals(listOf("unavailable", "third"), work.await().songIds)
            assertTrue(api.addTargets.isEmpty())
        } finally { connection.network.retire() }
    }

    @Test fun changedIdentityBetweenIndexAndDetailPreventsFurtherRequestsAndWrites() = runTest {
        val api = FakeAccountApi()
        var active = true
        api.onIndex = { active = false }
        val connection = connection(api)
        try {
            val result = async {
                runCatching { SystemMediaFavoriteApi(connection, StandardTestDispatcher(testScheduler)).set("one", true) {
                    if (!active) throw ApiFailure(Failure.RETIRED)
                } }
            }; runCurrent()
            assertEquals(Failure.RETIRED, (result.await().exceptionOrNull() as ApiFailure).failure)
            assertEquals(0, api.detailReads)
            assertTrue(api.addTargets.isEmpty())
            assertNull(api.remove)
        } finally { connection.network.retire() }
    }

    @Test fun revisionConflictIsNotRetriedAndAWriteWithoutConfirmationIsNotSuccess() = runTest {
        val api = FakeAccountApi()
        api.addError = ApiFailure(Failure.REQUEST, 409, "REVISION_CONFLICT")
        val connection = connection(api)
        try {
            val access = SystemMediaFavoriteApi(connection, StandardTestDispatcher(testScheduler))
            val conflict = async { runCatching { access.set("one", true) {} } }; runCurrent()
            assertEquals(409, (conflict.await().exceptionOrNull() as ApiFailure).httpStatus)
            assertEquals(1, api.writeAttempts)
            api.addError = null
            api.ignoreWrite = true
            val unconfirmed = async { runCatching { access.set("one", true) {} } }; runCurrent()
            assertEquals(Failure.INVALID_RESPONSE, (unconfirmed.await().exceptionOrNull() as ApiFailure).failure)
        } finally { connection.network.retire() }
    }

    @Test fun alreadyDesiredMembershipIsANoopAndNeverSendsAMutation() = runTest {
        val api = FakeAccountApi()
        api.current = detail(2, "one")
        val connection = connection(api)
        try {
            val work = async { SystemMediaFavoriteApi(connection, StandardTestDispatcher(testScheduler)).set("one", true) {} }; runCurrent()
            assertEquals(listOf("one"), work.await().songIds)
            assertEquals(0, api.writeAttempts)
        } finally { connection.network.retire() }
    }

    private class FakeAccess(var detail: PlaylistDetail) : SystemMediaFavoriteAccess {
        var loads = 0
        val writes = mutableListOf<Pair<String, Boolean>>()
        var loadAction: suspend () -> PlaylistDetail = { detail }
        var setAction: suspend (String, Boolean, () -> Unit) -> PlaylistDetail = { _, _, _ -> detail }
        override suspend fun load(guard: () -> Unit): PlaylistDetail { guard(); loads++; return loadAction().also { guard() } }
        override suspend fun set(songId: String, desired: Boolean, guard: () -> Unit): PlaylistDetail {
            guard(); writes += songId to desired; return setAction(songId, desired, guard)
        }
    }

    private class FakeAccountApi : AccountApi {
        var current = detail(1)
        var detailReads = 0
        var legacyReads = 0
        var addTargets = emptyList<PlaylistTarget>()
        var addIds = emptyList<String>()
        var remove: Triple<String, String, Long>? = null
        var onIndex: () -> Unit = {}
        var addError: Exception? = null
        var ignoreWrite = false
        var writeAttempts = 0
        override fun songs(query: String, offset: Int): SongPage = error("Unexpected songs")
        override fun favorites(): List<Song> { legacyReads++; error("No legacy favorites path") }
        override fun changePassword(current: String, replacement: String): Unit = error("Unexpected password write")
        override fun logout(): Unit = error("Unexpected logout")
        override fun playlists(): List<PlaylistSummary> { onIndex(); return listOf(current.playlist.copy(revision = 0)) }
        override fun playlist(id: String): PlaylistDetail { check(id == "favorite-real-id"); detailReads++; return current }
        override fun addPlaylistSongs(targets: List<PlaylistTarget>, songIds: List<String>): PlaylistAddResult {
            writeAttempts++; addTargets = targets; addIds = songIds; addError?.let { throw it }
            if (!ignoreWrite) current = detail(current.playlist.revision + 1, *(current.songIds + songIds).toTypedArray())
            return PlaylistAddResult("applied", listOf(PlaylistTargetResult("favorite-real-id", "applied", songIds)), listOf("favorite-real-id"))
        }
        override fun removePlaylistSong(id: String, songId: String, expectedRevision: Long): PlaylistMutationResult {
            writeAttempts++; remove = Triple(id, songId, expectedRevision)
            current = detail(current.playlist.revision + 1, *current.songIds.filterNot { it == songId }.toTypedArray())
            return PlaylistMutationResult(current.playlist, current)
        }
    }

    private fun connection(api: AccountApi): ActiveConnection = ActiveConnection(
        SessionNetwork(InstanceAddress.parse("https://music.example"), baseClient = OkHttpClient.Builder()
            .addInterceptor { throw AssertionError("No real HTTP is allowed") }.build()), api,
        AuthSession(Account("system-account", "listener", "Listener"), "fixture-csrf", false))

    companion object {
        private fun detail(revision: Long, vararg ids: String) = PlaylistDetail(
            PlaylistSummary("favorite-real-id", "Favorites", "favorite", revision = revision),
            ids.map { PlaylistSong(Song(it, "Track $it", audioUrl = "")) })
    }
}
