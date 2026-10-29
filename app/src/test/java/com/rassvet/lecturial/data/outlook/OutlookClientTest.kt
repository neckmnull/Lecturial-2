package com.rassvet.lecturial.data.outlook

import com.rassvet.lecturial.model.CalendarEntry
import com.rassvet.lecturial.model.SyncPlan
import com.rassvet.lecturial.model.SyncedEntry
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OutlookClientTest {
    private lateinit var server: MockWebServer
    private val day = LocalDate.of(2026, 10, 22)

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() = server.close()

    @Test
    fun `PKCE challenge matches RFC 7636`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        assertTrue(Pkce.verifier().length >= 43)
    }

    @Test
    fun `the code is traded for tokens and errors are explained`() = runBlocking {
        val auth = MicrosoftAuth("client", OkHttpClient(), authority = server.url("/oauth").toString().trimEnd('/'), now = { 1_000L })
        server.enqueue(MockResponse.Builder().body("""{"access_token":"A","refresh_token":"R","expires_in":3600}""").build())
        val tokens = auth.exchangeCode("code", "com.rassvet.lecturial2://oauth", "verifier")
        assertEquals(MicrosoftTokens("A", "R", 1_000L + 3540_000L), tokens)
        val form = server.takeRequest().body!!.utf8()
        assertTrue("code_verifier=verifier" in form && "grant_type=authorization_code" in form)

        server.enqueue(MockResponse.Builder().code(400).body("""{"error":"invalid_grant","error_description":"AADSTS70000: expired"}""").build())
        val failure = runCatching { auth.refresh("R") }.exceptionOrNull()
        assertTrue(failure is MicrosoftAuthException && failure.code == "invalid_grant")
    }

    @Test
    fun `changes go out in one batch and the answers are applied`() = runBlocking {
        var refreshed = false
        val graph = GraphCalendar(OkHttpClient(), { force -> if (force) { refreshed = true; "new" } else "old" }, server.url("/v1.0").toString().trimEnd('/'), ZoneId.of("Europe/Moscow"))
        val entry = CalendarEntry("lesson:a", "МатАнал", day, day.atTime(8, 45), day.atTime(10, 5), false, "К_1", "body", 15)
        val changed = entry.copy(key = "lesson:b", subject = "Изменено")
        val gone = SyncedEntry("lesson:c", "r3", "x", day)
        val before = listOf(SyncedEntry("lesson:b", "r2", "old", day), gone)
        // The first try is rejected as expired: the client renews the token once and retries.
        server.enqueue(MockResponse.Builder().code(401).body("{}").build())
        server.enqueue(
            MockResponse.Builder().body(
                """{"responses":[{"id":"0","status":201,"body":{"id":"r1"}},{"id":"1","status":200,"body":{}},{"id":"2","status":204}]}""",
            ).build(),
        )

        val result = graph.apply("cal", SyncPlan(listOf(entry), listOf(before[0] to changed), listOf(gone)), before)

        assertTrue(refreshed)
        server.takeRequest()
        val batch = JSONObject(server.takeRequest().body!!.utf8()).getJSONArray("requests")
        assertEquals("POST", batch.getJSONObject(0).getString("method"))
        assertEquals("/me/calendars/cal/events", batch.getJSONObject(0).getString("url"))
        // 08:45 in Moscow is 05:45 UTC.
        assertEquals("2026-10-22T05:45:00", batch.getJSONObject(0).getJSONObject("body").getJSONObject("start").getString("dateTime"))
        assertEquals("PATCH", batch.getJSONObject(1).getString("method"))
        assertEquals("DELETE", batch.getJSONObject(2).getString("method"))
        assertEquals(Triple(1, 1, 1), Triple(result.created, result.updated, result.deleted))
        assertEquals(setOf("lesson:a" to "r1", "lesson:b" to "r2"), result.synced.map { it.key to it.remoteId }.toSet())
    }
}
