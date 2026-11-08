package com.rassvet.lecturial.data.network

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.rassvet.lecturial.model.AppUpdateState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class GitHubUpdateChecker(
    private val apiUrl: String = LATEST_RELEASE_API,
    private val client: OkHttpClient = OkHttpClient(),
    private val releaseDownloadPrefix: String = RELEASE_DOWNLOAD_PREFIX,
    /** The installed release APK; with it, a release rebuilt under the same number is offered too. */
    private val installedApk: File? = null,
) {
    suspend fun check(currentVersion: String): AppUpdateState = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Lecturial-2/$currentVersion (Android)")
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (failure: IOException) {
            throw IOException("Не удалось подключиться к GitHub. Проверьте интернет", failure)
        }
        val body = response.use {
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    403, 429 -> "Лимит запросов GitHub исчерпан. Повторите проверку позже"
                    404 -> "На GitHub пока нет опубликованных стабильных релизов"
                    else -> "GitHub временно недоступен (HTTP ${response.code})"
                }
                throw IOException(message)
            }
            response.body.string()
        }
        val release = runCatching { JSONObject(body) }
            .getOrElse { throw IOException("GitHub вернул некорректный ответ", it) }
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            throw IOException("GitHub не вернул стабильный релиз")
        }
        val tag = release.optString("tag_name").trim()
        val releaseUrl = release.optString("html_url").trim()
        if (tag.isEmpty() || !releaseUrl.startsWith(RELEASE_URL_PREFIX)) {
            throw IOException("В ответе GitHub нет данных о релизе")
        }
        val latestVersion = normalizedVersion(tag)
        val apk = findApkAsset(release.optJSONArray("assets") ?: JSONArray())
        val comparison = compareVersions(latestVersion, currentVersion)
        // Same number, another file: the release was rebuilt in place. GitHub gives each file's SHA-256.
        val rebuilt = comparison == 0 && installedApk != null && apk?.digest != null &&
            apk.digest != runCatching { sha256Of(installedApk) }.getOrNull()
        if (comparison > 0 || rebuilt) {
            apk ?: throw IOException("В релизе $tag не найден APK-файл")
            val minVersion = requiredMinVersion(release.optString("body"))
            AppUpdateState.Available(
                version = latestVersion,
                releaseUrl = releaseUrl,
                apkUrl = apk.url,
                apkName = apk.name,
                required = minVersion != null && compareVersions(currentVersion, minVersion) < 0,
            )
        } else {
            AppUpdateState.UpToDate(latestVersion)
        }
    }

    private class ApkAsset(val name: String, val url: String, val digest: String?)

    suspend fun downloadApk(
        update: AppUpdateState.Available,
        destination: File,
        onProgress: (Int?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (!isTrustedApkUrl(update.apkUrl)) {
            throw IOException("GitHub вернул недоверенную ссылку на APK")
        }
        destination.parentFile?.mkdirs()
        // Only the newest download is worth keeping: earlier ones are installed or abandoned.
        destination.parentFile?.listFiles()?.forEach { it.delete() }
        val temporary = File(destination.parentFile, "${destination.name}.part")
        val request = Request.Builder()
            .url(update.apkUrl)
            .header("Accept", APK_MIME_TYPE)
            .header("User-Agent", "Lecturial-2/${update.version} (Android)")
            .get()
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (failure: IOException) {
            throw IOException("Не удалось скачать APK. Проверьте интернет", failure)
        }
        try {
            response.use {
                if (!response.isSuccessful) {
                    throw IOException("Не удалось скачать APK (HTTP ${response.code})")
                }
                val contentType = response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
                if (contentType != null && contentType !in APK_CONTENT_TYPES) {
                    throw IOException("GitHub вернул файл неподдерживаемого типа")
                }
                val expectedBytes = response.body.contentLength().takeIf { length -> length >= 0L }
                if (expectedBytes != null && expectedBytes > MAX_APK_BYTES) {
                    throw IOException("APK слишком большой")
                }
                onProgress(expectedBytes?.let { 0 })
                response.body.byteStream().use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        var lastProgress = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            if (downloaded > MAX_APK_BYTES) throw IOException("APK слишком большой")
                            output.write(buffer, 0, count)
                            val progress = expectedBytes
                                ?.takeIf { it > 0L }
                                ?.let { ((downloaded * 100L) / it).coerceIn(0L, 100L).toInt() }
                            if (progress != lastProgress) {
                                onProgress(progress)
                                lastProgress = progress ?: -1
                            }
                        }
                    }
                }
                if (temporary.length() < 4L || !temporary.hasZipHeader()) {
                    throw IOException("Скачанный файл не является корректным APK")
                }
                if (expectedBytes != null && temporary.length() != expectedBytes) {
                    throw IOException("APK скачан не полностью")
                }
                destination.delete()
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                onProgress(100)
                destination
            }
        } catch (failure: Throwable) {
            temporary.delete()
            throw failure
        }
    }

    private fun findApkAsset(assets: JSONArray): ApkAsset? {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name").trim()
            val url = asset.optString("browser_download_url").trim()
            if (name.endsWith(".apk", ignoreCase = true) && isTrustedApkUrl(url)) {
                val digest = asset.optString("digest").trim().lowercase().removePrefix("sha256:")
                return ApkAsset(name, url, digest.takeIf { it.matches(Regex("[0-9a-f]{64}")) })
            }
        }
        return null
    }

    private fun isTrustedApkUrl(url: String): Boolean =
        url.startsWith(releaseDownloadPrefix) &&
            url.substringBefore('?').endsWith(".apk", ignoreCase = true)

    private companion object {
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/neckmnull/Lecturial-2/releases/latest"
        const val RELEASE_URL_PREFIX =
            "https://github.com/neckmnull/Lecturial-2/releases/"
        const val RELEASE_DOWNLOAD_PREFIX =
            "https://github.com/neckmnull/Lecturial-2/releases/download/"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val MAX_APK_BYTES = 250L * 1024L * 1024L
        val APK_CONTENT_TYPES = setOf(APK_MIME_TYPE, "application/octet-stream", "application/zip")
    }
}

private fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun File.hasZipHeader(): Boolean = inputStream().use { input ->
    input.read() == 'P'.code && input.read() == 'K'.code && input.read() == 3 && input.read() == 4
}

/**
 * Whether [apk] is this app signed with the installed copy's key, i.e. a real update and not a
 * substitute. Checked before the installer opens, so a foreign APK never reaches the user.
 */
@Suppress("DEPRECATION")
internal fun isSameAppUpdate(context: Context, apk: File): Boolean {
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val packageManager = context.packageManager
    val offered = packageManager.getPackageArchiveInfo(apk.path, flags) ?: return false
    if (offered.packageName != context.packageName) return false
    val installed = packageManager.getPackageInfo(context.packageName, flags)
    return acceptsUpdate(installed.signers(withHistory = false), offered.signers(withHistory = true))
}

/** Every key the installed app is signed with must be among the update's signers or, after a key rotation, their history. */
internal fun acceptsUpdate(installed: List<String>, offered: List<String>): Boolean =
    installed.isNotEmpty() && offered.containsAll(installed)

@Suppress("DEPRECATION")
private fun PackageInfo.signers(withHistory: Boolean): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return signatures.orEmpty().map { it.toCharsString() }
    val info = signingInfo ?: return emptyList()
    val certificates = if (withHistory && !info.hasMultipleSigners()) info.signingCertificateHistory else info.apkContentsSigners
    return certificates.orEmpty().map { it.toCharsString() }
}

/**
 * The oldest version still allowed to run, if the release notes set one with a hidden line
 * `<!-- lecturial-min-version: 1.11.0 -->`. Anything older must update before it can be used.
 */
internal fun requiredMinVersion(releaseNotes: String): String? =
    Regex("""lecturial-min-version:\s*v?(\d+(?:\.\d+)*)""").find(releaseNotes)?.groupValues?.get(1)

internal fun compareVersions(first: String, second: String): Int {
    val firstParts = versionParts(first)
    val secondParts = versionParts(second)
    repeat(maxOf(firstParts.size, secondParts.size)) { index ->
        val comparison = (firstParts.getOrNull(index) ?: 0)
            .compareTo(secondParts.getOrNull(index) ?: 0)
        if (comparison != 0) return comparison
    }
    return 0
}

private fun normalizedVersion(version: String): String = version
    .trim()
    .removePrefix("v")
    .substringBefore('-')
    .substringBefore('+')
    .also { versionParts(it) }

private fun versionParts(version: String): List<Int> {
    val normalized = version.trim().removePrefix("v").substringBefore('-').substringBefore('+')
    if (!normalized.matches(Regex("[0-9]+(?:\\.[0-9]+)*"))) {
        throw IOException("Некорректный номер версии: $version")
    }
    return normalized.split('.').map { part ->
        part.toIntOrNull() ?: throw IOException("Некорректный номер версии: $version")
    }
}