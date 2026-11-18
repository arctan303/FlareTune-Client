package com.arcinks.flaretune.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedConnection(val origin: String, val cookie: String)

/** No password or CSRF token is ever part of the persisted account record. */
data class SavedAccount(
    val accountKey: String,
    val origin: String,
    val accountId: String,
    val username: String,
    val displayName: String,
    val cookie: String?,
    val lastUsedAt: Long = 0L,
    val needsReauth: Boolean = false,
    val avatarUrl: String? = null,
) {
    companion object {
        fun key(origin: String, accountId: String): String = InstanceAddress.parse(origin).origin + "|" + accountId
        internal fun legacy(connection: SavedConnection) = SavedAccount(
            key(connection.origin, ""), InstanceAddress.parse(connection.origin).origin, "", "", "", connection.cookie)
    }
}

data class SavedAccountSummary(
    val accountKey: String,
    val origin: String,
    val accountId: String,
    val username: String,
    val displayName: String,
    val active: Boolean = false,
    val needsReauth: Boolean = false,
    val avatarUrl: String? = null,
)

data class SavedAccountsSnapshot(val accounts: List<SavedAccount> = emptyList(), val activeAccountKey: String? = null)

interface SessionStore {
    // Legacy compatibility for existing integrations; new consumers use the atomic account snapshot.
    fun read(): SavedConnection?
    fun write(connection: SavedConnection)
    fun clear()
    var lastOrigin: String
    fun readAccounts(): SavedAccountsSnapshot
    fun writeAccounts(snapshot: SavedAccountsSnapshot)
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

class AndroidSessionStore private constructor(
    private val prefs: SharedPreferences,
    private val cipher: SessionCipher,
    private val deleteKey: () -> Unit,
) : SessionStore {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("session_secure_v1", Context.MODE_PRIVATE),
        SessionCipher(::sessionKey),
        { sessionKeyStore().deleteEntry(SESSION_KEY_ALIAS) },
    )

    /** Exercises the real preference/encryption/migration path without pretending Robolectric is a hardware Keystore. */
    internal constructor(context: Context, cipher: SessionCipher) : this(
        context.applicationContext.getSharedPreferences("session_secure_v1", Context.MODE_PRIVATE), cipher, {})

    internal constructor(preferences: SharedPreferences, cipher: SessionCipher) : this(preferences, cipher, {})

    override var lastOrigin: String
        get() = prefs.getString("last_origin", "") ?: ""
        set(value) { if (!prefs.edit().putString("last_origin", value).commit()) throw ApiFailure(Failure.STORAGE) }

    @Synchronized override fun readAccounts(): SavedAccountsSnapshot {
        try {
            prefs.getString("encrypted_accounts_v2", null)?.let { blob ->
                val json = decode(blob)
                require(json.getInt("version") == 2)
                val source = json.getJSONArray("accounts")
                val accounts = (0 until source.length()).map { index ->
                    val row = source.getJSONObject(index)
                    val origin = InstanceAddress.parse(row.getString("origin")).origin
                    val id = row.getString("accountId")
                    val key = SavedAccount.key(origin, id)
                    require(row.getString("accountKey") == key)
                    SavedAccount(key, origin, id, row.getString("username"), row.getString("displayName"),
                        row.optString("cookie").takeIf { it.isNotBlank() }, row.getLong("lastUsedAt"), row.getBoolean("needsReauth"),
                        row.optString("avatarUrl").takeIf { it.isNotBlank() })
                }
                require(accounts.map { it.accountKey }.distinct().size == accounts.size)
                val active = json.optString("activeAccountKey").takeIf { it.isNotEmpty() }
                require(active == null || accounts.any { it.accountKey == active })
                return SavedAccountsSnapshot(accounts, active)
            }
            val old = prefs.getString("encrypted_session", null) ?: return SavedAccountsSnapshot()
            val json = decode(old)
            val account = SavedAccount.legacy(SavedConnection(json.getString("origin"), json.getString("cookie")))
            val migrated = SavedAccountsSnapshot(listOf(account), account.accountKey)
            // One synchronous transaction commits the new ciphertext and removes the old one.
            writeAccounts(migrated)
            return migrated
        } catch (_: Exception) {
            // A corrupt envelope is not an instruction to erase unrelated recoverable ciphertext.
            throw ApiFailure(Failure.STORAGE)
        }
    }

    private fun decode(blob: String) = JSONObject(cipher.decrypt(Base64.decode(blob, Base64.NO_WRAP)).toString(Charsets.UTF_8))

    @Synchronized override fun writeAccounts(snapshot: SavedAccountsSnapshot) {
        try {
            require(snapshot.accounts.map { it.accountKey }.distinct().size == snapshot.accounts.size)
            require(snapshot.activeAccountKey == null || snapshot.accounts.any { it.accountKey == snapshot.activeAccountKey })
            val rows = JSONArray()
            snapshot.accounts.forEach { account ->
                require(account.accountKey == SavedAccount.key(account.origin, account.accountId))
                rows.put(JSONObject().put("accountKey", account.accountKey).put("origin", account.origin)
                    .put("accountId", account.accountId).put("username", account.username).put("displayName", account.displayName)
                    .put("cookie", account.cookie ?: "").put("lastUsedAt", account.lastUsedAt).put("needsReauth", account.needsReauth)
                    .put("avatarUrl", account.avatarUrl ?: ""))
            }
            val plain = JSONObject().put("version", 2).put("accounts", rows)
                .put("activeAccountKey", snapshot.activeAccountKey ?: "").toString().toByteArray(Charsets.UTF_8)
            val encoded = Base64.encodeToString(cipher.encrypt(plain), Base64.NO_WRAP)
            if (!prefs.edit().putString("encrypted_accounts_v2", encoded).remove("encrypted_session").commit())
                throw ApiFailure(Failure.STORAGE)
        } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }
    }

    override fun read(): SavedConnection? = readAccounts().let { snapshot ->
        snapshot.accounts.find { it.accountKey == snapshot.activeAccountKey && !it.needsReauth }?.let { account ->
            account.cookie?.let { SavedConnection(account.origin, it) }
        }
    }
    override fun write(connection: SavedConnection) {
        val account = SavedAccount.legacy(connection)
        val old = readAccounts()
        writeAccounts(SavedAccountsSnapshot(old.accounts.filterNot { it.accountKey == account.accountKey } + account, account.accountKey))
    }
    @Synchronized override fun clear() {
        // Global erasure is explicit; removing one account must keep the shared encryption key.
        try { deleteKey() } catch (_: Exception) { throw ApiFailure(Failure.STORAGE) }
        if (!prefs.edit().remove("encrypted_session").remove("encrypted_accounts_v2").commit()) throw ApiFailure(Failure.STORAGE)
    }
}

private const val SESSION_KEY_ALIAS = "FlareTune.Session.v1"
private fun sessionKeyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
private fun sessionKey(): SecretKey {
    (sessionKeyStore().getKey(SESSION_KEY_ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(SESSION_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
    }.generateKey()
}
