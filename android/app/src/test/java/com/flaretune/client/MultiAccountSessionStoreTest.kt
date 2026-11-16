package com.flaretune.client

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.flaretune.client.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
class MultiAccountSessionStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("session_secure_v1", Context.MODE_PRIVATE)
    private val cipher get() = SessionCipher { SecretKeySpec(ByteArray(32) { 41 }, "AES") }
    private val cookie = "$SESSION_COOKIE_NAME=${"A".repeat(43)}; Path=/; Secure; HttpOnly; Max-Age=3600"
    @Before fun clear() { prefs.edit().clear().commit() }
    private fun account(origin: String, id: String) = SavedAccount(
        SavedAccount.key(origin, id), origin, id, "user-$id", "Listener $id", cookie, 123L)

    @Test fun legacyCiphertextMigratesAtomicallyAndCanStillResumeAfterReconstruction() {
        val old = JSONObject().put("origin", "https://music.example/").put("cookie", cookie).toString().toByteArray()
        val encrypted = Base64.encodeToString(cipher.encrypt(old), Base64.NO_WRAP)
        prefs.edit().putString("encrypted_session", encrypted).putString("last_origin", "https://music.example").commit()
        val store = AndroidSessionStore(context, cipher)
        val snapshot = store.readAccounts()
        assertEquals(1, snapshot.accounts.size)
        assertEquals("https://music.example", snapshot.accounts.single().origin)
        assertEquals(cookie, snapshot.accounts.single().cookie)
        assertEquals(snapshot.accounts.single().accountKey, snapshot.activeAccountKey)
        assertFalse(prefs.contains("encrypted_session"))
        assertTrue(prefs.contains("encrypted_accounts_v2"))
        val recreated = AndroidSessionStore(context, cipher)
        assertEquals(snapshot, recreated.readAccounts())
        assertEquals(SavedConnection("https://music.example", cookie), recreated.read())
        assertEquals("https://music.example", recreated.lastOrigin)
        assertFalse(prefs.all.values.any { it.toString().contains(cookie) })
    }

    @Test fun allAccountMetadataAndCookiesAreEncryptedAndRoundTripAcrossInstances() {
        val first = account("https://music.example", "one")
        val second = account("https://music.example", "two")
        val third = account("https://other.example:8443", "one").copy(cookie = null, needsReauth = true)
        val expected = SavedAccountsSnapshot(listOf(first, second, third), second.accountKey)
        AndroidSessionStore(context, cipher).writeAccounts(expected)
        val encoded = prefs.getString("encrypted_accounts_v2", null)!!
        val storedBytes = Base64.decode(encoded, Base64.NO_WRAP)
        assertFalse(storedBytes.toString(Charsets.ISO_8859_1).contains(first.username))
        assertEquals(setOf("encrypted_accounts_v2"), prefs.all.keys)
        val json = JSONObject(cipher.decrypt(storedBytes).toString(Charsets.UTF_8))
        assertEquals(2, json.getInt("version"))
        assertEquals(3, json.getJSONArray("accounts").length())
        assertFalse(json.toString().contains("password"))
        assertEquals(expected, AndroidSessionStore(context, cipher).readAccounts())
        assertEquals(SavedConnection(second.origin, cookie), AndroidSessionStore(context, cipher).read())
        AndroidSessionStore(context, cipher).writeAccounts(expected.copy(accounts = listOf(first, third), activeAccountKey = first.accountKey))
        assertEquals(listOf(first, third), AndroidSessionStore(context, cipher).readAccounts().accounts)
    }

    @Test fun failedMigrationDoesNotDeleteTheOnlyExistingResumeCookie() {
        val encrypted = Base64.encodeToString(cipher.encrypt(JSONObject().put("origin", "https://music.example")
            .put("cookie", cookie).toString().toByteArray()), Base64.NO_WRAP)
        prefs.edit().putString("encrypted_session", encrypted).commit()
        val failedPrefs = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val editor = prefs.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        editor.putString(key, value)
                        return this
                    }
                    override fun remove(key: String?): SharedPreferences.Editor {
                        editor.remove(key)
                        return this
                    }
                    override fun commit() = false
                }
            }
        }
        val failing = AndroidSessionStore(failedPrefs, cipher)
        assertThrows(ApiFailure::class.java) { failing.readAccounts() }
        assertEquals(encrypted, prefs.getString("encrypted_session", null))
        assertFalse(prefs.contains("encrypted_accounts_v2"))
        assertEquals(cookie, AndroidSessionStore(context, cipher).read()!!.cookie)
    }

    @Test fun ciphertextTamperingAndWrongKeyFailClosedWithoutErasingTheOriginal() {
        val first = account("https://music.example", "one")
        AndroidSessionStore(context, cipher).writeAccounts(SavedAccountsSnapshot(listOf(first), first.accountKey))
        val encoded = prefs.getString("encrypted_accounts_v2", null)!!
        val wrongKey = SessionCipher { SecretKeySpec(ByteArray(32) { 42 }, "AES") }
        assertThrows(ApiFailure::class.java) { AndroidSessionStore(context, wrongKey).readAccounts() }
        assertEquals(encoded, prefs.getString("encrypted_accounts_v2", null))
        val changed = Base64.decode(encoded, Base64.NO_WRAP).apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        prefs.edit().putString("encrypted_accounts_v2", Base64.encodeToString(changed, Base64.NO_WRAP)).commit()
        assertThrows(ApiFailure::class.java) { AndroidSessionStore(context, cipher).readAccounts() }
        assertNull(AndroidSessionStore(context, cipher).runCatching { read() }.getOrNull())
    }

    @Test fun inconsistentAccountKeyIsRejectedBeforeReplacingExistingAccounts() {
        val first = account("https://music.example", "one")
        val store = AndroidSessionStore(context, cipher)
        store.writeAccounts(SavedAccountsSnapshot(listOf(first), first.accountKey))
        assertThrows(ApiFailure::class.java) {
            store.writeAccounts(SavedAccountsSnapshot(listOf(first.copy(accountKey = "wrong")), "wrong"))
        }
        assertEquals(listOf(first), store.readAccounts().accounts)
    }
}
