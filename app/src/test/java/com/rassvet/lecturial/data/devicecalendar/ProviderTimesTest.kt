package com.rassvet.lecturial.data.devicecalendar

import com.rassvet.lecturial.model.CalendarEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderTimesTest {
    private val moscow = ZoneId.of("Europe/Moscow")
    private val date = LocalDate.of(2026, 10, 30)

    private fun entry(start: java.time.LocalDateTime, end: java.time.LocalDateTime, allDay: Boolean) = CalendarEntry(
        key = "test",
        subject = "Пара",
        date = start.toLocalDate(),
        start = start,
        end = end,
        allDay = allDay,
        location = null,
        body = "",
        reminderMinutes = null,
    )

    @Test
    fun `a lesson keeps its local time and zone`() {
        val times = providerTimes(entry(date.atTime(8, 45), date.atTime(10, 5), allDay = false), moscow)

        assertEquals(Instant.parse("2026-10-30T05:45:00Z").toEpochMilli(), times.start)
        assertEquals(Instant.parse("2026-10-30T07:05:00Z").toEpochMilli(), times.end)
        assertEquals("Europe/Moscow", times.zone)
    }

    @Test
    fun `an all-day plan is stored as UTC midnights, as the provider requires`() {
        val times = providerTimes(entry(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), allDay = true), moscow)

        assertEquals(Instant.parse("2026-10-30T00:00:00Z").toEpochMilli(), times.start)
        assertEquals(Instant.parse("2026-10-31T00:00:00Z").toEpochMilli(), times.end)
        assertEquals("UTC", times.zone)
    }

    @Test
    fun `an all-day plan ending on its own day still lasts the day`() {
        val times = providerTimes(entry(date.atStartOfDay(), date.atStartOfDay(), allDay = true), moscow)

        assertEquals(Instant.parse("2026-10-31T00:00:00Z").toEpochMilli(), times.end)
    }
}
