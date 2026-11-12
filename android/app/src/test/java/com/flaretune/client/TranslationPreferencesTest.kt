package com.flaretune.client

import android.content.Context
import com.flaretune.client.data.AndroidTranslationPreferences
import com.flaretune.client.ui.ClientUiState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TranslationPreferencesTest {
    @Test fun translationDisplayDefaultsOffAndOnlyPersistsOneDeviceBoolean() {
        val context = RuntimeEnvironment.getApplication()
        val backing = context.getSharedPreferences("playback_display_v1", Context.MODE_PRIVATE)
        backing.edit().clear().commit()
        val first = AndroidTranslationPreferences(context)
        assertFalse(first.read())
        first.write(true)
        val recreated = AndroidTranslationPreferences(context)
        assertTrue(recreated.read())
        assertEquals(setOf("translation_enabled"), backing.all.keys)
        assertEquals(true, backing.all["translation_enabled"])
        recreated.write(false)
        assertFalse(first.read())
        assertFalse(ClientUiState().translationEnabled)
    }
}