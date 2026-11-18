package com.arcinks.flaretune.data

import com.arcinks.flaretune.model.PlaylistShelf
import com.arcinks.flaretune.model.PlaylistSong
import com.arcinks.flaretune.model.PlaylistSummary
import com.arcinks.flaretune.ui.PlaylistSongSortMode
import com.arcinks.flaretune.ui.PlaylistSortMode
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

interface PlaylistPreferencesStore {
    fun readSortMode(identity: HistoryIdentity): PlaylistSortMode
    fun writeSortMode(identity: HistoryIdentity, mode: PlaylistSortMode)
    fun clear(identity: HistoryIdentity)
}

/** Per-account playlist UI preference store partitioned by [HistoryIdentity]; stores no URLs or credentials. */
class JsonPlaylistPreferencesStore(private val storage: HistoryStorage) : PlaylistPreferencesStore {
    override fun readSortMode(identity: HistoryIdentity): PlaylistSortMode = localStateOperation {
        val raw = storage.read(listeningStorageKey(PREFS_PREFIX, identity)) ?: return@localStateOperation PlaylistSortMode.CUSTOM
        val json = boundedLocalJson(raw)
        val value = json.optString("playlistSort", PlaylistSortMode.CUSTOM.name)
        PlaylistSortMode.entries.firstOrNull { it.name == value } ?: PlaylistSortMode.CUSTOM
    }

    override fun writeSortMode(identity: HistoryIdentity, mode: PlaylistSortMode) = localStateOperation {
        val payload = JSONObject().put("playlistSort", mode.name)
        writeLocalJson(storage, listeningStorageKey(PREFS_PREFIX, identity), payload)
    }

    override fun clear(identity: HistoryIdentity) = localStateOperation {
        storage.remove(listeningStorageKey(PREFS_PREFIX, identity))
    }

    private companion object {
        const val PREFS_PREFIX = "playlist_prefs_v1"
    }
}

internal fun sortPlaylists(
    playlists: List<PlaylistSummary>,
    mode: PlaylistSortMode,
    shelf: PlaylistShelf?,
): List<PlaylistSummary> {
    if (playlists.size <= 1) return playlists
    val shelfPositions = shelf?.items?.mapIndexed { index, item -> item.id to index }?.toMap().orEmpty()
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
    return when (mode) {
        PlaylistSortMode.CUSTOM ->
            playlists.sortedBy { shelfPositions[it.id] ?: Int.MAX_VALUE }
        PlaylistSortMode.RECENTLY_UPDATED ->
            playlists.sortedWith { a, b ->
                val updated = b.updatedAt.compareTo(a.updatedAt)
                if (updated != 0) updated
                else {
                    val created = b.createdAt.compareTo(a.createdAt)
                    if (created != 0) created
                    else {
                        val shelfCmp = (shelfPositions[a.id] ?: Int.MAX_VALUE).compareTo(shelfPositions[b.id] ?: Int.MAX_VALUE)
                        if (shelfCmp != 0) shelfCmp else collator.compare(a.name, b.name)
                    }
                }
            }
        PlaylistSortMode.NAME ->
            playlists.sortedWith { a, b ->
                val nameCmp = collator.compare(a.name, b.name)
                if (nameCmp != 0) nameCmp else b.updatedAt.compareTo(a.updatedAt)
            }
        PlaylistSortMode.TRACK_COUNT ->
            playlists.sortedWith { a, b ->
                val countCmp = b.songCount.compareTo(a.songCount)
                if (countCmp != 0) countCmp else collator.compare(a.name, b.name)
            }
    }
}

internal fun sortPlaylistEntries(
    entries: List<PlaylistSong>,
    mode: PlaylistSongSortMode,
): List<PlaylistSong> {
    if (entries.size <= 1 || mode == PlaylistSongSortMode.DEFAULT) return entries
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
    return when (mode) {
        PlaylistSongSortMode.DEFAULT -> entries
        PlaylistSongSortMode.TITLE ->
            entries.sortedWith { a, b ->
                val titleCmp = collator.compare(a.song.title, b.song.title)
                if (titleCmp != 0) titleCmp else a.sortOrder.compareTo(b.sortOrder)
            }
        PlaylistSongSortMode.ARTIST ->
            entries.sortedWith { a, b ->
                val artistCmp = collator.compare(a.song.artist, b.song.artist)
                if (artistCmp != 0) artistCmp
                else {
                    val titleCmp = collator.compare(a.song.title, b.song.title)
                    if (titleCmp != 0) titleCmp else a.sortOrder.compareTo(b.sortOrder)
                }
            }
        PlaylistSongSortMode.DURATION ->
            entries.sortedWith { a, b ->
                val durCmp = b.song.durationSeconds.compareTo(a.song.durationSeconds)
                if (durCmp != 0) durCmp else a.sortOrder.compareTo(b.sortOrder)
            }
        PlaylistSongSortMode.REVERSED -> entries.asReversed()
    }
}
