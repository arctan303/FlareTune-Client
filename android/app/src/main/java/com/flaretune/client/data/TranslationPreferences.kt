package com.flaretune.client.data

import android.content.Context

/** Device display preference only. It never changes server translation or AI settings. */
interface TranslationPreferences {
    fun read(): Boolean
    fun write(enabled: Boolean)
}

class AndroidTranslationPreferences(context: Context) : TranslationPreferences {
    private val preferences = context.applicationContext.getSharedPreferences("playback_display_v1", Context.MODE_PRIVATE)
    override fun read(): Boolean = try {
        preferences.getBoolean("translation_enabled", false)
    } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }

    override fun write(enabled: Boolean) {
        try {
            if (!preferences.edit().putBoolean("translation_enabled", enabled).commit()) throw ApiFailure(Failure.STORAGE)
        } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }
    }
}