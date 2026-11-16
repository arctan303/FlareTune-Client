package com.flaretune.client

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flaretune.client.data.*
import com.flaretune.client.model.*
import com.flaretune.client.playback.PlaybackService
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

/** Real-device checks use generated silent audio and temporary credentials, never a user's account. */
@RunWith(AndroidJUnit4::class)
class DevicePlaybackTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return result!!.getOrThrow()
    }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("Timed out: $label")
    }

    @Test fun androidKeyStoreEncryptsRandomlyAndRejectsTampering() {
        val alias = "FlareTune.DeviceTest." + UUID.randomUUID()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build())
            }.generateKey()
            val cipher = SessionCipher { key }
            val plain = "device-only-fixture".toByteArray()
            val encrypted = cipher.encrypt(plain)
            assertArrayEquals(plain, cipher.decrypt(encrypted))
            assertFalse(encrypted.contentEquals(cipher.encrypt(plain)))
            val altered = encrypted.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
            assertThrows(Exception::class.java) { cipher.decrypt(altered) }
        } finally { keyStore.deleteEntry(alias) }
    }

    @Test fun realMediaSessionStreamsSeeksChangesTracksAndContinuesInBackground() {
        val context = instrumentation.targetContext
        val app = context.applicationContext as ClientApplication
        // Do not replace a user's existing session or queue with fixtures.
        assumeTrue("Requires a signed-out development install", AndroidSessionStore(app).read() == null)
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        assumeTrue("Requires a signed-out development install", app.connections.current == null)

        val certificate = HeldCertificate.Builder().commonName("localhost")
            .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1")
            .addSubjectAlternativeName("::1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val base = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
        val cookies = ConcurrentLinkedQueue<String>()
        val audio = silentWave()
        val server = MockWebServer().apply {
            useHttps(serverTls.sslSocketFactory(), false)
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    cookies.add(request.getHeader("Cookie") ?: "")
                    val start = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                    if (start !in audio.indices) return MockResponse().setResponseCode(416)
                    return MockResponse().setResponseCode(if (start == 0) 200 else 206)
                        .setHeader("Content-Type", "audio/wav").setHeader("Accept-Ranges", "bytes")
                        .apply { if (start > 0) setHeader("Content-Range", "bytes $start-${audio.lastIndex}/${audio.size}") }
                        .setBody(Buffer().write(audio, start, audio.size - start))
                }
            }
            start()
        }
        val token = "D".repeat(43)
        val address = InstanceAddress.parse(server.url("/").toString())
        val network = SessionNetwork(address,
            "$SESSION_COOKIE_NAME=$token; Path=/; Secure; HttpOnly; Max-Age=600", base)
        val connection = ActiveConnection(network, FlareTuneApi(network),
            AuthSession(Account("device-fixture", "fixture", "Fixture"), "fixture-csrf", false))
        var controller: MediaController? = null
        try {
            main { app.playbackRuntime.install(connection) }
            val future = main { MediaController.Builder(context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync() }
            val player = future.get(12, TimeUnit.SECONDS).also { controller = it }
            main {
                player.setMediaItems(listOf(
                    MediaItem.Builder().setMediaId("first").setUri(server.url("/first.wav").toString()).build(),
                    MediaItem.Builder().setMediaId("second").setUri(server.url("/second.wav").toString()).build()))
                player.prepare()
                player.play()
            }
            await("real audio pipeline ready") { main { player.isPlaying && player.duration in 29_000..31_000 } }
            await("playback advances") { main { player.currentPosition > 300 } }
            main { player.pause() }
            await("pause") { main { !player.isPlaying && !player.playWhenReady } }
            main { player.seekTo(10_000) }
            await("seek") { main { player.playbackState == Player.STATE_READY && player.currentPosition in 9_800..10_200 } }
            main {
                player.seekToNextMediaItem()
                player.shuffleModeEnabled = true
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.play()
            }
            await("next track") { main { player.currentMediaItem?.mediaId == "second" && player.isPlaying } }
            assertTrue(main { player.shuffleModeEnabled })
            assertEquals(Player.REPEAT_MODE_ALL, main { player.repeatMode })
            val beforeBackground = main { player.currentPosition }
            main { activity.moveTaskToBack(true) }
            await("background position advances") { main { player.isPlaying && player.currentPosition > beforeBackground + 1_500 } }
            val notifications = app.getSystemService(NotificationManager::class.java).activeNotifications
            assertTrue("MediaSession notification missing", notifications.any { it.notification.category == Notification.CATEGORY_TRANSPORT })
            assertTrue(cookies.isNotEmpty())
            assertTrue("Instance cookie absent from audio requests", cookies.all { it == "$SESSION_COOKIE_NAME=$token" })
        } finally {
            main {
                controller?.run { stop(); clearMediaItems(); release() }
                app.playbackRuntime.clear()
            }
            network.retire()
            server.shutdown()
            base.dispatcher.executorService.shutdownNow()
            base.connectionPool.evictAll()
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
    }

    private fun silentWave(): ByteArray {
        val pcmBytes = 8_000 * 2 * 30
        return ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcmBytes); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8_000); putInt(16_000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(pcmBytes)
        }.array()
    }
}
