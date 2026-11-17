package com.flaretune.client.data

import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Generates PKCE verifiers/challenges and builds native Google login handoff URLs.
 */
object GoogleAuthPkce {
    const val CALLBACK_SCHEME = "flaretune"
    const val CALLBACK_HOST = "auth"
    const val CALLBACK_PATH = "/google/callback"

    private val random = SecureRandom()

    fun generateVerifier(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return encodeBase64Url(bytes)
    }

    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        return encodeBase64Url(digest)
    }

    fun buildNativeStartUri(origin: String, challenge: String): Uri =
        Uri.parse(origin).buildUpon()
            .encodedPath("/auth/google/native/start")
            .appendQueryParameter("challenge", challenge)
            .build()

    fun isCallbackUri(uri: Uri?): Boolean =
        uri != null &&
            uri.scheme == CALLBACK_SCHEME &&
            uri.host == CALLBACK_HOST &&
            uri.path == CALLBACK_PATH

    private fun encodeBase64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
