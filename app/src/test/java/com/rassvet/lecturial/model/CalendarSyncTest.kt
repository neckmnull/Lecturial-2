package com.rassvet.lecturial.model

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSyncTest {
    private val day = LocalDate.of(2026, 10, 22)

    private fun lesson(id: String, start: String = "08:45", end: String = "10:05", room: String? = "1,34К_1 (Основной)") = ScheduleEvent(
        id = id,
        date = day,
        startTime = start,
        endTime = end,
        pairNumber = 1,
        discipline = "Математический анализ",
        type = "Лекция",
        classroom = room,
        address = null,
        teachers = listOf("Криворотько Ольга Игоревна"),
        comment = null,
        onlineUrl = null,
    )

    @Test
    fun `a lesson becomes a timed event with the tidy room and the user's name`() {
        val entry = lessonEntries(listOf(lesson("a")), mapOf("Математический анализ" to "МатАнал"), reminderMinutes = 15).single()

        assertEquals("lesson:a", entry.key)
        assertEquals("МатАнал · Лекция", entry.subject)
        assertEquals(day.atTime(8, 45), entry.start)
        assertEquals(day.atTime(10, 5), entry.end)
        assertEquals("К_1, ауд. 1.34", entry.location)
        assertFalse(entry.allDay)
        assertEquals(15, entry.reminderMinutes)
        assertTrue("Криворотько" in entry.body)
    }

    @Test
    fun `lessons without a usable time or with a link for a room are handled`() {
        assertTrue(lessonEntries(listOf(lesson("x", start = "", end = "")), emptyMap(), null).isEmpty())
        assertEquals(null, lessonEntries(listOf(lesson("y", room = "Дистанционно")), emptyMap(), null).single().location)
    }

    @Test
    fun `plans become one event per occurrence, all-day without a time`() {
        val weekly = StudyPlan(
            id = "p", type = StudyPlanType.Event, title = "Кружок", dueDate = day, dueTime = LocalTime.of(18, 0),
            endTime = LocalTime.of(19, 30), recurrence = StudyPlanRecurrence.Weekly, createdAtMillis = 0,
        )
        val deadline = StudyPlan(id = "d", type = StudyPlanType.Deadline, title = "Курсовая", dueDate = day.plusDays(2), createdAtMillis = 0)
        val done = deadline.copy(id = "done", isCompleted = true)

        val entries = planEntries(listOf(weekly, deadline, done), day, day.plusDays(14), reminderMinutes = null)

        assertEquals(listOf("plan:p:$day", "plan:d:${day.plusDays(2)}", "plan:p:${day.plusDays(7)}", "plan:p:${day.plusDays(14)}").sorted(), entries.map { it.key }.sorted())
        val evening = entries.first { it.key == "plan:p:$day" }
        assertEquals(day.atTime(18, 0), evening.start)
        assertEquals(day.atTime(19, 30), evening.end)
        val allDay = entries.first { it.key.startsWith("plan:d") }
        assertTrue(allDay.allDay)
        assertEquals(day.plusDays(3).atStartOfDay(), allDay.end)
    }

    @Test
    fun `sync creates the new, updates the changed and deletes the gone, keeping history`() {
        val kept = lessonEntries(listOf(lesson("same")), emptyMap(), null).single()
        val changed = lessonEntries(listOf(lesson("moved", start = "10:20", end = "11:40")), emptyMap(), null).single()
        val added = lessonEntries(listOf(lesson("new")), emptyMap(), null).single()
        val synced = listOf(
            SyncedEntry("lesson:same", "r1", kept.fingerprint, day),
            SyncedEntry("lesson:moved", "r2", "old-fingerprint", day),
            SyncedEntry("lesson:cancelled", "r3", "x", day),
            SyncedEntry("lesson:last-month", "r4", "x", day.minusDays(30)),
        )

        val plan = planSync(listOf(kept, changed, added), synced, windowStart = day.minusDays(7))

        assertEquals(listOf("lesson:new"), plan.create.map { it.key })
        assertEquals(listOf("r2"), plan.update.map { it.first.remoteId })
        assertEquals(listOf("r3"), plan.delete.map { it.remoteId })
        assertTrue(planSync(listOf(kept), listOf(synced.first()), day).isEmpty)
    }
}
