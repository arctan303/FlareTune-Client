package com.flaretune.client

import com.flaretune.client.data.HistoryIdentity
import com.flaretune.client.data.HistoryStorage
import com.flaretune.client.data.JsonPlaylistPreferencesStore
import com.flaretune.client.data.sortPlaylistEntries
import com.flaretune.client.data.sortPlaylists
import com.flaretune.client.model.PlaylistShelf
import com.flaretune.client.model.PlaylistSong
import com.flaretune.client.model.PlaylistSummary
import com.flaretune.client.model.ShelfItem
import com.flaretune.client.model.Song
import com.flaretune.client.ui.PlaylistSongSortMode
import com.flaretune.client.ui.PlaylistSortMode
import org.junit.Assert.*
import org.junit.Test

class PlaylistPreferencesTest {
    private class MapStorage : HistoryStorage {
        val entries = mutableMapOf<String, String>()
        override fun read(key: String): String? = entries[key]
        override fun write(key: String, value: String) { entries[key] = value }
        override fun remove(key: String) { entries.remove(key) }
    }

    @Test fun storeIsolatesSortPreferencePerAccountAndInstance() {
        val storage = MapStorage()
        val store = JsonPlaylistPreferencesStore(storage)
        val accountA = HistoryIdentity("https://music.example", "acc-a")
        val accountB = HistoryIdentity("https://music.example", "acc-b")
        val otherOriginA = HistoryIdentity("https://other.example", "acc-a")

        assertEquals(PlaylistSortMode.CUSTOM, store.readSortMode(accountA))
        store.writeSortMode(accountA, PlaylistSortMode.TRACK_COUNT)
        store.writeSortMode(accountB, PlaylistSortMode.NAME)
        store.writeSortMode(otherOriginA, PlaylistSortMode.RECENTLY_UPDATED)

        assertEquals(PlaylistSortMode.TRACK_COUNT, store.readSortMode(accountA))
        assertEquals(PlaylistSortMode.NAME, store.readSortMode(accountB))
        assertEquals(PlaylistSortMode.RECENTLY_UPDATED, store.readSortMode(otherOriginA))
        assertTrue(storage.entries.values.none { it.contains("https://") || it.contains("token", ignoreCase = true) })

        store.clear(accountA)
        assertEquals(PlaylistSortMode.CUSTOM, store.readSortMode(accountA))
        assertEquals(PlaylistSortMode.NAME, store.readSortMode(accountB))
    }

    @Test fun sortPlaylistsSupportsCustomRecentlyUpdatedNameAndTrackCount() {
        val fav = PlaylistSummary("fav", "Favorites", "favorite", songCount = 5, createdAt = 100, updatedAt = 200)
        val beta = PlaylistSummary("b", "Beta Mix", "regular", songCount = 12, createdAt = 200, updatedAt = 500)
        val alpha = PlaylistSummary("a", "Alpha Mix", "regular", songCount = 2, createdAt = 300, updatedAt = 300)
        val list = listOf(fav, beta, alpha)
        val shelf = PlaylistShelf(1, items = listOf(ShelfItem("a"), ShelfItem("fav"), ShelfItem("b")))

        assertEquals(listOf("a", "fav", "b"), sortPlaylists(list, PlaylistSortMode.CUSTOM, shelf).map { it.id })
        assertEquals(listOf("b", "a", "fav"), sortPlaylists(list, PlaylistSortMode.RECENTLY_UPDATED, shelf).map { it.id })
        assertEquals(listOf("a", "b", "fav"), sortPlaylists(list, PlaylistSortMode.NAME, shelf).map { it.id })
        assertEquals(listOf("b", "fav", "a"), sortPlaylists(list, PlaylistSortMode.TRACK_COUNT, shelf).map { it.id })
    }

    @Test fun sortPlaylistEntriesSupportsDefaultTitleArtistDurationAndReversed() {
        val s1 = PlaylistSong(Song("1", "Zebra", "Bob", durationSeconds = 120.0, audioUrl = "https://music.example/media/1.mp3"), sortOrder = 0)
        val s2 = PlaylistSong(Song("2", "Apple", "Charlie", durationSeconds = 300.0, audioUrl = "https://music.example/media/2.mp3"), sortOrder = 1)
        val s3 = PlaylistSong(Song("3", "Mango", "Alice", durationSeconds = 200.0, audioUrl = "https://music.example/media/3.mp3"), sortOrder = 2)
        val entries = listOf(s1, s2, s3)

        assertEquals(listOf("1", "2", "3"), sortPlaylistEntries(entries, PlaylistSongSortMode.DEFAULT).map { it.song.id })
        assertEquals(listOf("2", "3", "1"), sortPlaylistEntries(entries, PlaylistSongSortMode.TITLE).map { it.song.id })
        assertEquals(listOf("3", "1", "2"), sortPlaylistEntries(entries, PlaylistSongSortMode.ARTIST).map { it.song.id })
        assertEquals(listOf("2", "3", "1"), sortPlaylistEntries(entries, PlaylistSongSortMode.DURATION).map { it.song.id })
        assertEquals(listOf("3", "2", "1"), sortPlaylistEntries(entries, PlaylistSongSortMode.REVERSED).map { it.song.id })
    }
}
