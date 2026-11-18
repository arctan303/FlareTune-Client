package com.arcinks.flaretune.data

import android.content.Context

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

enum class BluetoothLyricsMode { OFF, BLUETOOTH_AUDIO, ALWAYS }

/** Device display and player gain preferences. Never changes server AI or system output volume. */
interface TranslationPreferences {
    fun read(): Boolean
    fun write(enabled: Boolean)
    fun readKeepScreenOn(): Boolean = true
    fun writeKeepScreenOn(enabled: Boolean) {}
    fun readThemeMode(): AppThemeMode = AppThemeMode.SYSTEM
    fun writeThemeMode(mode: AppThemeMode) {}
    fun readBluetoothLyricsMode(): BluetoothLyricsMode = BluetoothLyricsMode.OFF
    fun writeBluetoothLyricsMode(mode: BluetoothLyricsMode) {}
    fun readIncludePrereleases(): Boolean = false
    fun writeIncludePrereleases(enabled: Boolean) {}
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

    override fun readBluetoothLyricsMode(): BluetoothLyricsMode = try {
        when (preferences.getString("bluetooth_lyrics_mode", "off")) {
            "bluetooth_audio" -> BluetoothLyricsMode.BLUETOOTH_AUDIO
            "always" -> BluetoothLyricsMode.ALWAYS
            else -> BluetoothLyricsMode.OFF
        }
    } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }

    override fun writeBluetoothLyricsMode(mode: BluetoothLyricsMode) {
        val raw = when (mode) {
            BluetoothLyricsMode.OFF -> "off"
            BluetoothLyricsMode.BLUETOOTH_AUDIO -> "bluetooth_audio"
            BluetoothLyricsMode.ALWAYS -> "always"
        }
        try {
            if (!preferences.edit().putString("bluetooth_lyrics_mode", raw).commit()) throw ApiFailure(Failure.STORAGE)
        } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }
    }

    override fun readIncludePrereleases(): Boolean = try {
        preferences.getBoolean("include_prerelease_updates", false)
    } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }

    override fun writeIncludePrereleases(enabled: Boolean) {
        try {
            if (!preferences.edit().putBoolean("include_prerelease_updates", enabled).commit()) throw ApiFailure(Failure.STORAGE)
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