package com.arcinks.flaretune.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Interface languages of the app.
 *
 * Ids intentionally match the web client's UI language ids (`server/shared/uiLanguages.js`) so the
 * web player and the native app expose the same five languages. [SYSTEM] means "follow the device
 * language" and is the default, which keeps the existing behaviour for every current install.
 */
enum class AppLanguage(val id: String, val languageTag: String?) {
    SYSTEM("system", null),
    ZH_CN("zh", "zh-CN"),
    ZH_HANT("zh-Hant", "zh-Hant"),
    EN("en", "en"),
    JA("ja", "ja"),
    KO("ko", "ko");

    companion object {
        /** Unknown or removed ids fall back to following the device language. */
        fun fromId(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** Device-local interface language choice. Never sent to the instance. */
interface AppLanguagePreferences {
    fun read(): AppLanguage
    fun write(language: AppLanguage)
}

class AndroidAppLanguagePreferences(context: Context) : AppLanguagePreferences {
    // Read through the caller's own context: during Application.attachBaseContext the application
    // context does not exist yet, and getSharedPreferences already returns the process-wide instance.
    private val preferences = context.getSharedPreferences("app_language_v1", Context.MODE_PRIVATE)

    override fun read(): AppLanguage = try {
        AppLanguage.fromId(preferences.getString("app_language", null))
    } catch (_: Exception) {
        throw ApiFailure(Failure.STORAGE)
    }

    override fun write(language: AppLanguage) {
        try {
            if (!preferences.edit().putString("app_language", language.id).commit()) throw ApiFailure(Failure.STORAGE)
        } catch (_: Exception) {
            throw ApiFailure(Failure.STORAGE)
        }
    }
}

/**
 * Applies the saved interface language to resource lookups.
 *
 * The override is a configuration overlay on the activity/service/application base context, which is
 * why the app does not need an AppCompat theme migration. [SYSTEM] returns the untouched context so
 * Android resolves resources against the device locale exactly as before.
 */
object AppLocale {
    fun languageTag(context: Context): String? = try {
        AndroidAppLanguagePreferences(context).read().languageTag
    } catch (_: Exception) {
        null
    }

    fun wrap(base: Context): Context {
        val tag = languageTag(base) ?: return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }

    /** Locale for dates, times, sorting and number formatting shown by the app. */
    fun current(context: Context): Locale =
        languageTag(context)?.let(Locale::forLanguageTag) ?: Locale.getDefault()
}
