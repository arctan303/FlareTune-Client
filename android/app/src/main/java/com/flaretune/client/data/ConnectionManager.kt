package com.flaretune.client.data

import com.flaretune.client.model.AuthSession
import com.flaretune.client.model.ClientNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class ActiveConnection(val network: SessionNetwork, val api: AccountApi, val auth: AuthSession)

interface Connections {
    val current: ActiveConnection?
    val lastOrigin: String
    val invalidations: SharedFlow<ClientNotice>
    suspend fun restore(): ActiveConnection?
    suspend fun login(address: String, username: String, password: String): ActiveConnection
    suspend fun changePassword(current: String, replacement: String)
    suspend fun logout()
}

class ConnectionManager(private val store: SessionStore, private val baseClient: OkHttpClient = OkHttpClient()) : Connections {
    private val lock = Any()
    private var generation = 0L
    @Volatile override var current: ActiveConnection? = null
        private set
    override val lastOrigin get() = store.lastOrigin
    private val events = MutableSharedFlow<ClientNotice>(extraBufferCapacity = 8)
    override val invalidations: SharedFlow<ClientNotice> = events

    private fun newNetwork(address: InstanceAddress, cookie: String? = null) =
        SessionNetwork(address, cookie, baseClient, ::invalidate)

    private fun invalidate(network: SessionNetwork) {
        synchronized(lock) {
            if (current?.network !== network) return
            generation++
            current = null
            network.retire()
            try { store.clear() } catch (_: Exception) { /* Retired in memory; next startup verifies with server. */ }
        }
        events.tryEmit(ClientNotice.SESSION_EXPIRED)
    }

    private fun begin(): Long = synchronized(lock) {
        generation++
        current?.network?.retire()
        current = null
        store.clear()
        generation
    }

    private fun commit(ticket: Long, network: SessionNetwork, api: FlareTuneApi, auth: AuthSession): ActiveConnection =
        synchronized(lock) {
            if (ticket != generation || !network.isActive) { network.retire(); throw ApiFailure(Failure.RETIRED) }
            val cookie = network.jar.serialized() ?: throw ApiFailure(Failure.INVALID_RESPONSE)
            store.write(SavedConnection(network.address.origin, cookie))
            ActiveConnection(network, api, auth).also { current = it }
        }

    override suspend fun login(address: String, username: String, password: String): ActiveConnection = withContext(Dispatchers.IO) {
        val normalized = InstanceAddress.parse(address)
        val ticket = begin()
        store.lastOrigin = normalized.origin
        val network = newNetwork(normalized)
        try {
            val api = FlareTuneApi(network)
            api.checkReady()
            commit(ticket, network, api, api.login(username, password))
        } catch (error: Exception) {
            network.retire()
            synchronized(lock) { if (ticket == generation) { current = null; store.clear() } }
            throw error
        }
    }

    override suspend fun restore(): ActiveConnection? = withContext(Dispatchers.IO) {
        val (saved, ticket) = synchronized(lock) {
            current?.takeIf { it.network.isActive }?.let { return@withContext it }
            val saved = store.read() ?: return@withContext null
            // Reading the resume material and claiming its generation are one transaction.
            saved to ++generation
        }
        val network = newNetwork(InstanceAddress.parse(saved.origin), saved.cookie)
        try {
            val api = FlareTuneApi(network)
            api.checkReady()
            commit(ticket, network, api, api.restore())
        } catch (error: Exception) {
            network.retire()
            // A network outage keeps the encrypted cookie for a later retry; no catalog is shown.
            if (error is ApiFailure && error.failure in setOf(Failure.NETWORK, Failure.NOT_READY)) throw error
            synchronized(lock) { if (ticket == generation) { current = null; store.clear() } }
            if (error is ApiFailure && error.failure == Failure.SESSION_EXPIRED) null else throw error
        }
    }

    override suspend fun changePassword(current: String, replacement: String): Unit = withContext(Dispatchers.IO) {
        val (connection, ticket) = synchronized(lock) {
            (this@ConnectionManager.current ?: throw ApiFailure(Failure.SESSION_EXPIRED)) to generation
        }
        connection.api.changePassword(current, replacement)
        synchronized(lock) {
            if (ticket != generation || this@ConnectionManager.current !== connection) throw ApiFailure(Failure.RETIRED)
            generation++
            connection.network.retire()
            this@ConnectionManager.current = null
            store.clear()
        }
        Unit
    }

    private data class LogoutCapture(val connection: ActiveConnection?, val cookie: String?, val clearError: Exception?)

    override suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        val capture = synchronized(lock) {
            val old = current
            val cookie = old?.network?.jar?.serialized()
            generation++
            old?.network?.retire()
            current = null
            var clearError: Exception? = null
            try { store.clear() } catch (error: Exception) { clearError = error }
            LogoutCapture(old, cookie, clearError)
        }
        val old = capture.connection
        val cookie = capture.cookie
        if (old != null && cookie != null) {
            // Standalone, origin-pinned revocation request survives retirement of the old session client.
            val request = Request.Builder().url(old.network.address.endpoint("/api/auth/logout"))
                .header("Cookie", cookie.substringBefore(';'))
                .header("Origin", old.network.address.origin).header("X-Requested-With", "FlareTune")
                .header("X-CSRF-Token", old.auth.csrfToken)
                .header("X-FlareTune-Expected-Account", old.auth.account.id)
                .post("{}".toRequestBody("application/json".toMediaType())).build()
            try {
                baseClient.newBuilder().cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false)
                    .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build().newCall(request).execute().close()
            } catch (_: Exception) { /* Local logout is complete even if the old server session cannot be revoked. */ }
        }
        capture.clearError?.let { throw it }
        Unit
    }
}
