package com.rassvet.lecturial.data.network

import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.model.displayAddress
import com.rassvet.lecturial.model.displayComment
import com.rassvet.lecturial.model.displayEventType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

interface ScheduleRemoteDataSource {
    suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek
    suspend fun fetchGroups(query: String): List<String> = emptyList()
}

class SiriusScheduleDataSource(
    private val baseUrl: String = "https://schedule.siriusuniversity.ru/",
    client: OkHttpClient? = null,
) : ScheduleRemoteDataSource {
    private val cookieJar = InMemoryCookieJar()
    private val http = (client?.newBuilder() ?: OkHttpClient.Builder())
        .cookieJar(cookieJar)
        .protocols(listOf(Protocol.HTTP_1_1))
        .dns { hostname ->
            Dns.SYSTEM.lookup(hostname).sortedBy { address ->
                if (address is Inet4Address) 0 else 1
            }
        }
        .fastFallback(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Lecturial/1.0 (Android; schedule client)")
                    .build(),
            )
        }
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionMutex = Mutex()

    override suspend fun fetchGroups(query: String): List<String> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            cookieJar.clear()
            val initialHtml = execute(Request.Builder().url(baseUrl).get().build())
            val normalizedQuery = query.trim()
            if (normalizedQuery.isEmpty()) {
                return@withContext LivewireGroupParser.parse(initialHtml)
                    .takeIf { it.isNotEmpty() }
                    ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
                    ?: throw IOException("Сайт не содержит список учебных групп")
            }
            val state = livewireStateFromHtml(initialHtml)
            LivewireGroupParser.parse(state.syncInput("search", normalizedQuery))
        }
    }

    override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            val normalizedTarget = weekStart.with(DayOfWeek.MONDAY)
            cookieJar.clear()
            val initialHtml = execute(Request.Builder().url(baseUrl).get().build())
            val state = livewireStateFromHtml(initialHtml)

        var data = state.call("set", JSONArray().put(group))
        val baseData = state.serverMemo["data"]?.jsonObject
            ?: throw IOException("Livewire не вернул данные расписания")
        val baseDate = runCatching { parseSiteDate(baseData["date"]?.jsonPrimitive?.content.orEmpty()) }
            .getOrElse { throw IOException("Livewire вернул некорректную дату", it) }
        val baseWeek = baseDate.with(DayOfWeek.MONDAY)
        val offset = ChronoUnit.WEEKS.between(baseWeek, normalizedTarget)
        if (offset !in -104L..104L) {
            throw IOException("Расписание доступно в пределах двух лет от текущей недели")
        }
        val method = if (offset >= 0) "addWeek" else "minusWeek"
        repeat(kotlin.math.abs(offset).toInt()) {
            data = state.call(method, JSONArray())
        }

            LivewireEventParser.parse(data, group, normalizedTarget)
        }
    }

    private fun execute(request: Request): String {
        val response = runCatching { http.newCall(request).execute() }
            .getOrElse { failure ->
                throw IOException("Не удалось связаться с сервером расписания. Проверьте интернет и повторите попытку.", failure)
            }
        response.use {
            val body = it.body.string()
            if (!it.isSuccessful) {
                throw IOException("Сервер расписания временно недоступен (HTTP ${it.code}). Повторите попытку позже.")
            }
            return body
        }
    }

    private inner class LivewireState(
        private val fingerprint: JsonObject,
        var serverMemo: JsonObject,
        private val csrfToken: String,
    ) {
        private var requestId = 0

        fun call(method: String, params: JSONArray): JSONObject {
            val paramValues = json.parseToJsonElement(params.toString()) as JsonArray
            sendUpdate(buildJsonObject {
                put("type", "callMethod")
                put("payload", buildJsonObject {
                    put("id", "lecturial-${requestId++}")
                    put("method", method)
                    put("params", paramValues)
                })
            })
            return JSONObject(serverMemo["data"].toString())
        }

        fun syncInput(name: String, value: String): String {
            val response = sendUpdate(buildJsonObject {
                put("type", "syncInput")
                put("payload", buildJsonObject {
                    put("id", "lecturial-${requestId++}")
                    put("name", name)
                    put("value", value)
                })
            })
            return response["effects"]?.jsonObject?.get("html")?.jsonPrimitive?.content
                ?: throw IOException("Livewire не вернул результаты поиска групп")
        }

        private fun sendUpdate(update: JsonObject): JsonObject {
            val payload = buildJsonObject {
                put("fingerprint", fingerprint)
                put("serverMemo", serverMemo)
                put("updates", buildJsonArray { add(update) })
            }
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/livewire/message/main-grid")
                .header("X-CSRF-TOKEN", csrfToken)
                .header("X-Livewire", "true")
                .header("Accept", "text/html, application/xhtml+xml")
                .header("Referer", baseUrl)
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            val response = runCatching { json.parseToJsonElement(execute(request)).jsonObject }
                .getOrElse { throw IOException("Некорректный ответ Livewire", it) }
            val memoPatch = response["serverMemo"] as? JsonObject
                ?: throw IOException("Livewire не вернул serverMemo")
            serverMemo = mergeMemo(serverMemo, memoPatch)
            return response
        }
    }

    private fun livewireStateFromHtml(html: String): LivewireState {
        val initial = Regex("wire:initial-data=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: throw IOException("Сайт не содержит начальное состояние Livewire")
        val token = Regex("window\\.livewire_token\\s*=\\s*'([^']+)'").find(html)?.groupValues?.get(1)
            ?: throw IOException("Сайт не содержит CSRF-токен Livewire")
        val decoded = initial
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
        val root = runCatching { json.parseToJsonElement(decoded).jsonObject }
            .getOrElse { throw IOException("Некорректное начальное состояние Livewire", it) }
        val fingerprint = root["fingerprint"] as? JsonObject
            ?: throw IOException("Livewire не содержит fingerprint")
        val memo = root["serverMemo"] as? JsonObject
            ?: throw IOException("Livewire не содержит serverMemo")
        return LivewireState(
            fingerprint = fingerprint,
            serverMemo = memo,
            csrfToken = token,
        )
    }

    private fun mergeMemo(
        current: JsonObject,
        patch: JsonObject,
    ): JsonObject = buildJsonObject {
        current.forEach { (key, value) -> put(key, value) }
        patch.forEach { (key, value) ->
            if (key == "data" && value is JsonObject) {
                val oldData = current["data"] as? JsonObject ?: JsonObject(emptyMap())
                put(key, buildJsonObject {
                    oldData.forEach { (dataKey, dataValue) -> put(dataKey, dataValue) }
                    value.forEach { (dataKey, dataValue) -> put(dataKey, dataValue) }
                })
            } else {
                put(key, value)
            }
        }
    }

    private fun parseSiteDate(value: String): LocalDate =
        LocalDate.parse(value, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}

internal object LivewireGroupParser {
    private val groupAttribute = Regex("""data-group\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)

    fun parse(html: String): List<String> = groupAttribute.findAll(html)
        .map { decodeHtml(it.groupValues[2]).trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    private fun decodeHtml(value: String): String {
        val named = value
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
        val decimal = Regex("&#(\\d+);").replace(named) { match ->
            match.groupValues[1].toIntOrNull()?.let(::codePointToString) ?: match.value
        }
        return Regex("&#[xX]([0-9a-fA-F]+);").replace(decimal) { match ->
            match.groupValues[1].toIntOrNull(16)?.let(::codePointToString) ?: match.value
        }
    }

    private fun codePointToString(codePoint: Int): String =
        runCatching { String(Character.toChars(codePoint)) }.getOrDefault("")
}

internal object LivewireEventParser {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun parse(data: JSONObject, group: String, weekStart: LocalDate): ScheduleWeek {
        val byDate = mutableMapOf<LocalDate, MutableList<ScheduleEvent>>()
        val eventBuckets = data.optJSONObject("events") ?: JSONObject()
        eventBuckets.keys().forEach { bucketKey ->
            val bucket = eventBuckets.optJSONArray(bucketKey) ?: return@forEach
            for (index in 0 until bucket.length()) {
                val raw = bucket.optJSONObject(index) ?: continue
                val event = raw.toDomain(group, "$bucketKey:$index") ?: continue
                byDate.getOrPut(event.date) { mutableListOf() }.add(event)
            }
        }

        val days = (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            ScheduleDay(
                date = date,
                events = byDate[date].orEmpty().sortedWith(
                    compareBy<ScheduleEvent> { it.startTime }
                        .thenBy { it.pairNumber ?: Int.MAX_VALUE }
                        .thenBy { it.discipline },
                ),
            )
        }
        return ScheduleWeek(group, weekStart, days)
    }

    private fun JSONObject.toDomain(group: String, sourcePosition: String): ScheduleEvent? {
        val date = runCatching { LocalDate.parse(optString("date"), dateFormatter) }.getOrNull()
            ?: return null
        val discipline = optString("discipline").trim().ifEmpty { return null }
        val teachers = optJSONObject("teachers")?.let { teacherObject ->
            teacherObject.keys().asSequence().mapNotNull { key ->
                val teacher = teacherObject.opt(key) as? JSONObject ?: return@mapNotNull null
                teacher.optString("fio").trim().ifEmpty {
                    listOf(
                        teacher.optString("last_name").trim(),
                        teacher.optString("first_name_one").trim(),
                        teacher.optString("middle_name_one").trim(),
                    ).filter { it.isNotEmpty() }.joinToString(" ")
                }.takeIf { it.isNotEmpty() }
            }
        }.orEmpty().toList().distinct()
        val classroom = normalizeDuplicatedText(optNullableString("classroom"))
        val start = optString("startTime").trim()
        val end = optString("endTime").trim()
        val key = listOf(
            group,
            date,
            start,
            end,
            optString("numberPair"),
            discipline,
            optString("groupType"),
            classroom,
            optNullableString("address"),
            teachers.joinToString(),
            optNullableString("comment"),
            sourcePosition,
        ).joinToString("|")
        return ScheduleEvent(
            id = key.sha256(),
            date = date,
            startTime = start,
            endTime = end,
            pairNumber = if (has("numberPair") && !isNull("numberPair")) optInt("numberPair") else null,
            discipline = discipline,
            type = displayEventType(optString("groupType")),
            classroom = classroom,
            address = displayAddress(normalizeDuplicatedText(optNullableString("address"))),
            teachers = teachers,
            comment = displayComment(optNullableString("comment")),
            onlineUrl = optNullableString("urlOnline"),
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

    internal fun normalizeDuplicatedText(value: String?): String? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.length % 2 == 0) {
            val half = text.length / 2
            if (text.substring(0, half) == text.substring(half)) return text.substring(0, half)
        }
        return text
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private class InMemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this.cookies) {
            cookies.forEach { incoming ->
                this.cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
                this.cookies += incoming
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
        cookies.removeAll { it.expiresAt < System.currentTimeMillis() }
        cookies.filter { it.matches(url) }
    }

    fun clear() = synchronized(cookies) { cookies.clear() }
}
