package com.arcinks.flaretune.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<String> = emptyList(),
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1
        if (other.prerelease.isEmpty()) return -1
        val count = minOf(prerelease.size, other.prerelease.size)
        for (i in 0 until count) {
            val left = prerelease[i]
            val right = other.prerelease[i]
            if (left == right) continue
            val leftNum = left.toLongOrNull()
            val rightNum = right.toLongOrNull()
            return when {
                leftNum != null && rightNum != null -> leftNum.compareTo(rightNum)
                leftNum != null -> -1 // Numeric identifiers always have lower precedence than non-numeric.
                rightNum != null -> 1
                else -> left.compareTo(right)
            }
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val CORE_PATTERN = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?$")

        fun parse(raw: String): AppVersion? {
            val cleaned = raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('+')
                .trim()
            if (cleaned.isEmpty()) return null
            val dashIndex = cleaned.indexOf('-')
            val corePart = if (dashIndex >= 0) cleaned.substring(0, dashIndex) else cleaned
            val prePart = if (dashIndex >= 0) cleaned.substring(dashIndex + 1) else ""
            if (dashIndex >= 0 && prePart.isEmpty()) return null
            val match = CORE_PATTERN.matchEntire(corePart) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val prerelease = if (prePart.isEmpty()) {
                emptyList()
            } else {
                val segments = prePart.split('.')
                if (segments.any { it.isEmpty() }) return null
                segments
            }
            return AppVersion(major, minor, patch, prerelease)
        }
    }
}

data class AppUpdateRelease(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val body: String,
    val publishedAt: String,
    val isPrerelease: Boolean,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val apkSha256: String?,
)

enum class AppUpdateError {
    NETWORK,
    REPOSITORY_UNAVAILABLE,
    NO_APK_ASSET,
    INTEGRITY_MISMATCH,
    INVALID_PACKAGE,
    INSTALL_FAILED,
    STORAGE,
}

class AppUpdateFailure(val error: AppUpdateError) : IOException(error.name)

sealed class AppUpdateCheckResult {
    data class Available(val release: AppUpdateRelease) : AppUpdateCheckResult()
    data class UpToDate(val latestRelease: AppUpdateRelease?) : AppUpdateCheckResult()
    data object NoReleaseFound : AppUpdateCheckResult()
    data class Failed(val error: AppUpdateError) : AppUpdateCheckResult()
}

interface AppUpdateRepository {
    suspend fun checkLatest(includePrereleases: Boolean, currentVersion: String): AppUpdateCheckResult
    suspend fun downloadApk(
        release: AppUpdateRelease,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File
}

interface AppUpdateInstaller {
    fun canRequestPackageInstalls(): Boolean
    fun openUnknownSourcesSettings(): Boolean
    fun installApk(apkFile: File): Boolean
}

class GitHubAppUpdateRepository(
    private val cacheDir: File,
    private val expectedPackageName: String,
    private val currentVersionCode: Long,
    baseClient: OkHttpClient = OkHttpClient(),
    private val releasesEndpoint: HttpUrl = DEFAULT_RELEASES_ENDPOINT.toHttpUrlOrNull()!!,
    private val extraTrustedHosts: Set<String> = emptySet(),
    private val archiveValidator: (File) -> Boolean = { true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppUpdateRepository {

    // Never share FlareTune instance cookies, redirects, or auth headers with GitHub requests.
    private val apiClient: OkHttpClient = baseClient.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val downloadClient: OkHttpClient = apiClient.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    // Serialize APK downloads so cancel-and-retry attempts never race on the updates cache directory.
    private val downloadMutex = Mutex()

    override suspend fun checkLatest(
        includePrereleases: Boolean,
        currentVersion: String,
    ): AppUpdateCheckResult = withContext(ioDispatcher) {
        val currentParsed = AppVersion.parse(currentVersion)
            ?: return@withContext AppUpdateCheckResult.Failed(AppUpdateError.INVALID_PACKAGE)
        val request = Request.Builder()
            .url(releasesEndpoint)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FlareTune-Android/${currentVersion.trim()}")
            .get()
            .build()
        val call = apiClient.newCall(request)
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (response.code == 404) {
                    return@withContext AppUpdateCheckResult.NoReleaseFound
                }
                if (response.code == 403 || response.code == 429) {
                    return@withContext AppUpdateCheckResult.Failed(AppUpdateError.REPOSITORY_UNAVAILABLE)
                }
                if (!response.isSuccessful) {
                    return@withContext AppUpdateCheckResult.Failed(AppUpdateError.REPOSITORY_UNAVAILABLE)
                }
                val source = response.body?.source()
                    ?: return@withContext AppUpdateCheckResult.Failed(AppUpdateError.REPOSITORY_UNAVAILABLE)
                source.request(MAX_API_JSON_BYTES + 1)
                if (source.buffer.size > MAX_API_JSON_BYTES) {
                    return@withContext AppUpdateCheckResult.Failed(AppUpdateError.REPOSITORY_UNAVAILABLE)
                }
                val array = try {
                    JSONArray(source.buffer.readUtf8())
                } catch (_: Exception) {
                    return@withContext AppUpdateCheckResult.Failed(AppUpdateError.REPOSITORY_UNAVAILABLE)
                }
                val candidates = parseReleases(array, includePrereleases)
                if (candidates.isEmpty()) {
                    return@withContext AppUpdateCheckResult.NoReleaseFound
                }
                val best = candidates.maxByOrNull { it.first } ?: return@withContext AppUpdateCheckResult.NoReleaseFound
                if (best.first > currentParsed) {
                    AppUpdateCheckResult.Available(best.second)
                } else {
                    AppUpdateCheckResult.UpToDate(best.second)
                }
            }
        } catch (_: IOException) {
            coroutineContext.ensureActive()
            AppUpdateCheckResult.Failed(AppUpdateError.NETWORK)
        } finally {
            cancelHandle?.dispose()
        }
    }

    override suspend fun downloadApk(
        release: AppUpdateRelease,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(ioDispatcher) {
        val initialUrl = release.apkDownloadUrl?.toHttpUrlOrNull()
            ?.takeIf(::isTrustedDownloadUrl)
            ?: throw AppUpdateFailure(AppUpdateError.NO_APK_ASSET)
        downloadMutex.withLock {
            coroutineContext.ensureActive()
            val updatesDir = File(cacheDir, "updates")
            if (!updatesDir.exists() && !updatesDir.mkdirs()) {
                throw AppUpdateFailure(AppUpdateError.STORAGE)
            }
            // Clean stale update installers before starting a fresh download.
            updatesDir.listFiles()?.forEach { old -> runCatching { old.delete() } }

            val safeFileName = sanitizeApkFileName(release.apkFileName ?: "${release.versionName}.apk")
            val partFile = File.createTempFile("download-", ".part", updatesDir)
            val finalFile = File(updatesDir, safeFileName)
            var currentUrl = initialUrl
            val activeCall = AtomicReference<Call?>(null)
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
                if (cause != null) activeCall.get()?.cancel()
            }

            try {
                fun executeTrusted(url: HttpUrl): okhttp3.Response {
                    val call = newTrustedGetCall(url)
                    activeCall.set(call)
                    return call.execute()
                }

                var response = executeTrusted(currentUrl)
                var redirects = 0
                while (response.code in setOf(301, 302, 303, 307, 308)) {
                    val location = response.header("Location")
                    response.close()
                    if (redirects >= MAX_DOWNLOAD_REDIRECTS || location.isNullOrBlank()) {
                        throw AppUpdateFailure(AppUpdateError.REPOSITORY_UNAVAILABLE)
                    }
                    val nextUrl = currentUrl.resolve(location)
                        ?.takeIf(::isTrustedDownloadUrl)
                        ?: throw AppUpdateFailure(AppUpdateError.REPOSITORY_UNAVAILABLE)
                    currentUrl = nextUrl
                    redirects++
                    response = executeTrusted(currentUrl)
                }

                response.use { finalResponse ->
                    if (!finalResponse.isSuccessful) {
                        throw AppUpdateFailure(AppUpdateError.REPOSITORY_UNAVAILABLE)
                    }
                    val body = finalResponse.body ?: throw AppUpdateFailure(AppUpdateError.REPOSITORY_UNAVAILABLE)
                    val declaredLength = body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes
                    if (declaredLength > MAX_APK_BYTES) {
                        throw AppUpdateFailure(AppUpdateError.INTEGRITY_MISMATCH)
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var downloaded = 0L
                    var lastProgressAt = 0L
                    val buffer = ByteArray(16 * 1024)
                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                downloaded += read
                                if (downloaded > MAX_APK_BYTES) {
                                    throw AppUpdateFailure(AppUpdateError.INTEGRITY_MISMATCH)
                                }
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                                val now = System.currentTimeMillis()
                                if (now - lastProgressAt >= 120L || (declaredLength > 0 && downloaded >= declaredLength)) {
                                    lastProgressAt = now
                                    val progressTotal = if (declaredLength > 0L) declaredLength.coerceAtLeast(downloaded) else -1L
                                    onProgress(downloaded, progressTotal)
                                }
                            }
                            output.fd.sync()
                        }
                    }
                    val finalProgressTotal = if (declaredLength > 0L) declaredLength.coerceAtLeast(downloaded) else -1L
                    onProgress(downloaded, finalProgressTotal)
                    val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                    val expectedSha256 = release.apkSha256?.lowercase(Locale.ROOT)
                    if (expectedSha256 != null && actualSha256 != expectedSha256) {
                        throw AppUpdateFailure(AppUpdateError.INTEGRITY_MISMATCH)
                    }
                }

                if (!archiveValidator(partFile)) {
                    throw AppUpdateFailure(AppUpdateError.INVALID_PACKAGE)
                }
                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }
                finalFile
            } catch (error: AppUpdateFailure) {
                throw error
            } catch (error: IOException) {
                coroutineContext.ensureActive()
                throw AppUpdateFailure(AppUpdateError.NETWORK)
            } finally {
                activeCall.set(null)
                cancelHandle?.dispose()
                runCatching { if (partFile.exists()) partFile.delete() }
            }
        }
    }

    private fun newTrustedGetCall(url: HttpUrl): Call {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "FlareTune-Android/$currentVersionCode")
            .get()
            .build()
        return downloadClient.newCall(request)
    }

    private fun parseReleases(array: JSONArray, includePrereleases: Boolean): List<Pair<AppVersion, AppUpdateRelease>> {
        val results = mutableListOf<Pair<AppVersion, AppUpdateRelease>>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue
            val isPrerelease = obj.optBoolean("prerelease", false)
            if (isPrerelease && !includePrereleases) continue
            val tagName = obj.optNonNullString("tag_name")
            val parsedVersion = AppVersion.parse(tagName) ?: continue
            val htmlUrl = obj.optNonNullString("html_url").takeIf(::isTrustedPageUrlString)
                ?: DEFAULT_RELEASES_PAGE
            val asset = pickApkAsset(obj.optJSONArray("assets"))
            val cleanVersionName = tagName.removePrefix("v").removePrefix("V")
            val release = AppUpdateRelease(
                tagName = tagName,
                versionName = cleanVersionName,
                releaseName = obj.optNonNullString("name").ifBlank { tagName },
                body = obj.optNonNullString("body"),
                publishedAt = obj.optNonNullString("published_at"),
                isPrerelease = isPrerelease,
                htmlUrl = htmlUrl,
                apkDownloadUrl = asset?.downloadUrl,
                apkFileName = asset?.name,
                apkSizeBytes = asset?.sizeBytes ?: 0L,
                apkSha256 = asset?.sha256,
            )
            results += parsedVersion to release
        }
        return results
    }

    private data class ApkAsset(val name: String, val downloadUrl: String, val sizeBytes: Long, val sha256: String?)

    private fun pickApkAsset(assets: JSONArray?): ApkAsset? {
        if (assets == null) return null
        val candidates = mutableListOf<ApkAsset>()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optNonNullString("name")
            if (!name.lowercase(Locale.ROOT).endsWith(".apk")) continue
            if (name.contains("androidTest", ignoreCase = true)) continue
            val urlStr = asset.optNonNullString("browser_download_url")
            val parsedUrl = urlStr.toHttpUrlOrNull()?.takeIf(::isTrustedDownloadUrl) ?: continue
            val size = asset.optLong("size", 0L).coerceAtLeast(0L)
            val digestRaw = asset.optNonNullString("digest")
            val sha256 = digestRaw.takeIf { it.startsWith("sha256:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
            candidates += ApkAsset(name, parsedUrl.toString(), size, sha256)
        }
        return candidates.firstOrNull { !it.name.contains("unsigned", ignoreCase = true) }
            ?: candidates.firstOrNull()
    }

    private fun isTrustedDownloadUrl(url: HttpUrl): Boolean {
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        val host = url.host.lowercase(Locale.ROOT)
        return host == "github.com" ||
            host.endsWith(".github.com") ||
            host == "githubusercontent.com" ||
            host.endsWith(".githubusercontent.com") ||
            host in extraTrustedHosts
    }

    private fun isTrustedPageUrlString(raw: String): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        val host = url.host.lowercase(Locale.ROOT)
        return host == "github.com" || host in extraTrustedHosts
    }

    private fun sanitizeApkFileName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
            .ifBlank { "flaretune-update.apk" }
        return if (base.lowercase(Locale.ROOT).endsWith(".apk")) base else "$base.apk"
    }

    private fun JSONObject.optNonNullString(key: String): String =
        if (isNull(key)) "" else optString(key, "").trim()

    companion object {
        const val DEFAULT_RELEASES_ENDPOINT = "https://api.github.com/repos/arctan303/FlareTune-Client/releases?per_page=15"
        const val DEFAULT_RELEASES_PAGE = "https://github.com/arctan303/FlareTune-Client/releases"
        private const val MAX_API_JSON_BYTES = 2L * 1024 * 1024
        private const val MAX_APK_BYTES = 250L * 1024 * 1024
        private const val MAX_DOWNLOAD_REDIRECTS = 4

        fun androidArchiveValidator(context: Context, expectedPackageName: String, minVersionCode: Long): (File) -> Boolean = { file ->
            runCatching {
                val pm = context.packageManager
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(file.absolutePath, 0)
                }
                info != null &&
                    info.packageName == expectedPackageName &&
                    PackageInfoCompat.getLongVersionCode(info) >= minVersionCode
            }.getOrDefault(false)
        }
    }
}

class AndroidAppUpdateInstaller(private val context: Context) : AppUpdateInstaller {
    override fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    override fun openUnknownSourcesSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    override fun installApk(apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
