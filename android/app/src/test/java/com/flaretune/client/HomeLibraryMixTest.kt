package com.flaretune.client

import com.flaretune.client.data.allocateHomeLibraryQuotas
import com.flaretune.client.data.interleaveProportionalTracks
import com.flaretune.client.model.Song
import org.junit.Assert.*
import org.junit.Test

class HomeLibraryMixTest {
    private fun song(id: String, language: String = "") = Song(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "Album",
        durationSeconds = 180.0,
        audioUrl = "https://music.example/media/$id.mp3",
        language = language,
    )

    @Test fun hamiltonQuotasGuaranteeEachLanguageIncludingUntaggedAndSumToLimit() {
        val quotas = allocateHomeLibraryQuotas(
            mapOf("zh" to 60, "en" to 30, "untagged" to 20, "jp" to 10, "ko" to 0, "  " to 5),
            limit = 16,
        )
        assertEquals(setOf("zh", "en", "untagged", "jp"), quotas.keys)
        assertEquals(16, quotas.values.sum())
        assertTrue(quotas.values.all { it >= 1 })
        assertEquals(7, quotas["zh"])
        assertEquals(4, quotas["en"])
        assertEquals(3, quotas["untagged"])
        assertEquals(2, quotas["jp"])
    }

    @Test fun hamiltonQuotasSelectTopSixteenWhenMoreThanSixteenLanguagesExist() {
        val counts = (1..20).associate { i -> "l%02d".format(i) to (i * 10) }
        val quotas = allocateHomeLibraryQuotas(counts, limit = 16)
        assertEquals(16, quotas.size)
        assertEquals(16, quotas.values.sum())
        assertTrue(quotas.values.all { it == 1 })
        assertFalse(quotas.containsKey("l01"))
        assertFalse(quotas.containsKey("l04"))
        assertTrue(quotas.containsKey("l20"))
    }

    @Test fun smallBucketCapacityCapsQuotaSoRemainingSlotsGoToLargerLanguages() {
        val quotas = allocateHomeLibraryQuotas(mapOf("zh" to 12, "en" to 1, "untagged" to 5), limit = 16)
        assertEquals(16, quotas.values.sum())
        assertEquals(1, quotas["en"])
        assertEquals(5, quotas["untagged"])
        assertEquals(10, quotas["zh"])
    }

    @Test fun interleaverDistributesLanguagesProportionallyAndDeduplicatesWithFallback() {
        val quotas = mapOf("zh" to 4, "en" to 2, "untagged" to 2)
        val zh = (1..4).map { song("zh-$it", "zh") }
        val en = (1..2).map { song("en-$it", "en") }
        val untagged = listOf(song("u-1", ""), zh.first())
        val fallback = listOf(song("fb-1", "jp"), song("fb-2", "ko"))

        val mixed = interleaveProportionalTracks(
            tracksByLanguage = mapOf("zh" to zh, "en" to en, "untagged" to untagged),
            quotas = quotas,
            fallbackSongs = fallback,
            limit = 8,
        )

        assertEquals(8, mixed.size)
        assertEquals(8, mixed.map { it.id }.distinct().size)
        // Proportional pacing spreadszh instead of putting zh-1..zh-4 sequentially at the front.
        assertNotEquals(listOf("zh-1", "zh-2", "zh-3", "zh-4"), mixed.take(4).map { it.id })
        assertTrue(mixed.any { it.id == "u-1" })
        assertTrue(mixed.any { it.id == "fb-1" })
    }
}
