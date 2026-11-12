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

    @Test fun playerGainDefaultsToOneSurvivesReconstructionAndRejectsCorruptValues() {
        val context = RuntimeEnvironment.getApplication()
        val backing = context.getSharedPreferences("playback_display_v1", Context.MODE_PRIVATE)
        backing.edit().clear().commit()
        val first = AndroidTranslationPreferences(context)
        assertEquals(1f, first.readVolume(), .0001f)
        first.writeVolume(.4f)
        assertEquals(.4f, AndroidTranslationPreferences(context).readVolume(), .0001f)
        assertEquals(setOf("player_gain"), backing.all.keys)
        for (gain in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f)) {
            try { first.writeVolume(gain); fail("Expected invalid gain rejection") }
            catch (_: com.flaretune.client.data.ApiFailure) { }
        }
        backing.edit().putFloat("player_gain", Float.NaN).commit()
        try { first.readVolume(); fail("Expected corrupt gain rejection") }
        catch (_: com.flaretune.client.data.ApiFailure) { }
    }
}