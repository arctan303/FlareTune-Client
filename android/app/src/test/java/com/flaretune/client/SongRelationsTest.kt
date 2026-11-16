package com.flaretune.client

import androidx.media3.common.MediaItem
import com.flaretune.client.data.*
import com.flaretune.client.model.*
import com.flaretune.client.playback.item
import com.flaretune.client.playback.song
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SongRelationsTest {
    private val external = Song("yt:abcdefghijk", "Same song", "Britney Spears", "Album one", 212.0,
        "https://music.example/media/fresh?signature=temporary", "https://music.example/media/cover?signature=temporary", "en",
        "youtube", listOf(SongArtist("yt:artist:UCbritney", "Britney Spears")), "yt:artist:UCbritney", "yt:album:MPREb_one")
    private val identity = HistoryIdentity("https://music.example", "listener")
    private class Memory : HistoryStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        fun stripRelations() {
            values.replaceAll { _, raw ->
                val root = JSONObject(raw)
                fun clean(rows: JSONArray?) { if (rows != null) repeat(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    SONG_RELATION_KEYS.forEach(row::remove)
                } }
                clean(root.optJSONArray("entries")); clean(root.optJSONArray("entities")); clean(root.optJSONArray("songs"))
                root.optJSONObject("spotlight")?.let { clean(it.optJSONArray("songs")) }
                root.toString()
            }
        }
    }

    @Test fun externalRelationsNeverFallBackToLocalSameName() {
        assertEquals("yt:artist:UCbritney", external.relatedArtists().single().id)
        assertEquals("yt:album:MPREb_one", external.relatedAlbum()!!.id)
        val missing = external.copy(artists = listOf(SongArtist(name = "Britney Spears")), artistId = null, albumId = null)
        assertTrue(missing.relatedArtists().isEmpty())
        assertNull(missing.relatedAlbum())
        assertTrue(missing.copy(source = null).relatedArtists().isEmpty())
        assertEquals("yt:album:MPREb_one", external.copy(album = "").relatedAlbum()!!.id)
    }

    @Test fun localArtistsKeepNameRoutesAndAlbumNeedsKnownId() {
        val local = Song("local", "Song", "Singer A / Singer B", "Album", audioUrl = "")
        assertEquals(listOf("Singer A", "Singer B"), local.relatedArtists().map { it.name })
        assertTrue(local.relatedArtists().all { it.id == null })
        assertNull(local.relatedAlbum())
        assertEquals("WyJBcnRpc3QiLCJBbGJ1bSJd", local.copy(albumId = "WyJBcnRpc3QiLCJBbGJ1bSJd").relatedAlbum()!!.id)
        assertNull(local.copy(albumId = "yt:album:MPREb_one").relatedAlbum())
    }

    @Test fun freshMediaAndExplicitAlbumContextMergeWithoutCrossingSongOrSource() {
        val fresh = external.copy(albumId = "yt:album:MPREb_two", audioUrl = "https://music.example/media/renewed")
        assertEquals("yt:album:MPREb_two", mergeSongRelations(fresh, external).albumId)
        assertEquals("yt:album:MPREb_one", mergeSongRelations(fresh, external, preferPrevious = true).albumId)
        assertEquals(fresh.audioUrl, mergeSongRelations(fresh, external, preferPrevious = true).audioUrl)
        assertEquals(fresh.coverUrl, mergeSongRelations(fresh, external, preferPrevious = true).coverUrl)
        assertEquals(external.artists, mergeSongRelations(fresh.copy(artists = emptyList()), external).artists)
        assertNull(mergeSongRelations(fresh.copy(id = "yt:otherabcdef", albumId = null), external).albumId)
        assertEquals(fresh, mergeSongRelations(fresh, external.copy(id = "other")))
        assertNull(mergeSongRelations(Song("same", "Song", audioUrl = ""),
            Song("same", "Song", audioUrl = "", source = "youtube", albumId = "yt:album:MPREb_one")).albumId)
    }

    @Test fun mediaSessionRoundTripRetainsRelationsAndAcceptsLegacyItems() {
        assertEquals(external, external.item().song())
        val legacy = MediaItem.Builder().setMediaId("yt:abcdefghijk").build().song()
        assertEquals(SongRelations(), legacy.relations)
        assertTrue(legacy.relatedArtists().isEmpty())
    }

    @Test fun safeRelationCodecNormalizesRawChannelIdAndRejectsWrongSourceIds() {
        val row = JSONObject().put("source", "youtube").put("artistId", "UCbritney")
            .put("artists", JSONArray().put(JSONObject().put("id", "UCbritney").put("name", "Britney Spears")
                .put("sourceId", "UCbritney").put("ignored_url", "https://private.example")))
            .put("albumId", "yt:album:MPREb_one")
        val links = row.readSongRelations(external.id)
        assertEquals(external.relations, links)
        val safe = JSONObject().putSongRelations(external.id, links).toString()
        assertFalse(safe.contains("ignored_url")); assertFalse(safe.contains("private.example"))
        listOf(row.toString().replace("yt:album:MPREb_one", "Local album"),
            row.toString().replace("UCbritney", "not-an-opaque-id")).forEach { raw ->
            try { JSONObject(raw).readSongRelations(external.id); fail("Invalid provider ID accepted") }
            catch (_: IllegalArgumentException) { }
        }
        val tooMany = JSONObject().put("artists", JSONArray(List(51) { JSONObject().put("name", "Singer") }))
        try { tooMany.readSongRelations("local"); fail("Unbounded artists accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun queueHistorySearchAndDiscoveryPreserveSafeRelationsAndReadLegacySnapshots() {
        val disk = Memory()
        val queue = JsonPlaybackQueueStore(disk)
        val entry = metadataEntry(external)
        queue.write(identity, SavedPlaybackQueue(listOf(entry), 0, 1234))
        assertEquals(external.relations, queue.read(identity)!!.entries.single().toSong().relations)
        val history = JsonPlaybackHistoryStore(disk)
        history.write(identity, listOf(HistoryEntry(external.id, external.title, external.artist, external.album,
            external.durationSeconds, external.language, 1, external.relations)))
        assertEquals(external.relations, history.read(identity).single().toSong().relations)
        val search = SearchHistoryStore(disk)
        search.addEntity(identity, SearchEntity.fromSong(external))
        assertEquals(external.relations, search.load(identity).entities.single().toSong().relations)
        val discovery = DiscoveryCache(disk)
        discovery.saveDaily(identity, "2026-11-13", listOf(external))
        assertEquals(external.relations, discovery.load(identity)!!.songs.single().toSong().relations)
        disk.values.values.forEach { raw ->
            assertFalse(raw.contains("signature")); assertFalse(raw.contains("https://music.example/media"))
            assertFalse(raw.contains("audio_url")); assertFalse(raw.contains("cover_url"))
            assertFalse(raw.contains("Cookie")); assertFalse(raw.contains("csrf")); assertFalse(raw.contains("resultRef"))
        }
        disk.stripRelations()
        assertEquals(SongRelations(), queue.read(identity)!!.entries.single().relations)
        assertEquals(SongRelations(), history.read(identity).single().relations)
        assertEquals(SongRelations(), search.load(identity).entities.single().relations)
        assertEquals(SongRelations(), discovery.load(identity)!!.songs.single().relations)
        assertNull(queue.read(identity.copy(accountId = "other")))
        assertTrue(history.read(identity.copy(accountId = "other")).isEmpty())
    }
}
