package com.rassvet.lecturial.data.network

import com.rassvet.lecturial.model.ScheduleTarget
import com.rassvet.lecturial.model.TeacherInfo
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TeacherScheduleTest {
    @Test
    fun `teacher list is read from the uuid-keyed teachersList`() {
        // Shape captured from schedule.siriusuniversity.ru/teacher (teachers.teacher-main-grid).
        val list = Json.parseToJsonElement(
            """
            {
              "59639f66-61e7-43c0-9bef-7462b4a627c4": {
                "id": "59639f66-61e7-43c0-9bef-7462b4a627c4", "last_name": "Ваганов",
                "first_name": "Михаил", "middle_name": "Геннадьевич",
                "fio": "Ваганов Михаил Геннадьевич", "department": "Президентский Лицей «Сириус»"
              },
              "31ad9019-67e8-4393-8ed4-be2aa8ed9e41": {
                "id": "31ad9019-67e8-4393-8ed4-be2aa8ed9e41", "last_name": "Бобылёв",
                "first_name": "Александр", "middle_name": "Геннадьевич", "fio": "", "department": null
              },
              "broken": "not a teacher"
            }
            """.trimIndent(),
        )

        val teachers = LivewireTeacherParser.parse(list)

        assertEquals(
            listOf(
                TeacherInfo("31ad9019-67e8-4393-8ed4-be2aa8ed9e41", "Бобылёв Александр Геннадьевич", null),
                TeacherInfo("59639f66-61e7-43c0-9bef-7462b4a627c4", "Ваганов Михаил Геннадьевич", "Президентский Лицей «Сириус»"),
            ),
            teachers,
        )
    }

    @Test
    fun `empty search result arrives as an array`() {
        assertEquals(emptyList<TeacherInfo>(), LivewireTeacherParser.parse(Json.parseToJsonElement("[]")))
        assertEquals(emptyList<TeacherInfo>(), LivewireTeacherParser.parse(null))
    }

    @Test
    fun `filtered classroom lists arrive as index-keyed objects`() {
        val unfiltered = Json.parseToJsonElement("""["Альфа 5.8Альфа 5.8 (Основной)", "К_12К_12 (Основной)"]""")
        val filtered = Json.parseToJsonElement("""{"3": "К_14К_14 (Основной)", "7": "К_12К_12 (Основной)", "9": 5}""")

        assertEquals(listOf("Альфа 5.8Альфа 5.8 (Основной)", "К_12К_12 (Основной)"), LivewireClassroomParser.parse(unfiltered))
        assertEquals(listOf("К_14К_14 (Основной)", "К_12К_12 (Основной)"), LivewireClassroomParser.parse(filtered))
        assertEquals(emptyList<String>(), LivewireClassroomParser.parse(null))
    }

    @Test
    fun `teacher lessons keep the student group`() {
        val lesson = JSONObject()
            .put("date", "19.10.2026")
            .put("startTime", "11:55")
            .put("endTime", "13:15")
            .put("discipline", "География")
            .put("groupType", "Практические занятия")
            .put("classroom", "1,29К_6")
            .put("teachers", JSONObject())
            .put("group", "ИОП-ИТ-26/1")
            .put("numberPair", 3)
        val data = JSONObject().put("events", JSONObject().put("3_0", JSONArray().put(lesson)))

        val week = LivewireEventParser.parse(data, ScheduleTarget.teacherKey("t-1"), LocalDate.of(2026, 10, 19))

        assertEquals("ИОП-ИТ-26/1", week.days.first().events.single().studentGroup)
    }

    @Test
    fun `one lecture for several groups becomes one lesson for a teacher`() {
        fun lesson(group: String) = JSONObject()
            .put("date", "21.10.2026").put("startTime", "15:05").put("endTime", "16:25")
            .put("discipline", "Приоритеты Стратегии").put("groupType", "Лекции")
            .put("classroom", "Альфа 5.8").put("teachers", JSONObject()).put("group", group)
        val data = JSONObject().put(
            "events",
            JSONObject().put("5_0", JSONArray().put(lesson("ИОП-ИТ-26/1")))
                .put("5_1", JSONArray().put(lesson("ИОП-ИТ-26/2"))),
        )
        val weekStart = LocalDate.of(2026, 10, 19)

        val teacherDay = LivewireEventParser.parse(data, ScheduleTarget.teacherKey("t"), weekStart).days[2].events
        val groupDay = LivewireEventParser.parse(data, "ИОП-ИТ-26/1", weekStart).days[2].events

        assertEquals(1, teacherDay.size)
        assertEquals(setOf("ИОП-ИТ-26/1", "ИОП-ИТ-26/2"), teacherDay.single().studentGroup!!.split(", ").toSet())
        assertEquals(2, groupDay.size)
    }

    @Test
    fun `schedule keys tell teachers and groups apart`() {
        val key = ScheduleTarget.teacherKey("59639f66")

        assertEquals("59639f66", ScheduleTarget.teacherId(key))
        assertTrue(ScheduleTarget.isTeacher(key))
        assertFalse(ScheduleTarget.isTeacher("ИОП-ИТ-26/1"))
        assertNull(ScheduleTarget.teacherId("teacher:"))
        assertEquals("Ваганов М. Г.", ScheduleTarget.label(key, "Ваганов М. Г."))
        assertEquals("ИОП-ИТ-26/1", ScheduleTarget.label("ИОП-ИТ-26/1", "ignored"))
    }
}
