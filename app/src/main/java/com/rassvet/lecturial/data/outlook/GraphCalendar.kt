package com.rassvet.lecturial.data.outlook

import com.rassvet.lecturial.model.CalendarEntry
import com.rassvet.lecturial.model.SyncPlan
import com.rassvet.lecturial.model.SyncedEntry
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** The signed-in account, as Outlook shows it. */
data class GraphAccount(val name: String, val email: String)

/** What a sync did; [failed] changes are retried by the next sync. */
data class GraphSyncResult(val created: Int, val updated: Int, val deleted: Int, val failed: Int, val synced: List<SyncedEntry>)

/**
 * The calendar part of Microsoft Graph that the sync needs: the account, the app's own
 * calendar and batched event changes. [token] gives an access token; with `true` it must
 * renew it, which happens once when Graph answers 401.
 */
class GraphCalendar(
    private val http: OkHttpClient,
    private val token: suspend (forceRefresh: Boolean) -> String,
    private val baseUrl: String = "https://graph.microsoft.com/v1.0",
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun account(): GraphAccount {
        val me = get("/me?\$select=displayName,mail,userPrincipalName")
        return GraphAccount(
            name = me.optString("displayName"),
            email = me.optString("mail").takeIf { it.isNotBlank() && it != "null" } ?: me.optString("userPrincipalName"),
        )
    }

    /** The app's own calendar, created on first use, so its events never mix with the user's. */
    suspend fun calendarId(name: String): String {
        val found = get("/me/calendars?\$select=id,name&\$top=100").optJSONArray("value") ?: JSONArray()
        for (index in 0 until found.length()) {
            val calendar = found.getJSONObject(index)
            if (calendar.optString("name") == name) return calendar.getString("id")
        }
        return send("POST", "/me/calendars", JSONObject().put("name", name).put("color", "lightBlue")).getString("id")
    }

    /** Applies [plan] to the calendar in batches; returns what is in the calendar afterwards. */
    suspend fun apply(calendarId: String, plan: SyncPlan, before: List<SyncedEntry>): GraphSyncResult {
        val synced = before.associateBy { it.key }.toMutableMap()
        var created = 0
        var updated = 0
        var deleted = 0
        var failed = 0
        val operations = plan.create.map { Operation.Create(it) } +
            plan.update.map { (old, new) -> Operation.Update(old, new) } +
            plan.delete.map { Operation.Delete(it) }
        operations.chunked(BATCH_LIMIT).forEach { chunk ->
            val requests = JSONArray()
            chunk.forEachIndexed { index, operation ->
                val request = JSONObject().put("id", "$index")
                when (operation) {
                    is Operation.Create -> request.put("method", "POST").put("url", "/me/calendars/$calendarId/events")
                        .put("headers", JSONObject().put("Content-Type", "application/json"))
                        .put("body", eventJson(operation.entry))
                    is Operation.Update -> request.put("method", "PATCH").put("url", "/me/events/${operation.old.remoteId}")
                        .put("headers", JSONObject().put("Content-Type", "application/json"))
                        .put("body", eventJson(operation.entry))
                    is Operation.Delete -> request.put("method", "DELETE").put("url", "/me/events/${operation.old.remoteId}")
                }
                requests.put(request)
            }
            val responses = send("POST", "/\$batch", JSONObject().put("requests", requests)).optJSONArray("responses") ?: JSONArray()
            val statuses = (0 until responses.length()).associate { index ->
                val response = responses.getJSONObject(index)
                response.optString("id").toIntOrNull() to response
            }
            chunk.forEachIndexed { index, operation ->
                val response = statuses[index]
                val status = response?.optInt("status") ?: 0
                when (operation) {
                    is Operation.Create -> if (status in 200..299) {
                        val id = response!!.optJSONObject("body")?.optString("id").orEmpty()
                        synced[operation.entry.key] = SyncedEntry(operation.entry.key, id, operation.entry.fingerprint, operation.entry.date)
                        created++
                    } else failed++
                    is Operation.Update -> when {
                        status in 200..299 -> {
                            synced[operation.entry.key] = operation.old.copy(fingerprint = operation.entry.fingerprint, date = operation.entry.date)
                            updated++
                        }
                        // Deleted in Outlook meanwhile: forget it, the next sync creates it again.
                        status == 404 -> {
                            synced.remove(operation.old.key)
                            failed++
                        }
                        else -> failed++
                    }
                    is Operation.Delete -> if (status in 200..299 || status == 404) {
                        synced.remove(operation.old.key)
                        deleted++
                    } else failed++
                }
            }
        }
        return GraphSyncResult(created, updated, deleted, failed, synced.values.toList())
    }

    internal fun eventJson(entry: CalendarEntry): JSONObject = JSONObject()
        .put("subject", entry.subject)
        .put("body", JSONObject().put("contentType", "text").put("content", entry.body))
        .put("start", time(entry.start, entry.allDay))
        .put("end", time(entry.end, entry.allDay))
        .put("isAllDay", entry.allDay)
        .put("location", JSONObject().put("displayName", entry.location.orEmpty()))
        .put("isReminderOn", entry.reminderMinutes != null)
        .put("reminderMinutesBeforeStart", entry.reminderMinutes ?: 0)
        .put("showAs", if (entry.allDay) "free" else "busy")

    /** Timed events go in UTC, which is unambiguous; all-day ones keep midnight in the phone's zone. */
    private fun time(local: LocalDateTime, allDay: Boolean): JSONObject = if (allDay) {
        JSONObject().put("dateTime", local.format(GraphTime)).put("timeZone", zone.id)
    } else {
        val utc = local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        JSONObject().put("dateTime", utc.format(GraphTime)).put("timeZone", "UTC")
    }

    private suspend fun get(path: String): JSONObject = send("GET", path, null)

    private suspend fun send(method: String, path: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        var response = call(method, path, body, token(false))
        if (response.first == 401) response = call(method, path, body, token(true))
        val (status, text) = response
        if (status !in 200..299) {
            val message = runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrNull()
            throw IOException(
                when (status) {
                    401, 403 -> "Microsoft не дал доступ к календарю. Войдите заново"
                    429 -> "Microsoft просит подождать. Синхронизация повторится позже"
                    else -> message?.takeIf { it.isNotBlank() } ?: "Microsoft Graph вернул ошибку $status"
                },
            )
        }
        if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun call(method: String, path: String, body: JSONObject?, accessToken: String): Pair<Int, String> {
        val requestBody = body?.toString()?.toRequestBody(JsonType)
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $accessToken")
            .method(method, requestBody)
            .build()
        return try {
            http.newCall(request).execute().use { it.code to it.body.string() }
        } catch (failure: IOException) {
            throw IOException("Не удалось связаться с Microsoft. Проверьте интернет", failure)
        }
    }

    private sealed class Operation {
        class Create(val entry: CalendarEntry) : Operation()
        class Update(val old: SyncedEntry, val entry: CalendarEntry) : Operation()
        class Delete(val old: SyncedEntry) : Operation()
    }

    private companion object {
        /** Microsoft Graph takes at most 20 requests per batch. */
        const val BATCH_LIMIT = 20
        val GraphTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val JsonType = "application/json; charset=utf-8".toMediaType()
    }
}
