package com.flaretune.client.data

import android.content.Context

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

/** Device display and player gain preferences. Never changes server AI or system output volume. */
interface TranslationPreferences {
    fun read(): Boolean
    fun write(enabled: Boolean)
    fun readKeepScreenOn(): Boolean = true
    fun writeKeepScreenOn(enabled: Boolean) {}
    fun readThemeMode(): AppThemeMode = AppThemeMode.SYSTEM
    fun writeThemeMode(mode: AppThemeMode) {}
    fun readVolume(): Float = 1f
    fun writeVolume(volume: Float) {}
}

class AndroidTranslationPreferences(context: Context) : TranslationPreferences {
    private val preferences = context.applicationContext.getSharedPreferences("playback_display_v1", Context.MODE_PRIVATE)
    override fun read(): Boolean = try {
        preferences.getBoolean("translation_enabled", false)
    } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }

    override fun readKeepScreenOn(): Boolean = try {
        preferences.getBoolean("keep_screen_on", true)
    } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }

    override fun writeKeepScreenOn(enabled: Boolean) {
        try {
            if (!preferences.edit().putBoolean("keep_screen_on", enabled).commit()) throw ApiFailure(Failure.STORAGE)
        } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }
    }

    override fun readThemeMode(): AppThemeMode = try {
        when (preferences.getString("theme_mode", "system")) {
            "light" -> AppThemeMode.LIGHT
            "dark" -> AppThemeMode.DARK
            else -> AppThemeMode.SYSTEM
        }
    } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }

    override fun writeThemeMode(mode: AppThemeMode) {
        val raw = when (mode) {
            AppThemeMode.SYSTEM -> "system"
            AppThemeMode.LIGHT -> "light"
            AppThemeMode.DARK -> "dark"
        }
        try {
            if (!preferences.edit().putString("theme_mode", raw).commit()) throw ApiFailure(Failure.STORAGE)
        } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }
    }

    override fun readVolume(): Float = try {
        preferences.getFloat("player_gain", 1f).also {
            if (!it.isFinite() || it !in 0f..1f) throw ApiFailure(Failure.STORAGE)
        }
    } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }

    override fun writeVolume(volume: Float) {
        if (!volume.isFinite() || volume !in 0f..1f) throw ApiFailure(Failure.STORAGE)
        try {
            if (!preferences.edit().putFloat("player_gain", volume).commit()) throw ApiFailure(Failure.STORAGE)
        } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }
    }

    override fun write(enabled: Boolean) {
        try {
            if (!preferences.edit().putBoolean("translation_enabled", enabled).commit()) throw ApiFailure(Failure.STORAGE)
        } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }
    }
}