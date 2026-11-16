package com.flaretune.client.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class InstanceAddress private constructor(val url: HttpUrl) {
    val origin: String get() = url.toString().removeSuffix("/")
    fun sameOrigin(other: HttpUrl) = url.scheme == other.scheme && url.host == other.host && url.port == other.port
    fun endpoint(path: String): HttpUrl = url.resolve(path) ?: throw IllegalArgumentException("Invalid endpoint")
    fun media(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        val parsed = when {
            value.startsWith("https://", true) -> value.toHttpUrlOrNull()
            value.contains("://") -> null
            value.startsWith("/media/") || value.startsWith("/api/") -> url.resolve(value)
            else -> url.resolve("/media/" + value.trimStart('/'))
        }
        return parsed?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }?.toString()
    }
    companion object {
        fun parse(input: String): InstanceAddress {
            val value = input.trim().let { if ("://" in it) it else "https://$it" }
            val parsed = value.toHttpUrlOrNull() ?: throw ApiFailure(Failure.INVALID_URL)
            if (!parsed.isHttps || parsed.username.isNotEmpty() || parsed.password.isNotEmpty() ||
                parsed.encodedPath != "/" || parsed.query != null || parsed.fragment != null) {
                throw ApiFailure(Failure.INVALID_URL)
            }
            return InstanceAddress(parsed)
        }
    }
}
