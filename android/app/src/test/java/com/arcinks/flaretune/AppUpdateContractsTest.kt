package com.arcinks.flaretune

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.arcinks.flaretune.data.*
import com.arcinks.flaretune.model.*
import com.arcinks.flaretune.playback.PlaybackGateway
import com.arcinks.flaretune.ui.AppUpdateStatus
import com.arcinks.flaretune.ui.ClientScreen
import com.arcinks.flaretune.ui.PlaybackSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppUpdateContractsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var baseClient: OkHttpClient

    @Before
    fun setup() {
        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .addSubjectAlternativeName("::1")
            .build()
        val tlsServer = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(tlsServer.sslSocketFactory(), false); start() }
        baseClient = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
    }

    @After
    fun teardown() {
        server.shutdown()
        baseClient.dispatcher.executorService.shutdownNow()
        baseClient.connectionPool.evictAll()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun appVersionParsesAndComparesSemVerAndPrereleases() {
        val dev9 = AppVersion.parse("0.1.0-dev.9")!!
        val dev10 = AppVersion.parse("v0.1.0-dev.10")!!
        val stable = AppVersion.parse("0.1.0")!!
        val numericPre = AppVersion.parse("0.1.0-1")!!
        val alpha = AppVersion.parse("0.1.0-alpha.1")!!
        val nextMinor = AppVersion.parse("V0.2.0+build12")!!

        assertTrue(dev9 < dev10)
        assertTrue(numericPre < alpha) // Numeric vs non-numeric identifiers follow SemVer 2.0.0.
        assertTrue(alpha < dev10)
        assertTrue(dev10 < stable)
        assertTrue(stable < nextMinor)
        assertNull(AppVersion.parse("not-a-version"))
        assertNull(AppVersion.parse("1.2.3-"))
    }

    @Test
    fun prereleasePreferenceDefaultsFalseAndPersists() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("playback_display_v1", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val first = AndroidTranslationPreferences(context)
        assertFalse(first.readIncludePrereleases())

        first.writeIncludePrereleases(true)
        assertTrue(AndroidTranslationPreferences(context).readIncludePrereleases())

        first.writeIncludePrereleases(false)
        assertFalse(AndroidTranslationPreferences(context).readIncludePrereleases())
    }

    @Test
    fun githubRepositoryFiltersPrereleasesChecksDigestCookiesRedirectsAndPackageArchive(): Unit = runBlocking {
        val apkBytes = "mock-apk-payload-v0.1.0-dev.10".toByteArray()
        val digestHex = sha256Hex(apkBytes)
        val apkUrl = server.url("/releases/download/v0.1.0-dev.10/flaretune-dev10.apk").toString()
        val releasesJson = """
            [
              {
                "tag_name": "v0.1.0-dev.11",
                "name": "Draft",
                "draft": true,
                "prerelease": true,
                "body": "draft",
                "html_url": "https://github.com/arctan303/FlareTune-Client/releases/tag/v0.1.0-dev.11",
                "assets": []
              },
              {
                "tag_name": "v0.1.0-dev.10",
                "name": "FlareTune v0.1.0-dev.10",
                "draft": false,
                "prerelease": true,
                "published_at": "2026-11-17T00:00:00Z",
                "body": "Google login + update checker",
                "html_url": "https://github.com/arctan303/FlareTune-Client/releases/tag/v0.1.0-dev.10",
                "assets": [
                  {
                    "name": "checksums.txt",
                    "browser_download_url": "https://localhost/checksums.txt",
                    "size": 64
                  },
                  {
                    "name": "app-release.apk",
                    "browser_download_url": "$apkUrl",
                    "size": ${apkBytes.size},
                    "digest": "sha256:$digestHex"
                  }
                ]
              },
              {
                "tag_name": "v0.0.9",
                "name": "Stable 0.0.9",
                "draft": false,
                "prerelease": false,
                "published_at": "2026-11-01T00:00:00Z",
                "body": "Older stable",
                "html_url": "https://github.com/arctan303/FlareTune-Client/releases/tag/v0.0.9",
                "assets": []
              }
            ]
        """.trimIndent()

        var validateArchiveResult = true
        val repo = GitHubAppUpdateRepository(
            cacheDir = tempFolder.newFolder("cache"),
            expectedPackageName = "com.arcinks.flaretune",
            currentVersionCode = 9L,
            baseClient = baseClient,
            releasesEndpoint = server.url("/repos/arctan303/FlareTune-Client/releases").toString().toHttpUrl(),
            extraTrustedHosts = setOf("localhost", "127.0.0.1"),
            archiveValidator = { validateArchiveResult },
            ioDispatcher = Dispatchers.IO,
        )

        // Stable-only mode compares against v0.0.9 and finds current 0.1.0-dev.9 up to date.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Set-Cookie", "gh_sess=secret-cookie; Path=/")
                .setBody(releasesJson),
        )
        val stableCheck = repo.checkLatest(includePrereleases = false, currentVersion = "0.1.0-dev.9")
        assertTrue(stableCheck is AppUpdateCheckResult.UpToDate)
        server.takeRequest()

        // Pre-release mode discovers v0.1.0-dev.10 and ignores draft v0.1.0-dev.11.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Set-Cookie", "gh_sess=should-not-stick; Path=/")
                .setBody(releasesJson),
        )
        val preCheck = repo.checkLatest(includePrereleases = true, currentVersion = "0.1.0-dev.9")
        assertTrue(preCheck is AppUpdateCheckResult.Available)
        val release = (preCheck as AppUpdateCheckResult.Available).release
        assertEquals("0.1.0-dev.10", release.versionName)
        assertTrue(release.isPrerelease)
        assertEquals(digestHex, release.apkSha256)
        server.takeRequest()

        // Downloading APK must not send previously returned Set-Cookie and validates SHA-256 + archive.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", apkBytes.size.toString())
                .setBody(okio.Buffer().write(apkBytes)),
        )
        val progressEvents = mutableListOf<Pair<Long, Long>>()
        val file = repo.downloadApk(release) { downloaded, total ->
            progressEvents += downloaded to total
        }
        val downloadRequest = server.takeRequest()
        assertNull(downloadRequest.getHeader("Cookie"))
        assertTrue(file.exists())
        assertEquals(apkBytes.size.toLong(), file.length())
        assertTrue(progressEvents.isNotEmpty())

        // Untrusted redirect domain must be blocked.
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://evil.example.org/payload.apk"),
        )
        try {
            repo.downloadApk(release) { _, _ -> }
            fail("Expected untrusted redirect host rejection")
        } catch (failure: AppUpdateFailure) {
            assertEquals(AppUpdateError.REPOSITORY_UNAVAILABLE, failure.error)
        }
        server.takeRequest()

        // SHA-256 mismatch must fail with INTEGRITY_MISMATCH and leave no final APK.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("tampered-apk"),
        )
        try {
            repo.downloadApk(release) { _, _ -> }
            fail("Expected SHA-256 mismatch failure")
        } catch (failure: AppUpdateFailure) {
            assertEquals(AppUpdateError.INTEGRITY_MISMATCH, failure.error)
        }
        server.takeRequest()

        // Invalid package archive validator rejection must fail with INVALID_PACKAGE.
        validateArchiveResult = false
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(apkBytes)),
        )
        try {
            repo.downloadApk(release) { _, _ -> }
            fail("Expected invalid package rejection")
        } catch (failure: AppUpdateFailure) {
            assertEquals(AppUpdateError.INVALID_PACKAGE, failure.error)
        }
        server.takeRequest()

        // 404 from private/unpublished repo yields NoReleaseFound.
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(
            AppUpdateCheckResult.NoReleaseFound,
            repo.checkLatest(includePrereleases = false, currentVersion = "0.1.0-dev.9"),
        )
        server.takeRequest()
        Unit
    }

    @Test
    fun githubRepositoryHandlesNullMetadataUnknownSizeProgressAndCancelRetryRace(): Unit = runBlocking {
        val apkBytes = ByteArray(48 * 1024) { (it % 127).toByte() }
        val digestHex = sha256Hex(apkBytes)
        val apkUrl = server.url("/releases/download/v0.2.0/app-release.apk").toString()
        val nullFieldsJson = """
            [
              {
                "tag_name": "v0.2.0",
                "name": null,
                "draft": false,
                "prerelease": false,
                "published_at": null,
                "body": null,
                "html_url": "https://github.com/arctan303/FlareTune-Client/releases/tag/v0.2.0",
                "assets": [
                  {
                    "name": "app-release.apk",
                    "browser_download_url": "$apkUrl",
                    "size": 0,
                    "digest": null
                  }
                ]
              }
            ]
        """.trimIndent()

        val repo = GitHubAppUpdateRepository(
            cacheDir = tempFolder.newFolder("race-cache"),
            expectedPackageName = "com.arcinks.flaretune",
            currentVersionCode = 9L,
            baseClient = baseClient,
            releasesEndpoint = server.url("/repos/arctan303/FlareTune-Client/releases").toString().toHttpUrl(),
            extraTrustedHosts = setOf("localhost", "127.0.0.1"),
            archiveValidator = { file -> file.exists() && file.length() == apkBytes.size.toLong() },
            ioDispatcher = Dispatchers.IO,
        )

        // Null name/body/digest fields must not turn into literal "null" strings.
        server.enqueue(MockResponse().setResponseCode(200).setBody(nullFieldsJson))
        val available = repo.checkLatest(includePrereleases = false, currentVersion = "0.1.0-dev.9") as AppUpdateCheckResult.Available
        assertEquals("v0.2.0", available.release.releaseName)
        assertEquals("", available.release.body)
        assertEquals("", available.release.publishedAt)
        assertNull(available.release.apkSha256)
        assertEquals(0L, available.release.apkSizeBytes)
        server.takeRequest()

        // Unknown-size download (no Content-Length) must report totalBytes = -1L (indeterminate), not downloaded bytes.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(apkBytes))
                .removeHeader("Content-Length"),
        )
        val totals = mutableListOf<Long>()
        val downloadedUnknownSize = repo.downloadApk(available.release) { _, total -> totals += total }
        assertTrue(downloadedUnknownSize.exists())
        assertTrue(totals.isNotEmpty())
        assertTrue(totals.all { it == -1L })
        server.takeRequest()

        // Cancel-and-immediate-retry race must cancel the first HTTP call cleanly and keep retry's file intact.
        val verifiedRelease = available.release.copy(
            apkSizeBytes = apkBytes.size.toLong(),
            apkSha256 = digestHex,
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", apkBytes.size.toString())
                .throttleBody(4096, 250, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setBody(okio.Buffer().write(apkBytes)),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", apkBytes.size.toString())
                .setBody(okio.Buffer().write(apkBytes)),
        )

        val firstStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val firstAttempt = launch(Dispatchers.IO) {
            try {
                repo.downloadApk(verifiedRelease) { _, _ ->
                    firstStarted.complete(Unit)
                }
            } catch (_: CancellationException) {
            }
        }
        firstStarted.await()
        firstAttempt.cancel()
        val secondAttempt = async(Dispatchers.IO) {
            repo.downloadApk(verifiedRelease) { _, _ -> }
        }
        firstAttempt.join()
        val finalFile = secondAttempt.await()
        assertTrue(finalFile.exists())
        assertEquals(apkBytes.size.toLong(), finalFile.length())
        assertEquals(digestHex, sha256Hex(finalFile.readBytes()))
        Unit
    }

    @Test
    fun viewModelNeverAutoChecksOnStartupAndHandlesPermissionResumeAndCancel() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("playback_display_v1", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = AndroidTranslationPreferences(context)
        val apkFile = tempFolder.newFile("flaretune-next.apk").apply { writeText("apk-content") }

        val release = AppUpdateRelease(
            tagName = "v0.1.0-dev.10",
            versionName = "0.1.0-dev.10",
            releaseName = "FlareTune 0.1.0-dev.10",
            body = "Release notes",
            publishedAt = "2026-11-17T00:00:00Z",
            isPrerelease = true,
            htmlUrl = "https://github.com/arctan303/FlareTune-Client/releases/tag/v0.1.0-dev.10",
            apkDownloadUrl = "https://github.com/arctan303/FlareTune-Client/releases/download/v0.1.0-dev.10/app.apk",
            apkFileName = "app.apk",
            apkSizeBytes = 1024L,
            apkSha256 = null,
        )

        var checkCalls = 0
        var lastIncludePrereleases: Boolean? = null
        var holdDownload = false
        val fakeRepo = object : AppUpdateRepository {
            override suspend fun checkLatest(includePrereleases: Boolean, currentVersion: String): AppUpdateCheckResult {
                checkCalls++
                lastIncludePrereleases = includePrereleases
                return if (includePrereleases) {
                    AppUpdateCheckResult.Available(release)
                } else {
                    AppUpdateCheckResult.UpToDate(null)
                }
            }

            override suspend fun downloadApk(
                release: AppUpdateRelease,
                onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
            ): File {
                onProgress(512L, 1024L)
                while (holdDownload) {
                    delay(50)
                }
                onProgress(1024L, 1024L)
                return apkFile
            }
        }

        var canInstall = false
        var openedSettingsCalls = 0
        var installedPaths = mutableListOf<String>()
        val fakeInstaller = object : AppUpdateInstaller {
            override fun canRequestPackageInstalls(): Boolean = canInstall
            override fun openUnknownSourcesSettings(): Boolean {
                openedSettingsCalls++
                return true
            }
            override fun installApk(apkFile: File): Boolean {
                installedPaths += apkFile.absolutePath
                return true
            }
        }

        val connections = FakeUpdateConnections().apply {
            restorableConnection = createActiveConnection("https://music.example.com", "alice")
        }
        val model = ClientViewModel(
            connections = connections,
            player = FakeUpdatePlayback(),
            ioDispatcher = dispatcher,
            translationPreferences = prefs,
            savedStateHandle = SavedStateHandle(),
            appUpdater = fakeRepo,
            appUpdateInstaller = fakeInstaller,
            appVersionName = "0.1.0-dev.9",
        )

        try {
            advanceUntilIdle()
            assertEquals(ClientScreen.LIBRARY, model.state.value.screen)
            // Strict non-intrusive guarantee: entering app must never automatically check GitHub.
            assertEquals(0, checkCalls)
            assertFalse(model.state.value.appUpdate.includePrereleases)
            assertFalse(model.state.value.appUpdate.dialogVisible)

            // Stable-only manual check returns UpToDate without opening dialog.
            model.checkForAppUpdate()
            advanceUntilIdle()
            assertEquals(1, checkCalls)
            assertEquals(false, lastIncludePrereleases)
            assertEquals(AppUpdateStatus.UP_TO_DATE, model.state.value.appUpdate.status)
            assertFalse(model.state.value.appUpdate.dialogVisible)

            // Enable pre-release updates and verify persistence + Available dialog.
            model.setIncludePrereleaseUpdates(true)
            advanceUntilIdle()
            assertTrue(model.state.value.appUpdate.includePrereleases)
            assertTrue(prefs.readIncludePrereleases())

            model.checkForAppUpdate()
            advanceUntilIdle()
            assertEquals(2, checkCalls)
            assertEquals(true, lastIncludePrereleases)
            assertEquals(AppUpdateStatus.AVAILABLE, model.state.value.appUpdate.status)
            assertTrue(model.state.value.appUpdate.dialogVisible)
            assertEquals("0.1.0-dev.10", model.state.value.appUpdate.availableRelease?.versionName)

            // Dismiss then reopen dialog preserves discovered release.
            dismissAppUpdateDownloadAndReopenCheck(model)

            // Test cancelling in-flight download returns to AVAILABLE cleanly.
            holdDownload = true
            model.startAppUpdateDownload()
            kotlinx.coroutines.yield()
            assertEquals(AppUpdateStatus.DOWNLOADING, model.state.value.appUpdate.status)
            model.cancelAppUpdateDownload()
            advanceUntilIdle()
            assertEquals(AppUpdateStatus.AVAILABLE, model.state.value.appUpdate.status)

            // Complete download with missing install permission -> opens settings, then resumes on foreground.
            holdDownload = false
            canInstall = false
            model.startAppUpdateDownload()
            advanceUntilIdle()
            assertEquals(AppUpdateStatus.INSTALL_PERMISSION_REQUIRED, model.state.value.appUpdate.status)
            assertEquals(1, openedSettingsCalls)
            assertTrue(installedPaths.isEmpty())

            // Returning from system settings without granting keeps INSTALL_PERMISSION_REQUIRED.
            model.onForegroundResume()
            assertEquals(AppUpdateStatus.INSTALL_PERMISSION_REQUIRED, model.state.value.appUpdate.status)
            assertTrue(installedPaths.isEmpty())

            // Granting permission and returning to foreground auto-launches package installer.
            canInstall = true
            model.onForegroundResume()
            assertEquals(AppUpdateStatus.READY_TO_INSTALL, model.state.value.appUpdate.status)
            assertEquals(listOf(apkFile.absolutePath), installedPaths)
        } finally {
            model.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun dismissAppUpdateDownloadAndReopenCheck(model: ClientViewModel) {
        model.dismissAppUpdateDialog()
        assertFalse(model.state.value.appUpdate.dialogVisible)
        model.openAppUpdateDialog()
        assertTrue(model.state.value.appUpdate.dialogVisible)
    }

    private fun createActiveConnection(origin: String, username: String): ActiveConnection {
        val normalized = InstanceAddress.parse(origin)
        val network = SessionNetwork(normalized)
        val api = object : AccountApi {
            override fun songs(query: String, offset: Int) = SongPage(emptyList(), 0, false)
            override fun favorites() = emptyList<Song>()
            override fun albums(offset: Int) = AlbumPage(emptyList(), 0, false)
            override fun album(id: String) = AlbumDetail(Album(id, "A", "B"), emptyList())
            override fun changePassword(current: String, replacement: String) {}
            override fun logout() {}
        }
        return ActiveConnection(network, api, AuthSession(Account("acc-$username", username, username), "csrf", false))
    }

    private class FakeUpdateConnections : Connections {
        override var current: ActiveConnection? = null
        override val lastOrigin: String = ""
        override val invalidations = MutableSharedFlow<ClientNotice>()
        var restorableConnection: ActiveConnection? = null

        override suspend fun restore(): ActiveConnection? = restorableConnection?.also { current = it }
        override suspend fun checkInstance(address: String): InstanceProbeResult =
            InstanceProbeResult(InstanceAddress.parse(address).origin, googleEnabled = false, googleNativeEnabled = false)
        override suspend fun login(address: String, username: String, password: String): ActiveConnection =
            throw ApiFailure(Failure.INVALID_LOGIN)
        override suspend fun changePassword(current: String, replacement: String) {}
        override suspend fun logout() { current = null }
    }

    private class FakeUpdatePlayback : PlaybackGateway {
        override val state: StateFlow<PlaybackSnapshot> = MutableStateFlow(PlaybackSnapshot())
        override val imageLoader: ImageLoader? = null
        override suspend fun connect(connection: ActiveConnection) {}
        override fun play(songs: List<Song>, index: Int) {}
        override fun setVolume(volume: Float) {}
        override fun togglePlay() {}
        override fun next() {}
        override fun previous() {}
        override fun seekTo(positionMs: Long) {}
        override fun toggleShuffle() {}
        override fun cycleRepeat() {}
        override fun select(index: Int) {}
        override fun retry() {}
        override fun stopAndClear() {}
        override fun release() {}
    }
}
