package com.rassvet.lecturial.data.network

import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleTarget
import com.rassvet.lecturial.model.ScheduleWeek
import com.rassvet.lecturial.model.TeacherInfo
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
    /** [group] is a schedule key: a group name or [ScheduleTarget.teacherKey]. */
    suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek
    suspend fun fetchGroups(query: String): List<String> = emptyList()
    suspend fun fetchTeachers(query: String): List<TeacherInfo> = emptyList()
    suspend fun fetchClassrooms(query: String): List<String> = emptyList()

    /** Consecutive weeks starting at [firstWeekStart], delivered one by one as they load. */
    suspend fun fetchWeeks(
        group: String,
        firstWeekStart: LocalDate,
        count: Int,
        onWeek: suspend (ScheduleWeek) -> Unit,
    ) {
        repeat(count) { onWeek(fetchWeek(group, firstWeekStart.plusWeeks(it.toLong()))) }
    }
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
    private val teacherPageUrl = baseUrl.trimEnd('/') + "/teacher"
    private val classroomPageUrl = baseUrl.trimEnd('/') + "/classroom"

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
            // Habitual spellings like "иоп ит" are retried as "иоп-ит" in the same session;
            // the search field keeps what the user typed.
            SearchVariants.forGroup(normalizedQuery)
                .flatMap { variant -> LivewireGroupParser.parse(state.syncInput("search", variant)) }
                .distinct()
        }
    }

    override suspend fun fetchTeachers(query: String): List<TeacherInfo> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            cookieJar.clear()
            val state = livewireStateFromHtml(execute(Request.Builder().url(teacherPageUrl).get().build()))
            val variants = SearchVariants.forTeacher(query)
            if (variants.isEmpty()) {
                return@withContext LivewireTeacherParser.parse(state.serverMemo["data"]?.jsonObject?.get("teachersList"))
            }
            variants.flatMap { variant ->
                state.syncInput("search", variant)
                LivewireTeacherParser.parse(state.serverMemo["data"]?.jsonObject?.get("teachersList"))
            }.distinctBy { it.id }
        }
    }

    override suspend fun fetchClassrooms(query: String): List<String> = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            cookieJar.clear()
            val state = livewireStateFromHtml(execute(Request.Builder().url(classroomPageUrl).get().build()))
            val normalizedQuery = query.trim()
            if (normalizedQuery.isNotEmpty()) state.syncInput("search", normalizedQuery)
            LivewireClassroomParser.parse(state.serverMemo["data"]?.jsonObject?.get("classroomsList"))
        }
    }

    override suspend fun fetchWeek(group: String, weekStart: LocalDate): ScheduleWeek {
        var result: ScheduleWeek? = null
        fetchWeeks(group, weekStart, 1) { result = it }
        return requireNotNull(result)
    }

    override suspend fun fetchWeeks(
        group: String,
        firstWeekStart: LocalDate,
        count: Int,
        onWeek: suspend (ScheduleWeek) -> Unit,
    ) = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            val normalizedTarget = firstWeekStart.with(DayOfWeek.MONDAY)
            val teacherId = ScheduleTarget.teacherId(group)
            val classroomId = ScheduleTarget.classroomId(group)
            cookieJar.clear()
            val pageUrl = when {
                teacherId != null -> teacherPageUrl
                classroomId != null -> classroomPageUrl
                else -> baseUrl
            }
            val initialHtml = execute(Request.Builder().url(pageUrl).get().build())
            val state = livewireStateFromHtml(initialHtml)

        var data = state.call("set", JSONArray().put(teacherId ?: classroomId ?: group))
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

            // One session walks forward week by week instead of reopening the site per week.
            var week = normalizedTarget
            repeat(count.coerceAtLeast(0)) { index ->
                if (index > 0) {
                    data = state.call("addWeek", JSONArray())
                    week = week.plusWeeks(1)
                }
                onWeek(LivewireEventParser.parse(data, group, week))
            }
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

        // "main-grid" for groups, "teachers.teacher-main-grid" for teachers.
        private val componentName: String =
            fingerprint["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "main-grid"

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
                .url(baseUrl.trimEnd('/') + "/livewire/message/" + componentName)
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
        val initial = initialDataPattern.find(html)?.groupValues?.get(1)
            ?: throw IOException("Сайт не содержит начальное состояние Livewire")
        val token = tokenPattern.find(html)?.groupValues?.get(1)
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

    private fun parseSiteDate(value: String): LocalDate = LocalDate.parse(value, siteDateFormatter)

    private companion object {
        val initialDataPattern = Regex("wire:initial-data=\"([^\"]+)\"")
        val tokenPattern = Regex("window\\.livewire_token\\s*=\\s*'([^']+)'")
        val siteDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}

/** Forgiving spellings to try for a search, most literal first. */
internal object SearchVariants {
    private val separators = Regex("""[\s_–—]+""")
    private val spaces = Regex("""\s+""")

    /** "иоп ит" → "иоп ит", "иоп-ит", "иопит": the site only matches its own "ИОП-ИТ". */
    fun forGroup(query: String): List<String> {
        val typed = query.trim()
        if (typed.isEmpty()) return emptyList()
        return listOf(typed, typed.replace(separators, "-"), typed.replace(separators, "")).distinct()
    }

    /** Collapses spaces and also tries "е" for "ё": "Семёнов  М" → "Семёнов М", "Семенов М". */
    fun forTeacher(query: String): List<String> {
        val typed = query.trim().replace(spaces, " ")
        if (typed.isEmpty()) return emptyList()
        return listOf(typed, typed.replace('ё', 'е').replace('Ё', 'Е')).distinct()
    }
}

internal object LivewireClassroomParser {
    /**
     * `classroomsList` is a JSON array for the unfiltered list, but a filtered Laravel
     * collection keeps its original indexes and arrives as an object: {"3": "...", "7": "..."}.
     */
    fun parse(list: kotlinx.serialization.json.JsonElement?): List<String> {
        val entries = when (list) {
            is JsonArray -> list
            is JsonObject -> list.values
            else -> return emptyList()
        }
        return entries
            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}

internal object LivewireTeacherParser {
    /** Reads `teachersList`: an object keyed by UUID (or an empty array when nothing matches). */
    fun parse(list: kotlinx.serialization.json.JsonElement?): List<TeacherInfo> {
        val entries = when (list) {
            is JsonObject -> list.values
            is JsonArray -> list
            else -> return emptyList()
        }
        return entries.mapNotNull { element ->
            val teacher = element as? JsonObject ?: return@mapNotNull null
            fun field(name: String) = (teacher[name] as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
            val id = field("id") ?: return@mapNotNull null
            val name = field("fio")
                ?: listOfNotNull(field("last_name"), field("first_name"), field("middle_name")).joinToString(" ")
                    .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            TeacherInfo(id = id, name = name, department = field("department"))
        }.distinctBy { it.id }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}

internal object LivewireGroupParser {
    private val groupAttribute = Regex("""data-group\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
    private val decimalEntity = Regex("""&#(\d+);""")
    private val hexEntity = Regex("""&#[xX]([0-9a-fA-F]+);""")

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
        val decimal = decimalEntity.replace(named) { match ->
            match.groupValues[1].toIntOrNull()?.let(::codePointToString) ?: match.value
        }
        return hexEntity.replace(decimal) { match ->
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

        val teacherSchedule = ScheduleTarget.isTeacher(group)
        val days = (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            val dayEvents = byDate[date].orEmpty()
            ScheduleDay(
                date = date,
                events = (if (teacherSchedule) mergeSharedLessons(dayEvents) else dayEvents).sortedWith(
                    compareBy<ScheduleEvent> { it.startTime }
                        .thenBy { it.pairNumber ?: Int.MAX_VALUE }
                        .thenBy { it.discipline },
                ),
            )
        }
        return ScheduleWeek(group, weekStart, days)
    }

    /**
     * A teacher's lecture for several groups arrives once per group; show it once with
     * every group listed.
     */
    internal fun mergeSharedLessons(events: List<ScheduleEvent>): List<ScheduleEvent> =
        events.groupBy { listOf(it.date, it.startTime, it.endTime, it.discipline, it.type, it.classroom) }
            .values
            .map { same ->
                val groups = same.mapNotNull { it.studentGroup }.distinct()
                same.first().copy(studentGroup = groups.joinToString(", ").ifEmpty { null })
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
            studentGroup = optNullableString("group"),
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

    private const val HEX = "0123456789abcdef"

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(StandardCharsets.UTF_8))
        val hex = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            hex[index * 2] = HEX[value ushr 4]
            hex[index * 2 + 1] = HEX[value and 0x0F]
        }
        return String(hex)
    }
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
