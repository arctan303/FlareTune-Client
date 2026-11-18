package com.arcinks.flaretune

import android.content.Context
import com.arcinks.flaretune.data.AndroidTranslationPreferences
import com.arcinks.flaretune.data.AppThemeMode
import com.arcinks.flaretune.data.BluetoothLyricsMode
import com.arcinks.flaretune.ui.ClientUiState
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

    @Test fun keepScreenOnDefaultsTrueAndPersistsWithoutChangingTranslation() {
        val context = RuntimeEnvironment.getApplication()
        val backing = context.getSharedPreferences("playback_display_v1", Context.MODE_PRIVATE)
        backing.edit().clear().commit()
        val first = AndroidTranslationPreferences(context)
        assertTrue(first.readKeepScreenOn())
        first.writeKeepScreenOn(false)
        assertFalse(AndroidTranslationPreferences(context).readKeepScreenOn())
        assertFalse(first.read())
        assertTrue(ClientUiState().keepScreenOnEnabled)
        first.writeKeepScreenOn(true)
        assertTrue(AndroidTranslationPreferences(context).readKeepScreenOn())
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
            catch (_: com.arcinks.flaretune.data.ApiFailure) { }
        }
        backing.edit().putFloat("player_gain", Float.NaN).commit()
        try { first.readVolume(); fail("Expected corrupt gain rejection") }
        catch (_: com.arcinks.flaretune.data.ApiFailure) { }
    }

    @Test fun themeModeDefaultsToSystemAndPersistsAcrossInstances() {
        val context = RuntimeEnvironment.getApplication()
        val backing = context.getSharedPreferences("playback_display_v1", Context.MODE_PRIVATE)
        backing.edit().clear().commit()
        val first = AndroidTranslationPreferences(context)
        assertEquals(AppThemeMode.SYSTEM, first.readThemeMode())
        assertTrue(backing.all.isEmpty())
        assertEquals(AppThemeMode.SYSTEM, ClientUiState().themeMode)
        first.writeThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, AndroidTranslationPreferences(context).readThemeMode())
        first.writeThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, AndroidTranslationPreferences(context).readThemeMode())
        first.writeThemeMode(AppThemeMode.SYSTEM)
        assertEquals(AppThemeMode.SYSTEM, AndroidTranslationPreferences(context).readThemeMode())
    }

    @Test fun bluetoothLyricsModeDefaultsToOffPersistsAndHandlesCorruptValues() {
        val context = RuntimeEnvironment.getApplication()
        val backing = context.getSharedPreferences("playback_display_v1", Context.MODE_PRIVATE)
        backing.edit().clear().commit()
        val first = AndroidTranslationPreferences(context)
        assertEquals(BluetoothLyricsMode.OFF, first.readBluetoothLyricsMode())
        assertTrue(backing.all.isEmpty())
        assertEquals(BluetoothLyricsMode.OFF, ClientUiState().bluetoothLyricsMode)

        first.writeBluetoothLyricsMode(BluetoothLyricsMode.BLUETOOTH_AUDIO)
        assertEquals(BluetoothLyricsMode.BLUETOOTH_AUDIO, AndroidTranslationPreferences(context).readBluetoothLyricsMode())
        assertEquals("bluetooth_audio", backing.getString("bluetooth_lyrics_mode", null))

        first.writeBluetoothLyricsMode(BluetoothLyricsMode.ALWAYS)
        assertEquals(BluetoothLyricsMode.ALWAYS, AndroidTranslationPreferences(context).readBluetoothLyricsMode())

        first.writeBluetoothLyricsMode(BluetoothLyricsMode.OFF)
        assertEquals(BluetoothLyricsMode.OFF, AndroidTranslationPreferences(context).readBluetoothLyricsMode())

        backing.edit().putString("bluetooth_lyrics_mode", "CORRUPT_VALUE").commit()
        assertEquals(BluetoothLyricsMode.OFF, AndroidTranslationPreferences(context).readBluetoothLyricsMode())
    }
}