package com.flaretune.client.model

// Eligibility comes only from the lyrics response, never from the song language.
val LyricState.canRequestLyricTranslation: Boolean
    get() = status == LyricStatus.READY && translationSupported &&
        translationState in setOf(LyricTranslationStatus.IDLE, LyricTranslationStatus.FAILED)

// Pending is visible progress, not permission to submit another generation request.
val LyricState.showLyricTranslationAction: Boolean
    get() = canRequestLyricTranslation ||
        (status == LyricStatus.READY && translationSupported &&
            translationState == LyricTranslationStatus.PENDING)
