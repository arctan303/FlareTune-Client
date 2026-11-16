package com.flaretune.client.playback

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.flaretune.client.data.BluetoothLyricsMode
import com.flaretune.client.model.LyricState
import com.flaretune.client.model.LyricStatus
import com.flaretune.client.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class SystemLyricsMetadata(
    val mediaId: String,
    val title: String,
    val artist: String,
    val isLyricActive: Boolean,
)

/**
 * Permission-free check for Bluetooth audio output device types exposed by [AudioDeviceInfo].
 * Avoids requiring `BLUETOOTH_CONNECT` runtime permission.
 */
internal fun isBluetoothAudioOutputDeviceType(
    type: Int,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean =
    type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        (sdkInt >= Build.VERSION_CODES.P && type == AudioDeviceInfo.TYPE_HEARING_AID) ||
        (sdkInt >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) ||
        (sdkInt >= Build.VERSION_CODES.TIRAMISU && type == AudioDeviceInfo.TYPE_BLE_BROADCAST)

internal fun hasBluetoothAudioOutput(
    deviceTypes: Iterable<Int>,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = deviceTypes.any { isBluetoothAudioOutputDeviceType(it, sdkInt) }

internal fun queryHasBluetoothAudioOutput(audioManager: AudioManager?): Boolean {
    if (audioManager == null) return false
    val devices = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull()
        ?: return false
    return hasBluetoothAudioOutput(devices.map { it.type })
}

internal fun isBluetoothLyricsActiveForRoute(
    mode: BluetoothLyricsMode,
    hasBluetoothAudioOutput: Boolean,
): Boolean = when (mode) {
    BluetoothLyricsMode.OFF -> false
    BluetoothLyricsMode.BLUETOOTH_AUDIO -> hasBluetoothAudioOutput
    BluetoothLyricsMode.ALWAYS -> true
}

internal fun formatBluetoothLyricsArtist(song: Song): String =
    if (song.artist.isNotBlank()) "${song.title} - ${song.artist}" else song.title

internal fun resolveSystemLyricsMetadata(
    song: Song?,
    isPlaying: Boolean,
    lyricState: LyricState,
    mode: BluetoothLyricsMode,
    hasBluetoothAudioOutput: Boolean,
): SystemLyricsMetadata? {
    if (song == null) return null
    val fallback = SystemLyricsMetadata(
        mediaId = song.id,
        title = song.title,
        artist = song.artist,
        isLyricActive = false,
    )
    if (!isBluetoothLyricsActiveForRoute(mode, hasBluetoothAudioOutput)) return fallback
    if (!isPlaying) return fallback
    if (lyricState.status != LyricStatus.READY) return fallback
    if (lyricState.songId != song.id) return fallback
    val activeText = lyricState.lines
        .getOrNull(lyricState.activeLyricIndex)
        ?.text
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return fallback
    return SystemLyricsMetadata(
        mediaId = song.id,
        title = activeText,
        artist = formatBluetoothLyricsArtist(song),
        isLyricActive = true,
    )
}

internal fun applySystemLyricsMetadata(
    currentItem: MediaItem?,
    baseMetadata: MediaMetadata,
    applied: SystemLyricsMetadata?,
    isPlaying: Boolean,
): MediaMetadata {
    val item = currentItem ?: return baseMetadata
    val rawSong = item.song()
    val effective = if (
        applied != null &&
        applied.mediaId == rawSong.id &&
        (!applied.isLyricActive || isPlaying)
    ) {
        applied
    } else {
        SystemLyricsMetadata(
            mediaId = rawSong.id,
            title = rawSong.title,
            artist = rawSong.artist,
            isLyricActive = false,
        )
    }
    if (
        baseMetadata.title?.toString() == effective.title &&
        baseMetadata.artist?.toString() == effective.artist &&
        (!effective.isLyricActive || (baseMetadata.displayTitle == null && baseMetadata.subtitle == null))
    ) {
        return baseMetadata
    }
    return baseMetadata.buildUpon()
        .setTitle(effective.title)
        .setArtist(effective.artist)
        .apply {
            if (effective.isLyricActive) {
                setDisplayTitle(null)
                setSubtitle(null)
            }
        }
        .build()
}

/**
 * Coordinates system-visible metadata updates with lightweight throttling (>= 300ms) for rapid
 * skips/seeks while executing fallbacks (pause, OFF, Bluetooth disconnect, blank lines) immediately.
 */
internal class BluetoothLyricsController(
    private val scope: CoroutineScope,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime,
    private val minIntervalMs: Long = 350L,
    private val onAppliedChanged: () -> Unit,
) {
    init {
        require(minIntervalMs >= 300L) { "AVRCP metadata throttle must be >= 300ms" }
    }

    var applied: SystemLyricsMetadata? = null
        private set

    private var pendingJob: Job? = null
    private var pendingTarget: SystemLyricsMetadata? = null
    private var lastLyricEmitMs: Long = Long.MIN_VALUE

    fun update(
        song: Song?,
        isPlaying: Boolean,
        lyricState: LyricState,
        mode: BluetoothLyricsMode,
        hasBluetoothAudioOutput: Boolean,
    ) {
        val target = resolveSystemLyricsMetadata(song, isPlaying, lyricState, mode, hasBluetoothAudioOutput)
        if (target == null) {
            cancelPending()
            if (applied != null) {
                applied = null
                onAppliedChanged()
            }
            return
        }
        if (target == applied) {
            cancelPending()
            return
        }
        if (!target.isLyricActive) {
            cancelPending()
            applied = target
            onAppliedChanged()
            return
        }
        val now = clockMs()
        val elapsed = if (lastLyricEmitMs == Long.MIN_VALUE) Long.MAX_VALUE else (now - lastLyricEmitMs)
        if (elapsed >= minIntervalMs) {
            cancelPending()
            applied = target
            lastLyricEmitMs = now
            onAppliedChanged()
            return
        }
        if (pendingJob?.isActive == true && pendingTarget == target) return
        cancelPending()
        pendingTarget = target
        val waitMs = (minIntervalMs - elapsed).coerceAtLeast(1L)
        pendingJob = scope.launch {
            delay(waitMs)
            pendingTarget = null
            if (applied != target) {
                applied = target
                lastLyricEmitMs = clockMs()
                onAppliedChanged()
            }
        }
    }

    fun clear() {
        cancelPending()
        applied = null
        lastLyricEmitMs = Long.MIN_VALUE
    }

    private fun cancelPending() {
        pendingJob?.cancel()
        pendingJob = null
        pendingTarget = null
    }
}
