package com.arcinks.flaretune

import com.arcinks.flaretune.data.*
import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.model.SpotlightArtist
import com.arcinks.flaretune.playback.QualifiedListeningTracker
import com.arcinks.flaretune.ui.PlaybackSnapshot
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ListeningHelpersTest {
    private val a = HistoryIdentity("https://music.example", "account-A")
    private val b = HistoryIdentity("https://music.example", "account-B")
    private fun song(id: String = "song") = Song(id, "Title", "Singer", "Album", 100.0,
        "https://private.invalid/audio?token=AUDIO_SECRET", "https://private.invalid/cover?csrf=COVER_SECRET", "UNKNOWN")

    @Test fun searchesKeepTenNewestTermsAndEntitiesAndAreIsolatedByOwnerAndInstance() {
        val backend = Memory()
        val store = SearchHistoryStore(backend)
        repeat(12) { store.addTerm(a, "term-$it") }
        assertEquals(10, store.load(a).terms.size)
        assertEquals("term-11", store.load(a).terms.first())
        store.addTerm(a, " TERM-9 ")
        assertEquals("TERM-9", store.load(a).terms.first())
        assertEquals(1, store.load(a).terms.count { it.equals("term-9", true) })
        store.addEntity(a, SearchEntity.fromArtist("Singer"))
        store.addEntity(a, SearchEntity.fromArtist("singer"))
        store.addEntity(a, SearchEntity.fromSong(song()))
        assertEquals(2, store.load(a).entities.size)
        assertEquals("song", store.load(a).entities.first().type)
        assertTrue(store.load(b).entities.isEmpty())
        assertTrue(store.load(HistoryIdentity("https://different.example", a.accountId)).terms.isEmpty())
        val restored = SearchHistoryStore(backend).load(a).entities.first().toSong()
        assertEquals("", restored.audioUrl); assertNull(restored.coverUrl); assertEquals("UNKNOWN", restored.language)
        assertFalse(backend.values.values.joinToString().contains("SECRET"))
        assertTrue(backend.values.keys.all { it.matches(Regex("search_v1_[a-f0-9]{64}")) })
    }

    @Test fun removingAndClearingSearchSectionsPreservesTheOtherSection() {
        val store = SearchHistoryStore(Memory())
        val entity = SearchEntity.fromSong(song())
        store.addTerm(a, "listen"); store.addEntity(a, entity)
        assertTrue(store.removeTerm(a, "LISTEN").terms.isEmpty())
        assertEquals(listOf(entity), store.load(a).entities)
        store.addTerm(a, "again")
        assertTrue(store.clearEntities(a).entities.isEmpty())
        assertEquals(listOf("again"), store.load(a).terms)
        store.addEntity(a, entity)
        assertTrue(store.removeEntity(a, entity).entities.isEmpty())
        assertTrue(store.clearTerms(a).terms.isEmpty())
    }

    @Test fun discoveryKeepsEmptySuccessFlagsAndDoesNotRelabelYesterdayOtherPartition() {
        val backend = Memory()
        val cache = DiscoveryCache(backend)
        val first = cache.saveDaily(a, "2026-11-12", listOf(song()))
        assertTrue(first.dailyLoaded); assertFalse(first.spotlightLoaded)
        val paired = cache.saveSpotlight(a, "2026-11-12", SpotlightArtist("Singer", 1, listOf(song())))
        assertTrue(paired.dailyLoaded); assertTrue(paired.spotlightLoaded)
        val today = cache.saveDaily(a, "2026-11-13", emptyList())
        assertTrue(today.dailyLoaded); assertTrue(today.songs.isEmpty())
        assertFalse(today.spotlightLoaded); assertNull(today.spotlight)
        val absentSpotlight = cache.saveSpotlight(a, "2026-11-13", null)
        assertTrue(absentSpotlight.spotlightLoaded); assertNull(absentSpotlight.spotlight)
        assertNull(cache.load(b)); assertNull(cache.load(a, "2026-11-12"))
        assertEquals("2026-11-13", DiscoveryCache(backend).load(a)!!.date)
        assertFalse(backend.values.values.joinToString().contains("SECRET"))
        assertFalse(backend.values.values.joinToString().contains("audioUrl"))
        assertFalse(backend.values.values.joinToString().contains("coverUrl"))
    }

    @Test fun aNewDayOfSpotlightDoesNotReplaceTheSavedRandomBatch() {
        val cache = DiscoveryCache(Memory())
        val original = cache.saveDaily(a, "2026-11-17", listOf(song()))
        val refreshed = cache.saveSpotlight(a, "2026-11-18", null)
        assertTrue(refreshed.dailyLoaded)
        assertEquals(original.songs, refreshed.songs)
        assertEquals(original.songs, cache.load(a)!!.songs)
    }

    @Test fun discoveryCooldownsMatchWebAndDoNotCrossAccounts() {
        val cache = DiscoveryCache(Memory())
        cache.markRefreshed(a, "daily", 1000)
        cache.markRefreshed(a, "spotlight", 1000)
        assertEquals(1500L, cache.remainingCooldownMs(a, "daily", 1500))
        assertEquals(100L, cache.remainingCooldownMs(a, "spotlight", 1500))
        assertEquals(0L, cache.remainingCooldownMs(b, "daily", 1500))
        assertEquals(0L, cache.remainingCooldownMs(a, "daily", 3000))
    }

    @Test fun unknownOrCredentialBearingLocalSchemasFailClosedRatherThanAdoptingGlobalData() {
        val backend = Memory(); val store = SearchHistoryStore(backend)
        store.addTerm(a, "valid")
        val key = backend.values.keys.single()
        backend.values[key] = JSONObject(backend.values.getValue(key)).put("cookie", "PRIVATE_COOKIE_CANARY").toString()
        assertEquals(Failure.STORAGE, assertThrows(ApiFailure::class.java) { store.load(a) }.failure)
        assertTrue(store.load(b).terms.isEmpty())
    }

    @Test fun qualifiedListeningNeedsThirtyRealSecondsAndOnlyOneEventPerIteration() {
        val seen = mutableListOf<String>()
        val tracker = QualifiedListeningTracker { seen += it.id }
        for (time in 0L..29_000L step 1000) tracker.observe(snapshot(time))
        assertTrue(seen.isEmpty())
        tracker.observe(snapshot(30_000)); assertEquals(listOf("song"), seen)
        for (time in 31_000L..60_000L step 1000) tracker.observe(snapshot(time))
        assertEquals(1, seen.size)
        tracker.onMediaItemTransition()
        for (time in 0L..30_000L step 1000) tracker.observe(snapshot(time))
        assertEquals(2, seen.size)
    }

    @Test fun shortSongHalfQualifiesButPauseBufferingAndSeekJumpsDoNotAddTime() {
        val shortSeen = mutableListOf<Song>()
        val short = QualifiedListeningTracker { shortSeen += it }
        for (time in 0L..19_000L step 1000) short.observe(snapshot(time, duration = 40_000))
        assertTrue(shortSeen.isEmpty())
        short.observe(snapshot(20_000, duration = 40_000)); assertEquals(1, shortSeen.size)
        val seen = mutableListOf<Song>(); val tracker = QualifiedListeningTracker { seen += it }
        for (time in 0L..10_000L step 1000) tracker.observe(snapshot(time))
        repeat(20) { tracker.observe(snapshot(10_000, playing = false)) }
        tracker.onPositionDiscontinuity()
        tracker.observe(snapshot(60_000))
        for (time in 61_000L..79_000L step 1000) tracker.observe(snapshot(time))
        assertTrue(seen.isEmpty())
        tracker.observe(snapshot(80_000)); assertEquals(1, seen.size)
        tracker.onPositionDiscontinuity()
        for (time in 0L..40_000L step 1000) tracker.observe(snapshot(time))
        assertEquals(1, seen.size)
        val bufferingSeen = mutableListOf<Song>(); val buffering = QualifiedListeningTracker { bufferingSeen += it }
        for (time in 0L..40_000L step 1000) buffering.observe(snapshot(time).copy(buffering = true))
        assertTrue(bufferingSeen.isEmpty())
    }

    private fun snapshot(position: Long, duration: Long = 100_000, playing: Boolean = true) = PlaybackSnapshot(current = song(), playing = playing, positionMs = position, durationMs = duration)
    private class Memory : HistoryStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
}
