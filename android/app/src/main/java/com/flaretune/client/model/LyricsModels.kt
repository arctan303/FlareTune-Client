package com.flaretune.client.model

data class LyricWord(val text: String, val startTimeSeconds: Double, val endTimeSeconds: Double)

data class LyricLine(
    val text: String,
    val translation: String? = null,
    val timeSeconds: Double? = null,
    val endTimeSeconds: Double? = null,
    val words: List<LyricWord> = emptyList(),
)

enum class LyricStatus { IDLE, LOADING, INSTRUMENTAL, READY, EMPTY, FAILED }
enum class LyricTranslationStatus { IDLE, PENDING, READY, FAILED, UNAVAILABLE, NOT_NEEDED }

data class LyricDocument(
    val lines: List<LyricLine> = emptyList(),
    val instrumental: Boolean = false,
    val resultRef: String? = null,
    val translationState: LyricTranslationStatus = LyricTranslationStatus.UNAVAILABLE,
    val translationSupported: Boolean = false,
    val sourceId: String? = null,
    val syncMode: String = "none",
)

data class LyricState(
    val songId: String? = null,
    val status: LyricStatus = LyricStatus.IDLE,
    val lines: List<LyricLine> = emptyList(),
    val activeLyricIndex: Int = -1,
    val translationState: LyricTranslationStatus = LyricTranslationStatus.UNAVAILABLE,
    val translationSupported: Boolean = false,
    val syncMode: String = "none",
    val polling: Boolean = false,
    val notice: ClientNotice? = null,
)
