package com.flaretune.client.playback

import com.flaretune.client.model.Song

/** One history event per entered media item, only after real playback starts. */
class PlayedTrackTracker(private val record: (Song) -> Unit) {
    private var generation = 0L
    private var currentId: String? = null
    private var recordedGeneration = -1L

    fun onMediaItemTransition() { generation++ }
    fun observe(song: Song?, playing: Boolean) {
        if (song?.id != currentId) {
            currentId = song?.id
            generation++
        }
        if (song == null || !playing || recordedGeneration == generation) return
        recordedGeneration = generation
        record(song)
    }
}