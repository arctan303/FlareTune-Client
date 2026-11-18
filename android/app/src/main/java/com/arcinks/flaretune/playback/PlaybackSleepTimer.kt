package com.arcinks.flaretune.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SleepTimerMode { OFF, TIMED, END_OF_TRACK }

/** A static monotonic deadline: rendering a countdown must not update the application state. */
data class SleepTimerState(
    val mode: SleepTimerMode = SleepTimerMode.OFF,
    val endElapsedRealtimeMs: Long? = null,
    val finishCurrentTrackAfterTimer: Boolean = false,
) {
    val stopAfterCurrentTrack: Boolean
        get() = mode == SleepTimerMode.END_OF_TRACK || (mode == SleepTimerMode.TIMED && finishCurrentTrackAfterTimer)
}

/**
 * Application-owned command bridge, attached only to the current PlaybackService's player.
 * All calls run on the player's application looper. The Service owns scheduling and its wake lock;
 * neither activity visibility nor a composable's lifetime participates in timer execution.
 */
class PlaybackSleepTimer {
    private val mutable = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = mutable
    private var attachment: Attachment? = null
    private var job: Job? = null
    private var revision = 0L
    private var originalVolume: Float? = null
    val isFading: Boolean get() = originalVolume != null
    val displayedVolume: Float? get() = originalVolume

    internal interface Target {
        var volume: Float
        var pauseAtEndOfTrack: Boolean
        fun pause()
    }

    private class Attachment(
        val owner: Any,
        val scope: CoroutineScope,
        val target: Target,
        val elapsedRealtime: () -> Long,
        val acquireWakeLock: (Long) -> Unit,
        val releaseWakeLock: () -> Unit,
    )

    internal fun attach(
        owner: Any,
        scope: CoroutineScope,
        target: Target,
        elapsedRealtime: () -> Long,
        acquireWakeLock: (Long) -> Unit = {},
        releaseWakeLock: () -> Unit = {},
    ) {
        cancel()
        attachment = Attachment(owner, scope, target, elapsedRealtime, acquireWakeLock, releaseWakeLock)
    }

    internal fun detach(owner: Any) {
        if (attachment?.owner !== owner) return
        cancel()
        attachment = null
    }

    fun setSleepTimer(minutes: Int) {
        if (minutes <= 0) { cancel(); return }
        val attached = attachment ?: return
        val preserveAfterTrack = mutable.value.stopAfterCurrentTrack
        cancel()
        val durationMs = minutes.toLong() * 60_000L
        val deadline = attached.elapsedRealtime() + durationMs
        // A bounded partial wake lock keeps elapsed-time scheduling accurate even while paused,
        // buffering or screen-off. No exact-alarm permission or foreground UI loop is required.
        attached.acquireWakeLock(durationMs + FADE_MS + 5_000L)
        mutable.value = SleepTimerState(SleepTimerMode.TIMED, deadline, preserveAfterTrack)
        val ticket = revision
        job = attached.scope.launch {
            delay((deadline - attached.elapsedRealtime()).coerceAtLeast(0L))
            if (ticket == revision && attachment === attached) {
                if (mutable.value.finishCurrentTrackAfterTimer) {
                    job = null
                    attached.releaseWakeLock()
                    attached.target.pauseAtEndOfTrack = true
                    mutable.value = SleepTimerState(SleepTimerMode.END_OF_TRACK)
                } else {
                    fadeAndPause(attached, ticket, FADE_MS)
                }
            }
        }
    }

    fun setSleepTimerEndOfTrack() {
        val attached = attachment ?: return
        cancel()
        attached.target.pauseAtEndOfTrack = true
        mutable.value = SleepTimerState(SleepTimerMode.END_OF_TRACK)
    }

    fun setSleepTimerFinishCurrentTrack(enabled: Boolean) {
        val current = mutable.value
        when (current.mode) {
            SleepTimerMode.TIMED -> {
                if (attachment == null) return
                mutable.value = current.copy(finishCurrentTrackAfterTimer = enabled)
            }
            SleepTimerMode.END_OF_TRACK -> if (!enabled) cancel()
            SleepTimerMode.OFF -> if (enabled) setSleepTimerEndOfTrack()
        }
    }

    fun cancel() {
        revision++
        job?.cancel()
        job = null
        attachment?.let { attached ->
            // Restore synchronously, before a new timer or identity can use the same player.
            originalVolume?.let { attached.target.volume = it }
            originalVolume = null
            attached.target.pauseAtEndOfTrack = false
            attached.releaseWakeLock()
        }
        mutable.value = SleepTimerState()
    }

    /** The native ExoPlayer pause-at-item-end is the boundary guard, including repeat/shuffle. */
    internal fun onTrackEnded(owner: Any) {
        if (attachment?.owner !== owner || mutable.value.mode != SleepTimerMode.END_OF_TRACK) return
        attachment?.target?.pause()
        cancel()
    }

    /** Fade the final audible portion when duration is known; native boundary pause is the fallback. */
    internal fun observeTrack(owner: Any, positionMs: Long, durationMs: Long, playing: Boolean) {
        val attached = attachment ?: return
        if (attached.owner !== owner || mutable.value.mode != SleepTimerMode.END_OF_TRACK || job != null) return
        if (!playing || durationMs <= 0 || durationMs - positionMs !in 1L..FADE_MS) return
        val remaining = durationMs - positionMs
        attached.acquireWakeLock(remaining + 5_000L)
        val ticket = revision
        job = attached.scope.launch { fadeAndPause(attached, ticket, remaining) }
    }

    private suspend fun fadeAndPause(attached: Attachment, ticket: Long, durationMs: Long) {
        val saved = attached.target.volume
        originalVolume = saved
        try {
            val steps = 12
            repeat(steps) { index ->
                delay((durationMs / steps).coerceAtLeast(1))
                if (ticket != revision || attachment !== attached) return
                attached.target.volume = saved * (steps - index - 1).toFloat() / steps
            }
            if (ticket == revision && attachment === attached) attached.target.pause()
        } finally {
            if (ticket == revision && attachment === attached) {
                attached.target.volume = saved
                originalVolume = null
                attached.target.pauseAtEndOfTrack = false
                attached.releaseWakeLock()
                job = null
                mutable.value = SleepTimerState()
            }
        }
    }

    internal companion object { const val FADE_MS = 600L }
}
