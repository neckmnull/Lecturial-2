package com.rassvet.lecturial.data.network

import com.rassvet.lecturial.model.AppUpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class GitHubUpdateChecker(
    private val apiUrl: String = LATEST_RELEASE_API,
    private val client: OkHttpClient = OkHttpClient(),
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
            AppUpdateState.Available(latestVersion, releaseUrl)
        } else {
            AppUpdateState.UpToDate(latestVersion)
        }
    }

    private companion object {
        const val LATEST_RELEASE_API =
            "https://api.github.com/repos/neckmnull/Lecturial-2/releases/latest"
        const val RELEASE_URL_PREFIX =
            "https://github.com/neckmnull/Lecturial-2/releases/"
    }
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