package com.rassvet.lecturial.data.outlook

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rassvet.lecturial.data.local.SecretBox
import com.rassvet.lecturial.eventually
import com.rassvet.lecturial.lesson
import com.rassvet.lecturial.model.UniversityZone
import com.rassvet.lecturial.testApp
import com.rassvet.lecturial.withTestSchedule
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The whole Outlook sync on the phone's Keystore and files, with Microsoft played by [FakeMicrosoft]. */
@RunWith(AndroidJUnit4::class)
class OutlookSyncTest {
    private class FakeMicrosoft {
        val events = LinkedHashMap<String, JSONObject>()
        val calendars = LinkedHashMap<String, String>()
        var tokenAnswer = """{"access_token":"new","refresh_token":"R2","expires_in":3600}"""
        var tokenRequests = 0
        var created = 0
        private var nextId = 0

        val http: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain -> synchronized(this) { answer(chain.request()) } }
            .build()

        private fun answer(request: Request): Response {
            val path = request.url.encodedPath
            val body = request.body?.let { content -> Buffer().also(content::writeTo).readUtf8() }.orEmpty()
            return when {
                request.url.host == "login.microsoftonline.com" -> {
                    tokenRequests++
                    reply(request, if ("error" in tokenAnswer) 400 else 200, tokenAnswer)
                }
                path == "/v1.0/me" -> reply(request, 200, JSONObject().put("displayName", "Тест").put("mail", "t@example.com").toString())
                path == "/v1.0/me/calendars" && request.method == "GET" -> {
                    val list = JSONArray()
                    calendars.forEach { (id, name) -> list.put(JSONObject().put("id", id).put("name", name)) }
                    reply(request, 200, JSONObject().put("value", list).toString())
                }
                path == "/v1.0/me/calendars" -> {
                    val id = "calendar${++nextId}"
                    calendars[id] = JSONObject(body).getString("name")
                    reply(request, 201, JSONObject().put("id", id).toString())
                }
                path.endsWith("/calendarView") -> {
                    val id = path.removeSuffix("/calendarView").substringAfterLast('/')
                    if (id !in calendars) reply(request, 404, """{"error":{"message":"not found"}}""") else calendarView(request)
                }
                path.endsWith("batch") -> reply(request, 200, batch(JSONObject(body)))
                else -> reply(request, 404, "{}")
            }
        }

        private fun calendarView(request: Request): Response {
            val list = JSONArray()
            events.values.forEach { event ->
                // As Graph gives times back: with fractions of a second.
                val copy = JSONObject(event.toString())
                listOf("start", "end").forEach { side ->
                    copy.getJSONObject(side).put("dateTime", event.getJSONObject(side).getString("dateTime").take(19) + ".0000000")
                }
                list.put(copy)
            }
            return reply(request, 200, JSONObject().put("value", list).toString())
        }

        private fun batch(payload: JSONObject): String {
            val requests = payload.getJSONArray("requests")
            val responses = JSONArray()
            for (index in 0 until requests.length()) {
                val item = requests.getJSONObject(index)
                val url = item.getString("url")
                val eventId = url.substringAfterLast('/')
                val result = JSONObject().put("id", item.getString("id"))
                when (item.getString("method")) {
                    "POST" -> {
                        val id = "event${++nextId}"
                        events[id] = item.getJSONObject("body").put("id", id)
                        created++
                        result.put("status", 201).put("body", JSONObject().put("id", id))
                    }
                    "PATCH" -> if (eventId in events) {
                        events[eventId] = item.getJSONObject("body").put("id", eventId)
                        result.put("status", 200).put("body", JSONObject())
                    } else {
                        result.put("status", 404)
                    }
                    "DELETE" -> result.put("status", if (events.remove(eventId) != null) 204 else 404)
                }
                responses.put(result)
            }
            return JSONObject().put("responses", responses).toString()
        }

        private fun reply(request: Request, code: Int, text: String) = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(text.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private val app = testApp
    private val syncPrefs = app.getSharedPreferences("outlook_sync", Context.MODE_PRIVATE)
    private val authPrefs = app.getSharedPreferences("outlook_auth", Context.MODE_PRIVATE)
    private val record = File(app.filesDir, "outlook_synced.json")
    private val vault = TokenVault(app)
    private val microsoft = FakeMicrosoft()
    private val day: LocalDate = LocalDate.now(UniversityZone).plusDays(1)
    private val lessons = listOf(
        lesson("a", day, "08:45", "10:05", "Физика"),
        lesson("b", day, "10:20", "11:40", "Химия"),
    )

    private fun clean() {
        syncPrefs.edit().clear().commit()
        authPrefs.edit().clear().commit()
        record.delete()
    }

    @Before
    fun setUp() = clean()

    @After
    fun tearDown() = clean()

    private fun signedIn(expiresAt: Long = System.currentTimeMillis() + 3_600_000L): OutlookSync {
        vault.save(MicrosoftTokens("A", "R", expiresAt))
        syncPrefs.edit().putString("account", "t@example.com").commit()
        return OutlookSync(app, microsoft.http)
    }

    private fun lessonEvents() = microsoft.events.values.filter {
        val subject = it.getString("subject")
        subject.startsWith("Физика") || subject.startsWith("Химия")
    }

    @Test
    fun aSyncPutsTheLessonsInTheAppsOwnCalendar() = runBlocking {
        withTestSchedule(lessons) {
            signedIn().syncNow()

            assertEquals(listOf("Lecturial 2"), microsoft.calendars.values.toList())
            assertEquals(2, lessonEvents().size)
            // Nothing changed, so nothing more goes out.
            val created = microsoft.created
            signedIn().syncNow()
            assertEquals(created, microsoft.created)
        }
    }

    @Test
    fun signingInAgainDoesNotDoubleTheLessons() = runBlocking {
        withTestSchedule(lessons) {
            signedIn().syncNow()
            val created = microsoft.created

            // What a new sign-in or a sync broken off before saving leaves: no record of the events.
            syncPrefs.edit().remove("calendar_id").commit()
            record.delete()
            signedIn().syncNow()

            assertEquals(2, lessonEvents().size)
            assertEquals(created, microsoft.created)
        }
    }

    @Test
    fun extraCopiesGoAndTheUsersOwnEventsStay() = runBlocking {
        withTestSchedule(lessons) {
            signedIn().syncNow()
            microsoft.events["copy"] = JSONObject(lessonEvents().first().toString()).put("id", "copy")
            microsoft.events["mine"] = JSONObject(lessonEvents().first().toString())
                .put("id", "mine")
                .put("subject", "Встреча")
                .put("body", JSONObject().put("contentType", "text").put("content", "Моё"))
            record.delete()

            signedIn().syncNow()

            assertEquals(2, lessonEvents().size)
            assertFalse("copy" in microsoft.events)
            assertTrue("mine" in microsoft.events)
        }
    }

    @Test
    fun aCalendarDeletedInOutlookIsCreatedAndFilledAgain() = runBlocking {
        withTestSchedule(lessons) {
            val sync = signedIn()
            sync.syncNow()
            microsoft.calendars.clear()
            microsoft.events.clear()

            sync.syncNow()
            assertTrue(sync.state.value.error!!.contains("удалён"))
            assertFalse(syncPrefs.contains("calendar_id"))

            sync.syncNow()
            assertNull(sync.state.value.error)
            assertEquals(1, microsoft.calendars.size)
            assertEquals(2, lessonEvents().size)
        }
    }

    @Test
    fun aPassingFailureAtMicrosoftKeepsTheSignIn() = runBlocking {
        withTestSchedule(lessons) {
            microsoft.tokenAnswer = """{"error":"temporarily_unavailable","error_description":"Try later"}"""
            val sync = signedIn(expiresAt = 0L)

            sync.syncNow()

            assertEquals(1, microsoft.tokenRequests)
            assertEquals("t@example.com", sync.state.value.account)
            assertNotNull(vault.load())
            assertEquals("Try later", sync.state.value.error)
        }
    }

    @Test
    fun anEndedSignInAsksToSignInAgain() = runBlocking {
        withTestSchedule(lessons) {
            microsoft.tokenAnswer = """{"error":"invalid_grant","error_description":"Expired"}"""
            val sync = signedIn(expiresAt = 0L)

            sync.syncNow()

            assertFalse(sync.state.value.signedIn)
            assertNull(vault.load())
            assertEquals("Вход в Microsoft истёк. Войдите заново", sync.state.value.error)
        }
    }

    @Test
    fun anExpiredTokenIsRenewedAndSaved() = runBlocking {
        withTestSchedule(lessons) {
            signedIn(expiresAt = 0L).syncNow()

            assertEquals(1, microsoft.tokenRequests)
            assertEquals("new", vault.load()!!.accessToken)
            assertEquals(2, lessonEvents().size)
        }
    }

    @Test
    fun theBrowsersAnswerSignsInAndRunsTheFirstSync() = runBlocking {
        withTestSchedule(lessons) {
            syncPrefs.edit().putString("pkce_state", "real").putString("pkce_verifier", "verifier").commit()
            val sync = OutlookSync(app, microsoft.http)

            sync.completeSignIn(Uri.parse("${app.packageName}://oauth?code=c&state=real"))

            eventually("the first sync") { sync.state.value.lastSummary != null }
            assertEquals("t@example.com", sync.state.value.account)
            assertEquals(2, lessonEvents().size)
            assertFalse("A code works once", syncPrefs.contains("pkce_verifier"))
        }
    }

    @Test
    fun aForgedAnswerIsRefused() {
        syncPrefs.edit().putString("pkce_state", "real").putString("pkce_verifier", "verifier").commit()
        val sync = OutlookSync(app, microsoft.http)

        sync.completeSignIn(Uri.parse("${app.packageName}://oauth?code=stolen&state=forged"))

        eventually("the refusal") { sync.state.value.error != null }
        assertEquals(0, microsoft.tokenRequests)
        assertNull(vault.load())
        assertFalse(sync.state.value.signedIn)
    }

    @Test
    fun anAnswerWithoutARequestIsRefused() {
        val sync = OutlookSync(app, microsoft.http)

        sync.completeSignIn(Uri.parse("${app.packageName}://oauth?code=stolen"))

        eventually("the refusal") { sync.state.value.error != null }
        assertEquals(0, microsoft.tokenRequests)
        assertNull(vault.load())
    }

    @Test
    fun signingOutLeavesNothingOnThePhone() = runBlocking {
        withTestSchedule(lessons) {
            val sync = signedIn()
            sync.syncNow()
            assertTrue(record.exists())

            sync.signOut()

            eventually("the sign-out") { vault.load() == null && !record.exists() && !syncPrefs.contains("account") }
            assertFalse(sync.state.value.signedIn)
            assertFalse(syncPrefs.contains("calendar_id"))
        }
    }

    @Test
    fun tokensAreStoredSealedAndOlderOnesStillOpen() {
        vault.save(MicrosoftTokens("secret-access", "secret-refresh", 5L))

        assertFalse(authPrefs.getString("tokens", null)!!.contains("secret"))
        assertEquals(MicrosoftTokens("secret-access", "secret-refresh", 5L), vault.load())

        // Sealed the way versions before the shared SecretBox did: the same key and format.
        val older = JSONObject().put("access", "old-access").put("refresh", "old-refresh").put("expires", 9L).toString()
        authPrefs.edit().putString("tokens", SecretBox("lecturial_outlook_tokens").encrypt(older)).commit()
        assertEquals(MicrosoftTokens("old-access", "old-refresh", 9L), vault.load())

        vault.clear()
        assertNull(vault.load())

        authPrefs.edit().putString("tokens", "not sealed by this key").commit()
        assertNull("Garbage reads as signed out", vault.load())
    }
}
