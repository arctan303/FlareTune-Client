package com.flaretune.client.data

import com.flaretune.client.model.PlaylistDetail
import com.flaretune.client.model.PlaylistTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal interface SystemMediaFavoriteAccess {
    suspend fun load(guard: () -> Unit): PlaylistDetail
    suspend fun set(songId: String, desired: Boolean, guard: () -> Unit): PlaylistDetail
}

/** Uses the same modern account APIs and revision checks as the app's library. */
internal class SystemMediaFavoriteApi(
    private val connection: ActiveConnection,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SystemMediaFavoriteAccess {
    override suspend fun load(guard: () -> Unit): PlaylistDetail = withContext(ioDispatcher) {
        guard()
        val favorite = connection.api.playlists().singleOrNull { it.kind == "favorite" }
            ?: throw ApiFailure(Failure.INVALID_RESPONSE)
        guard()
        connection.api.playlist(favorite.id).also {
            guard()
            if (it.playlist.kind != "favorite" || it.playlist.id != favorite.id) throw ApiFailure(Failure.INVALID_RESPONSE)
        }
    }

    override suspend fun set(songId: String, desired: Boolean, guard: () -> Unit): PlaylistDetail {
        // Refresh the complete collection, including unavailable members, immediately before any write.
        val before = load(guard)
        if (before.entries.any { it.song.id == songId } == desired) return before
        withContext(ioDispatcher) {
            guard()
            val favorite = before.playlist
            if (desired) {
                val result = connection.api.addPlaylistSongs(listOf(PlaylistTarget(favorite.id, favorite.revision)), listOf(songId))
                guard()
                val row = result.results.singleOrNull { it.playlistId == favorite.id }
                if (result.outcome !in setOf("applied", "noop") || row == null ||
                    row.outcome !in setOf("applied", "noop") || songId !in row.addedSongIds + row.noopSongIds)
                    throw ApiFailure(Failure.REQUEST)
            } else {
                val result = connection.api.removePlaylistSong(favorite.id, songId, favorite.revision)
                guard()
                if (result.playlist.id != favorite.id || result.playlist.kind != "favorite" || result.outcome !in setOf("applied", "noop"))
                    throw ApiFailure(Failure.INVALID_RESPONSE)
            }
        }
        return load(guard).also {
            if (it.entries.any { row -> row.song.id == songId } != desired) throw ApiFailure(Failure.INVALID_RESPONSE)
        }
    }
}

internal data class SystemMediaFavoriteState(
    val songId: String? = null,
    val saved: Boolean? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val commandId: String = newFavoriteCommand(),
)

private fun newFavoriteCommand() = "com.flaretune.client.FAVORITE." + UUID.randomUUID()

internal enum class SystemMediaFavoriteResult { SUCCESS, REJECTED, FAILED }

/** Main-thread owner. Old notification commands can never act on a different track or account. */
internal class SystemMediaFavorites(
    private val scope: CoroutineScope,
    private val access: SystemMediaFavoriteAccess,
    private val isCurrent: () -> Boolean,
    private val onChanged: (PlaylistDetail) -> Unit,
) {
    private val mutable = MutableStateFlow(SystemMediaFavoriteState())
    val state: StateFlow<SystemMediaFavoriteState> = mutable.asStateFlow()
    @Volatile private var generation = 0L
    private var loadingJob: Job? = null
    private var favoriteId: String? = null
    private var favoriteRevision = -1L

    fun observeSong(songId: String?) {
        if (state.value.songId == songId) return
        generation++
        loadingJob?.cancel()
        mutable.value = SystemMediaFavoriteState(songId = songId, loading = songId != null)
        if (songId != null) {
            val ticket = generation
            loadingJob = scope.launch { load(songId, ticket) }
        }
    }

    private fun guard(ticket: Long, songId: String) {
        if (generation != ticket || mutable.value.songId != songId || !isCurrent()) throw ApiFailure(Failure.RETIRED)
    }

    private suspend fun load(songId: String, ticket: Long): SystemMediaFavoriteResult = try {
        guard(ticket, songId)
        val detail = access.load { guard(ticket, songId) }
        guard(ticket, songId)
        if (apply(detail, songId) || mutable.value.saved != null) SystemMediaFavoriteResult.SUCCESS
        else SystemMediaFavoriteResult.FAILED
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (generation == ticket && mutable.value.songId == songId && isCurrent())
            mutable.value = mutable.value.copy(loading = false, busy = false, failed = true, commandId = newFavoriteCommand())
        SystemMediaFavoriteResult.FAILED
    }

    /** Accept authoritative same-account UI acknowledgements, rejecting older revisions. */
    fun adopt(detail: PlaylistDetail) {
        val current = state.value
        val songId = current.songId ?: return
        if (!isCurrent() || detail.playlist.kind != "favorite" ||
            (favoriteId != null && detail.playlist.id != favoriteId) || detail.playlist.revision < favoriteRevision) return
        val saved = detail.entries.any { it.song.id == songId }
        if (detail.playlist.revision == favoriteRevision && current.saved == saved && !current.loading) return
        if (!current.busy) {
            generation++
            loadingJob?.cancel()
        }
        favoriteId = detail.playlist.id
        favoriteRevision = detail.playlist.revision
        mutable.value = current.copy(saved = saved, loading = false, failed = false, commandId = newFavoriteCommand())
    }

    suspend fun perform(commandId: String): SystemMediaFavoriteResult {
        val before = state.value
        val songId = before.songId ?: return SystemMediaFavoriteResult.REJECTED
        if (commandId != before.commandId || before.loading || before.busy || !isCurrent()) return SystemMediaFavoriteResult.REJECTED
        val ticket = generation
        if (before.saved == null) {
            // A failed status read is a read retry, never a blind favorite/unfavorite operation.
            mutable.value = before.copy(loading = true, failed = false, commandId = newFavoriteCommand())
            return load(songId, ticket)
        }
        mutable.value = before.copy(busy = true, failed = false, commandId = newFavoriteCommand())
        return try {
            guard(ticket, songId)
            val detail = access.set(songId, !before.saved) { guard(ticket, songId) }
            guard(ticket, songId)
            if (apply(detail, songId)) onChanged(detail)
            SystemMediaFavoriteResult.SUCCESS
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (generation == ticket && mutable.value.songId == songId && isCurrent())
                mutable.value = mutable.value.copy(busy = false, failed = true, commandId = newFavoriteCommand())
            SystemMediaFavoriteResult.FAILED
        }
    }

    private fun apply(detail: PlaylistDetail, songId: String): Boolean {
        if (detail.playlist.kind != "favorite" || (favoriteId != null && detail.playlist.id != favoriteId))
            throw ApiFailure(Failure.INVALID_RESPONSE)
        if (detail.playlist.revision < favoriteRevision) {
            mutable.value = mutable.value.copy(loading = false, busy = false, failed = mutable.value.saved == null,
                commandId = newFavoriteCommand())
            return false
        }
        favoriteId = detail.playlist.id
        favoriteRevision = detail.playlist.revision
        mutable.value = mutable.value.copy(saved = detail.entries.any { it.song.id == songId }, loading = false,
            busy = false, failed = false, commandId = newFavoriteCommand())
        return true
    }

    fun close() {
        generation++
        loadingJob?.cancel()
        mutable.value = SystemMediaFavoriteState()
        favoriteId = null
        favoriteRevision = -1
    }
}
