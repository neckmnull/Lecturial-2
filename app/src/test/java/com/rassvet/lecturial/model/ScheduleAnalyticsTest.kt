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
    fun `course overview counts course pairs, weeks and teachers`() {
        fun teaching(date: LocalDate, start: String, end: String, groups: String?, teachers: List<String> = emptyList()) =
            lesson(date, start, end).copy(studentGroup = groups, teachers = teachers)
        val events = listOf(
            teaching(monday, "08:45", "10:05", "ИОП-ИТ-26/1, ИОП-ИТ-26/2"),
            teaching(monday, "10:20", "11:40", "ИОП-ИТ-24/2"),
            teaching(monday.plusWeeks(1), "08:45", "10:05", "иоп-ит-26/1"),
        )
        val range = monday..monday.plusDays(13)

        assertEquals(2, coursePairs(events, listOf("ИОП-ИТ-26/1"), range))
        assertEquals(listOf(monday to 2, monday.plusWeeks(1) to 1), weeklyPairs(events, range))

        val groupLessons = listOf(
            teaching(monday, "08:45", "10:05", null, listOf("Семенов  Михаил Евгеньевич", "Ваганов Михаил Геннадьевич")),
            teaching(monday, "10:20", "11:40", null, listOf("семенов михаил евгеньевич", " ")),
        )
        assertEquals(listOf("Ваганов Михаил Геннадьевич", "Семенов Михаил Евгеньевич"), courseTeacherNames(groupLessons))
    }

    @Test
    fun `readable names, levels and plurals`() {
        assertEquals("Семенов М. Е.", surnameWithInitials("Семенов Михаил Евгеньевич"))
        assertEquals("Семенов М.", surnameWithInitials("Семенов Михаил"))
        assertEquals("ЛК", surnameWithInitials("ЛК"))
        assertEquals(normalizePersonName("Семёнов  Михаил"), normalizePersonName("семенов михаил"))
        assertEquals(LoadLevel.High, loadLevel(12.0))
        assertEquals(LoadLevel.Medium, loadLevel(4.0))
        assertEquals(LoadLevel.Low, loadLevel(1.5))
        assertEquals(listOf("пара", "пары", "пар", "пар", "пара"), listOf(1, 3, 5, 12, 21).map(::pairsWord))
    }

    @Test
    fun `specialist groups and classroom names`() {
        assertTrue(isSpecialistGroup("ИОП-ИТ-26/1"))
        assertTrue(isSpecialistGroup(" иоп-ит-24/2"))
        assertFalse(isSpecialistGroup("С06ББ-26/1"))
        assertFalse(isSpecialistGroup("К0409-26/1"))
        assertEquals("Альфа 5.8", ScheduleTarget.classroomLabel("Альфа 5.8Альфа 5.8 (Основной)"))
        assertEquals("1.35К_0 (стартап-лаборатория ИНТЦ)", ScheduleTarget.classroomLabel("1.35К_0 (стартап-лаборатория ИНТЦ) (Основной)"))
        assertEquals("Альфа 5.8", ScheduleTarget.label(ScheduleTarget.classroomKey("Альфа 5.8Альфа 5.8 (Основной)"), null))
    }

    @Test
    fun `free rooms in a pair and until when they stay free`() {
        val third = SiriusPairSlots[2] // 11:55–13:15
        val schedules = mapOf(
            "Альфа 4.1" to listOf(lesson(monday, "11:55", "13:15")),
            // Ends right as the pair starts, then has a lesson at 15:05.
            "Альфа 4.2" to listOf(lesson(monday, "10:20", "11:55"), lesson(monday, "15:05", "16:25")),
            // Busy only on another day.
            "К_9" to listOf(lesson(monday.plusDays(1), "11:55", "13:15")),
            // Overlaps the pair partly.
            "Бета 2.3" to listOf(lesson(monday, "13:00", "14:00")),
        )

        val free = freeRooms(schedules, monday, third).associate { it.room to it.freeUntil }

        assertEquals(setOf("Альфа 4.2", "К_9"), free.keys)
        assertEquals(java.time.LocalTime.of(15, 5), free["Альфа 4.2"])
        assertEquals(null, free["К_9"])
    }

    @Test
    fun `the pair looked up first is the current or next one`() {
        fun at(date: LocalDate, hour: Int, minute: Int) = upcomingPair(LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)))

        // During the third pair: still the third pair today.
        assertEquals(monday to 3, at(monday, 12, 30).let { it.first to it.second.number })
        // Between pairs: the next one.
        assertEquals(monday to 4, at(monday, 13, 20).let { it.first to it.second.number })
        // After the last pair: the first pair tomorrow.
        assertEquals(monday.plusDays(1) to 1, at(monday, 20, 0).let { it.first to it.second.number })
        // Saturday evening and Sunday: Monday morning.
        val saturday = monday.with(DayOfWeek.SATURDAY)
        assertEquals(monday.plusWeeks(1) to 1, at(saturday, 21, 0).let { it.first to it.second.number })
        assertEquals(monday.plusWeeks(1) to 1, at(saturday.plusDays(1), 10, 0).let { it.first to it.second.number })
    }
}
