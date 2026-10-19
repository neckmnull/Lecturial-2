package com.rassvet.lecturial.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleAnalyticsTest {
    private val monday = LocalDate.of(2026, 10, 26)

    private fun lesson(date: LocalDate, start: String, end: String, type: String = "Лекция", id: String = "$date$start") =
        ScheduleEvent(
            id = id, date = date, startTime = start, endTime = end, pairNumber = null, discipline = "Предмет",
            type = type, classroom = null, address = null, teachers = emptyList(), comment = null, onlineUrl = null,
        )

    @Test
    fun `common free time skips pairs anyone is busy in and prefers mid-day`() {
        val schedules = mapOf(
            "Семенов" to listOf(lesson(monday, "08:45", "10:05")),
            "Ваганов" to listOf(lesson(monday, "10:20", "11:40"), lesson(monday, "14:00", "15:00")),
        )

        val availability = slotAvailability(schedules, monday..monday)
        val free = availability.filter { it.isFreeForAll }.map { it.slot.number }
        val best = recommendMeetingSlots(availability, LocalDateTime.of(monday.minusDays(1), java.time.LocalTime.NOON))

        // 14:00–15:00 overlaps pairs 4 (13:30–14:50) and 5 (15:05 starts after 15:00 — free).
        assertEquals(listOf(3, 5, 6, 7), free)
        assertEquals(listOf("Ваганов"), availability.single { it.slot.number == 4 }.busy)
        assertEquals(3, best.first().slot.number)
        assertTrue(best.first().isFreeForAll)
    }

    @Test
    fun `past slots are never recommended`() {
        val availability = slotAvailability(mapOf("A" to emptyList()), monday..monday)

        val best = recommendMeetingSlots(availability, monday.atTime(13, 0), limit = 10)

        assertTrue(best.all { it.slot.start.isAfter(java.time.LocalTime.of(13, 0)) })
    }

    @Test
    fun `occupancy counts parallel subgroups once and finds windows`() {
        val tuesday = monday.plusDays(1)
        val events = listOf(
            lesson(monday, "08:45", "10:05", id = "a"),
            lesson(monday, "08:45", "10:05", type = "Практика", id = "a-subgroup"),
            lesson(monday, "11:55", "13:15", type = "Практика"),
            lesson(tuesday, "10:20", "11:40"),
            lesson(monday.plusWeeks(1), "10:20", "11:40"),
        )

        val stats = occupancyStats(events, monday..monday.plusDays(6))

        assertEquals(3, stats.pairs)
        assertEquals(6, stats.academicHours)
        assertEquals(240, stats.busyMinutes)
        assertEquals(2, stats.activeDays)
        assertEquals(6, stats.studyDays)
        assertEquals(2, stats.maxPairsInDay)
        assertEquals(monday, stats.busiestDay)
        assertEquals(1, stats.windows)
        assertEquals(2, stats.pairsByWeekday[DayOfWeek.MONDAY])
        assertEquals(1.5, stats.averagePerActiveDay, 0.001)
    }

    @Test
    fun `periods cover week, month and the current semester`() {
        val autumn = LocalDate.of(2026, 10, 19)

        assertEquals(LocalDate.of(2026, 10, 19)..LocalDate.of(2026, 10, 25), AnalyticsPeriod.Week.range(autumn))
        assertEquals(LocalDate.of(2026, 10, 1)..LocalDate.of(2026, 10, 31), AnalyticsPeriod.Month.range(autumn))
        assertEquals(LocalDate.of(2026, 9, 1)..LocalDate.of(2027, 1, 31), AnalyticsPeriod.Semester.range(autumn))
        assertEquals(LocalDate.of(2026, 9, 1)..LocalDate.of(2027, 1, 31), AnalyticsPeriod.Semester.range(LocalDate.of(2027, 1, 10)))
        assertEquals(LocalDate.of(2027, 2, 1)..LocalDate.of(2027, 6, 30), AnalyticsPeriod.Semester.range(LocalDate.of(2027, 3, 5)))
    }

    @Test
    fun `room load ranks rooms and spreads through the day`() {
        val schedules = mapOf(
            "Альфа 5.8" to listOf(lesson(monday, "08:45", "10:05"), lesson(monday, "10:20", "11:40")),
            "К_12" to listOf(lesson(monday, "08:45", "10:05")),
            "Пустая" to emptyList(),
        )

        val loads = roomLoads(schedules, monday..monday)
        val intraday = intradayRoomLoad(loads)

        assertEquals(listOf("Альфа 5.8", "К_12", "Пустая"), loads.map { it.room })
        assertEquals(2.0 / 7, loads.first().utilization, 0.0001)
        assertEquals(2.0 / 3, intraday.first().second, 0.0001)
        assertEquals(1.0 / 3, intraday[1].second, 0.0001)
        assertEquals(0.0, intraday[2].second, 0.0001)
    }

    @Test
    fun `specialist groups and classroom names`() {
        assertTrue(isSpecialistGroup("С06ББ-26/1"))
        assertFalse(isSpecialistGroup("ИОП-ИТ-26/1"))
        assertFalse(isSpecialistGroup("Сириус"))
        assertEquals("Альфа 5.8", ScheduleTarget.classroomLabel("Альфа 5.8Альфа 5.8 (Основной)"))
        assertEquals("1.35К_0 (стартап-лаборатория ИНТЦ)", ScheduleTarget.classroomLabel("1.35К_0 (стартап-лаборатория ИНТЦ) (Основной)"))
        assertEquals("Альфа 5.8", ScheduleTarget.label(ScheduleTarget.classroomKey("Альфа 5.8Альфа 5.8 (Основной)"), null))
    }
}
