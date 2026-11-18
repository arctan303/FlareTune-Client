package com.arcinks.flaretune.playback

import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.ui.PlaybackSnapshot

/** Qualified listening is separate from the first-playing history marker. Sample at most one second apart. */
internal class QualifiedListeningTracker(private val onQualified: (Song) -> Unit) {
    private var songId: String? = null
    private var lastPosition: Long? = null
    private var wasPlaying = false
    private var accumulatedMs = 0L
    private var recorded = false

    fun onMediaItemTransition() {
        songId = null
        lastPosition = null
        wasPlaying = false
        accumulatedMs = 0
        recorded = false
    }

    /** A user seek discards the discontinuity, retaining already heard time in this iteration. */
    fun onPositionDiscontinuity() { lastPosition = null; wasPlaying = false }

    fun observe(snapshot: PlaybackSnapshot) {
        val song = snapshot.current
        if (song == null || song.id.isBlank()) { onMediaItemTransition(); return }
        val playing = snapshot.playing && !snapshot.buffering && !snapshot.failed
        val position = snapshot.positionMs.coerceAtLeast(0)
        if (songId != song.id) {
            onMediaItemTransition()
            songId = song.id
            lastPosition = position
            wasPlaying = playing
            return
        }
        val previous = lastPosition
        if (!recorded && playing && wasPlaying && previous != null) {
            val delta = position - previous
            // Matches Web's positive <1.5s progress rule; jumps and pauses never add listening time.
            if (delta in 1..1499) accumulatedMs += delta
            val duration = snapshot.durationMs.takeIf { it > 0 }
                ?: (song.durationSeconds * 1000).takeIf { it.isFinite() && it > 0 }?.toLong() ?: 0
            if (accumulatedMs >= 30_000 || (duration in 1..59_999 && accumulatedMs.toDouble() >= duration * .5)) {
                recorded = true
                onQualified(song)
            }
        }
        lastPosition = position
        wasPlaying = playing
    }
}
