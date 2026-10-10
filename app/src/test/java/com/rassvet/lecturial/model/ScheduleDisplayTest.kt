package com.rassvet.lecturial.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleDisplayTest {
    @Test
    fun appThemeUsesSiriusAsDefaultAndFallsBackForUnknownValues() {
        assertEquals(AppTheme.Sirius, AppTheme.default)
        assertEquals(AppTheme.Ocean, AppTheme.fromStorageId("ocean"))
        assertEquals(AppTheme.Sirius, AppTheme.fromStorageId("unknown"))
        assertEquals(AppTheme.Sirius, AppTheme.fromStorageId(null))
    }

    @Test
    fun lectureTypeIsSingular() {
        assertEquals("Лекция", displayEventType("Лекции"))
        assertEquals("Лекция", displayEventType("лекция"))
        assertEquals("Лекция", displayEventType("Лекции (основной)"))
    }

    @Test
    fun practiceTypeIsSingular() {
        assertEquals("Практика", displayEventType("Практики"))
    }

    @Test
    fun genericCampusAddressIsHidden() {
        assertNull(displayAddress("Основной"))
        assertNull(displayAddress(" основной "))
        assertEquals("Главный корпус", displayAddress("Главный корпус"))
    }

    @Test
    fun teacherNameKeepsNameAndPatronymic() {
        assertEquals("И. Петр Алексеевич", shortenTeacherName("Иванов Петр Алексеевич"))
        assertEquals("П. Анна Олеговна", shortenTeacherName("Петрова Анна Олеговна"))
        assertEquals("Иванов", shortenTeacherName("Иванов"))
    }

    @Test
    fun liveStatePrefersCurrentLessonThenUpcoming() {
        val date = LocalDate.of(2026, 9, 2)
        val current = ScheduleEvent(
            id = "now",
            date = date,
            startTime = "13:00",
            endTime = "13:45",
            pairNumber = 3,
            discipline = "Физика",
            type = "Лекция",
            classroom = null,
            address = null,
            teachers = emptyList(),
            comment = null,
            onlineUrl = null,
        )
        val later = current.copy(id = "later", startTime = "15:00", endTime = "16:30", pairNumber = 5)
        assertEquals("ещё 35 мин", liveLessonState(listOf(current, later), date, LocalTime.of(13, 10))?.label)
        assertEquals("через 45 мин", liveLessonState(listOf(current, later), date, LocalTime.of(12, 15))?.label)
        assertEquals("через 1 ч 15 мин", liveLessonState(listOf(later), date, LocalTime.of(13, 45))?.label)
    }

    @Test
    fun extracurricularTypeIsShort() {
        assertEquals("Внеучебное", displayEventType("Внеучебное мероприятие"))
        assertEquals("Внеучебное", displayEventType("внеучебные мероприятия"))
    }

    @Test
    fun liveChipUsesReadableTimeUnits() {
        assertEquals("8 мин", formatLiveChip(8))
        assertEquals("1 час", formatLiveChip(60))
        assertEquals("1 ч 15 мин", formatLiveChip(75))
    }

    @Test
    fun reminderAppearsTenMinutesBeforeStart() {
        val date = LocalDate.of(2026, 9, 3)
        val event = ScheduleEvent(
            id = "soon",
            date = date,
            startTime = "16:30",
            endTime = "18:00",
            pairNumber = 6,
            discipline = "Алгоритмы",
            type = "Внеучебное мероприятие",
            classroom = "5.7",
            address = null,
            teachers = emptyList(),
            comment = null,
            onlineUrl = null,
        )
        val zone = java.time.ZoneOffset.UTC
        val live = visibleLessonLive(listOf(event), java.time.LocalDateTime.of(date, LocalTime.of(16, 22)), zone)
        assertEquals(LessonLivePhase.UPCOMING, live?.phase)
        assertEquals(8, live?.remainingMinutes)

        val tooEarly = visibleLessonLive(listOf(event), java.time.LocalDateTime.of(date, LocalTime.of(16, 0)), zone)
        assertNull(tooEarly)

        val alarms = scheduledLessonAlarms(listOf(event), java.time.LocalDateTime.of(date, LocalTime.of(10, 0)), zone)
        assertEquals(listOf(LessonAlarmKind.REMIND, LessonAlarmKind.START, LessonAlarmKind.END), alarms.map { it.kind })
    }

    @Test
    fun weekRangeUsesEnDashInSameMonth() {
        val start = LocalDate.of(2026, 9, 7)
        assertEquals("7–13 сентября", weekRangeLabel(start, start.plusDays(6)))
    }
}
