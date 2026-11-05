package com.rassvet.lecturial.model

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPlannerTest {
    private val monday = LocalDate.of(2026, 11, 2)

    private fun pair(date: LocalDate, start: String, end: String, discipline: String, type: String = "Практическое занятие") = ScheduleEvent(
        id = "$date-$start-$discipline",
        date = date,
        startTime = start,
        endTime = end,
        pairNumber = null,
        discipline = discipline,
        type = type,
        classroom = null,
        address = null,
        teachers = emptyList(),
        comment = null,
        onlineUrl = null,
    )

    private val week = listOf(
        pair(monday, "08:45", "10:05", "Физика"),
        pair(monday, "10:20", "11:40", "География"),
        pair(monday.plusDays(1), "10:20", "11:40", "Физика", "Лекция"),
        pair(monday.plusDays(2), "13:30", "14:50", "География"),
        pair(monday.plusDays(4), "08:45", "10:05", "Иностранный язык"),
    )

    private val homework = LksMark(
        id = "m1", discipline = "География", date = monday, startTime = "10:20", type = "Практические занятия",
        grade = null, absent = false, topic = null, homework = "Параграф 12",
    )

    @Test
    fun `homework is due at the next pair and outranks going over a subject`() {
        val tasks = studyTasks(monday, week, listOf(homework), emptyList(), emptyMap())

        val first = tasks.first()
        assertEquals("hw:m1", first.key)
        assertTrue(first.detail!!.contains("Параграф 12"))
        // Tomorrow's lecture gets a short look, and geography isn't asked twice.
        assertEquals(15, tasks.single { it.key.startsWith("prep:") }.minutes)
        assertEquals(1, tasks.count { it.discipline == "География" })
        assertTrue(tasks.any { it.key == "review:иностранный язык" })
    }

    @Test
    fun `homework is most pressing on the day of its pair and gone after it`() {
        // Set on Monday, due at Wednesday's geography pair.
        val dueDay = studyTasks(monday.plusDays(2), week, listOf(homework), emptyList(), emptyMap())

        assertEquals("hw:m1", dueDay.first().key)
        assertTrue(dueDay.first().detail!!.contains("к паре сегодня"))
        assertTrue(studyTasks(monday.plusDays(3), week, listOf(homework), emptyList(), emptyMap()).none { it.key == "hw:m1" })
    }

    @Test
    fun `a subject studied yesterday waits behind one left alone`() {
        val tasks = studyTasks(
            monday,
            week,
            emptyList(),
            emptyList(),
            lastStudied = mapOf("география" to monday.minusDays(1)),
        )
        val reviews = tasks.filter { it.key.startsWith("review:") }.map { it.key }

        assertTrue(reviews.indexOf("review:иностранный язык") < reviews.indexOf("review:география"))
    }

    @Test
    fun `study fills the free time after the pairs with breaks, and done tasks drop out`() {
        val tasks = studyTasks(monday, week, listOf(homework), emptyList(), emptyMap())
        val settings = DayPlannerSettings(dayEnd = LocalTime.of(16, 0))
        val now = monday.atTime(7, 0)

        val day = planDay(monday, now, week, emptyList(), tasks, done = emptySet(), settings = settings)

        assertEquals(2, day.count { it.kind == DayBlockKind.Lesson })
        val rest = day.single { it.kind == DayBlockKind.Break && it.title.startsWith("Отдых") }
        assertEquals(LocalTime.of(11, 40), rest.start)
        val study = day.filter { it.kind == DayBlockKind.Study }
        assertEquals("hw:m1", study.first { it.start.isAfter(LocalTime.of(11, 0)) }.task!!.key)
        // Nothing overlaps.
        day.zipWithNext().forEach { (a, b) -> assertFalse("$a overlaps $b", b.start.isBefore(a.end)) }

        val after = planDay(monday, now, week, emptyList(), tasks, done = setOf("hw:m1"), settings = settings)
        assertTrue(after.none { it.task?.key == "hw:m1" })
    }

    @Test
    fun `today is planned from now on`() {
        val tasks = studyTasks(monday, week, listOf(homework), emptyList(), emptyMap())

        val day = planDay(monday, monday.atTime(14, 2), week, emptyList(), tasks, done = emptySet())

        assertTrue(day.filter { it.kind == DayBlockKind.Study || it.kind == DayBlockKind.Free }.all { !it.start.isBefore(LocalTime.of(14, 5)) })
        assertEquals(2, day.count { it.kind == DayBlockKind.Lesson })
    }

    @Test
    fun `events outside the curriculum take up time but are nothing to study for`() {
        val meeting = pair(monday, "12:00", "13:00", "Встречи с проектной группой", "Внеучебное")
        val tutorHour = pair(monday.plusDays(1), "15:00", "16:00", "Тьюторский час", "Внеучебное")
        val events = week + meeting + tutorHour

        val tasks = studyTasks(monday, events, emptyList(), emptyList(), emptyMap())
        val day = planDay(monday, monday.atTime(7, 0), events, emptyList(), tasks, done = emptySet())

        assertTrue(tasks.none { it.discipline == meeting.discipline || it.discipline == tutorHour.discipline })
        assertTrue(day.any { it.kind == DayBlockKind.Lesson && it.start == LocalTime.of(12, 0) })
        assertTrue(day.none { it.kind == DayBlockKind.Study && it.start.isBefore(LocalTime.of(13, 0)) && it.end.isAfter(LocalTime.of(12, 0)) })
    }

    @Test
    fun `physical education is never something to prepare or go over`() {
        val pe = listOf(
            pair(monday.plusDays(1), "08:45", "10:05", "Физическая культура и спорт"),
            pair(monday.plusDays(3), "08:45", "10:05", "Элективные дисциплины по физической культуре"),
        )

        val tasks = studyTasks(monday, week + pe, emptyList(), emptyList(), emptyMap())

        assertTrue(tasks.none { it.discipline.orEmpty().contains("физическ", ignoreCase = true) })
        assertTrue(tasks.any { it.key.startsWith("prep:") })
    }

    @Test
    fun `free windows skip the busy parts`() {
        val windows = freeWindows(
            LocalTime.of(8, 0),
            LocalTime.of(12, 30),
            listOf(LocalTime.of(8, 45) to LocalTime.of(10, 5), LocalTime.of(10, 20) to LocalTime.of(11, 40)),
        )

        // The 15 minutes between the pairs are a break, not a window.
        assertEquals(listOf(LocalTime.of(8, 0) to LocalTime.of(8, 45), LocalTime.of(11, 40) to LocalTime.of(12, 30)), windows)
    }
}
