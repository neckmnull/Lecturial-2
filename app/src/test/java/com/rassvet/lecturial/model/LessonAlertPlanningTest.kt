package com.rassvet.lecturial.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonAlertPlanningTest {
    private val event = ScheduleEvent(
        id = "lesson",
        date = LocalDate.of(2026, 10, 14),
        startTime = "09:00",
        endTime = "10:30",
        pairNumber = 1,
        discipline = "Основы программирования",
        type = "Лекция",
        classroom = "5.7",
        address = null,
        teachers = emptyList(),
        comment = null,
        onlineUrl = null,
    )

    @Test
    fun `upcoming progress grows from reminder to lesson start`() {
        val start = 1_000_000L
        val live = VisibleLessonLive(event, LessonLivePhase.UPCOMING, start, start + 90 * MINUTE, 15)

        assertEquals(LessonProgress(0, 360), lessonProgress(live, start - 15 * MINUTE))
        assertEquals(LessonProgress(180, 360), lessonProgress(live, start - 7 * MINUTE - 30_000L))
        assertEquals(LessonProgress(360, 360), lessonProgress(live, start))
    }

    @Test
    fun `current progress represents elapsed part of lesson`() {
        val start = 1_000_000L
        val end = start + 90 * MINUTE
        val live = VisibleLessonLive(event, LessonLivePhase.CURRENT, start, end, 90)

        assertEquals(LessonProgress(0, 360), lessonProgress(live, start))
        assertEquals(LessonProgress(60, 360), lessonProgress(live, start + 15 * MINUTE))
        assertEquals(LessonProgress(360, 360), lessonProgress(live, end))
    }

    @Test
    fun `progress is determinate and clamped outside period`() {
        val start = 1_000_000L
        val end = start + 90 * MINUTE
        val live = VisibleLessonLive(event, LessonLivePhase.CURRENT, start, end, 90)

        assertEquals(LessonProgress(0, 1), lessonProgress(live, start - MINUTE, max = 0))
        assertEquals(LessonProgress(1, 1), lessonProgress(live, end + MINUTE, max = 0))
    }

    @Test
    fun `ongoing lesson wins over a next lesson that is within the reminder window`() {
        val next = event.copy(id = "next", startTime = "10:40", endTime = "12:10")
        val now = LocalDateTime.of(event.date, LocalTime.of(10, 30).minusMinutes(5))

        val live = visibleLessonLive(listOf(next, event), now, ZoneOffset.UTC)

        assertEquals("lesson", live?.event?.id)
        assertEquals(LessonLivePhase.CURRENT, live?.phase)
    }

    @Test
    fun `next lesson becomes upcoming once the current one ends`() {
        val next = event.copy(id = "next", startTime = "10:40", endTime = "12:10")
        val now = LocalDateTime.of(event.date, LocalTime.of(10, 30))

        val live = visibleLessonLive(listOf(event, next), now, ZoneOffset.UTC)

        assertEquals("next", live?.event?.id)
        assertEquals(LessonLivePhase.UPCOMING, live?.phase)
        assertEquals(10, live?.remainingMinutes)
    }

    @Test
    fun `nothing is live when the next lesson is beyond the reminder window`() {
        val now = LocalDateTime.of(event.date, LocalTime.of(8, 30))

        assertNull(visibleLessonLive(listOf(event), now, ZoneOffset.UTC))
    }

    @Test
    fun `alarms cover reminder, start and end that are still ahead`() {
        val now = LocalDateTime.of(event.date, LocalTime.of(8, 50))

        val kinds = scheduledLessonAlarms(listOf(event), now, ZoneOffset.UTC).map { it.kind }

        assertEquals(listOf(LessonAlarmKind.START, LessonAlarmKind.END), kinds)
    }

    @Test
    fun `pause ends at the start of the day after the last muted day`() {
        val now = LocalDateTime.of(2026, 10, 19, 12, 30)
        fun end(pause: LessonAlertPause) =
            LocalDateTime.ofEpochSecond(pause.until(now, ZoneOffset.UTC) / 1000, 0, ZoneOffset.UTC)

        assertEquals(LocalDateTime.of(2026, 10, 20, 0, 0), end(LessonAlertPause.Today))
        assertEquals(LocalDateTime.of(2026, 10, 22, 0, 0), end(LessonAlertPause.ThreeDays))
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0), end(LessonAlertPause.Week))
    }

    @Test
    fun `forever pause mutes every future lesson`() {
        val now = LocalDateTime.of(2026, 10, 19, 12, 30)
        val until = LessonAlertPause.Forever.until(now, ZoneOffset.UTC)
        val nextYear = event.copy(id = "later", date = event.date.plusYears(1))

        assertEquals(LessonAlertPause.FOREVER_MILLIS, until)
        assertEquals(emptyList<ScheduleEvent>(), lessonsOutsidePause(listOf(event, nextYear), until, ZoneOffset.UTC))
    }

    @Test
    fun `paused lessons get no alerts and later ones still do`() {
        val tomorrow = event.copy(id = "tomorrow", date = event.date.plusDays(1))
        val pausedUntil = LessonAlertPause.Today.until(LocalDateTime.of(event.date, LocalTime.of(8, 0)), ZoneOffset.UTC)

        val active = lessonsOutsidePause(listOf(event, tomorrow), pausedUntil, ZoneOffset.UTC)

        assertEquals(listOf("tomorrow"), active.map { it.id })
        assertEquals(listOf(event, tomorrow), lessonsOutsidePause(listOf(event, tomorrow), null, ZoneOffset.UTC))
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}