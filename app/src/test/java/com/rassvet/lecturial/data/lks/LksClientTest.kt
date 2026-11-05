package com.rassvet.lecturial.data.lks

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

    private fun client(signedOut: Boolean = false) = LksClient(
        "session=abc",
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val answered = if (signedOut) request.newBuilder().url(LksClient.LOGIN_URL).build() else request
            Response.Builder()
                .request(answered)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(pages[request.url.encodedPath].orEmpty().toResponseBody("text/html".toMediaType()))
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
}
