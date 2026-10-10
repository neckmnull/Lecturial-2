package com.rassvet.lecturial.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class SiriusScheduleDataSourceTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `performs initial request and Livewire group call`() = runBlocking {
        server.enqueue(
            MockResponse()
                .addHeader("Set-Cookie", "session=test; Path=/")
                .setBody(initialHtml()),
        )
        server.enqueue(MockResponse().setBody(groupResponse()))
        val source = SiriusScheduleDataSource(server.url("/").toString(), OkHttpClient())

        val week = source.fetchWeek("ИОП-ИТ-26/2", LocalDate.of(2026, 8, 31))

        assertEquals("Физика", week.days.first().events.single().discipline)
        assertEquals("/", server.takeRequest().path)
        val livewire = server.takeRequest()
        assertEquals("/livewire/message/main-grid", livewire.path)
        assertEquals("true", livewire.getHeader("X-Livewire"))
        assertTrue(livewire.body.readUtf8().contains("ИОП-ИТ-26/2"))
        assertTrue(livewire.getHeader("Cookie").orEmpty().contains("session=test"))
    }

    @Test
    fun `parses group attributes entities and removes duplicates`() {
        val html = """
            <li data-group="ИОП-ИТ-26/1"></li>
            <li DATA-GROUP='Дизайн &amp; искусство'></li>
            <li data-group="Группа &#1040;&#x2F;1"></li>
            <li data-group=" ИОП-ИТ-26/1 "></li>
        """.trimIndent()

        assertEquals(
            listOf("ИОП-ИТ-26/1", "Дизайн & искусство", "Группа А/1"),
            LivewireGroupParser.parse(html),
        )
        assertTrue(LivewireGroupParser.parse("<html>broken</html>").isEmpty())
    }

    @Test
    fun `loads initial groups from page`() = runBlocking {
        server.enqueue(MockResponse().setBody(initialHtml() + "<li data-group=\"Магистратура 1\"></li>"))
        val source = SiriusScheduleDataSource(server.url("/").toString(), OkHttpClient())

        assertEquals(listOf("Магистратура 1"), source.fetchGroups(""))
        assertEquals("/", server.takeRequest().path)
    }

    @Test
    fun `searches groups with Livewire sync input`() = runBlocking {
        server.enqueue(MockResponse().setBody(initialHtml()))
        server.enqueue(
            MockResponse().setBody(
                JSONObject()
                    .put("serverMemo", JSONObject().put("data", JSONObject()).put("checksum", "search"))
                    .put("effects", JSONObject().put("html", "<li data-group=\"Биоинженерия &amp; науки\"></li>"))
                    .toString(),
            ),
        )
        val source = SiriusScheduleDataSource(server.url("/").toString(), OkHttpClient())

        assertEquals(listOf("Биоинженерия & науки"), source.fetchGroups("  био  "))
        server.takeRequest()
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        val update = body.getJSONArray("updates").getJSONObject(0)
        assertEquals("syncInput", update.getString("type"))
        assertEquals("search", update.getJSONObject("payload").getString("name"))
        assertEquals("био", update.getJSONObject("payload").getString("value"))
    }

    private fun initialHtml(): String {
        val initial = JSONObject()
            .put("fingerprint", JSONObject().put("id", "component").put("name", "main-grid"))
            .put(
                "serverMemo",
                JSONObject()
                    .put("data", JSONObject().put("date", "31.08.2026").put("events", JSONObject()))
                    .put("checksum", "initial"),
            )
            .toString()
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
        return """<div wire:initial-data="$initial"></div><script>window.livewire_token = 'token';</script>"""
    }

    private fun groupResponse(): String {
        val event = JSONObject()
            .put("date", "31.08.2026")
            .put("startTime", "08:45")
            .put("endTime", "10:05")
            .put("discipline", "Физика")
            .put("groupType", "Лекции")
            .put("numberPair", 1)
            .put("teachers", JSONObject())
        val data = JSONObject()
            .put("group", "ИОП-ИТ-26/2")
            .put("events", JSONObject().put("0_0", JSONArray().put(event)))
        return JSONObject()
            .put("serverMemo", JSONObject().put("data", data).put("checksum", "next"))
            .put("effects", JSONObject())
            .toString()
    }
}
