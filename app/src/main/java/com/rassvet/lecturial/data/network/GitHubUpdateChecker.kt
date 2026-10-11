package com.rassvet.lecturial.data.network

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

class GitHubUpdateChecker(
    private val apiUrl: String = LATEST_RELEASE_API,
    private val client: OkHttpClient = OkHttpClient(),
    private val releaseDownloadPrefix: String = RELEASE_DOWNLOAD_PREFIX,
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
        if (compareVersions(latestVersion, currentVersion) > 0) {
            val apk = findApkAsset(release.optJSONArray("assets") ?: JSONArray())
                ?: throw IOException("В релизе $tag не найден APK-файл")
            AppUpdateState.Available(
                version = latestVersion,
                releaseUrl = releaseUrl,
                apkUrl = apk.second,
                apkName = apk.first,
            )
        } else {
            AppUpdateState.UpToDate(latestVersion)
        }
    }

    suspend fun downloadApk(
        update: AppUpdateState.Available,
        destination: File,
        onProgress: (Int?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (!isTrustedApkUrl(update.apkUrl)) {
            throw IOException("GitHub вернул недоверенную ссылку на APK")
        }
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.part")
        temporary.delete()
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

    private fun findApkAsset(assets: JSONArray): Pair<String, String>? {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name").trim()
            val url = asset.optString("browser_download_url").trim()
            if (name.endsWith(".apk", ignoreCase = true) && isTrustedApkUrl(url)) {
                return name to url
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

private fun File.hasZipHeader(): Boolean = inputStream().use { input ->
    input.read() == 'P'.code && input.read() == 'K'.code && input.read() == 3 && input.read() == 4
}

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