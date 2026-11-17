package com.flaretune.client.data

import com.flaretune.client.model.AuthSession
import com.flaretune.client.model.ClientNotice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class ActiveConnection(val network: SessionNetwork, val api: AccountApi, @Volatile var auth: AuthSession)

data class InstanceProbeResult(
    val origin: String,
    val googleEnabled: Boolean = false,
    val googleNativeEnabled: Boolean = false,
)

interface Connections {
    val current: ActiveConnection?
    val lastOrigin: String
    val invalidations: SharedFlow<ClientNotice>
    val savedAccounts: List<SavedAccountSummary> get() = emptyList()
    val activeAccountKey: String? get() = current?.let { SavedAccount.key(it.network.address.origin, it.auth.account.id) }
    val selectedAccountKey: String? get() = activeAccountKey
    suspend fun restore(): ActiveConnection?
    suspend fun checkInstance(address: String): InstanceProbeResult =
        InstanceProbeResult(InstanceAddress.parse(address).origin)
    suspend fun login(address: String, username: String, password: String): ActiveConnection
    suspend fun loginWithGoogle(address: String, code: String, codeVerifier: String): ActiveConnection =
        throw ApiFailure(Failure.GOOGLE_UNAVAILABLE)
    suspend fun changePassword(current: String, replacement: String)
    suspend fun logout()
    fun beginAddAccount() {}
    suspend fun cancelAddAccount(): ActiveConnection? = current
    suspend fun switchAccount(accountKey: String): ActiveConnection? = null
    suspend fun removeSavedAccount(accountKey: String) {}
    fun updateActiveAccountProfile(
        displayName: String? = null,
        avatarUrl: String? = null,
        updateAvatar: Boolean = false,
        avatarRevision: Long? = null,
    ) {}
}

class ConnectionManager(
    private val store: SessionStore,
    private val baseClient: OkHttpClient = OkHttpClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Connections {
    private val lock = Any()
    private var generation = 0L
    private var selectionSet = false
    private var selectedKey: String? = null
    private var addReturnKey: String? = null
    // Storage failures must not make an explicitly removed/expired credential usable again in this process.
    private val quarantined = mutableSetOf<String>()
    @Volatile override var current: ActiveConnection? = null
        private set
    override val lastOrigin get() = store.lastOrigin
    override val activeAccountKey: String? get() = synchronized(lock) {
        current?.takeIf { it.network.isActive }?.let { SavedAccount.key(it.network.address.origin, it.auth.account.id) }
    }
    override val selectedAccountKey: String? get() = synchronized(lock) { selected() }
    override val savedAccounts: List<SavedAccountSummary> get() = synchronized(lock) {
        val active = activeAccountKey
        store.readAccounts().accounts.sortedByDescending { it.lastUsedAt }.map {
            SavedAccountSummary(it.accountKey, it.origin, it.accountId, it.username, it.displayName,
                active == it.accountKey, it.needsReauth || it.accountKey in quarantined, it.avatarUrl)
        }
    }
    private val events = MutableSharedFlow<ClientNotice>(extraBufferCapacity = 8)
    override val invalidations: SharedFlow<ClientNotice> = events

    private fun selected(): String? = if (selectionSet) selectedKey else store.readAccounts().activeAccountKey
    private fun select(key: String?) { selectionSet = true; selectedKey = key }
    private fun newNetwork(address: InstanceAddress, cookie: String? = null) =
        SessionNetwork(address, cookie, baseClient, ::invalidate)

    private fun markReauth(key: String) {
        quarantined += key
        val old = store.readAccounts()
        store.writeAccounts(old.copy(accounts = old.accounts.map {
            if (it.accountKey == key) it.copy(cookie = null, needsReauth = true) else it
        }))
    }

    private fun invalidate(network: SessionNetwork) {
        synchronized(lock) {
            val old = current?.takeIf { it.network === network } ?: return
            val key = SavedAccount.key(old.network.address.origin, old.auth.account.id)
            generation++
            current = null
            select(key)
            network.retire()
            try { markReauth(key) } catch (_: Exception) { /* Retired in memory; startup always verifies with server. */ }
        }
        events.tryEmit(ClientNotice.SESSION_EXPIRED)
    }

    private fun begin(key: String?): Long {
        generation++
        current?.network?.retire()
        current = null
        select(key)
        return generation
    }

    private fun commit(ticket: Long, network: SessionNetwork, api: FlareTuneApi, auth: AuthSession,
        restored: SavedAccount? = null): ActiveConnection = synchronized(lock) {
        if (ticket != generation || !network.isActive) { network.retire(); throw ApiFailure(Failure.RETIRED) }
        // A valid cookie for a different server account is still an identity failure.
        if (restored != null && restored.accountId.isNotEmpty() && restored.accountId != auth.account.id)
            throw ApiFailure(Failure.SESSION_EXPIRED)
        val cookie = network.jar.serialized() ?: throw ApiFailure(Failure.SESSION_EXPIRED)
        val account = auth.account
        val key = SavedAccount.key(network.address.origin, account.id)
        val old = store.readAccounts()
        val existingAvatar = old.accounts.find { it.accountKey == key }?.avatarUrl
        val resolvedAvatar = if (restored != null) account.avatarUrl else (account.avatarUrl ?: existingAvatar)
        val saved = SavedAccount(key, network.address.origin, account.id, account.username, account.displayName,
            cookie, maxOf(System.currentTimeMillis(), (old.accounts.maxOfOrNull { it.lastUsedAt } ?: 0L) + 1L), false, resolvedAvatar)
        store.writeAccounts(SavedAccountsSnapshot(old.accounts.filterNot {
            it.accountKey == key || (it.origin == network.address.origin && it.accountId.isEmpty()) ||
                (restored != null && it.accountKey == restored.accountKey)
        } + saved, key))
        select(key)
        quarantined.remove(key)
        addReturnKey = null
        val updatedAuth = auth.copy(account = account.copy(avatarUrl = resolvedAvatar))
        ActiveConnection(network, api, updatedAuth).also { current = it }
    }

    override fun updateActiveAccountProfile(
        displayName: String?,
        avatarUrl: String?,
        updateAvatar: Boolean,
        avatarRevision: Long?,
    ) {
        synchronized(lock) {
            val activeConn = current?.takeIf { it.network.isActive } ?: return
            val key = SavedAccount.key(activeConn.network.address.origin, activeConn.auth.account.id)
            val prevAccount = activeConn.auth.account
            val nextDisplayName = displayName ?: prevAccount.displayName
            val nextAvatarUrl = if (updateAvatar) avatarUrl else prevAccount.avatarUrl
            val nextAvatarRevision = avatarRevision ?: prevAccount.avatarRevision
            val updatedAccount = prevAccount.copy(
                displayName = nextDisplayName,
                avatarUrl = nextAvatarUrl,
                avatarRevision = nextAvatarRevision,
            )
            activeConn.auth = activeConn.auth.copy(account = updatedAccount)
            runCatching {
                val old = store.readAccounts()
                store.writeAccounts(old.copy(accounts = old.accounts.map { saved ->
                    if (saved.accountKey == key) {
                        saved.copy(displayName = nextDisplayName, avatarUrl = nextAvatarUrl)
                    } else saved
                }))
            }
        }
    }

    // Claim the user's intent before the first suspension. A queued IO continuation must never
    // claim a newer generation after a later logout, switch or add-account action has already run.
    private fun checkTicket(ticket: Long) {
        if (ticket != generation) throw ApiFailure(Failure.RETIRED)
    }

    override suspend fun checkInstance(address: String): InstanceProbeResult {
        val normalized = InstanceAddress.parse(address)
        return withContext(ioDispatcher) {
            val network = newNetwork(normalized)
            try {
                val status = FlareTuneApi(network).checkInstance()
                InstanceProbeResult(
                    origin = normalized.origin,
                    googleEnabled = status.googleEnabled,
                    googleNativeEnabled = status.googleNativeEnabled,
                )
            } finally {
                network.retire()
            }
        }
    }

    override suspend fun login(address: String, username: String, password: String): ActiveConnection =
        authenticate(address) { api -> api.login(username, password) }

    override suspend fun loginWithGoogle(address: String, code: String, codeVerifier: String): ActiveConnection =
        authenticate(address) { api -> api.exchangeGoogleLogin(code, codeVerifier) }

    private suspend fun authenticate(address: String, signIn: (FlareTuneApi) -> AuthSession): ActiveConnection {
        val normalized = InstanceAddress.parse(address)
        val ticket = synchronized(lock) {
            val ticket = begin(null)
            store.lastOrigin = normalized.origin
            ticket
        }
        return withContext(ioDispatcher) {
            synchronized(lock) { checkTicket(ticket) }
            val network = newNetwork(normalized)
            try {
                val api = FlareTuneApi(network)
                api.checkReady()
                commit(ticket, network, api, signIn(api))
            } catch (error: Exception) {
                network.retire()
                synchronized(lock) {
                    checkTicket(ticket)
                    current = null
                }
                throw error
            }
        }
    }

    override fun beginAddAccount() = synchronized(lock) {
        // Calling twice from a retained UI does not replace the original cancel destination.
        if (addReturnKey == null) addReturnKey = activeAccountKey ?: selected()
        begin(null)
        Unit
    }

    /** Called under lock, before the caller can suspend. */
    private fun prepareRestore(accountKey: String): Pair<SavedAccount, Long> {
        val old = store.readAccounts()
        val record = old.accounts.find { it.accountKey == accountKey } ?: throw ApiFailure(Failure.REQUEST)
        val saved = if (accountKey in quarantined) record.copy(cookie = null, needsReauth = true) else record
        val ticket = begin(accountKey)
        store.writeAccounts(old.copy(accounts = old.accounts.map { if (it.accountKey == accountKey) saved else it }, activeAccountKey = accountKey))
        store.lastOrigin = saved.origin
        return saved to ticket
    }

    override suspend fun cancelAddAccount(): ActiveConnection? {
        val captured = synchronized(lock) {
            val key = addReturnKey
            addReturnKey = null
            if (key == null) { begin(null); return null }
            prepareRestore(key)
        }
        return restoreCaptured(captured)
    }

    override suspend fun restore(): ActiveConnection? {
        val captured = synchronized(lock) {
            current?.takeIf { it.network.isActive }?.let { return it }
            val key = selected() ?: return null
            prepareRestore(key)
        }
        return restoreCaptured(captured)
    }

    override suspend fun switchAccount(accountKey: String): ActiveConnection? {
        val captured = synchronized(lock) { prepareRestore(accountKey) }
        return restoreCaptured(captured)
    }

    private suspend fun restoreCaptured(captured: Pair<SavedAccount, Long>): ActiveConnection? = withContext(ioDispatcher) {
        val (saved, ticket) = captured
        synchronized(lock) { checkTicket(ticket) }
        val cookie = saved.cookie
        if (saved.needsReauth || cookie == null) return@withContext null
        val network = newNetwork(InstanceAddress.parse(saved.origin), cookie)
        try {
            val api = FlareTuneApi(network)
            api.checkReady()
            commit(ticket, network, api, api.restore(), saved)
        } catch (error: Exception) {
            network.retire()
            synchronized(lock) {
                checkTicket(ticket)
                current = null
                if (error is ApiFailure && error.failure == Failure.SESSION_EXPIRED) markReauth(saved.accountKey)
            }
            if (error is ApiFailure && error.failure == Failure.SESSION_EXPIRED) null else throw error
        }
    }

    override suspend fun removeSavedAccount(accountKey: String) {
        // This action is entirely local: complete its atomic deletion before any suspension or
        // later authentication intent can run, rather than queueing an identity-changing begin().
        synchronized(lock) {
            val old = store.readAccounts()
            if (old.accounts.none { it.accountKey == accountKey }) return
            if (activeAccountKey == accountKey || selected() == accountKey) begin(null)
            else if (current == null) generation++ // An unfinished login has no known identity yet.
            if (addReturnKey == accountKey) addReturnKey = null
            quarantined += accountKey
            store.writeAccounts(SavedAccountsSnapshot(old.accounts.filterNot { it.accountKey == accountKey },
                old.activeAccountKey.takeUnless { it == accountKey }))
        }
    }

    override suspend fun changePassword(current: String, replacement: String) {
        val (connection, ticket) = synchronized(lock) {
            (this@ConnectionManager.current ?: throw ApiFailure(Failure.SESSION_EXPIRED)) to ++generation
        }
        withContext(ioDispatcher) {
            synchronized(lock) {
                checkTicket(ticket)
                if (this@ConnectionManager.current !== connection) throw ApiFailure(Failure.RETIRED)
            }
            connection.api.changePassword(current, replacement)
            synchronized(lock) {
                checkTicket(ticket)
                if (this@ConnectionManager.current !== connection) throw ApiFailure(Failure.RETIRED)
                val key = SavedAccount.key(connection.network.address.origin, connection.auth.account.id)
                begin(key)
                markReauth(key)
            }
        }
    }

    private data class LogoutCapture(val connection: ActiveConnection?, val cookie: String?, val clearError: Exception?)

    override suspend fun logout() {
        val (capture, ticket) = synchronized(lock) {
            val old = current
            val key = activeAccountKey ?: selected()
            val cookie = old?.network?.jar?.serialized()
            val ticket = begin(null)
            addReturnKey = null
            if (key != null) quarantined += key
            var clearError: Exception? = null
            try {
                val saved = store.readAccounts()
                store.writeAccounts(SavedAccountsSnapshot(saved.accounts.filterNot { it.accountKey == key }, null))
            } catch (error: Exception) { clearError = error }
            LogoutCapture(old, cookie, clearError) to ticket
        }
        withContext(ioDispatcher) {
            synchronized(lock) { checkTicket(ticket) }
            val old = capture.connection
            val cookie = capture.cookie
            if (old != null && cookie != null) {
                // Only the captured origin/account/cookie is revoked; completion never changes
                // local state, even if a newer login starts while this HTTP request is in flight.
                val request = Request.Builder().url(old.network.address.endpoint("/api/auth/logout"))
                    .header("Cookie", cookie.substringBefore(';'))
                    .header("Origin", old.network.address.origin).header("X-Requested-With", "FlareTune")
                    .header("X-CSRF-Token", old.auth.csrfToken)
                    .header("X-FlareTune-Expected-Account", old.auth.account.id)
                    .post("{}".toRequestBody("application/json".toMediaType())).build()
                try {
                    baseClient.newBuilder().cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false)
                        .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build().newCall(request).execute().close()
                } catch (_: Exception) { /* Local logout is complete even if revocation is unreachable. */ }
            }
            capture.clearError?.let { throw it }
        }
    }
}
