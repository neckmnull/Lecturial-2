package com.rassvet.lecturial.data.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LivewireEventParserTest {
    private val weekStart = LocalDate.of(2026, 8, 31)

    @Test
    fun `parses parallel lessons and skips malformed teachers`() {
        val first = event(
            discipline = "Физика",
            classroom = "Альфа 5.7Альфа 5.7",
            teachers = JSONObject()
                .put("bad", "у")
                .put("one", JSONObject().put("fio", "Иванов Иван Иванович")),
        )
        val second = event(
            discipline = "Иностранный язык",
            classroom = "2.3",
            teachers = JSONObject().put("two", JSONObject().put("fio", "Петрова Анна Олеговна")),
        )
        val data = JSONObject().put("events", JSONObject().put("0_0", JSONArray().put(first).put(second)))

        val result = LivewireEventParser.parse(data, "ИОП-ИТ-26/2", weekStart)

        assertEquals(2, result.days.first().events.size)
        assertEquals("Альфа 5.7", result.days.first().events.first { it.discipline == "Физика" }.classroom)
        assertEquals("Лекция", result.days.first().events.first { it.discipline == "Физика" }.type)
        assertNull(result.days.first().events.first { it.discipline == "Физика" }.address)
        assertEquals(listOf("Иванов Иван Иванович"), result.days.first().events.first { it.discipline == "Физика" }.teachers)
    }

    @Test
    fun `creates seven empty days for missing events`() {
        val result = LivewireEventParser.parse(JSONObject(), "ИОП-ИТ-26/1", weekStart)

        assertEquals(7, result.days.size)
        assertEquals(0, result.days.sumOf { it.events.size })
    }

    @Test
    fun `supports nullable optional fields`() {
        val raw = event("Математика", null, JSONObject()).put("comment", JSONObject.NULL)
        val data = JSONObject().put("events", JSONObject().put("0", JSONArray().put(raw)))

        val parsed = LivewireEventParser.parse(data, "ИОП-ИТ-26/1", weekStart).days.first().events.single()

        assertNull(parsed.classroom)
        assertNull(parsed.comment)
        assertEquals(emptyList<String>(), parsed.teachers)
    }

    @Test
    fun `keeps identical parallel lessons as separate events`() {
        val sameLesson = event("Физика", "5.7", JSONObject())
        val data = JSONObject().put(
            "events",
            JSONObject().put("0_0", JSONArray().put(sameLesson).put(JSONObject(sameLesson.toString()))),
        )

        val events = LivewireEventParser.parse(data, "ИОП-ИТ-26/2", weekStart).days.first().events

        assertEquals(2, events.size)
        assertEquals(2, events.map { it.id }.distinct().size)
    }

    private fun event(discipline: String, classroom: String?, teachers: JSONObject) = JSONObject()
        .put("date", "31.08.2026")
        .put("startTime", "08:45")
        .put("endTime", "10:05")
        .put("discipline", discipline)
        .put("groupType", "Лекции")
        .put("address", "Основной")
        .put("classroom", classroom ?: JSONObject.NULL)
        .put("teachers", teachers)
        .put("numberPair", 1)
}
