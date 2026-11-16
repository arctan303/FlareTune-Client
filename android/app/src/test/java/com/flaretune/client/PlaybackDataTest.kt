package com.flaretune.client

import com.flaretune.client.model.Song
import com.flaretune.client.playback.PlayedTrackTracker
import com.flaretune.client.playback.item
import com.flaretune.client.playback.song
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackDataTest {
    @Test fun historyOnlyRecordsActualPlaybackOncePerTransition() {
        val seen = mutableListOf<String>()
        val tracker = PlayedTrackTracker { seen += it.id }
        val first = Song("first", "First", audioUrl = "https://music.example/media/first.mp3")
        val second = first.copy(id = "second")
        tracker.observe(first, false)
        assertTrue(seen.isEmpty())
        tracker.observe(first, true)
        repeat(5) { tracker.observe(first, true) }
        tracker.observe(first, false)
        tracker.observe(first, true)
        assertEquals(listOf("first"), seen)
        tracker.onMediaItemTransition()
        tracker.observe(first, true)
        tracker.onMediaItemTransition()
        tracker.observe(second, false)
        tracker.observe(second, true)
        assertEquals(listOf("first", "first", "second"), seen)
    }

    @Test fun mediaSessionRoundTripPreservesStrictLanguageAndMetadata() {
        val original = Song("instrumental", "Music", "Artist", "Album", 8.5,
            "https://music.example/media/song.flac", "https://music.example/media/cover.jpg", "instrumental")
        assertEquals(original, original.item().song())
        assertNull(original.copy(language = null).item().song().language)
        assertEquals("UNKNOWN", original.copy(language = "UNKNOWN").item().song().language)
    }
}