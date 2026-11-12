package com.flaretune.client.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedConnection(val origin: String, val cookie: String)
interface SessionStore {
    fun read(): SavedConnection?
    fun write(connection: SavedConnection)
    fun clear()
    var lastOrigin: String
}

class SessionCipher(private val key: () -> SecretKey) {
    private val purpose = "FlareTune.Android.Session.v1".toByteArray()
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(purpose)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
    }
    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size >= 30 && blob[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(1, 13)))
        cipher.updateAAD(purpose)
        return cipher.doFinal(blob.copyOfRange(13, blob.size))
    }
}

class AndroidSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("session_secure_v1", Context.MODE_PRIVATE)
    private val alias = "FlareTune.Session.v1"
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    private val cipher = SessionCipher(::key)
    override var lastOrigin: String
        get() = prefs.getString("last_origin", "") ?: ""
        set(value) { if (!prefs.edit().putString("last_origin", value).commit()) throw ApiFailure(Failure.STORAGE) }

    override fun read(): SavedConnection? {
        val blob = prefs.getString("encrypted_session", null) ?: return null
        try {
            val json = JSONObject(cipher.decrypt(Base64.decode(blob, Base64.NO_WRAP)).toString(Charsets.UTF_8))
            return SavedConnection(InstanceAddress.parse(json.getString("origin")).origin, json.getString("cookie"))
        } catch (_: Exception) {
            clear()
            throw ApiFailure(Failure.STORAGE)
        }
    }
    override fun write(connection: SavedConnection) {
        try {
            val plain = JSONObject().put("origin", connection.origin).put("cookie", connection.cookie).toString().toByteArray()
            val encoded = Base64.encodeToString(cipher.encrypt(plain), Base64.NO_WRAP)
            if (!prefs.edit().putString("encrypted_session", encoded).commit()) throw ApiFailure(Failure.STORAGE)
        } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }
    }
    override fun clear() {
        // Delete the key first: a stale preference cannot revive a cleared session.
        try { keyStore().deleteEntry(alias) } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }
        if (!prefs.edit().remove("encrypted_session").commit()) throw ApiFailure(Failure.STORAGE)
    }
}
