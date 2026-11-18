package com.arcinks.flaretune.data

import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import org.json.JSONObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

const val SESSION_COOKIE_NAME = "__Host-ft_session"

class SessionCookieJar(private val address: InstanceAddress, saved: String? = null) : CookieJar {
    @Volatile var active: Boolean = true
        private set
    private var cookie: Cookie? = saved?.let { Cookie.parse(address.url, it) }?.takeIf(::valid)
    private fun valid(value: Cookie) = value.name == SESSION_COOKIE_NAME && value.secure && value.httpOnly &&
        value.hostOnly && value.domain == address.url.host && value.path == "/" &&
        value.value.matches(Regex("[A-Za-z0-9_-]{43}")) && value.expiresAt > System.currentTimeMillis()

    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!active || !address.sameOrigin(url)) return
        cookies.filter { it.name == SESSION_COOKIE_NAME }.lastOrNull()?.let { cookie = it.takeIf(::valid) }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (active && address.sameOrigin(url)) listOfNotNull(cookie?.takeIf(::valid)) else emptyList()
    @Synchronized fun serialized(): String? = if (active) cookie?.takeIf(::valid)?.toString() else null
    @Synchronized fun retire() { active = false; cookie = null }
}

class SessionNetwork(
    val address: InstanceAddress,
    savedCookie: String? = null,
    baseClient: OkHttpClient = OkHttpClient(),
    private val onInvalidated: (SessionNetwork) -> Unit = {},
) {
    val jar = SessionCookieJar(address, savedCookie)
    val isActive get() = jar.active
    private val common = baseClient.newBuilder()
        .dispatcher(Dispatcher()).connectionPool(ConnectionPool())
        .cookieJar(jar).connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            if (!isActive) throw ApiFailure(Failure.RETIRED)
            if (!chain.request().url.isHttps) throw IOException("HTTPS required")
            val response = chain.proceed(chain.request())
            if (address.sameOrigin(response.request.url) &&
                response.request.url.encodedPath !in setOf("/api/auth/login", "/api/auth/google/exchange", "/api/auth/google/status") &&
                (response.code == 401 || (response.code == 409 &&
                    isAccountChanged(response)))) {
                onInvalidated(this)
            }
            response
        }.build()
    val apiClient: OkHttpClient = common.newBuilder().followRedirects(false).followSslRedirects(false).build()
    val mediaClient: OkHttpClient = common.newBuilder().followRedirects(true).followSslRedirects(false).build()
    fun retire() {
        jar.retire()
        // Closing pooled TLS sockets writes close_notify; run off the caller thread so UI actions never trigger NetworkOnMainThreadException.
        try {
            common.dispatcher.executorService.execute {
                common.dispatcher.cancelAll()
                common.connectionPool.evictAll()
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Executor already shutting down; jar retirement has already invalidated this session.
        }
    }
}

private fun isAccountChanged(response: okhttp3.Response): Boolean = try {
    val body = JSONObject(response.peekBody(4096).string())
    body.opt("error") == "account_changed" || body.opt("code") == "account_changed"
} catch (_: Exception) { false }