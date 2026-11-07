package com.rassvet.lecturial.data.lks

import com.rassvet.lecturial.model.LksDiscipline
import com.rassvet.lecturial.model.LksMark
import com.rassvet.lecturial.model.LksNews
import com.rassvet.lecturial.model.LksNotification
import com.rassvet.lecturial.model.LksSemester
import com.rassvet.lecturial.model.LksSnapshot
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The account's pages through jsoup, shaped as the site sends them: JSON in `wire:initial-data`, the rest in the markup. */
class LksClientTest {
    private fun component(name: String, data: String, inner: String = ""): String {
        val state = """{"fingerprint":{"name":"$name"},"serverMemo":{"data":$data}}""".replace("\"", "&quot;")
        return """<div wire:id="x" wire:initial-data="$state">$inner</div>"""
    }

    private val pages = mapOf(
        "/dashboard" to """
            <html><body>
            ${component(
                "user-notifications-menu",
                "{}",
                """3 <div wire:click="openNotification('n1')"><div>Заголовок</div><div>Текст</div><div>02.11.2026</div></div>""",
            )}
            <a href="/news/show/15"><span>Объявление</span> 2026-11-01 Подробнее</a>
            </body></html>
        """.trimIndent(),
        "/journal/list" to component(
            "journal-list",
            """{"grades":{"2026/2027":{"2026-11-02":{"disciplines":{"a":{"discipline":"Физика","grades":{"m1":
            {"mark_id":"m1","grade_as_long":5,"subject":"Оптика","homework":"Задача 3","start_date":"02.11.2026",
            "start_time":"13:30","action_type_name":"Лекции","absent":0}}}}}}}}""",
        ),
        "/student/trajectory" to component(
            "trajectory",
            """{"semesters":{"3":{"yearTitle":"2026/2027","disciplines":{"Физика":{"labor":4,"size":144,"marks":[],
            "elements":{"regel_part":{"control_actions":[{"control_action":{"title":"Экзамен"}}]}}}}}}}""",
        ),
    )

    private fun client(signedOut: Boolean = false, answers: Map<String, String> = pages) = LksClient(
        "session=abc",
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val answered = if (signedOut) request.newBuilder().url(LksClient.LOGIN_URL).build() else request
            Response.Builder()
                .request(answered)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(answers[request.url.encodedPath].orEmpty().toResponseBody("text/html".toMediaType()))
                .build()
        }.build(),
    )

    @Test
    fun `reads marks, trajectory, notifications and news from the pages`() {
        val snapshot = runBlocking { client().snapshot(nowMillis = 7L) }

        val mark = snapshot.marks.single()
        assertEquals("m1", mark.id)
        assertEquals("Физика", mark.discipline)
        assertEquals(LocalDate.of(2026, 11, 2), mark.date)
        assertEquals(5, mark.grade)
        assertEquals("Оптика", mark.topic)
        assertEquals("Задача 3", mark.homework)
        assertEquals(listOf("Экзамен"), snapshot.semesters.single().disciplines.single().controlForms)
        val notification = snapshot.notifications.single()
        assertEquals(listOf("n1", "Заголовок", "Текст", "02.11.2026"), listOf(notification.id, notification.title, notification.text, notification.time))
        assertEquals(3, snapshot.unreadNotifications)
        val news = snapshot.news.single()
        assertEquals(listOf("15", "Объявление", "2026-11-01"), listOf(news.id, news.title, news.date))
        assertEquals(7L, snapshot.loadedAtMillis)
    }

    @Test
    fun `landing on the sign-in form means the session ended`() {
        assertThrows(LksSignedOutException::class.java) {
            runBlocking { client(signedOut = true).snapshot() }
        }
    }

    @Test
    fun `a maintenance page is an error, not an account without grades`() {
        val maintenance = pages + ("/journal/list" to "<html><body>Технические работы</body></html>")
        assertThrows(IOException::class.java) {
            runBlocking { client(answers = maintenance).snapshot() }
        }
    }

    @Test
    fun `too many requests pause for as long as the site says, within limits`() {
        fun answer(retryAfter: String?) = runCatching {
            val client = LksClient(
                "session=abc",
                OkHttpClient.Builder().addInterceptor { chain ->
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(429).message("Too Many Requests")
                        .apply { retryAfter?.let { header("Retry-After", it) } }
                        .body("".toResponseBody("text/html".toMediaType())).build()
                }.build(),
            )
            runBlocking { client.snapshot() }
        }.exceptionOrNull() as LksThrottledException

        assertEquals(600_000L, answer("600").waitMillis)
        assertEquals("Without a number, five minutes", 300_000L, answer(null).waitMillis)
        assertEquals("A date instead of seconds", 300_000L, answer("Wed, 21 Oct 2026 07:28:00 GMT").waitMillis)
        assertEquals("Never less than a minute", 60_000L, answer("1").waitMillis)
        assertEquals("Never more than an hour", 3_600_000L, answer("999999").waitMillis)
    }

    @Test
    fun `a refresh waits for the interval and for the pause the site asked for`() {
        val minute = 60_000L
        assertEquals("Never tried", 0L, lksRefreshWaitMillis(now = 10 * minute, lastAttempt = 0L, blockedUntil = 0L, minInterval = minute))
        assertEquals(30_000L, lksRefreshWaitMillis(now = 10 * minute, lastAttempt = 10 * minute - 30_000L, blockedUntil = 0L, minInterval = minute))
        assertEquals(0L, lksRefreshWaitMillis(now = 10 * minute, lastAttempt = 9 * minute, blockedUntil = 0L, minInterval = minute))
        assertEquals("The longer of the two", 5 * minute, lksRefreshWaitMillis(now = 10 * minute, lastAttempt = 10 * minute, blockedUntil = 15 * minute, minInterval = minute))
        assertEquals("The clock moved back: no attempt in the future holds it up", 0L, lksRefreshWaitMillis(now = 10 * minute, lastAttempt = 500 * minute, blockedUntil = 0L, minInterval = minute))
        assertEquals("Nor a pause far ahead", 0L, lksRefreshWaitMillis(now = 10 * minute, lastAttempt = 0L, blockedUntil = 500 * minute, minInterval = minute))
    }

    @Test
    fun `a snapshot reads back the same from the cache`() {
        val snapshot = LksSnapshot(
            marks = listOf(
                LksMark("m1", "Физика", LocalDate.of(2026, 11, 2), "13:30", "Лекции", 5, false, "Оптика", "Задача 3"),
                LksMark("m2", "Физика", LocalDate.of(2026, 11, 3), null, "Практика", null, null, null, null),
            ),
            semesters = listOf(
                LksSemester(
                    "3",
                    "2026/2027",
                    listOf(
                        LksDiscipline("Физика", listOf("Экзамен"), 4.0, 144, listOf("отлично"), true),
                        LksDiscipline("Химия", emptyList(), null, null, emptyList(), null),
                    ),
                ),
            ),
            notifications = listOf(LksNotification("n1", "Заголовок", "Текст", "02.11.2026")),
            unreadNotifications = 3,
            news = listOf(LksNews("15", "Объявление", "2026-11-01")),
            loadedAtMillis = 7L,
        )

        assertEquals(snapshot, JSONObject(snapshot.toJson().toString()).toSnapshot())
    }
}
