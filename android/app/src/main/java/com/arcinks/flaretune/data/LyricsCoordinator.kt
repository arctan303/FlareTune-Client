package com.arcinks.flaretune.data

import com.arcinks.flaretune.model.ClientNotice
import com.arcinks.flaretune.model.LyricDocument
import com.arcinks.flaretune.model.LyricLine
import com.arcinks.flaretune.model.LyricState
import com.arcinks.flaretune.model.LyricStatus
import com.arcinks.flaretune.model.LyricTranslationStatus
import com.arcinks.flaretune.model.Song
import com.arcinks.flaretune.model.canRequestLyricTranslation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns one ephemeral lyric result for the current song and authenticated session.
 * The UI receives display state only; result references never leave this owner.
 */
class LyricsCoordinator(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMs: Long = 3000,
    private val maxPollAttempts: Int = 20,
) {
    init {
        require(pollIntervalMs >= 0)
        require(maxPollAttempts >= 0)
    }

    private val lock = Any()
    private val mutableState = MutableStateFlow(LyricState())
    val state: StateFlow<LyricState> = mutableState.asStateFlow()
    private var connection: ActiveConnection? = null
    private var song: Song? = null
    private var generation = 0L
    private var positionMs = 0L
    private var resultRef: String? = null
    private var job: Job? = null

    fun activate(connection: ActiveConnection?, song: Song?) = synchronized(lock) {
        if (connection == null || song == null || !connection.network.isActive ||
            connection.auth.mustChangePassword
        ) {
            clearLocked()
            return@synchronized
        }
        val sameSong = this.song?.id == song.id
        val sameIdentity = sameIdentity(this.connection, connection)
        val sameLanguage = languageKey(this.song?.language) == languageKey(song.language)
        this.song = song
        this.connection = connection
        if (sameSong && sameIdentity && sameLanguage) return@synchronized

        invalidateRequestLocked()
        resultRef = null
        if (!sameSong || !sameIdentity) positionMs = 0L
        if (song.language == "instrumental") {
            showInstrumentalLocked(song.id)
        } else {
            mutableState.value = LyricState(songId = song.id, status = LyricStatus.LOADING)
            startLocked(connection, song.id, reference = null, translate = false)
        }
    }

    fun updatePosition(positionMs: Long) = synchronized(lock) {
        val active = connection ?: return@synchronized
        if (!active.network.isActive) {
            clearLocked()
            return@synchronized
        }
        this.positionMs = positionMs.coerceAtLeast(0)
        val current = mutableState.value
        val nextIndex = activeIndex(current)
        if (nextIndex != current.activeLyricIndex) {
            mutableState.value = current.copy(activeLyricIndex = nextIndex)
        }
    }

    fun retry() = synchronized(lock) {
        val active = usableConnectionLocked() ?: return@synchronized
        val currentSong = song ?: return@synchronized
        if (currentSong.language == "instrumental" || mutableState.value.status == LyricStatus.INSTRUMENTAL) {
            showInstrumentalLocked(currentSong.id)
            return@synchronized
        }
        mutableState.value = mutableState.value.copy(
            status = if (mutableState.value.lines.isEmpty()) LyricStatus.LOADING else LyricStatus.READY,
            polling = false,
            notice = null,
        )
        startLocked(active, currentSong.id, reference = resultRef, translate = false)
    }

    fun requestTranslation() = synchronized(lock) {
        val active = usableConnectionLocked() ?: return@synchronized
        val currentSong = song ?: return@synchronized
        val current = mutableState.value
        val reference = resultRef ?: return@synchronized
        if (job?.isActive == true || !current.canRequestLyricTranslation) return@synchronized
        startLocked(active, currentSong.id, reference, translate = true)
    }

    fun clear() = synchronized(lock) { clearLocked() }

    private fun languageKey(language: String?) = language?.takeUnless { it.isBlank() }

    private fun sameIdentity(left: ActiveConnection?, right: ActiveConnection): Boolean =
        left != null && left.network === right.network &&
            left.auth.account.id == right.auth.account.id &&
            left.auth.csrfToken == right.auth.csrfToken &&
            left.auth.mustChangePassword == right.auth.mustChangePassword

    private fun usableConnectionLocked(): ActiveConnection? {
        val active = connection ?: return null
        if (!active.network.isActive || active.auth.mustChangePassword) {
            clearLocked()
            return null
        }
        return active
    }

    private fun invalidateRequestLocked() {
        generation++
        job?.cancel()
        job = null
    }

    private fun clearLocked() {
        invalidateRequestLocked()
        connection = null
        song = null
        resultRef = null
        positionMs = 0L
        mutableState.value = LyricState()
    }

    private fun currentLocked(ticket: Long, expected: ActiveConnection, songId: String): Boolean {
        if (ticket != generation || song?.id != songId || !sameIdentity(connection, expected)) return false
        if (!expected.network.isActive || expected.auth.mustChangePassword) {
            clearLocked()
            return false
        }
        return true
    }

    private fun showInstrumentalLocked(songId: String) {
        resultRef = null
        mutableState.value = LyricState(
            songId = songId,
            status = LyricStatus.INSTRUMENTAL,
            lines = listOf(LyricLine("纯音乐，请欣赏", timeSeconds = 0.0)),
            activeLyricIndex = 0,
            translationState = LyricTranslationStatus.NOT_NEEDED,
        )
    }

    private fun activeIndex(current: LyricState): Int {
        if (current.status == LyricStatus.INSTRUMENTAL) return 0
        if (current.status != LyricStatus.READY || current.syncMode == "none") return -1
        val seconds = positionMs.toDouble() / 1000.0
        return current.lines.indexOfLast { line ->
            line.timeSeconds?.let { it.isFinite() && it <= seconds } == true
        }
    }

    private fun startLocked(active: ActiveConnection, songId: String, reference: String?, translate: Boolean) {
        invalidateRequestLocked()
        val ticket = generation
        mutableState.value = mutableState.value.copy(polling = translate, notice = null)
        // Lazy start installs the owner before an immediate dispatcher can run it.
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                readAndPoll(active, songId, ticket, reference, translate)
            } finally {
                synchronized(lock) {
                    if (currentLocked(ticket, active, songId)) {
                        mutableState.value = mutableState.value.copy(polling = false)
                        job = null
                    }
                }
            }
        }
        job = next
        next.start()
    }

    private suspend fun readAndPoll(
        active: ActiveConnection,
        songId: String,
        ticket: Long,
        initialReference: String?,
        initialTranslation: Boolean,
    ) {
        var reference = initialReference
        var translate = initialTranslation
        var recoveredReference = false
        var pollAttempts = 0
        var failedGraceAttempts = 0
        val maxFailedGraceChecks = minOf(maxPollAttempts, 4)
        while (true) {
            if (!synchronized(lock) { currentLocked(ticket, active, songId) }) return
            val document = try {
                withContext(ioDispatcher) {
                    // Blocking transports may complete after cancellation. This check
                    // also prevents queued IO work from starting with a retired session.
                    if (!synchronized(lock) { currentLocked(ticket, active, songId) }) {
                        throw CancellationException("Lyric request retired")
                    }
                    if (translate) active.api.translateLyrics(requireNotNull(reference))
                    else active.api.lyrics(songId, reference)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = error as? ApiFailure
                val retiredReference = failure?.httpStatus == 409 &&
                    failure.code in setOf("LYRIC_RESULT_RETIRED", "LYRIC_RESULT_CHANGED")
                if (reference != null && retiredReference && !recoveredReference) {
                    val stillCurrent = synchronized(lock) {
                        if (!currentLocked(ticket, active, songId)) false
                        else {
                            resultRef = null
                            true
                        }
                    }
                    if (!stillCurrent) return
                    // One fresh chain selection per explicit operation, including its
                    // whole polling window. Repeated 409s cannot form a reload loop.
                    recoveredReference = true
                    reference = null
                    translate = false
                    continue
                }
                synchronized(lock) {
                    if (!currentLocked(ticket, active, songId)) return
                    if (failure?.failure in setOf(Failure.SESSION_EXPIRED, Failure.RETIRED)) {
                        clearLocked()
                        return
                    }
                    val invalidReference = retiredReference ||
                        (failure?.httpStatus == 400 && failure.code == "LYRIC_RESULT_INVALID")
                    if (reference != null && invalidReference) {
                        resultRef = null
                    }
                    if (failure?.httpStatus == 404 && failure.reason == "lyrics_not_found") {
                        resultRef = null
                        mutableState.value = LyricState(songId = songId, status = LyricStatus.EMPTY)
                    } else {
                        val previous = mutableState.value
                        val translationOperation = initialTranslation ||
                            (reference != null && (previous.translationState == LyricTranslationStatus.PENDING || previous.polling))
                        val keepOriginal = translationOperation && !invalidReference &&
                            previous.status == LyricStatus.READY && previous.lines.isNotEmpty() &&
                            failure?.failure != Failure.FORBIDDEN &&
                            !(failure?.httpStatus == 404 && failure.reason == "song_not_found")
                        mutableState.value = if (keepOriginal) previous.copy(
                            translationState = LyricTranslationStatus.FAILED,
                            polling = false, notice = safeNotice(error),
                        ) else previous.copy(status = LyricStatus.FAILED, activeLyricIndex = -1,
                            polling = false, notice = safeNotice(error))
                    }
                }
                return
            }

            val nextReference = document.resultRef?.takeIf { it.isNotBlank() }
            val pending = !document.instrumental &&
                document.translationState == LyricTranslationStatus.PENDING
            val pendingPoll = pending && nextReference != null && pollAttempts < maxPollAttempts
            val initialFailedGracePoll = !initialTranslation &&
                !document.instrumental &&
                document.translationSupported &&
                document.translationState == LyricTranslationStatus.FAILED &&
                nextReference != null &&
                failedGraceAttempts < maxFailedGraceChecks
            val pollAgain = pendingPoll || initialFailedGracePoll
            synchronized(lock) {
                if (!currentLocked(ticket, active, songId)) return
                if (document.instrumental) {
                    showInstrumentalLocked(songId)
                } else {
                    resultRef = nextReference
                    val next = LyricState(
                        songId = songId,
                        status = if (document.lines.isEmpty()) LyricStatus.EMPTY else LyricStatus.READY,
                        lines = document.lines.toList(),
                        translationState = document.translationState,
                        translationSupported = document.translationSupported,
                        syncMode = document.syncMode,
                        polling = pollAgain,
                        notice = if (pending && nextReference == null) ClientNotice.INVALID_RESPONSE else null,
                        translationLanguage = document.translationLanguage,
                        translationTargetLanguage = document.translationTargetLanguage,
                    )
                    mutableState.value = next.copy(activeLyricIndex = activeIndex(next))
                }
            }
            if (!pollAgain) return
            delay(pollIntervalMs)
            if (initialFailedGracePoll) failedGraceAttempts++ else pollAttempts++
            reference = nextReference
            translate = false
        }
    }

    private fun safeNotice(error: Exception): ClientNotice = when ((error as? ApiFailure)?.failure) {
        Failure.INVALID_URL -> ClientNotice.INVALID_URL
        Failure.NOT_READY -> ClientNotice.NOT_READY
        Failure.NETWORK -> ClientNotice.NETWORK
        Failure.INVALID_LOGIN -> ClientNotice.INVALID_LOGIN
        Failure.SESSION_EXPIRED, Failure.RETIRED -> ClientNotice.SESSION_EXPIRED
        Failure.RATE_LIMITED -> ClientNotice.RATE_LIMITED
        Failure.INVALID_RESPONSE -> ClientNotice.INVALID_RESPONSE
        Failure.STORAGE -> ClientNotice.STORAGE_FAILED
        Failure.FORBIDDEN -> ClientNotice.FORBIDDEN
        else -> ClientNotice.REQUEST_FAILED
    }
}
