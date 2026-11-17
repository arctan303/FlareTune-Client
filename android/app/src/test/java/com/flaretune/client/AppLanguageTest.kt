package com.flaretune.client

import android.content.Context
import com.flaretune.client.data.AndroidAppLanguagePreferences
import com.flaretune.client.data.AppLanguage
import com.flaretune.client.data.AppLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppLanguageTest {
    private fun context(): Context = RuntimeEnvironment.getApplication()

    @Test
    fun defaultsToFollowingTheSystemLanguage() {
        val context = context()
        context.getSharedPreferences("app_language_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val preferences = AndroidAppLanguagePreferences(context)
        assertEquals(AppLanguage.SYSTEM, preferences.read())
        assertSame("following the system must not replace the base context", context, AppLocale.wrap(context))
        assertEquals(AppLanguage.SYSTEM.id, AppLanguage.fromId(null).id)
    }

    @Test
    fun everyLanguageRoundTripsAndUnknownValuesFallBackToSystem() {
        val context = context()
        val backing = context.getSharedPreferences("app_language_v1", Context.MODE_PRIVATE)
        backing.edit().clear().commit()
        for (language in AppLanguage.entries) {
            AndroidAppLanguagePreferences(context).write(language)
            assertEquals(language, AndroidAppLanguagePreferences(context).read())
            assertEquals(language.id, backing.getString("app_language", null))
        }
        backing.edit().putString("app_language", "fr").commit()
        assertEquals(AppLanguage.SYSTEM, AndroidAppLanguagePreferences(context).read())
        assertEquals("system", AppLanguage.SYSTEM.id)
        assertEquals("zh-Hant", AppLanguage.ZH_HANT.languageTag)
        assertEquals("zh-CN", AppLanguage.ZH_CN.languageTag)
    }

    @Test
    fun savedLanguageIsAppliedToResourceLookups() {
        val context = context()
        AndroidAppLanguagePreferences(context).write(AppLanguage.SYSTEM)
        val english = context.getString(R.string.ft_home)

        AndroidAppLanguagePreferences(context).write(AppLanguage.JA)
        val japanese = AppLocale.wrap(context)
        assertEquals("ja", japanese.resources.configuration.locales[0].language)
        assertNotEquals("Japanese must resolve a translated string", english, japanese.getString(R.string.ft_home))

        AndroidAppLanguagePreferences(context).write(AppLanguage.KO)
        val korean = AppLocale.wrap(context)
        assertEquals("ko", korean.resources.configuration.locales[0].language)
        assertNotEquals(english, korean.getString(R.string.ft_home))

        AndroidAppLanguagePreferences(context).write(AppLanguage.ZH_HANT)
        val traditional = AppLocale.wrap(context)
        assertEquals("zh", traditional.resources.configuration.locales[0].language)
        assertEquals("Hant", traditional.resources.configuration.locales[0].script)
        assertNotEquals("Traditional Chinese must resolve a translated string", english, traditional.getString(R.string.ft_home))

        AndroidAppLanguagePreferences(context).write(AppLanguage.ZH_CN)
        val simplified = AppLocale.wrap(context)
        assertEquals("zh", simplified.resources.configuration.locales[0].language)
        assertEquals("CN", simplified.resources.configuration.locales[0].country)
        assertEquals("主页", simplified.getString(R.string.ft_home))

        AndroidAppLanguagePreferences(context).write(AppLanguage.EN)
        val explicitEnglish = AppLocale.wrap(context)
        assertEquals(english, explicitEnglish.getString(R.string.ft_home))
    }

    @Test
    fun formattingLocaleFollowsTheSavedLanguage() {
        val context = context()
        AndroidAppLanguagePreferences(context).write(AppLanguage.KO)
        assertEquals("ko", AppLocale.current(context).language)
        AndroidAppLanguagePreferences(context).write(AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM.id, AndroidAppLanguagePreferences(context).read().id)
        assertEquals(AppLocale.languageTag(context), AppLocale.languageTag(context))
    }
}
