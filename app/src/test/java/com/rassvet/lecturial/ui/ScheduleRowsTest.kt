package com.rassvet.lecturial.ui

import com.rassvet.lecturial.model.ScheduleDay
import com.rassvet.lecturial.model.ScheduleEvent
import com.rassvet.lecturial.model.ScheduleWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleRowsTest {
    private val monday = LocalDate.of(2026, 11, 2)

    private fun event(id: String, discipline: String, type: String) = ScheduleEvent(
        id = id,
        date = monday,
        startTime = "08:45",
        endTime = "10:05",
        pairNumber = null,
        discipline = discipline,
        type = type,
        classroom = null,
        address = null,
        teachers = emptyList(),
        comment = null,
        onlineUrl = null,
    )

    private fun headerFor(events: List<ScheduleEvent>): String {
        val week = ScheduleWeek("ИОП-ИТ-26/1", monday, (0L..6L).map { ScheduleDay(monday.plusDays(it), if (it == 0L) events else emptyList()) })
        val rows = buildScheduleRows(listOf(week), null, null, monday, null, null, week.group, monday, monday.atTime(7, 0), emptyMap(), emptyList())
        return rows.filterIsInstance<ScheduleRow.Header>().single().subtitle
    }

    @Test
    fun `the header counts pairs and leaves out events outside the curriculum`() {
        val tutorHour = event("c", "Тьюторский час", "Внеучебное")

        assertEquals("сегодня • 2 пары", headerFor(listOf(event("a", "Физика", "Лекция"), event("b", "Химия", "Практика"), tutorHour)))
        assertEquals("сегодня • нет пар", headerFor(listOf(tutorHour)))
    }
}
